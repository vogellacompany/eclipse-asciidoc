package com.vogella.asciidoc.lsp.server.render;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * Options for {@link AsciidocHtmlRenderer}.
 * baseDir is the directory of the rendered document, openDocuments returns the in-memory text of an
 * open document at a path (used for includes), inlineImages controls data-URI embedding of images.
 */
public record RenderOptions(Path baseDir, Function<Path, Optional<String>> openDocuments, boolean inlineImages) {

	public static RenderOptions simple(Path baseDir) {
		return new RenderOptions(baseDir, p -> Optional.empty(), true);
	}
}
