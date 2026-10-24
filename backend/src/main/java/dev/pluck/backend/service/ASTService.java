package dev.pluck.backend.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ASTService {

    // Node.js / Browser standard library modules
    private static final Set<String> JS_STDLIB = Set.of(
            "fs", "path", "http", "https", "crypto", "os", "stream", "util", "events",
            "child_process", "buffer", "url", "querystring", "zlib", "net", "tls",
            "assert", "cluster", "dgram", "dns", "readline", "repl", "v8", "vm",
            "perf_hooks", "worker_threads", "wasi", "diagnostics_channel", "string_decoder",
            "timers", "constants", "tty", "punycode"
    );

    // Python standard library modules
    private static final Set<String> PYTHON_STDLIB = Set.of(
            "sys", "os", "json", "math", "re", "typing", "datetime", "collections",
            "itertools", "functools", "pathlib", "random", "time", "logging", "argparse",
            "subprocess", "urllib", "http", "threading", "multiprocessing", "queue",
            "io", "csv", "sqlite3", "socket", "ssl", "hashlib", "hmac", "secrets",
            "base64", "shutil", "tempfile", "copy", "uuid", "enum", "dataclasses",
            "abc", "inspect", "unittest", "contextlib", "concurrent", "asyncio",
            "platform", "struct", "ctypes", "traceback", "weakref", "warnings",
            "gc", "glob", "heapq", "bisect", "shelve", "pickle", "gzip", "zipfile",
            "tarfile", "smtplib", "email", "xml", "html", "cgi", "ipaddress", "configparser"
    );

    // Python import alias mapping (import name -> pip package name)
    private static final Map<String, String> PYTHON_PACKAGE_MAP = Map.of(
            "cv2", "opencv-python",
            "PIL", "Pillow",
            "yaml", "PyYAML",
            "sklearn", "scikit-learn",
            "bs4", "beautifulsoup4",
            "dotenv", "python-dotenv",
            "google.protobuf", "protobuf",
            "psycopg2", "psycopg2-binary",
            "jwt", "PyJWT",
            "magic", "python-magic"
    );

    // Go standard library packages
    private static final Set<String> GO_STDLIB = Set.of(
            "fmt", "net/http", "os", "io", "strings", "strconv", "time", "sync",
            "context", "errors", "encoding/json", "log", "bytes", "math", "sort",
            "bufio", "path", "flag", "net", "crypto", "database/sql", "regexp",
            "html", "testing", "unsafe", "syscall", "runtime", "image", "archive"
    );

    // Rust built-in crates
    private static final Set<String> RUST_STDLIB = Set.of(
            "std", "core", "alloc", "crate", "super", "self"
    );

    public record ASTAnalysisResult(
            String detectedLanguage,
            List<Map<String, Object>> dependencies,
            List<String> dependencyNames,
            Map<String, String> installCommands,
            List<String> envKeys,
            List<Map<String, Object>> symbols,
            boolean isRunnable
    ) {}

    public ASTAnalysisResult parseSnippet(String language, String code) {
        if (code == null) code = "";
        String cleanCode = code.trim();

        String normalizedLang = normalizeLanguage(language, cleanCode);
        List<Map<String, Object>> dependencies = new ArrayList<>();
        Set<String> depNames = new LinkedHashSet<>();

        switch (normalizedLang.toLowerCase()) {
            case "python" -> parsePython(cleanCode, dependencies, depNames);
            case "java" -> parseJava(cleanCode, dependencies, depNames);
            case "go" -> parseGo(cleanCode, dependencies, depNames);
            case "rust" -> parseRust(cleanCode, dependencies, depNames);
            default -> parseJavaScriptTypeScript(cleanCode, dependencies, depNames);
        }

        List<String> dependencyNameList = new ArrayList<>(depNames);
        Map<String, String> installCommands = generateInstallCommands(normalizedLang, dependencyNameList);
        List<String> envKeys = extractEnvironmentVariables(cleanCode);
        List<Map<String, Object>> symbols = extractSymbols(normalizedLang, cleanCode);
        boolean isRunnable = detectRunnable(normalizedLang, cleanCode);

        return new ASTAnalysisResult(
                normalizedLang,
                dependencies,
                dependencyNameList,
                installCommands,
                envKeys,
                symbols,
                isRunnable
        );
    }

    public String normalizeLanguage(String lang, String code) {
        if (lang != null && !lang.isBlank() && !lang.equalsIgnoreCase("auto")) {
            String lower = lang.toLowerCase();
            if (lower.contains("python") || lower.equals("py")) return "python";
            if (lower.contains("java") && !lower.contains("javascript")) return "java";
            if (lower.contains("go")) return "go";
            if (lower.contains("rust") || lower.equals("rs")) return "rust";
            if (lower.contains("typescript") || lower.equals("ts") || lower.equals("tsx")) return "typescript";
            if (lower.contains("javascript") || lower.equals("js") || lower.equals("jsx")) return "javascript";
            return lower;
        }

        // Automatic Language Heuristics
        if (code.contains("def ") && (code.contains("import ") || code.contains("from ") || code.contains("print("))) {
            return "python";
        }
        if (code.contains("public class ") || code.contains("package ") || code.contains("System.out.println")) {
            return "java";
        }
        if (code.contains("func ") && (code.contains("package ") || code.contains("import ("))) {
            return "go";
        }
        if (code.contains("fn ") && (code.contains("let mut ") || code.contains("use std::") || code.contains("println!"))) {
            return "rust";
        }
        if (code.contains("interface ") || code.contains(": React.FC") || code.contains("<T>") || code.contains("type ")) {
            return "typescript";
        }
        return "javascript";
    }

    private void parseJavaScriptTypeScript(String code, List<Map<String, Object>> deps, Set<String> names) {
        // 1. Regular imports: import ... from 'pkg'
        Pattern impPattern = Pattern.compile("(?:^|\\n)\\s*import\\s+(?:(type\\s+)?(?:.+?\\s+from\\s+)?|.*?)(['\"])([^'\"]+)\\2", Pattern.DOTALL);
        Matcher m1 = impPattern.matcher(code);
        while (m1.find()) {
            boolean isTypeOnly = m1.group(1) != null || m1.group(0).contains("import type ");
            String specifier = m1.group(3).trim();
            processJsSpecifier(specifier, isTypeOnly, deps, names);
        }

        // 2. require('pkg') and dynamic import('pkg')
        Pattern reqPattern = Pattern.compile("(?:require|import)\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)");
        Matcher m2 = reqPattern.matcher(code);
        while (m2.find()) {
            String specifier = m2.group(2).trim();
            processJsSpecifier(specifier, false, deps, names);
        }

        // 3. export ... from 'pkg'
        Pattern expPattern = Pattern.compile("(?:^|\\n)\\s*export\\s+(?:.+?\\s+from\\s+)(['\"])([^'\"]+)\\1");
        Matcher m3 = expPattern.matcher(code);
        while (m3.find()) {
            String specifier = m3.group(2).trim();
            processJsSpecifier(specifier, false, deps, names);
        }
    }

    private void processJsSpecifier(String specifier, boolean isTypeOnly, List<Map<String, Object>> deps, Set<String> names) {
        if (specifier == null || specifier.isEmpty()) return;
        // Skip relative paths and aliases
        if (specifier.startsWith(".") || specifier.startsWith("/") || specifier.startsWith("@/") || specifier.startsWith("~/") || specifier.startsWith("#/")) {
            return;
        }
        // Skip Node.js builtins: node:fs, fs, path, etc.
        if (specifier.startsWith("node:")) return;
        if (JS_STDLIB.contains(specifier)) return;

        // Extract root package name
        String pkgName;
        if (specifier.startsWith("@")) {
            // Scoped package e.g. @tanstack/react-query/core -> @tanstack/react-query
            String[] parts = specifier.split("/");
            if (parts.length >= 2) {
                pkgName = parts[0] + "/" + parts[1];
            } else {
                pkgName = specifier;
            }
        } else {
            // Regular package e.g. lodash/debounce -> lodash
            pkgName = specifier.split("/")[0];
        }

        if (pkgName.isBlank() || JS_STDLIB.contains(pkgName)) return;

        if (names.add(pkgName)) {
            Map<String, Object> dep = new HashMap<>();
            dep.put("name", pkgName);
            dep.put("ecosystem", "npm");
            dep.put("versionHint", "^1.0.0");
            dep.put("typeOnly", isTypeOnly);
            dep.put("confidence", 1.0);
            deps.add(dep);
        }
    }

    private void parsePython(String code, List<Map<String, Object>> deps, Set<String> names) {
        // 1. import pkg, pkg2
        Pattern p1 = Pattern.compile("^[ \\t]*import[ \\t]+([^\\r\\n#]+)", Pattern.MULTILINE);
        Matcher m1 = p1.matcher(code);
        while (m1.find()) {
            String[] items = m1.group(1).split(",");
            for (String item : items) {
                String trimmed = item.trim();
                if (trimmed.isEmpty()) continue;
                String raw = trimmed.split("\\s+")[0].split("\\.")[0];
                addPythonPackage(raw, deps, names);
            }
        }

        // 2. from pkg import ...
        Pattern p2 = Pattern.compile("^[ \\t]*from[ \\t]+([A-Za-z0-9_.]+)[ \\t]+import", Pattern.MULTILINE);
        Matcher m2 = p2.matcher(code);
        while (m2.find()) {
            String raw = m2.group(1).trim().split("\\.")[0];
            addPythonPackage(raw, deps, names);
        }
    }

    private void addPythonPackage(String rawName, List<Map<String, Object>> deps, Set<String> names) {
        if (rawName == null || rawName.isBlank()) return;
        if (PYTHON_STDLIB.contains(rawName)) return;

        String pkg = PYTHON_PACKAGE_MAP.getOrDefault(rawName, rawName);
        if (names.add(pkg)) {
            Map<String, Object> dep = new HashMap<>();
            dep.put("name", pkg);
            dep.put("ecosystem", "pypi");
            dep.put("versionHint", ">=1.0.0");
            dep.put("typeOnly", false);
            dep.put("confidence", 0.95);
            deps.add(dep);
        }
    }

    private void parseJava(String code, List<Map<String, Object>> deps, Set<String> names) {
        Pattern pattern = Pattern.compile("(?:^|\\n)\\s*import\\s+([A-Za-z0-9_.]+);");
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String fqcn = matcher.group(1).trim();
            if (fqcn.startsWith("java.") || fqcn.startsWith("javax.") || fqcn.startsWith("jakarta.") || fqcn.startsWith("sun.")) {
                continue;
            }

            String pkg;
            if (fqcn.startsWith("org.springframework")) {
                pkg = "org.springframework.boot:spring-boot-starter-web";
            } else if (fqcn.startsWith("com.fasterxml.jackson")) {
                pkg = "com.fasterxml.jackson.core:jackson-databind";
            } else if (fqcn.startsWith("org.slf4j")) {
                pkg = "org.slf4j:slf4j-api";
            } else if (fqcn.startsWith("org.projectlombok")) {
                pkg = "org.projectlombok:lombok";
            } else {
                String[] parts = fqcn.split("\\.");
                pkg = (parts.length >= 2) ? parts[0] + "." + parts[1] : fqcn;
            }

            if (names.add(pkg)) {
                Map<String, Object> dep = new HashMap<>();
                dep.put("name", pkg);
                dep.put("ecosystem", "maven");
                dep.put("versionHint", "3.0.0");
                dep.put("typeOnly", false);
                dep.put("confidence", 0.9);
                deps.add(dep);
            }
        }
    }

    private void parseGo(String code, List<Map<String, Object>> deps, Set<String> names) {
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String pkg = matcher.group(1).trim();
            if (pkg.contains("/") && !GO_STDLIB.contains(pkg)) {
                if (pkg.startsWith("github.com/") || pkg.startsWith("golang.org/") || pkg.startsWith("gopkg.in/")) {
                    if (names.add(pkg)) {
                        Map<String, Object> dep = new HashMap<>();
                        dep.put("name", pkg);
                        dep.put("ecosystem", "go");
                        dep.put("versionHint", "latest");
                        dep.put("typeOnly", false);
                        dep.put("confidence", 1.0);
                        deps.add(dep);
                    }
                }
            }
        }
    }

    private void parseRust(String code, List<Map<String, Object>> deps, Set<String> names) {
        Pattern pattern = Pattern.compile("(?:^|\\n)\\s*use\\s+([A-Za-z0-9_]+)::");
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String crate = matcher.group(1).trim();
            if (!RUST_STDLIB.contains(crate)) {
                if (names.add(crate)) {
                    Map<String, Object> dep = new HashMap<>();
                    dep.put("name", crate);
                    dep.put("ecosystem", "cargo");
                    dep.put("versionHint", "1.0");
                    dep.put("typeOnly", false);
                    dep.put("confidence", 1.0);
                    deps.add(dep);
                }
            }
        }
    }

    public Map<String, String> generateInstallCommands(String language, List<String> packageNames) {
        Map<String, String> commands = new LinkedHashMap<>();
        if (packageNames == null || packageNames.isEmpty()) {
            commands.put("npm", "# No external packages required");
            commands.put("pnpm", "# No external packages required");
            commands.put("yarn", "# No external packages required");
            commands.put("bun", "# No external packages required");
            commands.put("pip", "# No external packages required");
            commands.put("uv", "# No external packages required");
            commands.put("go", "# No external packages required");
            commands.put("cargo", "# No external packages required");
            return commands;
        }

        String joined = String.join(" ", packageNames);

        // JavaScript / TypeScript PMs
        commands.put("npm", "npm i " + joined);
        commands.put("pnpm", "pnpm add " + joined);
        commands.put("yarn", "yarn add " + joined);
        commands.put("bun", "bun add " + joined);

        // Python PMs
        commands.put("pip", "pip install " + joined);
        commands.put("uv", "uv add " + joined);

        // Go & Rust
        commands.put("go", "go get " + joined);
        commands.put("cargo", "cargo add " + joined);

        return commands;
    }

    public List<String> extractEnvironmentVariables(String code) {
        Set<String> envs = new LinkedHashSet<>();
        if (code == null) return new ArrayList<>();

        // 1. process.env.KEY or import.meta.env.KEY
        Pattern p1 = Pattern.compile("(?:process\\.env|import\\.meta\\.env)\\.([A-Z0-9_]{2,})");
        Matcher m1 = p1.matcher(code);
        while (m1.find()) {
            envs.add(m1.group(1));
        }

        // 2. os.environ['KEY'] or os.environ.get('KEY')
        Pattern p2 = Pattern.compile("os\\.environ(?:\\.get)?\\(\\s*['\"]([A-Z0-9_]{2,})['\"]\\s*\\)|os\\.environ\\[['\"]([A-Z0-9_]{2,})['\"]\\]");
        Matcher m2 = p2.matcher(code);
        while (m2.find()) {
            if (m2.group(1) != null) envs.add(m2.group(1));
            else if (m2.group(2) != null) envs.add(m2.group(2));
        }

        // 3. System.getenv("KEY") or os.Getenv("KEY") or std::env::var("KEY")
        Pattern p3 = Pattern.compile("(?:System\\.getenv|os\\.Getenv|env::var)\\s*\\(\\s*['\"]([A-Z0-9_]{2,})['\"]\\s*\\)");
        Matcher m3 = p3.matcher(code);
        while (m3.find()) {
            envs.add(m3.group(1));
        }

        return new ArrayList<>(envs);
    }

    public List<Map<String, Object>> extractSymbols(String language, String code) {
        List<Map<String, Object>> symbols = new ArrayList<>();
        if (code == null) return symbols;

        // Functions / Hooks
        Pattern funcPattern = Pattern.compile("(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z0-9_$]+)|(?:export\\s+)?const\\s+([A-Za-z0-9_$]+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z0-9_$]+)\\s*=>|def\\s+([A-Za-z0-9_]+)\\s*\\(|func\\s+([A-Za-z0-9_]+)\\s*\\(|fn\\s+([A-Za-z0-9_]+)\\s*\\(");
        Matcher m1 = funcPattern.matcher(code);
        while (m1.find()) {
            String name = null;
            for (int i = 1; i <= m1.groupCount(); i++) {
                if (m1.group(i) != null) {
                    name = m1.group(i);
                    break;
                }
            }
            if (name != null && !name.isBlank()) {
                Map<String, Object> sym = new HashMap<>();
                sym.put("name", name);
                sym.put("type", name.startsWith("use") ? "hook" : "function");
                symbols.add(sym);
            }
        }

        // Classes / Interfaces / Structs / Types
        Pattern classPattern = Pattern.compile("(?:class|interface|struct|type)\\s+([A-Za-z0-9_$]+)");
        Matcher m2 = classPattern.matcher(code);
        while (m2.find()) {
            String name = m2.group(1);
            if (name != null && !name.isBlank()) {
                Map<String, Object> sym = new HashMap<>();
                sym.put("name", name);
                sym.put("type", "type_definition");
                symbols.add(sym);
            }
        }

        return symbols;
    }

    public boolean detectRunnable(String language, String code) {
        if (code == null || code.isBlank()) return false;
        // Contains main entry point
        if (code.contains("main(") || code.contains("public static void main") || code.contains("func main()")) {
            return true;
        }
        // Self-executing or top-level log/fetch calls
        if (code.contains("console.log(") || code.contains("print(") || code.contains("println!(")) {
            return true;
        }
        if (code.contains("if __name__ == '__main__':") || code.contains("if __name__ == \"__main__\":")) {
            return true;
        }
        return false;
    }
}
