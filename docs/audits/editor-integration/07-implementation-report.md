# Shared text editor implementation

Implemented on 11 November 2026, starting from `112b5b02`. The six editor-integration audits were read before implementation. The user's approved public API and pre-release policy supersede the audits' proposed compatibility bridges.

## Public API

The toolkit-independent API is in `dev.railroadide.railroad.plugin.spi.editor`:

| Type / operation | Contract |
| --- | --- |
| `TextEditorAccess` | One open source editor's optional text capability. No JavaFX or RichTextFX types appear in its public signatures. |
| `reveal()` | Reveals source; preview-only Markdown switches to split view and acquires its source when necessary. |
| `snapshot()` | Returns the actual canonical `TextDocumentSnapshot` from `DocumentStore.current()`. Materialized sources must agree with its text and location. Closed/unreconciled targets throw `IllegalStateException`. |
| `selection()` / `TextSelection(anchor, caret)` | Directed UTF-16 selection, with a collapsed selection representing the caret. Unmaterialized source reports `(0, 0)`. |
| `selectAndReveal(expected, selection)` | Checks snapshot identity, revision, content, metadata and bounds before navigation. Unfolds the selected source location and follows the caret. |
| `isEditable()` | Reports effective editability, including the source's explicit read-only setting and known non-writable existing backing. |
| `replace(expected, replacements)` | Validates the complete batch inside `TextEditorPane`'s existing ownership boundary, then applies one isolated undoable change. |
| `TextReplacement(TextRange, String)` | Half-open UTF-16 replacement against original snapshot offsets. Input order is irrelevant. Overlap and duplicate starting offsets are rejected; adjacent ranges are allowed. |
| `EditResult` | `APPLIED`, `STALE`, `INVALID`, `READ_ONLY`, `CLOSED`, or `UNAVAILABLE`. Empty/text-identical batches succeed without history or revision changes. |
| `observe(listener)` | Initial and subsequent settled canonical snapshots, coalesced on the editor UI thread and deduplicated across unrelated changes, unchanged overlays and save promotion. |
| `observeSelection(listener)` | Initial and subsequent settled directed selections. |
| `EditorSubscription` | Idempotent disposable registration; closing cancels queued delivery. Accepted editor closure also releases owned registrations. |

Operations use the editor UI thread. Immutable snapshots can be searched on worker threads. Source features should call `reveal()` before taking the snapshot they will search: source initialization can publish a new revision, including existing RichTextFX line-ending normalization.

`EditorOpenView.textEditor()` exposes the optional capability. `DocumentEditorStateService.getActiveTextEditor()` and `observeActiveTextEditor(...)` expose managed active-tab state independently of keyboard focus. The service's old concrete `getActiveEditorPane()` API was removed, its setter now accepts the typed capability, and its cursor/selection projections use SST `LineMap`. Unsupported image views return absence. Concrete source access remains only for existing construction, save, specialized presentation and lifecycle ownership.

## Significant implementation changes

- Added a stable adapter to `TextEditorPane`; canonical reads never manufacture a published revision with `captureSnapshot()`.
- Checked selection and replacement share the existing private `saveLock`. They recheck closure and canonical state after acquiring ownership. Replacement rejects stale identities/revisions/metadata, invalid or overlapping ranges, and read-only targets before mutation.
- Builds one contiguous replacement from the batch's original offsets and applies it through RichTextFX. `preventMerge()` brackets the operation, preserving one undo/redo step independently of surrounding typing. Ordinary overlay publication and save/conflict handling remain in place.
- An external backing revision newer than the visible overlay does not invalidate that unchanged overlay. Replacement advances above the store's latest revision while retaining the unresolved conflict and existing overlay baseline; it does not save or overwrite external content.
- Document and selection subscriptions coalesce onto the UI thread, filter unchanged state, and cancel queued callbacks on disposal. Pane observation and accepted background closure share ownership so a closed pane cannot receive a new callback.
- Replaced Markdown-specific unwrapping in `EditorOpenView` with explicitly supplied source observation, text capability and wrapper lifecycle hooks. View-owned source listeners are removed on accepted close/reset.
- Markdown preview follows published store content, including retained drafts, source edits, clean external reloads and dirty conflict overlays. Initial disk content only seeds an absent store entry with an expected-state check; a newer existing publication wins.
- Clean project-covered previews remain lazy. Recovered drafts acquire a hidden source so existing tab dirty/save/conflict and project-close behavior protects them. Files outside project watcher coverage use the same hidden source's existing watcher, including after relocation out of project coverage. No second watcher implementation or document model was added.
- Markdown source acquisition does not deliver feature callbacks during construction. Wrapper closure removes store, source and theme listeners and releases preview resources; vetoed closure keeps observation usable.
- Caret status now consumes the public active-text API, follows managed groups, and tears down subscriptions on target changes and scene detachment.
- Removed the deferred initial caret reset that could overwrite a newly selected match. Java breakpoint cleanup now waits for accepted source closure. Corrected the legacy modification-event range's line/column argument order without using that unversioned event as the new API's revision authority.

## Demonstration and regression coverage

`src/test/java/dev/railroadide/railroad/plugin/spi/editor/FindReplaceConsumer.java` is a test-only feature consumer. Its imports are limited to the public editor API, SST snapshots/ranges, the state service and JDK collections. It discovers and observes the active target, searches a snapshot, selects a match and submits replace-all without concrete editor or RichTextFX imports.

The conformance suite exercises Java, JSON, plain text, Markdown source, split and preview-first. Fixture code uses concrete controls only to construct actual editors and inspect existing undo/save ownership. Coverage includes:

