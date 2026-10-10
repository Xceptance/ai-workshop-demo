package com.workshop.agent.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    private MarkdownRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownRenderer();
    }

    @Test
    void testRenderMarkdownTableProperly() {
        String markdown = """
                Exact Token Breakdown (cl100k / typical BPE)
                
                Here is how the sentence is sliced into individual tokens:
                
                | # | Token representation | Notes |
                |---|---|---|
                | 1 | `Depending` | Capitalized common word |
                | 2 | `on` | Leading space + word |
                | 3 | `whether` | Leading space + word |
                """;

        String html = renderer.renderHtml(markdown);

        assertNotNull(html);
        assertTrue(html.contains("<table"), "Output should contain a <table> element");
        assertTrue(html.contains("class=\"table"), "Table should have bootstrap table classes");
        assertTrue(html.contains("<thead>"), "Output should contain <thead>");
        assertTrue(html.contains("<tbody>"), "Output should contain <tbody>");
        assertTrue(html.contains("<th>Token representation</th>"), "Header cells should be parsed as <th>");
        assertTrue(html.contains("<td><code>Depending</code></td>"), "Table content should be parsed into <td> cells");
        assertFalse(html.contains("<p>| # | Token representation"), "Table should NOT be rendered as a raw plain text paragraph");
    }

    @Test
    void testAutolinkAndStrikethrough() {
        String md = "Check https://google.com and ~~deleted text~~";
        String html = renderer.renderHtml(md);

        assertTrue(html.contains("href=\"https://google.com\""), "URLs should be autolinked");
        assertTrue(html.contains("<del>deleted text</del>"), "Strikethrough should be rendered as <del>");
    }

    @Test
    void testBlankAndNullMarkdown() {
        assertEquals("", renderer.renderHtml(null));
        assertEquals("", renderer.renderHtml("   "));
    }
}
