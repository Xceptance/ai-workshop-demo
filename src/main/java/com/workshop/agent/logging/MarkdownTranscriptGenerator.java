package com.workshop.agent.logging;

import com.workshop.agent.model.AgentSession;
import com.workshop.agent.model.AgentStepEvent;
import com.workshop.agent.model.ChatMessageDto;
import com.workshop.agent.model.TokenUsageStats;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-readable, rich Markdown audit transcripts of agent sessions.
 */
@Component
public class MarkdownTranscriptGenerator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public String generateMarkdown(AgentSession session) {
        StringBuilder sb = new StringBuilder();

        sb.append("# AI Mini-Agent Execution Audit Transcript\n\n");
        sb.append("**Session ID:** `").append(session.getId()).append("`  \n");
        sb.append("**Title:** ").append(session.getTitle()).append("  \n");
        sb.append("**Model:** `").append(session.getModelName()).append("`  \n");
        sb.append("**Created:** ").append(TIME_FORMATTER.format(session.getCreatedAt())).append("  \n");

        TokenUsageStats stats = session.getTotalTokenStats();
        sb.append("**Token Usage:** Input: `").append(stats.inputTokens())
                .append("` | Output: `").append(stats.outputTokens())
                .append("` | Total: `").append(stats.totalTokens())
                .append("` | Est. Cost: `").append(stats.formattedCost())
                .append("` | Execution Time: `").append(stats.formattedDuration()).append("`\n\n");

        sb.append("---\n\n");
        sb.append("## System Prompt\n\n");
        sb.append("```\n").append(session.getSystemPrompt().trim()).append("\n```\n\n");

        sb.append("---\n\n");
        sb.append("## Conversation & Execution Trace\n\n");

        for (ChatMessageDto msg : session.getMessages()) {
            if ("user".equalsIgnoreCase(msg.role())) {
                sb.append("### 👤 User\n\n");
                sb.append("> ").append(msg.content().replace("\n", "\n> ")).append("\n\n");
            } else if ("assistant".equalsIgnoreCase(msg.role())) {
                sb.append("### 🤖 Agent\n\n");
                sb.append(msg.content()).append("\n\n");
            }
        }

        sb.append("---\n\n");
        sb.append("## Granular ReAct Lifecycle Events\n\n");

        for (AgentStepEvent event : session.getStepEvents()) {
            sb.append("#### Step ").append(event.stepNumber()).append(": [").append(event.type().name()).append("] ")
                    .append(event.title()).append("\n\n");

            if (event.summary() != null && !event.summary().isBlank()) {
                sb.append("**Summary:** ").append(event.summary()).append("\n\n");
            }

            if (event.tokenStats() != null && event.tokenStats().totalTokens() > 0) {
                sb.append("**Step Stats:** `").append(event.tokenStats().totalTokens()).append(" tokens` | `")
                        .append(event.tokenStats().formattedDuration()).append("`\n\n");
            }

            if (event.requestPayload() != null && !event.requestPayload().isBlank()) {
                sb.append("<details><summary>📤 View Request / Ask Payload</summary>\n\n");
                sb.append("```json\n").append(event.requestPayload().trim()).append("\n```\n\n");
                sb.append("</details>\n\n");
            }
            if (event.responsePayload() != null && !event.responsePayload().isBlank()) {
                sb.append("<details><summary>📥 View Response / Decision Payload</summary>\n\n");
                sb.append("```json\n").append(event.responsePayload().trim()).append("\n```\n\n");
                sb.append("</details>\n\n");
            }
            if ((event.requestPayload() == null || event.requestPayload().isBlank())
                    && (event.responsePayload() == null || event.responsePayload().isBlank())
                    && event.rawContent() != null && !event.rawContent().isBlank()) {
                sb.append("<details><summary>🔍 View Raw Step Payload</summary>\n\n");
                sb.append("```json\n").append(event.rawContent().trim()).append("\n```\n\n");
                sb.append("</details>\n\n");
            }
        }

        sb.append("\n---\n*Generated automatically by AI Mini-Agent Workshop Audit Logger.*\n");
        return sb.toString();
    }
}
