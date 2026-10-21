/**
 * Syntax/semantic tree (SST) snapshot publication policy, established by issue #149.
 *
 * <h2>Construction and publication</h2>
 * Completed document snapshots, syntax trees/nodes, semantic models/scopes/symbols/types,
 * diagnostics, and source project indexes are immutable published results. Mutable
 * lexers, parsers, analysis contexts, and builders are confined to one construction
 * thread; callers must not mutate inputs while construction copies them. Publication
 * begins when a completed result is returned or handed to a consumer. No subsequent
 * mutation may change its observable state, including nested collections and objects.
 * <p>
 * Document constructors copy text/bytes; origin and semantic type records copy lists.
 * Green syntax nodes copy children. Semantic model builders copy association maps and
 * diagnostics, preserving syntax-node identity keys. Scope construction uses a single
 * authoritative hierarchy: building a semantic model permanently freezes the connected
 * hierarchy, including ancestors and descendants. Retained scope references reject
 * declarations and new children. A fresh analysis requiring new declarations creates
 * fresh scopes. Model builders may be reused for associations/diagnostics; earlier models
 * remain unchanged. Scope queries preserve overload order and local-to-parent lookup;
 * declaration maps preserve first-name insertion order. SimpleSymbol, Java's internal
 * synthetic symbols, and sealed semantic Type implementations are immutable.
 * Provider-defined Symbol and SyntaxNode implementations must satisfy the same contract;
 * the core cannot clone arbitrary implementations or make a mutable provider object safe
 * by copying its container.
 *
 * <h2>Equality and revision identity</h2>
 * DocumentId is logical identity. The pair (DocumentId, DocumentVersion) is content
 * revision identity; DocumentSnapshot.hasSameRevision compares that pair explicitly.
 * DocumentSnapshot, SyntaxTree, SemanticModel, and Scope retain reference equality and
 * identity hashing. Two captures at the same revision need not have equal complete state:
 * URI-only changes preserve the version, and origin/language/encoding metadata may differ.
 * There is no complete-snapshot value comparison or content hash. Value records such as
 * IDs, versions, origins, types, and index descriptors keep their existing value equality.
 * Syntax-node and symbol associations refer to the exact objects in their analysis tree;
 * nodes from another parse cannot be substituted merely because their ranges match.
 *
 * <h2>Concurrent reads and caches</h2>
 * Completed compliant results support concurrent reads without external locks. Use a
 * happens-before handoff, for example executor submission, Future completion, a volatile
 * field, or a concurrent collection. Final fields protect constructed state; freezing
 * construction objects is not a replacement for safe publication. Do not share builders
 * or freeze scopes concurrently with construction or reads.
 * <p>
 * Red syntax wrappers are observably immutable: their lazy children are an immutable
 * list, initialised under a lock and published through a volatile field. All readers see
 * the same wrapper identities and parent relationships. Other memoization must similarly
 * compute only from immutable inputs, publish immutable values safely, and preserve
 * observable results. Snapshot-owned caches die with their owner and should have a bound
 * derived from that snapshot's data (as with one child list per syntax node). External
 * caches need explicit service/session ownership, size/version bounds, and release on
 * replacement or close. Revision-only keys are insufficient for metadata-sensitive work;
 * use the exact snapshot or include the relevant metadata/context generation. Never use
 * a global strong map that retains every historical snapshot or unbounded request key.
 *
 * <h2>Lifetime and currentness</h2>
 * A newer revision does not mutate or invalidate an older document, tree, or model.
 * Existing results remain readable after a document changes, closes, or leaves the
 * workspace. Operations retain their input snapshots until completion or cancellation;
 * cancellation affects usefulness, not readability. Snapshots own in-memory data and
 * require no close/dispose or reference counting. Release unused references and let
 * garbage collection reclaim unreachable graphs, including parent/child cycles.
 * <p>
 * Validity is separate from currentness. The future DocumentStore, workspace snapshot,
 * and analysis scheduler subsystems must capture a coherent request context and check it
 * at the actual editor publication boundary. Match document ID/version and the active
 * snapshot/request generation, including metadata, workspace/index dependencies, and
 * closed-document state as appropriate. An old completion must never overwrite newer
 * editor state, even when a rename leaves the content version unchanged. Cancellation
 * alone is insufficient; publication must reject superseded requests. Owners retain
 * current results plus explicitly bounded pending work/history, release replaced results,
 * and clear owned caches when sessions close. Workspace snapshots must compose immutable
 * document, syntax, semantic, and index results for a coherent captured workspace state;
 * no workspace snapshot or central snapshot manager is implemented here.
 *
 * <h2>Existing indexes and follow-up boundaries</h2>
 * JavaProjectSemanticIndex copies builder maps and nested lists; its SourceFileIndex,
 * ImportDescriptor, and SymbolDescriptor values are immutable and concurrently readable.
 * It currently carries paths, not document revision/workspace generation tokens. Adding
 * those tokens and atomic asynchronous index publication belongs to the indexing and
 * workspace/scheduling subsystems, not this policy change.
 * <p>
 * JavaStubSymbolIndex (including library/JDK indexes) and CompositeJavaSymbolIndex copy
 * their containers but expose ClassStub graphs whose record collections and nested
 * classparser types are not deeply immutable. They must not be described as compliant
 * immutable snapshots. Migrating those graphs or publishing immutable query projections
 * belongs to the classparser/library-index subsystem; until then callers must not mutate
 * them and shared use requires ownership discipline. JavaAnalysisContextProvider's
 * per-service library/JDK maps can retain many distinct roots and need lifecycle/size
 * bounds in that subsystem. ProjectLanguageIndexService owns current indexes by project
 * and language, but its lifecycle/publication rules still need to meet the policy above.
 * <p>
 * CodeEditorPane rejects superseded diagnostic generations on the UI thread, but allocates
 * fresh IDs per request and relies on worker shutdown at close without an explicit
 * closed-editor publication check. Stable owner identity and complete close/metadata/
 * workspace invalidation belong to the DocumentStore/editor scheduling migration;
 * this change does not claim that migration is complete.
 * <p>
 * JavaSemanticAnalyzer's process-wide JDK stub cache uses lock/volatile publication and
 * holds one runtime's data, rather than historical snapshots, but its public stub graph
 * has the same classparser immutability limitation. Its per-analysis resolution/type
 * caches stay in the thread-confined analysis context and do not escape into models.
 * SyntaxKind's concurrent interning table retains kind IDs, not snapshots; providers
 * should use a finite vocabulary of stable kinds rather than per-document IDs.
 * The legacy Java AST is a separate parser representation, not a published SST snapshot;
 * mutable collections there are a legacy-AST migration, not an immutable syntax-tree
 * guarantee. These limitations are explicit follow-up work, not claims of completed
 * indexing or workspace infrastructure.
 *
 * <h2>Verification</h2>
 * Regression tests exercise publication through retained root/nested scope references,
 * builder reuse, collection isolation, node identity maps, revision comparisons, and
 * historical readability. Coordinated worker tests read shared results and race cold
 * red-node child initialisation using latches and futures, without timing-based sleeps.
 */
package dev.railroadide.railroad.ide.sst;
