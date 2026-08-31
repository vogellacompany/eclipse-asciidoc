# Progress

Tick a step only after its commit exists.
Add a short note under a step when something deviated from `PLAN.md`.
On resume: `git status --short`, `git log --oneline -20`, then continue with the first unchecked step.

## Phase 1: skeleton and green build

- [x] 1.1 Build infrastructure: `.mvn/`, `mvnw`, `pom.xml`, `target-platform/`, `.gitignore`, `LICENSE` (PLAN 3.1)
- [x] 1.2 Copy and rename the four projects into `bundles/` and `tests/` (PLAN 3.2, rename map in PLAN 2)
- [x] 1.3 Manifests, `build.properties`, `.project` files as specified (PLAN 3.3)
- [x] 1.4 `AsciidocConnectionProvider` replaces the two old provider classes (PLAN 3.4)
- [x] 1.5 Feature and update site (PLAN 3.5)
- [x] 1.6 CI workflow (PLAN 3.6); GitHub Pages is already enabled
- [x] 1.7 Exit criteria run and green (PLAN 3.7)

Notes (deviations from PLAN, required to make the build green against the pinned versions):
- `AsciidocConnectionProvider` also implements `getErrorStream()` returning `null`; LSP4E 0.19.15 (in the 0.30.8 repo) declares it abstract.
- Target platform adds `junit-platform-launcher`, `junit-platform-suite-api`, `junit-platform-suite-engine`; the tycho-surefire junit6 provider requires `org.junit.platform.suite.api`.
- Removed `license-feature=""` from `feature.xml`; the empty value made p2 require a nonexistent `.feature.group`.
- Remaining `grep` matches for the old ids are only inside `PLAN.md` (the rename map text); no code or config matches.

## Phase 2: two-tab editor

- [x] 2.1 Editor registration in `plugin.xml` (PLAN 4.1)
- [x] 2.2 `AsciidocEditor` with Source and Preview pages (PLAN 4.2)
- [x] 2.3 `AsciidocEditorContributor` (PLAN 4.3)
- [x] 2.4 Real outline via `documentSymbol` plus test (PLAN 4.4)
- [ ] 2.5 Exit criteria run and green (PLAN 4.5)

## Phase 3: preview with images

- [ ] 3.1 `AsciidocLanguageServerApi`, `preview` in server and service, `serverInterface` in client `plugin.xml` (PLAN 5.1)
- [ ] 3.2 `AsciidocHtmlRenderer` block syntax (PLAN 5.2, block table)
- [ ] 3.3 `AsciidocHtmlRenderer` inline syntax, images as data URIs, includes (PLAN 5.2, inline table)
- [ ] 3.4 Preview page wiring: refresh, debounce, scroll position, link handling (PLAN 5.3)
- [ ] 3.5 Renderer and preview tests (PLAN 5.4)
- [ ] 3.6 Exit criteria run and green (PLAN 5.4)

## Phase 4: language features

- [ ] 4.1 `AsciidocDocumentModel` parses headings, anchors, attributes, macros, blocks, plus test (PLAN 6 intro)
- [ ] 4.2 Demo leftovers removed (PLAN 6.1)
- [ ] 4.3 Diagnostics plus test (PLAN 6.2)
- [ ] 4.4 Completion plus test (PLAN 6.3)
- [ ] 4.5 Document links, definition, hover plus tests (PLAN 6.4)
- [ ] 4.6 Folding plus test (PLAN 6.5)
- [ ] 4.7 Exit criteria run and green (PLAN 6.7)

## Phase 5: UI test (optional, time-boxed)

- [ ] 5.1 `com.vogella.asciidoc.editor.tests` with `AsciidocEditorTest`, or removed with a note here (PLAN 7)

## Phase 6: documentation

- [ ] 6.1 `README.md` (PLAN 8)
- [ ] 6.2 `docs/features.adoc` (PLAN 8)

## Handover notes

(Write here what a successor needs to know: partial work, failing commands, decisions taken under PLAN 0.3 last bullet.)
