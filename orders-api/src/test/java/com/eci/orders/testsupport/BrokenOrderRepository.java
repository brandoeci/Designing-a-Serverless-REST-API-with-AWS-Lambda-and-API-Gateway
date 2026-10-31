package com.eci.orders.testsupport;

import com.eci.orders.application.OrderRepository;
import com.eci.orders.application.OrderService;
import com.eci.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * A store that fails the way a real one does when its backing service is unavailable.
 *
 * <p>Used to prove that an unexpected failure becomes a 500 with a generic message instead of
 * leaking a Java exception to the client.</p>
 */
public class BrokenOrderRepository implements OrderRepository {

    public static OrderService service() {
        return new OrderService(new BrokenOrderRepository(), TestServices.sequentialIds());
    }

    @Override
    public void save(Order order) {
        throw new IllegalStateException("storage unavailable");
    }

    @Override
    public Optional<Order> findById(String id) {
        throw new IllegalStateException("storage unavailable");
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        throw new IllegalStateException("storage unavailable");
    }

    @Override
    public boolean deleteById(String id) {
        throw new IllegalStateException("storage unavailable");
    }
}
