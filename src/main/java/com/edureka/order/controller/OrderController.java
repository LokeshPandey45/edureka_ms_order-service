package com.edureka.order.controller;

import com.edureka.order.event.OrderPlacedEvent;
import com.edureka.order.model.Order;
import com.edureka.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public static final Logger _logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    /**
     * Place a new order. Persists to DB and publishes OrderPlacedEvent to Kafka.
     * Returns 201 Created with the created order, or 400 for validation errors.
     */
    @PostMapping(value = {"", "/", "/placeOrder"})
    public ResponseEntity<?> placeOrder(@RequestBody Order order) {
        if (order == null) {
            _logger.warn("Order is null");
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
        if (order.getPrice() != null && order.getPrice().signum() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order price must be non-negative"));
        }

        order.setId(order.getId() != null && !order.getId().isBlank() ? order.getId() : UUID.randomUUID().toString());
        order.setOrderNumber(UUID.randomUUID().toString());
        Order saved = orderRepository.save(order);
        _logger.info("Order placed successfully, order number: {}", saved.getOrderNumber());

        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(saved.getOrderNumber(),
                saved.getEmail() != null ? saved.getEmail() : "user@example.com"));

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Get all orders. Optional filter by email.
     * Returns 200 OK with list (empty list if none or no match).
     */
    @GetMapping(value = {"", "/", "/all"})
    public ResponseEntity<?> getAllOrders(
            @RequestParam(required = false) String email) {
        _logger.info("Getting all orders" + (email != null && !email.isBlank() ? " for email: " + email : ""));
        List<Order> orders = email != null && !email.isBlank()
                ? orderRepository.findByEmail(email)
                : orderRepository.findAll();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get order by ID (path variable). Returns 200 with order or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order id is required"));
        }
        _logger.info("Getting order with id: {}", id);
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found for id: " + id)));
    }

    /**
     * Get order by order number. Returns 200 with order or 404 Not Found.
     */
    @GetMapping("/orderNumber/{orderNumber}")
    public ResponseEntity<?> getOrderByOrderNumber(@PathVariable String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order number is required"));
        }
        _logger.info("Getting order with order number: {}", orderNumber);
        return orderRepository.findByOrderNumber(orderNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found for order number: " + orderNumber)));
    }

    /**
     * Update an existing order by ID.
     * Returns 200 OK with updated order, 404 if not found, 400 for validation errors.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateOrder(@PathVariable String id, @RequestBody Order order) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order id is required"));
        }
        if (order == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order body is required"));
        }
        if (order.getQuantity() != null && order.getQuantity() <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order quantity must be positive"));
        }
        if (order.getPrice() != null && order.getPrice().signum() < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order price must be non-negative"));
        }

        Optional<Order> existing = orderRepository.findById(id);
        if (existing.isEmpty()) {
            _logger.warn("Order not found for update: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found for id: " + id));
        }

        Order toUpdate = existing.get();
        if (order.getOrderNumber() != null) toUpdate.setOrderNumber(order.getOrderNumber());
        if (order.getSkuCode() != null) toUpdate.setSkuCode(order.getSkuCode());
        if (order.getPrice() != null) toUpdate.setPrice(order.getPrice());
        if (order.getQuantity() != null) toUpdate.setQuantity(order.getQuantity());
        if (order.getEmail() != null) toUpdate.setEmail(order.getEmail());

        Order updated = orderRepository.save(toUpdate);
        _logger.info("Order updated successfully: {}", id);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an order by ID.
     * Returns 204 No Content on success, 404 if not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOrder(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Order id is required"));
        }
        if (!orderRepository.existsById(id)) {
            _logger.warn("Order not found for delete: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found for id: " + id));
        }
        orderRepository.deleteById(id);
        _logger.info("Order deleted successfully: {}", id);
        return ResponseEntity.noContent().build();
    }
}
