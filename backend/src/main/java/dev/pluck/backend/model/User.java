package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "users")
public class User {

    @Id
    private String id;
    private String name;

    @Indexed(unique = true)
    private String email;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;
    private String avatar;
    private String avatarUrl;
    private String role = "owner";
    private Map<String, Object> preferences = new HashMap<>();
    private String createdAt;
    private String updatedAt;

    public User() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
        initDefaultPreferences();
    }

    public User(String id, String name, String email, String avatar, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.avatar = avatar;
        this.avatarUrl = avatar;
        this.role = role;
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
        initDefaultPreferences();
    }

    private void initDefaultPreferences() {
        if (this.preferences == null || this.preferences.isEmpty()) {
            this.preferences = new HashMap<>();
            this.preferences.put("packageManager", "npm");
            this.preferences.put("pythonPackageManager", "pip");
            this.preferences.put("theme", "system");
            this.preferences.put("editorKeymap", "default");
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAvatar() { return avatar != null ? avatar : avatarUrl; }
    public void setAvatar(String avatar) { 
        this.avatar = avatar; 
        this.avatarUrl = avatar;
    }

    public String getAvatarUrl() { return avatarUrl != null ? avatarUrl : avatar; }
    public void setAvatarUrl(String avatarUrl) { 
        this.avatarUrl = avatarUrl;
        this.avatar = avatarUrl;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Map<String, Object> getPreferences() { 
        if (preferences == null) initDefaultPreferences();
        return preferences; 
    }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
