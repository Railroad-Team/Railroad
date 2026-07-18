# SST architecture gap report

## Executive assessment

Railroad currently has a credible Java parser, semantic analyzer, inspection system, and declaration index. It is a good foundation for Java diagnostics, but it is not yet a general IDE semantic platform.

The central architectural gap is that parsing, semantic analysis, indexing, editor features, and document mutation are still connected through Java-specific or feature-specific APIs. The system can answer “what does this Java node mean in this analysis run?”, but it cannot yet reliably answer workspace-level questions such as:

- Where is the stable declaration for this reference?
- What are all usages of this exact overload?
- Which classes implement or override this symbol?
- Can this rename be applied safely across five files?
- Is this diagnostic’s quick fix still valid for the current document version?
- How should a binary language expose symbols and locations?
- How can a plugin add an entirely new language without modifying Railroad itself?

The highest-priority work is therefore not more inspections. It is establishing five shared foundations:

1. Versioned document and workspace snapshots.
2. Stable symbol identities and locations.
3. A cross-file reference and relationship index.
4. Transactional workspace edits.
5. A capability-based language/plugin architecture.

Without those, navigation, usages, implementations, reliable completion, quick fixes, and refactoring will each end up building incompatible local solutions.

## Current strengths

Several parts are already valuable and should be retained:

- The green/red concrete syntax tree is immutable, lossless, offset-aware, and supports lazy red-node construction.
- Java parsing has recovery and missing-token representation.
- There is an incremental parsing API in `JavaSyntaxParser`, although it is not integrated into the editor.
- `SemanticModel` provides declared symbols, resolved references, inferred types, and diagnostics.
- Project, dependency, and JDK symbols can be combined.
- Project declaration indexes are persistent and respond to source-file changes.
- Java inspections have a plugin-facing registration mechanism.
- The editor debounces feature requests and prevents stale results from being displayed.
- Railroad has strong Java regression coverage, including parsing its own project.

These are useful building blocks. The recommendation is to wrap and generalize them, not replace everything.

## Readiness overview

| Capability | Current state | Priority |
|---|---|---:|
| Java parsing and recovery | Strong foundation | Maintain |
| Java semantic diagnostics | Functional, heavily coupled | P1 |
| Additional text languages | Registry exists, semantic frontend contract absent | P0 |
| Binary languages | Viewer support only | P0 |
| Go to definition | No workspace-level API or precise indexed locations | P0 |
| Find usages | No reference index | P0 |
| Go to implementation | No inheritance/override index | P0 |
| Quick fixes | No code-action or edit model | P0 |
| Rename/refactoring | No stable symbol identity or workspace transaction | P0 |
| Incremental live analysis | Parser prototype only; editor performs full analysis | P1 |
| Unsaved document support | No index overlay | P0 |
| Completion | Dot completion with minimal item model | P1 |
| Signature help | Separate JDT backend | P1 |
| Hover/documentation | Absent | P2 |
| Semantic highlighting | Java uses Tree-sitter highlighting, not semantic facts | P2 |
| Formatting/import management | Absent as language capabilities | P1 |
| Outline/hierarchies | No query APIs | P1/P2 |
| Plugin-defined languages | Not loaded by the plugin manager | P0 |

## 1. Language extensibility gaps

### 1.1 `LanguageSupport` is a closed list of features

`LanguageSupport` directly defines factories for diagnostics, completion, signature help, indexing, persistence, and index-context contribution.

This works for the current feature set, but every new IDE feature would require:

- Adding another method to `LanguageSupport`.
- Extending `CodeEditorConfig`.
- Extending `CodeEditorPane`.
- Updating every base language implementation.

That does not scale to hover, navigation, references, rename, formatting, code actions, document symbols, semantic tokens, folding, inlay hints, and future capabilities.

A capability registry would be more extensible:

```text
LanguageContribution
 ├─ ParserProvider
 ├─ SemanticModelProvider
 ├─ ProjectIndexContributor
 ├─ DiagnosticProvider
 ├─ CompletionProvider
 ├─ NavigationProvider
 ├─ ReferenceProvider
 ├─ CodeActionProvider
 ├─ RenameProvider
 ├─ FormatterProvider
 └─ BinaryViewProvider
```

