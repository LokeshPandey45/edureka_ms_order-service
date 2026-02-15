package com.edureka.order.client;

import com.edureka.order.dto.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for Customer Service.
 * Proxy pattern for inter-service communication.
 */
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @GetMapping("/email/{email}")
    ResponseEntity<CustomerResponse> getCustomerByEmail(@PathVariable("email") String email);
}
