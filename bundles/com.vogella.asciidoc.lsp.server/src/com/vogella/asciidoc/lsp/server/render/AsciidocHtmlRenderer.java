package com.vogella.asciidoc.lsp.server.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small AsciiDoc to HTML renderer in plain Java, used by the preview.
 * Unknown constructs render as plain paragraphs and never throw.
 */
public class AsciidocHtmlRenderer {

	private static final Pattern HEADING = Pattern.compile("^(={1,6})\\s+(.+?)\\s*(=+\\s*)?$");
	private static final Pattern ATTRIBUTE_ENTRY = Pattern.compile("^:([A-Za-z0-9_][A-Za-z0-9_-]*):\\s*(.*)$");
	private static final Pattern ANCHOR = Pattern.compile("^\\[\\[([^\\]]+)\\]\\]\\s*$");
	private static final Pattern ID_LINE = Pattern.compile("^\\[#([^\\]]+)\\]\\s*$");

	private final Map<String, String> attributes = new LinkedHashMap<>();

	private RenderOptions options;

	/** Renders the document to a complete HTML page. */
	public String render(String text, RenderOptions options) {
		this.options = options;
		this.attributes.clear();
		String body = renderBody(text == null ? "" : text, 0);
		return page(body);
	}

	String renderBody(String text, int includeDepth) {
		List<String> lines = splitLines(text);
		StringBuilder out = new StringBuilder();
		String pendingId = null;
		String pendingTitle = null;
		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i);
			String stripped = line.strip();

			if (stripped.isEmpty()) {
				i++;
				continue;
			}
			if (stripped.startsWith("//")) {
				i++;
				continue;
			}
			Matcher attr = ATTRIBUTE_ENTRY.matcher(line);
			if (attr.matches()) {
				attributes.put(attr.group(1), attr.group(2).strip());
				i++;
				continue;
			}
			Matcher anchor = ANCHOR.matcher(line);
			if (anchor.matches()) {
				pendingId = anchor.group(1);
				i++;
				continue;
			}
			Matcher idLine = ID_LINE.matcher(line);
			if (idLine.matches()) {
				pendingId = idLine.group(1);
				i++;
				continue;
			}
			if (stripped.startsWith(".") && stripped.length() > 1 && !stripped.startsWith("..")
					&& !Character.isWhitespace(stripped.charAt(1)) && !stripped.matches("\\.{4,}")) {
				pendingTitle = stripped.substring(1);
				i++;
				continue;
			}
			Matcher heading = HEADING.matcher(line);
			if (heading.matches()) {
				int level = heading.group(1).length();
				String title = heading.group(2).strip();
				String id = pendingId != null ? pendingId : slug(title);
				out.append("<h").append(level).append(" id=\"").append(escape(id)).append("\">")
						.append(inline(title)).append("</h").append(level).append(">\n");
				pendingId = null;
				pendingTitle = null;
				i++;
				continue;
			}

