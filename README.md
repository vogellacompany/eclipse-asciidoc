# Eclipse AsciiDoc Editor

A modern AsciiDoc editor for the Eclipse IDE, powered by a custom Language Server built on LSP4E.

## Features

*   **Two-Tab Editor**: Source editing with an integrated HTML preview page.
*   **Live Preview**: The preview updates automatically as you type. It supports rich block and inline AsciiDoc syntax, including lists, tables, admonitions, inline images, and file includes.
*   **Intelligent Diagnostics**:
    *   Errors for missing included files.
    *   Warnings for missing images, broken cross-references (`xref`, `<<id>>`), duplicate anchors, and broken file links.
*   **Code Completion**:
    *   Snippets for structural elements like headings, source blocks, tables, and admonitions.
    *   Context-aware completion for image and include file paths.
    *   Cross-reference completion for internal anchors.
    *   Variable completion for defined document attributes.
*   **Rich Navigation**:
    *   Clickable document links (`include::`, `image::`, URLs).
    *   Go to Definition for internal anchors, headings, and included files.
*   **Hover Information**: Shows heading details when hovering over cross-references.
*   **Code Folding**: Fold sections, comment blocks, and delimited blocks to keep your workspace tidy.

## Installation

You can install the plugin via our update site:
`https://vogellacompany.github.io/eclipse-asciidoc/`

*Note: The Eclipse LSP4E and TM4E plugins are required.*

## Architecture

The project is structured around a Language Server architecture:
*   **AsciidocDocumentModel**: A lightweight domain model responsible for parsing the AsciiDoc content (headings, anchors, attributes, macros, blocks).
*   **Language Server**: Implements the LSP protocol to provide features like diagnostics, completions, and navigation, using the `AsciidocDocumentModel` to understand the document structure.

## Build Instructions

This project uses Maven and Tycho. To build the project and run all tests, simply execute:

```bash
./mvnw -ntp clean verify
```

## Running from the IDE

To run the project directly from your Eclipse IDE:
1. Import the project as existing Maven/Eclipse projects.
2. Open `target-platform/target-platform.target` and set it as your active Target Platform.
3. Launch a Runtime Workbench (Eclipse Application) to test the AsciiDoc Editor.

## Manual Testing

A comprehensive manual testing document is provided at `tests/com.vogella.asciidoc.lsp.server.tests/manual/Testing.adoc`. Open this file in the AsciiDoc Editor to evaluate all Language Server features and the integrated live preview.

## License

This project is licensed under the Eclipse Public License 2.0.
