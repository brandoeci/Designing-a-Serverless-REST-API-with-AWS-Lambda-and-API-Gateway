package com.eci.orders.infrastructure;

import com.eci.orders.application.OrderRepository;
import com.eci.orders.domain.Order;

/**
 * Seed orders, so that read operations have something to return right after a cold start.
 *
 * <p>The workshop does not require a database but does require {@code GET /orders/{id}} to be
 * demonstrable, and nothing created by a previous invocation can be relied upon. Seeding when the
 * container starts is the honest way to get predictable demo data out of ephemeral storage.</p>
 */
public final class DemoData {

    public static final String SEEDED_CUSTOMER = "CUST-42";

    private DemoData() {
    }

    public static void seed(OrderRepository repository) {
        repository.save(new Order("ORD-1001", SEEDED_CUSTOMER, "MacBook Air", 1));
        repository.save(new Order("ORD-1002", SEEDED_CUSTOMER, "Magic Keyboard", 2));
        repository.save(new Order("ORD-1003", "CUST-7", "Keyboard", 2));
    }
}
