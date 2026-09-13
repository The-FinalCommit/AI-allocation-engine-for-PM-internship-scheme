package in.pragati.common;

import java.util.Map;

/** Standard error payload. Never contains stack traces or internal fingerprints. */
public record ErrorDto(int status, String error, String message, String requestId,
                       Map<String, Object> fieldErrors) {
}
