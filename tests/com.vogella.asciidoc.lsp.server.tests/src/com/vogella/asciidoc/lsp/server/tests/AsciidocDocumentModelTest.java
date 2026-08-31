package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.vogella.asciidoc.lsp.server.AsciidocDocumentModel;

public class AsciidocDocumentModelTest {

    @Test
    public void testModelParsing() {
        String doc = "= Document Title\n"
                + ":author: John Doe\n"
                + "\n"
                + "[[intro]]\n"
                + "== Introduction\n"
                + "This is a test.\n"
                + "image::test.png[]\n"
                + "include::other.adoc[]\n"
                + "----\n"
                + "code block\n"
                + "----\n"
                + "<<intro, Intro>>";
        
        AsciidocDocumentModel model = new AsciidocDocumentModel(doc);
        
        assertEquals(2, model.getHeadings().size());
        assertEquals("Document Title", model.getHeadings().get(0).title);
        assertEquals(1, model.getHeadings().get(0).level);
        assertEquals("Introduction", model.getHeadings().get(1).title);
        assertEquals(2, model.getHeadings().get(1).level);
        
        assertEquals(1, model.getAttributes().size());
        assertEquals("John Doe", model.getAttributes().get("author"));
        
        assertEquals(1, model.getAnchors().size());
        assertEquals("intro", model.getAnchors().get(0).id);
        
        assertEquals(3, model.getMacros().size());
        assertEquals("image", model.getMacros().get(0).type);
        assertEquals("test.png", model.getMacros().get(0).target);
        
        assertEquals("include", model.getMacros().get(1).type);
        assertEquals("other.adoc", model.getMacros().get(1).target);
        
        assertEquals("xref", model.getMacros().get(2).type);
        assertEquals("intro", model.getMacros().get(2).target);
        assertEquals(" Intro", model.getMacros().get(2).attributes);
        
        assertEquals(1, model.getBlocks().size());
        assertEquals("-", model.getBlocks().get(0).delimiter);
        assertEquals(8, model.getBlocks().get(0).startLine);
        assertEquals(10, model.getBlocks().get(0).endLine);
    }
}
