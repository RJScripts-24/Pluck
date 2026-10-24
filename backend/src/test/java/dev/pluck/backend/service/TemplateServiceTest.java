package dev.pluck.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateServiceTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService();
    }

    @Test
    @DisplayName("Interpolates {{delay|300}} into 500 when passed {\"delay\":\"500\"}")
    void testBasicVariableInterpolation() {
        String code = "function wait(ms = {{delay|300}}) { return new Promise(r => setTimeout(r, ms)); }";
        TemplateService.RenderResult result = templateService.renderTemplate(code, Map.of("delay", "500"), false);

        assertEquals("function wait(ms = 500) { return new Promise(r => setTimeout(r, ms)); }", result.renderedCode());
        assertTrue(result.missingVariables().isEmpty());
    }

    @Test
    @DisplayName("Falls back to default value when variable is omitted from values map")
    void testDefaultValueFallback() {
        String code = "const timeout = {{timeout|1000}};";
        TemplateService.RenderResult result = templateService.renderTemplate(code, Map.of(), false);

        assertEquals("const timeout = 1000;", result.renderedCode());
        assertTrue(result.missingVariables().isEmpty());
    }

    @Test
    @DisplayName("JSX style={{ width }} and value={{ prop }} are not modified")
    void testJsxAttributeExclusion() {
        String code = """
            export function Widget() {
                return (
                    <div style={{ width }} value={{ prop }} className="container">
                        <span>{{message|Hello World}}</span>
                    </div>
                );
            }
            """;

        TemplateService.RenderResult result = templateService.renderTemplate(code, Map.of("message", "Live Update"), false);

        assertTrue(result.renderedCode().contains("style={{ width }}"), "style={{ width }} must NOT be parsed or modified");
        assertTrue(result.renderedCode().contains("value={{ prop }}"), "value={{ prop }} must NOT be parsed or modified");
        assertTrue(result.renderedCode().contains("<span>Live Update</span>"), "Pluck child variable should be interpolated");
    }

    @Test
    @DisplayName("Escaped variables with backslash \\{{name}} render as literal {{name}}")
    void testEscaping() {
        String code = "const mustache = \"\\{{unrendered}}\";";
        TemplateService.RenderResult result = templateService.renderTemplate(code, Map.of("unrendered", "replacement"), false);

        assertEquals("const mustache = \"{{unrendered}}\";", result.renderedCode());
    }

    @Test
    @DisplayName("Preserves indentation on multi-line substituted values")
    void testMultilineIndentationPreservation() {
        String template = """
            function init() {
                const config = {{config}};
                return config;
            }
            """;

        String multilineConfig = """
            {
              port: 4000,
              debug: true
            }
            """.trim();

        TemplateService.RenderResult result = templateService.renderTemplate(template, Map.of("config", multilineConfig), false);

        // Verify subsequent lines of multilineConfig have the 4-space indent
        assertTrue(result.renderedCode().contains("    const config = {\n      port: 4000,\n      debug: true\n    };"));
    }

    @Test
    @DisplayName("Strict mode records missing variables without default values")
    void testStrictModeMissingVariables() {
        String code = "const key = \"{{apiKey}}\"; const delay = {{delay|200}};";
        TemplateService.RenderResult result = templateService.renderTemplate(code, Map.of(), true);

        assertEquals(1, result.missingVariables().size());
        assertTrue(result.missingVariables().contains("apiKey"));
        assertTrue(result.renderedCode().contains("{{apiKey}}"), "Strict mode preserves placeholder when missing");
        assertTrue(result.renderedCode().contains("200"), "Variables with defaults are still resolved");
    }

    @Test
    @DisplayName("extractVariables extracts variable definitions with inferred types")
    void testExtractVariables() {
        String code = """
            const name = "{{username|john_doe}}";
            const retries = {{retries|3}};
            const enabled = {{enabled|true}};
            const empty = {{requiredToken}};
            """;

        List<TemplateService.VariableDefinition> vars = templateService.extractVariables(code);
        assertEquals(4, vars.size());

        assertEquals("username", vars.get(0).name());
        assertEquals("string", vars.get(0).type());

        assertEquals("retries", vars.get(1).name());
        assertEquals("number", vars.get(1).type());

        assertEquals("enabled", vars.get(2).name());
        assertEquals("boolean", vars.get(2).type());

        assertEquals("requiredToken", vars.get(3).name());
        assertNull(vars.get(3).defaultValue());
    }
}
