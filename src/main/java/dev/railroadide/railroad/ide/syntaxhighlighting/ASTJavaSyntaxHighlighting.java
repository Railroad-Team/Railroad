package dev.railroadide.railroad.ide.syntaxhighlighting;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.modules.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dev.railroadide.railroad.Railroad;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;

public class ASTJavaSyntaxHighlighting {
    private static final JavaParser PARSER = new JavaParser();

    private ASTJavaSyntaxHighlighting() {
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        long start = System.currentTimeMillis();
        CompilationUnit compilationUnit = PARSER.parse(text).getResult().orElseThrow();

        var highlighter = new SyntaxHighlighter(text);
        highlighter.visit(compilationUnit, null);

        var styles = highlighter.computeStyleSpans();
        Railroad.LOGGER.debug("Computed highlighting in {} ms", System.currentTimeMillis() - start);
        return styles;
    }

    public static class SyntaxHighlighter extends VoidVisitorAdapter<Void> {
        private final List<StyleRange> styleRanges = new ArrayList<>();
        private final String text;

        public SyntaxHighlighter(String text) {
            this.text = text;
        }

        private static int getIndex(com.github.javaparser.Position position, String text) {
            int line = position.line;
            int column = position.column;

            int index = 0;
            for (int i = 0; i < line - 1; i++) {
                index = text.indexOf('\n', index) + 1;
            }

            return index + column - 1;
        }

        private static String getStyleClassByNode(Node node) {
            return switch (node) {
                case ClassOrInterfaceDeclaration classOrInterfaceDeclaration -> "class";
                case PackageDeclaration packageDeclaration -> "package";
                case Name name -> "name";
                case ImportDeclaration importDeclaration -> "import";
                case Comment comment -> "comment";
                case AnnotationDeclaration annotationDeclaration -> "annotation";
                case AnnotationMemberDeclaration annotationMemberDeclaration -> "annotation";
                case ArrayAccessExpr arrayAccessExpr -> "array";
                case ArrayCreationExpr arrayCreationExpr -> "array";
                case ArrayInitializerExpr arrayInitializerExpr -> "array";
                case AssertStmt assertStmt -> "keyword";
                case AssignExpr assignExpr -> "operator";
                case BinaryExpr binaryExpr -> "operator";
                case BlockStmt blockStmt -> "block";
                case BooleanLiteralExpr booleanLiteralExpr -> "literal";
                case BreakStmt breakStmt -> "keyword";
                case CastExpr castExpr -> "operator";
                case CatchClause catchClause -> "keyword";
                case CharLiteralExpr charLiteralExpr -> "literal";
                case ClassOrInterfaceType classOrInterfaceType -> "class";
                case ConditionalExpr conditionalExpr -> "operator";
                case ConstructorDeclaration constructorDeclaration -> "constructor";
                case ContinueStmt continueStmt -> "keyword";
                case DoStmt doStmt -> "keyword";
                case DoubleLiteralExpr doubleLiteralExpr -> "literal";
                case EmptyStmt emptyStmt -> "keyword";
                case EnclosedExpr enclosedExpr -> "operator";
                case EnumConstantDeclaration enumConstantDeclaration -> "enum";
                case EnumDeclaration enumDeclaration -> "enum";
                case ExplicitConstructorInvocationStmt explicitConstructorInvocationStmt -> "constructor";
                case ExpressionStmt expressionStmt -> "expression";
                case FieldAccessExpr fieldAccessExpr -> "field";
                case FieldDeclaration fieldDeclaration -> "field";
                case ForEachStmt forEachStmt -> "keyword";
                case ForStmt forStmt -> "keyword";
                case IfStmt ifStmt -> "keyword";
                case InitializerDeclaration initializerDeclaration -> "block";
                case InstanceOfExpr instanceOfExpr -> "operator";
                case IntegerLiteralExpr integerLiteralExpr -> "literal";
                case LabeledStmt labeledStmt -> "label";
                case LongLiteralExpr longLiteralExpr -> "literal";
                case MarkerAnnotationExpr markerAnnotationExpr -> "annotation";
                case MemberValuePair memberValuePair -> "name";
                case MethodCallExpr methodCallExpr -> "method";
                case MethodDeclaration methodDeclaration -> "method";
                case NameExpr nameExpr -> "name";
                case NormalAnnotationExpr normalAnnotationExpr -> "annotation";
                case NullLiteralExpr nullLiteralExpr -> "literal";
                case ObjectCreationExpr objectCreationExpr -> "object";
                case Parameter parameter -> "parameter";
                case PrimitiveType primitiveType -> "type";
                case SimpleName simpleName -> "name";
                case ArrayType arrayType -> "type";
                case ArrayCreationLevel arrayCreationLevel -> "array";
                case IntersectionType intersectionType -> "type";
                case UnionType unionType -> "type";
                case ReturnStmt returnStmt -> "keyword";
                case SingleMemberAnnotationExpr singleMemberAnnotationExpr -> "annotation";
                case StringLiteralExpr stringLiteralExpr -> "literal";
                case SuperExpr superExpr -> "keyword";
                case SwitchEntry switchEntry -> "keyword";
                case SwitchStmt switchStmt -> "keyword";
                case SynchronizedStmt synchronizedStmt -> "keyword";
                case ThisExpr thisExpr -> "keyword";
                case ThrowStmt throwStmt -> "keyword";
                case TryStmt tryStmt -> "keyword";
                case LocalClassDeclarationStmt localClassDeclarationStmt -> "class";
                case LocalRecordDeclarationStmt localRecordDeclarationStmt -> "record";
                case TypeParameter typeParameter -> "type";
                case UnaryExpr unaryExpr -> "operator";
                case VariableDeclarationExpr variableDeclarationExpr -> "variable";
                case VariableDeclarator variableDeclarator -> "variable";
                case VoidType voidType -> "type";
                case WhileStmt whileStmt -> "keyword";
                case WildcardType wildcardType -> "type";
                case LambdaExpr lambdaExpr -> "lambda";
                case MethodReferenceExpr methodReferenceExpr -> "method";
                case TypeExpr typeExpr -> "type";
                case ModuleDeclaration moduleDeclaration -> "module";
                case ModuleRequiresDirective moduleRequiresDirective -> "module";
                case ModuleExportsDirective moduleExportsDirective -> "module";
                case ModuleProvidesDirective moduleProvidesDirective -> "module";
                case ModuleUsesDirective moduleUsesDirective -> "module";
                case ModuleOpensDirective moduleOpensDirective -> "module";
                case UnparsableStmt unparsableStmt -> "error";
                case ReceiverParameter receiverParameter -> "parameter";
                case VarType varType -> "type";
                case Modifier modifier -> "modifier";
                case SwitchExpr switchExpr -> "keyword";
                case TextBlockLiteralExpr textBlockLiteralExpr -> "literal";
                case YieldStmt yieldStmt -> "keyword";
                case TypePatternExpr typePatternExpr -> "type";
                case RecordDeclaration recordDeclaration -> "record";
                case CompactConstructorDeclaration compactConstructorDeclaration -> "constructor";
                case RecordPatternExpr recordPatternExpr -> "record";
                case null, default -> "";
            };
        }

