package dev.pluck.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "snippets")
@CompoundIndex(name = "ws_slug_idx", def = "{'workspaceId': 1, 'slug': 1}")
@CompoundIndex(name = "ws_updated_idx", def = "{'workspaceId': 1, 'updatedAt': -1}")
public class Snippet {

    @Id
    private String id;

    @Indexed
    private String workspaceId = "ws_personal";

    @Indexed
    private String folderId = "hooks";
    private String folderName = "hooks";

    private String authorId;

    @Indexed
    private String slug;

    private String title;
    private String description;
    private String language = "typescript";
    private String code;
    private String codeHash;

    @Indexed
    private List<String> tags = new ArrayList<>();

    private List<Map<String, Object>> dependencies = new ArrayList<>();

    @Indexed
    private List<String> dependencyNames = new ArrayList<>();

    private List<Map<String, Object>> symbols = new ArrayList<>();
    private String symbolsText = "";
    private boolean isRunnable = false;

    private List<Map<String, Object>> envSchema = new ArrayList<>();
    private List<String> envKeys = new ArrayList<>();
    private List<Object> variables = new ArrayList<>();

    private String visibility = "private"; // "private" | "team" | "unlisted" | "public"

    @Indexed(unique = true, sparse = true)
    private String shareId;

    private String forkedFromId;
    private int version = 1;
    private List<Map<String, Object>> versions = new ArrayList<>();

    private int copyCount = 0;
    private int forkCount = 0;

    private String aiSummary;
    private List<String> aiKeywords = new ArrayList<>();
    private List<String> aiUseCases = new ArrayList<>();
    private String designPattern;

    private List<Double> embedding = new ArrayList<>();
    private String embeddingModel = "text-embedding-3-small (1536 dims)";
    private String embeddingStatus = "ready";
    private int vectorDimensions = 1536;

    private String secretScanStatus = "clean"; // "clean" | "redacted" | "acknowledged"

    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public Snippet() {
        this.createdAt = Instant.now().toString();
        this.updatedAt = Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<Map<String, Object>> getDependencies() { return dependencies; }
    public void setDependencies(List<Map<String, Object>> dependencies) { this.dependencies = dependencies; }

    public List<String> getDependencyNames() { return dependencyNames; }
    public void setDependencyNames(List<String> dependencyNames) { this.dependencyNames = dependencyNames; }

    public List<Map<String, Object>> getSymbols() { return symbols; }
    public void setSymbols(List<Map<String, Object>> symbols) { this.symbols = symbols; }

    public String getSymbolsText() { return symbolsText; }
    public void setSymbolsText(String symbolsText) { this.symbolsText = symbolsText; }

    public boolean isRunnable() { return isRunnable; }
    public void setRunnable(boolean isRunnable) { this.isRunnable = isRunnable; }

    public List<Map<String, Object>> getEnvSchema() { return envSchema; }
    public void setEnvSchema(List<Map<String, Object>> envSchema) { this.envSchema = envSchema; }

    public List<String> getEnvKeys() { return envKeys; }
    public void setEnvKeys(List<String> envKeys) { this.envKeys = envKeys; }

    public List<Object> getVariables() { return variables; }
    public void setVariables(List<Object> variables) { this.variables = variables; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }

    public String getForkedFromId() { return forkedFromId; }
    public void setForkedFromId(String forkedFromId) { this.forkedFromId = forkedFromId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<Map<String, Object>> getVersions() { return versions; }
    public void setVersions(List<Map<String, Object>> versions) { this.versions = versions; }

    public int getCopyCount() { return copyCount; }
    public void setCopyCount(int copyCount) { this.copyCount = copyCount; }

    public int getForkCount() { return forkCount; }
    public void setForkCount(int forkCount) { this.forkCount = forkCount; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public List<String> getAiKeywords() { return aiKeywords; }
    public void setAiKeywords(List<String> aiKeywords) { this.aiKeywords = aiKeywords; }

    public List<String> getAiUseCases() { return aiUseCases; }
    public void setAiUseCases(List<String> aiUseCases) { this.aiUseCases = aiUseCases; }

    public String getDesignPattern() { return designPattern; }
    public void setDesignPattern(String designPattern) { this.designPattern = designPattern; }

    public List<Double> getEmbedding() { return embedding; }
    public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getEmbeddingStatus() { return embeddingStatus; }
    public void setEmbeddingStatus(String embeddingStatus) { this.embeddingStatus = embeddingStatus; }

    public int getVectorDimensions() { return vectorDimensions; }
    public void setVectorDimensions(int vectorDimensions) { this.vectorDimensions = vectorDimensions; }

    public String getSecretScanStatus() { return secretScanStatus; }
    public void setSecretScanStatus(String secretScanStatus) { this.secretScanStatus = secretScanStatus; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
