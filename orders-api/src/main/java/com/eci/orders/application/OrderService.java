package com.eci.orders.application;

import com.eci.orders.domain.InvalidOrderException;
import com.eci.orders.domain.Order;
import com.eci.orders.domain.OrderNotFoundException;

import java.util.List;
import java.util.Objects;

/**
 * The use cases of the Orders application.
 *
 * <p>This class is the point of Part I of the workshop: it is ordinary Java. It does not depend on
 * a web server, on Spring, on API Gateway or on the AWS SDK, so it can be exercised entirely from
 * JUnit and reused unchanged by both architectures explored in the workshop.</p>
 *
 * <p>Outcomes are reported as return values and domain exceptions. Translating those outcomes into
 * HTTP status codes happens exactly once per architecture, in the adapter.</p>
 */
public class OrderService {

    private final OrderRepository repository;
    private final OrderIdGenerator idGenerator;

    public OrderService(OrderRepository repository, OrderIdGenerator idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    /**
     * Creates and stores a new order.
     *
     * @throws InvalidOrderException if the request is incomplete or violates a business rule
     */
    public Order createOrder(CreateOrderRequest request) {
        CreateOrderRequest valid = CreateOrderValidator.validate(request);
        Order order = new Order(idGenerator.nextId(), valid.customerId(), valid.product(), valid.quantity());
        repository.save(order);
        return order;
    }

    /**
     * @throws InvalidOrderException  if no usable identifier was supplied
     * @throws OrderNotFoundException if no order has that identifier
     */
    public Order getOrder(String id) {
        String orderId = requireIdentifier(id, "id");
        return repository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Orders belonging to a customer.
     *
     * <p>An unknown customer is not an error: the answer to "which orders does this customer have?"
     * is legitimately "none". That is why this method returns an empty list instead of throwing,
     * and why the API answers 200 with {@code []} rather than 404.</p>
     *
     * @throws InvalidOrderException if no usable customer identifier was supplied
     */
    public List<Order> findOrdersByCustomer(String customerId) {
        return repository.findByCustomerId(requireIdentifier(customerId, "customerId"));
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException(name + " is required and must not be blank");
        }
        return value.trim();
    }
}
