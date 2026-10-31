package com.eci.orders.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Order (domain invariants)")
class OrderTest {

    @Test
    @DisplayName("keeps the values it was created with")
    void holdsItsValues() {
        Order order = new Order("ORD-1", "CUST-1", "MacBook Air", 2);

        assertEquals("ORD-1", order.id());
        assertEquals("CUST-1", order.customerId());
        assertEquals("MacBook Air", order.product());
        assertEquals(2, order.quantity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("cannot be created with a blank id")
    void rejectsBlankId(String id) {
        assertThrows(InvalidOrderException.class, () -> new Order(id, "CUST-1", "Mouse", 1));
    }

    @Test
    @DisplayName("cannot be created with a null customer")
    void rejectsNullCustomer() {
        assertThrows(InvalidOrderException.class, () -> new Order("ORD-1", null, "Mouse", 1));
    }

    @Test
    @DisplayName("cannot be created with a blank product")
    void rejectsBlankProduct() {
        assertThrows(InvalidOrderException.class, () -> new Order("ORD-1", "CUST-1", " ", 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("cannot be created with a non-positive quantity")
    void rejectsNonPositiveQuantity(int quantity) {
        assertThrows(InvalidOrderException.class, () -> new Order("ORD-1", "CUST-1", "Mouse", quantity));
    }

    @Test
    @DisplayName("two orders with the same values are equal (value semantics)")
    void hasValueSemantics() {
        assertEquals(new Order("ORD-1", "CUST-1", "Mouse", 1), new Order("ORD-1", "CUST-1", "Mouse", 1));
    }
}
