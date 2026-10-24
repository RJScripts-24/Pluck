package dev.pluck.backend.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateService {

    public record VariableDefinition(
            String name,
            String defaultValue,
            String type, // "string", "number", "boolean", "object"
            String description
    ) {}

    public record RenderResult(
            String renderedCode,
            List<String> missingVariables,
            List<VariableDefinition> variables
    ) {}

    // Pluck variable regex: {{name}} or {{name|default}}
    // Note: Variable names must start with a letter or underscore, followed by alphanumeric/underscores.
    private static final Pattern VAR_PATTERN = Pattern.compile("(?<!\\\\)\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\\|([^}]*))?\\}\\}");
    private static final Pattern ESCAPED_VAR_PATTERN = Pattern.compile("\\\\\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:\\|([^}]*))?\\}\\}");

    /**
     * Extracts all unique variable definitions declared in code template.
     */
    public List<VariableDefinition> extractVariables(String code) {
        if (code == null || code.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, VariableDefinition> result = new LinkedHashMap<>();
        Matcher m = VAR_PATTERN.matcher(code);

        while (m.find()) {
            int start = m.start();
            if (isJsxAttribute(code, start)) {
                continue;
            }

            String varName = m.group(1);
            String defaultValue = m.group(2); // May be null

            if (!result.containsKey(varName)) {
                String inferredType = inferType(defaultValue);
                String desc = "Variable: " + varName + (defaultValue != null ? " (default: " + defaultValue + ")" : "");
                result.put(varName, new VariableDefinition(varName, defaultValue, inferredType, desc));
            }
        }

        return new ArrayList<>(result.values());
    }

    /**
     * Renders template by interpolating variables with provided values.
     * Preserves indentation for multi-line values.
     * Escapes \{{var}} to {{var}}.
     * Excludes JSX attributes like style={{ width }} or value={{ prop }}.
     */
    public RenderResult renderTemplate(String code, Map<String, String> values, boolean strict) {
        if (code == null) {
            return new RenderResult("", Collections.emptyList(), Collections.emptyList());
        }

        Map<String, String> safeValues = values != null ? values : Collections.emptyMap();
        List<VariableDefinition> detectedVars = extractVariables(code);
        List<String> missing = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        int cursor = 0;

        Matcher m = VAR_PATTERN.matcher(code);
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            // Append code preceding the match
            sb.append(code, cursor, start);

            // Check if this match is inside a JSX attribute e.g. style={{ width }}
            if (isJsxAttribute(code, start)) {
                sb.append(code, start, end);
                cursor = end;
                continue;
            }

            String varName = m.group(1);
            String defaultValue = m.group(2);

            String replacement = null;
            if (safeValues.containsKey(varName) && safeValues.get(varName) != null) {
                replacement = safeValues.get(varName);
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                missing.add(varName);
                if (strict) {
                    replacement = m.group(0); // Keep placeholder in strict mode if missing
                } else {
                    replacement = "";
                }
            }

            // Multi-line Indentation Preservation
            if (replacement.contains("\n")) {
                String lineIndent = calculateLineIndentation(code, start);
                replacement = indentSubsequentLines(replacement, lineIndent);
            }

            sb.append(replacement);
            cursor = end;
        }

        // Append remaining code
        if (cursor < code.length()) {
            sb.append(code.substring(cursor));
        }

        // Replace escaped placeholders \{{var}} with {{var}}
        String finalOutput = ESCAPED_VAR_PATTERN.matcher(sb.toString()).replaceAll("{{$1$2}}");

        return new RenderResult(finalOutput, missing, detectedVars);
    }

    /**
     * Checks if the {{ at index is a JSX attribute expression like style={{ width }} or value={{ prop }}.
     */
    private boolean isJsxAttribute(String code, int index) {
        if (index <= 0) return false;

        // Check if immediately preceded by attr= e.g. style={{ or value={{ or prop={{
        int lookback = Math.max(0, index - 50);
        String prefix = code.substring(lookback, index);

        // Pattern: [a-zA-Z0-9_$-]+\s*=\s*$
        if (prefix.matches(".*\\b[a-zA-Z0-9_$-]+\\s*=\\s*$")) {
            // Verify if we are inside an open JSX tag <Tag ... attr={{
            int lastLt = code.lastIndexOf('<', index);
            int lastGt = code.lastIndexOf('>', index);
            if (lastLt > lastGt) {
                return true;
            }
            // Even if not strictly inside a tag (e.g. JSX fragment or object prop),
            // style={{ ... }} or value={{ ... }} is JSX attribute syntax
            if (prefix.matches(".*\\b(style|value|sx|props|initial|animate|exit|options)\\s*=\\s*$")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines leading whitespace of the line containing the given index.
     */
    private String calculateLineIndentation(String code, int index) {
        int lineStart = code.lastIndexOf('\n', index);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;

        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < index; i++) {
            char c = code.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    /**
     * Indents all lines after the first with the given line indentation.
     */
    private String indentSubsequentLines(String multilineText, String lineIndent) {
        if (lineIndent.isEmpty()) return multilineText;

        String[] lines = multilineText.split("\r?\n", -1);
        if (lines.length <= 1) return multilineText;

        StringBuilder sb = new StringBuilder();
        sb.append(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            sb.append("\n").append(lineIndent).append(lines[i]);
        }
        return sb.toString();
    }

    private String inferType(String defaultValue) {
        if (defaultValue == null) return "string";
        String trimmed = defaultValue.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) return "boolean";
        if (trimmed.matches("^-?\\d+(\\.\\d+)?$")) return "number";
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "object";
        return "string";
    }
}
