package com.eci.orders.adapter.dto;

import com.eci.orders.domain.Order;

import java.util.List;

/**
 * The JSON representation of an order at the API boundary.
 *
 * <p>Why a separate type instead of serializing {@link Order} directly? Because the wire format is
 * a published contract with clients, while {@code Order} is free to evolve with the business. The
 * mapping between both lives here, in one place.</p>
 *
 * <p>It is an old-fashioned mutable bean with a no-argument constructor rather than a record,
 * because in Architecture A the <em>AWS runtime</em> performs the serialization and the bean
 * convention is what every version of that serializer understands.</p>
 */
public class OrderView {

    private String id;
    private String customerId;
    private String product;
    private int quantity;

    public OrderView() {
    }

    public OrderView(String id, String customerId, String product, int quantity) {
        this.id = id;
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    public static OrderView from(Order order) {
        return new OrderView(order.id(), order.customerId(), order.product(), order.quantity());
    }

    public static List<OrderView> fromAll(List<Order> orders) {
        return orders.stream().map(OrderView::from).toList();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
