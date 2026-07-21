# SST implementation TODO roadmap

This roadmap translates the findings in `sst-architecture-gap-report.md` into an ordered implementation backlog.

The ordering is intentional. P0 work establishes contracts required by navigation, usages, quick fixes, refactoring, and external language plugins. Later feature work should not introduce private alternatives to these shared foundations.

## Priority definitions

| Priority | Meaning |
|---|---|
| P0 | Architectural prerequisite. Blocks one or more core IDE capabilities. |
| P1 | Required for a robust, responsive, production-quality language platform. |
| P2 | Major IDE capability built on the P0/P1 foundations. |
| P3 | Advanced capability or longer-term scalability work. |

## Critical execution order

```text
Architecture decisions
    ↓
Document snapshots and locations
    ↓
Workspace/document store and cancellation
    ↓
Workspace edits and language capabilities
    ↓
Stable symbols and workspace symbol graph
    ↓
Definitions, usages, and implementations
    ↓
Code actions and refactoring
    ↓
Incremental semantic sessions and broader IDE features
```

## General rules for all work

- [ ] **SST-GEN-001** Keep core APIs language-neutral; place Java-specific contracts under a Java extension API.
- [ ] **SST-GEN-002** Do not add new feature-specific methods to `LanguageSupport`; add capability types instead.
- [ ] **SST-GEN-003** Do not add more semantic algorithms to `JavaRuleContext`; expose reusable semantic services.
- [ ] **SST-GEN-004** Do not let quick fixes or refactorings mutate editor controls directly; return workspace edits.
- [ ] **SST-GEN-005** Require every asynchronous result to identify the input document/workspace version.
- [ ] **SST-GEN-006** Require cancellation support for parsing, semantic analysis, indexing, inspections, and queries.
- [ ] **SST-GEN-007** Keep source and binary symbols addressable through the same query layer without forcing binary content into text APIs.
- [ ] **SST-GEN-008** Add tests and performance budgets as part of each capability rather than as later cleanup.
- [ ] **SST-GEN-009** Version every persisted index format and include the extractor/configuration version in cache validity.
- [ ] **SST-GEN-010** Preserve existing zero-error Railroad self-analysis throughout the migration.

# P0 — Platform foundations

## P0.1 Architecture decisions and package boundaries

- [ ] **SST-P0-001** Write an architecture decision record defining document snapshots, semantic snapshots, project indexes, and workspace ownership.
- [ ] **SST-P0-002** Decide whether the public core API follows immutable snapshots with short-lived builders; document the thread-safety contract.
- [ ] **SST-P0-003** Define which APIs are stable plugin SPI, internal implementation, and language-specific extension API.
- [ ] **SST-P0-004** Define compatibility/versioning rules for plugin-facing SST APIs.
- [ ] **SST-P0-005** Define position units for text documents, including UTF-16 offsets, code points, bytes, lines, and columns.
- [ ] **SST-P0-006** Define binary location semantics: byte offsets, byte ranges, virtual addresses, and archive entries.
- [ ] **SST-P0-007** Define source-origin categories: source, generated, binary, decompiled, archive entry, in-memory, and external.
- [ ] **SST-P0-008** Decide how language-specific data is attached to neutral symbols, types, diagnostics, and locations.
- [ ] **SST-P0-009** Decide whether the detached `sst.ast` model will be removed, generated as typed CST wrappers, or maintained with a formal lossless mapping.
- [ ] **SST-P0-010** Identify legacy parser/lexer abstractions that are unused by the green-tree parser and plan their removal or generalization.

### Completion criteria

- The ownership and lifecycle of every document, syntax tree, semantic model, and index snapshot are explicit.
- Plugin SPI packages do not expose internal mutable implementation types.
- Text and binary requirements are represented in the same architectural decisions.

## P0.2 Document identity and immutable snapshots