Languages would register only the capabilities they implement. New feature types could be introduced without modifying every language class.

### 1.2 Plugins cannot currently contribute complete languages

The plugin manager discovers `JavaInspectionRuleProvider` through `ServiceLoader`. It does not similarly discover:

- `LanguageSupport`
- Parsers
- Semantic analyzers
- Index contributors
- Navigation providers
- Binary viewers

`LanguageSupportRegistry` is public, but a plugin has no managed load/unload path for language contributions. A proper contribution system needs:

- Service discovery.
- API compatibility/version checks.
- Registration ownership.
- Deterministic priority.
- Unregistration on plugin unload.
- Disposal of caches, executors, and project sessions.
- Conflict handling when two plugins claim the same extension.
- Capability isolation and failure reporting.

### 1.3 Generic semantic contracts remain Java-shaped

The supposedly shared semantic model contains several closed Java-oriented concepts:

- `Type` is sealed and permits only its built-in variants.
- `SymbolKind` is a closed enum containing Java concepts such as class, interface, record, field, and constructor.
- `Symbol` explicitly describes Java source symbols.
- `LanguageInspectionRuleReporter` requires a `SyntaxNode`, even for a nominally generic language inspection.

A Kotlin-like, SQL, shader, assembly, configuration, or binary format language may need:

- Namespaces, properties, traits, macros, labels, sections, resources, opcodes, or exported entries.
- Function, tuple, union, intersection, nullable, dynamic, structural, or address types.
- Locations that are byte offsets, virtual addresses, archive entries, or generated source mappings.

The core should provide neutral concepts such as `SymbolId`, `SymbolKindId`, `Location`, and `SemanticType`, while allowing language-specific typed data behind capability APIs.

### 1.4 Language selection is too simple

Language matching is mainly file-extension based and takes the first registry match. A mature language resolver should support:

- Exact file names such as `Dockerfile`.
- Multiple suffixes such as `.blade.php`.
- Shebangs.
- MIME/content sniffing.
- Project configuration overrides.
- Priority and explicit conflict resolution.
- Embedded and injected languages.
- Virtual archive entries.
- Language variants and dialects.

## 2. Text and binary document model gaps

### 2.1 APIs assume `Path + String`

Diagnostics, completion, signature help, and semantic analysis accept raw strings. Project indexing ultimately calls `Files.readString`, and `ProjectLanguageIndexer.indexFile` accepts `String sourceContent`.

That prevents a shared indexing pipeline from supporting actual binary inputs. A binary parser should not need to decode arbitrary bytes into a Java `String`.

The core needs a versioned document abstraction, approximately:

```java
sealed interface DocumentSnapshot {
    DocumentId id();
    URI uri();
    long version();
    ContentType contentType();
}

record TextSnapshot(
    DocumentId id,
    URI uri,
    long version,
    String text,
    Charset encoding,
    LineMap lineMap
) implements DocumentSnapshot {}

record BinarySnapshot(
    DocumentId id,
    URI uri,
    long version,
    ByteBuffer bytes
) implements DocumentSnapshot {}
```

This should support physical, in-memory, generated, archive-entry, decompiled, and otherwise virtual documents.

### 2.2 No consistent location abstraction

Current locations are primarily integer character offsets. There is no common representation for:

- Document identity.
- Snapshot version.
- Text range.
- Byte range.
- Virtual address.
- Archive entry.
- Generated-to-original source mapping.
- Decompiled source and originating binary member.
- Related or secondary locations.

A common `Location` contract is needed before diagnostics, navigation, refactoring, and binary languages can share infrastructure.

### 2.3 Binary support is currently viewer-level

`BaseBinaryLanguageSupport` mainly marks a language as non-text and returns no diagnostics/completion/signature capabilities. Images are handled as a special viewer. Other binaries are generally opened externally.

Java dependency binaries are parsed into class stubs, which is useful, but this is separate from the language system. The class index:

