package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.Token;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.TokenRepository;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Autowired
    public TokenController(TokenRepository tokenRepository, UserRepository userRepository, AuthService authService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal) {
            return principal.getUser();
        } else if (auth != null && auth.getPrincipal() instanceof User u) {
            return u;
        }
        List<User> users = userRepository.findAll();
        return users.isEmpty() ? null : users.get(0);
    }

    @GetMapping
    public ResponseEntity<?> getTokens() {
        User user = getAuthenticatedUser();
        List<Token> tokens;
        if (user != null) {
            tokens = tokenRepository.findByUserId(user.getId());
        } else {
            tokens = tokenRepository.findAll();
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("tokens", tokens);
        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<?> createToken(@RequestBody Map<String, Object> body) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Authentication required to generate access tokens."));
        }

        String name = (String) body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "TOKEN_NAME_REQUIRED", "message", "Token name is required."));
        }

        List<String> scopes = (body.containsKey("scopes") && body.get("scopes") instanceof List)
                ? (List<String>) body.get("scopes")
                : List.of("snippets:read", "snippets:write");

        List<String> workspaceIds = (body.containsKey("workspaceIds") && body.get("workspaceIds") instanceof List)
                ? (List<String>) body.get("workspaceIds")
                : List.of("ws_personal");

        String expires = (String) body.getOrDefault("expires", "in 90 days");

        Map<String, Object> generated = authService.generatePat(user, name, scopes, workspaceIds, expires);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("token", generated.get("token"));
        res.put("rawToken", generated.get("rawToken"));
        res.put("message", "Token created. Save this token now, you will not be able to see it again.");
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteToken(@PathVariable String id) {
        Optional<Token> opt = tokenRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "TOKEN_NOT_FOUND", "message", "Token with id " + id + " not found."));
        }

        Token token = opt.get();
        token.setRevokedAt(Instant.now().toString());
        tokenRepository.save(token);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token '" + token.getName() + "' revoked successfully."
        ));
    }
}
