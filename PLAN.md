# eclipse-asciidoc: execution brief

This file is the complete brief for building the AsciiDoc editor in this repository.
Work through it top to bottom.
Every decision that could be made in advance has been made; do not reopen them.
Progress is tracked in `PROGRESS.md`, see section 0 for the protocol.

## 0. Working protocol (read first, applies to every step)

### 0.1 Resume protocol

You may be stopped at any time (token limit, timeout).
Therefore:

1. At the start of every session run `git status --short`, `git log --oneline -20` and read `PROGRESS.md`.
2. If the working tree is dirty, inspect `git diff`, finish or discard that partial step, and commit it before doing anything else.
3. Continue with the first unchecked step in `PROGRESS.md`.
4. Never redo a checked step.

### 0.2 Commit protocol

* Commit after **every numbered step** (1.1, 1.2, ...), not only after a phase.
  Small commits are the recovery mechanism.
* Each commit includes the code of the step **and** the updated `PROGRESS.md` (tick the box, add notes).
* Commit message: first line `Step <n>: <what>` (example: `Step 1.3: add target platform definition`), then an optional short body.
* Always append the trailer with this exact command form:

  ```bash
  git commit -m "Step 1.3: add target platform definition" --trailer "Assisted-by: multiple AI agents and layers of automated tooling 🤖"
  ```

* Never add a `Co-Authored-By:` trailer.
  Never add a "Generated with" footer.
* Never amend or rewrite commits that already exist.
* Push after every commit if your environment has push access to the branch you work on.
  Otherwise leave the commits local.
* Never run `gh pr create`; pull requests are opened by the human.
* If you must stop in the middle of a step, commit what exists with the message `WIP step <n>: <what is missing>` and write the same information under "Handover notes" in `PROGRESS.md`.

### 0.3 House rules

* Java 25, Eclipse 2026-06, Tycho 5.0.4, Maven 3.9.11 (wrapper).
  Do not use other versions.
* License: EPL-2.0 for everything in this repository.
* Never use em dashes in any text (code comments, docs, commit messages).
  Use commas, colons, parentheses or separate sentences.
* Markdown and AsciiDoc files: one sentence per line.
* Comments and Javadoc: short and sparse.
  One or two sentences per class or method, no `@param`/`@return` when obvious, no change history.
* Do not modify the `codeexamples-ide` repository; it is only a source to copy from.
* Do not add features that this brief marks as out of scope, even if they seem easy.
* Verify with the exact commands given in each phase's "Exit criteria"; do not declare a phase done without running them.
* If a step fails after two honest attempts, write what you tried in `PROGRESS.md` under the step, commit, and continue with the next step that does not depend on it.

## 1. Fixed decisions

| Topic | Decision |
|---|---|
| Namespace | Everything is renamed to `com.vogella.asciidoc.*` (bundle ids, `.project` names, Java packages, content type id, extension ids). |
| Highlighting | TM4E grammar (existing `syntaxes/asciidoc.json`). LSP semantic tokens are **out of scope**. |
| Preview engine | Small own renderer in plain Java inside the LSP server bundle. No AsciidoctorJ, no JRuby, no external process. |
| Preview transport | Custom LSP request `asciidoc/preview` served by the in-process language server, called from the editor through LSP4E. |
| Editor shape | One editor with two tabs: `Source` (generic editor with LSP4E and TM4E) and `Preview` (SWT Browser). |
| Update site | GitHub Pages, only "latest", published on every push to `main` from the CI build. URL: `https://vogellacompany.github.io/eclipse-asciidoc/` |
| History | Plain copy of the source files, no git history import. |
| Source of the existing code | `https://github.com/vogellacompany/codeexamples-ide`, commit `6bb2262e2302a795155223860cb92b1aca06439d` (branch `main`). If `/home/vogella/git/codeexamples-ide` exists locally, use it, otherwise `git clone --depth 1 https://github.com/vogellacompany/codeexamples-ide /tmp/codeexamples-ide`. Referred to as `$SRC` below. |

## 2. Target layout and rename map

```
eclipse-asciidoc/
  pom.xml                                   the only pom.xml (root)
  .mvn/extensions.xml, .mvn/maven.config, .mvn/wrapper/maven-wrapper.properties
  mvnw, mvnw.cmd
  .gitignore, LICENSE, README.md, PLAN.md, PROGRESS.md
  target-platform/target-platform.target    referenced by file, not a module
  bundles/
    com.vogella.asciidoc.editor/            content type, TM4E grammar, language configuration, icons, 2-tab editor
    com.vogella.asciidoc.lsp.server/        LSP4J server, no Eclipse dependencies, includes the HTML renderer
    com.vogella.asciidoc.lsp.client/        LSP4E registration and in-process connection provider
  tests/
    com.vogella.asciidoc.lsp.server.tests/  fragment of the server, JUnit 5
    com.vogella.asciidoc.editor.tests/      UI test, optional (phase 5)
  features/
    com.vogella.asciidoc.feature/
  sites/
    com.vogella.asciidoc.updatesite/        category.xml
  .github/workflows/build.yml
```

`bundles`, `tests`, `features` and `sites` are folder names that Tycho's pomless build turns into aggregators automatically.
No `pom.tycho` and no `pom.xml` is needed in those folders.

Rename map (apply with `sed` to file contents and with `git mv` to paths; check with `grep -rn "vogella.ide\|vogella.lsp\|vogella.editor" --include=* .` afterwards, the result must be empty):

