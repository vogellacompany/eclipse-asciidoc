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

/**
 * Editor with a Source page (LSP4E and TM4E enabled) and an HTML Preview page.
 */
public class AsciidocEditor extends MultiPageEditorPart implements IGotoMarker {

	private ExtensionBasedTextEditor sourceEditor;
	private Browser browser;

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
		} catch (SWTError e) {
			Label label = new Label(composite, SWT.WRAP);
			label.setText(e.getMessage());
		}
		int index = addPage(composite);
		setPageText(index, "Preview");
	}

	/** Renders the current document into the preview browser. No-op until phase 3. */
	protected void refreshPreview() {
		// filled in phase 3
	}

	@Override
	protected void pageChange(int newPageIndex) {
		super.pageChange(newPageIndex);
		if (newPageIndex == 1) {
			refreshPreview();
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
		if (browser != null && !browser.isDisposed()) {
			browser.dispose();
		}
		super.dispose();
	}
}
