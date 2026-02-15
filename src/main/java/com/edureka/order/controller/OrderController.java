package com.edureka.order.controller;

import com.edureka.order.event.OrderPlacedEvent;
import com.edureka.order.model.Order;
import com.edureka.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @PostMapping("/placeOrder")
    public ResponseEntity<?> placeOrder(@RequestBody Order order) {
        // 1. Save Order to MongoDB
        order.setOrderNumber(UUID.randomUUID().toString());
        orderRepository.save(order);

        // 2. Send Message to Kafka (Asynchronous)
        // Topic: "notificationTopic"
        kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber(), "user@example.com"));

        return ResponseEntity.ok().body("Order Placed Successfully");
    }
}