- Canonical snapshot identity, stale versions, changed metadata, UTF-16 surrogate pairs, CRLF input and the live buffer's normalized offsets.
- Complete batch validation, zero-mutation rejections, no-op history preservation, original-offset batches, isolated undo/redo, read-only navigation and rejected mutation.
- Settled reload notifications, hidden backing changes, save promotion, subscription disposal and queued callback cancellation.
- Dirty external overlays, retained preview drafts, hidden-source save/project close, vetoed closure, forced reset, external/ignored-path watching and rename handoff.
- Actual managed tab changes, unsupported images, close/reopen with stable document identity and fresh capabilities, plus caret scene detach/reattach.
- Deterministic barriers forcing checked replacement to wait behind an in-flight save or accepted background close. The operation revalidates ownership and preserves the newer overlay or rejects closure accordingly.

Independent review was performed by a separate agent for mutation correctness, concurrency, lifecycle and unnecessary abstractions. Its concrete findings were addressed: queued observation versus background close, recovered-preview draft persistence, synchronous lazy-source callbacks, Java close-veto cleanup, real-tab retargeting and watcher ownership after relocation. A standalone conflict test was isolated from the project synchronizer because two uncoordinated publishers do not represent the supported editor ownership model.

## Implementation commits

| Commit | Change |
| --- | --- |
| `ddb86b123b716ac0f8181de91843698caf040f4a` | Public text operations, checked mutation/observation, isolated undo, caret initialization and close-veto regressions. |
| `b6f6423d9e4a1d4cfe024dc7bbbbc18c3557864c` | Active-text service/consumer migration, view lifecycle, canonical Markdown previews, draft protection and watcher handoff with regression tests. |
| `821532bc7a7f4660c4215679d9831ca725b25e29` | Public-API-only find/replace fixture and cross-editor/lifecycle conformance tests. |

These commits are local. The pre-existing untracked audits and investigation material were not included or modified.

## Tests and results

Validation used the configured JDK 25 toolchain and existing 4 GiB application test worker. The final source revision is `821532bc7a7f4660c4215679d9831ca725b25e29`.

Required formatting was run after the final source/test edits:

```powershell
.\gradlew.bat format formatCheck --console=plain
```

Result: **PASS**. The combined final verification additionally checked formatting across all three implementation commits, using the starting revision as the ratchet base:

```powershell
.\gradlew.bat formatCheck -PformatRatchetFrom=112b5b02 check --continue --console=plain
```

| Final verification | Result |
| --- | --- |
| Semantic formatting and Spotless across the implementation range | PASS |
| Application tests | **1,208 run; 1,207 pass; one assertion failure; zero errors/skips** |
| CSS validation tests | **5 pass** |
| Javadoc tooling tests | **12 pass** |
| JavaFX stylesheet validation | **67 stylesheets pass** |

The sole failing test is `RailroadProjectDiagnosticsRegressionTest.currentRailroadSourcesHaveNoErrorDiagnostics()`. It reports **108 ERROR diagnostics**, with the ledger at `build/sst-self-analysis/diagnostics.json`. This is the existing failing self-analysis gate, but its current ledger includes findings on the newly added adapter code; **this report does not claim an unchanged diagnostic baseline**. Java compilation and runtime regressions pass. No analyzer changes, suppressions, exclusions or diagnostic workaround refactors were made; investigating SST diagnostics is outside the requested scope. Consequently the overall `check` command exits unsuccessfully, and this is not a claim of a wholly green repository.

All **182 focused editor/document tests pass in the final full run**:

| Suite | Passing tests |
| --- | ---: |
| `DefaultDocumentEditorStateServiceTest` | 5 |
| `ProjectDocumentSynchronizerTest` | 2 |
| `DefaultDocumentStoreTest` | 92 |
| `TextEditorAccessConformanceTest` | 19 |
| `TextEditorPaneSnapshotTest` | 7 |
| `EditorTabManagerDocumentStoreTest` | 57 |

Focused tests were also run during implementation. The public consumer's final isolated conformance run passed all 19 tests. Development failures identified incorrect CRLF-preservation expectations, an assertion before queued tab activation, a Windows path-alias mismatch in a synchronizer fixture, and competing publishers in an unmanaged standalone fixture. These were corrected without weakening the mutation, lifecycle or save assertions. The final full run covers those corrections and the last rename/watcher regression.

Local evidence:

- `build/editor-integration-format-final.log`
- `build/editor-integration-conformance-retest.log`
- `build/editor-integration-lifecycle-final-recheck.log`
- `build/editor-integration-preview-move.log`
- `build/editor-integration-full-check.log` (full verification completed in 5m 40s)
- `build/test-results/test/`, `build/test-results/cssValidationTest/`, and `build/test-results/javadocCoverageTest/`

There are no unresolved architectural decisions within this integration boundary. The outstanding validation concern is the explicitly reported self-analysis failure. The shared-consumer stop gate is demonstrated, and implementation stops here.

## Scope and limitations

- This establishes the single-document integration foundation for issue #128. It does not implement find/replace UI, regex options, all-match decorations, project-wide replacement, completion refactoring, W2 scheduling or a workspace-edit engine.
- RichTextFX retains its existing CRLF-to-LF normalization. Coordinates belong to the returned source snapshot, not raw file bytes. A raw preview snapshot can become stale when source is first revealed; callers reveal and recapture before searching.
- The contract assumes existing single-buffer ownership. Arbitrary plugin writes directly into an open document's store remain outside that ownership contract. No multi-document transaction or filesystem compare-and-swap guarantee is introduced.
- Effective filesystem writability is advisory; missing/unreadable backing and save failures continue through existing conflict/error behavior. Immutable SST origin metadata does not grant edit permission.
- No compatibility bridge, second mutable document model, undo manager, universal capability registry or scheduling framework was introduced. No GitHub issue, PR or remote branch was modified.
