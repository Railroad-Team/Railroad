/**
 * Public concrete syntax tree API.
 * <p>
 * This layer exposes the parsed source exactly as a tree of {@link
 * dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode} and {@link
 * dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken} instances. Kind identifiers are
 * stable strings such as {@code JAVA_CLASS_DECLARATION} and {@code JAVA_IDENTIFIER}. Nodes
 * preserve token boundaries, offsets, parent/child links, and parser recovery details.
 * <p>
 * Prefer this package when writing inspections. It gives you:
 * <ul>
 *     <li>stable source offsets for diagnostics</li>
 *     <li>direct access to parser kinds and tokens</li>
 *     <li>a tree shape that matches the semantic model</li>
 * </ul>
 * <p>
 * A common inspection flow is:
 * <pre>{@code
 * SyntaxTree tree = context.syntaxTree();
 * SyntaxNode root = tree.root();
 * context.traverse(node -> {
 *     if ("JAVA_IMPORT_DECLARATION".equals(node.kind().id())) {
 *         // inspect and report
 *     }
 * });
 * }</pre>
 */
package dev.railroadide.railroad.ide.sst.syntax.api;
