package com.edureka.order.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Event published to Kafka when order is placed.
 * Consumed by Payment and Inventory services for async processing.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String orderId;
    private String email;
    private String skuCode;
    private Integer quantity;
    private BigDecimal amount;
}
