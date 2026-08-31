
package com.vogella.asciidoc.lsp.server.render;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
	private static final Pattern ATTR_BLOCK = Pattern.compile("^\\[(.*)\\]\\s*$");
	private static final Pattern LIST_ITEM = Pattern.compile("^(\\*+|-|\\.+)\\s+(.*)$");
	private static final Pattern DLIST_ITEM = Pattern.compile("^(.*?)::\\s+(.*)$");
	private static final Pattern ADMONITION_PARA = Pattern.compile("^(NOTE|TIP|IMPORTANT|WARNING|CAUTION):\\s+(.*)$");
	private static final Pattern BLOCK_IMAGE = Pattern.compile("^image::([^\\[]+)\\[(.*)\\]\\s*$");
	private static final Pattern INCLUDE = Pattern.compile("^include::([^\\[]+)\\[(.*)\\]\\s*$");

	private final Map<String, String> attributes = new LinkedHashMap<>();
	private RenderOptions options;

	public String render(String text, RenderOptions options) {
		this.options = options;
		this.attributes.clear();
		String body = renderBody(text == null ? "" : text, 0, new HashSet<>());
		return page(body);
	}

	String renderBody(String text, int includeDepth, Set<Path> includedPaths) {
		List<String> lines = splitLines(text);
		StringBuilder out = new StringBuilder();
		String pendingId = null;
		String pendingTitle = null;
		Map<String, String> pendingAttrs = new LinkedHashMap<>();

		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i);
			String stripped = line.strip();

			if (stripped.isEmpty()) {
				i++;
				continue;
			}
			if (stripped.equals("<<<") || stripped.equals("+")) {
				i++;
				continue;
			}
			if (stripped.equals("'''")) {
				out.append("<hr>\n");
				i++;
				continue;
			}
			if (stripped.startsWith("//") && !stripped.startsWith("////")) {
				i++;
				continue;
			}
			if (stripped.startsWith("////")) {
				i++;
				while (i < lines.size() && !lines.get(i).strip().startsWith("////")) {
					i++;
				}
				if (i < lines.size()) i++;
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
			Matcher attrBlock = ATTR_BLOCK.matcher(line);
			if (attrBlock.matches()) {
				String battr = attrBlock.group(1);
				if (battr.startsWith("source")) {
					pendingAttrs.put("style", "source");
					String[] parts = battr.split(",", 2);
					if (parts.length > 1) {
						pendingAttrs.put("lang", parts[1].strip());
					}
				} else if (battr.startsWith("options=")) {
					pendingAttrs.put("options", battr.substring("options=".length()).replace("\"", ""));
				} else {
					pendingAttrs.put("style", battr);
				}
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
				pendingAttrs.clear();
				i++;
				continue;
			}

			// Block delimiters
			if (stripped.equals("----") || stripped.equals("....") || stripped.equals("====") || stripped.equals("****") || stripped.equals("____") || stripped.equals("|===")) {
				String delim = stripped;
				i++;
				List<String> blockLines = new ArrayList<>();
				while (i < lines.size() && !lines.get(i).strip().equals(delim)) {
					blockLines.add(lines.get(i));
					i++;
				}
				if (i < lines.size()) i++;

				if (delim.equals("----") || delim.equals("....")) {
					String lang = pendingAttrs.get("lang");
					if (pendingTitle != null) out.append("<div class=\"title\">").append(inline(pendingTitle)).append("</div>\n");
					out.append("<pre>");
					if (delim.equals("----")) {
						if (lang != null) out.append("<code class=\"language-").append(escape(lang)).append("\">");
						else out.append("<code>");
					}
					for (String bl : blockLines) {
						out.append(escape(bl)).append("\n");
					}
					if (delim.equals("----")) {
						out.append("</code>");
					}
					out.append("</pre>\n");
				} else if (delim.equals("====")) {
					String style = pendingAttrs.getOrDefault("style", "");
					if (List.of("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION").contains(style)) {
						out.append("<div class=\"admonition ").append(style.toLowerCase()).append("\">");
						out.append("<strong>").append(style.charAt(0)).append(style.substring(1).toLowerCase()).append("</strong>");
					} else {
						out.append("<div class=\"example\">");
					}
					if (pendingTitle != null) out.append("<div class=\"title\">").append(inline(pendingTitle)).append("</div>\n");
					out.append(renderBody(String.join("\n", blockLines), includeDepth, includedPaths));
					out.append("</div>\n");
				} else if (delim.equals("****")) {
					out.append("<aside class=\"sidebar\">\n");
					if (pendingTitle != null) out.append("<div class=\"title\">").append(inline(pendingTitle)).append("</div>\n");
					out.append(renderBody(String.join("\n", blockLines), includeDepth, includedPaths));
					out.append("</aside>\n");
				} else if (delim.equals("____")) {
					out.append("<blockquote>\n");
					if (pendingTitle != null) out.append("<div class=\"title\">").append(inline(pendingTitle)).append("</div>\n");
					out.append(renderBody(String.join("\n", blockLines), includeDepth, includedPaths));
					out.append("</blockquote>\n");
				} else if (delim.equals("|===")) {
					if (pendingTitle != null) out.append("<div class=\"title\">").append(inline(pendingTitle)).append("</div>\n");
					out.append("<table>\n");
					boolean isHeader = "header".equals(pendingAttrs.get("options"));
					if (!isHeader && !blockLines.isEmpty()) {
						if (blockLines.size() > 1 && blockLines.get(1).strip().isEmpty()) isHeader = true;
					}
					for (int k = 0; k < blockLines.size(); k++) {
						String bl = blockLines.get(k);
						if (bl.strip().isEmpty()) continue;
						out.append("<tr>");
						String[] cells = bl.split("\\|");
						for (int j = 1; j < cells.length; j++) {
							String cell = cells[j].strip();
							String tag = (k == 0 && isHeader) ? "th" : "td";
							out.append("<").append(tag).append(">").append(inline(cell)).append("</").append(tag).append(">");
						}
						out.append("</tr>\n");
					}
					out.append("</table>\n");
				}

				pendingId = null;
				pendingTitle = null;
				pendingAttrs.clear();
				continue;
			}

			Matcher admPara = ADMONITION_PARA.matcher(line);
			if (admPara.matches()) {
				String type = admPara.group(1);
				out.append("<div class=\"admonition ").append(type.toLowerCase()).append("\">");
				out.append("<strong>").append(type.charAt(0)).append(type.substring(1).toLowerCase()).append("</strong>");
				
				List<String> para = new ArrayList<>();
				para.add(admPara.group(2));
				i++;
				while (i < lines.size() && !lines.get(i).strip().isEmpty() && !isParagraphBreak(lines.get(i))) {
					para.add(lines.get(i));
					i++;
				}
				out.append("<p>");
				for (int k=0; k<para.size(); k++) {
					String l = para.get(k);
					boolean hardBreak = l.endsWith(" +");
					String tp = hardBreak ? l.substring(0, l.length() - 2) : l;
					out.append(inline(tp.strip()));
					if (hardBreak) out.append("<br>");
					if (k < para.size() - 1) out.append("\n");
				}
				out.append("</p></div>\n");
				pendingId = null;
				pendingTitle = null;
				pendingAttrs.clear();
				continue;
			}

			Matcher imgPara = BLOCK_IMAGE.matcher(line);
			if (imgPara.matches()) {
				String target = imgPara.group(1);
				String attrs = imgPara.group(2);
				String[] parts = attrs.split(",");
				String alt = parts.length > 0 ? parts[0].strip() : "";
				String width = parts.length > 1 ? parts[1].strip() : "";
				
				String imgSrc = resolveImage(target);
				if (imgSrc == null) {
					out.append("<span class=\"missing\">image not found: ").append(escape(target)).append("</span>\n");
				} else {
					out.append("<figure>");
					out.append("<img src=\"").append(imgSrc).append("\" alt=\"").append(escape(alt)).append("\"");
					if (!width.isEmpty()) out.append(" width=\"").append(escape(width)).append("\"");
					out.append(">");
					if (pendingTitle != null) {
						out.append("<figcaption>").append(inline(pendingTitle)).append("</figcaption>");
					}
					out.append("</figure>\n");
				}
				
				pendingId = null;
				pendingTitle = null;
				pendingAttrs.clear();
				i++;
				continue;
			}

			Matcher incPara = INCLUDE.matcher(line);
			if (incPara.matches()) {
				String target = incPara.group(1);
				if (includeDepth >= 10) {
					out.append("<p class=\"error\">include not found: ").append(escape(target)).append("</p>\n");
				} else {
					Path p = options.baseDir() != null ? options.baseDir().resolve(target).normalize() : Path.of(target);
					if (includedPaths.contains(p)) {
						out.append("<p class=\"error\">include not found: ").append(escape(target)).append("</p>\n");
					} else {
						Optional<String> txt = options.openDocuments().apply(p);
						String content = null;
						if (txt.isPresent()) {
							content = txt.get();
						} else if (Files.isRegularFile(p)) {
							try {
								content = Files.readString(p);
							} catch (IOException e) {
								// ignore
							}
						}
						if (content != null) {
							Set<Path> newInc = new HashSet<>(includedPaths);
							newInc.add(p);
							out.append(renderBody(content, includeDepth + 1, newInc));
						} else {
							out.append("<p class=\"error\">include not found: ").append(escape(target)).append("</p>\n");
						}
					}
				}
				i++;
				continue;
			}

			if (LIST_ITEM.matcher(stripped).matches() || DLIST_ITEM.matcher(stripped).matches()) {
				i = parseLists(lines, i, out, includeDepth, includedPaths);
				pendingId = null;
				pendingTitle = null;
				pendingAttrs.clear();
				continue;
			}

			// default: gather a paragraph of consecutive non-empty lines
			List<String> para = new ArrayList<>();
			while (i < lines.size() && !lines.get(i).strip().isEmpty() && !isParagraphBreak(lines.get(i))) {
				para.add(lines.get(i));
				i++;
			}
			appendParagraph(out, para, pendingTitle);
			pendingId = null;
			pendingTitle = null;
			pendingAttrs.clear();
		}
		return out.toString();
	}

	private int parseLists(List<String> lines, int i, StringBuilder out, int includeDepth, Set<Path> includedPaths) {
		String first = lines.get(i).strip();
		Matcher m = LIST_ITEM.matcher(first);
		Matcher dm = DLIST_ITEM.matcher(first);
		if (dm.matches()) {
			out.append("<dl>\n");
			while (i < lines.size()) {
				int startI = i;
				String l = lines.get(i).strip();
				if (l.isEmpty() || (isParagraphBreak(l) && !DLIST_ITEM.matcher(l).matches())) break;
				Matcher dlm = DLIST_ITEM.matcher(l);
				if (dlm.matches()) {
					out.append("<dt>").append(inline(dlm.group(1).strip())).append("</dt>\n");
					out.append("<dd>").append(inline(dlm.group(2).strip())).append("</dd>\n");
				} else {
					break;
				}
				i++;
				if (i == startI) i++; // fail-safe
			}
			out.append("</dl>\n");
			return i;
		} else if (m.matches()) {
			String marker = m.group(1);
			boolean isOl = marker.startsWith(".");
			String tag = isOl ? "ol" : "ul";
			out.append("<").append(tag).append(">\n");
			
			while (i < lines.size()) {
				int startI = i;
				String l = lines.get(i).strip();
				if (l.isEmpty() || (isParagraphBreak(l) && !LIST_ITEM.matcher(l).matches())) break;
				Matcher lm = LIST_ITEM.matcher(l);
				if (lm.matches() && lm.group(1).equals(marker)) {
					String content = lm.group(2).strip();
					if (!isOl && content.startsWith("[x] ")) {
						content = "<input type=\"checkbox\" disabled checked> " + content.substring(4);
					} else if (!isOl && content.startsWith("[ ] ")) {
						content = "<input type=\"checkbox\" disabled> " + content.substring(4);
					} else {
						content = inline(content);
					}
					out.append("<li>").append(content).append("</li>\n");
					i++;
				} else if (lm.matches() && lm.group(1).startsWith(marker.substring(0, 1)) && lm.group(1).length() > marker.length()) {
					i = parseLists(lines, i, out, includeDepth, includedPaths);
				} else {
					break; // Different kind of list or smaller indent, pop back up
				}
				if (i == startI) break; // fail-safe to prevent infinite loop
			}
			out.append("</").append(tag).append(">\n");
			return i;
		}
		return i + 1; // fallback
	}

	private boolean isParagraphBreak(String line) {
		String s = line.strip();
		if (s.startsWith("//")) return true;
		if (s.equals("<<<") || s.equals("+") || s.equals("'''")) return true;
		if (s.equals("----") || s.equals("....") || s.equals("====") || s.equals("****") || s.equals("____") || s.equals("|===")) return true;
		if (ATTR_BLOCK.matcher(s).matches()) return true;
		return HEADING.matcher(line).matches() || ATTRIBUTE_ENTRY.matcher(line).matches() || 
			ANCHOR.matcher(line).matches() || ID_LINE.matcher(line).matches() ||
			BLOCK_IMAGE.matcher(line).matches() || INCLUDE.matcher(line).matches() ||
			ADMONITION_PARA.matcher(line).matches() ||
			LIST_ITEM.matcher(s).matches() || DLIST_ITEM.matcher(s).matches();
	}

	private void appendParagraph(StringBuilder out, List<String> para, String title) {
		if (para.isEmpty()) return;
		if (title != null) {
			out.append("<div class=\"title\">").append(inline(title)).append("</div>\n");
		}
		StringBuilder content = new StringBuilder();
		for (int k = 0; k < para.size(); k++) {
			String line = para.get(k);
			boolean hardBreak = line.endsWith(" +");
			String textPart = hardBreak ? line.substring(0, line.length() - 2) : line;
			content.append(inline(textPart.strip()));
			if (hardBreak) content.append("<br>");
			if (k < para.size() - 1) content.append("\n");
		}
		out.append("<p>").append(content).append("</p>\n");
	}

	String inline(String raw) {
		if (raw == null) return "";
		String substituted = substituteAttributes(raw);
		return applyInline(substituted);
	}

	private String applyInline(String s) {
		// First extract +literal+ and escape everything else
		List<String> literals = new ArrayList<>();
		Matcher lm = Pattern.compile("\\+(.*?)\\+").matcher(s);
		StringBuilder sb1 = new StringBuilder();
		while (lm.find()) {
			literals.add(escape(lm.group(1)));
			lm.appendReplacement(sb1, "\u0000");
		}
		lm.appendTail(sb1);
		
		s = escape(sb1.toString());

		s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
		s = s.replaceAll("(?<![\\w*])\\*(\\S(?:.*?\\S)?)\\*(?![\\w*])", "<strong>$1</strong>");
		s = s.replaceAll("__(.+?)__", "<em>$1</em>");
		s = s.replaceAll("(?<![\\w_])_(\\S(?:.*?\\S)?)_(?![\\w_])", "<em>$1</em>");
		s = s.replaceAll("`(.+?)`", "<code>$1</code>");
		
		s = s.replaceAll("#(.+?)#", "<mark>$1</mark>");
		s = s.replaceAll("\\^(.+?)\\^", "<sup>$1</sup>");
		s = s.replaceAll("~(.+?)~", "<sub>$1</sub>");
		
		s = s.replaceAll("kbd:\\[(.+?)\\]", "<kbd>$1</kbd>");
		s = s.replaceAll("btn:\\[(.+?)\\]", "<b class=\"button\">$1</b>");
		s = s.replaceAll("menu:(.+?)\\[(.+?)\\]", "<span class=\"menu\">$1&gt;$2</span>"); // wait, just menu? "menu:File[Save]"

		// Links
		s = s.replaceAll("https?://[\\w\\./\\-?=#]+(?:\\[(.*?)\\])?", "\u0001$0\u0002");
		s = s.replaceAll("link:([^\\[]+)\\[(.*?)\\]", "\u0003$1\u0004$2\u0005");
		s = s.replaceAll("xref:([^\\[]+)\\[(.*?)\\]", "\u0006$1\u0004$2\u0005");
		s = s.replaceAll("&lt;&lt;([^,&]+)(?:,(.*?))?&gt;&gt;", "\u0006$1\u0004$2\u0005");
		
		// Image inline
		s = s.replaceAll("image:([^\\[]+)\\[(.*?)\\]", "\u0007$1\u0004$2\u0005");

		// Restore links
		Matcher urlM = Pattern.compile("\\u0001(https?://[\\w\\./\\-?=#]+)(?:\\[(.*?)\\])?\\u0002").matcher(s);
		StringBuilder b = new StringBuilder();
		while (urlM.find()) {
			String url = urlM.group(1);
			String txt = urlM.group(2) != null && !urlM.group(2).isEmpty() ? urlM.group(2) : url;
			urlM.appendReplacement(b, Matcher.quoteReplacement("<a href=\"" + url + "\">" + txt + "</a>"));
		}
		urlM.appendTail(b);
		s = b.toString();

		Matcher linkM = Pattern.compile("\\u0003(.*?)\\u0004(.*?)\\u0005").matcher(s);
		b = new StringBuilder();
		while (linkM.find()) {
			String target = linkM.group(1);
			String txt = linkM.group(2);
			if (txt.isEmpty()) txt = target;
			String href = target;
			if (!target.startsWith("http://") && !target.startsWith("https://")) {
				Path p = options.baseDir() != null ? options.baseDir().resolve(target).normalize() : Path.of(target);
				href = p.toUri().toString();
			}
			linkM.appendReplacement(b, Matcher.quoteReplacement("<a href=\"" + href + "\">" + txt + "</a>"));
		}
		linkM.appendTail(b);
		s = b.toString();
		
		Matcher xrefM = Pattern.compile("\\u0006(.*?)\\u0004(.*?)\\u0005").matcher(s);
		b = new StringBuilder();
		while (xrefM.find()) {
			String target = xrefM.group(1);
			String txt = xrefM.group(2);
			if (txt == null || txt.isEmpty() || txt.equals("null")) txt = target;
			String href;
			if (!target.contains(".adoc")) {
				href = "#" + target;
			} else {
				String[] parts = target.split("#", 2);
				Path p = options.baseDir() != null ? options.baseDir().resolve(parts[0]).normalize() : Path.of(parts[0]);
				href = p.toUri().toString() + (parts.length > 1 ? "#" + parts[1] : "");
			}
			xrefM.appendReplacement(b, Matcher.quoteReplacement("<a href=\"" + href + "\">" + txt + "</a>"));
		}
		xrefM.appendTail(b);
		s = b.toString();

		Matcher imgM = Pattern.compile("\\u0007(.*?)\\u0004(.*?)\\u0005").matcher(s);
		b = new StringBuilder();
		while (imgM.find()) {
			String target = imgM.group(1);
			String alt = imgM.group(2);
			String imgSrc = resolveImage(target);
			String imgTag;
			if (imgSrc == null) {
				imgTag = "<span class=\"missing\">image not found: " + escape(target) + "</span>";
			} else {
				imgTag = "<img src=\"" + imgSrc + "\" alt=\"" + escape(alt) + "\">";
			}
			imgM.appendReplacement(b, Matcher.quoteReplacement(imgTag));
		}
		imgM.appendTail(b);
		s = b.toString();

		// Restore literals
		for (String lit : literals) {
			s = s.replaceFirst("\u0000", lit);
		}

		return s;
	}

	private String resolveImage(String target) {
		String imagesdir = attributes.getOrDefault("imagesdir", "");
		Path p = Path.of(imagesdir, target);
		if (options.baseDir() != null) {
			p = options.baseDir().resolve(p).normalize();
		}
		if (options.inlineImages() && Files.isRegularFile(p)) {
			try {
				long size = Files.size(p);
				if (size <= 10 * 1024 * 1024) {
					String mime = null;
					String name = p.getFileName().toString().toLowerCase();
					if (name.endsWith(".png")) mime = "image/png";
					else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mime = "image/jpeg";
					else if (name.endsWith(".gif")) mime = "image/gif";
					else if (name.endsWith(".svg")) mime = "image/svg+xml";
					else if (name.endsWith(".webp")) mime = "image/webp";
					
					if (mime != null) {
						byte[] bytes = Files.readAllBytes(p);
						return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
					}
				}
			} catch (IOException e) {}
		}
		if (!Files.isRegularFile(p)) {
			return null;
		}
		return p.toUri().toString();
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
