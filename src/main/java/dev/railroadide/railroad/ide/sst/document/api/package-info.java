/**
 * Language-neutral document identity contracts for the SST platform.
 * <p>
 * {@link dev.railroadide.railroad.ide.sst.document.api.DocumentId} identifies a logical
 * document independently of its path, URI, content, language, or revision. A workspace
 * or document service owns allocation and association; syntax, semantic, indexing, and
 * feature results borrow that identity and must not reinterpret its value.
 * {@link dev.railroadide.railroad.ide.sst.document.api.DocumentUri} is the immutable
 * physical or virtual address currently associated with that document.
 * {@link dev.railroadide.railroad.ide.sst.document.api.DocumentVersion} identifies the
 * immutable content revision observed by a snapshot or analysis result.
 * {@link dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot} is the sealed
 * root for immutable text and binary revisions. Text inputs are copied into strings;
 * binary inputs are copied and exposed only through defensive copies or read-only
 * buffers.
 * <p>
 * Identity does not imply location or snapshot equality. A rename retains the same ID
 * while changing its URI, an edit advances the version while retaining the ID, and a
 * copy receives a different ID. Snapshots use reference equality; hasSameRevision compares
 * ID and version without implying equal metadata or complete state. Retained snapshots
 * remain readable after close/removal and need no disposal. See the
 * {@link dev.railroadide.railroad.ide.sst SST publication policy} for lifetime, cache
 * ownership, safe sharing, and currentness rules.
 * <p>
 * {@link dev.railroadide.railroad.ide.sst.document.api.LineMap} is the canonical API
 * for text offset/line/column conversion. Offsets and columns count zero-based UTF-16
 * code units; line indices are also zero-based. Line content ends exclude LF, CR,
 * and CRLF terminators, while positions inside terminators belong to the preceding
 * line and round-trip exactly. Obtain a text snapshot's immutable, thread-safe,
 * lazily cached map through
 * {@link dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot#lineMap()}.
 * The cache belongs to that snapshot and is independent of other revisions.
 * <p>
 * Legacy syntax construction and parser entry points without owner-supplied metadata
 * remain source compatible by allocating a fresh anonymous identity, in-memory URI, and
 * initial version. Callers that correlate work across parses must use the explicit
 * metadata overloads.
 */
package dev.railroadide.railroad.ide.sst.document.api;