| Old | New |
|---|---|
| `$SRC/com.vogella.ide.editor.asciidoc` | `bundles/com.vogella.asciidoc.editor` |
| `$SRC/lsp/com.vogella.lsp.asciidoc.server` | `bundles/com.vogella.asciidoc.lsp.server` |
| `$SRC/lsp/com.vogella.lsp.asciidoc.client` | `bundles/com.vogella.asciidoc.lsp.client` |
| `$SRC/lsp/com.vogella.lsp.asciidoc.server.tests` | `tests/com.vogella.asciidoc.lsp.server.tests` |
| package `com.vogella.lsp.asciidoc.server` | `com.vogella.asciidoc.lsp.server` |
| package `com.vogella.lsp.asciidoc.client` | `com.vogella.asciidoc.lsp.client` |
| package `com.vogella.lsp.asciidoc.server.tests` | `com.vogella.asciidoc.lsp.server.tests` |
| content type id `com.vogella.editor.asciidoc.contenttype` | `com.vogella.asciidoc.contenttype` |
| grammar scope `com.vogella.ide.editor.asciidoc.grammar` | `com.vogella.asciidoc.grammar` (also inside `syntaxes/asciidoc.json` if the scope name appears there) |
| LS id `org.vogella.lsp.asciidoc.server` | `com.vogella.asciidoc.lsp.server` |
| class `ConnectionProviderSolution` + `AbstractConnectionProvider` | one class `AsciidocConnectionProvider` |
| `Bundle-Vendor: VOGELLA` | `Bundle-Vendor: vogella GmbH` |

Files from `$SRC` that are **not** copied: `com.vogella.ide.editor.asciidoc.tests` (empty), everything outside the four projects above except `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`.

## 3. Phase 1: repository skeleton and green build

### 3.1 Build infrastructure

Create these files with exactly this content.

`.mvn/extensions.xml`

```xml
<extensions>
  <extension>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>tycho-build</artifactId>
    <version>${tycho.version}</version>
  </extension>
</extensions>
```

`.mvn/maven.config`

```
-Dtycho.localArtifacts=ignore
-Dtycho.version=5.0.4
-Dtycho.pomless.parent=${maven.multiModuleProjectDirectory}
```

`.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`: copy from `$SRC` with `cp -p` (keeps the executable bit on `mvnw`).
The properties file must point at `apache-maven-3.9.11-bin.zip`.

`pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.vogella.asciidoc</groupId>
  <artifactId>com.vogella.asciidoc.parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <tycho.version>5.0.4</tycho.version>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-maven-plugin</artifactId>
        <version>${tycho.version}</version>
        <extensions>true</extensions>
      </plugin>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>target-platform-configuration</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <target>
            <file>${maven.multiModuleProjectDirectory}/target-platform/target-platform.target</file>
          </target>
          <executionEnvironment>JavaSE-25</executionEnvironment>
          <environments>
            <environment>
              <os>linux</os>
              <ws>gtk</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>win32</os>
              <ws>win32</ws>
              <arch>x86_64</arch>
            </environment>
            <environment>
              <os>macosx</os>
              <ws>cocoa</ws>
              <arch>aarch64</arch>
            </environment>
          </environments>
        </configuration>
      </plugin>
    </plugins>
  </build>

  <modules>
    <module>bundles</module>
    <module>tests</module>
    <module>features</module>
    <module>sites</module>
  </modules>
</project>
```

`target-platform/target-platform.target`

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target name="target-platform">
  <locations>
    <location includeAllPlatforms="false" includeConfigurePhase="true" includeMode="planner" includeSource="true" type="InstallableUnit">
      <repository location="https://download.eclipse.org/releases/2026-06"/>
      <unit id="org.eclipse.platform.feature.group"/>
      <unit id="org.eclipse.equinox.sdk.feature.group"/>
      <unit id="junit-jupiter-api"/>
      <unit id="junit-jupiter-engine"/>
    </location>
    <location includeAllPlatforms="false" includeConfigurePhase="true" includeMode="planner" includeSource="true" type="InstallableUnit">
      <repository location="https://download.eclipse.org/tm4e/releases/0.18.0/"/>
      <unit id="org.eclipse.tm4e.feature.feature.group"/>
    </location>
    <location includeAllPlatforms="false" includeConfigurePhase="true" includeMode="planner" includeSource="true" type="InstallableUnit">
      <repository location="https://download.eclipse.org/lsp4e/releases/0.30.8/"/>
      <unit id="org.eclipse.lsp4e"/>
    </location>
  </locations>
  <targetJRE path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-25"/>
</target>
```

The unit ids above were verified against the repositories on 2026-08-31 (`org.eclipse.lsp4e` 0.19.15 in the 0.30.8 repo, `org.eclipse.tm4e.feature.feature.group` 0.18.0).
If resolution fails because `com.google.gson` is missing, add this fourth location:

```xml
    <location includeDependencyDepth="none" includeSource="true" missingManifest="generate" type="Maven">
      <dependencies>
        <dependency>
          <groupId>com.google.code.gson</groupId>
          <artifactId>gson</artifactId>
          <version>2.13.1</version>
          <type>jar</type>
        </dependency>
      </dependencies>
    </location>
```

`.gitignore`

```
bin/
target/
*~
*.bak
.DS_Store
.polyglot.*
.tycho-consumer-pom.xml
```

`LICENSE`: the full EPL-2.0 text from `https://www.eclipse.org/legal/epl-2.0/epl-2.0.txt`.

