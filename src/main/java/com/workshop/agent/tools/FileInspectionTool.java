package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Educational Tool: Local File and Workspace Inspection
 *
 * Demonstrates how agents can read from and inspect local resources (documents, code, logs)
 * to answer queries with concrete context rather than guessing.
 */
@Component
public class FileInspectionTool {

    private static final Logger log = LoggerFactory.getLogger(FileInspectionTool.class);
    private static final Path WORKSPACE_ROOT = Paths.get(".").toAbsolutePath().normalize();

    @Tool("Lists files and directories in a given relative directory path.")
    public String listDirectory(
            @P("Relative directory path from workspace root, e.g. '.' or 'docs' or 'src'") String relativePath
    ) {
        log.info("[TOOL:FileInspection] listDirectory(relativePath={})", relativePath);
        try {
            Path targetPath = resolveSafePath(relativePath);
            if (!Files.exists(targetPath)) {
                return "Directory does not exist: " + relativePath;
            }
            if (!Files.isDirectory(targetPath)) {
                return "Path is a file, not a directory: " + relativePath;
            }

            try (Stream<Path> stream = Files.list(targetPath)) {
                List<String> entries = stream.map(p -> {
                    boolean isDir = Files.isDirectory(p);
                    long size = 0;
                    try {
                        size = isDir ? 0 : Files.size(p);
                    } catch (IOException ignored) {}
                    return String.format("%s %-30s %s",
                            isDir ? "[DIR] " : "[FILE]",
                            p.getFileName().toString(),
                            isDir ? "" : "(" + size + " bytes)");
                }).sorted().collect(Collectors.toList());

                return "Contents of '" + relativePath + "' (" + entries.size() + " items):\n" + String.join("\n", entries);
            }
        } catch (Exception e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool("Reads the full or partial text content of a file in the workspace.")
    public String readFile(
            @P("Relative path of the file to read, e.g. 'README.md' or 'pom.xml'") String relativePath,
            @P("Optional max lines to read (e.g. 50). Set to 0 to read entire file.") int maxLines
    ) {
        log.info("[TOOL:FileInspection] readFile(relativePath={}, maxLines={})", relativePath, maxLines);
        try {
            Path targetPath = resolveSafePath(relativePath);
            if (!Files.exists(targetPath)) {
                return "File not found: " + relativePath;
            }
            if (Files.isDirectory(targetPath)) {
                return "Path is a directory, not a file: " + relativePath;
            }

            List<String> lines = Files.readAllLines(targetPath, StandardCharsets.UTF_8);
            if (maxLines > 0 && lines.size() > maxLines) {
                String snippet = String.join("\n", lines.subList(0, maxLines));
                return snippet + "\n\n... [TRUNCATED: Showing first " + maxLines + " of " + lines.size() + " lines]";
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            return "Error reading file '" + relativePath + "': " + e.getMessage();
        }
    }

    @Tool("Searches for a keyword or pattern across text files in a workspace directory.")
    public String searchFiles(
            @P("Keyword or term to search for") String searchTerm,
            @P("Relative directory to search in, e.g. 'docs' or 'src'") String relativeDir
    ) {
        log.info("[TOOL:FileInspection] searchFiles(searchTerm={}, relativeDir={})", searchTerm, relativeDir);
        try {
            Path targetDir = resolveSafePath(relativeDir);
            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
                return "Directory not found: " + relativeDir;
            }

            StringBuilder results = new StringBuilder();
            int matchCount = 0;

            try (Stream<Path> stream = Files.walk(targetDir, 5)) {
                List<Path> files = stream.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (line.toLowerCase().contains(searchTerm.toLowerCase())) {
                                results.append(String.format("%s (Line %d): %s\n",
                                        WORKSPACE_ROOT.relativize(file.toAbsolutePath()),
                                        i + 1,
                                        line.trim()));
                                matchCount++;
                                if (matchCount >= 25) {
                                    results.append("... [Match limit of 25 reached]\n");
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Skip binary or unreadable files
                    }
                    if (matchCount >= 25) break;
                }
            }

            if (matchCount == 0) {
                return "No occurrences of '" + searchTerm + "' found in '" + relativeDir + "'.";
            }
            return "Found " + matchCount + " match(es) for '" + searchTerm + "':\n" + results;
        } catch (Exception e) {
            return "Error searching files: " + e.getMessage();
        }
    }

    private Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return WORKSPACE_ROOT;
        }
        Path resolved = WORKSPACE_ROOT.resolve(relativePath).normalize();
        // Prevent path traversal outside workspace
        if (!resolved.startsWith(WORKSPACE_ROOT)) {
            throw new SecurityException("Path traversal outside workspace root is disallowed.");
        }
        return resolved;
    }
}
