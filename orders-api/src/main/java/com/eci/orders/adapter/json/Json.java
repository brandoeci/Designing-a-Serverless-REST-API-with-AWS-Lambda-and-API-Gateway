package com.eci.orders.adapter.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON serialization used by the adapter layer.
 *
 * <p>The {@link ObjectMapper} is expensive to build and thread-safe once configured, so it is
 * created once per container and shared, which keeps it out of the request path after a cold
 * start.</p>
 *
 * <p>Unknown properties are ignored deliberately: a client that sends an extra field should not get
 * a 400, and this same mapper is used to read API Gateway events, which gain new fields over
 * time.</p>
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // A value the API itself produced cannot be serialized: that is a bug, not bad input.
            throw new IllegalStateException("Could not serialize " + value.getClass().getName(), e);
        }
    }

    /**
     * @throws MalformedRequestException if the text is not valid JSON for the requested type
     */
    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new MalformedRequestException("A JSON request body is required");
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new MalformedRequestException("Request body is not valid JSON");
        }
    }
}