- Stores the archive path as the symbol source.
- Does not preserve the archive entry for each class.
- Does not provide member bytecode locations.
- Does not associate attached source or decompiled text.
- Does not expose navigation into a class-file structure.

For true binary-language extensibility, Railroad needs:

- Byte-oriented snapshots.
- Structured binary nodes with byte/address ranges.
- Virtual-document support for archive contents.
- Binary symbol and reference extraction.
- Hex/disassembly/decompiler viewer capabilities.
- Optional mapping from binary symbols to attached or generated source.

Binary trees should not be forced into text-only `SyntaxNode` semantics. A shared structural-node concept can exist, but its location domain must be explicit.

## 3. Document lifecycle and incremental analysis gaps

### 3.1 No versioned workspace snapshot

The editor passes independent strings to each provider. There is no workspace object guaranteeing that:

- The document text corresponds to a known version.
- The project index represents the same logical state.
- Two related requests see the same dependency graph.
- Results can be rejected because their input version is obsolete.

Generation counters prevent stale results from being displayed, but they do not provide semantic snapshot consistency.

### 3.2 Unsaved files are not represented in the project index

Live diagnostics analyze editor text against a project index based on disk state. Until the file is saved and the index update is observed:

- Its project declaration entry may be stale.
- Other open files cannot see its new declarations.
- Renames or signature changes may produce inconsistent resolution.
- Find-usages results would necessarily miss unsaved changes.

The project index needs an in-memory overlay:

```text
Persisted base index
        +
Open-document overlay indexes
        =
Current workspace symbol graph
```

Saving can merge or replace the overlay, but analysis and navigation should always consult it first.

### 3.3 Incremental parsing is not integrated

`JavaSyntaxParser.parseIncremental` is only referenced by parser tests. Editor diagnostics and completion pass the full string to `JavaSemanticAnalyzer`, which reparses and reanalyzes it.

The current incremental parser also reparses from an affected top-level declaration to the end of the file. It does not yet provide fine-grained method-body or expression subtree reparsing.

A document session should own:

- Current text snapshot.
- Current syntax snapshot.
- Applied edit sequence.
- Reused green nodes.
- Declaration summary.
- Semantic caches and dependency keys.
- Diagnostics for the current version.

Incremental parsing should be validated by asserting that incremental and full parses produce equivalent trees for randomized edit sequences.

### 3.4 No cancellation or cooperative scheduling

`CodeEditorPane` creates a worker pool per editor. Obsolete work is discarded after completion, but not cancelled.

This can result in:

- Several full semantic analyses running for outdated text.
- Excessive threads when many editors are open.
- Plugin inspections occupying a worker indefinitely.
- Completion waiting behind stale diagnostics.
- No way to prioritize interactive work over background indexing.

Every expensive feature API should accept a cancellation token/deadline. Railroad should use shared project/application schedulers with priorities such as:

1. Caret-sensitive completion/signature help.
2. Visible-document parsing.
3. Visible-document diagnostics.
4. Open-document semantic refresh.
5. Background workspace indexing.

Plugin-provided work should have timeouts, cancellation checks, and failure isolation.

## 4. Syntax-tree gaps

### 4.1 `SyntaxTree` is too small as a public snapshot

`SyntaxTree` contains only the root node. It lacks:

- Document identity and version.
- Original source snapshot.
- Syntax diagnostics.
- Language ID and language version.
- Efficient `tokenAt`, `nodeAt`, and range queries.
- Shared traversal and descendant APIs.
- Line-map access.
- Node identity across incremental versions.

As a result, large helpers such as `JavaRuleContext` repeatedly implement traversal and structural queries.

### 4.2 Syntax diagnostics are disconnected

`JavaSyntaxParser.parseWithDiagnostics` can produce syntax diagnostics, but `JavaSemanticAnalyzer.analyzeFacts(String)` calls the plain `parse` path. `JavaDiagnosticsProvider` therefore runs semantic inspections without incorporating the parser’s syntax diagnostics.

