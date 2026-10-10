package com.workshop.agent.util;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create()
        );

        this.parser = Parser.builder()
                .extensions(extensions)
                .build();

        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                    if (node instanceof TableBlock) {
                        attributes.put("class", "table table-hover table-bordered my-3 align-middle");
                    }
                })
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
