package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "workspace_members")
@CompoundIndex(name = "ws_user_idx", def = "{'workspaceId': 1, 'userId': 1}", unique = true)
public class WorkspaceMember {

    @Id
    private String id;

    @Indexed
    private String workspaceId;

    @Indexed
    private String userId;

    private String role = "viewer"; // "admin" | "editor" | "viewer"
    private String name;
    private String email;
    private String status = "active"; // "active" | "pending"
    private String createdAt;

    public WorkspaceMember() {
        this.createdAt = Instant.now().toString();
    }

    public WorkspaceMember(String workspaceId, String userId, String role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role != null ? role : "viewer";
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
