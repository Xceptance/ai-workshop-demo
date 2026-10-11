package com.workshop.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private String systemPrompt = """
        You are an intelligent, transparent AI Mini-Agent designed for educational workshops.
        You have access to a set of specialized tools.
        When a user asks a question, carefully consider whether you need to call one or more tools to retrieve accurate facts, perform calculations, or inspect local workspace files.
        Always explain your reasoning clearly and provide helpful, concise answers.
        """;

    private int maxSteps = 25;
    private LoggingProperties logging = new LoggingProperties();

    public static class LoggingProperties {
        private boolean diskEnabled = true;
        private String logDir = "logs/sessions";
        private boolean saveRawPayloads = true;
        private boolean generateMarkdownTranscript = true;

        public boolean isDiskEnabled() {
            return diskEnabled;
        }

        public void setDiskEnabled(boolean diskEnabled) {
            this.diskEnabled = diskEnabled;
        }

        public String getLogDir() {
            return logDir;
        }

        public void setLogDir(String logDir) {
            this.logDir = logDir;
        }

        public boolean isSaveRawPayloads() {
            return saveRawPayloads;
        }

        public void setSaveRawPayloads(boolean saveRawPayloads) {
            this.saveRawPayloads = saveRawPayloads;
        }

        public boolean isGenerateMarkdownTranscript() {
            return generateMarkdownTranscript;
        }

        public void setGenerateMarkdownTranscript(boolean generateMarkdownTranscript) {
            this.generateMarkdownTranscript = generateMarkdownTranscript;
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public void setLogging(LoggingProperties logging) {
        this.logging = logging;
    }
}
