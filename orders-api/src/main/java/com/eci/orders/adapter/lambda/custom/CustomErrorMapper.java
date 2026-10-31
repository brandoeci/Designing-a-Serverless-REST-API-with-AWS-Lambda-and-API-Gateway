package com.eci.orders.adapter.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.domain.InvalidOrderException;
import com.eci.orders.domain.OrderNotFoundException;

/**
 * Maps an application outcome to the error document that API Gateway will translate into a status
 * code in Architecture A.
 *
 * <p>It plays the same role that {@code HttpResponses} plays in Architecture B: one place where the
 * translation from "what happened" to "what the client is told" is decided. Keeping it in one class
 * is what stops four handlers from each inventing their own error codes.</p>
 *
 * <p>The catch-all matters more than it looks. With a custom integration, an unmapped Lambda failure
 * falls through to the <em>default</em> integration response, which is the success one, so the
 * client would receive {@code 200 OK} with a Lambda error envelope as the body. Turning every
 * unexpected exception into an {@code INTERNAL_ERROR} document gives API Gateway something
 * deterministic to match on.</p>
 */
final class CustomErrorMapper {

    private CustomErrorMapper() {
    }

    static ApiError toApiError(RuntimeException failure, Context context) {
        if (failure instanceof ApiError alreadyMapped) {
            return alreadyMapped;
        }
        if (failure instanceof OrderNotFoundException notFound) {
            return ApiError.notFound(notFound);
        }
        if (failure instanceof InvalidOrderException invalid) {
            return ApiError.validation(invalid);
        }
        // A bug or an unavailable dependency: log it for CloudWatch, tell the client nothing.
        log(context, "Unhandled failure: " + failure);
        return ApiError.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(message);
        } else {
            System.err.println(message);
        }
    }
}
