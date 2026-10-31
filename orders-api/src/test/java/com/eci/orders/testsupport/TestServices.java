package com.eci.orders.testsupport;

import com.eci.orders.application.OrderIdGenerator;
import com.eci.orders.application.OrderService;
import com.eci.orders.infrastructure.DemoData;
import com.eci.orders.infrastructure.InMemoryOrderRepository;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds application instances for tests.
 *
 * <p>Every test gets its own repository and a deterministic id generator, so tests cannot leak
 * state into each other and assertions can name the expected id. Production uses random ids for
 * the reasons explained in {@code UuidOrderIdGenerator}; that difference is exactly why the
 * generator is an injected port.</p>
 */
public final class TestServices {

    private TestServices() {
    }

    /** An application pre-loaded with the demo orders (ORD-1001, ORD-1002, ORD-1003). */
    public static OrderService seeded() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        DemoData.seed(repository);
        return new OrderService(repository, sequentialIds());
    }

    /** An application with no orders at all. */
    public static OrderService empty() {
        return new OrderService(new InMemoryOrderRepository(), sequentialIds());
    }

    /** Hands out ORD-2001, ORD-2002, ... so created orders have predictable identifiers. */
    public static OrderIdGenerator sequentialIds() {
        AtomicInteger next = new AtomicInteger(2001);
        return () -> "ORD-" + next.getAndIncrement();
    }
}
