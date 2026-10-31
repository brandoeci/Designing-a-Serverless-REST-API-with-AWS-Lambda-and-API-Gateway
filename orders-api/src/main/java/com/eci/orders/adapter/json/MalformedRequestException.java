package com.eci.orders.adapter.json;

/**
 * Raised when a request body is absent or is not valid JSON.
 *
 * <p>This exception belongs to the adapter, not to the domain: "valid JSON" is a property of the
 * transport, and the application layer never sees a string of JSON at all.</p>
 */
public class MalformedRequestException extends RuntimeException {

    public MalformedRequestException(String message) {
        super(message);
    }
}
