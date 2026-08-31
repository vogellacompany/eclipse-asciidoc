package com.vogella.asciidoc.editor;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import com.vogella.asciidoc.lsp.server.AsciidocLanguageServerApi;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/**
 * Editor with a Source page (LSP4E and TM4E enabled) and an HTML Preview page.
 */
public class AsciidocEditor extends MultiPageEditorPart implements IGotoMarker {

	private ExtensionBasedTextEditor sourceEditor;
	private Browser browser;

	private final Runnable refreshRunnable = this::refreshPreview;
	private final IDocumentListener documentListener = new IDocumentListener() {
		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {}
		@Override
		public void documentChanged(DocumentEvent event) {
			Display.getDefault().timerExec(-1, refreshRunnable);
			Display.getDefault().timerExec(500, refreshRunnable);
		}
	};

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		super.init(site, input);
		setPartName(input.getName());
	}

	@Override
	protected void createPages() {
		createSourcePage();
		createPreviewPage();
	}

	private void createSourcePage() {
		sourceEditor = new ExtensionBasedTextEditor();
		try {
			int index = addPage(sourceEditor, getEditorInput());
			setPageText(index, "Source");
		} catch (PartInitException e) {
			throw new IllegalStateException("Could not create the Source page", e);
		}
	}

	private void createPreviewPage() {
		Composite composite = new Composite(getContainer(), SWT.NONE);
		composite.setLayout(new FillLayout());
		try {
			browser = new Browser(composite, SWT.NONE);
			browser.setText("<p>Preview is not available yet.</p>");
			
			browser.addLocationListener(new LocationListener() {
				@Override
				public void changing(LocationEvent event) {
					String loc = event.location;
					if (loc == null || "about:blank".equals(loc) || loc.startsWith("about:blank#")) {
						return;
					}
					
					if (loc.startsWith("file:")) {
						int fragmentIndex = loc.indexOf('#');
						String path = fragmentIndex > 0 ? loc.substring(0, fragmentIndex) : loc;
						if (path.endsWith(".adoc")) {
							event.doit = false;
							try {
								java.net.URI uri = new java.net.URI(path);
								IDE.openEditorOnFileStore(getSite().getPage(), EFS.getLocalFileSystem().getStore(uri));
							} catch (Exception e) {
								// ignore
							}
						}
					} else if (loc.startsWith("http://") || loc.startsWith("https://")) {
						event.doit = false;
						try {
							PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new java.net.URL(loc));
						} catch (Exception e) {
							// ignore
						}
					}
				}

				@Override
				public void changed(LocationEvent event) {
				}
			});
		} catch (SWTError e) {
			Label label = new Label(composite, SWT.WRAP);
			label.setText(e.getMessage());
		}
		int index = addPage(composite);
		setPageText(index, "Preview");
	}

	/** Renders the current document into the preview browser. */
	protected void refreshPreview() {
		if (browser == null || browser.isDisposed()) {
			return;
		}
		IDocument document = getDocument();
		if (document == null) {
			return;
		}
		String uri = LSPEclipseUtils.toUri(document).toASCIIString();

		LanguageServers.forDocument(document)
			.computeFirst(ls -> ((AsciidocLanguageServerApi) ls).preview(new TextDocumentIdentifier(uri)))
			.thenAccept(html -> {
				Display.getDefault().asyncExec(() -> {
					if (browser != null && !browser.isDisposed()) {
						Double y = null;
						try {
							y = (Double) browser.evaluate("return window.scrollY;");
						} catch (Exception e) {
							// ignore
						}
						final Double scrollY = y;

						ProgressListener progressListener = new org.eclipse.swt.browser.ProgressAdapter() {
							@Override
							public void completed(ProgressEvent event) {
								if (browser != null && !browser.isDisposed() && scrollY != null) {
									try {
										browser.evaluate("window.scrollTo(0, " + scrollY + ");");
									} catch (Exception e) {
										// ignore
									}
								}
								browser.removeProgressListener(this);
							}
						};
						browser.addProgressListener(progressListener);

						browser.setText((html != null && html.isPresent()) ? (String) html.get() : "");
					}
				});
			})
			.exceptionally(ex -> {
				Display.getDefault().asyncExec(() -> {
					if (browser != null && !browser.isDisposed()) {
						browser.setText("<p>Error generating preview: " + ex.getMessage() + "</p>");
					}
				});
				return null;
			});
	}

	@Override
	protected void pageChange(int newPageIndex) {
		super.pageChange(newPageIndex);
		IDocument document = getDocument();
		if (newPageIndex == 1) {
			if (document != null) {
				document.addDocumentListener(documentListener);
			}
			refreshPreview();
		} else {
			if (document != null) {
				document.removeDocumentListener(documentListener);
			}
			Display.getDefault().timerExec(-1, refreshRunnable);
		}
	}

	@Override
	public void gotoMarker(IMarker marker) {
		setActivePage(0);
		IDE.gotoMarker(sourceEditor, marker);
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		sourceEditor.doSave(monitor);
	}

	@Override
	public void doSaveAs() {
		sourceEditor.doSaveAs();
		setInput(sourceEditor.getEditorInput());
		setPartName(getEditorInput().getName());
	}

	@Override
	public boolean isSaveAsAllowed() {
		return sourceEditor.isSaveAsAllowed();
	}

	/** The document of the Source page, used by the preview and the tests. */
	public IDocument getDocument() {
		return sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter == ITextEditor.class) {
			return (T) sourceEditor;
		}
		if (adapter == IGotoMarker.class) {
			return (T) this;
		}
		return super.getAdapter(adapter);
	}

	@Override
	public void dispose() {
		IDocument document = getDocument();
		if (document != null) {
			document.removeDocumentListener(documentListener);
		}
		Display.getDefault().timerExec(-1, refreshRunnable);
		if (browser != null && !browser.isDisposed()) {
			browser.dispose();
		}
		super.dispose();
	}
}
