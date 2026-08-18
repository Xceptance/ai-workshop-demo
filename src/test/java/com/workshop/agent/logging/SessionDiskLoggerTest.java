package com.workshop.agent.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.ChatMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionDiskLoggerTest {

    @TempDir
    Path tempDir;

    private SessionDiskLogger diskLogger;

    @BeforeEach
    void setUp() {
        AgentProperties props = new AgentProperties();
        props.getLogging().setLogDir(tempDir.toString());
        props.getLogging().setDiskEnabled(true);
        props.getLogging().setSaveRawPayloads(true);
        props.getLogging().setGenerateMarkdownTranscript(true);

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MarkdownTranscriptGenerator gen = new MarkdownTranscriptGenerator();
        diskLogger = new SessionDiskLogger(props, gen, mapper);
    }

    @Test
    void testSaveSessionSnapshotCreatesFiles() {
        AgentSession session = new AgentSession("test-session-456", "Test Session", "gemini-3.7-flash", "System Prompt", 0.2);
        session.addMessage(ChatMessageDto.user("msg-1", "What is the weather?"));

        List<Object> rawPayloads = List.of(
                Map.of("iteration", 1, "requestMessages", List.of(
                        Map.of("type", "SYSTEM", "text", "System Prompt"),
                        Map.of("type", "USER", "text", "What is the weather?")
                ))
        );

        diskLogger.saveSessionSnapshot(session, rawPayloads);

        Path sessionDir = tempDir.resolve("test-session-456");
        assertTrue(Files.exists(sessionDir.resolve("summary.json")));
        assertTrue(Files.exists(sessionDir.resolve("raw_payloads.json")));
        assertTrue(Files.exists(sessionDir.resolve("transcript.md")));
    }
}
