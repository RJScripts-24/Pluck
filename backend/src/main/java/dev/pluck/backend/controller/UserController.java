package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import dev.pluck.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuthService authService;

    @Autowired
    public UserController(UserRepository userRepository, WorkspaceRepository workspaceRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.authService = authService;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal) {
            return principal.getUser();
        } else if (auth != null && auth.getPrincipal() instanceof User u) {
            return u;
        }
        // Fallback for unauthenticated access or preview
        List<User> users = userRepository.findAll();
        if (!users.isEmpty()) {
            return users.get(0);
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> getMe() {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "User session required."));
        }

        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(user.getId());
        if (workspaces.isEmpty()) {
            workspaces = List.of(authService.ensurePersonalWorkspace(user));
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("user", user);
        res.put("workspaces", workspaces);
        res.put("preferences", user.getPreferences());
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@RequestBody Map<String, Object> updates) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "User session required."));
        }

        Map<String, Object> prefs = user.getPreferences();
        if (prefs == null) {
            prefs = new HashMap<>();
        }

        if (updates.containsKey("packageManager")) {
            prefs.put("packageManager", updates.get("packageManager"));
        }
        if (updates.containsKey("pythonPackageManager")) {
            prefs.put("pythonPackageManager", updates.get("pythonPackageManager"));
        }
        if (updates.containsKey("theme")) {
            prefs.put("theme", updates.get("theme"));
        }
        if (updates.containsKey("editorKeymap")) {
            prefs.put("editorKeymap", updates.get("editorKeymap"));
        }

        user.setPreferences(prefs);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("success", true, "preferences", user.getPreferences()));
    }

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences() {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "User session required."));
        }
        return ResponseEntity.ok(Map.of("success", true, "preferences", user.getPreferences()));
    }

    @PatchMapping
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "User session required."));
        }

        if (body.containsKey("name") && body.get("name") != null) {
            user.setName(body.get("name").trim());
        }
        if (body.containsKey("avatar") && body.get("avatar") != null) {
            user.setAvatar(body.get("avatar").trim());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "user", saved));
    }
}
