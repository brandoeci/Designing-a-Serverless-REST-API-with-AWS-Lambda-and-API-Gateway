package com.eci.orders.infrastructure;

import com.eci.orders.application.OrderRepository;
import com.eci.orders.domain.Order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link OrderRepository}.
 *
 * <p><strong>This is not persistence.</strong> A Lambda execution environment is ephemeral: the map
 * survives only while the container that created it stays warm, it is not shared with other
 * concurrent containers, and it disappears when AWS recycles the environment. The workshop
 * explicitly allows this; a production system would implement the same port over DynamoDB.</p>
 *
 * <p>A {@link ConcurrentHashMap} is used because a warm container can be reused by several
 * invocations and the runtime makes no promise about which thread serves them.</p>
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        ordersById.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return ordersById.values().stream()
                .filter(order -> order.customerId().equals(customerId))
                .sorted(Comparator.comparing(Order::id))
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return ordersById.remove(id) != null;
    }

    /** Number of orders currently held. Used by tests and diagnostics. */
    public int count() {
        return ordersById.size();
    }
}
