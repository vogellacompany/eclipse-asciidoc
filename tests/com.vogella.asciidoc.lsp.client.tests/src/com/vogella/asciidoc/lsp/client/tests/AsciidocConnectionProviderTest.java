package com.vogella.asciidoc.lsp.client.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.vogella.asciidoc.lsp.client.AsciidocConnectionProvider;
import com.vogella.asciidoc.lsp.server.AsciidocLanguageServerApi;

class AsciidocConnectionProviderTest {

	private final AsciidocConnectionProvider provider = new AsciidocConnectionProvider();
	private final ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
	private Future<Void> clientListening;

	@AfterEach
	void tearDown() {
		try {
			stopConnection();
		} finally {
			clientExecutor.shutdownNow();
		}
	}

	@Test
	void stopBeforeStartAndRepeatedStopsAreSafe() {
		provider.stop();
		provider.stop();
		assertNull(provider.getInputStream());
		assertNull(provider.getOutputStream());
	}

	@Test
	void stopClosesAndClearsStreams() throws Exception {
		provider.start();
		assertNotNull(initializeConnection().getCapabilities());
		InputStream input = provider.getInputStream();
		OutputStream output = provider.getOutputStream();

		stopConnection();
		provider.stop();

		assertNull(provider.getInputStream());
		assertNull(provider.getOutputStream());
		assertThrows(IOException.class, input::read);
		assertThrows(IOException.class, () -> output.write(0));
	}

	@Test
	void duplicateStartDoesNotReplaceTheRunningConnection() throws Exception {
		provider.start();
		InputStream input = provider.getInputStream();
		OutputStream output = provider.getOutputStream();

		assertThrows(IOException.class, provider::start);

		assertSame(input, provider.getInputStream());
		assertSame(output, provider.getOutputStream());
		assertNotNull(initializeConnection().getCapabilities());
	}

	@Test
	void stoppedProviderCanStartANewConnection() throws Exception {
		provider.start();
		assertNotNull(initializeConnection().getCapabilities());
		InputStream input = provider.getInputStream();
		OutputStream output = provider.getOutputStream();
		stopConnection();

		provider.start();

		assertNotSame(input, provider.getInputStream());
		assertNotSame(output, provider.getOutputStream());
		assertNotNull(initializeConnection().getCapabilities());
	}

	@Test
	void stopTerminatesTheLauncherThread() throws Exception {
		Set<Thread> existingThreads = Thread.getAllStackTraces().keySet();
		provider.start();
		assertNotNull(initializeConnection().getCapabilities());
		List<Thread> serverThreads = Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> !existingThreads.contains(thread))
				.filter(thread -> thread.getName().startsWith("asciidoc-language-server-"))
				.toList();
		assertEquals(1, serverThreads.size());

		stopConnection();

		Thread serverThread = serverThreads.getFirst();
		serverThread.join(Duration.ofSeconds(5));
		assertFalse(serverThread.isAlive(), "Stopping the provider must terminate its launcher executor");
	}

	private InitializeResult initializeConnection() throws Exception {
		Launcher<AsciidocLanguageServerApi> launcher = new Launcher.Builder<AsciidocLanguageServerApi>()
				.setLocalService(new Object())
				.setRemoteInterface(AsciidocLanguageServerApi.class)
				.setInput(provider.getInputStream())
				.setOutput(provider.getOutputStream())
				.setExecutorService(clientExecutor)
				.create();
		clientListening = launcher.startListening();
		return launcher.getRemoteProxy().initialize(new InitializeParams()).get(5, TimeUnit.SECONDS);
	}

	private void stopConnection() {
		try {
			if (clientListening != null) {
				clientListening.cancel(true);
				clientListening = null;
			}
		} finally {
			provider.stop();
		}
	}
}
