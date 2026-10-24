package dev.pluck.backend.service;

import dev.pluck.backend.model.ActivityEvent;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.model.WorkspaceMember;
import dev.pluck.backend.repository.ActivityEventRepository;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceMemberRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class TeamService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ActivityEventRepository activityEventRepository;
    private final UserRepository userRepository;

    @Autowired
    public TeamService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            ActivityEventRepository activityEventRepository,
            UserRepository userRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.activityEventRepository = activityEventRepository;
        this.userRepository = userRepository;
    }

    public Workspace getOrCreateTeamWorkspace(String teamIdOrSlug) {
        String identifier = (teamIdOrSlug != null && !teamIdOrSlug.isBlank()) ? teamIdOrSlug : "acme-core-eng";

        Optional<Workspace> opt = workspaceRepository.findByIdAndDeletedAtIsNull(identifier)
                .or(() -> workspaceRepository.findBySlugAndDeletedAtIsNull(identifier))
                .or(() -> workspaceRepository.findBySlug(identifier));

        if (opt.isPresent()) {
            return opt.get();
        }

        // Initialize default team workspace "Acme Core Eng"
        Workspace team = new Workspace();
        team.setId("ws_acme_core");
        team.setName("Acme Core Eng");
        team.setSlug("acme-core-eng");
        team.setType("team");
        team.setOwnerUserId("usr_alex");
        team.setStrictSecretGating(true);
        team.setEditorsCanPublish(false);
        Workspace saved = workspaceRepository.save(team);

        // Seed initial members
        seedInitialTeamMembers(saved.getId());

        // Seed initial audit activity
        recordActivity(saved.getId(), "usr_alex", "Alex Developer", "team.created", saved.getId(),
                Map.of("message", "Created team workspace Acme Core Eng"));

        return saved;
    }

    private void seedInitialTeamMembers(String workspaceId) {
        List<WorkspaceMember> existing = memberRepository.findByWorkspaceId(workspaceId);
        if (!existing.isEmpty()) return;

        WorkspaceMember m1 = new WorkspaceMember(workspaceId, "usr_alex", "admin");
        m1.setName("Alex Developer (You)");
        m1.setEmail("alex@pluck.dev");
        m1.setStatus("active");
        memberRepository.save(m1);

        WorkspaceMember m2 = new WorkspaceMember(workspaceId, "usr_sarah", "editor");
        m2.setName("Sarah Connor");
        m2.setEmail("sarah@acme.corp");
        m2.setStatus("active");
        memberRepository.save(m2);

        WorkspaceMember m3 = new WorkspaceMember(workspaceId, "usr_leo", "viewer");
        m3.setName("Leo Chen");
        m3.setEmail("leo@acme.corp");
        m3.setStatus("active");
        memberRepository.save(m3);
    }

    public Map<String, Object> getTeamDetails(String teamIdOrSlug) {
        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);
        List<WorkspaceMember> members = memberRepository.findByWorkspaceId(team.getId());

        List<Map<String, Object>> memberList = new ArrayList<>();
        for (WorkspaceMember m : members) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getUserId() != null ? m.getUserId() : m.getId());
            item.put("name", m.getName() != null ? m.getName() : "Team Member");
            item.put("email", m.getEmail() != null ? m.getEmail() : "member@pluck.dev");
            item.put("role", capitalize(m.getRole()));
            item.put("status", m.getStatus() != null ? m.getStatus() : "active");
            item.put("isOwner", "usr_alex".equals(m.getUserId()) || (team.getOwnerUserId() != null && team.getOwnerUserId().equals(m.getUserId())));
            memberList.add(item);
        }

        List<Map<String, Object>> collections = List.of(
                Map.of("name", "auth-middleware", "emoji", "🔐", "count", 8, "description", "JWT verification, RBAC guards, and session decryptors."),
                Map.of("name", "aws-infrastructure", "emoji", "☁️", "count", 12, "description", "Boto3 client setups, DynamoDB pagination, and S3 uploaders."),
                Map.of("name", "database-caching", "emoji", "💾", "count", 5, "description", "Redis pipelines, Postgres connection poolers, and cache invalidators.")
        );

        Map<String, Object> result = new HashMap<>();
        result.put("id", team.getId());
        result.put("slug", team.getSlug());
        result.put("name", team.getName());
        result.put("type", team.getType());
        result.put("strictSecretGating", team.isStrictSecretGating());
        result.put("editorsCanPublish", team.isEditorsCanPublish());
        result.put("members", memberList);
        result.put("memberCount", memberList.size());
        result.put("collections", collections);
        return result;
    }

    public Workspace updateTeamPolicies(String teamIdOrSlug, User actor, Map<String, Object> updates) {
        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);

        if (updates.containsKey("name") && updates.get("name") instanceof String name) {
            team.setName(name);
        }
        if (updates.containsKey("strictSecretGating") && updates.get("strictSecretGating") instanceof Boolean b) {
            team.setStrictSecretGating(b);
        }
        if (updates.containsKey("editorsCanPublish") && updates.get("editorsCanPublish") instanceof Boolean b) {
            team.setEditorsCanPublish(b);
        }
        team.setUpdatedAt(Instant.now().toString());
        Workspace saved = workspaceRepository.save(team);

        String actorId = (actor != null) ? actor.getId() : "usr_alex";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "Alex Developer";
        recordActivity(team.getId(), actorId, actorName, "team.settings_updated", team.getId(), updates);

        return saved;
    }

    public WorkspaceMember inviteMember(String teamIdOrSlug, User actor, String email, String role) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email address is required.");
        }

        String validRole = (role != null && !role.isBlank()) ? role.trim().toLowerCase() : "editor";
        if (!List.of("admin", "editor", "viewer").contains(validRole)) {
            validRole = "editor";
        }

        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);

        // Check if member already exists
        Optional<WorkspaceMember> existing = memberRepository.findByWorkspaceIdAndEmail(team.getId(), email.trim());
        WorkspaceMember member;
        if (existing.isPresent()) {
            member = existing.get();
            member.setRole(validRole);
            member.setStatus("pending");
        } else {
            String userId = "usr_" + UUID.randomUUID().toString().substring(0, 8);
            String name = email.split("@")[0];
            member = new WorkspaceMember(team.getId(), userId, validRole);
            member.setEmail(email.trim());
            member.setName(name);
            member.setStatus("pending");
        }

        WorkspaceMember saved = memberRepository.save(member);

        String actorId = (actor != null) ? actor.getId() : "usr_alex";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "Alex Developer";
        recordActivity(team.getId(), actorId, actorName, "member.invited", saved.getUserId(),
                Map.of("email", email, "role", capitalize(validRole)));

        return saved;
    }

    public WorkspaceMember updateMemberRole(String teamIdOrSlug, User actor, String userId, String newRole) {
        if (newRole == null || newRole.trim().isEmpty()) {
            throw new IllegalArgumentException("Role is required.");
        }

        String cleanRole = newRole.trim().toLowerCase();
        if (!List.of("admin", "editor", "viewer").contains(cleanRole)) {
            throw new IllegalArgumentException("Invalid role: " + newRole + ". Allowed roles: Admin, Editor, Viewer.");
        }

        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);

        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(team.getId(), userId)
                .or(() -> memberRepository.findById(userId))
                .orElse(null);

        if (member == null) {
            throw new NoSuchElementException("Member not found in team workspace.");
        }

        // Invariant: Last Admin cannot be demoted
        if ("admin".equalsIgnoreCase(member.getRole()) && !"admin".equalsIgnoreCase(cleanRole)) {
            long adminCount = memberRepository.countByWorkspaceIdAndRoleIgnoreCase(team.getId(), "admin");
            if (adminCount <= 1) {
                throw new IllegalArgumentException("LAST_ADMIN: Cannot demote the last remaining admin in the workspace.");
            }
        }

        member.setRole(cleanRole);
        WorkspaceMember saved = memberRepository.save(member);

        String actorId = (actor != null) ? actor.getId() : "usr_alex";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "Alex Developer";
        recordActivity(team.getId(), actorId, actorName, "member.role_updated", member.getUserId(),
                Map.of("newRole", capitalize(cleanRole)));

        return saved;
    }

    public boolean removeMember(String teamIdOrSlug, User actor, String userId) {
        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);

        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(team.getId(), userId)
                .or(() -> memberRepository.findById(userId))
                .orElse(null);

        if (member == null) {
            return false;
        }

        // Invariant: Last Admin cannot be removed
        if ("admin".equalsIgnoreCase(member.getRole())) {
            long adminCount = memberRepository.countByWorkspaceIdAndRoleIgnoreCase(team.getId(), "admin");
            if (adminCount <= 1) {
                throw new IllegalArgumentException("LAST_ADMIN: Cannot remove the last remaining admin in the workspace.");
            }
        }

        memberRepository.delete(member);

        String actorId = (actor != null) ? actor.getId() : "usr_alex";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "Alex Developer";
        recordActivity(team.getId(), actorId, actorName, "member.removed", member.getUserId(),
                Map.of("removedUser", member.getName() != null ? member.getName() : userId));

        return true;
    }

    public List<Map<String, Object>> getTeamActivity(String teamIdOrSlug) {
        Workspace team = getOrCreateTeamWorkspace(teamIdOrSlug);
        List<ActivityEvent> events = activityEventRepository.findByWorkspaceIdOrderByCreatedAtDesc(team.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (ActivityEvent e : events) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", e.getId());
            map.put("user", e.getActorName() != null ? e.getActorName() : "System");
            map.put("action", formatAction(e.getType(), e.getMetadata()));
            map.put("target", e.getSnippetId());
            map.put("time", formatTimeAgo(e.getCreatedAt()));
            map.put("icon", getActionIcon(e.getType() != null ? e.getType() : ""));
            map.put("timestamp", e.getCreatedAt());
            list.add(map);
        }

        // Add default activity if empty
        if (list.isEmpty()) {
            list.add(Map.of("user", "Alex Developer", "action", "created snippet redis-ratelimit", "time", "2h ago", "icon", "green"));
            list.add(Map.of("user", "Sarah Connor", "action", "updated aws-s3-uploader v2", "time", "4h ago", "icon", "cyan"));
            list.add(Map.of("user", "Leo Chen", "action", "copied s3-presigned-upload via CLI", "time", "5h ago", "icon", "pink"));
        }

        return list;
    }

    private void recordActivity(String workspaceId, String userId, String userName, String action, String targetId, Map<String, Object> meta) {
        Map<String, Object> safeMeta = new HashMap<>();
        if (meta != null) {
            meta.forEach((k, v) -> safeMeta.put(k, v != null ? String.valueOf(v) : ""));
        }
        ActivityEvent event = new ActivityEvent(workspaceId, userId, userName, action, targetId, safeMeta);
        activityEventRepository.save(event);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "Member";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String formatAction(String action, Map<String, Object> meta) {
        if ("member.invited".equals(action)) {
            String role = meta != null && meta.containsKey("role") ? String.valueOf(meta.get("role")) : "Editor";
            String email = meta != null && meta.containsKey("email") ? String.valueOf(meta.get("email")) : "";
            return "invited " + email + " as " + role;
        }
        if ("member.role_updated".equals(action)) {
            String role = meta != null && meta.containsKey("newRole") ? String.valueOf(meta.get("newRole")) : "";
            return "updated role to " + role;
        }
        if ("member.removed".equals(action)) {
            String name = meta != null && meta.containsKey("removedUser") ? String.valueOf(meta.get("removedUser")) : "member";
            return "removed " + name + " from team";
        }
        if ("team.settings_updated".equals(action)) {
            return "updated team workspace policies";
        }
        return action;
    }

    private String getActionIcon(String action) {
        if (action.contains("created")) return "green";
        if (action.contains("updated")) return "cyan";
        if (action.contains("removed")) return "red";
        if (action.contains("invited")) return "yellow";
        return "purple";
    }

    private String formatTimeAgo(String createdAt) {
        if (createdAt == null) return "Recently";
        try {
            Instant t = Instant.parse(createdAt);
            long secs = Instant.now().getEpochSecond() - t.getEpochSecond();
            if (secs < 60) return "Just now";
            if (secs < 3600) return (secs / 60) + "m ago";
            if (secs < 86400) return (secs / 3600) + "h ago";
            return (secs / 86400) + "d ago";
        } catch (Exception e) {
            return "Recently";
        }
    }
}
