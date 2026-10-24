package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.CliService;
import dev.pluck.backend.service.SnippetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/cli")
public class CliController {

    private final CliService cliService;
    private final UserRepository userRepository;

    @Autowired
    public CliController(CliService cliService, UserRepository userRepository) {
        this.cliService = cliService;
        this.userRepository = userRepository;
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

    private String resolveWorkspaceId(String headerWorkspace) {
        if (headerWorkspace != null && !headerWorkspace.isBlank()) {
            return headerWorkspace;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal && principal.getActiveWorkspaceId() != null) {
            return principal.getActiveWorkspaceId();
        }
        return "ws_personal";
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestParam(value = "since", required = false) String since,
            @RequestParam(value = "workspace", required = false) String workspaceParam,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceHeader
    ) {
        String wsId = (workspaceParam != null && !workspaceParam.isBlank())
                ? workspaceParam
                : resolveWorkspaceId(workspaceHeader);
        Map<String, Object> resp = cliService.sync(wsId, since);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/push")
    public ResponseEntity<?> push(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceHeader
    ) {
        try {
            User user = getAuthenticatedUser();
            String wsId = resolveWorkspaceId(workspaceHeader);
            Map<String, Object> result = cliService.push(wsId, user, body);
            return ResponseEntity.ok(result);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "SECRET_DETECTED", "message", se.getMessage()));
        } catch (SnippetService.VersionConflictException ve) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "VERSION_CONFLICT", "message", ve.getMessage(), "currentVersion", ve.getCurrentVersion()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "BAD_REQUEST", "message", ie.getMessage()));
        }
    }

    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(
            @RequestParam("ref") String ref,
            @RequestParam(value = "pm", required = false, defaultValue = "npm") String pm,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceHeader
    ) {
        try {
            String wsId = resolveWorkspaceId(workspaceHeader);
            Map<String, Object> resolved = cliService.resolve(wsId, ref, pm);
            return ResponseEntity.ok(resolved);
        } catch (NoSuchElementException ne) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", ne.getMessage()));
        }
    }

    @PostMapping("/pull")
    public ResponseEntity<?> pull(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceHeader
    ) {
        try {
            String wsId = resolveWorkspaceId(workspaceHeader);
            Map<String, Object> pulled = cliService.pull(wsId, body);
            return ResponseEntity.ok(pulled);
        } catch (NoSuchElementException ne) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", ne.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "BAD_REQUEST", "message", ie.getMessage()));
        }
    }
}
