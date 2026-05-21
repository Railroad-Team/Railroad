/**
 * Public abstract syntax tree (AST) API for Java source files.
 * <p>
 * This package exposes the semantic structure of parsed Java source after it has been
 * normalised into domain-level nodes such as {@code CompilationUnit},
 * {@code ClassDeclaration}, {@code MethodDeclaration}, and expression/statement nodes.
 * Use this layer when you want to reason about Java constructs directly instead of
 * working with raw parser production names from the syntax tree.
 * <p>
 * Typical usage:
 * <ol>
 *     <li>Start from {@link dev.railroadide.railroad.ide.sst.ast.program.CompilationUnit}.</li>
 *     <li>Traverse via {@link dev.railroadide.railroad.ide.sst.ast.AstVisitor} or
 *     {@link dev.railroadide.railroad.ide.sst.ast.AstNode#children()}.</li>
 *     <li>Use {@link dev.railroadide.railroad.ide.sst.ast.Span} for source locations.</li>
 * </ol>
 * <p>
 * The AST is useful for source transformations, higher-level analyses, and features that
 * care about Java concepts rather than parser trivia. Inspections that need exact token
 * identity, recovery/missing-token handling, or compatibility with the semantic model
 * typically work with the syntax and semantic APIs instead.
 */
package dev.railroadide.railroad.ide.sst.ast;
