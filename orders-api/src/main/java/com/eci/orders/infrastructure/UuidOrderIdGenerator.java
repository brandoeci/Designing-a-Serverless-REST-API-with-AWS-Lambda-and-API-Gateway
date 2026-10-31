package com.eci.orders.infrastructure;

import com.eci.orders.application.OrderIdGenerator;

import java.util.UUID;

/**
 * Produces identifiers such as {@code ORD-9F3B1C7A}.
 *
 * <p>A counter ({@code ORD-1}, {@code ORD-2}, ...) would be wrong here: Lambda runs an unknown
 * number of concurrent execution environments, each with its own memory, so two containers would
 * happily hand out the same "next" number. A random identifier needs no shared state, which is
 * exactly the constraint a stateless function has to live with.</p>
 */
public class UuidOrderIdGenerator implements OrderIdGenerator {

    @Override
    public String nextId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
