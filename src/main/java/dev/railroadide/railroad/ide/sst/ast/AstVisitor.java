package dev.railroadide.railroad.ide.sst.ast;

import dev.railroadide.railroad.ide.sst.ast.annotation.ElementValueArray;
import dev.railroadide.railroad.ide.sst.ast.annotation.MarkerAnnotation;
import dev.railroadide.railroad.ide.sst.ast.annotation.NormalAnnotation;
import dev.railroadide.railroad.ide.sst.ast.annotation.SingleMemberAnnotation;
import dev.railroadide.railroad.ide.sst.ast.clazz.*;
import dev.railroadide.railroad.ide.sst.ast.expression.*;
import dev.railroadide.railroad.ide.sst.ast.generic.*;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.ReceiverParameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.program.CompilationUnit;
import dev.railroadide.railroad.ide.sst.ast.program.ImportDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.PackageDeclaration;
import dev.railroadide.railroad.ide.sst.ast.program.j9.*;
import dev.railroadide.railroad.ide.sst.ast.statements.*;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.statements.block.InstanceInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.statements.block.StaticInitializerBlock;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.CaseItem;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchLabel;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchRule;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.*;

/**
 * Typed visitor over the Java AST.
 * <p>
 * Implement this interface when you want exhaustive, type-safe dispatch over AST node
 * kinds. Each {@link AstNode} implementation routes {@link AstNode#accept(AstVisitor)} to
 * the matching visit method.
 * <p>
 * Typical usage:
 *
 * <pre>
 * AstVisitor&lt;Void&gt; visitor = new AstVisitor&lt;&gt;() {
 *     &#64;Override
 *     public Void visitMethodDeclaration(MethodDeclaration node) {
 *         // inspect node
 *         return null;
 *     }
 *
 *     &#64;Override
 *     public Void visitCompilationUnit(CompilationUnit node) {
 *         for (AstNode child : node.children()) {
 *             child.accept(this);
 *         }
 *         return null;
 *     }
 *
 *     // implement remaining methods
 * };
 * </pre>
 *
 * @param <R> result type returned by each visit method
 */
