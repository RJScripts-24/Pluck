package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.model.WorkspaceMember;
import dev.pluck.backend.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    @Autowired
    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal) {
            return principal.getUser();
        } else if (auth != null && auth.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTeam(@PathVariable String id) {
        Map<String, Object> details = teamService.getTeamDetails(id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("team", details);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateTeamPolicies(@PathVariable String id, @RequestBody Map<String, Object> body) {
        User actor = getAuthenticatedUser();
        Workspace updated = teamService.updateTeamPolicies(id, actor, body);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "team", updated,
                "message", "Team policies updated successfully"
        ));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> getTeamActivity(@PathVariable String id) {
        List<Map<String, Object>> activity = teamService.getTeamActivity(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "activity", activity
        ));
    }

    @PostMapping("/{id}/invites")
    public ResponseEntity<?> inviteMember(@PathVariable String id, @RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        String role = body != null ? body.getOrDefault("role", "Editor") : "Editor";

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "EMAIL_REQUIRED",
                    "message", "Colleague email address is required."
            ));
        }

        try {
            User actor = getAuthenticatedUser();
            WorkspaceMember member = teamService.inviteMember(id, actor, email, role);

            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("id", member.getUserId());
            memberMap.put("name", member.getName());
            memberMap.put("email", member.getEmail());
            memberMap.put("role", member.getRole());
            memberMap.put("status", member.getStatus());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "member", memberMap,
                    "message", "Invitation sent to " + email + " (Role: " + role + ")"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "VALIDATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @PatchMapping("/{id}/members/{userId}")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable String id,
            @PathVariable String userId,
            @RequestBody Map<String, String> body
    ) {
        String role = body != null ? body.get("role") : null;
        if (role == null || role.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "ROLE_REQUIRED",
                    "message", "Role is required."
            ));
        }

        try {
            User actor = getAuthenticatedUser();
            WorkspaceMember member = teamService.updateMemberRole(id, actor, userId, role);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "member", member,
                    "message", "Member role updated to " + role
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "MEMBER_NOT_FOUND",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "INVALID_ROLE_CHANGE",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable String id, @PathVariable String userId) {
        try {
            User actor = getAuthenticatedUser();
            boolean removed = teamService.removeMember(id, actor, userId);
            if (!removed) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "MEMBER_NOT_FOUND",
                        "message", "Member '" + userId + "' does not exist in workspace."
                ));
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Member removed from team workspace."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "CANNOT_REMOVE_MEMBER",
                    "message", e.getMessage()
            ));
        }
    }
}
