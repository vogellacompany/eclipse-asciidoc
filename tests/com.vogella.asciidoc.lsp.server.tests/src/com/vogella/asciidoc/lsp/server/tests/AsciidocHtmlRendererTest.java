package com.vogella.asciidoc.lsp.server.tests;

import com.vogella.asciidoc.lsp.server.render.AsciidocHtmlRenderer;
import com.vogella.asciidoc.lsp.server.render.RenderOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class AsciidocHtmlRendererTest {

    @TempDir
    Path tempDir;

    @Test
    public void testHeadingsWithIds() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "== Hello World\n\n[[my-anchor]]\n== Heading 2";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<h2 id=\"_hello_world\">Hello World</h2>"));
        assertTrue(html.contains("<h2 id=\"my-anchor\">Heading 2</h2>"));
    }

    @Test
    public void testInlineFormatting() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "This is **bold**, __italic__, and `mono`.";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<em>italic</em>"));
        assertTrue(html.contains("<code>mono</code>"));
    }

    @Test
    public void testLists() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "* Item 1\n* Item 2\n\n. One\n. Two";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<ul>\n<li>Item 1</li>\n<li>Item 2</li>\n</ul>"));
        assertTrue(html.contains("<ol>\n<li>One</li>\n<li>Two</li>\n</ol>"));
    }

    @Test
    public void testSourceBlock() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "[source,java]\n----\nclass Test {}\n----";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<code class=\"language-java\">"));
        assertTrue(html.contains("class Test {}"));
    }

    @Test
    public void testAdmonitionParagraph() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "NOTE: some text";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<div class=\"admonition note\">"));
        assertTrue(html.contains("some text"));
    }

    @Test
    public void testTable() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "[options=\"header\"]\n|===\n|Col 1|Col 2\n|Val 1|Val 2\n|===";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<th>Col 1</th>"));
        assertTrue(html.contains("<td>Val 1</td>"));
    }

    @Test
    public void testImageInlined() throws Exception {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        Path imgPath = tempDir.resolve("test.png");
        ImageIO.write(img, "png", imgPath.toFile());

        String text = "image::test.png[]";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("data:image/png;base64,"));
    }

    @Test
    public void testMissingImage() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "image::missing.png[]";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<span class=\"missing\">image not found: missing.png</span>"));
    }

    @Test
    public void testInclude() throws Exception {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        Path includePath = tempDir.resolve("included.adoc");
        Files.writeString(includePath, "Included text");

        String text = "include::included.adoc[]";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<p>Included text</p>"));
    }

    @Test
    public void testIncludeCycle() throws Exception {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        Path p1 = tempDir.resolve("cycle1.adoc");
        Path p2 = tempDir.resolve("cycle2.adoc");
        Files.writeString(p1, "include::cycle2.adoc[]");
        Files.writeString(p2, "include::cycle1.adoc[]");

        String text = "include::cycle1.adoc[]";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<p class=\"error\">include cycle or depth limit reached: cycle1.adoc</p>"));
    }

    @Test
    public void testXref() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "<<anchor>>";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<a href=\"#anchor\">anchor</a>"));
    }

    @Test
    public void testLink() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "link:target.adoc[text]";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<a href=\"file:///"));
        assertTrue(html.contains("text</a>"));
    }

    @Test
    public void testAttributeSubstitution() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = ":name: value\n\n{name}";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("<p>value</p>"));
    }

    @Test
    public void testHtmlEscaping() {
        AsciidocHtmlRenderer renderer = new AsciidocHtmlRenderer();
        String text = "<script>alert('xss')</script>";
        String html = renderer.render(text, RenderOptions.simple(tempDir));
        assertTrue(html.contains("&lt;script&gt;alert('xss')&lt;/script&gt;"));
    }
}
