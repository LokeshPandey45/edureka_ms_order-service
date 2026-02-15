package com.edureka.order.client;

import com.edureka.order.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for Product Catalog Service.
 * Proxy pattern for inter-service communication.
 */
@FeignClient(name = "product_service", path = "/api/products")
public interface ProductClient {

    @GetMapping("/sku/{skuCode}")
    ResponseEntity<ProductResponse> getProductBySkuCode(@PathVariable("skuCode") String skuCode);
}
