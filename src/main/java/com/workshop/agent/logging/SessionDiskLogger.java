package com.workshop.agent.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.AgentStepEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Persists comprehensive execution audit logs to disk for each agent session.
 * Outputs:
 * - summary.json
 * - trace_events.jsonl
 * - raw_payloads.json
 * - transcript.md
 */
@Component
public class SessionDiskLogger {

    private static final Logger log = LoggerFactory.getLogger(SessionDiskLogger.class);

    private final AgentProperties agentProperties;
    private final MarkdownTranscriptGenerator transcriptGenerator;
    private final ObjectMapper objectMapper;

    public SessionDiskLogger(
            AgentProperties agentProperties,
            MarkdownTranscriptGenerator transcriptGenerator,
            ObjectMapper objectMapper
    ) {
        this.agentProperties = agentProperties;
        this.transcriptGenerator = transcriptGenerator;
        this.objectMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public Path getSessionDir(String sessionId) {
        String logDirStr = agentProperties.getLogging().getLogDir();
        return Paths.get(logDirStr, sessionId).toAbsolutePath().normalize();
    }

    public void appendStepEvent(AgentSession session, AgentStepEvent event) {
        if (!agentProperties.getLogging().isDiskEnabled()) {
            return;
        }

        try {
            Path sessionDir = getSessionDir(session.getId());
            Files.createDirectories(sessionDir);

            // Append to trace_events.jsonl
            Path jsonlFile = sessionDir.resolve("trace_events.jsonl");
            String line = objectMapper.writeValueAsString(event);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonlFile.toFile(), StandardCharsets.UTF_8, true))) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to append step event to disk log for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    public void saveSessionSnapshot(AgentSession session, List<Object> rawPayloads) {
        if (!agentProperties.getLogging().isDiskEnabled()) {
            return;
        }

        try {
            Path sessionDir = getSessionDir(session.getId());
            Files.createDirectories(sessionDir);

            // 1. summary.json
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sessionId", session.getId());
            summary.put("title", session.getTitle());
            summary.put("modelName", session.getModelName());
            summary.put("thinkingLevel", session.getThinkingLevel());
            summary.put("createdAt", session.getCreatedAt());
            summary.put("updatedAt", Instant.now());
            summary.put("messageCount", session.getMessages().size());
            summary.put("stepEventCount", session.getStepEvents().size());
            summary.put("tokenStats", session.getTotalTokenStats());
            summary.put("systemPrompt", session.getSystemPrompt());

            File summaryFile = sessionDir.resolve("summary.json").toFile();
            objectMapper.writeValue(summaryFile, summary);

            // 2. raw_payloads.json
            if (agentProperties.getLogging().isSaveRawPayloads() && rawPayloads != null) {
                File payloadsFile = sessionDir.resolve("raw_payloads.json").toFile();
                objectMapper.writeValue(payloadsFile, rawPayloads);
            }

            // 3. transcript.md
            if (agentProperties.getLogging().isGenerateMarkdownTranscript()) {
                String mdContent = transcriptGenerator.generateMarkdown(session);
                Path transcriptPath = sessionDir.resolve("transcript.md");
                Files.writeString(transcriptPath, mdContent, StandardCharsets.UTF_8);
            }

            log.info("Persisted complete session audit snapshot to: {}", sessionDir);
        } catch (IOException e) {
            log.error("Failed to write session snapshot to disk for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listRecentSessionSummaries() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Path baseDir = Paths.get(agentProperties.getLogging().getLogDir());
            if (!Files.exists(baseDir)) return list;

            try (var stream = Files.list(baseDir)) {
                List<Path> sessionDirs = stream.filter(Files::isDirectory).toList();
                for (Path dir : sessionDirs) {
                    Path summaryFile = dir.resolve("summary.json");
                    if (Files.exists(summaryFile)) {
                        try {
                            Map<String, Object> map = objectMapper.readValue(summaryFile.toFile(), Map.class);
                            list.add(map);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not list session directories: {}", e.getMessage());
        }
        return list;
    }
}
