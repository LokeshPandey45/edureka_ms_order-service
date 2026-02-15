package com.edureka.order.client;

import com.edureka.order.dto.InventoryCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for Inventory Service.
 * Proxy pattern for inter-service communication.
 */
@FeignClient(name = "inventory-service", path = "/api/inventory")
public interface InventoryClient {

    @GetMapping("/check")
    ResponseEntity<InventoryCheckResponse> checkStock(
            @RequestParam("skuCode") String skuCode,
            @RequestParam("quantity") Integer quantity);
}