        public StyleSpans<Collection<String>> computeStyleSpans() {
            // Ensure ranges are in order
            styleRanges.sort(Comparator.comparingInt(styleRange -> styleRange.beginOffset));
            StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
            int lastEnd = 0;

            for (int index = 0; index < styleRanges.size(); index++) {
                StyleRange styleRange = styleRanges.get(index);
                StyleRange nextStyleRange = index + 1 < styleRanges.size() ? styleRanges.get(index + 1) : null;
                if (styleRange.beginOffset > lastEnd) {
                    spansBuilder.add(Collections.emptyList(), styleRange.beginOffset - lastEnd);
                }

                int endOffset = nextStyleRange != null ? nextStyleRange.beginOffset - 1 : styleRange.endOffset;
                spansBuilder.add(
                    Collections.singleton(styleRange.styleClass),
                    (endOffset - styleRange.beginOffset) + 1);
                lastEnd = endOffset + 1;
            }

            if (lastEnd < text.length()) {
                spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
            }

            return spansBuilder.create();
        }

        private void addStyleRange(Node node) {
            Range range = node.getRange().orElseThrow();
            int beginOffset = getIndex(range.begin, text);
            int endOffset = getIndex(range.end, text);
            String styleClass = getStyleClassByNode(node);

            styleRanges.add(new StyleRange(beginOffset, endOffset, styleClass));
        }

        @Override
        public void visit(PackageDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(Name n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LineComment n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(BlockComment n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(AnnotationDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(AnnotationMemberDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ArrayAccessExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ArrayCreationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ArrayInitializerExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(AssertStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(AssignExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(BlockStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(BooleanLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(BreakStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(CastExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(CatchClause n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(CharLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ConditionalExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ContinueStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(DoubleLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(EmptyStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(EnclosedExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(EnumConstantDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ExplicitConstructorInvocationStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ExpressionStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(InitializerDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(InstanceOfExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(IntegerLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(JavadocComment n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LabeledStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LongLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(MarkerAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(MemberValuePair n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(NormalAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(NullLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(Parameter n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(PrimitiveType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SimpleName n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ArrayType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ArrayCreationLevel n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(IntersectionType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(UnionType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ReturnStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(StringLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SuperExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SwitchEntry n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SynchronizedStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ThisExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ThrowStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(TryStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LocalClassDeclarationStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LocalRecordDeclarationStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(TypeParameter n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(UnaryExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(VariableDeclarationExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(VoidType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(WildcardType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(LambdaExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(TypeExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleRequiresDirective n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleExportsDirective n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleProvidesDirective n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleUsesDirective n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ModuleOpensDirective n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(UnparsableStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(ReceiverParameter n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(VarType n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(Modifier n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(SwitchExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(TextBlockLiteralExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(YieldStmt n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(TypePatternExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(RecordDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(CompactConstructorDeclaration n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        @Override
        public void visit(RecordPatternExpr n, Void arg) {
            super.visit(n, arg);
            addStyleRange(n);
        }

        private record StyleRange(int beginOffset, int endOffset, String styleClass) implements Comparable<StyleRange> {
            @Override
            public int compareTo(StyleRange other) {
                return Integer.compare(this.beginOffset, other.beginOffset);
            }
        }
    }
}
