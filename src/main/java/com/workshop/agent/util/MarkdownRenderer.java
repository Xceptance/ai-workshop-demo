package com.workshop.agent.util;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder()
                .escapeHtml(false)
                .sanitizeUrls(true)
                .build();
    }

    public String renderHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        try {
            var document = parser.parse(markdown);
            return renderer.render(document);
        } catch (Exception e) {
            return "<p>" + markdown.replace("\n", "<br/>") + "</p>";
        }
    }
}
