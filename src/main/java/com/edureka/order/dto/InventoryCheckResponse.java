package com.edureka.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Inventory check response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryCheckResponse {
    private Boolean inStock;
    private Integer availableQuantity;
}
