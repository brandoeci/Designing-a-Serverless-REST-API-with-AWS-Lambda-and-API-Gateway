package com.eci.orders.application;

import com.eci.orders.domain.InvalidOrderException;
import com.eci.orders.domain.Order;
import com.eci.orders.domain.OrderNotFoundException;
import com.eci.orders.testsupport.TestServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checkpoint-1 question "can the application functionality be tested without AWS?" is answered
 * by this file: no AWS class, no HTTP, no server, no network.
 */
@DisplayName("OrderService (application use cases)")
class OrderServiceTest {

    private final OrderService service = TestServices.seeded();

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("creates a valid order and assigns it an identifier")
        void createsValidOrder() {
            Order created = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

            assertEquals("ORD-2001", created.id());
            assertEquals("CUST-42", created.customerId());
            assertEquals("MacBook Air", created.product());
            assertEquals(1, created.quantity());
        }

        @Test
        @DisplayName("stores the order so it can be retrieved afterwards")
        void storesTheOrder() {
            Order created = service.createOrder(new CreateOrderRequest("CUST-99", "Monitor", 3));

            assertEquals(created, service.getOrder(created.id()));
        }

        @Test
        @DisplayName("trims surrounding whitespace")
        void trimsInput() {
            Order created = service.createOrder(new CreateOrderRequest("  CUST-42  ", "  Mouse  ", 1));

            assertEquals("CUST-42", created.customerId());
            assertEquals("Mouse", created.product());
        }

        @Test
        @DisplayName("rejects a null request")
        void rejectsNullRequest() {
            assertThrows(InvalidOrderException.class, () -> service.createOrder(null));
        }

        @Test
        @DisplayName("reports every violation of an empty request at once")
        void reportsAllViolations() {
            InvalidOrderException failure = assertThrows(InvalidOrderException.class,
                    () -> service.createOrder(new CreateOrderRequest(null, null, null)));

            assertEquals(3, failure.violations().size());
            assertTrue(failure.getMessage().contains("customerId"));
            assertTrue(failure.getMessage().contains("product"));
            assertTrue(failure.getMessage().contains("quantity"));
        }

        @Test
        @DisplayName("rejects a blank customerId")
        void rejectsBlankCustomer() {
            assertThrows(InvalidOrderException.class,
                    () -> service.createOrder(new CreateOrderRequest("   ", "Mouse", 1)));
        }

        @Test
        @DisplayName("rejects a zero or negative quantity")
        void rejectsNonPositiveQuantity() {
            assertThrows(InvalidOrderException.class,
                    () -> service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 0)));
            assertThrows(InvalidOrderException.class,
                    () -> service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", -5)));
        }

        @Test
        @DisplayName("rejects an absurd quantity")
        void rejectsHugeQuantity() {
            assertThrows(InvalidOrderException.class,
                    () -> service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 10_000)));
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("returns an existing order")
        void returnsExistingOrder() {
            Order order = service.getOrder("ORD-1001");

            assertEquals(new Order("ORD-1001", "CUST-42", "MacBook Air", 1), order);
        }

        @Test
        @DisplayName("fails with OrderNotFoundException when the order does not exist")
        void failsForUnknownOrder() {
            OrderNotFoundException failure =
                    assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-999"));

            assertEquals("ORD-999", failure.orderId());
            assertEquals("Order ORD-999 does not exist", failure.getMessage());
        }

        @Test
        @DisplayName("rejects a blank identifier as invalid input, not as a missing order")
        void rejectsBlankId() {
            assertThrows(InvalidOrderException.class, () -> service.getOrder("  "));
            assertThrows(InvalidOrderException.class, () -> service.getOrder(null));
        }
    }

    @Nested
    @DisplayName("findOrdersByCustomer")
    class FindOrdersByCustomer {

        @Test
        @DisplayName("returns every order of the customer, ordered by id")
        void returnsOrdersOfCustomer() {
            List<Order> orders = service.findOrdersByCustomer("CUST-42");

            assertEquals(List.of("ORD-1001", "ORD-1002"), orders.stream().map(Order::id).toList());
        }

        @Test
        @DisplayName("includes orders created during the same session")
        void includesNewOrders() {
            service.createOrder(new CreateOrderRequest("CUST-7", "Trackpad", 1));

            assertEquals(2, service.findOrdersByCustomer("CUST-7").size());
        }

        @Test
        @DisplayName("returns an empty list for a customer with no orders")
        void returnsEmptyListForUnknownCustomer() {
            assertTrue(service.findOrdersByCustomer("CUST-100").isEmpty());
        }

        @Test
        @DisplayName("rejects a blank customer identifier")
        void rejectsBlankCustomerId() {
            assertThrows(InvalidOrderException.class, () -> service.findOrdersByCustomer(" "));
        }
    }

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @Test
        @DisplayName("deletes an existing order, which can then no longer be retrieved")
        void deletesExistingOrder() {
            service.deleteOrder("ORD-1001");

            assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-1001"));
        }

        @Test
        @DisplayName("fails when the order does not exist")
        void failsDeletingUnknownOrder() {
            OrderNotFoundException failure =
                    assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-999"));

            assertEquals("ORD-999", failure.orderId());
        }

        @Test
        @DisplayName("is not idempotent: deleting twice fails the second time")
        void failsDeletingTwice() {
            service.deleteOrder("ORD-1001");

            assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-1001"));
        }

        @Test
        @DisplayName("rejects a blank identifier")
        void rejectsBlankId() {
            assertThrows(InvalidOrderException.class, () -> service.deleteOrder(" "));
            assertThrows(InvalidOrderException.class, () -> service.deleteOrder(null));
        }

        @Test
        @DisplayName("leaves the other orders of the same customer untouched")
        void deletesOnlyTheRequestedOrder() {
            service.deleteOrder("ORD-1001");

            assertEquals(List.of("ORD-1002"),
                    service.findOrdersByCustomer("CUST-42").stream().map(Order::id).toList());
        }
    }
}
