package com.eci.orders.application;

import com.eci.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * Storage port for orders.
 *
 * <p>The application depends on this interface, never on a concrete store. The workshop uses an
 * in-memory implementation; replacing it with DynamoDB would not require touching
 * {@link OrderService}, the Lambda handlers or the API Gateway configuration.</p>
 */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(String id);

    /** Orders of a customer, ordered by id. Returns an empty list when the customer has none. */
    List<Order> findByCustomerId(String customerId);

    /**
     * Removes an order.
     *
     * @return {@code true} if an order was removed, {@code false} if none had that id. The boolean
     *         keeps the "did it exist?" decision with the caller instead of making storage throw.
     */
    boolean deleteById(String id);
}
