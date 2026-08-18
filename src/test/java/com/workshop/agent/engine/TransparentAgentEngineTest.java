package com.workshop.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.logging.ConsoleAnsiLogger;
import com.workshop.agent.logging.MarkdownTranscriptGenerator;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.ChatMessageDto;
import com.workshop.agent.tools.*;
import com.workshop.agent.util.MarkdownRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TransparentAgentEngineTest {

    @TempDir
    Path tempLogDir;

    private TransparentAgentEngine engine;
    private SessionDiskLogger diskLogger;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        CalculatorTool calc = new CalculatorTool();
        SystemInfoTool sys = new SystemInfoTool();
        FileInspectionTool fileTool = new FileInspectionTool();
        WebSearchMockTool webTool = new WebSearchMockTool();
        ToolRegistry registry = new ToolRegistry(calc, sys, fileTool, webTool, mapper);

        AgentProperties properties = new AgentProperties();
        properties.getLogging().setLogDir(tempLogDir.toString());

        MarkdownTranscriptGenerator transcriptGen = new MarkdownTranscriptGenerator();
        diskLogger = new SessionDiskLogger(properties, transcriptGen, mapper);
        ConsoleAnsiLogger consoleLogger = new ConsoleAnsiLogger();
        MarkdownRenderer mdRenderer = new MarkdownRenderer();
        MockLanguageModel mockModel = new MockLanguageModel();

        engine = new TransparentAgentEngine(
                mockModel,
                registry,
                properties,
                diskLogger,
                consoleLogger,
                mdRenderer,
                mapper
        );
    }

    @Test
    void testProcessTurnWithMockModel() {
        String sessionId = "test-session-123";
        ChatMessageDto result = engine.processTurn(sessionId, "Please calculate 42 * 8");

        assertNotNull(result);
        assertEquals("assistant", result.role());
        assertNotNull(result.content());
        assertTrue(result.content().contains("calculate"));

        AgentSession session = engine.getOrCreateSession(sessionId);
        assertEquals(2, session.getMessages().size()); // 1 user + 1 assistant
        assertTrue(session.getStepEvents().size() >= 4); // User Input, Context Assembly, Model Request, Tool Call, Execution, Observation, Final Synthesis
    }
}