### 3.2 Copy and rename the bundles

1. Copy the four projects listed in the rename map to their new locations.
2. Apply the rename map to directory names, `.project` `<name>`, `MANIFEST.MF`, `plugin.xml`, Java package declarations and imports, and test sources.
3. Delete `bin/` and `target/` folders if any were copied.
4. In `bundles/com.vogella.asciidoc.editor/plugin.xml` remove the whole `org.eclipse.ui.ide.unassociatedEditorStrategy` extension (it references a class that does not exist).
5. In `bundles/com.vogella.asciidoc.editor/META-INF/MANIFEST.MF` replace `Require-Bundle` and `Import-Package` with the block in 3.3.
6. In the client bundle delete `AbstractConnectionProvider.java` and `ConnectionProviderSolution.java` and create `AsciidocConnectionProvider.java` from 3.4.
   The old code echoed all protocol traffic to `System.err` and shared one static server instance across restarts; both are gone.
7. In the server bundle keep all classes for now (cleanup happens in phase 4).
8. Copy `$SRC/lsp/com.vogella.lsp.asciidoc.server.tests/manual/` as `tests/com.vogella.asciidoc.lsp.server.tests/manual/` (sample documents for manual testing).

### 3.3 Manifests

`bundles/com.vogella.asciidoc.editor/META-INF/MANIFEST.MF`

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: AsciiDoc Editor
Bundle-SymbolicName: com.vogella.asciidoc.editor;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Vendor: vogella GmbH
Bundle-RequiredExecutionEnvironment: JavaSE-25
Automatic-Module-Name: com.vogella.asciidoc.editor
Bundle-ActivationPolicy: lazy
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.core.resources,
 org.eclipse.core.filesystem,
 org.eclipse.ui,
 org.eclipse.ui.ide,
 org.eclipse.ui.editors,
 org.eclipse.ui.workbench.texteditor,
 org.eclipse.jface.text,
 org.eclipse.ui.genericeditor,
 org.eclipse.tm4e.registry,
 org.eclipse.tm4e.languageconfiguration,
 org.eclipse.lsp4e,
 org.eclipse.lsp4j,
 org.eclipse.lsp4j.jsonrpc,
 com.vogella.asciidoc.lsp.server
```

`bundles/com.vogella.asciidoc.editor/build.properties`

```
source.. = src/
output.. = bin/
bin.includes = META-INF/,\
               .,\
               plugin.xml,\
               language-configurations/,\
               icons/,\
               syntaxes/
```

Create an empty `src/` folder in the editor bundle in this phase (a `.gitkeep` is fine); phase 2 fills it.

`bundles/com.vogella.asciidoc.lsp.server/META-INF/MANIFEST.MF`

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: AsciiDoc Language Server
Bundle-SymbolicName: com.vogella.asciidoc.lsp.server
Bundle-Version: 1.0.0.qualifier
Bundle-Vendor: vogella GmbH
Bundle-RequiredExecutionEnvironment: JavaSE-25
Automatic-Module-Name: com.vogella.asciidoc.lsp.server
Export-Package: com.vogella.asciidoc.lsp.server,
 com.vogella.asciidoc.lsp.server.render
Require-Bundle: org.eclipse.lsp4j,
 org.eclipse.lsp4j.jsonrpc
```

The `render` package is created in phase 3; until then remove that line or create the package with one class, otherwise the manifest exports a non-existent package (a build warning, but keep the build clean).

`bundles/com.vogella.asciidoc.lsp.client/META-INF/MANIFEST.MF`

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: AsciiDoc Language Server Client
Bundle-SymbolicName: com.vogella.asciidoc.lsp.client;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Vendor: vogella GmbH
Bundle-RequiredExecutionEnvironment: JavaSE-25
Automatic-Module-Name: com.vogella.asciidoc.lsp.client
Bundle-ActivationPolicy: lazy
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.lsp4e,
 org.eclipse.lsp4j,
 org.eclipse.lsp4j.jsonrpc,
 com.vogella.asciidoc.lsp.server
```

`bundles/com.vogella.asciidoc.lsp.client/plugin.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
   <extension point="org.eclipse.lsp4e.languageServer">
      <server
            class="com.vogella.asciidoc.lsp.client.AsciidocConnectionProvider"
            id="com.vogella.asciidoc.lsp.server"
            label="AsciiDoc Language Server"
            serverInterface="com.vogella.asciidoc.lsp.server.AsciidocLanguageServerApi">
      </server>
      <contentTypeMapping
            contentType="com.vogella.asciidoc.contenttype"
            id="com.vogella.asciidoc.lsp.server"
            languageId="asciidoc">
      </contentTypeMapping>
   </extension>
</plugin>
```

`serverInterface` is created in phase 3 (3.1 of phase 3).
For phase 1 leave the attribute out and add it in phase 3.

`tests/com.vogella.asciidoc.lsp.server.tests/META-INF/MANIFEST.MF`

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: AsciiDoc Language Server Tests
Bundle-SymbolicName: com.vogella.asciidoc.lsp.server.tests
Bundle-Version: 1.0.0.qualifier
Bundle-Vendor: vogella GmbH
Bundle-RequiredExecutionEnvironment: JavaSE-25
Automatic-Module-Name: com.vogella.asciidoc.lsp.server.tests
Fragment-Host: com.vogella.asciidoc.lsp.server
Require-Bundle: junit-jupiter-api
```

