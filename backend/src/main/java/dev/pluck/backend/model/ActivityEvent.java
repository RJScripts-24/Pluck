package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "activity_events")
@CompoundIndex(name = "ws_created_idx", def = "{'workspaceId': 1, 'createdAt': -1}")
public class ActivityEvent {

    @Id
    private String id;

    @Indexed
    private String workspaceId;

    private String actorId;
    private String actorName = "Alex Developer";
    private String type;
    private String snippetId;
    private String folderId;
    private Map<String, Object> metadata = new HashMap<>();
    private String createdAt;

    public ActivityEvent() {
        this.createdAt = Instant.now().toString();
    }

    public ActivityEvent(String workspaceId, String actorId, String actorName, String type, String snippetId, Map<String, Object> metadata) {
        this.workspaceId = workspaceId;
        this.actorId = actorId;
        this.actorName = actorName != null ? actorName : "User";
        this.type = type;
        this.snippetId = snippetId;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSnippetId() { return snippetId; }
    public void setSnippetId(String snippetId) { this.snippetId = snippetId; }

    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
