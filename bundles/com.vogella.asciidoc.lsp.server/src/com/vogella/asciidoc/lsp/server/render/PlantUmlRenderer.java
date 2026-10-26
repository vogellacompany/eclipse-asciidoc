package com.vogella.asciidoc.lsp.server.render;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/**
 * Renders PlantUML source to inline SVG with the embedded PlantUML library.
 * No Graphviz installation is required. Results are cached, since the preview re-renders on every edit.
 */
final class PlantUmlRenderer {

	private static final int CACHE_SIZE = 64;

	private static final Map<String, String> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	private PlantUmlRenderer() {
	}

	/** Returns the SVG markup for the diagram; syntax errors come back as PlantUML's own error image. */
	static String toSvg(String source) {
		String text = source.strip().startsWith("@start") ? source : "@startuml\n" + source + "\n@enduml\n";
		return CACHE.computeIfAbsent(text, PlantUmlRenderer::render);
	}

	private static String render(String text) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			new SourceStringReader(text).outputImage(out, new FileFormatOption(FileFormat.SVG));
			String svg = out.toString(StandardCharsets.UTF_8);
			// drop the XML prolog, it is not allowed inside an HTML body
			if (svg.startsWith("<?xml")) {
				svg = svg.substring(svg.indexOf("?>") + 2);
			}
			return svg.strip();
		} catch (Exception | LinkageError e) {
			return "<span class=\"error\">PlantUML diagram could not be rendered: "
					+ AsciidocHtmlRenderer.escape(String.valueOf(e.getMessage())) + "</span>";
		}
	}
}
