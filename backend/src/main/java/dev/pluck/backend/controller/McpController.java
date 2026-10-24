package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.McpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpService mcpService;
    private final UserRepository userRepository;

    @Autowired
    public McpController(McpService mcpService, UserRepository userRepository) {
        this.mcpService = mcpService;
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleMcp(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceHeader
    ) {
        User user = getAuthenticatedUser();
        String workspaceId = resolveWorkspaceId(workspaceHeader);
        Map<String, Object> result = mcpService.handleJsonRpc(body, user, workspaceId);
        return ResponseEntity.ok(result);
    }
}
