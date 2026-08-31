package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;
import com.vogella.asciidoc.lsp.server.AsciidocTextDocumentService;

class AsciidocDiagnosticsTest {

	@TempDir
	Path tempDir;

	private AsciidocLanguageServer server;
	private AsciidocTextDocumentService service;
	private CompletableFuture<List<Diagnostic>> diagnosticsFuture;

	@BeforeEach
	void setUp() {
		server = new AsciidocLanguageServer();
		service = (AsciidocTextDocumentService) server.getTextDocumentService();
		diagnosticsFuture = new CompletableFuture<>();
		
		server.setRemoteProxy(new LanguageClient() {
			@Override
			public void telemetryEvent(Object object) {}
			@Override
			public void publishDiagnostics(PublishDiagnosticsParams params) {
				diagnosticsFuture.complete(params.getDiagnostics());
			}
			@Override
			public void showMessage(MessageParams messageParams) {}
			@Override
			public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
				return null;
			}
			@Override
			public void logMessage(MessageParams message) {}
		});
	}

	@Test
	void testDiagnostics() throws Exception {
		Path doc = tempDir.resolve("doc.adoc");
		String content = "== Title\n\n[[anchor1]]\n[[anchor1]]\ninclude::missing.adoc[]\nimage::missing.png[]\nxref:missing.adoc[]\nxref:missing_id[]\n";
		Files.writeString(doc, content);
		
		DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams();
		openParams.setTextDocument(new TextDocumentItem(doc.toUri().toString(), "asciidoc", 1, content));
		service.didOpen(openParams);
		
		List<Diagnostic> diagnostics = diagnosticsFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
		
		assertEquals(5, diagnostics.size(), "Should find 5 diagnostics");
		
		boolean duplicateAnchor = false;
		boolean missingInclude = false;
		boolean missingImage = false;
		boolean missingXrefFile = false;
		boolean missingXrefId = false;
		
		for (Diagnostic d : diagnostics) {
			assertEquals("asciidoc", d.getSource());
			if (String.valueOf(d.getMessage()).contains("Duplicate anchor id")) duplicateAnchor = true;
			if (String.valueOf(d.getMessage()).contains("Include file not found")) missingInclude = true;
			if (String.valueOf(d.getMessage()).contains("Image file not found")) missingImage = true;
			if (String.valueOf(d.getMessage()).contains("Target file not found")) missingXrefFile = true;
			if (String.valueOf(d.getMessage()).contains("Unresolved internal reference")) missingXrefId = true;
		}
		
		assertTrue(duplicateAnchor);
		assertTrue(missingInclude);
		assertTrue(missingImage);
		assertTrue(missingXrefFile);
		assertTrue(missingXrefId);
	}
}