`build.properties` of the test fragment: `source.. = src/`, `output.. = bin/`, `bin.includes = META-INF/,.`.
The `manual/` folder is not part of `bin.includes`.

### 3.4 Connection provider

`bundles/com.vogella.asciidoc.lsp.client/src/com/vogella/asciidoc/lsp/client/AsciidocConnectionProvider.java`

```java
package com.vogella.asciidoc.lsp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Future;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import com.vogella.asciidoc.lsp.server.AsciidocLanguageServer;

/**
 * Runs the AsciiDoc language server in-process and connects it to LSP4E via piped streams.
 */
public class AsciidocConnectionProvider implements StreamConnectionProvider {

	private static final int PIPE_SIZE = 64 * 1024;

	private PipedInputStream clientInput;
	private PipedOutputStream clientOutput;
	private Future<Void> listening;

	@Override
	public void start() throws IOException {
		PipedInputStream serverInput = new PipedInputStream(PIPE_SIZE);
		PipedOutputStream serverOutput = new PipedOutputStream();
		clientInput = new PipedInputStream(serverOutput, PIPE_SIZE);
		clientOutput = new PipedOutputStream(serverInput);

		AsciidocLanguageServer server = new AsciidocLanguageServer();
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, serverInput, serverOutput);
		server.setRemoteProxy(launcher.getRemoteProxy());
		listening = launcher.startListening();
	}

	@Override
	public InputStream getInputStream() {
		return clientInput;
	}

	@Override
	public OutputStream getOutputStream() {
		return clientOutput;
	}

	@Override
	public void stop() {
		if (listening != null) {
			listening.cancel(true);
		}
		closeQuietly(clientInput);
		closeQuietly(clientOutput);
	}

	private static void closeQuietly(AutoCloseable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (Exception e) {
			// nothing useful to do while shutting down
		}
	}
}
```

A new server instance per `start()` is intentional: LSP4E may restart the server, and the old shared static instance kept stale state.

### 3.5 Feature and update site

`features/com.vogella.asciidoc.feature/feature.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feature
      id="com.vogella.asciidoc.feature"
      label="AsciiDoc Editor"
      version="1.0.0.qualifier"
      provider-name="vogella GmbH"
      license-feature="">

   <description>
      AsciiDoc editor for the Eclipse IDE with syntax highlighting, content assist, validation, hyperlinks and an HTML preview, driven by a language server.
   </description>

   <copyright>
      Copyright (c) 2026 vogella GmbH
   </copyright>

   <license url="https://www.eclipse.org/legal/epl-2.0/">
      Eclipse Public License 2.0
   </license>

   <plugin id="com.vogella.asciidoc.editor" version="0.0.0"/>
   <plugin id="com.vogella.asciidoc.lsp.server" version="0.0.0"/>
   <plugin id="com.vogella.asciidoc.lsp.client" version="0.0.0"/>
</feature>
```

`features/com.vogella.asciidoc.feature/build.properties`: `bin.includes = feature.xml`.

`sites/com.vogella.asciidoc.updatesite/category.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site>
   <feature id="com.vogella.asciidoc.feature">
      <category name="asciidoc"/>
   </feature>
   <category-def name="asciidoc" label="AsciiDoc Editor">
      <description>AsciiDoc editing support for the Eclipse IDE, provided by vogella GmbH.</description>
   </category-def>
   <repository-reference location="https://download.eclipse.org/releases/2026-06" enabled="true"/>
   <repository-reference location="https://download.eclipse.org/lsp4e/releases/0.30.8/" enabled="true"/>
   <repository-reference location="https://download.eclipse.org/tm4e/releases/0.18.0/" enabled="true"/>
</site>
```

The repository references let p2 pull LSP4E and TM4E when a user installs into an IDE that lacks them.
The site itself contains only our three bundles and the feature.

Every folder under `bundles/`, `tests/`, `features/` and `sites/` needs a `.project` file (copy the shape from the source projects, adjust `<name>`).
Leave out the `org.eclipse.m2e.core.maven2Builder` build command and the `filteredResources` block.

### 3.6 CI and GitHub Pages

`.github/workflows/build.yml`

```yaml
name: Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - name: Install Xvfb
        run: sudo apt-get update && sudo apt-get install -y xvfb
      - name: Build
        run: xvfb-run -a ./mvnw -ntp clean verify
      - name: Upload update site
        if: github.event_name == 'push' && github.ref == 'refs/heads/main'
        uses: actions/upload-pages-artifact@v3
        with:
          path: sites/com.vogella.asciidoc.updatesite/target/repository

  deploy:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

GitHub Pages is already enabled for the repository with `build_type: workflow` (done 2026-08-31), so the `deploy` job works on the first push to `main`.
No further Pages configuration is needed.

### 3.7 Exit criteria for phase 1

```bash
./mvnw -ntp clean verify
ls sites/com.vogella.asciidoc.updatesite/target/repository/plugins
grep -rn "vogella.ide\|vogella.lsp\|vogella.editor" --exclude-dir=target --exclude-dir=.git . ; echo "exit code $? (1 means clean)"
```

Expected: build `SUCCESS`; the plugins folder lists `com.vogella.asciidoc.editor_*.jar`, `com.vogella.asciidoc.lsp.server_*.jar`, `com.vogella.asciidoc.lsp.client_*.jar`; the four existing server test classes ran (check `tests/com.vogella.asciidoc.lsp.server.tests/target/surefire-reports/`); the grep finds nothing.

## 4. Phase 2: two-tab editor

All code goes into `bundles/com.vogella.asciidoc.editor/src/com/vogella/asciidoc/editor/`.

### 4.1 Register the editor

Replace the `org.eclipse.ui.editors` extension in the editor bundle's `plugin.xml` (the old one bound the plain generic editor) with:

```xml
   <extension point="org.eclipse.ui.editors">
      <editor
            id="com.vogella.asciidoc.editor"
            name="AsciiDoc Editor"
            icon="icons/asciidoc.png"
            class="com.vogella.asciidoc.editor.AsciidocEditor"
            contributorClass="com.vogella.asciidoc.editor.AsciidocEditorContributor"
            default="true">
         <contentTypeBinding contentTypeId="com.vogella.asciidoc.contenttype"/>
      </editor>
   </extension>