public interface AstVisitor<R> {
    /**
     * Visits a {@link CompilationUnit} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCompilationUnit(CompilationUnit node);
    /**
     * Visits a {@link PackageDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitPackageDeclaration(PackageDeclaration node);
    /**
     * Visits a {@link ImportDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitImportDeclaration(ImportDeclaration node);

    /**
     * Visits a {@link ModularCompilationUnit} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitModularCompilationUnit(ModularCompilationUnit node);
    /**
     * Visits a {@link RequiresDirective} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitRequiresDirective(RequiresDirective node);
    /**
     * Visits a {@link ExportsDirective} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitExportsDirective(ExportsDirective node);
    /**
     * Visits a {@link OpensDirective} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitOpensDirective(OpensDirective node);
    /**
     * Visits a {@link UsesDirective} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitUsesDirective(UsesDirective node);
    /**
     * Visits a {@link ProvidesDirective} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitProvidesDirective(ProvidesDirective node);

    /**
     * Visits a {@link ClassDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitClassDeclaration(ClassDeclaration node);
    /**
     * Visits a {@link EnumDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitEnumDeclaration(EnumDeclaration node);
    /**
     * Visits a {@link RecordDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitRecordDeclaration(RecordDeclaration node);
    /**
     * Visits a {@link RecordComponent} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitRecordComponent(RecordComponent node);
    /**
     * Visits a {@link InterfaceDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitInterfaceDeclaration(InterfaceDeclaration node);
    /**
     * Visits a {@link AnnotationTypeDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAnnotationTypeDeclaration(AnnotationTypeDeclaration node);
    /**
     * Visits a {@link AnnotationElement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAnnotationElement(AnnotationElement node);
    /**
     * Visits a {@link EmptyTypeDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitEmptyTypeDeclaration(EmptyTypeDeclaration node);
    /**
     * Visits a {@link AnonymousClassDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAnonymousClassDeclaration(AnonymousClassDeclaration node);

    /**
     * Visits a {@link FieldDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitFieldDeclaration(FieldDeclaration node);
    /**
     * Visits a {@link MethodDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitMethodDeclaration(MethodDeclaration node);
    /**
     * Visits a {@link ConstructorDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitConstructorDeclaration(ConstructorDeclaration node);
    /**
     * Visits a {@link CompactConstructorDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCompactConstructorDeclaration(CompactConstructorDeclaration node);
    /**
     * Visits a {@link EnumConstantDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitEnumConstantDeclaration(EnumConstantDeclaration node);
    /**
     * Visits a {@link AnnotationTypeMemberDeclaration} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAnnotationTypeMemberDeclaration(AnnotationTypeMemberDeclaration node);

    /**
     * Visits a {@link StaticInitializerBlock} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitStaticInitializerBlock(StaticInitializerBlock node);
    /**
     * Visits a {@link InstanceInitializerBlock} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitInstanceInitializerBlock(InstanceInitializerBlock node);

    /**
     * Visits a {@link BlockStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBlockStatement(BlockStatement node);
    /**
     * Visits a {@link EmptyStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitEmptyStatement(EmptyStatement node);
    /**
     * Visits a {@link LabeledStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitLabeledStatement(LabeledStatement node);
    /**
     * Visits a {@link ExpressionStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitExpressionStatement(ExpressionStatement node);
    /**
     * Visits a {@link LocalVariableDeclarationStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitLocalVariableDeclarationStatement(LocalVariableDeclarationStatement node);
    /**
     * Visits a {@link IfStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitIfStatement(IfStatement node);
    /**
     * Visits a {@link SwitchStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSwitchStatement(SwitchStatement node);
    /**
     * Visits a {@link SwitchRule} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSwitchRule(SwitchRule node);
    /**
     * Visits a {@link SwitchLabel.CaseLabel} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCaseLabel(SwitchLabel.CaseLabel node);
    /**
     * Visits a {@link SwitchLabel.DefaultLabel} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitDefaultLabel(SwitchLabel.DefaultLabel node);
    /**
     * Visits a {@link CaseItem.CaseConstant} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCaseConstant(CaseItem.CaseConstant node);
    /**
     * Visits a {@link CaseItem.CasePattern} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCasePattern(CaseItem.CasePattern node);
    /**
     * Visits a {@link CaseItem.CasePattern.Guard} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCasePatternGuard(CaseItem.CasePattern.Guard node);
    /**
     * Visits a {@link CaseItem.CaseNull} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCaseNull(CaseItem.CaseNull node);
    /**
     * Visits a {@link WhileStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitWhileStatement(WhileStatement node);
    /**
     * Visits a {@link DoWhileStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitDoWhileStatement(DoWhileStatement node);
    /**
     * Visits a {@link BasicForStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBasicForStatement(BasicForStatement node);
    /**
     * Visits a {@link EnhancedForStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitEnhancedForStatement(EnhancedForStatement node);
    /**
     * Visits a {@link BreakStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBreakStatement(BreakStatement node);
    /**
     * Visits a {@link ContinueStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitContinueStatement(ContinueStatement node);
    /**
     * Visits a {@link ReturnStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitReturnStatement(ReturnStatement node);
    /**
     * Visits a {@link ThrowStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitThrowStatement(ThrowStatement node);
    /**
     * Visits a {@link TryStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitTryStatement(TryStatement node);
    /**
     * Visits a {@link CatchClause} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCatchClause(CatchClause node);
    /**
     * Visits a {@link FinallyClause} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitFinallyClause(FinallyClause node);
    /**
     * Visits a {@link ThrowsClause} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitThrowsClause(ThrowsClause node);
    /**
     * Visits a {@link SynchronizedStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSynchronizedStatement(SynchronizedStatement node);
    /**
     * Visits a {@link AssertStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAssertStatement(AssertStatement node);
    /**
     * Visits a {@link YieldStatement} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitYieldStatement(YieldStatement node);

    /**
     * Visits a {@link AssignmentExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitAssignmentExpression(AssignmentExpression node);
    /**
     * Visits a {@link ConditionalExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitConditionalExpression(ConditionalExpression node);
    /**
     * Visits a {@link LambdaExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitLambdaExpression(LambdaExpression node);
    /**
     * Visits a {@link MethodInvocationExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitMethodInvocationExpression(MethodInvocationExpression node);
    /**
     * Visits a {@link MethodReferenceExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitMethodReferenceExpression(MethodReferenceExpression node);
    /**
     * Visits a {@link ObjectCreationExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitObjectCreationExpression(ObjectCreationExpression node);
    /**
     * Visits a {@link ArrayInitializerExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitArrayInitializer(ArrayInitializerExpression node);
    /**
     * Visits a {@link ArrayCreationExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitArrayCreationExpression(ArrayCreationExpression node);
    /**
     * Visits a {@link ArrayAccessExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitArrayAccessExpression(ArrayAccessExpression node);
    /**
     * Visits a {@link FieldAccessExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitFieldAccessExpression(FieldAccessExpression node);
    /**
     * Visits a {@link ThisExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitThisExpression(ThisExpression node);
    /**
     * Visits a {@link SuperExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSuperExpression(SuperExpression node);
    /**
     * Visits a {@link TypeCastExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitTypeCastExpression(TypeCastExpression node);
    /**
     * Visits a {@link InstanceofExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitInstanceofExpression(InstanceofExpression node);
    /**
     * Visits a {@link BinaryExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBinaryExpression(BinaryExpression node);
    /**
     * Visits a {@link UnaryExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitUnaryExpression(UnaryExpression node);
    /**
     * Visits a {@link SwitchExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSwitchExpression(SwitchExpression node);

    /**
     * Visits a {@link Pattern.TypeTestPattern} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitTypeTestPattern(Pattern.TypeTestPattern node);
    /**
     * Visits a {@link Pattern.RecordPattern} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitRecordPattern(Pattern.RecordPattern node);
    /**
     * Visits a {@link Pattern.MatchAllPattern} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitMatchAllPattern(Pattern.MatchAllPattern node);

    /**
     * Visits a {@link PrimitiveTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitPrimitiveType(PrimitiveTypeRef node);
    /**
     * Visits a {@link ArrayTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitArrayType(ArrayTypeRef node);
    /**
     * Visits a {@link ClassOrInterfaceTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitClassOrInterfaceType(ClassOrInterfaceTypeRef node);
    /**
     * Visits a {@link ClassOrInterfaceTypeRef.Part} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitClassOrInterfaceTypePart(ClassOrInterfaceTypeRef.Part node);
    /**
     * Visits a {@link IntersectionTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitIntersectionType(IntersectionTypeRef node);
    /**
     * Visits a {@link UnionTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitUnionType(UnionTypeRef node);
    /**
     * Visits a {@link WildcardTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitWildcardType(WildcardTypeRef node);
    /**
     * Visits a {@link ThrowsClause.ExceptionType} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitExceptionType(ThrowsClause.ExceptionType node);
    /**
     * Visits a {@link SugarTypeRef} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSugarType(SugarTypeRef node);
    /**
     * Visits a {@link TypeDiamond} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitTypeDiamond(TypeDiamond node);

    /**
     * Visits a {@link Modifier} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitModifier(Modifier node);
    /**
     * Visits a {@link MarkerAnnotation} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitMarkerAnnotation(MarkerAnnotation node);
    /**
     * Visits a {@link SingleMemberAnnotation} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitSingleMemberAnnotation(SingleMemberAnnotation node);
    /**
     * Visits a {@link NormalAnnotation} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitNormalAnnotation(NormalAnnotation node);
    /**
     * Visits a {@link ElementValueArray} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitElementValueArray(ElementValueArray node);

    /**
     * Visits a {@link NameExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitName(NameExpression node);
    /**
     * Visits a {@link Parameter} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitParameter(Parameter node);
    /**
     * Visits a {@link ReceiverParameter} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitReceiverParameter(ReceiverParameter node);
    /**
     * Visits a {@link TypeParameter} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitTypeParameter(TypeParameter node);
    /**
     * Visits a {@link VariableDeclarator} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitVariableDeclarator(VariableDeclarator node);
    /**
     * Visits a {@link LambdaBody} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitLambdaBody(LambdaBody node);

    /**
     * Visits a {@link LexerToken} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitToken(LexerToken<?> node);
    /**
     * Visits a {@link Whitespace} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitWhitespace(Whitespace node);
    /**
     * Visits a {@link LineComment} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitLineComment(LineComment node);
    /**
     * Visits a {@link BlockComment} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBlockComment(BlockComment node);
    /**
     * Visits a {@link JavadocComment} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitJavadocComment(JavadocComment node);

    /**
     * Visits a {@link IntegerLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitIntegerLiteral(IntegerLiteralExpression node);
    /**
     * Visits a {@link FloatingPointLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitFloatingPointLiteral(FloatingPointLiteralExpression node);
    /**
     * Visits a {@link BooleanLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitBooleanLiteral(BooleanLiteralExpression node);
    /**
     * Visits a {@link CharacterLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitCharacterLiteral(CharacterLiteralExpression node);
    /**
     * Visits a {@link StringLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitStringLiteral(StringLiteralExpression node);
    /**
     * Visits a {@link NullLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitNullLiteral(NullLiteralExpression node);
    /**
     * Visits a {@link ClassLiteralExpression} node.
     *
     * @param node node to visit
     * @return visitor-defined result
     */
    R visitClassLiteral(ClassLiteralExpression node);
}
