package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;

class AsciidocLanguageServerTest {

	@Test
	void shutdownReturnsTheNullResultRequiredByLsp() throws Exception {
		AsciidocLanguageServer server = new AsciidocLanguageServer();

		assertNull(server.shutdown().get(5, TimeUnit.SECONDS));
	}
}