```

Also change the content type's `file-extensions` to `adoc,asciidoc` and its `name` to `AsciiDoc`.
Keep the TM4E grammar, language configuration and generic editor icon extensions as they are (with the renamed ids).

### 4.2 `AsciidocEditor`

`AsciidocEditor extends org.eclipse.ui.part.MultiPageEditorPart implements org.eclipse.ui.ide.IGotoMarker`.

Requirements:

* Page 0, title `Source`: an instance of `org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor`, added with `addPage(editor, getEditorInput())`.
  This class is internal API (the package is exported with `x-internal`), which produces a "discouraged access" warning; that is accepted.
  Using it is what makes LSP4E (completion, hover, diagnostics, hyperlinks, outline) and TM4E work without any further wiring.
* Page 1, title `Preview`: a `Composite` holding an `org.eclipse.swt.browser.Browser` (`SWT.NONE`).
  In phase 2 the browser shows the static text `<p>Preview is not available yet.</p>`; phase 3 replaces it.
  If `new Browser(...)` throws `SWTError`, show a `Label` with the error message instead of failing the editor.
* `init(site, input)`: call `super.init`, then `setPartName(input.getName())`.
* `getAdapter(Class<T>)`: for `ITextEditor.class` and `IGotoMarker.class` return the source editor (or `this` for `IGotoMarker`) regardless of the active page; otherwise `super.getAdapter`.
* `gotoMarker(IMarker)`: `setActivePage(0)` then `IDE.gotoMarker(sourceEditor, marker)`.
* `doSave`, `doSaveAs`, `isSaveAsAllowed`: delegate to the source editor; after `doSaveAs` call `setInput(sourceEditor.getEditorInput())` and update the part name.
* `pageChange(int)`: call `super.pageChange`; when the new page is 1, call `refreshPreview()` (no-op in phase 2, defined in phase 3).
* `dispose`: dispose the browser resources if needed, then `super.dispose`.
* Expose `IDocument getDocument()` returning `sourceEditor.getDocumentProvider().getDocument(sourceEditor.getEditorInput())` (used by phase 3 and by the tests).

### 4.3 `AsciidocEditorContributor`

```java
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
```

### 4.4 Real outline

In `AsciidocTextDocumentService.documentSymbol` replace the `MyClass`/`myMethod` stub with the real section hierarchy:

* A heading is a line matching `^(={1,6})\s+(.+?)\s*(=+\s*)?$`; the number of `=` is the level.
* Produce nested `DocumentSymbol`s (`SymbolKind.Module` is fine), `range` from the heading line to the line before the next heading of the same or a higher level (or end of document), `selectionRange` the heading line, `name` the title text.
* Return `Either.forRight(symbol)` entries.
* Lines inside delimited blocks (`----`, `....`, `////`, `====`, `****`, `____`, `|===`) are not headings.

Add a test `AsciidocDocumentSymbolTest` in the test fragment covering nesting and a `=` inside a `----` block.

### 4.5 Exit criteria for phase 2

```bash
./mvnw -ntp clean verify
```

Then a manual check in a runtime workbench (describe in `PROGRESS.md` if you cannot run a workbench; the human will do it):

1. Open a `.adoc` file: the `AsciidocEditor` opens with tabs `Source` and `Preview`.
2. `Ctrl+Space` after `image::` offers files from the `img/` folder.
3. `F3` on `include::other.adoc[]` opens `other.adoc`.
4. The Outline view shows the section hierarchy.
5. `Ctrl+Z`, `Ctrl+F`, `Ctrl+S` work on the Source page.

## 5. Phase 3: preview with images

### 5.1 Custom LSP request

`bundles/com.vogella.asciidoc.lsp.server/src/com/vogella/asciidoc/lsp/server/AsciidocLanguageServerApi.java`

```java
package com.vogella.asciidoc.lsp.server;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Language server protocol of the AsciiDoc server, extended with a preview request.
 */
public interface AsciidocLanguageServerApi extends LanguageServer {

	/** Renders the current in-memory content of the document to a complete HTML page. */
	@JsonRequest(value = "asciidoc/preview", useSegment = false)
	CompletableFuture<String> preview(TextDocumentIdentifier document);
}
```

`AsciidocLanguageServer implements AsciidocLanguageServerApi` and delegates `preview` to `AsciidocTextDocumentService.preview(uri)`, which:

1. Looks up the open document text (extend `AsciidocDocumentModel` with `getText()`; store the full text on `didOpen`/`didChange`).
2. Computes `baseDir = Paths.get(URI.create(uri)).getParent()`.
3. Calls `new AsciidocHtmlRenderer().render(text, new RenderOptions(baseDir, this::openDocumentText, true))` where `openDocumentText(Path)` returns the in-memory text of an open document at that path, or empty.
   Normalize when comparing: LSP4E sends `file:///...` URIs; use `Paths.get(URI.create(uri)).toAbsolutePath().normalize()` as the map key for this lookup.
