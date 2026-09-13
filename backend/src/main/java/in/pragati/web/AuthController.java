package in.pragati.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.pragati.common.ApiException;
import in.pragati.security.AuthUser;
import in.pragati.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Sign in and inspect the current session.")
public class AuthController {

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password; returns a JWT and the user.")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        AuthService.LoginResult res = auth.login(req.email(), req.password());
        return Map.of("token", res.token(), "user", res.user());
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user (from the token, never the client).")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser me) {
        if (me == null) throw ApiException.notFound("Not signed in.");
        return Map.of("id", me.id(), "email", me.email(), "name", me.name(), "role", me.role());
    }
}
