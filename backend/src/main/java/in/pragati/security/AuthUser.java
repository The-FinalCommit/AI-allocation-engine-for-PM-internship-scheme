package in.pragati.security;

/** Authenticated principal carried in the security context (never from the client). */
public record AuthUser(Long id, String email, String name, String role) {
}
