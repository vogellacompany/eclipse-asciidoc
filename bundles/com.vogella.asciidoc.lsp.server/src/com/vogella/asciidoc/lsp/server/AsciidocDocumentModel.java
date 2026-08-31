package com.vogella.asciidoc.lsp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsciidocDocumentModel {

	public static class DocumentLine {
		public final int line;
		public final String text;

		protected DocumentLine(int line, String text) {
			this.line = line;
			this.text = text;
		}
	}

	public static class Heading {
		public final int level;
		public final String title;
		public final String id;
		public final int line;
		
		public Heading(int level, String title, String id, int line) {
			this.level = level;
			this.title = title;
			this.id = id;
			this.line = line;
		}
	}

	public static class Anchor {
		public final String id;
		public final int line;
		
		public Anchor(String id, int line) {
			this.id = id;
			this.line = line;
		}
	}
	
	public static class Macro {
		public final String type;
		public final String target;
		public final String attributes;
		public final int line;
		public final int startChar;
		public final int endChar;
		
		public Macro(String type, String target, String attributes, int line, int startChar, int endChar) {
			this.type = type;
			this.target = target;
			this.attributes = attributes;
			this.line = line;
			this.startChar = startChar;
			this.endChar = endChar;
		}
	}
	
	public static class BlockRange {
		public final String delimiter;
		public final int startLine;
		public int endLine;
		
		public BlockRange(String delimiter, int startLine) {
			this.delimiter = delimiter;
			this.startLine = startLine;
			this.endLine = -1;
		}
	}

	private final List<DocumentLine> lines = new ArrayList<>();
	private final String text;

	private final List<Heading> headings = new ArrayList<>();
	private final List<Anchor> anchors = new ArrayList<>();
	private final Map<String, String> attributes = new HashMap<>();
	private final List<Macro> macros = new ArrayList<>();
	private final List<BlockRange> blocks = new ArrayList<>();

	private static final Pattern HEADING_PATTERN = Pattern.compile("^(={1,6})\\s+(.+?)\\s*(=+\\s*)?$");
	private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("^:([\\w-]+):(.*)$");
	private static final Pattern ANCHOR_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]|\\[#([^\\]]+)\\]");
	private static final Pattern MACRO_PATTERN = Pattern.compile("(include|image|link|xref):[:]?([^\\s\\[\\]]+)\\[([^\\]]*)\\]|<<([^>,]+)(?:,([^>]*))?>>");

	public AsciidocDocumentModel(String text) {
		this.text = text;
		try (Reader r = new StringReader(text); BufferedReader reader = new BufferedReader(r)) {
			String lineText;
			int lineNumber = 0;
			BlockRange currentBlock = null;
			
			while ((lineText = reader.readLine()) != null) {
				DocumentLine line = new DocumentLine(lineNumber, lineText);
				lines.add(line);
				
				String delim = blockDelimiterToken(lineText);
				if (currentBlock != null) {
					if (delim != null && delim.equals(currentBlock.delimiter)) {
						currentBlock.endLine = lineNumber;
						blocks.add(currentBlock);
						currentBlock = null;
					}
				} else if (delim != null) {
					currentBlock = new BlockRange(delim, lineNumber);
				} else {
					Matcher m = HEADING_PATTERN.matcher(lineText);
					if (m.matches()) {
						int level = m.group(1).length();
						String title = m.group(2).trim();
						String id = ""; 
						// If prev line has anchor, it could be the id, but let's keep it simple for now or parse it if it's on the same line
						headings.add(new Heading(level, title, id, lineNumber));
					}
					
					Matcher attr = ATTRIBUTE_PATTERN.matcher(lineText);
					if (attr.matches()) {
						attributes.put(attr.group(1), attr.group(2).trim());
					}
					
					Matcher anchor = ANCHOR_PATTERN.matcher(lineText);
					while (anchor.find()) {
						String id = anchor.group(1) != null ? anchor.group(1) : anchor.group(2);
						anchors.add(new Anchor(id, lineNumber));
					}
					
					Matcher macro = MACRO_PATTERN.matcher(lineText);
					while (macro.find()) {
						if (macro.group(1) != null) {
							macros.add(new Macro(macro.group(1), macro.group(2), macro.group(3), lineNumber, macro.start(2), macro.end(2)));
						} else {
							macros.add(new Macro("xref", macro.group(4), macro.group(5) != null ? macro.group(5) : "", lineNumber, macro.start(4), macro.end(4)));
						}
					}
				}
				lineNumber++;
			}
			if (currentBlock != null) {
				currentBlock.endLine = lineNumber - 1;
				blocks.add(currentBlock);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String blockDelimiterToken(String line) {
		String t = line.strip();
		if (t.matches("-{4,}")) return "-";
		if (t.matches("\\.{4,}")) return ".";
		if (t.matches("/{4,}")) return "/";
		if (t.matches("={4,}")) return "=";
		if (t.matches("\\*{4,}")) return "*";
		if (t.matches("_{4,}")) return "_";
		if (t.matches("\\|={3,}")) return "|";
		return null;
	}

	public String getText() { return text; }
	public String getLineContent(int lineNumber) {
		if (lineNumber < 0 || lineNumber >= lines.size()) return null;
		return lines.get(lineNumber).text;
	}
	public List<DocumentLine> getResolvedLines() { return Collections.unmodifiableList(this.lines); }
	public List<String> getLines() {
		List<String> result = new ArrayList<>();
		for (DocumentLine line : lines) result.add(line.text);
		return Collections.unmodifiableList(result);
	}
	
	public List<Heading> getHeadings() { return Collections.unmodifiableList(headings); }
	public List<Anchor> getAnchors() { return Collections.unmodifiableList(anchors); }
	public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }
	public List<Macro> getMacros() { return Collections.unmodifiableList(macros); }
	public List<BlockRange> getBlocks() { return Collections.unmodifiableList(blocks); }
}
