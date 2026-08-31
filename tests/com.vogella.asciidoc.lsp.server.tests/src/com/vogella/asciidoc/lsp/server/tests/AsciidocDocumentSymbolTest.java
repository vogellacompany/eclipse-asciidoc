package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;
import com.vogella.asciidoc.lsp.server.AsciidocTextDocumentService;

class AsciidocDocumentSymbolTest {

	private AsciidocTextDocumentService service;
	private final String docUri = "file:///symbols.adoc";

	@BeforeEach
	void setUp() {
		AsciidocLanguageServer server = new AsciidocLanguageServer();
		service = (AsciidocTextDocumentService) server.getTextDocumentService();
	}

	private List<DocumentSymbol> outline(String content) throws InterruptedException, ExecutionException {
		service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(docUri, "asciidoc", 1, content)));
		DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(docUri));
		List<Either<SymbolInformation, DocumentSymbol>> result = service.documentSymbol(params).get();
		return result.stream().map(Either::getRight).toList();
	}

	@Test
	void testNesting() throws Exception {
		String content = """
				= Document Title

				Intro paragraph.

				== Section One

				Text.

				=== Sub Section

				More text.

				== Section Two

				End.
				""";
		List<DocumentSymbol> roots = outline(content);

		assertEquals(1, roots.size());
		DocumentSymbol title = roots.get(0);
		assertEquals("Document Title", title.getName());
		assertEquals(2, title.getChildren().size());

		DocumentSymbol sectionOne = title.getChildren().get(0);
		assertEquals("Section One", sectionOne.getName());
		assertEquals(1, sectionOne.getChildren().size());
		assertEquals("Sub Section", sectionOne.getChildren().get(0).getName());

		DocumentSymbol sectionTwo = title.getChildren().get(1);
		assertEquals("Section Two", sectionTwo.getName());
		assertTrue(sectionTwo.getChildren().isEmpty());
	}

	@Test
	void testEqualsInsideListingBlockIsNotHeading() throws Exception {
		String content = """
				= Title

				----
				= This is not a heading
				== Neither is this
				----

				== Real Section
				""";
		List<DocumentSymbol> roots = outline(content);

		assertEquals(1, roots.size());
		DocumentSymbol title = roots.get(0);
		assertEquals("Title", title.getName());
		assertEquals(1, title.getChildren().size());
		assertEquals("Real Section", title.getChildren().get(0).getName());
	}

	@Test
	void testSelectionRangeIsHeadingLine() throws Exception {
		String content = "= Only Title\n\nBody line.\n";
		List<DocumentSymbol> roots = outline(content);

		assertEquals(1, roots.size());
		DocumentSymbol title = roots.get(0);
		assertNotNull(title.getSelectionRange());
		assertEquals(0, title.getSelectionRange().getStart().getLine());
		assertEquals(0, title.getSelectionRange().getStart().getCharacter());
		assertEquals(0, title.getSelectionRange().getEnd().getLine());
		assertEquals("= Only Title".length(), title.getSelectionRange().getEnd().getCharacter());
	}
}
