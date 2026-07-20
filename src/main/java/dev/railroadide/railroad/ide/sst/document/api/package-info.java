/**
 * Language-neutral document identity contracts for the SST platform.
 * <p>
 * {@link dev.railroadide.railroad.ide.sst.document.api.DocumentId} identifies a logical
 * document independently of its path, URI, content, language, or revision. A workspace
 * or document service owns allocation and association; syntax, semantic, indexing, and
 * feature results borrow that identity and must not reinterpret its value.
 * <p>
 * Identity does not imply location or snapshot equality. A rename retains the same ID,
 * an edit produces a later snapshot with the same ID, and a copy receives a different ID.
 * URI, version, text/binary snapshot, and source-origin contracts are intentionally
 * separate platform concepts.
 * <p>
 * Legacy syntax construction and parser entry points without an owner-supplied ID remain
 * source compatible by allocating a fresh anonymous identity. Callers that correlate
 * work across parses must use the explicit-ID overloads.
 */
package dev.railroadide.railroad.ide.sst.document.api;