Syntax errors should be first-class diagnostics from the same document snapshot, with precise recovery information and expected-token details.

### 4.3 No rewrite or syntax-generation system

The tree is immutable, which is desirable, but there is no supported way to:

- Construct new syntax.
- Replace a node or token.
- Insert before/after a node.
- Remove a construct while handling surrounding trivia.
- Normalize whitespace.
- Print a modified tree.
- Produce minimal text edits from a tree change.

`JavaSyntaxParser.TextEdit` describes input to incremental parsing; it is not a source-transformation API.

For quick fixes and refactorings, add language-specific syntax factories and rewriters that ultimately produce text edits. Tree mutation itself should remain immutable.

### 4.4 The parallel AST appears disconnected

The `sst.ast` package describes an immutable Java AST intended for transformations, but it appears largely unused outside that package. The active analyzer and inspections operate on the concrete syntax tree.

Maintaining two unrelated tree models risks:

- Duplicate node definitions.
- Conversion and source-mapping problems.
- Transformations written against a model that cannot reproduce source faithfully.
- Feature divergence.

A better direction is typed language wrappers over the concrete syntax tree, similar to:

```java
JavaClassDeclarationSyntax classDeclaration =
    JavaSyntax.cast(node, JavaClassDeclarationSyntax.class);
```

Typed wrappers retain the lossless tree and source identity without requiring a separate detached AST.

### 4.5 The legacy parser abstraction leaks Java

The generic `Parser` imports `JavaTokenType`, hard-codes Java EOF handling, and contains a TODO that trivia is discarded. The active green Java parser does not use this base.

This should either be removed as redundant or redesigned as a genuinely language-neutral parsing toolkit.

## 5. Semantic-model gaps

### 5.1 Symbols have no stable identity

A symbol currently contains:

- Kind.
- Simple name.
- Optional qualified name.
- Optional declaration syntax node.

That is insufficient for cross-file IDE operations.

For example, Java method qualified names are formed as `owner#method`, without parameter types. Overloads therefore share the same apparent identity. `SimpleSymbol` also has identity-based object equality.

Introduce a stable `SymbolId`, with language-defined canonical encoding:

```text
java:type:com.example.Service
java:method:com.example.Service#run(java.lang.String,int)
java:field:com.example.Service#executor
```

The exact format is less important than ensuring identity includes the elements required to distinguish overloads, constructors, nested types, source sets, and modules.

### 5.2 Symbols lack proper locations and metadata

A symbol should be able to expose:

- Stable symbol ID.
- Definition location.
- Name-selection location.
- Container symbol.
- Signature.
- Declared type.
- Modifiers and visibility.
- Documentation location.
- Source/binary/generated origin.
- Deprecation and annotation metadata.
- Language-specific data.

`Symbol.declaration()` returning a syntax node works only inside the originating syntax tree. It does not identify the document or support binary symbols.

### 5.3 Semantic maps are one-way and incomplete

`SemanticModel` supports `node -> symbol/type` queries but does not expose:

- All declarations.
- All references.
- References grouped by symbol.
- Candidate symbols for ambiguous/unresolved references.
- Scope at a position/node.
- Scope children.
- Conversion information.
- Chosen overload and substitutions.
- Call targets.
- Constant values.
- Flow facts.

Local find-usages could theoretically traverse every node and query each one, but cross-file usage remains impossible and symbol identity is unreliable.

### 5.4 The semantic model is not fully immutable

`SemanticModel` describes itself as immutable, but it exposes a mutable `Scope`, whose public `declare` method remains callable after model creation.

Final semantic snapshots should expose frozen scope data. Mutable builders should remain internal to analysis.

### 5.5 Type representation is too shallow

Types are often identified by display strings. The public type model lacks important concepts such as:

- Error type distinct from unknown.
- Null type and nullability.
- Intersection and union types.
- Executable/method types.
- Captured wildcards.
- Raw types.
- Substituted generic instances.
- Annotated types.
- Language-specific type constructors.
- Stable declared-type symbol identity.

There are also effectively two Java type models: the SST semantic `Type` and the class-parser `Type`. These should share a normalized semantic representation at the boundary.

