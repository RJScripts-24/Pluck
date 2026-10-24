package dev.pluck.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ASTServiceTest {

    private ASTService astService;

    @BeforeEach
    void setUp() {
        astService = new ASTService();
    }

    @Test
    @DisplayName("Stage 3: JavaScript & TypeScript AST Dependency Extraction & Stdlib Filtering")
    void testJavaScriptParsing() {
        String code = """
            import React, { useState, useEffect } from 'react';
            import { useQuery } from '@tanstack/react-query';
            import debounce from 'lodash/debounce';
            import type { UserProfile } from './types';
            import fs from 'node:fs';
            import path from 'path';
            const axios = require('axios');
            
            export async function fetchUser(id: string) {
                const apiKey = process.env.GITHUB_API_KEY;
                console.log("Fetching user...");
                return null;
            }
            """;

        ASTService.ASTAnalysisResult result = astService.parseSnippet("typescript", code);

        assertEquals("typescript", result.detectedLanguage());
        assertTrue(result.dependencyNames().contains("react"));
        assertTrue(result.dependencyNames().contains("@tanstack/react-query"));
        assertTrue(result.dependencyNames().contains("lodash"));
        assertTrue(result.dependencyNames().contains("axios"));

        // Stdlib node:fs, path, and relative ./types must be filtered out
        assertFalse(result.dependencyNames().contains("node:fs"));
        assertFalse(result.dependencyNames().contains("fs"));
        assertFalse(result.dependencyNames().contains("path"));
        assertFalse(result.dependencyNames().contains("./types"));

        // Verify install commands
        Map<String, String> cmds = result.installCommands();
        assertTrue(cmds.get("npm").startsWith("npm i "));
        assertTrue(cmds.get("pnpm").startsWith("pnpm add "));
        assertTrue(cmds.get("bun").startsWith("bun add "));
        assertTrue(cmds.get("npm").contains("@tanstack/react-query"));

        // Verify env keys
        assertTrue(result.envKeys().contains("GITHUB_API_KEY"));

        // Verify symbols
        boolean hasFetchUser = result.symbols().stream().anyMatch(s -> "fetchUser".equals(s.get("name")));
        assertTrue(hasFetchUser);

        // Verify runnable
        assertTrue(result.isRunnable());
    }

    @Test
    @DisplayName("Stage 3: Python AST Parsing, Translation & Stdlib Filtering")
    void testPythonParsing() {
        String code = """
            import os
            import sys
            import json
            import cv2
            from PIL import Image
            import requests
            import yaml
            
            def process_image(path):
                db_url = os.environ.get("DATABASE_URL")
                print("Processing:", path)
            """;

        ASTService.ASTAnalysisResult result = astService.parseSnippet("python", code);

        assertEquals("python", result.detectedLanguage());

        // Packages translated correctly
        assertTrue(result.dependencyNames().contains("opencv-python")); // cv2 -> opencv-python
        assertTrue(result.dependencyNames().contains("Pillow"));        // PIL -> Pillow
        assertTrue(result.dependencyNames().contains("PyYAML"));        // yaml -> PyYAML
        assertTrue(result.dependencyNames().contains("requests"));

        // Python stdlib os, sys, json must be filtered out
        assertFalse(result.dependencyNames().contains("os"));
        assertFalse(result.dependencyNames().contains("sys"));
        assertFalse(result.dependencyNames().contains("json"));

        // Install commands for pip and uv
        assertTrue(result.installCommands().get("pip").startsWith("pip install "));
        assertTrue(result.installCommands().get("pip").contains("opencv-python"));
        assertTrue(result.installCommands().get("uv").startsWith("uv add "));

        // Environment variables
        assertTrue(result.envKeys().contains("DATABASE_URL"));
    }

    @Test
    @DisplayName("Stage 3: Go and Rust Package Extraction")
    void testGoAndRustParsing() {
        String goCode = """
            package main
            import (
                "fmt"
                "net/http"
                "github.com/gin-gonic/gin"
            )
            func main() {
                r := gin.Default()
                r.Run()
            }
            """;

        ASTService.ASTAnalysisResult goResult = astService.parseSnippet("go", goCode);
        assertEquals("go", goResult.detectedLanguage());
        assertTrue(goResult.dependencyNames().contains("github.com/gin-gonic/gin"));
        assertFalse(goResult.dependencyNames().contains("fmt"));
        assertFalse(goResult.dependencyNames().contains("net/http"));
        assertTrue(goResult.installCommands().get("go").contains("github.com/gin-gonic/gin"));

        String rustCode = """
            use std::collections::HashMap;
            use tokio::time::sleep;
            use serde::{Serialize, Deserialize};
            
            fn main() {
                println!("Rust snippet");
            }
            """;

        ASTService.ASTAnalysisResult rustResult = astService.parseSnippet("rust", rustCode);
        assertEquals("rust", rustResult.detectedLanguage());
        assertTrue(rustResult.dependencyNames().contains("tokio"));
        assertTrue(rustResult.dependencyNames().contains("serde"));
        assertFalse(rustResult.dependencyNames().contains("std"));
        assertTrue(rustResult.installCommands().get("cargo").contains("cargo add tokio serde"));
    }
}
