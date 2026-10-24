package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "tokens")
public class Token {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;

    @Indexed(unique = true)
    private String tokenPrefix;

    @Indexed
    private String tokenHash;

    private List<String> scopes = new ArrayList<>();
    private List<String> workspaceIds = new ArrayList<>();
    private String lastUsed = "Never";
    private String lastUsedAt;
    private String lastUsedIp;
    private String expires = "in 90 days";
    private String expiresAt;
    private String revokedAt;
    private String createdAt;

    public Token() {
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }

    // Backward compatibility for prefix
    public String getPrefix() { return tokenPrefix; }
    public void setPrefix(String prefix) { this.tokenPrefix = prefix; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public List<String> getWorkspaceIds() { return workspaceIds; }
    public void setWorkspaceIds(List<String> workspaceIds) { this.workspaceIds = workspaceIds; }

    public String getLastUsed() { return lastUsed; }
    public void setLastUsed(String lastUsed) { this.lastUsed = lastUsed; }

    public String getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(String lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public String getLastUsedIp() { return lastUsedIp; }
    public void setLastUsedIp(String lastUsedIp) { this.lastUsedIp = lastUsedIp; }

    public String getExpires() { return expires; }
    public void setExpires(String expires) { this.expires = expires; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getRevokedAt() { return revokedAt; }
    public void setRevokedAt(String revokedAt) { this.revokedAt = revokedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
