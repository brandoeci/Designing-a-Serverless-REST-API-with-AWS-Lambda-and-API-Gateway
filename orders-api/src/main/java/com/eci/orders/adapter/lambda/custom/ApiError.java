package com.eci.orders.adapter.lambda.custom;

import com.eci.orders.adapter.dto.ErrorBody;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.adapter.json.Json;
import com.eci.orders.domain.InvalidOrderException;
import com.eci.orders.domain.OrderNotFoundException;

/**
 * The only way Architecture A can report a failure.
 *
 * <p>With a non-proxy (custom) integration, Lambda has no notion of a status code. A failed
 * invocation is reported to API Gateway as:</p>
 *
 * <pre>{@code
 * { "errorMessage": "...", "errorType": "...", "stackTrace": [...] }
 * }</pre>
 *
 * <p>API Gateway then picks an integration response by matching a regular expression against
 * {@code errorMessage}. So the trick used here is to make {@code errorMessage} <em>be</em> the JSON
 * error body of the API: the integration response template copies it out unchanged, and the
 * selection pattern matches on the error code inside it.</p>
 *
 * <p>This class is the clearest illustration of the cost of Architecture A: the business code stays
 * clean, but the handler has to encode a convention that only makes sense to API Gateway.</p>
 */
public class ApiError extends RuntimeException {

    private ApiError(String errorBodyAsJson) {
        super(errorBodyAsJson);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(Json.write(new ErrorBody(code, message)));
    }

    public static ApiError notFound(OrderNotFoundException cause) {
        return of(ErrorCode.ORDER_NOT_FOUND, cause.getMessage());
    }

    public static ApiError validation(InvalidOrderException cause) {
        return of(ErrorCode.VALIDATION_ERROR, cause.getMessage());
    }

    /** The stack trace is noise here: the message is a contract, not a diagnostic. */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
