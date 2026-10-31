package com.eci.orders.infrastructure;

import com.eci.orders.application.OrderService;

/**
 * Wires the application together for the AWS runtime.
 *
 * <p>The service is held in a {@code static} field on purpose. Code outside the handler method runs
 * once per execution environment, so the wiring (and the seed data) is paid on cold start and
 * reused by every warm invocation. That same field is why in-memory data looks "almost persistent"
 * during a demo and then suddenly resets: it lives and dies with the container.</p>
 *
 * <p>Tests never touch the shared instance; they build an isolated one so that they cannot leak
 * state into each other.</p>
 */
public final class ApplicationFactory {

    private static final OrderService SHARED_INSTANCE = newSeededService();

    private ApplicationFactory() {
    }

    /** The per-container application instance used by the Lambda handlers. */
    public static OrderService orderService() {
        return SHARED_INSTANCE;
    }

    /** A brand new, independently seeded application instance. */
    public static OrderService newSeededService() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        DemoData.seed(repository);
        return new OrderService(repository, new UuidOrderIdGenerator());
    }
}
