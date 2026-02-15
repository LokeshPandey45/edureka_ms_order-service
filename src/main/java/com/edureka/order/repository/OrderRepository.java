package com.edureka.order.repository;

import com.edureka.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    // Custom finders can be added here if needed, e.g.:
    // List<Order> findByEmail(String email);
}