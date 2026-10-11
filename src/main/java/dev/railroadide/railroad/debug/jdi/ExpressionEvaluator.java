package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.*;

/**
 * Reads locals, fields, array elements and array lengths without invoking target methods.
 */
public final class ExpressionEvaluator {
    /**
     * Evaluates an identifier followed by field access or non-negative array indices.
     * Supports locals, {@code this}, instance and static fields and array {@code length}; method calls
     * and general Java operators are unsupported and no target methods are invoked.
     *
     * @param frame suspended stack frame to inspect
     * @param expression watch expression to resolve in the selected frame
     * @return the resolved JDI value, or null for a null target value
     * @throws EvaluationException if syntax is unsupported, a name is unresolved or an access is invalid
     */
    public Value evaluate(StackFrame frame, String expression) throws EvaluationException {
        var cursor = new Cursor(expression);

        String root = cursor.readIdentifier();
        if (root == null)
            throw new EvaluationException("Expected identifier");

        Value value = resolveRoot(frame, root);

        while (!cursor.atEnd()) {
            if (cursor.consume('.')) {
                String member = cursor.readIdentifier();

                if (member == null)
                    throw new EvaluationException("Expected member name");

                value = resolveMember(
                    frame,
                    value,
                    member);

                continue;
            }

            if (cursor.consume('[')) {
                int index = cursor.readInteger();

                cursor.require(']');

                value = resolveIndex(
                    value,
                    index);

                continue;
            }

            throw new EvaluationException("Unexpected token at position " + cursor.position());
        }

        return value;
    }

    private Value resolveRoot(StackFrame frame, String name) throws EvaluationException {
        if (name.equals("this")) {
            ObjectReference object = frame.thisObject();
            if (object == null)
                throw new EvaluationException("'this' is unavailable in this frame");

            return object;
        }

        try {
            LocalVariable variable = frame.visibleVariableByName(name);

            if (variable != null)
                return frame.getValue(variable);
        } catch (AbsentInformationException _) {
        }

        ObjectReference thisObject = frame.thisObject();
        ReferenceType declaringType = frame.location().declaringType();
        Field field = declaringType.fieldByName(name);
        if (field != null) {
            if (field.isStatic())
                return field.declaringType().getValue(field);
            if (thisObject != null)
                return thisObject.getValue(field);
        }

        throw new EvaluationException("Cannot resolve '" + name + "'");
    }

    private Value resolveMember(StackFrame frame, Value target, String member) throws EvaluationException {
        if (target == null)
            throw new EvaluationException("Cannot access '" + member + "' on null");

        if (target instanceof ArrayReference array && member.equals("length"))
            return frame.virtualMachine().mirrorOf(array.length());

        if (!(target instanceof ObjectReference object))
            throw new EvaluationException("Value does not have fields");

        Field field = object.referenceType().fieldByName(member);
        if (field == null)
            throw new EvaluationException("No field named '" + member + "'");

        return field.isStatic() ? field.declaringType().getValue(field) : object.getValue(field);
    }

    private Value resolveIndex(Value target, int index) throws EvaluationException {
        if (!(target instanceof ArrayReference array))
            throw new EvaluationException("Value is not an array");

        if (index < 0 || index >= array.length())
            throw new EvaluationException("Array index out of bounds: " + index);

        return array.getValue(index);
    }

    private static final class Cursor {
        private final String text;
        private int index;

        private Cursor(String text) {
            this.text = text;
        }

        private int position() {
            return index;
        }

        private boolean atEnd() {
            skipWhitespace();
            return index >= text.length();
        }

        private boolean consume(char expected) {
            skipWhitespace();

            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }

            return false;
        }

        private void require(char expected) throws EvaluationException {
            if (!consume(expected))
                throw new EvaluationException("Expected '" + expected + "'");
        }

        private String readIdentifier() {
            skipWhitespace();

            if (index >= text.length())
                return null;

            char first = text.charAt(index);
            if (!Character.isJavaIdentifierStart(first))
                return null;

            int start = index++;
            while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
                index++;
            }

            return text.substring(start, index);
        }

        private int readInteger() throws EvaluationException {
            skipWhitespace();

            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }

            if (start == index)
                throw new EvaluationException("Expected array index");

            return Integer.parseInt(text.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }
}
