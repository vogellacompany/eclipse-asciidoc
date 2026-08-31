package com.vogella.asciidoc.lsp.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class AsciidocLanguageServer implements AsciidocLanguageServerApi {

	private TextDocumentService textService;
	private WorkspaceService workspaceService;
	LanguageClient client;

	public AsciidocLanguageServer() {
		textService = new AsciidocTextDocumentService(this);
		workspaceService = new AsciidocWorkspaceService();
	}

	/**
	 * Here we tell the framework which functionality our server supports
	 */
	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		final InitializeResult res = new InitializeResult(new ServerCapabilities());
		res.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
		res.getCapabilities().setCompletionProvider(new CompletionOptions());
		res.getCapabilities().setDocumentSymbolProvider(Boolean.TRUE);
		res.getCapabilities().setHoverProvider(Boolean.TRUE);
		res.getCapabilities().setDefinitionProvider(Boolean.TRUE);
		res.getCapabilities().setDocumentLinkProvider(new org.eclipse.lsp4j.DocumentLinkOptions());
		res.getCapabilities().setCodeActionProvider(Boolean.TRUE);
//		res.getCapabilities().setReferencesProvider(Boolean.TRUE);
		return CompletableFuture.supplyAsync(() -> res);
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		return CompletableFuture.supplyAsync(() -> Boolean.FALSE);
	}

	@Override
	public void exit() {
		System.out.println("Shutdown");
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return this.textService;
	}

	@Override
	public CompletableFuture<String> preview(TextDocumentIdentifier document) {
		return ((AsciidocTextDocumentService) textService).preview(document.getUri());
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return this.workspaceService;
	}

	public void setRemoteProxy(LanguageClient remoteProxy) {
		this.client = remoteProxy;
	}

}
