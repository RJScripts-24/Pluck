package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "workspaces")
public class Workspace {

    @Id
    private String id;
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String type = "personal"; // "personal" | "team"
    private String ownerUserId;
    private boolean editorsCanPublish = false;
    private boolean strictSecretGating = true;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public Workspace() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    public Workspace(String id, String name, String slug, String type, String ownerUserId) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.type = type != null ? type : "personal";
        this.ownerUserId = ownerUserId;
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public boolean isEditorsCanPublish() { return editorsCanPublish; }
    public void setEditorsCanPublish(boolean editorsCanPublish) { this.editorsCanPublish = editorsCanPublish; }

    public boolean isStrictSecretGating() { return strictSecretGating; }
    public void setStrictSecretGating(boolean strictSecretGating) { this.strictSecretGating = strictSecretGating; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
