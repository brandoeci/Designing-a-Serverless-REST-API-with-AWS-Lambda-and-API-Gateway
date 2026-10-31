package com.eci.orders.adapter.dto;

/**
 * Machine-readable error codes returned in {@link ErrorBody#getError()}.
 *
 * <p>They are part of the public contract of the API, which is why they live at the boundary and
 * are declared in exactly one place. In Architecture A they double as the regular expression that
 * API Gateway matches to pick a status code, so changing a value here is a contract change.</p>
 */
public final class ErrorCode {

    /** The requested order does not exist. Maps to 404. */
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

    /** The payload or a parameter broke a business rule. Maps to 400. */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    /** A required query parameter was absent. Maps to 400. */
    public static final String MISSING_QUERY_PARAMETER = "MISSING_QUERY_PARAMETER";

    /** The body was absent or was not valid JSON. Maps to 400. */
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    /** No resource matches the requested path. Maps to 404. */
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";

    /** The resource exists but does not support the requested method. Maps to 405. */
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

    /** Anything unexpected. Maps to 500 and never leaks internal details. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {
    }
}