### 5.6 Analysis facts are not reusable services

Control flow, definite assignment, constant evaluation, exception flow, and other analyses are implemented inside individual inspections. That leads to duplicated traversal and inconsistent results.

A production semantic layer should expose reusable services:

- Control-flow graph.
- Data-flow engine.
- Definite assignment.
- Nullability facts.
- Constant evaluation.
- Effect/side-effect classification.
- Exception flow.
- Type conversion and overload resolution.
- Accessibility and module visibility.

Inspections, quick fixes, and refactorings should query the same canonical facts.

### 5.7 Java implementation remains too concentrated

`JavaSemanticAnalyzer` is approximately 6,900 lines, while `JavaRuleContext` is approximately 3,700 lines.

This indicates that responsibilities are accumulating in two places:

- Declaration and scope construction.
- Import resolution.
- Name lookup.
- Member lookup.
- Overload resolution.
- Type inference.
- Accessibility.
- Hierarchy traversal.
- Exception facts.
- Inspection convenience functions.

The previous declaration extraction was useful, but further boundaries are needed:

```text
JavaSemanticSession
 ├─ DeclarationPass
 ├─ ScopeGraph
 ├─ ImportResolver
 ├─ NameResolver
 ├─ TypeResolver
 ├─ OverloadResolver
 ├─ HierarchyService
 ├─ AccessibilityService
 ├─ ControlFlowService
 └─ DiagnosticPass
```

`JavaRuleContext` should become a small façade over those services instead of owning another semantic implementation.

## 6. Project index and navigation gaps

### 6.1 The generic index has no query contract

`ProjectLanguageIndex` exposes only `getFileIndex(Path)`.

Everything else is Java-specific. There is no shared API for:

- Symbol lookup.
- Definitions.
- References.
- Implementations.
- Workspace symbol search.
- Relationships.
- Streaming results.
- Search scopes.

A language can keep its own storage format, but the IDE needs standard query capabilities.

### 6.2 The Java project index stores declarations, not semantic relationships

`JavaProjectSemanticIndex` stores source files, packages, imports, and declaration descriptors. Its symbol descriptor has a path, signature, static flag, and owner, but no source range.

It does not store:

- Reference occurrences.
- Read/write/call roles.
- Extends/implements relationships.
- Override relationships.
- Call edges.
- Type usages.
- Annotation usages.
- Declaration name range.
- Return and parameter types in a structured form.
- Complete modifiers and visibility.

Signature extraction explicitly still has TODOs for generics, varargs, and return types.

### 6.3 Find usages cannot currently be implemented reliably

A reference index should contain records similar to:

```java
record ReferenceOccurrence(
    SymbolId target,
    Location location,
    ReferenceKind kind,
    SymbolId enclosingSymbol
) {}
```

`ReferenceKind` should distinguish declaration, read, write, call, type use, import, inheritance, annotation, method reference, documentation reference, and other language-defined roles.

The index should support both:

- Forward references per file, for efficient replacement on reindex.
- Reverse references per symbol, for usages queries.

### 6.4 Go to implementation needs relationship indexes

Implementation navigation requires at least:

- Direct subtype edges.
- Reverse subtype edges.
- Method override/implementation edges.
- Interface default-method handling.
- Signature substitution through generics.
- Source and binary participants.
- Scope filtering.

The current source project index does not retain superclass or interface relationships, so this cannot be answered without reparsing or scanning every file.

### 6.5 Index updates will not scale indefinitely

Updating one Java file currently rebuilds a new project index and reconstructs secondary maps from all file indexes. Persistence then rewrites the single snapshot.

This is acceptable at Railroad’s current scale but will become expensive for large projects. A longer-term design should use:

- Immutable per-file shards.
- Incrementally maintained global posting lists.
- Per-file replacement transactions.
- Segmented persistence.
- Background compaction.
- Schema and extractor versioning.
- Crash-safe journals or atomic segment replacement.

### 6.6 Dependency caches can become stale

