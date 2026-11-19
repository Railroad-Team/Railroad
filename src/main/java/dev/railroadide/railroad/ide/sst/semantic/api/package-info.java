/**
 * Public semantic model API layered on top of the syntax tree.
 * <p>
 * The semantic model answers questions that the syntax tree alone cannot:
 * declared symbols, resolved references, inferred types, scopes, and diagnostics produced
 * by semantic analysis.
 * <p>
 * Language-neutral inspections access this package through their analysis result:
 *
 * <pre>{@code
 * model.resolvedSymbol(node)
 * model.declaredSymbol(node)
 * model.inferredType(node)
 * }</pre>
 * <p>
 * Use this package when an inspection needs name resolution, assignability, visibility,
 * or type-based reasoning. For purely structural checks, the syntax tree is usually
 * cheaper and simpler.
 * <p>
 * Models copy associations and diagnostics and permanently freeze the entire scope
 * hierarchy at build time. Construction is thread-confined; published models, frozen
 * scopes, immutable symbols, and types support concurrent reads. See the
 * {@link dev.railroadide.railroad.ide.sst SST publication policy} for lifetime and caching.
 */
package dev.railroadide.railroad.ide.sst.semantic.api;