4. Returns the HTML string; for an unknown document returns a page saying `Document is not open`.

Add `serverInterface="com.vogella.asciidoc.lsp.server.AsciidocLanguageServerApi"` to the client `plugin.xml` now.

### 5.2 Renderer

Package `com.vogella.asciidoc.lsp.server.render`, classes `AsciidocHtmlRenderer`, `RenderOptions` (record: `Path baseDir`, `Function<Path, Optional<String>> openDocuments`, `boolean inlineImages`).
Plain Java, no dependencies beyond the JDK.
Output is a complete HTML5 document with an embedded stylesheet (system font stack, `max-width: 60em`, `margin: auto`, code blocks with a light gray background, admonitions with a colored left border, `@media (prefers-color-scheme: dark)` variant).
All text is HTML-escaped before inline formatting is applied.

Block syntax to support, checked in this order per line:

| Syntax | HTML |
|---|---|
| `// comment`, `////` ... `////` | dropped |
| `:name: value` | stored as attribute, dropped from output; `{name}` in text is substituted; unknown `{name}` stays literal |
| `= Title` to `====== Title` | `<h1 id="...">` to `<h6 id="...">`; id from a preceding `[[id]]` or `[#id]` line, otherwise `_` plus the title lowercased with every run of non-alphanumeric characters replaced by `_` |
| `.Block title` (no space after the dot) | `<div class="title">` attached to the next block |
| `[source,lang]` or `[source]` then `----` ... `----` | `<pre><code class="language-lang">` |
| `----` ... `----` (no source attribute) | `<pre><code>` |
| `....` ... `....` | `<pre>` |
| `====` ... `====` | `<div class="example">`; with a preceding `[NOTE]` etc. an admonition block |
| `****` ... `****` | `<aside class="sidebar">` |
| `____` ... `____` | `<blockquote>` |
| `\|===` ... `\|===` | `<table>`; cells are split on `\|`; a row is one line; the first row is `<th>` when it is followed by an empty line or the block has `[options="header"]` |
| `NOTE: text` (also TIP, IMPORTANT, WARNING, CAUTION) | `<div class="admonition note"><strong>Note</strong><p>text</p></div>` |
| `image::target[alt,width]` | `<figure><img src alt width><figcaption>` (caption from block title) |
| `include::target[]` | content of the target rendered in place; depth limit 10; cycle detection; missing target renders `<p class="error">include not found: target</p>`; `lines=` and `tag=` attributes are out of scope |
| `* item`, `** nested`, `- item` | `<ul>`, nesting by marker count; `* [x]`/`* [ ]` becomes a disabled checkbox |
| `. item`, `.. nested` | `<ol>` |
| `term:: description` | `<dl>` |
| `'''` | `<hr>` |
| `<<<` | dropped |
| `+` alone on a line | dropped (list continuation is out of scope) |
| any other consecutive non-empty lines | `<p>`; a trailing ` +` becomes `<br>` |

Inline syntax, applied to paragraphs, list items, table cells, titles and admonition text:

| Syntax | HTML |
|---|---|
| `*bold*`, `**bold**` | `<strong>` |
| `_italic_`, `__italic__` | `<em>` |
| `` `mono` `` | `<code>` |
| `#text#` | `<mark>` |
| `^sup^`, `~sub~` | `<sup>`, `<sub>` |
| `+text+` | literal text |
| `https://host/path` and `http://...` | `<a href>`; `https://host/path[text]` uses text |
| `link:target[text]` | `<a href>`; a relative target that ends in `.adoc` becomes `file:///absolute/path` resolved against `baseDir`; other relative targets become `file:///...` too |
| `xref:target[text]`, `<<target,text>>`, `<<target>>` | `<a href="#target">` when target has no `.adoc`; otherwise `file:///...#fragment`; text defaults to the target |
| `image:target[alt]` | inline `<img>` |
| `kbd:[Ctrl+S]`, `btn:[OK]`, `menu:File[Save]` | `<kbd>`, `<b class="button">`, `<span class="menu">` |
| `{attr}` | attribute value |

Image resolution: `baseDir` joined with the `imagesdir` attribute (default empty) joined with the target.
With `inlineImages` true, PNG, JPG, JPEG, GIF, SVG and WEBP files up to 10 MB are embedded as `data:<mime>;base64,...`; larger files use a `file:///` URL; a missing file renders `<span class="missing">image not found: target</span>`.
Inlining is what makes images work in `Browser.setText`, which has no base URL and cannot load `file://` resources on all platforms.

Non-goals for the renderer: footnotes, callouts, conditional preprocessor directives (`ifdef::`), passthrough blocks, nested tables, list continuation, `lines=`/`tag=` include filters, escaping with backslash.
Unknown constructs must render as plain paragraphs, never throw.

### 5.3 Preview page in the editor

In `AsciidocEditor`:

* `refreshPreview()`: obtain the `IDocument`, call

  ```java
  LanguageServers.forDocument(document)
      .computeFirst(ls -> ((AsciidocLanguageServerApi) ls).preview(new TextDocumentIdentifier(uri)))
  ```

  where `uri` is `LSPEclipseUtils.toUri(document).toASCIIString()` (both classes from `org.eclipse.lsp4e`).
  On completion, set the HTML into the browser on the UI thread (`Display.asyncExec`, check `browser.isDisposed()`); on failure show the exception message in the browser.
