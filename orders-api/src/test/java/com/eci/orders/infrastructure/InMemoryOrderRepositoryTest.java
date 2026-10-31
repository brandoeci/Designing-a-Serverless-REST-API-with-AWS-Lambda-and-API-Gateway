package com.eci.orders.infrastructure;

import com.eci.orders.domain.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryOrderRepository")
class InMemoryOrderRepositoryTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

    @Test
    @DisplayName("stores and returns an order by id")
    void savesAndFinds() {
        Order order = new Order("ORD-1", "CUST-1", "Mouse", 1);

        repository.save(order);

        assertEquals(Optional.of(order), repository.findById("ORD-1"));
    }

    @Test
    @DisplayName("returns an empty optional for an unknown id")
    void findsNothingForUnknownId() {
        assertTrue(repository.findById("ORD-404").isEmpty());
    }

    @Test
    @DisplayName("saving the same id twice replaces the order instead of duplicating it")
    void replacesOnSameId() {
        repository.save(new Order("ORD-1", "CUST-1", "Mouse", 1));
        repository.save(new Order("ORD-1", "CUST-1", "Mouse", 5));

        assertEquals(1, repository.count());
        assertEquals(5, repository.findById("ORD-1").orElseThrow().quantity());
    }

    @Test
    @DisplayName("filters by customer and sorts by id")
    void filtersByCustomer() {
        repository.save(new Order("ORD-3", "CUST-1", "Monitor", 1));
        repository.save(new Order("ORD-1", "CUST-1", "Mouse", 1));
        repository.save(new Order("ORD-2", "CUST-2", "Keyboard", 1));

        assertEquals(List.of("ORD-1", "ORD-3"),
                repository.findByCustomerId("CUST-1").stream().map(Order::id).toList());
    }

    @Test
    @DisplayName("seeded demo data is present after seeding")
    void seedsDemoData() {
        DemoData.seed(repository);

        assertEquals(3, repository.count());
        assertEquals(2, repository.findByCustomerId(DemoData.SEEDED_CUSTOMER).size());
    }
}
