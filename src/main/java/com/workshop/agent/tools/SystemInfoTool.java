package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Educational Tool: System Environment and Time
 *
 * Demonstrates grounding an LLM with real-time environment information that the LLM's
 * training weights cannot know (current date, exact time, host environment).
 */
@Component
public class SystemInfoTool {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoTool.class);

    @Tool("Returns the current date, time, and timezone of the server.")
    public String getCurrentDateTime(
            @P("Optional timezone ID, e.g. 'UTC', 'America/New_York', 'Europe/Berlin'. Defaults to server timezone if blank.") String timezone
    ) {
        log.info("[TOOL:SystemInfo] getCurrentDateTime(timezone={})", timezone);
        ZoneId zoneId;
        try {
            if (timezone != null && !timezone.isBlank()) {
                zoneId = ZoneId.of(timezone.trim());
            } else {
                zoneId = ZoneId.systemDefault();
            }
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z (VV)");
        return "Current Server Date/Time: " + now.format(formatter);
    }

    @Tool("Returns host hardware, operating system, and Java runtime memory stats.")
    public String getHostRuntimeMetrics() {
        log.info("[TOOL:SystemInfo] getHostRuntimeMetrics()");
        Runtime runtime = Runtime.getRuntime();
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);
        long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMb = totalMemoryMb - freeMemoryMb;
        int availableProcessors = runtime.availableProcessors();
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String javaVersion = System.getProperty("java.version");

        return String.format("""
                Host Runtime Info:
                - OS: %s (v%s)
                - Java Version: %s
                - Available CPU Cores: %d
                - JVM Heap Used: %d MB / %d MB (Max: %d MB)
                """, osName, osVersion, javaVersion, availableProcessors, usedMemoryMb, totalMemoryMb, maxMemoryMb);
    }
}