- [x] **SST-P0-011** Add a stable `DocumentId` independent of filesystem path spelling. See [SST document identity contract](sst-document-identity.md).
- [x] **SST-P0-012** Add a `DocumentUri`/URI-based identity capable of representing physical and virtual documents. See [SST document identity contract](sst-document-identity.md).
- [x] **SST-P0-013** Add a monotonic `DocumentVersion` type. See [SST document identity contract](sst-document-identity.md).
- [x] **SST-P0-014** Add a sealed `DocumentSnapshot` root contract. See [SST document identity contract](sst-document-identity.md#snapshot-rules).
- [x] **SST-P0-015** Add `TextDocumentSnapshot` with immutable text, encoding, version, and language identity. See [SST document identity contract](sst-document-identity.md#snapshot-rules).
- [x] **SST-P0-016** Add `BinaryDocumentSnapshot` with immutable/read-only bytes, version, and language identity. See [SST document identity contract](sst-document-identity.md#snapshot-rules).
- [ ] **SST-P0-017** Add a shared `ContentType`/content-kind model.
- [ ] **SST-P0-018** Add a cached immutable `LineMap` for text offset/line/column conversion.
- [ ] **SST-P0-019** Support virtual documents such as archive entries and decompiled source.
- [ ] **SST-P0-020** Support generated documents with mappings back to one or more original locations.
- [ ] **SST-P0-021** Define snapshot equality and identity rules.
- [ ] **SST-P0-022** Ensure snapshots can safely be shared across analysis worker threads.
- [ ] **SST-P0-023** Replace feature-provider `String document` parameters with snapshot-based inputs.
- [ ] **SST-P0-024** Replace direct feature-provider `Path` assumptions with document identity and optional physical-path access.
- [ ] **SST-P0-025** Add compatibility adapters so existing Java providers can migrate incrementally.

### Tests

- [ ] **SST-P0-026** Test stable document identity across normalized and equivalent paths.
- [ ] **SST-P0-027** Test version progression for edits, reloads, saves, and external changes.
- [ ] **SST-P0-028** Test line-map conversion for CRLF, LF, Unicode surrogate pairs, and empty final lines.
- [ ] **SST-P0-029** Test read-only binary snapshot behavior.
- [ ] **SST-P0-030** Test virtual archive-entry and generated-document identities.

## P0.3 Unified ranges and locations

- [ ] **SST-P0-031** Add validated half-open `TextRange`.
- [ ] **SST-P0-032** Add validated half-open `ByteRange`.
- [ ] **SST-P0-033** Add optional binary `AddressRange` for formats with virtual addresses.
- [ ] **SST-P0-034** Add a language-neutral `Location` containing document identity, snapshot version, and range.
- [ ] **SST-P0-035** Add `RelatedLocation` with a human-readable relationship/message.
- [ ] **SST-P0-036** Add source-map support for generated and decompiled locations.
- [ ] **SST-P0-037** Add a location resolver that can open physical, virtual, archive, binary, and generated targets.
- [ ] **SST-P0-038** Replace raw diagnostic offsets with locations or snapshot-bound ranges.
- [ ] **SST-P0-039** Add conversion adapters for existing `SyntaxNode.start/end` ranges.
- [ ] **SST-P0-040** Add validation preventing a location from being used against the wrong snapshot version.

## P0.4 Workspace and open-document store

- [ ] **SST-P0-041** Add a `WorkspaceSnapshot` representing project configuration and document versions used by a query.
- [ ] **SST-P0-042** Add a central `DocumentStore` for closed and open documents.
- [ ] **SST-P0-043** Move editor text ownership behind the document store rather than passing independent strings to providers.
- [ ] **SST-P0-044** Add open-document overlays over disk-backed documents.
- [ ] **SST-P0-045** Add overlay events for open, edit, save, close, external reload, rename, and deletion.
- [ ] **SST-P0-046** Define conflict behavior when a disk file changes while an unsaved overlay exists.
- [ ] **SST-P0-047** Allow feature requests to capture a consistent workspace/document snapshot.
- [ ] **SST-P0-048** Expose project/module/configuration identity through workspace snapshots.
- [ ] **SST-P0-049** Ensure other open files resolve declarations from unsaved overlays.
- [ ] **SST-P0-050** Remove overlays cleanly when documents close or projects unload.

### Tests

- [ ] **SST-P0-051** Test that an unsaved declaration becomes visible to another open file.
- [ ] **SST-P0-052** Test that closing an unsaved document removes its overlay facts.
- [ ] **SST-P0-053** Test external-edit conflicts and non-conflicting reloads.
- [ ] **SST-P0-054** Test project unload releases document and analysis snapshots.

## P0.5 Cancellation, scheduling, and request context

- [ ] **SST-P0-055** Add a lightweight `CancellationToken` and source.
- [ ] **SST-P0-056** Add a common `FeatureRequestContext` containing workspace snapshot, document snapshot, cancellation, and deadline.
- [ ] **SST-P0-057** Add cancellation checks to Java parsing loops.
- [ ] **SST-P0-058** Add cancellation checks between Java semantic passes and during expensive graph searches.
- [ ] **SST-P0-059** Add cancellation checks to project and binary indexing.
- [ ] **SST-P0-060** Add cancellation checks between inspection providers and rules.
- [ ] **SST-P0-061** Replace per-editor worker pools with shared application/project schedulers.
- [ ] **SST-P0-062** Define priorities for completion, signature help, visible diagnostics, open-document analysis, and background indexing.
- [ ] **SST-P0-063** Cancel superseded feature requests instead of only discarding their results.
- [ ] **SST-P0-064** Add plugin task deadlines and failure isolation.
- [ ] **SST-P0-065** Record per-provider timing and cancellation metrics.

## P0.6 Transactional workspace edits

- [ ] **SST-P0-066** Add snapshot-bound `TextEdit` with expected document version.
- [ ] **SST-P0-067** Add binary patch/edit representation or explicitly mark binary documents read-only per language.
- [ ] **SST-P0-068** Add create-file resource operations.
- [ ] **SST-P0-069** Add delete-file resource operations.
- [ ] **SST-P0-070** Add rename/move-file resource operations.
- [ ] **SST-P0-071** Add `WorkspaceEdit` containing ordered document and resource changes.
- [ ] **SST-P0-072** Add change annotations describing why each edit is required.
- [ ] **SST-P0-073** Validate document versions before applying edits.
- [ ] **SST-P0-074** Detect overlapping and contradictory text edits.
- [ ] **SST-P0-075** Normalize edit ordering and apply edits without offset drift.
- [ ] **SST-P0-076** Support atomic multi-document application.
- [ ] **SST-P0-077** Roll back all changes if any edit or resource operation fails.
- [ ] **SST-P0-078** Integrate workspace edits with one-step undo/redo.
- [ ] **SST-P0-079** Add an edit preview model suitable for a diff UI.
- [ ] **SST-P0-080** Enforce read-only, generated, external, and permission constraints.
- [ ] **SST-P0-081** Define save behavior after workspace edits.
- [ ] **SST-P0-082** Trigger overlay, index, diagnostics, and UI refreshes after successful edits.
- [ ] **SST-P0-083** Support requested caret and selection placement after an edit.
- [ ] **SST-P0-084** Add optional post-edit formatting/import processing hooks.

### Tests

- [ ] **SST-P0-085** Test multiple non-overlapping edits in one document.
- [ ] **SST-P0-086** Test atomic edits across several documents.
- [ ] **SST-P0-087** Test overlapping-edit rejection.
- [ ] **SST-P0-088** Test stale-version rejection.
- [ ] **SST-P0-089** Test rollback after filesystem failure.
- [ ] **SST-P0-090** Test one-step undo across text and resource operations.

## P0.7 Capability-based language runtime

- [ ] **SST-P0-091** Add a `LanguageId` value type with namespaced IDs.
- [ ] **SST-P0-092** Add `LanguageContribution` as the plugin-owned language registration unit.
- [ ] **SST-P0-093** Add typed capability keys and a capability registry.
- [ ] **SST-P0-094** Add parser/structure capability.
- [ ] **SST-P0-095** Add semantic-analysis capability.
- [ ] **SST-P0-096** Add index-extraction and index-query capabilities.
- [ ] **SST-P0-097** Add diagnostics capability.
- [ ] **SST-P0-098** Add completion and signature-help capabilities.
- [ ] **SST-P0-099** Add definition, reference, implementation, and workspace-symbol capabilities.
- [ ] **SST-P0-100** Add code-action, rename, and refactoring capabilities.
- [ ] **SST-P0-101** Add formatting, semantic-highlighting, folding, hover, inlay-hint, and outline capability types.
- [ ] **SST-P0-102** Add binary structure/view capability types.
- [ ] **SST-P0-103** Add language-contribution priority and conflict resolution.
- [ ] **SST-P0-104** Support exact names, extensions, compound suffixes, shebangs, and content sniffing.
- [ ] **SST-P0-105** Define dialect/variant support without creating unrelated language IDs for every project setting.
- [ ] **SST-P0-106** Add per-project language sessions with explicit disposal.
- [ ] **SST-P0-107** Add per-document language sessions where stateful incrementality is required.
- [ ] **SST-P0-108** Add managed `ServiceLoader` discovery for language contributions.
- [ ] **SST-P0-109** Track registrations by owning plugin.
- [ ] **SST-P0-110** Unregister capabilities and dispose sessions on plugin unload.
- [ ] **SST-P0-111** Add API-version compatibility checks and useful load failures.
- [ ] **SST-P0-112** Migrate Java, JSON, Markdown, plain text, and image support to contributions.
- [ ] **SST-P0-113** Deprecate nullable factory methods on `LanguageSupport` after migration.
- [ ] **SST-P0-114** Remove the old closed feature-factory path once all built-ins migrate.

### Reference implementations and tests

- [ ] **SST-P0-115** Implement a tiny sample text-language plugin with parsing, diagnostics, symbols, navigation, and one quick fix.
- [ ] **SST-P0-116** Implement a tiny sample binary-format plugin with structure parsing, symbols, navigation, and diagnostics.
- [ ] **SST-P0-117** Verify both sample languages require no changes to core editor classes.
- [ ] **SST-P0-118** Test dynamic load/unload and cache/session cleanup.
- [ ] **SST-P0-119** Test two plugins claiming the same file and deterministic conflict resolution.
- [ ] **SST-P0-120** Publish a plugin-author language contribution guide.

## P0.8 Language-neutral diagnostics and code actions

- [ ] **SST-P0-121** Replace `EditorDiagnostic`'s `javax.tools` dependency with a neutral diagnostic model.
- [ ] **SST-P0-122** Include document location, severity, code, source, message, tags, related information, and opaque data.
- [ ] **SST-P0-123** Bind diagnostic results to a document version.
- [ ] **SST-P0-124** Unify syntax, semantic, build, and inspection diagnostics at the editor boundary.
- [ ] **SST-P0-125** Preserve diagnostic origin so duplicate reports can be explained and filtered.
- [ ] **SST-P0-126** Define code-action kinds and metadata.
- [ ] **SST-P0-127** Add `CodeActionProvider.actions(...)` for lightweight discovery.
- [ ] **SST-P0-128** Add lazy `CodeActionProvider.resolve(...)` for expensive edit construction.
- [ ] **SST-P0-129** Allow diagnostics to advertise associated action IDs/data without eagerly computing edits.
- [ ] **SST-P0-130** Add preferred-action and fix-all grouping support.
- [ ] **SST-P0-131** Reject actions whose source diagnostic/document version is stale unless they can be safely recomputed.
- [ ] **SST-P0-132** Add a generic command result for actions that do not directly edit documents.
- [ ] **SST-P0-133** Add code-action cancellation and latency budgets.

## P0.9 Stable symbol identities and locations

- [ ] **SST-P0-134** Add a neutral `SymbolId` value type.
- [ ] **SST-P0-135** Define canonical Java IDs for packages, modules, types, fields, constructors, methods, parameters, locals, and type parameters.
- [ ] **SST-P0-136** Ensure Java method/constructor IDs distinguish overloads by normalized signatures.
- [ ] **SST-P0-137** Define symbol IDs for source, binary, generated, and decompiled declarations.
- [ ] **SST-P0-138** Add `SymbolKindId` so plugins can define new symbol categories.
- [ ] **SST-P0-139** Add a neutral immutable symbol descriptor.
- [ ] **SST-P0-140** Store both full declaration range and name-selection range.
- [ ] **SST-P0-141** Store owning/container symbol ID.
- [ ] **SST-P0-142** Store origin and navigability metadata.
- [ ] **SST-P0-143** Store structured signature and declared type references.
- [ ] **SST-P0-144** Store modifiers, visibility, deprecation, and annotations in extensible metadata.
- [ ] **SST-P0-145** Add source/binary alternate locations and attached-source locations.
- [ ] **SST-P0-146** Replace cross-file reliance on `SimpleSymbol` object identity.
- [ ] **SST-P0-147** Keep syntax-node attachments as per-snapshot conveniences, not symbol identity.
- [ ] **SST-P0-148** Add symbol-ID stability tests across whitespace and comment edits.
- [ ] **SST-P0-149** Add uniqueness tests for nested types, overloads, constructors, and anonymous/local declarations.

## P0.10 Workspace symbol graph and reference index

- [ ] **SST-P0-150** Define an immutable per-file semantic index shard.
- [ ] **SST-P0-151** Store declared symbol descriptors per shard.
- [ ] **SST-P0-152** Store forward reference occurrences per shard.
- [ ] **SST-P0-153** Define reference kinds: declaration, read, write, call, type use, import, inheritance, annotation, method reference, documentation, and language-defined kinds.
- [ ] **SST-P0-154** Store enclosing symbol ID for every reference where known.
- [ ] **SST-P0-155** Add reverse symbol-to-reference postings.
- [ ] **SST-P0-156** Add direct subtype edges.
- [ ] **SST-P0-157** Add reverse subtype edges.
- [ ] **SST-P0-158** Add method override/implementation edges.
- [ ] **SST-P0-159** Add call edges where resolution is sufficiently certain.
- [ ] **SST-P0-160** Add unresolved-reference records where useful for later re-resolution.
- [ ] **SST-P0-161** Replace one file shard transactionally without rebuilding unrelated file facts.
- [ ] **SST-P0-162** Remove all forward and reverse facts when a file is deleted.
- [ ] **SST-P0-163** Layer open-document shards over persisted disk shards.
- [ ] **SST-P0-164** Define query consistency against a captured workspace snapshot.
- [ ] **SST-P0-165** Support project, module, source-set, dependency, and custom search scopes.
- [ ] **SST-P0-166** Support cancellable/streaming queries for large result sets.
- [ ] **SST-P0-167** Add exact, prefix, fuzzy, and qualified workspace-symbol search.
- [ ] **SST-P0-168** Add schema/extractor/configuration versions to persisted shards.
- [ ] **SST-P0-169** Add atomic persistence and recovery from corrupt/incomplete shards.
- [ ] **SST-P0-170** Add optional background compaction for segmented storage.

### Java extraction work

- [ ] **SST-P0-171** Add Java declaration/name ranges to indexed symbols.
- [ ] **SST-P0-172** Replace string-only method signatures with normalized structured signatures.
- [ ] **SST-P0-173** Include return type, type parameters, generics, arrays, and varargs.
- [ ] **SST-P0-174** Include complete modifiers and visibility.
- [ ] **SST-P0-175** Extract superclass, interface, permits, and module relationships.
- [ ] **SST-P0-176** Extract reference occurrences from resolved semantic facts.
- [ ] **SST-P0-177** Extract read/write roles for fields, locals, and parameters.
- [ ] **SST-P0-178** Extract call targets and constructor calls.
- [ ] **SST-P0-179** Extract method override relationships with generic substitution.
- [ ] **SST-P0-180** Include annotation and documentation references where resolvable.
- [ ] **SST-P0-181** Add binary Java symbols to the same symbol graph.
- [ ] **SST-P0-182** Preserve JAR/JMOD entry identity rather than only the archive path.
- [ ] **SST-P0-183** Link attached source or decompiled locations to binary Java symbols.

### Tests

- [ ] **SST-P0-184** Test file-shard replacement removes obsolete reverse references.
- [ ] **SST-P0-185** Test deletion removes symbols, references, subtype edges, and overrides.
- [ ] **SST-P0-186** Test overload-specific usages.
- [ ] **SST-P0-187** Test source-to-binary and binary-to-source hierarchy relationships.
- [ ] **SST-P0-188** Test unsaved overlays replace disk reference results.
- [ ] **SST-P0-189** Test concurrent updates do not lose unrelated file changes.
- [ ] **SST-P0-190** Test persistence recovery after interrupted writes.

## P0.11 Navigation query services

- [ ] **SST-P0-191** Add a symbol-at-position query using the current semantic snapshot.
- [ ] **SST-P0-192** Add go-to-declaration/definition queries.
- [ ] **SST-P0-193** Add go-to-type-definition queries.
- [ ] **SST-P0-194** Add find-references/usages queries.
- [ ] **SST-P0-195** Add go-to-implementation queries for types and methods.
- [ ] **SST-P0-196** Add result ranking for source, attached source, decompiled source, and binary targets.
- [ ] **SST-P0-197** Add ambiguity results rather than silently choosing an arbitrary target.
- [ ] **SST-P0-198** Add partial-result streaming for large usage searches.
- [ ] **SST-P0-199** Add cancellation and progress reporting.
- [ ] **SST-P0-200** Add navigation history entries based on stable locations.
- [ ] **SST-P0-201** Add a virtual-document opening path for archive and decompiled targets.
- [ ] **SST-P0-202** Add API-level tests before building the final navigation UI.

## P0.12 Binary-language foundation

- [ ] **SST-P0-203** Define a binary structural-node API with kind, location, parent, and children.
- [ ] **SST-P0-204** Allow binary languages to expose symbols and references through the workspace graph.
- [ ] **SST-P0-205** Add a virtual-address mapping abstraction for executable/object formats.
- [ ] **SST-P0-206** Add archive-container and archive-entry document support.
- [ ] **SST-P0-207** Add a reusable hex-view capability with selection mapped to byte ranges.
- [ ] **SST-P0-208** Add a structured binary outline/tree view capability.
- [ ] **SST-P0-209** Allow binary diagnostics to target byte/address ranges.
- [ ] **SST-P0-210** Allow read-only binary navigation and hover independently of editing support.
- [ ] **SST-P0-211** Define optional binary patch support and explicit safety constraints.
- [ ] **SST-P0-212** Integrate Java class files/JAR entries as the first substantial binary-language consumer.

# P1 — Robust analysis and first end-user features

## P1.1 Shared incremental analysis sessions

- [ ] **SST-P1-001** Add a per-document analysis session owning text, syntax, and semantic snapshots.
- [ ] **SST-P1-002** Feed editor change batches into incremental parsing.
- [ ] **SST-P1-003** Extend Java incremental parsing below top-level declaration tails.
- [ ] **SST-P1-004** Identify safe reparse boundaries for members, blocks, statements, expressions, and imports.
- [ ] **SST-P1-005** Preserve reusable green nodes outside invalidated regions.
- [ ] **SST-P1-006** Incrementally refresh declaration summaries.
- [ ] **SST-P1-007** Define semantic dependency keys for scopes, imports, declarations, and body facts.
- [ ] **SST-P1-008** Invalidate only semantic facts affected by an edit where practical.
- [ ] **SST-P1-009** Share one semantic snapshot between diagnostics, completion, signature help, hover, and navigation.
- [ ] **SST-P1-010** Publish overlay index shards from the current analysis snapshot.
- [ ] **SST-P1-011** Fall back safely to a full parse/analysis after unsupported edits or internal failures.
- [ ] **SST-P1-012** Bound retained historical snapshots to avoid editor-session memory leaks.
- [ ] **SST-P1-013** Add randomized edit-sequence equivalence tests against full parsing.
- [ ] **SST-P1-014** Add edit-to-diagnostics latency benchmarks.

## P1.2 Syntax API and transformations

- [ ] **SST-P1-015** Attach document identity/version and language ID to syntax snapshots.
- [ ] **SST-P1-016** Include syntax diagnostics in the syntax snapshot.
- [ ] **SST-P1-017** Add efficient `tokenAt`, `nodeAt`, covering-node, and intersecting-node queries.
- [ ] **SST-P1-018** Add shared ancestor, descendant, sibling, and token traversal helpers.
- [ ] **SST-P1-019** Add a language-neutral trivia/token classification hook.
- [ ] **SST-P1-020** Add typed Java CST wrappers generated or maintained from syntax kinds.
- [ ] **SST-P1-021** Add Java syntax factories for identifiers, names, types, declarations, statements, expressions, and imports.
- [ ] **SST-P1-022** Add immutable replace/insert/remove rewriter operations.
- [ ] **SST-P1-023** Add trivia-preservation policies.
- [ ] **SST-P1-024** Add a syntax-diff-to-minimal-text-edits engine.
- [ ] **SST-P1-025** Add formatting hooks for newly generated syntax.
- [ ] **SST-P1-026** Resolve the detached `sst.ast` decision and remove duplicate/dead APIs.
- [ ] **SST-P1-027** Remove or generalize the legacy parser base that hard-codes Java EOF.

## P1.3 Semantic model and reusable analysis services

- [ ] **SST-P1-028** Make completed semantic snapshots deeply immutable.
- [ ] **SST-P1-029** Replace mutable public `Scope` exposure with immutable scope snapshots.
- [ ] **SST-P1-030** Add scope-at-node and scope-at-position queries.
- [ ] **SST-P1-031** Expose declarations and references as enumerable semantic facts.
- [ ] **SST-P1-032** Expose ambiguous candidates and unresolved-reason data.
- [ ] **SST-P1-033** Record selected overload and generic substitutions.
- [ ] **SST-P1-034** Add structured conversion results.
- [ ] **SST-P1-035** Add distinct error, unknown, null, executable, intersection, union, raw, captured, and annotated type representations where needed.
- [ ] **SST-P1-036** Associate declared semantic types with stable type symbol IDs.
- [ ] **SST-P1-037** Normalize class-parser binary types into the main semantic type system.
- [ ] **SST-P1-038** Add reusable constant-evaluation service.
- [ ] **SST-P1-039** Add reusable control-flow graph service.
- [ ] **SST-P1-040** Add reusable data-flow framework.
- [ ] **SST-P1-041** Add definite-assignment facts.
- [ ] **SST-P1-042** Add exception-flow facts.
- [ ] **SST-P1-043** Add side-effect/effect classification.
- [ ] **SST-P1-044** Add hierarchy and override services.
- [ ] **SST-P1-045** Add accessibility and module-visibility service.
- [ ] **SST-P1-046** Migrate inspections from private duplicate analyses to shared services.

## P1.4 Java semantic analyzer decomposition

- [ ] **SST-P1-047** Introduce `JavaSemanticSession` as the orchestration boundary.
- [ ] **SST-P1-048** Keep declaration collection in a dedicated pass.
- [ ] **SST-P1-049** Extract immutable scope graph construction.
- [ ] **SST-P1-050** Extract import parsing/classification and import resolution.
- [ ] **SST-P1-051** Extract type-name resolution.
- [ ] **SST-P1-052** Extract value/name resolution.
- [ ] **SST-P1-053** Extract member lookup and inheritance traversal.
- [ ] **SST-P1-054** Extract overload-resolution engine.
- [ ] **SST-P1-055** Extract type inference and conversion logic.
- [ ] **SST-P1-056** Extract generic substitution/capture logic.
- [ ] **SST-P1-057** Extract accessibility checks.
- [ ] **SST-P1-058** Separate semantic diagnostics from fact production.
- [ ] **SST-P1-059** Replace static global JDK lookup fallbacks with project/session dependencies.
- [ ] **SST-P1-060** Shrink `JavaRuleContext` to a stable inspection façade.
- [ ] **SST-P1-061** Move syntax helpers to typed syntax utilities.
- [ ] **SST-P1-062** Move semantic helpers to semantic services.
- [ ] **SST-P1-063** Keep old entry points as adapters during migration.
- [ ] **SST-P1-064** Preserve Railroad self-analysis and semantic regression results after every extracted pass.

## P1.5 Project and build-model correctness

- [ ] **SST-P1-065** Add Java language level to project analysis context.
- [ ] **SST-P1-066** Add preview-feature state.
- [ ] **SST-P1-067** Add module identity and module-readability graph.
- [ ] **SST-P1-068** Model Gradle/Maven source sets separately.
- [ ] **SST-P1-069** Preserve main/test/custom source-set visibility.
- [ ] **SST-P1-070** Preserve per-module classpaths and dependency scopes.
- [ ] **SST-P1-071** Model annotation processor paths and generated-source outputs.
- [ ] **SST-P1-072** Mark generated files and define edit policies.
- [ ] **SST-P1-073** Handle multi-release JAR selection for the configured runtime/language level.
- [ ] **SST-P1-074** Fingerprint dependency roots by path, size, timestamp, and optionally content hash.
- [ ] **SST-P1-075** Invalidate library indexes when a JAR changes at the same path.
- [ ] **SST-P1-076** Invalidate JDK indexes when the selected runtime changes.
- [ ] **SST-P1-077** Add explicit cache clearing on project/model refresh.
- [ ] **SST-P1-078** Add per-key synchronization/single-flight project index building.
- [ ] **SST-P1-079** Prevent lost updates from concurrent file-index changes.
- [ ] **SST-P1-080** Move persistence off latency-sensitive watcher/editor threads.

## P1.6 Navigation UI

- [ ] **SST-P1-081** Add go-to-definition editor command and mouse gesture.
- [ ] **SST-P1-082** Add target chooser for ambiguous definitions.
- [ ] **SST-P1-083** Add find-usages results pane with grouping and incremental results.
- [ ] **SST-P1-084** Add go-to-implementation command and chooser.
- [ ] **SST-P1-085** Add navigation back/forward history.
- [ ] **SST-P1-086** Open and position virtual/decompiled/binary targets.
- [ ] **SST-P1-087** Show stale/cancelled query states correctly.
- [ ] **SST-P1-088** Keep results linked to symbol IDs so files can be refreshed safely.

## P1.7 Completion and signature help

- [ ] **SST-P1-089** Replace raw completion inputs with snapshot/request contexts.
- [ ] **SST-P1-090** Expand `CompletionItem` with kind, detail, documentation, sort/filter text, and stable identity.
- [ ] **SST-P1-091** Add primary replacement text edit and explicit range.
- [ ] **SST-P1-092** Add additional edits for imports and related changes.
- [ ] **SST-P1-093** Add snippet/tab-stop support.
- [ ] **SST-P1-094** Add commit characters and post-insert commands.
- [ ] **SST-P1-095** Add lazy completion-item resolution.
- [ ] **SST-P1-096** Support identifier, type, keyword, import, constructor, annotation, and override completion.
- [ ] **SST-P1-097** Support explicit invocation independently of trigger characters.
- [ ] **SST-P1-098** Add context-aware ranking and filtering.
- [ ] **SST-P1-099** Use the shared semantic snapshot rather than reparsing for completion.
- [ ] **SST-P1-100** Implement SST-backed Java signature help.
- [ ] **SST-P1-101** Remove the hard-coded JLS 21 JDT signature path after parity is reached.
- [ ] **SST-P1-102** Add cancellation and latency tests for completion/signature help.

## P1.8 First quick fixes and refactorings

- [ ] **SST-P1-103** Implement remove-unused-import as a code action.
- [ ] **SST-P1-104** Implement add-missing-import with ambiguity handling.
- [ ] **SST-P1-105** Implement replace-redundant-expression fixes for selected existing inspections.
- [ ] **SST-P1-106** Implement source-level inspection suppression action.
- [ ] **SST-P1-107** Implement fix-all for one safely composable inspection family.
- [ ] **SST-P1-108** Add prepare-rename API.
- [ ] **SST-P1-109** Implement local-variable/parameter rename using semantic references.
- [ ] **SST-P1-110** Implement field/method rename with overload-aware identity.
- [ ] **SST-P1-111** Implement type rename including file rename where required.
- [ ] **SST-P1-112** Include override families when language rules require them.
- [ ] **SST-P1-113** Add rename conflict detection for scopes, imports, overloads, and visibility.
- [ ] **SST-P1-114** Add refactoring preview and cancellation.
- [ ] **SST-P1-115** Verify one-step undo for every refactoring.
- [ ] **SST-P1-116** Add golden tests proving comments and unrelated formatting are preserved.

## P1.9 Formatting and imports

- [ ] **SST-P1-117** Define document, range, and on-type formatting capabilities.
- [ ] **SST-P1-118** Define formatting options independent of editor controls.
- [ ] **SST-P1-119** Implement Java formatting or integrate a formatter behind the capability.
- [ ] **SST-P1-120** Implement deterministic Java import insertion.
- [ ] **SST-P1-121** Implement organize/optimize imports.
- [ ] **SST-P1-122** Preserve comments associated with imports.
- [ ] **SST-P1-123** Integrate formatting with generated syntax and workspace edits.
- [ ] **SST-P1-124** Add format-on-save and format-selection policies.

## P1.10 Reliability, performance, and observability

- [ ] **SST-P1-125** Establish latency budgets for parsing, open-file semantics, diagnostics, completion, and navigation.
- [ ] **SST-P1-126** Establish memory budgets per open document and project index.
- [ ] **SST-P1-127** Add structured timing for each semantic pass and inspection rule.
- [ ] **SST-P1-128** Add index build/load/update/query metrics.
- [ ] **SST-P1-129** Add cancellation and superseded-work metrics.
- [ ] **SST-P1-130** Add slow-plugin/provider diagnostics.
- [ ] **SST-P1-131** Add bounded caches with explicit eviction and disposal.
- [ ] **SST-P1-132** Add stress tests with many open editors.
- [ ] **SST-P1-133** Add large-project index update and usage-query benchmarks.
- [ ] **SST-P1-134** Add fuzzing for parsers and workspace edit validation.
- [ ] **SST-P1-135** Continue JFR/profile regression checks for project-wide analysis.

# P2 — Full IDE intelligence

## P2.1 Hover and documentation

- [ ] **SST-P2-001** Add hover capability and common result model.
- [ ] **SST-P2-002** Show symbol signature, type, owner, modifiers, and documentation.
- [ ] **SST-P2-003** Resolve JavaDoc and attached dependency documentation.
- [ ] **SST-P2-004** Support binary/decompiled symbol hover.
- [ ] **SST-P2-005** Add related links and navigation targets.
- [ ] **SST-P2-006** Cache hover results by semantic snapshot and symbol ID.

## P2.2 Document structure and workspace symbols

- [ ] **SST-P2-007** Add document-symbol/outline capability.
- [ ] **SST-P2-008** Display nested symbols with stable navigation locations.
- [ ] **SST-P2-009** Add workspace-symbol search UI.
- [ ] **SST-P2-010** Add fuzzy ranking, filters, and result kinds.
- [ ] **SST-P2-011** Include source, dependency, generated, and binary symbols.
- [ ] **SST-P2-012** Add breadcrumbs driven by syntax and semantic containers.

## P2.3 Hierarchies and call graphs

- [ ] **SST-P2-013** Add type hierarchy API and UI.
- [ ] **SST-P2-014** Add method override/implementation hierarchy.
- [ ] **SST-P2-015** Add incoming-call hierarchy.
- [ ] **SST-P2-016** Add outgoing-call hierarchy.
- [ ] **SST-P2-017** Represent uncertain/dynamic call targets explicitly.
- [ ] **SST-P2-018** Stream and cancel hierarchy expansion.

## P2.4 Semantic presentation

- [ ] **SST-P2-019** Add language-neutral semantic token/highlighting capability.
- [ ] **SST-P2-020** Implement Java semantic highlighting from shared semantic facts.
- [ ] **SST-P2-021** Merge semantic styles with lexical/Tree-sitter styles deterministically.
- [ ] **SST-P2-022** Add deprecated, unresolved, unused, mutable, static, and declaration modifiers.
- [ ] **SST-P2-023** Add folding-range capability.
- [ ] **SST-P2-024** Add selection-range/structural expansion capability.
- [ ] **SST-P2-025** Add inlay-hint capability.
- [ ] **SST-P2-026** Add Java parameter-name and inferred-type hints.
- [ ] **SST-P2-027** Add code-lens capability for usages, implementations, tests, and run targets.

## P2.5 Advanced Java refactorings and generators

- [ ] **SST-P2-028** Implement safe delete.
- [ ] **SST-P2-029** Implement change signature.
- [ ] **SST-P2-030** Implement extract local variable.
- [ ] **SST-P2-031** Implement extract constant/field.
- [ ] **SST-P2-032** Implement extract method.
- [ ] **SST-P2-033** Implement inline local variable.
- [ ] **SST-P2-034** Implement inline method where safe.
- [ ] **SST-P2-035** Implement introduce parameter/field.
- [ ] **SST-P2-036** Implement move type/member.
- [ ] **SST-P2-037** Generate constructors.
- [ ] **SST-P2-038** Generate accessors.
- [ ] **SST-P2-039** Generate overrides/implementations.
- [ ] **SST-P2-040** Generate `equals`, `hashCode`, and `toString` using structured edits.
- [ ] **SST-P2-041** Add conflict analysis and golden tests for every refactoring.

## P2.6 Reusable inspections and data-flow consumers

- [ ] **SST-P2-042** Migrate constant-condition inspections to the shared constant/data-flow engine.
- [ ] **SST-P2-043** Migrate definite-assignment inspections to shared facts.
- [ ] **SST-P2-044** Migrate unreachable-code inspections to the canonical CFG.
- [ ] **SST-P2-045** Migrate exception inspections to shared exception-flow facts.
- [ ] **SST-P2-046** Add nullability analysis and inspections.
- [ ] **SST-P2-047** Add unused declaration/reference analysis from the workspace graph.
- [ ] **SST-P2-048** Add dead-code analysis across source files.
- [ ] **SST-P2-049** Ensure inspections expose applicable code actions.

## P2.7 Cross-language and injected-language support

- [ ] **SST-P2-050** Define cross-language symbol/reference contributions.
- [ ] **SST-P2-051** Support references from configuration/resource files to Java symbols.
- [ ] **SST-P2-052** Support Java references to resource keys and generated assets.
- [ ] **SST-P2-053** Define injected-language regions within text documents.
- [ ] **SST-P2-054** Map injected ranges back to host-document locations and edits.
- [ ] **SST-P2-055** Route completion, diagnostics, hover, and navigation to the active injected language.
- [ ] **SST-P2-056** Add generated-source origin navigation.
- [ ] **SST-P2-057** Add build-script and project-model symbol contributions.

## P2.8 Binary and decompiled development experience

- [ ] **SST-P2-058** Add Java class-file structure view.
- [ ] **SST-P2-059** Add bytecode/disassembly view with symbol navigation.
- [ ] **SST-P2-060** Add source attachment discovery for dependencies.
- [ ] **SST-P2-061** Add decompiler capability and virtual decompiled documents.
- [ ] **SST-P2-062** Map decompiled declarations to binary symbol IDs.
- [ ] **SST-P2-063** Navigate from source usages to attached source, decompiled source, or bytecode according to availability.
- [ ] **SST-P2-064** Add binary find-usages results for source references to dependency symbols.
- [ ] **SST-P2-065** Add safe binary patch preview for languages that explicitly support editing.

## P2.9 Inspection platform maturity

- [ ] **SST-P2-066** Add project/file/directory inspection profiles.
- [ ] **SST-P2-067** Add source-level suppression API per language.
- [ ] **SST-P2-068** Add baselines for accepted existing diagnostics.
- [ ] **SST-P2-069** Add rule documentation and settings-schema metadata.
- [ ] **SST-P2-070** Add deterministic provider/rule ordering.
- [ ] **SST-P2-071** Add repeated-failure tracking and temporary provider disabling.
- [ ] **SST-P2-072** Add foreground/background inspection cost categories.
- [ ] **SST-P2-073** Add related locations and richer diagnostic tags.
- [ ] **SST-P2-074** Add project-wide fix-all with conflict detection.

# P3 — Advanced scale and ecosystem work

## P3.1 Index scalability

- [ ] **SST-P3-001** Move from a monolithic project snapshot to segmented/sharded persistence where benchmarks justify it.
- [ ] **SST-P3-002** Add background compaction and stale-segment cleanup.
- [ ] **SST-P3-003** Add memory-mapped or database-backed postings only if heap/query benchmarks require it.
- [ ] **SST-P3-004** Add partial/lazy project loading for very large workspaces.
- [ ] **SST-P3-005** Add remote or shared cache support with content-addressed validation.
- [ ] **SST-P3-006** Add index migration tooling between compatible schema versions.

## P3.2 Protocol and ecosystem integration

- [ ] **SST-P3-007** Consider an LSP adapter over Railroad capabilities without making LSP the internal model.
- [ ] **SST-P3-008** Add external language-server contributions for languages without native Railroad frontends.
- [ ] **SST-P3-009** Map LSP edits, diagnostics, symbols, and code actions into the same internal snapshot/edit contracts.
- [ ] **SST-P3-010** Publish an SST/language plugin SDK and example repository.
- [ ] **SST-P3-011** Add compatibility test kits for third-party language plugins.
- [ ] **SST-P3-012** Add plugin API deprecation and migration documentation.

## P3.3 Advanced analyses

- [ ] **SST-P3-013** Add interprocedural data-flow where needed.
- [ ] **SST-P3-014** Add configurable call-graph precision.
- [ ] **SST-P3-015** Add taint/security analysis extension points.
- [ ] **SST-P3-016** Add framework-specific semantic contributors without hard-coding frameworks into the Java analyzer.
- [ ] **SST-P3-017** Add language-aware structural search and replace.
- [ ] **SST-P3-018** Add batch refactoring APIs suitable for project migrations.

# Cross-cutting validation checklist

Apply this checklist to every major phase:

- [ ] Public contracts have API documentation and nullability annotations.
- [ ] Mutable builders are not exposed through completed snapshots.
- [ ] Thread-safety and lifecycle behavior are documented.
- [ ] Cancellation is tested, not merely accepted as a parameter.
- [ ] Results identify their input document/workspace versions.
- [ ] Open-document overlays are included in queries.
- [ ] Source, generated, binary, and virtual locations behave consistently.
- [ ] Plugin load/unload leaves no registrations, threads, or caches behind.
- [ ] Persisted data includes schema and extractor/configuration versions.
- [ ] Corrupt persisted data falls back safely without losing source files.
- [ ] Multi-file edits are previewable, atomic, and undoable.
- [ ] Java behavior remains covered by focused tests.
- [ ] Railroad self-analysis remains at zero errors.
- [ ] Full tests pass.
- [ ] Performance stays within the phase’s declared budgets.

# Suggested first milestone

The first milestone should be complete when all of the following are true:

- [ ] A text-language plugin can be loaded and unloaded dynamically.
- [ ] A binary-language plugin can be loaded and unloaded dynamically.
- [ ] Both receive versioned document snapshots.
- [ ] Both can publish stable symbols and locations.
- [ ] Java publishes stable declaration and reference identities.
- [ ] Railroad can navigate to a Java definition.
- [ ] Railroad can find usages of an overload-specific Java method.
- [ ] Railroad can find source and binary implementations.
- [ ] A diagnostic can expose a lazily resolved quick fix.
- [ ] The quick fix applies through an atomic, previewable, undoable workspace edit.
- [ ] Unsaved files participate in analysis and navigation through overlays.
- [ ] Superseded work is cancelled.
- [ ] All platform contract tests and the existing Java suite pass.

Completing this milestone establishes the backbone required for the remaining IDE capabilities without creating feature-specific architectural dead ends.
