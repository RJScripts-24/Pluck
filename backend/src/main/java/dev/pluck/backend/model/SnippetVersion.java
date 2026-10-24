package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "snippet_versions")
@CompoundIndex(name = "snip_version_idx", def = "{'snippetId': 1, 'version': 1}", unique = true)
public class SnippetVersion {

    @Id
    private String id;

    @Indexed
    private String snippetId;

    private int version;
    private String title;
    private String description;
    private String language;
    private String code;
    private List<Map<String, Object>> dependencies = new ArrayList<>();
    private List<Map<String, Object>> envSchema = new ArrayList<>();
    private List<String> envKeys = new ArrayList<>();
    private List<Object> variables = new ArrayList<>();
    private String authorId;
    private String createdAt;

    public SnippetVersion() {
        this.createdAt = Instant.now().toString();
    }

    public SnippetVersion(String snippetId, int version, String title, String description, String language, String code) {
        this.snippetId = snippetId;
        this.version = version;
        this.title = title;
        this.description = description;
        this.language = language;
        this.code = code;
        this.createdAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSnippetId() { return snippetId; }
    public void setSnippetId(String snippetId) { this.snippetId = snippetId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public List<Map<String, Object>> getDependencies() { return dependencies; }
    public void setDependencies(List<Map<String, Object>> dependencies) { this.dependencies = dependencies; }

    public List<Map<String, Object>> getEnvSchema() { return envSchema; }
    public void setEnvSchema(List<Map<String, Object>> envSchema) { this.envSchema = envSchema; }

    public List<String> getEnvKeys() { return envKeys; }
    public void setEnvKeys(List<String> envKeys) { this.envKeys = envKeys; }

    public List<Object> getVariables() { return variables; }
    public void setVariables(List<Object> variables) { this.variables = variables; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