Library caches are keyed by their root paths, and JDK caches by JDK home. If a JAR is replaced at the same path, the in-memory cache does not detect its new timestamp, size, or content hash.

Cache keys should include dependency fingerprints and relevant project configuration.

### 6.7 Project modelling is incomplete

`JavaLanguageIndexContext` has roots, classpath, module path, and JDK home, which is a good beginning. It lacks:

- Java language level.
- Preview-feature flag.
- Module identity and readability graph.
- Source-set ownership.
- Main/test/custom source-set visibility.
- Per-module classpaths.
- Annotation processor paths and generated-source lifecycle.
- Multi-release JAR handling.
- Variant-specific Gradle configurations.

Aggregating all source roots into one flat project index may allow symbols to resolve across boundaries where the compiler would reject them.

## 7. Quick fixes, code actions, and source modifications

### 7.1 Diagnostics cannot carry fixes

`SemanticDiagnostic` contains severity, code, message, range, and an optional syntax node. Inspection reporters can only report messages. There is no mechanism to attach:

- A quick-fix identifier.
- Fix-specific data.
- Applicability.
- Preferred fix.
- Lazy resolution.
- Fix-all grouping.
- Related locations.

A language-neutral diagnostic should carry opaque data and optionally advertise code-action kinds.

### 7.2 No `WorkspaceEdit`

Railroad needs a central edit model before implementing quick fixes:

```java
record TextEdit(
    DocumentId document,
    long expectedVersion,
    TextRange range,
    String replacement
) {}

sealed interface ResourceOperation {
    record CreateFile(...) implements ResourceOperation {}
    record DeleteFile(...) implements ResourceOperation {}
    record RenameFile(...) implements ResourceOperation {}
}

record WorkspaceEdit(
    List<TextEdit> textEdits,
    List<ResourceOperation> resourceOperations,
    List<ChangeAnnotation> annotations
) {}
```

Application must provide:

- Version/conflict validation.
- Overlapping-edit detection.
- Atomic multi-file application.
- Preview.
- One-step undo.
- Read-only/generated-file protection.
- Save policy.
- Index and diagnostics refresh.
- Optional post-edit formatting.
- Caret/selection placement.
- Change annotations explaining each modification.

Quick fixes should return edits; they should not mutate editor controls directly.

### 7.3 Code actions need a separate protocol

A scalable API should distinguish discovery from resolution:

```text
CodeActionProvider.actions(context, range, diagnostics)
    -> lightweight CodeAction list

CodeActionProvider.resolve(action, currentSnapshot)
    -> WorkspaceEdit or Command
```

This supports cheap menus and defers expensive analysis until the user selects an action.

Relevant action kinds include:

- Quick fix.
- Refactor extract.
- Refactor inline.
- Refactor rewrite.
- Source organize imports.
- Source generate.
- Fix all.
- Suppression.

### 7.4 Refactoring requires more than edits

Rename and structural refactorings need a prepare/validate/apply pipeline:

1. Resolve the selected symbol to a stable `SymbolId`.
2. Check whether the operation is supported.
3. Query all affected declarations and references.
4. Perform language-specific conflict analysis.
5. Build an annotated workspace edit.
6. Preview and apply atomically.
7. Reindex the changed snapshot.
8. Verify postconditions.

Rename may also require:

- File renames.
- Import updates.
- Resource/configuration updates.
- Reflection/string occurrence policies.
- Override-family renaming.
- Generated or binary occurrence handling.

## 8. Completion and signature-help gaps

### 8.1 Completion is limited to dot completion

`JavaSemanticCompletionEngine` rejects requests unless the trigger character is `.`. It does not cover ordinary identifier completion, imports, keywords, constructors, overrides, type names, annotation names, named arguments in other languages, or context-specific snippets.

### 8.2 Completion items are too small

`CompletionItem` contains only insertion and display text.

A production completion item needs:

- Kind/icon.
- Primary text edit and replacement range.
- Additional edits, such as imports.
- Snippet format and tab stops.
- Detail and documentation.
- Sort and filter text.
- Commit characters.
- Deprecation state.
- Parameter information.
- Lazy resolve token.
- Optional command after insertion.
- Stable identity for selection retention.

