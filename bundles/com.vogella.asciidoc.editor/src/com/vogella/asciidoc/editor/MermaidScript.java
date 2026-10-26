package com.vogella.asciidoc.editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.ILog;
import org.eclipse.swt.browser.Browser;

/**
 * Renders the Mermaid diagrams of a loaded preview page with the bundled mermaid.js.
 * The script is run in the page after it has loaded, rather than referenced from the page,
 * because a page set through {@link Browser#setText(String)} cannot load local files.
 */
final class MermaidScript {

	private static final String RESOURCE = "/mermaid/mermaid.min.js";

	private static String script;

	private MermaidScript() {
	}

	/** Runs mermaid.js on the page, then restores the scroll position, which the diagrams shift. */
	static void renderDiagrams(Browser browser, Double scrollY) {
		String source = script();
		if (source == null || !browser.execute(source)) {
			return;
		}
		String restoreScroll = scrollY != null ? "window.scrollTo(0, " + scrollY + ");" : "";
		browser.execute("""
				mermaid.initialize({ startOnLoad: false,
				  theme: window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'default' });
				mermaid.run({ querySelector: 'pre.mermaid' }).finally(() => { %s });
				""".formatted(restoreScroll));
	}

	private static synchronized String script() {
		if (script == null) {
			try (InputStream in = MermaidScript.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					ILog.of(MermaidScript.class).error("Missing " + RESOURCE);
					return null;
				}
				script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				ILog.of(MermaidScript.class).error("Could not read " + RESOURCE, e);
				return null;
			}
		}
		return script;
	}
}
