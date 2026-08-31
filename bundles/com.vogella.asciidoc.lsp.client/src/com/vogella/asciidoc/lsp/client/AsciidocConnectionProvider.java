package com.vogella.asciidoc.lsp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Future;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;

/**
 * Runs the AsciiDoc language server in-process and connects it to LSP4E via piped streams.
 */
public class AsciidocConnectionProvider implements StreamConnectionProvider {

	private static final int PIPE_SIZE = 64 * 1024;

	private PipedInputStream clientInput;
	private PipedOutputStream clientOutput;
	private Future<Void> listening;

	@Override
	public void start() throws IOException {
		PipedInputStream serverInput = new PipedInputStream(PIPE_SIZE);
		PipedOutputStream serverOutput = new PipedOutputStream();
		clientInput = new PipedInputStream(serverOutput, PIPE_SIZE);
		clientOutput = new PipedOutputStream(serverInput);

		AsciidocLanguageServer server = new AsciidocLanguageServer();
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, serverInput, serverOutput);
		server.setRemoteProxy(launcher.getRemoteProxy());
		listening = launcher.startListening();
	}

	@Override
	public InputStream getInputStream() {
		return clientInput;
	}

	@Override
	public OutputStream getOutputStream() {
		return clientOutput;
	}

	@Override
	public InputStream getErrorStream() {
		return null;
	}

	@Override
	public void stop() {
		if (listening != null) {
			listening.cancel(true);
		}
		closeQuietly(clientInput);
		closeQuietly(clientOutput);
	}

	private static void closeQuietly(AutoCloseable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (Exception e) {
			// nothing useful to do while shutting down
		}
	}
}
