package com.vogella.asciidoc.lsp.server;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vogella.asciidoc.lsp.server.render.AsciidocHtmlRenderer;
import com.vogella.asciidoc.lsp.server.render.RenderOptions;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class AsciidocTextDocumentService implements TextDocumentService {
	private final Map<String, AsciidocDocumentModel> docs = Collections.synchronizedMap(new HashMap<>());

	private final AsciidocLanguageServer languageServer;

	public AsciidocTextDocumentService(AsciidocLanguageServer languageServer) {
		this.languageServer = languageServer;
	}

	/** Renders the in-memory content of the document to a complete HTML page. */
	public CompletableFuture<String> preview(String uri) {
		return CompletableFuture.supplyAsync(() -> {
			AsciidocDocumentModel model = docs.get(uri);
			if (model == null) {
				return AsciidocHtmlRenderer.errorPage("Document is not open");
			}
			Path baseDir = null;
			try {
				Path path = Paths.get(URI.create(uri));
				baseDir = path.getParent();
			} catch (Exception e) {
				// no base directory, includes and images resolve to nothing
			}
			RenderOptions renderOptions = new RenderOptions(baseDir, this::openDocumentText, true);
			return new AsciidocHtmlRenderer().render(model.getText(), renderOptions);
		});
	}

	private Optional<String> openDocumentText(Path path) {
		Path key = path.toAbsolutePath().normalize();
		synchronized (docs) {
			for (Map.Entry<String, AsciidocDocumentModel> entry : docs.entrySet()) {
				try {
					Path open = Paths.get(URI.create(entry.getKey())).toAbsolutePath().normalize();
					if (open.equals(key)) {
						return Optional.of(entry.getValue().getText());
					}
				} catch (Exception e) {
					// ignore documents with a non-file uri
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
		return CompletableFuture.supplyAsync(() -> {
			List<CompletionItem> completionItems = new ArrayList<>();
			String uri = position.getTextDocument().getUri();
			AsciidocDocumentModel model = docs.get(uri);

			if (model == null) {
				return Either.forLeft(Collections.emptyList());
			}

			int lineNum = position.getPosition().getLine();
			int charPos = position.getPosition().getCharacter();
			String lineContent = model.getLineContent(lineNum);
			if (lineContent == null)
				lineContent = "";

			String prefixLine = charPos <= lineContent.length() ? lineContent.substring(0, charPos) : lineContent;
			String suffixLine = charPos < lineContent.length() ? lineContent.substring(charPos) : "";

			// 1. Image completion
			// Matches 'image::' or 'image:' followed by an optional path and an optional opening bracket at the end
			Pattern imagePattern = Pattern.compile("image:[:]?([^\\[\\]\\s]*)(\\[)?$");
			Matcher imageMatcher = imagePattern.matcher(prefixLine);
			if (imageMatcher.find()) {
				String pathPrefix = imageMatcher.group(1);
				boolean hasOpeningInPrefix = imageMatcher.group(2) != null;
				int startChar = charPos - pathPrefix.length() - (hasOpeningInPrefix ? 1 : 0);

				// Determine how much of the suffix to replace
				int endChar = charPos;
				if (suffixLine.startsWith("[]")) {
					endChar += 2;
				} else if (suffixLine.startsWith("]")) {
					endChar += 1;
				} else if (hasOpeningInPrefix && suffixLine.startsWith("[")) {
					// This case is unlikely given the regex, but good for completeness
					endChar += 1;
				}

				List<String> images = scanForFiles(uri, "img", new String[] { ".png", ".jpg", ".jpeg", ".gif" });
				for (String img : images) {
					if (img.toLowerCase().startsWith(pathPrefix.toLowerCase())) {
						CompletionItem item = new CompletionItem();
						item.setLabel(img);
						item.setKind(CompletionItemKind.File);
						item.setDetail("Image file");
						item.setSortText("0_" + img.toLowerCase()); // Sort alphabetically

						TextEdit edit = new TextEdit();
						edit.setRange(new Range(new Position(lineNum, startChar), new Position(lineNum, endChar)));
						edit.setNewText(img + "[]");
						item.setTextEdit(Either.forLeft(edit));

						completionItems.add(item);
					}
				}
				return Either.forLeft(completionItems);
			}

			// 2. Include completion with smart path support
			Pattern includePattern = Pattern.compile("include::([^\\[\\]\\s]*)(\\[)?$");
			Matcher includeMatcher = includePattern.matcher(prefixLine);
			if (includeMatcher.find()) {
				String pathPrefix = includeMatcher.group(1);
				boolean hasOpeningInPrefix = includeMatcher.group(2) != null;
				int startChar = charPos - pathPrefix.length() - (hasOpeningInPrefix ? 1 : 0);

				int endChar = charPos;
				if (suffixLine.startsWith("[]")) {
					endChar += 2;
				} else if (suffixLine.startsWith("]")) {
					endChar += 1;
				}

				// Parse path into directory and filename parts
				String dirPart = "";
				String filePrefix = pathPrefix;
				int lastSlash = pathPrefix.lastIndexOf('/');
				if (lastSlash >= 0) {
					dirPart = pathPrefix.substring(0, lastSlash + 1);
					filePrefix = pathPrefix.substring(lastSlash + 1);
				}

				// Get completions for the current path segment
				List<CompletionItem> pathCompletions = getPathCompletions(uri, dirPart, filePrefix, lineNum, startChar, endChar);
				completionItems.addAll(pathCompletions);

				return Either.forLeft(completionItems);
			}

			// 3. New Proposals
			// Line start or after typing letters at line start
			if (prefixLine.matches("^[a-zA-Z]*$")) {
				completionItems.add(createSnippetItem("NOTE: ", "NOTE: "));
				completionItems.add(createSnippetItem("TIP: ", "TIP: "));
				completionItems.add(createSnippetItem("IMPORTANT: ", "IMPORTANT: "));
				completionItems.add(createSnippetItem("WARNING: ", "WARNING: "));
				completionItems.add(createSnippetItem("CAUTION: ", "CAUTION: "));
				completionItems.add(createSnippetItem("image::", "image::${1:file}[]"));
				completionItems.add(createSnippetItem("include::", "include::${1:file}[]"));
				completionItems.add(createSnippetItem("[source,java]", "[source,${1:java}]\n----\n$0\n----"));
				completionItems.add(createSnippetItem("|===", "|===\n$0\n|==="));
				completionItems.add(createSnippetItem(".Title", ".${1:Title}"));
				completionItems.add(createSnippetItem("= Title", "= ${1:Title}"));
				completionItems.add(createSnippetItem("== Title", "== ${1:Title}"));
			}

			// After << or xref:
			Pattern refPattern = Pattern.compile("(<<|xref:)([^>\\[]*)$");
			Matcher refMatcher = refPattern.matcher(prefixLine);
			if (refMatcher.find()) {
				for (AsciidocDocumentModel.Anchor anchor : model.getAnchors()) {
					CompletionItem item = new CompletionItem();
					item.setLabel(anchor.id);
					item.setKind(CompletionItemKind.Reference);
					completionItems.add(item);
				}
				for (AsciidocDocumentModel.Heading h : model.getHeadings()) {
					if (h.id != null && !h.id.isEmpty()) {
						CompletionItem item = new CompletionItem();
						item.setLabel(h.id);
						item.setDetail(h.title);
						item.setKind(CompletionItemKind.Reference);
						completionItems.add(item);
					}
					String replaced = h.title.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
					String genId = "_" + replaced;
					CompletionItem item = new CompletionItem();
					item.setLabel(genId);
					item.setDetail(h.title);
					item.setKind(CompletionItemKind.Reference);
					completionItems.add(item);
				}
			}

			// After {
			Pattern varPattern = Pattern.compile("\\{([^}]*)$");
			Matcher varMatcher = varPattern.matcher(prefixLine);
			if (varMatcher.find()) {
				for (String attrName : model.getAttributes().keySet()) {
					CompletionItem item = new CompletionItem();
					item.setLabel(attrName);
					item.setKind(CompletionItemKind.Variable);
					completionItems.add(item);
				}
			}

			// After link: or xref: followed by path prefix
			Pattern linkPattern = Pattern.compile("(link:|xref:)([^>\\[\\s]*)(\\[)?$");
			Matcher linkMatcher = linkPattern.matcher(prefixLine);
			if (linkMatcher.find()) {
				String pathPrefix = linkMatcher.group(2);
				boolean hasOpeningInPrefix = linkMatcher.group(3) != null;
				int startChar = charPos - pathPrefix.length() - (hasOpeningInPrefix ? 1 : 0);
				int endChar = charPos;
				if (suffixLine.startsWith("[]")) {
					endChar += 2;
				} else if (suffixLine.startsWith("]")) {
					endChar += 1;
				}
				String dirPart = "";
				String filePrefix = pathPrefix;
				int lastSlash = pathPrefix.lastIndexOf('/');
				if (lastSlash >= 0) {
					dirPart = pathPrefix.substring(0, lastSlash + 1);
					filePrefix = pathPrefix.substring(lastSlash + 1);
				}
				List<CompletionItem> pathCompletions = getPathCompletions(uri, dirPart, filePrefix, lineNum, startChar, endChar);
				completionItems.addAll(pathCompletions);
			}

			return Either.forLeft(completionItems);
		});
	}

	private CompletionItem createSnippetItem(String label, String insertText) {
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		item.setKind(CompletionItemKind.Snippet);
		item.setInsertText(insertText);
		item.setInsertTextFormat(org.eclipse.lsp4j.InsertTextFormat.Snippet);
		return item;
	}

	private List<String> scanForFiles(String documentUri, String subDir, String[] extensions) {
		List<String> fileNames = new ArrayList<>();
		try {
			URI uri = new URI(documentUri);
			File docFile = new File(uri);
			File parentDir = docFile.getParentFile();

			File targetDir = new File(parentDir, subDir);
			if (targetDir.exists() && targetDir.isDirectory()) {
				File[] files = targetDir.listFiles((dir, name) -> {
					for (String ext : extensions) {
						if (name.toLowerCase().endsWith(ext)) return true;
					}
					return false;
				});

				if (files != null) {
					for (File f : files) {
						fileNames.add(f.getName());
					}
				}
			}
		} catch (Exception e) {
			// Ignore errors
		}
		return fileNames;
	}

	/**
	 * Get path-aware completions for include directives.
	 * Supports directory traversal and completes the current path segment.
	 * Results are sorted: files first, then directories, both alphabetically.
	 */
	private List<CompletionItem> getPathCompletions(String documentUri, String dirPart, String filePrefix,
			int lineNum, int startChar, int endChar) {
		List<CompletionItem> completions = new ArrayList<>();
		try {
			URI uri = new URI(documentUri);
			File docFile = new File(uri);
			File parentDir = docFile.getParentFile();

			// Resolve the target directory relative to the current document
			File targetDir = new File(parentDir, dirPart);
			if (!targetDir.exists() || !targetDir.isDirectory()) {
				return completions;
			}

			File[] entries = targetDir.listFiles();
			if (entries == null) {
				return completions;
			}

			for (File entry : entries) {
				String name = entry.getName();

				// Skip hidden files and the current document
				if (name.startsWith(".")) {
					continue;
				}

				// Check if name matches the prefix (case-insensitive)
				if (!name.toLowerCase().startsWith(filePrefix.toLowerCase())) {
					continue;
				}

				if (entry.isDirectory()) {
					// Add directory completion
					CompletionItem item = new CompletionItem();
					item.setLabel(name + "/");
					item.setKind(CompletionItemKind.Folder);
					item.setDetail("Directory");
					item.setSortText("1_" + name.toLowerCase()); // Directories after files

					TextEdit edit = new TextEdit();
					edit.setRange(new Range(new Position(lineNum, startChar), new Position(lineNum, endChar)));
					edit.setNewText(dirPart + name + "/");
					item.setTextEdit(Either.forLeft(edit));

					completions.add(item);
				} else if (name.endsWith(".adoc")) {
					// Add file completion
					CompletionItem item = new CompletionItem();
					item.setLabel(name);
					item.setKind(CompletionItemKind.File);
					item.setDetail("Asciidoc file");
					item.setSortText("0_" + name.toLowerCase()); // Files before directories

					TextEdit edit = new TextEdit();
					edit.setRange(new Range(new Position(lineNum, startChar), new Position(lineNum, endChar)));
					edit.setNewText(dirPart + name + "[]");
					item.setTextEdit(Either.forLeft(edit));

					completions.add(item);
				}
			}
		} catch (Exception e) {
			// Ignore errors
		}
		return completions;
	}


	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.supplyAsync(() -> {
			String uri = params.getTextDocument().getUri();
			AsciidocDocumentModel model = docs.get(uri);
			if (model == null) {
				return Collections.emptyList();
			}

			List<DocumentLink> links = new ArrayList<>();
			for (AsciidocDocumentModel.Macro macro : model.getMacros()) {
				String type = macro.type;
				String path = macro.target;
				
				if (!"include".equals(type) && !"image".equals(type) && !"link".equals(type)) {
					continue;
				}

				String targetUri = null;
				if ("link".equals(type)) {
					if (path.startsWith("http://") || path.startsWith("https://")) {
						targetUri = path;
					} else {
						Location loc = resolveFileLocation(uri, path);
						if (loc != null) targetUri = loc.getUri();
					}
				} else {
					Location loc = resolveFileLocation(uri, path);
					if (loc == null && "image".equals(type)) {
						loc = resolveFileLocation(uri, "img/" + path);
					}
					if (loc != null) targetUri = loc.getUri();
				}

				if (targetUri != null) {
					// The matcher startChar in the original model needs adjustment to capture just the path.
					// In Macro pattern: group 2 is path, start() and end() are for the whole macro.
					// Let's use the whole macro range for the link for simplicity, or we can approximate.
					Range range = new Range(new Position(macro.line, macro.startChar), new Position(macro.line, macro.endChar));
					DocumentLink link = new DocumentLink(range, targetUri, "Open " + path);
					links.add(link);
				}
			}

			return links;
		});
	}

	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
			DocumentSymbolParams params) {
		return CompletableFuture.supplyAsync(() -> {
			String uri = params.getTextDocument().getUri();
			AsciidocDocumentModel model = docs.get(uri);
			if (model == null) {
				return Collections.emptyList();
			}

			List<AsciidocDocumentModel.Heading> headings = model.getHeadings();
			int lineCount = model.getLines().size();
			List<DocumentSymbol> roots = new ArrayList<>();
			Deque<DocumentSymbol> stack = new ArrayDeque<>();
			Deque<Integer> levelStack = new ArrayDeque<>();
			
			for (int h = 0; h < headings.size(); h++) {
				AsciidocDocumentModel.Heading heading = headings.get(h);
				int startLine = heading.line;
				int level = heading.level;
				int endLine = lineCount - 1;
				
				for (int k = h + 1; k < headings.size(); k++) {
					if (headings.get(k).level <= level) {
						endLine = headings.get(k).line - 1;
						break;
					}
				}
				
				String headingLine = model.getLineContent(startLine);
				int endChar = endLine >= 0 && endLine < lineCount ? model.getLineContent(endLine).length() : 0;

				DocumentSymbol symbol = new DocumentSymbol();
				symbol.setName(heading.title);
				symbol.setKind(SymbolKind.Module);
				symbol.setRange(new Range(new Position(startLine, 0),
						new Position(Math.max(endLine, startLine), endChar)));
				symbol.setSelectionRange(
						new Range(new Position(startLine, 0), new Position(startLine, headingLine != null ? headingLine.length() : 0)));
				symbol.setChildren(new ArrayList<>());

				while (!levelStack.isEmpty() && levelStack.peek() >= level) {
					stack.pop();
					levelStack.pop();
				}
				if (stack.isEmpty()) {
					roots.add(symbol);
				} else {
					stack.peek().getChildren().add(symbol);
				}
				stack.push(symbol);
				levelStack.push(level);
			}

			return roots.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight)
					.collect(Collectors.toList());
		});
	}


	@Override
	public CompletableFuture<Hover> hover(HoverParams params) {
		return CompletableFuture.supplyAsync(() -> {
			String uri = params.getTextDocument().getUri();
			AsciidocDocumentModel model = docs.get(uri);
			if (model == null)
				return null;

			int lineNum = params.getPosition().getLine();
			int charPos = params.getPosition().getCharacter();

			for (AsciidocDocumentModel.Macro macro : model.getMacros()) {
				if (macro.line == lineNum && "image".equals(macro.type)) {
					if (charPos >= macro.startChar && charPos <= macro.endChar) {
						String imageName = macro.target;
						if (!imageName.isEmpty()) {
							try {
								URI docUri = new URI(uri);
								File docFile = new File(docUri);
								File parentDir = docFile.getParentFile();

								File imgFile = new File(parentDir, "img/" + imageName);
								if (!imgFile.exists()) {
									imgFile = new File(parentDir, imageName);
								}

								Hover hover = new Hover();
								if (imgFile.exists()) {
									String imgUri = imgFile.toURI().toString();
									String content = String.format("![%s](%s)", imageName, imgUri);
									hover.setContents(new MarkupContent(MarkupKind.MARKDOWN, content));
								} else {
									String content = String.format("**Image not found:** `%s`\n\nChecked in:\n* `%s`\n* `%s`", 
											imageName, new File(parentDir, "img/").getPath(), parentDir.getPath());
									hover.setContents(new MarkupContent(MarkupKind.MARKDOWN, content));
								}
								return hover;
							} catch (Exception e) {
							}
						}
					}
				}
			}
			return null;
		});
	}
	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			DefinitionParams params) {

		String uri = params.getTextDocument().getUri();
		AsciidocDocumentModel model = this.docs.get(uri);
		if (model == null) {
			return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
		}

		int line = params.getPosition().getLine();
		int character = params.getPosition().getCharacter();

		for (AsciidocDocumentModel.Macro macro : model.getMacros()) {
			if (macro.line == line && character >= macro.startChar && character <= macro.endChar) {
				String path = macro.target;
				if ("include".equals(macro.type)) {
					Location loc = resolveFileLocation(uri, path);
					if (loc != null) {
						return CompletableFuture.completedFuture(Either.forLeft(Collections.singletonList(loc)));
					}
				} else if ("image".equals(macro.type)) {
					Location loc = resolveFileLocation(uri, path);
					if (loc == null) {
						loc = resolveFileLocation(uri, "img/" + path);
					}
					if (loc != null) {
						return CompletableFuture.completedFuture(Either.forLeft(Collections.singletonList(loc)));
					}
				}
			}
		}

		return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
	}

	private Location resolveFileLocation(String baseUri, String relativePath) {
		try {
			URI uri = new URI(baseUri);
			File baseFile = new File(uri);
			File parentDir = baseFile.getParentFile();
			File targetFile = new File(parentDir, relativePath);
			if (targetFile.exists()) {
				Location location = new Location();
				// Normalize the path to remove ./ and ../ components
				location.setUri(targetFile.toPath().normalize().toUri().toString());
				location.setRange(new Range(new Position(0, 0), new Position(0, 0)));
				return location;
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}


	/**
	 * Utility method to find the word under the cursor in a given line of text.
	 */
	private String getWordAtPosition(String lineContent, int character) {
		// Define word boundaries (spaces or punctuation) to split the line into words.
		// This example assumes simple word boundaries.
		int start = character;
		int end = character;

		// Find the start of the word (left of the cursor)
		while (start > 0 && Character.isLetterOrDigit(lineContent.charAt(start - 1))) {
			start--;
		}

		// Find the end of the word (right of the cursor)
		while (end < lineContent.length() && Character.isLetterOrDigit(lineContent.charAt(end))) {
			end++;
		}

		// Extract the word
		return lineContent.substring(start, end);
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return null;
	}




	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		AsciidocDocumentModel model = new AsciidocDocumentModel(params.getTextDocument().getText());
		this.docs.put(params.getTextDocument().getUri(), model);
		CompletableFuture.runAsync(() -> languageServer.client
				.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate(params.getTextDocument().getUri(), model))));

	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		AsciidocDocumentModel model = new AsciidocDocumentModel(params.getContentChanges().get(0).getText());
		this.docs.put(params.getTextDocument().getUri(), model);
		CompletableFuture.runAsync(() -> languageServer.client
				.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate(params.getTextDocument().getUri(), model))));

	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		this.docs.remove(params.getTextDocument().getUri());
		CompletableFuture.runAsync(() -> languageServer.client
				.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), Collections.emptyList())));
	}
	
	@Override
	public void didSave(DidSaveTextDocumentParams params) {
	}

	private List<Diagnostic> validate(String uri, AsciidocDocumentModel model) {
		List<Diagnostic> diagnostics = new ArrayList<>();
		Path baseDir = null;
		try {
			baseDir = Paths.get(URI.create(uri)).getParent();
		} catch (Exception e) {
			// ignore
		}

		Map<String, Integer> seenAnchors = new HashMap<>();
		List<String> validIds = new ArrayList<>();

		for (AsciidocDocumentModel.Anchor anchor : model.getAnchors()) {
			if (seenAnchors.containsKey(anchor.id)) {
				Diagnostic d = new Diagnostic();
				d.setSeverity(DiagnosticSeverity.Warning);
				d.setMessage("Duplicate anchor id: " + anchor.id);
				d.setRange(new Range(new Position(anchor.line, 0), new Position(anchor.line, anchor.id.length() + 4)));
				d.setSource("asciidoc");
				diagnostics.add(d);
			} else {
				seenAnchors.put(anchor.id, anchor.line);
				validIds.add(anchor.id);
			}
		}

		for (AsciidocDocumentModel.Heading h : model.getHeadings()) {
			if (h.id != null && !h.id.isEmpty()) validIds.add(h.id);
			String replaced = h.title.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
			validIds.add("_" + replaced);
		}

		for (AsciidocDocumentModel.Macro macro : model.getMacros()) {
			Range range = new Range(new Position(macro.line, macro.startChar), new Position(macro.line, macro.endChar));
			
			if ("include".equals(macro.type)) {
				if (baseDir != null) {
					Path targetPath = baseDir.resolve(macro.target);
					if (!targetPath.toFile().exists()) {
						Diagnostic d = new Diagnostic();
						d.setSeverity(DiagnosticSeverity.Error);
						d.setMessage("Include file not found: " + macro.target);
						d.setRange(range);
						d.setSource("asciidoc");
						diagnostics.add(d);
					}
				}
			} else if ("image".equals(macro.type)) {
				if (baseDir != null) {
					String imagesdir = model.getAttributes().getOrDefault("imagesdir", "");
					Path targetPath = baseDir;
					if (!imagesdir.isEmpty()) {
						targetPath = targetPath.resolve(imagesdir);
					}
					targetPath = targetPath.resolve(macro.target);
					if (!targetPath.toFile().exists()) {
						Diagnostic d = new Diagnostic();
						d.setSeverity(DiagnosticSeverity.Warning);
						d.setMessage("Image file not found: " + macro.target);
						d.setRange(range);
						d.setSource("asciidoc");
						diagnostics.add(d);
					}
				}
			} else if ("xref".equals(macro.type) || "link".equals(macro.type)) {
				if (macro.target.endsWith(".adoc") || (macro.target.contains(".adoc#") && !macro.target.startsWith("http"))) {
					if (baseDir != null) {
						String filename = macro.target;
						if (filename.contains("#")) {
							filename = filename.substring(0, filename.indexOf('#'));
						}
						Path targetPath = baseDir.resolve(filename);
						if (!targetPath.toFile().exists()) {
							Diagnostic d = new Diagnostic();
							d.setSeverity(DiagnosticSeverity.Warning);
							d.setMessage("Target file not found: " + filename);
							d.setRange(range);
							d.setSource("asciidoc");
							diagnostics.add(d);
						}
					}
				} else if ("xref".equals(macro.type) && !macro.target.contains(".adoc")) {
					String refId = macro.target;
					if (refId.contains("#")) {
						refId = refId.substring(refId.indexOf('#') + 1);
					}
					if (!validIds.contains(refId)) {
						Diagnostic d = new Diagnostic();
						d.setSeverity(DiagnosticSeverity.Warning);
						d.setMessage("Unresolved internal reference: " + refId);
						d.setRange(range);
						d.setSource("asciidoc");
						diagnostics.add(d);
					}
				}
			}
		}

		return diagnostics;
	}

}
