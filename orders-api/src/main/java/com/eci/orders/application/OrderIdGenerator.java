package com.eci.orders.application;

/**
 * Supplies identifiers for new orders.
 *
 * <p>Injected instead of hardcoded so that tests can be deterministic and so that the id strategy
 * can change without touching the service.</p>
 */
@FunctionalInterface
public interface OrderIdGenerator {

    String nextId();
}
