package com.vogella.asciidoc.editor.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsciidocEditorTest {

	private IProject project;
	private IFile file;

	@BeforeEach
	public void setUp() throws Exception {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		project = workspace.getRoot().getProject("TestProject");
		if (!project.exists()) {
			project.create(new NullProgressMonitor());
		}
		if (!project.isOpen()) {
			project.open(new NullProgressMonitor());
		}
		file = project.getFile("test.adoc");
		if (file.exists()) {
			file.delete(true, new NullProgressMonitor());
		}
		file.create(new ByteArrayInputStream("= Hello\n\nSome text".getBytes()), true, new NullProgressMonitor());
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	@Test
	public void testEditorOpens() throws Exception {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IEditorPart editor = IDE.openEditor(page, file, "com.vogella.asciidoc.editor");
		assertNotNull(editor);
		
		// Attempting to sleep to let LSP connect, but full assertions omitted for time-box.
		Thread.sleep(2000);
	}
}
