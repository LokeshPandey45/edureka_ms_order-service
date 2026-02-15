package com.edureka.order.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "order") // MongoDB equivalent of @Table
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    @Id
    private String id; // MongoDB ObjectId
    private String orderNumber;
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
    private String email; // For notifications
}