### 8.3 Feature backends are inconsistent

Completion uses SST semantic analysis, while Java signature help still uses JDT and hard-codes JLS 21.

This means completion, diagnostics, and signature help can disagree about the same source. It also reparses the document independently.

All semantic editor features should consume one shared semantic snapshot. JDT can remain as an oracle or fallback during development, but not as an invisible second source of truth.

## 9. Diagnostics and inspection gaps

### 9.1 The editor diagnostic type is Java-specific

`EditorDiagnostic` implements `javax.tools.Diagnostic<JavaFileObject>`, which ties the editor-facing model to Java compiler concepts.

Replace it with a language-neutral diagnostic carrying URI, range, severity, code, source, tags, related information, and opaque data.

### 9.2 No syntax/semantic/inspection unification

Syntax diagnostics, semantic facts, and inspection diagnostics currently take different routes. They should all become one ordered diagnostic stream for a known document version.

### 9.3 Plugin inspections have no cancellation boundary

Inspection rules are caught if they throw, but a plugin rule can run indefinitely or consume excessive memory. Add:

- Cancellation tokens.
- Deadlines.
- Per-rule timing.
- Failure counters.
- Automatic temporary disabling after repeated failures.
- Diagnostics identifying failed providers.
- Optional background-only inspections.
- Per-language and per-rule cost categories.

### 9.4 Suppression and configuration need expansion

A complete inspection system should support:

- Source-level suppression.
- Project/file/directory scopes.
- Profiles.
- Baselines for existing issues.
- Severity inheritance.
- Fix-all grouping.
- Related locations.
- Rule documentation.
- Deterministic rule ordering.
- Language-specific configuration schemas.

## 10. Other IDE capabilities currently missing

Once the foundational APIs exist, the following can be implemented as ordinary language capabilities rather than bespoke editor code.

### Navigation and search

- Go to declaration/definition.
- Go to type definition.
- Find usages/references.
- Go to implementation.
- Type hierarchy.
- Method override hierarchy.
- Call hierarchy.
- Workspace symbol search.
- File-local symbol outline.
- Recent/navigation history and back/forward locations.

### Editing intelligence

- Rename.
- Safe delete.
- Change signature.
- Extract method/variable/constant.
- Inline variable/method.
- Introduce parameter/field.
- Move type/member.
- Generate constructors/accessors/overrides.
- Organize and optimize imports.
- Code formatting and range formatting.
- On-type formatting.
- Structural selection expansion.
- Smart enter and paired-token editing.

### Code understanding

- Hover and quick documentation.
- Parameter name hints.
- Type hints and inlay hints.
- Semantic highlighting.
- Deprecated/unused marking.
- Code lens.
- Folding ranges.
- Breadcrumbs.
- Structure view.
- Documentation/reference links.
- Dead-code and call-graph analysis.

### Cross-language and project support

- References between Java and resource/configuration files.
- Injected languages in strings/templates.
- Build-script symbols.
- Generated-source mappings.
- Annotation-processor awareness.
- Multi-module visibility.
- Dependency source attachment.
- Decompilation navigation.
- Resource keys and localization usages.

## 11. Recommended target architecture

A useful overall shape would be:

```text
Workspace / Document Store
        │
        ├── Versioned text and binary snapshots
        ├── Open-document overlays
        └── Transactional WorkspaceEdit application
        │
        ▼
Language Runtime Registry
        │
        ├── Parse capability
        ├── Semantic capability
        ├── Index extraction capability
        ├── Navigation/reference capability
        ├── Code-action/refactoring capability
        └── Presentation capabilities
        │
        ▼
Per-document Analysis Snapshot
        │
        ├── Syntax/structural tree
        ├── Semantic facts
        ├── Diagnostics
        └── Exported index facts
        │
        ▼
Workspace Symbol Graph
        │
        ├── Stable symbols and definitions
        ├── Reference occurrences
        ├── Inheritance/override relationships
        ├── Calls and type usages
        └── Source/binary/generated locations
        │
        ▼
IDE Feature Services
        ├── Completion / signature / hover
        ├── Definition / usages / implementations
        ├── Diagnostics / code actions
        ├── Refactoring
        └── Hierarchies / outline / semantic UI
```

