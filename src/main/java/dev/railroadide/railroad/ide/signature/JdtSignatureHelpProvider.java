package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.completion.JdtCompletionProvider;
import org.eclipse.jdt.core.dom.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Signature help provider backed by Eclipse JDT. Responsible solely for
 * analysing the document and producing {@link SignatureHelp} records; UI logic
 * remains in the editor.
 */
public record JdtSignatureHelpProvider(Path filePath, String[] systemModulePaths) implements SignatureHelpProvider {
    @Override
    public @Nullable SignatureHelp compute(String document, int caretPosition) {
        if (document == null || document.isEmpty() || caretPosition < 0)
            return null;

        ASTParser parser = ASTParser.newParser(AST.JLS21); // TODO: Detect version from project settings
        parser.setSource(document.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName(filePath.getFileName().toString());
        parser.setEnvironment(systemModulePaths, null, null, false);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        int searchAt = Math.max(0, Math.min(Math.max(document.length() - 1, 0), caretPosition > 0 ? caretPosition - 1 : 0));
        var finder = new NodeFinder(unit, searchAt, 0);
        ASTNode node = finder.getCoveringNode();

        while (node != null) {
            SignatureHelp result = switch (node) {
                case MethodInvocation invocation ->
                    buildSignatureHelpForMethodInvocation(invocation, caretPosition, document);
                case ClassInstanceCreation creation ->
                    buildSignatureHelpForClassInstanceCreation(creation, caretPosition, document);
                case SuperMethodInvocation invocation ->
                    buildSignatureHelpForSuperMethodInvocation(invocation, caretPosition, document);
                case ConstructorInvocation invocation ->
                    buildSignatureHelpForConstructorInvocation(invocation, caretPosition, document);
                case SuperConstructorInvocation invocation ->
                    buildSignatureHelpForSuperConstructorInvocation(invocation, caretPosition, document);
                default -> null;
            };

            if (result != null)
                return result;

            node = node.getParent();
        }

        return null;
    }

    private @Nullable SignatureHelp buildSignatureHelpForMethodInvocation(
        MethodInvocation invocation,
        int caretPosition,
        String text
    ) {
        if (isInvalidBounds(caretPosition, findParenthesisBounds(invocation, text)))
            return null;

        IMethodBinding binding = invocation.resolveMethodBinding();
        if (binding == null)
            return null;

        int argumentIndex = computeArgumentIndex(invocation.arguments(), caretPosition);
        return createSignatureHelp(binding, argumentIndex);
    }

    private @Nullable SignatureHelp buildSignatureHelpForSuperMethodInvocation(
        SuperMethodInvocation invocation,
        int caretPosition,
        String text
    ) {
        if (isInvalidBounds(caretPosition, findParenthesisBounds(invocation, text)))
            return null;

        IMethodBinding binding = invocation.resolveMethodBinding();
        if (binding == null)
            return null;

        int argumentIndex = computeArgumentIndex(invocation.arguments(), caretPosition);
        return createSignatureHelp(binding, argumentIndex);
    }

    private @Nullable SignatureHelp buildSignatureHelpForClassInstanceCreation(
        ClassInstanceCreation creation,
        int caretPosition,
        String text
    ) {
        if (isInvalidBounds(caretPosition, findParenthesisBounds(creation, text)))
            return null;

        IMethodBinding binding = creation.resolveConstructorBinding();
        if (binding == null)
            return null;

        int argumentIndex = computeArgumentIndex(creation.arguments(), caretPosition);
        return createSignatureHelp(binding, argumentIndex);
    }

    private @Nullable SignatureHelp buildSignatureHelpForConstructorInvocation(
        ConstructorInvocation invocation,
        int caretPosition,
        String text
    ) {
        if (isInvalidBounds(caretPosition, findParenthesisBounds(invocation, text)))
            return null;

        IMethodBinding binding = invocation.resolveConstructorBinding();
        if (binding == null)
            return null;

        int argumentIndex = computeArgumentIndex(invocation.arguments(), caretPosition);
        return createSignatureHelp(binding, argumentIndex);
    }

    private @Nullable SignatureHelp buildSignatureHelpForSuperConstructorInvocation(
        SuperConstructorInvocation invocation,
        int caretPosition,
        String text
    ) {
        if (isInvalidBounds(caretPosition, findParenthesisBounds(invocation, text)))
            return null;

        IMethodBinding binding = invocation.resolveConstructorBinding();
        if (binding == null)
            return null;

        int argumentIndex = computeArgumentIndex(invocation.arguments(), caretPosition);
        return createSignatureHelp(binding, argumentIndex);
    }

    private boolean isInvalidBounds(int caretPosition, int[] bounds) {
        int open = bounds[0];
        int close = bounds[1];
        if (open == -1 || caretPosition < open + 1)
            return true;

        return close != -1 && caretPosition > close;
    }

    private int[] findParenthesisBounds(ASTNode node, String text) {
        int start = node.getStartPosition();
        int length = Math.max(0, node.getLength());
        int end = Math.min(text.length(), start + length);
        if (start < 0 || start >= text.length())
            return new int[]{-1, -1};

        String snippet = text.substring(start, end);
        int openOffset = snippet.indexOf('(');
        if (openOffset < 0)
            return new int[]{-1, -1};

        int closeOffset = snippet.lastIndexOf(')');
        int open = start + openOffset;
        int close = closeOffset >= 0 ? start + closeOffset : -1;
        return new int[]{open, close};
    }

    private int computeArgumentIndex(List<?> arguments, int caretPosition) {
        if (arguments == null || arguments.isEmpty())
            return 0;

        for (int i = 0; i < arguments.size(); i++) {
            Object obj = arguments.get(i);
            if (obj instanceof ASTNode arg) {
                int start = arg.getStartPosition();
                int end = start + Math.max(0, arg.getLength());
                if (caretPosition <= start || caretPosition <= end)
                    return i;
            }
        }

        return arguments.size();
    }

    private SignatureHelp createSignatureHelp(IMethodBinding binding, int requestedIndex) {
        ITypeBinding declaring = binding.getDeclaringClass();
        String ownerQualified = declaring != null ? JdtCompletionProvider.renderType(declaring) : "";
        String ownerDisplay = declaring != null ? simpleName(ownerQualified) : "";
        String methodName = binding.isConstructor() ? ownerDisplay : binding.getName();

        ITypeBinding[] parameterTypes = binding.getParameterTypes();
        List<SignatureHelp.ParameterInfo> parameters = new ArrayList<>(parameterTypes.length);
        for (int i = 0; i < parameterTypes.length; i++) {
            ITypeBinding parameterType = parameterTypes[i];
            String typeLabel = JdtCompletionProvider.renderType(parameterType);
            boolean varargs = binding.isVarargs() && i == parameterTypes.length - 1;
            if (varargs && typeLabel.endsWith("[]")) {
                typeLabel = typeLabel.substring(0, typeLabel.length() - 2) + "...";
            }

            String name = "arg" + i;
            parameters.add(new SignatureHelp.ParameterInfo(typeLabel, name, varargs));
        }

        int activeIndex = computeActiveParameter(requestedIndex, parameters.size(), binding.isVarargs());
        String returnType = binding.isConstructor() ? ownerQualified : JdtCompletionProvider.renderType(binding.getReturnType());

        return new SignatureHelp(
            ownerQualified,
            ownerDisplay.isBlank() ? ownerQualified : ownerDisplay,
            methodName,
            parameters,
            activeIndex,
            binding.isConstructor(),
            returnType,
            binding.isVarargs()
        );
    }

    private int computeActiveParameter(int requestedIndex, int parameterCount, boolean varargs) {
        if (parameterCount == 0)
            return -1;

        if (requestedIndex < parameterCount)
            return Math.max(0, requestedIndex);

        return varargs ? parameterCount - 1 : parameterCount - 1;
    }

    private String simpleName(String qualified) {
        if (qualified == null || qualified.isBlank())
            return "";

        int lastDot = qualified.lastIndexOf('.');
        if (lastDot == -1)
            return qualified;

        return qualified.substring(lastDot + 1);
    }
}
