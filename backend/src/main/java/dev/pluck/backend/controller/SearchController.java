package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
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
    public ResponseEntity<SearchService.SearchResponse> searchGet(
            @RequestParam(required = false, name = "q", defaultValue = "") String queryParam,
            @RequestParam(required = false, name = "query", defaultValue = "") String queryAlt,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String q = !queryParam.isBlank() ? queryParam : queryAlt;
        String wsId = resolveWorkspaceId(headerWs);
        SearchService.SearchResponse response = searchService.search(q, wsId, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SearchService.SearchResponse> searchPost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String q = "";
        int limit = 20;

        if (body != null) {
            if (body.containsKey("query")) {
                q = String.valueOf(body.get("query"));
            } else if (body.containsKey("q")) {
                q = String.valueOf(body.get("q"));
            }

            if (body.containsKey("limit") && body.get("limit") instanceof Number num) {
                limit = num.intValue();
            }
        }

        String wsId = resolveWorkspaceId(headerWs);
        SearchService.SearchResponse response = searchService.search(q, wsId, limit);
        return ResponseEntity.ok(response);
    }
}
