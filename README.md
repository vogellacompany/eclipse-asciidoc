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

In Eclipse: *Help > Install New Software*, and add this update site:

```
https://vogellacompany.github.io/eclipse-asciidoc/
```

The required LSP4E and TM4E plugins are pulled in from the Eclipse release train and their own update sites, which the site references.

The site carries the newest build and nothing else, published from `main` by the [Release workflow](.github/workflows/release.yml).
Older versions are not supported: the previous build is dropped when a new one is published, so update rather than pin.

To install from your own build rather than the hosted site, see [Build Instructions](#build-instructions), then point *Add > Local* at

```
sites/com.vogella.asciidoc.updatesite/target/repository
```

## Architecture

The project is structured around a Language Server architecture:
*   **AsciidocDocumentModel**: A lightweight domain model responsible for parsing the AsciiDoc content (headings, anchors, attributes, macros, blocks).
*   **Language Server**: Implements the LSP protocol to provide features like diagnostics, completions, and navigation, using the `AsciidocDocumentModel` to understand the document structure.

## Build Instructions

This project uses Maven and Tycho. To build the project and run all tests, simply execute:

```bash
./mvnw -ntp clean verify
```

The resulting p2 repository lands in `sites/com.vogella.asciidoc.updatesite/target/repository/`.

Pushing to `main` runs that same build and publishes the result to the hosted update site, on the `gh-pages` branch.
The site carries one build at a time: `releng/update-composite-site.sh` writes the p2 composite metadata that points the root URL at it, and drops what came before.
A tag of the form `v*` additionally attaches the repository archive to a GitHub release.

The published artifacts are PGP signed with the vogella release key, held in the `MAVEN_GPG_KEY` and `MAVEN_GPG_PASSPHRASE` organization secrets.
Signing is off in a plain `./mvnw clean verify`; to exercise it locally, point Tycho at an exported secret key:

```bash
./mvnw clean verify -Dgpg.skip=false -Dtycho.pgp.signer.bc.secretKeys=/path/to/signing-key.asc
```

with the passphrase in `MAVEN_GPG_PASSPHRASE`.

## Running from the IDE

To run the project directly from your Eclipse IDE:
1. Import the project as existing Maven/Eclipse projects.
2. Open `target-platform/target-platform.target` and set it as your active Target Platform.
3. Launch a Runtime Workbench (Eclipse Application) to test the AsciiDoc Editor.

## Manual Testing

A comprehensive manual testing document is provided at `tests/com.vogella.asciidoc.lsp.server.tests/manual/Testing.adoc`. Open this file in the AsciiDoc Editor to evaluate all Language Server features and the integrated live preview.

## License

This project is licensed under the Eclipse Public License 2.0.
