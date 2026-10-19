package com.reservas.common;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    /** Extra machine-readable data, exposed as top-level fields of the ProblemDetail. */
    public ApiException with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public Map<String, Object> getProperties() { return properties; }
}
