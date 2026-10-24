package dev.pluck.backend.controller;

import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class McpControllerTest {

    private McpService mcpService;
    private UserRepository userRepository;
    private McpController mcpController;

    @BeforeEach
    void setUp() {
        mcpService = mock(McpService.class);
        userRepository = mock(UserRepository.class);
        mcpController = new McpController(mcpService, userRepository);

        when(userRepository.findAll()).thenReturn(List.of(new User("usr_alex", "Alex Developer", "alex@pluck.dev", "hash", "owner")));
    }

    @Test
    @DisplayName("Stage 9: McpController delegates JSON-RPC call and returns 200 OK")
    void testHandleMcp() {
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/list"
        );

        when(mcpService.handleJsonRpc(eq(req), any(), anyString()))
                .thenReturn(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of("tools", List.of())));

        ResponseEntity<Map<String, Object>> response = mcpController.handleMcp(req, "ws_personal");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("2.0", response.getBody().get("jsonrpc"));
    }
}
