package com.vogella.asciidoc.lsp.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.core.runtime.ILog;
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
	private PipedInputStream serverInput;
	private PipedOutputStream serverOutput;
	private ExecutorService executor;
	private Future<Void> listening;

	@Override
	public synchronized void start() throws IOException {
		if (listening != null) {
			throw new IOException("The AsciiDoc language server connection is already started");
		}
		try {
			serverInput = new PipedInputStream(PIPE_SIZE);
			serverOutput = new PipedOutputStream();
			clientInput = new PipedInputStream(serverOutput, PIPE_SIZE);
			clientOutput = new PipedOutputStream(serverInput);
			executor = Executors.newSingleThreadExecutor(
					Thread.ofPlatform().name("asciidoc-language-server-", 0).factory());

			AsciidocLanguageServer server = new AsciidocLanguageServer();
			Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, serverInput, serverOutput,
					executor, Function.identity());
			server.setRemoteProxy(launcher.getRemoteProxy());
			listening = launcher.startListening();
		} catch (IOException | RuntimeException e) {
			stop();
			throw e;
		}
	}

	@Override
	public synchronized InputStream getInputStream() {
		return clientInput;
	}

	@Override
	public synchronized OutputStream getOutputStream() {
		return clientOutput;
	}

	@Override
	public InputStream getErrorStream() {
		return null;
	}

	@Override
	public synchronized void stop() {
		try {
			if (listening != null) {
				listening.cancel(true);
			}
		} finally {
			listening = null;
			close(clientInput);
			close(clientOutput);
			close(serverInput);
			close(serverOutput);
			clientInput = null;
			clientOutput = null;
			serverInput = null;
			serverOutput = null;
			if (executor != null) {
				executor.shutdownNow();
				executor = null;
			}
		}
	}

	private static void close(Closeable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (IOException e) {
			ILog.of(AsciidocConnectionProvider.class).error("Could not close an AsciiDoc language server stream", e);
		}
	}
}
