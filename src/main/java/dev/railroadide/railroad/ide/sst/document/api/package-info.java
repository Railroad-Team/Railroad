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
 * <p>
 * Identity does not imply location or snapshot equality. A rename retains the same ID
 * while changing its URI, an edit advances the version while retaining the ID, and a
 * copy receives a different ID. Text/binary snapshot and source-origin contracts are
 * intentionally separate platform concepts.
 * <p>
 * Legacy syntax construction and parser entry points without owner-supplied metadata
 * remain source compatible by allocating a fresh anonymous identity, in-memory URI, and
 * initial version. Callers that correlate work across parses must use the explicit
 * metadata overloads.
 */
package dev.railroadide.railroad.ide.sst.document.api;
