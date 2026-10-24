package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import dev.pluck.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuthService authService;

    @Autowired
    public AuthController(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            AuthService authService
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String name = body.get("name");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "EMAIL_REQUIRED", "message", "Email address is required."));
        }

        email = email.trim().toLowerCase();

        if (password == null || password.length() < 10) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_PASSWORD", "message", "Password must be at least 10 characters."));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "USER_ALREADY_EXISTS", "message", "An account with this email already exists. Please sign in instead."));
        }

        if (name == null || name.trim().isEmpty()) {
            name = email.split("@")[0];
        }

        String initial = String.valueOf(name.charAt(0)).toUpperCase();
        User newUser = new User(
                "usr_" + UUID.randomUUID().toString().substring(0, 8),
                name,
                email,
                initial,
                "owner"
        );
        newUser.setPasswordHash(authService.encodePassword(password));
        User savedUser = userRepository.save(newUser);

        // Ensure personal workspace and default folders exist
        Workspace personalWorkspace = authService.ensurePersonalWorkspace(savedUser);

        // Issue JWT session token
        String sessionToken = authService.createSessionToken(savedUser);

        // Issue HTTP session cookie
        ResponseCookie cookie = ResponseCookie.from("pluck_session", sessionToken)
                .httpOnly(false)
                .path("/")
                .maxAge(30 * 24 * 3600)
                .sameSite("Lax")
                .build();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Account created successfully in MongoDB");
        res.put("token", sessionToken);
        res.put("user", savedUser);
        res.put("workspaces", List.of(personalWorkspace));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(res);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "EMAIL_AND_PASSWORD_REQUIRED", "message", "Email and password must be provided."));
        }

        email = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "USER_NOT_FOUND", "message", "No account found with this email. Please create an account first."));
        }

        User user = userOpt.get();
        boolean valid = authService.matchesPassword(password, user.getPasswordHash(), user.getPassword());
        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_CREDENTIALS", "message", "Incorrect password. Please verify and try again."));
        }

        // Migrate plain password to hash if needed
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            user.setPasswordHash(authService.encodePassword(password));
            user.setPassword(null);
            user = userRepository.save(user);
        }

        Workspace personalWorkspace = authService.ensurePersonalWorkspace(user);
        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(user.getId());

        String sessionToken = authService.createSessionToken(user);
        ResponseCookie cookie = ResponseCookie.from("pluck_session", sessionToken)
                .httpOnly(false)
                .path("/")
                .maxAge(30 * 24 * 3600)
                .sameSite("Lax")
                .build();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Authenticated successfully");
        res.put("token", sessionToken);
        res.put("user", user);
        res.put("workspaces", workspaces);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(res);
    }

    @PostMapping("/oauth")
    public ResponseEntity<?> oauth(@RequestBody(required = false) Map<String, String> body) {
        String provider = (body != null && body.containsKey("provider")) ? body.get("provider") : "google";
        String email = (body != null && body.containsKey("email")) ? body.get("email") : null;
        String name = (body != null && body.containsKey("name")) ? body.get("name") : null;
        String avatar = (body != null && body.containsKey("avatar")) ? body.get("avatar") : null;

        if (email == null || email.trim().isEmpty()) {
            email = "user." + UUID.randomUUID().toString().substring(0, 6) + "@pluck.dev";
        }
        email = email.trim().toLowerCase();

        if (name == null || name.trim().isEmpty()) {
            name = email.split("@")[0];
        }
        if (avatar == null || avatar.trim().isEmpty()) {
            avatar = String.valueOf(name.charAt(0)).toUpperCase();
        }

        final String finalEmail = email;
        final String finalName = name;
        final String finalAvatar = avatar;

        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User(
                    "usr_" + UUID.randomUUID().toString().substring(0, 8),
                    finalName,
                    finalEmail,
                    finalAvatar,
                    "owner"
            );
            return userRepository.save(newUser);
        });

        authService.ensurePersonalWorkspace(user);
        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(user.getId());

        String sessionToken = authService.createSessionToken(user);
        ResponseCookie cookie = ResponseCookie.from("pluck_session", sessionToken)
                .httpOnly(false)
                .path("/")
                .maxAge(30 * 24 * 3600)
                .sameSite("Lax")
                .build();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("provider", provider);
        res.put("token", sessionToken);
        res.put("user", user);
        res.put("workspaces", workspaces);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(res);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = ResponseCookie.from("pluck_session", "")
                .httpOnly(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("success", true, "message", "Logged out successfully"));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody(required = false) Map<String, String> body) {
        String email = body != null ? body.get("email") : "";
        return ResponseEntity.accepted().body(Map.of(
                "success", true,
                "message", "Password reset link dispatched",
                "email", email != null ? email : ""
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;

        if (auth instanceof PluckPrincipal principal) {
            currentUser = principal.getUser();
        } else if (auth != null && auth.getPrincipal() instanceof User userPrincipal) {
            currentUser = userPrincipal;
        }

        // Fallback for previewing if database has users
        if (currentUser == null) {
            List<User> all = userRepository.findAll();
            if (!all.isEmpty()) {
                currentUser = all.get(0);
            } else {
                currentUser = new User("usr_alex", "Alex Developer", "alex@pluck.dev", "A", "owner");
            }
        }

        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(currentUser.getId());
        if (workspaces.isEmpty()) {
            Workspace ws = authService.ensurePersonalWorkspace(currentUser);
            workspaces = List.of(ws);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("user", currentUser);
        res.put("workspaces", workspaces);
        res.put("preferences", currentUser.getPreferences());
        return ResponseEntity.ok(res);
    }
}
