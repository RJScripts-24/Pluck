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

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Autowired
    public WorkspaceController(
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            AuthService authService
    ) {
        this.workspaceRepository = workspaceRepository;
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
    public ResponseEntity<?> getWorkspaces() {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Authentication required to list workspaces."
            ));
        }

        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(user.getId());
        if (workspaces.isEmpty()) {
            Workspace defaultWs = authService.ensurePersonalWorkspace(user);
            workspaces = List.of(defaultWs);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "workspaces", workspaces
        ));
    }

    @PostMapping
    public ResponseEntity<?> createWorkspace(@RequestBody Map<String, String> body) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Authentication required to create workspace."
            ));
        }

        String name = body != null ? body.get("name") : null;
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "NAME_REQUIRED",
                    "message", "Workspace name is required."
            ));
        }

        name = name.trim();
        String slug = "ws_" + name.toLowerCase().replaceAll("[^a-z0-9]", "") + "_" + UUID.randomUUID().toString().substring(0, 4);

        Workspace ws = new Workspace();
        ws.setId("ws_" + UUID.randomUUID().toString().substring(0, 8));
        ws.setName(name);
        ws.setSlug(slug);
        ws.setType("personal");
        ws.setOwnerUserId(user.getId());
        ws.setEditorsCanPublish(true);
        ws.setStrictSecretGating(false);

        Workspace saved = workspaceRepository.save(ws);
        authService.seedDefaultFolders(saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "workspace", saved,
                "message", "Workspace created successfully."
        ));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> renameWorkspace(@PathVariable String id, @RequestBody Map<String, String> body) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Authentication required to rename workspace."
            ));
        }

        String name = body != null ? body.get("name") : null;
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "NAME_REQUIRED",
                    "message", "New workspace name is required."
            ));
        }

        Optional<Workspace> opt = workspaceRepository.findById(id);
        if (opt.isEmpty()) {
            opt = workspaceRepository.findBySlug(id);
        }
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "NOT_FOUND",
                    "message", "Workspace not found."
            ));
        }

        Workspace ws = opt.get();
        if (ws.getOwnerUserId() != null && !ws.getOwnerUserId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "You do not have permission to rename this workspace."
            ));
        }

        ws.setName(name.trim());
        ws.setUpdatedAt(Instant.now().toString());
        Workspace updated = workspaceRepository.save(ws);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "workspace", updated,
                "message", "Workspace renamed successfully."
        ));
    }
}
