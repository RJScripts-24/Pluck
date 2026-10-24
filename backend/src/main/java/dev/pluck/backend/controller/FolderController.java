package dev.pluck.backend.controller;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.User;
import dev.pluck.backend.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    @Autowired
    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    private String resolveWorkspaceId(String headerWorkspace) {
        if (headerWorkspace != null && !headerWorkspace.isBlank()) {
            return headerWorkspace;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal && principal.getActiveWorkspaceId() != null) {
            return principal.getActiveWorkspaceId();
        }
        return "ws_personal";
    }

    @GetMapping
    public ResponseEntity<?> listFolders(@RequestHeader(value = "X-Workspace-Id", required = false) String headerWs) {
        String wsId = resolveWorkspaceId(headerWs);
        List<Folder> folders = folderService.listFolders(wsId);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("count", folders.size());
        res.put("folders", folders);
        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<?> createFolder(
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestBody Map<String, Object> body
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        String name = (String) body.get("name");
        String emoji = (String) body.getOrDefault("emoji", "📁");
        String parentId = (String) body.get("parentId");

        try {
            Folder created = folderService.createFolder(wsId, name, emoji, parentId);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("folder", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "VALIDATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateFolder(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestBody Map<String, Object> body
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        try {
            Folder updated = folderService.updateFolder(wsId, id, body);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "FOLDER_NOT_FOUND",
                        "message", "Folder '" + id + "' not found."
                ));
            }

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("folder", updated);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "VALIDATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        boolean deleted = folderService.deleteFolder(wsId, id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "FOLDER_NOT_FOUND",
                    "message", "Folder '" + id + "' not found."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Folder deleted successfully"
        ));
    }
}
