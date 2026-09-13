package in.pragati.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Business/API exception with a human-readable message and safe details. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public Map<String, Object> getDetails() { return details; }

    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException tooMany(String message) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message); }
    public static ApiException serviceUnavailable(String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message); }
}