			// default: gather a paragraph of consecutive non-empty lines
			List<String> para = new ArrayList<>();
			while (i < lines.size() && !lines.get(i).strip().isEmpty() && !isParagraphBreak(lines.get(i))) {
				para.add(lines.get(i));
				i++;
			}
			appendParagraph(out, para, pendingTitle);
			pendingTitle = null;
		}
		return out.toString();
	}

	private boolean isParagraphBreak(String line) {
		String s = line.strip();
		if (s.startsWith("//")) {
			return true;
		}
		return HEADING.matcher(line).matches() || ATTRIBUTE_ENTRY.matcher(line).matches();
	}

	private void appendParagraph(StringBuilder out, List<String> para, String title) {
		if (para.isEmpty()) {
			return;
		}
		if (title != null) {
			out.append("<div class=\"title\">").append(inline(title)).append("</div>\n");
		}
		StringBuilder content = new StringBuilder();
		for (int k = 0; k < para.size(); k++) {
			String line = para.get(k);
			boolean hardBreak = line.endsWith(" +");
			String textPart = hardBreak ? line.substring(0, line.length() - 2) : line;
			content.append(inline(textPart.strip()));
			if (hardBreak) {
				content.append("<br>");
			}
			if (k < para.size() - 1) {
				content.append("\n");
			}
		}
		out.append("<p>").append(content).append("</p>\n");
	}

	// -- inline formatting (expanded in a later step) --

	String inline(String raw) {
		if (raw == null) {
			return "";
		}
		String substituted = substituteAttributes(raw);
		String escaped = escape(substituted);
		return applyInline(escaped);
	}

	private String applyInline(String s) {
		s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
		s = s.replaceAll("(?<![\\w*])\\*(\\S(?:.*?\\S)?)\\*(?![\\w*])", "<strong>$1</strong>");
		s = s.replaceAll("__(.+?)__", "<em>$1</em>");
		s = s.replaceAll("(?<![\\w_])_(\\S(?:.*?\\S)?)_(?![\\w_])", "<em>$1</em>");
		s = s.replaceAll("`(.+?)`", "<code>$1</code>");
		return s;
	}

	private String substituteAttributes(String s) {
		Matcher m = Pattern.compile("\\{([A-Za-z0-9_][A-Za-z0-9_-]*)\\}").matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String name = m.group(1);
			String value = attributes.get(name);
			m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group(0)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	// -- helpers --

	static List<String> splitLines(String text) {
		List<String> lines = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				String line = text.substring(start, i);
				if (line.endsWith("\r")) {
					line = line.substring(0, line.length() - 1);
				}
				lines.add(line);
				start = i + 1;
			}
		}
		if (start <= text.length()) {
			String line = text.substring(start);
			if (line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			if (!line.isEmpty() || !lines.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	static String slug(String title) {
		String lower = title.toLowerCase();
		String replaced = lower.replaceAll("[^a-z0-9]+", "_");
		replaced = replaced.replaceAll("^_+", "").replaceAll("_+$", "");
		return "_" + replaced;
	}

	static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&' -> sb.append("&amp;");
			case '<' -> sb.append("&lt;");
			case '>' -> sb.append("&gt;");
			case '"' -> sb.append("&quot;");
			default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	/** A complete HTML page reporting an error, used when a document is not open. */
	public static String errorPage(String message) {
		return new AsciidocHtmlRenderer().page("<p class=\"error\">" + escape(message) + "</p>\n");
	}

	private String page(String body) {
		return """
				<!DOCTYPE html>
				<html>
				<head>
				<meta charset="utf-8">
				<style>
				""" + STYLE + """
				</style>
				</head>
				<body>
				""" + body + """
				</body>
				</html>
				""";
	}

	private static final String STYLE = """
			body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
			  max-width: 60em; margin: auto; padding: 1em; line-height: 1.5; }
			pre { background: #f5f5f5; padding: 0.6em; border-radius: 4px; overflow: auto; }
			code { background: #f5f5f5; border-radius: 3px; padding: 0.1em 0.3em; }
			pre code { background: none; padding: 0; }
			table { border-collapse: collapse; }
			th, td { border: 1px solid #ccc; padding: 0.3em 0.6em; }
			figure { margin: 1em 0; }
			figure img { max-width: 100%; }
			.title { font-weight: bold; }
			.admonition { border-left: 4px solid #888; padding: 0.2em 0.8em; margin: 1em 0; background: #fafafa; }
			.admonition.note { border-left-color: #2196f3; }
			.admonition.tip { border-left-color: #4caf50; }
			.admonition.important { border-left-color: #e91e63; }
			.admonition.warning { border-left-color: #ff9800; }
			.admonition.caution { border-left-color: #ff5722; }
			.sidebar { background: #f0f0f0; border: 1px solid #ddd; padding: 0.6em 1em; margin: 1em 0; }
			blockquote { border-left: 4px solid #ccc; margin: 1em 0; padding: 0.2em 1em; color: #555; }
			.error, .missing { color: #b00020; }
			kbd { background: #eee; border: 1px solid #b4b4b4; border-radius: 3px; padding: 0.1em 0.4em; }
			@media (prefers-color-scheme: dark) {
			  body { background: #1e1e1e; color: #d4d4d4; }
			  pre, code { background: #2a2a2a; }
			  th, td { border-color: #555; }
			  .admonition { background: #262626; }
			  .sidebar { background: #262626; border-color: #444; }
			  kbd { background: #333; border-color: #555; }
			}
			""";
}
