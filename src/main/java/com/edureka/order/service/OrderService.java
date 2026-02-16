package com.edureka.order.service;

import com.edureka.order.client.CustomerClient;
import com.edureka.order.client.InventoryClient;
import com.edureka.order.client.ProductClient;
import com.edureka.order.dto.CustomerResponse;
import com.edureka.order.dto.InventoryCheckResponse;
import com.edureka.order.dto.ProductResponse;
import com.edureka.order.event.OrderPlacedEvent;
import com.edureka.order.model.Order;
import com.edureka.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Order Service - orchestrates order placement with inter-service communication.
 * Uses Feign clients (Proxy pattern) for sync calls to Product, Customer, Inventory.
 * Publishes OrderPlacedEvent to Kafka for async processing by Payment and Inventory.
 * Enhanced with Resilience4j for Circuit Breaker, Retry, and Timeout patterns.
 */
@Service
public class OrderService {

    private static final Logger _logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private CustomerClient customerClient;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    /**
     * Place order after validating with Product, Customer, and Inventory services.
     * Enhanced with Circuit Breaker, Retry, and Timeout patterns for resilience.
     * Falls back to graceful error handling when downstream services fail.
     */
    @CircuitBreaker(name = "orderService", fallbackMethod = "placeOrderFallback")
    @Retry(name = "orderService")
    @TimeLimiter(name = "orderService")
    public ResponseEntity<?> placeOrder(Order order) {
        if (order == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order body is required"));
        }
        if (order.getSkuCode() == null || order.getSkuCode().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order skuCode is required"));
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order quantity must be positive"));
        }
        if (order.getEmail() == null || order.getEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Customer email is required"));
        }

        try {
            // 1. Validate product exists and get price (sync - Product service)
            ResponseEntity<ProductResponse> productResp = productClient.getProductBySkuCode(order.getSkuCode());
            if (productResp.getStatusCode() != HttpStatus.OK || productResp.getBody() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product not found for skuCode: " + order.getSkuCode()));
            }
            ProductResponse product = productResp.getBody();
            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
            order.setPrice(unitPrice);

            // 2. Validate customer exists (sync - Customer service)
            ResponseEntity<CustomerResponse> customerResp = customerClient.getCustomerByEmail(order.getEmail());
            if (customerResp.getStatusCode() != HttpStatus.OK || customerResp.getBody() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Customer not found for email: " + order.getEmail()));
            }

            // 3. Check inventory (sync - Inventory service)
            ResponseEntity<InventoryCheckResponse> inventoryResp = inventoryClient.checkStock(
                    order.getSkuCode(), order.getQuantity());
            if (inventoryResp.getStatusCode() != HttpStatus.OK || inventoryResp.getBody() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Inventory check failed for skuCode: " + order.getSkuCode()));
            }
            if (!Boolean.TRUE.equals(inventoryResp.getBody().getInStock())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Insufficient stock for skuCode: " + order.getSkuCode(),
                                "availableQuantity", inventoryResp.getBody().getAvailableQuantity()));
            }

            // 4. Save order
            order.setId(order.getId() != null && !order.getId().isBlank() ? order.getId() : UUID.randomUUID().toString());
            order.setOrderNumber(UUID.randomUUID().toString());
            Order saved = orderRepository.save(order);
            _logger.info("Order placed successfully, order number: {}", saved.getOrderNumber());

            // 5. Publish event for Payment and Inventory (async - Kafka)
            BigDecimal totalAmount = saved.getPrice().multiply(BigDecimal.valueOf(saved.getQuantity()));
            kafkaTemplate.send("orderPlacedTopic", new OrderPlacedEvent(
                    saved.getOrderNumber(),
                    saved.getId(),
                    saved.getEmail(),
                    saved.getSkuCode(),
                    saved.getQuantity(),
                    totalAmount));

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (Exception e) {
            _logger.error("Error placing order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service temporarily unavailable. Please try again."));
        }
    }

    /**
     * Fallback method for placeOrder when Circuit Breaker opens or service calls fail.
     * Provides graceful degradation with meaningful error messages.
     * 
     * @param order The order being placed
     * @param throwable The exception that triggered the fallback
     * @return ResponseEntity with SERVICE_UNAVAILABLE status and error details
     */
    private ResponseEntity<?> placeOrderFallback(Order order, Throwable throwable) {
        _logger.error("Circuit breaker activated for order placement. Reason: {} - {}", 
                throwable.getClass().getSimpleName(), throwable.getMessage());
        
        String errorMessage = "Order placement service is temporarily unavailable. ";
        String serviceName = "downstream service";
        
        // Determine which service failed based on exception message
        String exceptionMessage = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        
        if (exceptionMessage.contains("product")) {
            serviceName = "Product Service";
        } else if (exceptionMessage.contains("customer")) {
            serviceName = "Customer Service";
        } else if (exceptionMessage.contains("inventory")) {
            serviceName = "Inventory Service";
        }
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", errorMessage + "Please try again in a few moments.",
                        "reason", serviceName + " is currently unavailable",
                        "timestamp", System.currentTimeMillis(),
                        "orderInfo", Map.of(
                                "skuCode", order.getSkuCode() != null ? order.getSkuCode() : "N/A",
                                "quantity", order.getQuantity() != null ? order.getQuantity() : 0,
                                "email", order.getEmail() != null ? order.getEmail() : "N/A"
                        ),
                        "suggestion", "Our engineering team has been notified. Please retry your order in 30 seconds."
                ));
    }
}