* While the Preview page is visible, register an `IDocumentListener` on the document and re-render with a 500 ms debounce (`Display.timerExec`); remove the listener when switching back to Source or on dispose.
* Preserve the vertical scroll position across refreshes: run `window.scrollY` via `browser.evaluate` before `setText` and `window.scrollTo(0, y)` after the `ProgressListener.completed` event.
* `LocationListener.changing`: for `file:` locations that end with `.adoc` (ignore the fragment) open the file in Eclipse (`IDE.openEditorOnFileStore(page, EFS.getLocalFileSystem().getStore(URI))`) and set `event.doit = false`; for `http`/`https` open the external browser (`PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(...)`) and set `event.doit = false`; `about:blank` and fragment-only navigation pass through.

Fallback: if after two honest attempts the custom request does not reach the server (check the LSP4E console / error log for "Unsupported request method"), call `AsciidocHtmlRenderer` directly from the editor bundle with the `IDocument` text and record this deviation in `PROGRESS.md`.
The rendering code stays in the server bundle either way.

### 5.4 Exit criteria for phase 3

* Renderer unit tests (`AsciidocHtmlRendererTest` in the test fragment, pure JUnit, use `@TempDir`) cover: headings with ids, bold/italic/mono, unordered and ordered lists, source block with language class, admonition paragraph, table with header row, `image::` inlined as `data:image/png;base64,`, missing image, `include::` of a file in the temp dir, include cycle, `<<anchor>>`, `link:` to `.adoc`, attribute substitution, HTML escaping of `<script>`.
* `AsciidocPreviewTest` (test fragment): open a document through the service and assert `preview` returns HTML containing `<h1` and `<img src="data:`.
* `./mvnw -ntp clean verify` green.
* Manual: `tests/com.vogella.asciidoc.lsp.server.tests/manual/Testing.adoc` opened in the editor shows the heading, the `Sample.png` image and the content of `Help.adoc` on the Preview tab; editing the source updates the preview within a second.

## 6. Phase 4: language features

Work in `AsciidocTextDocumentService`.
Extract a reusable `AsciidocDocumentModel` that parses once per change: lines, headings (level, title, id, line), anchors (`[[id]]`, `[#id]`), attribute entries, macros (`include`, `image`, `link`, `xref`, `<<...>>`) with their line and column ranges, and delimited block ranges.
All feature methods work on this model; no method scans raw lines with ad-hoc regexes anymore.

### 6.1 Remove demo leftovers

Delete: the `PLACEHOLDER_TEXT` diagnostic and its code action, the `TODO` code lens (`codeLens`, `resolveCodeLens`, `setCodeLensProvider`), `AsciidocElements` if no longer used, the unused `formatting`/`rename` overrides (return `null` defaults via the interface defaults instead), and the `// <.>` callout comments in `Main`.
Keep `Main` (standalone stdio launcher).

### 6.2 Diagnostics (`publishDiagnostics` on `didOpen` and `didChange`)

| Check | Severity | Range |
|---|---|---|
| `include::target[]` target file does not exist | Error | the target text |
| `image::target[]` / `image:target[]` file does not exist (resolved with `imagesdir`) | Warning | the target text |
| `<<id>>` / `xref:id[]` (no `.adoc` in target) with no matching anchor or heading id in the same document | Warning | the id text |
| duplicate anchor id in the document | Warning | the second occurrence |
| `xref:file.adoc[]` / `link:file.adoc[]` file does not exist | Warning | the target text |

Set `source` to `asciidoc` on every diagnostic.
Send an empty diagnostics list on `didClose`.

### 6.3 Completion

Keep the existing image and include path completion (it lists files of the right type relative to the document, with prefix filtering).
Add:

| Trigger | Items | Kind |
|---|---|---|
| line start or after typing letters at line start | `NOTE: `, `TIP: `, `IMPORTANT: `, `WARNING: `, `CAUTION: `, `image::${1:file}[]`, `include::${1:file}[]`, `[source,${1:java}]\n----\n$0\n----`, `\|===\n$0\n\|===`, `.${1:Title}`, `= ${1:Title}`, `== ${1:Title}` | Snippet |
| after `<<` or `xref:` | every anchor id and heading id of the document, label = id, detail = heading title | Reference |
| after `{` | every attribute name defined with `:name:` in the document | Variable |
| after `link:` or `xref:` followed by a path prefix | `.adoc` files like the include completion | File |

Register `<`, `{`, `:` and `/` as `triggerCharacters` in `CompletionOptions`.
Snippets use `InsertTextFormat.Snippet`.

### 6.4 Hyperlinks and navigation

* `documentLink`: `include::`, `image::`/`image:`, `link:`, `xref:file.adoc`, bare `http(s)://` URLs; `target` is the resolved `file:///` URI or the URL.
* `definition`: `include::` (exists), `<<id>>` and `xref:id[]` to the anchor or heading line in the same document; `xref:file.adoc#id[]` to the heading in the other file (read the file from disk or from the open documents).
* `hover`: keep the existing image and include hover; add for `<<id>>`: the heading title and level.

### 6.5 Folding

Capability `setFoldingRangeProvider(true)`.
Ranges: each section (heading line to the line before the next heading of the same or higher level), each delimited block, each comment block.
`FoldingRangeKind.Comment` for comment blocks, `Region` otherwise.

