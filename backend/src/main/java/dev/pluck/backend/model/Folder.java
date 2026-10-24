package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "folders")
public class Folder {

    @Id
    private String id;

    @Indexed
    private String workspaceId = "ws_personal";

    private String name;
    private String emoji = "📁";
    private String parentId;
    private int position = 0;
    private int count = 0;
    private String createdAt;

    public Folder() {
        this.createdAt = Instant.now().toString();
    }

    public Folder(String name, String emoji) {
        this.name = name;
        this.emoji = emoji;
        this.createdAt = Instant.now().toString();
    }

    public Folder(String id, String name, String emoji, int count, String workspaceId) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.count = count;
        this.workspaceId = workspaceId;
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