The critical principle is that all features consume the same document and symbol snapshots.

## 12. Recommended implementation order

### Phase 1: Neutral document and edit foundations — P0

Implement:

- `DocumentId`
- `TextSnapshot` and `BinarySnapshot`
- Versioned `Location` and range types
- Shared line maps
- Cancellation tokens
- `WorkspaceEdit`
- Atomic multi-file edit application
- Open-document overlay store

Do this before quick fixes or refactoring.

### Phase 2: Stable symbol graph — P0

Introduce:

- `SymbolId`
- `SymbolLocation`
- Definition/name ranges
- Reference occurrences
- Per-file semantic index shard
- Reverse reference postings
- Inheritance and override relationships
- Query services for definition, usages, and implementations

Upgrade Java extraction first and prove it through Railroad itself.

### Phase 3: Language capability/plugin architecture — P0

Replace the expanding nullable factory interface with capability registration. Add managed plugin discovery and unload.

Create two reference plugin languages:

- A small text language with parsing, diagnostics, symbols, navigation, and one quick fix.
- A small binary format with byte parsing, structural navigation, and diagnostics.

If both can be implemented without modifying core IDE classes, the extension architecture is working.

### Phase 4: Shared incremental analysis sessions — P1

Integrate incremental parsing into editor document sessions. Cache syntax and semantic snapshots and share them among diagnostics, completion, signature help, hover, and navigation.

Add fine-grained Java reparse boundaries and semantic invalidation.

### Phase 5: Java semantic decomposition — P1

Extract the remaining analyzer components and turn important analysis facts into reusable services. Replace duplicated inspection analyses with canonical control-flow, constant-evaluation, hierarchy, and type services.

Unify SST completion and signature help.

### Phase 6: Code actions and first refactorings — P1

Start with bounded operations that validate the architecture:

- Remove unused import.
- Add missing import.
- Replace a redundant expression.
- Rename a local variable.
- Rename a project type.
- Organize imports.
- Generate an override.

Each should use the same `WorkspaceEdit` and symbol graph.

### Phase 7: Broader IDE intelligence — P2

Add hover, semantic highlighting, outlines, hierarchies, inlay hints, formatting, call hierarchy, code lens, and deeper refactorings.

## 13. Testing gaps to address alongside the architecture

Railroad has extensive Java behavior tests, but it needs platform-level contract tests:

- Text-language plugin load/unload.
- Binary-language plugin load/unload.
- Capability registration conflict handling.
- Incremental parse versus full parse equivalence under randomized edits.
- Unsaved overlay resolution across multiple open documents.
- Stable symbol IDs across whitespace-only edits.
- Reference-index replacement after file edits and deletion.
- Concurrent index updates without lost changes.
- Cancellation of stale analysis.
- Workspace-edit conflict and rollback behavior.
- Multi-file undo.
- Quick-fix version invalidation.
- Rename conflict analysis.
- Index corruption and crash recovery.
- Dependency replacement at the same path.
- Multi-module/source-set visibility.
- Golden tests ensuring transformations preserve comments and formatting.

Performance budgets should also become tests for visible-document latency, project indexing, reference queries, and edit-to-diagnostic time.

## Final recommendation

The next major milestone should be framed as:

> Railroad can load a text or binary language plugin, create versioned analysis snapshots, assign stable identities to symbols, index definitions and references, navigate between them, and apply a previewable atomic workspace edit.

That milestone would provide the foundation for nearly every proper IDE feature the current SST lacks.

The most important thing to avoid is continuing to grow `JavaSemanticAnalyzer`, `JavaRuleContext`, or `LanguageSupport` with more feature-specific helper methods. Those files already show where capability boundaries are missing. Establish the document, symbol graph, query, and edit contracts first; the visible IDE features will then become much simpler and more consistent.
