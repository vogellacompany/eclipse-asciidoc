package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;
import com.vogella.asciidoc.lsp.server.AsciidocTextDocumentService;

/** Tests the preview request by opening a document and rendering it via the service. */
class AsciidocPreviewTest {

	@TempDir
	Path tempDir;

	private AsciidocLanguageServer server;
	private AsciidocTextDocumentService service;
	private String docUri;

	@BeforeEach
	void setUp() throws IOException {
		server = new AsciidocLanguageServer();
		service = (AsciidocTextDocumentService) server.getTextDocumentService();

		// Create a small PNG image
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, 0xFF0000);
		Path imgFile = tempDir.resolve("pic.png");
		ImageIO.write(img, "png", imgFile.toFile());

		// Write the document
		Path doc = tempDir.resolve("doc.adoc");
		Files.writeString(doc, "= Title\n\nHello world\n\nimage::pic.png[test image]\n");
		docUri = doc.toUri().toString();

		// Open the document in the service
		DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams();
		openParams.setTextDocument(new TextDocumentItem(docUri, "asciidoc", 1, Files.readString(doc)));
		service.didOpen(openParams);
	}

	@Test
	void previewReturnsHtmlWithHeadingAndImage() throws ExecutionException, InterruptedException {
		String html = service.preview(docUri).get();
		assertNotNull(html);
		assertTrue(html.contains("<h1"), "HTML should contain a heading");
		assertTrue(html.contains("Title"), "HTML should contain the heading text");
		assertTrue(html.contains("<img src=\"data:image/png;base64,"), "image should be inlined as data URI");
	}
}
