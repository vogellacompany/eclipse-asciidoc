package com.vogella.asciidoc.editor;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.editors.text.TextEditorActionContributor;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Routes the standard text editor actions (undo, find, ...) to the Source page.
 */
public class AsciidocEditorContributor extends MultiPageEditorActionBarContributor {

	private final TextEditorActionContributor textContributor = new TextEditorActionContributor();

	@Override
	public void init(IActionBars bars, IWorkbenchPage page) {
		super.init(bars, page);
		textContributor.init(bars, page);
	}

	@Override
	public void setActivePage(IEditorPart activeEditor) {
		textContributor.setActiveEditor(activeEditor instanceof ITextEditor ? activeEditor : null);
		getActionBars().updateActionBars();
	}

	@Override
	public void dispose() {
		textContributor.dispose();
		super.dispose();
	}
}
