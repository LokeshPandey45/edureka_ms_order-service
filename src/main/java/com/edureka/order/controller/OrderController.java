package com.edureka.order.controller;

import com.edureka.order.model.Order;
import com.edureka.order.repository.OrderRepository;
import com.edureka.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private OrderService orderService;

    /**
     * Place a new order. Validates with Product, Customer, Inventory services (sync),
     * then publishes OrderPlacedEvent to Kafka for Payment and Inventory (async).
     * Returns 201 Created with the created order, or 400 for validation errors.
     */
    @PostMapping(value = {"", "/", "/placeOrder"})
    public ResponseEntity<?> placeOrder(@RequestBody Order order) {
        return orderService.placeOrder(order);
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
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Order not found for id: " + id)));
    }
}
