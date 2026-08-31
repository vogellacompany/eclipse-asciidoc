package com.vogella.asciidoc.lsp.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Language server protocol of the AsciiDoc server, extended with a preview request.
 */
public interface AsciidocLanguageServerApi extends LanguageServer {

	/** Renders the current in-memory content of the document to a complete HTML page. */
	@JsonRequest(value = "asciidoc/preview", useSegment = false)
	CompletableFuture<String> preview(TextDocumentIdentifier document);
}
