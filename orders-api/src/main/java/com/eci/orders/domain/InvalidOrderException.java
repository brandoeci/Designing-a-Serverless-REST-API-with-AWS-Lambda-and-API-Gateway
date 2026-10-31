package com.eci.orders.domain;

import java.util.List;

/**
 * Raised when input does not satisfy the rules of the Orders application.
 *
 * <p>It carries the list of violated rules, not an HTTP status code: deciding that a violated rule
 * becomes {@code 400 Bad Request} is a responsibility of the HTTP layer, not of the application.</p>
 */
public class InvalidOrderException extends RuntimeException {

    private final List<String> violations;

    public InvalidOrderException(String violation) {
        this(List.of(violation));
    }

    public InvalidOrderException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    /** The individual rules that were violated, in declaration order. */
    public List<String> violations() {
        return violations;
    }
}
