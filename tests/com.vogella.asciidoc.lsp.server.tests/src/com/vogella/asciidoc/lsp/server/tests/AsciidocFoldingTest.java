package com.vogella.asciidoc.lsp.server.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;
import com.vogella.asciidoc.lsp.server.AsciidocTextDocumentService;

class AsciidocFoldingTest {

	private AsciidocTextDocumentService service;
	private final String docUri = "file:///folding.adoc";

	@BeforeEach
	void setUp() {
		AsciidocLanguageServer server = new AsciidocLanguageServer();
		service = (AsciidocTextDocumentService) server.getTextDocumentService();
	}

	private List<FoldingRange> getFoldingRanges(String content) throws InterruptedException, ExecutionException {
		service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(docUri, "asciidoc", 1, content)));
		FoldingRangeRequestParams params = new FoldingRangeRequestParams(new TextDocumentIdentifier(docUri));
		return service.foldingRange(params).get();
	}

	@Test
	void testSectionFolding() throws Exception {
		String content = "= Document Title\n" +
				"\n" +
				"Intro.\n" +
				"\n" +
				"== Section 1\n" +
				"\n" +
				"Text.\n" +
				"\n" +
				"== Section 2\n" +
				"\n" +
				"End.\n";
		
		List<FoldingRange> ranges = getFoldingRanges(content);
		assertEquals(3, ranges.size());
		
		FoldingRange docTitle = ranges.get(0);
		assertEquals(0, docTitle.getStartLine());
		assertEquals(10, docTitle.getEndLine());
		assertEquals(FoldingRangeKind.Region, docTitle.getKind());
		
		FoldingRange sec1 = ranges.get(1);
		assertEquals(4, sec1.getStartLine());
		assertEquals(7, sec1.getEndLine());
		assertEquals(FoldingRangeKind.Region, sec1.getKind());
	}

	@Test
	void testBlockFolding() throws Exception {
		String content = "= Title\n" +
				"\n" +
				"----\n" +
				"Source code\n" +
				"----\n" +
				"\n" +
				"////\n" +
				"Comment block\n" +
				"////\n";
				
		List<FoldingRange> ranges = getFoldingRanges(content);
		assertEquals(3, ranges.size());
		
		FoldingRange sourceBlock = ranges.get(1);
		assertEquals(2, sourceBlock.getStartLine());
		assertEquals(4, sourceBlock.getEndLine());
		assertEquals(FoldingRangeKind.Region, sourceBlock.getKind());
		
		FoldingRange commentBlock = ranges.get(2);
		assertEquals(6, commentBlock.getStartLine());
		assertEquals(8, commentBlock.getEndLine());
		assertEquals(FoldingRangeKind.Comment, commentBlock.getKind());
	}
}
