package com.eci.orders.adapter.dto;

/**
 * The single error representation of the API:
 *
 * <pre>{@code
 * { "error": "ORDER_NOT_FOUND", "message": "Order ORD-999 does not exist" }
 * }</pre>
 *
 * <p>{@code error} is a stable machine-readable code clients may branch on; {@code message} is
 * human-readable and may change. Both architectures produce this same shape, so a client cannot
 * tell which integration type served it.</p>
 */
public class ErrorBody {

    private String error;
    private String message;

    public ErrorBody() {
    }

    public ErrorBody(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
