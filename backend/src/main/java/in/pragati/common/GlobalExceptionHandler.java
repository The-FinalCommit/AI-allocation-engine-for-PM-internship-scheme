package in.pragati.common;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import java.util.HashMap;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/** Global error model: every error is a safe, human-readable payload. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorDto(ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                        ex.getMessage(), requestId(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> validation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorDto(400, "Validation failed",
                "Please review the highlighted fields and try again.", requestId(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorDto(400, "Bad request",
                "The request could not be understood. Please try again.", requestId(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorDto(403, "Access denied",
                "You do not have permission to perform this action.", requestId(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorDto> uploadTooBig(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorDto(413, "File too large",
                "The uploaded file is larger than the 5 MB limit.", requestId(), null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> noResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(404, "Not found",
                "The requested resource does not exist.", requestId(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> generic(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto(500, "Something went wrong",
                        "We could not complete this request. Please try again.", requestId(), null));
    }

    private String requestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            Object id = servlet.getRequest().getAttribute("pragati.requestId");
            if (id != null) return id.toString();
        }
        return "unknown";
    }
}