### 6.6 Out of scope for phase 4

Semantic tokens, rename, references, formatting, code lens, workspace symbols, `ifdef`/`ifndef`, multi-file anchor index, spell checking.

### 6.7 Exit criteria for phase 4

One test class per feature in the test fragment: `AsciidocDiagnosticsTest`, `AsciidocCompletionTest` (extend the existing one), `AsciidocDocumentLinkTest` (extend), `AsciidocDefinitionTest` (extend), `AsciidocHoverTest` (extend), `AsciidocFoldingTest`, `AsciidocDocumentModelTest`.
Diagnostics are tested with a recording `LanguageClient` stub passed to `setRemoteProxy`.
`./mvnw -ntp clean verify` green.

## 7. Phase 5: UI test (optional, time-boxed)

Goal: one end-to-end test that proves LSP4E connects to the server inside the two-tab editor.
Time box: if it is not green after two honest attempts, delete the `tests/com.vogella.asciidoc.editor.tests` folder, note it in `PROGRESS.md`, and move on.

`tests/com.vogella.asciidoc.editor.tests/` with `MANIFEST.MF` (`Bundle-SymbolicName: com.vogella.asciidoc.editor.tests`, `Require-Bundle: org.eclipse.core.resources, org.eclipse.ui, org.eclipse.ui.ide, org.eclipse.jface.text, org.eclipse.lsp4e, org.eclipse.lsp4j, com.vogella.asciidoc.editor, com.vogella.asciidoc.lsp.server, junit-jupiter-api`) and its own `pom.xml` (the only module with one, needed for the UI harness settings):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.vogella.asciidoc</groupId>
    <artifactId>com.vogella.asciidoc.parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>com.vogella.asciidoc.editor.tests</artifactId>
  <packaging>eclipse-test-plugin</packaging>
  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-surefire-plugin</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <useUIHarness>true</useUIHarness>
          <useUIThread>true</useUIThread>
          <product>org.eclipse.platform.ide</product>
          <application>org.eclipse.ui.ide.workbench</application>
          <dependencies>
            <dependency>
              <type>p2-installable-unit</type>
              <artifactId>org.eclipse.platform.feature.group</artifactId>
              <version>0.0.0</version>
            </dependency>
          </dependencies>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

If the `tests` aggregator does not pick up a module that has its own `pom.xml`, add `<module>tests/com.vogella.asciidoc.editor.tests</module>` to the root `pom.xml` modules.

Test `AsciidocEditorTest`:

1. Create a project and a file `doc.adoc` with `= Title\n\nimage::img/pic.png[]\n` plus `img/pic.png` (any small PNG, generate it with `javax.imageio`).
2. `IDE.openEditor(page, file)`; assert the part is an `AsciidocEditor` with two pages.
3. `LanguageServers.forDocument(doc).computeFirst(ls -> ls.getTextDocumentService().completion(params))` with the cursor after `image::img/`; wait up to 30 s; assert an item `pic.png`.
4. `computeFirst(ls -> ((AsciidocLanguageServerApi) ls).preview(id))`; assert the result contains `<img src="data:image/png`.

Run locally headless with:

```bash
env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a ./mvnw -ntp clean verify
```

## 8. Phase 6: documentation

`README.md` (one sentence per line) with: what it is, feature list (highlighting, completion, validation, hyperlinks, outline, folding, preview with images), the install URL `https://vogellacompany.github.io/eclipse-asciidoc/` with the note that LSP4E and TM4E are pulled from the referenced Eclipse repositories automatically, build instructions (`./mvnw clean verify`), how to run from the IDE (import projects, set `target-platform/target-platform.target` as active target, launch a runtime workbench), license, and a short "Architecture" section (three bundles, in-process language server, `asciidoc/preview` request).
Move the feature description from `$SRC/AsciidocSupport.adoc` into `docs/features.adoc`, rewritten to match what actually exists after phase 4.

Leave `PLAN.md` and `PROGRESS.md` in place; the human decides what happens to them.

## 9. Known traps

* Pomless aggregators only work for the folder names `bundles`, `tests`, `features`, `sites` (and a few others). Do not rename these folders.
* A `.target` file and its folder can have any name here because the root pom references it by `<file>`; it is not a module.
* Do not copy `-Dtycho.baseline.replace=none` or `-Pbuild-individual-bundles` from other repositories; this build has no baseline and no such profile.
* `Bundle-RequiredExecutionEnvironment: JavaSE-25` must appear in every manifest, otherwise Tycho compiles with a different level than the root pom's `executionEnvironment`.
* Test fragments need `Fragment-Host`; the JUnit 5 engine is added by tycho-surefire when `junit-jupiter-api` is required.
* LSP4E identifies documents by `file:///` URI; when mapping back to `Path` always go through `Paths.get(URI.create(uri))`, never string manipulation.
* `Browser.setText` has no base URL: images and links must be absolute (`data:` or `file:///`).
* `ExtensionBasedTextEditor` is internal; the discouraged access warning is accepted, do not try to reimplement it.
* Custom JSON-RPC method names: `@JsonRequest(value = "asciidoc/preview", useSegment = false)` on the interface, the server class must implement that interface, and the client `plugin.xml` must name it in `serverInterface`, otherwise the proxy cast in the editor fails with `ClassCastException`.
* `PipedInputStream` throws `Write end dead` when the writing thread ends; the connection provider therefore keeps one launcher per `start()` and cancels it in `stop()`.
