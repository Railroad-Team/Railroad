package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.*;
import dev.railroadide.railroad.debug.model.DebugVariable;

import java.util.*;

/**
 * Formats JDI values and retains expandable references for the current suspension.
 */
public final class VariableInspector {
    private long generation;
    private long nextReference = 1;

    private final Map<Long, Value> references = new HashMap<>();

    /**
     * Starts a new inspection lifetime, discarding previously retained references without reusing handles.
     *
     * @param generation identifier of the newly active suspension
     */
    public void beginSuspension(long generation) {
        this.generation = generation;
        references.clear();
    }

    /**
     * Releases all retained values so previously issued expansion handles become invalid.
     */
    public void endSuspension() {
        references.clear();
    }

    /**
     * Reads the receiver and visible locals; when local-variable metadata is missing, exposes numbered arguments.
     *
     * @param frame suspended stack frame to inspect
     * @return formatted values in the frame
     */
    public List<DebugVariable> inspectFrame(StackFrame frame) {
        List<DebugVariable> variables = new ArrayList<>();

        ObjectReference thisObject = frame.thisObject();
        if (thisObject != null) {
            variables.add(createVariable(
                "this",
                thisObject.referenceType().name(),
                thisObject));
        }

        try {
            List<LocalVariable> localVariables = frame.visibleVariables();
            Map<LocalVariable, Value> localVariableValues = frame.getValues(localVariables);
            for (LocalVariable localVariable : localVariables) {
                variables.add(createVariable(
                    localVariable.name(),
                    localVariable.typeName(),
                    localVariableValues.get(localVariable)));
            }
        } catch (AbsentInformationException _) {
            /*
             * No local-variable table.
             *
             * We can still expose method arguments without
             * their original names.
             */

            List<Value> arguments = frame.getArgumentValues();

            for (int index = 0; index < arguments.size(); index++) {
                variables.add(
                    createVariable(
                        "arg" + index,
                        typeOf(arguments.get(index)),
                        arguments.get(index)));
            }
        }

        return variables;
    }

    /**
     * Formats a JDI value and retains an expansion handle for arrays and non-string objects.
     *
     * @param name display name for the variable or watch
     * @param declaredType declared type name to display
     * @param value JDI value to format, or null for a null value
     * @return a display value with its type and any child-expansion metadata
     */
    public DebugVariable createVariable(String name, String declaredType, Value value) {
        if (value == null)
            return new DebugVariable(
                name,
                declaredType,
                "null",
                0,
                0);

        long childrenReference = 0;
        int indexedChildren = 0;

        if (value instanceof ArrayReference array) {
            childrenReference = storeReference(value);
            indexedChildren = array.length();
        } else if (value instanceof ObjectReference && !(value instanceof StringReference)) {
            childrenReference = storeReference(value);
        }

        return new DebugVariable(
            name,
            declaredType,
            formatValue(value),
            childrenReference,
            indexedChildren);
    }

    private long storeReference(Value value) {
        long id = nextReference++;
        references.put(id, value);
        return id;
    }

    /**
     * Expands a retained value into paged array elements or visible non-static object fields.
     *
     * @param reference expansion handle from the current suspension
     * @param start zero-based array offset; ignored for object fields
     * @param count maximum number of array elements; ignored for object fields
     * @return formatted child values, or an empty list for an empty range or leaf value
     * @throws IllegalArgumentException if the expansion handle is unknown or expired
     */
    public List<DebugVariable> expand(long reference, int start, int count) {
        Value value = references.get(reference);

        return switch (value) {
            case null -> throw new IllegalArgumentException("Unknown or expired variable reference");
            case ArrayReference array -> expandArray(array, start, count);
            case ObjectReference object -> expandObject(object);
            default -> List.of();
        };
    }

    private List<DebugVariable> expandArray(ArrayReference array, int start, int count) {
        int actualStart = Math.max(0, start);
        int actualCount = Math.min(count, array.length() - actualStart);
        if (actualCount <= 0)
            return List.of();

        List<Value> values = array.getValues(actualStart, actualCount);
        List<DebugVariable> variables = new ArrayList<>(values.size());

        for (int i = 0; i < values.size(); i++) {
            Value value = values.get(i);

            variables.add(createVariable(
                "[" + (actualStart + i) + "]",
                typeOf(value),
                value));
        }

        return variables;
    }

    private List<DebugVariable> expandObject(ObjectReference object) {
        List<Field> fields = object.referenceType()
            .visibleFields()
            .stream()
            .filter(field -> !field.isStatic())
            .sorted(
                Comparator.comparing(
                    Field::name))
            .toList();

        Map<Field, Value> values = object.getValues(fields);
        List<DebugVariable> variables = new ArrayList<>();

        for (Field field : fields) {
            variables.add(createVariable(
                field.name(),
                field.typeName(),
                values.get(field)));
        }

        return variables;
    }

    private String formatValue(Value value) {
        switch (value) {
            case null -> {
                return "null";
            }
            case StringReference string -> {
                String text = string.value();

                if (text.length() > 200) {
                    text = text.substring(0, 200) + "…";
                }

                return "\"" + escape(text) + "\"";
            }
            case CharValue character -> {
                return "'" + character.value() + "'";
            }
            case BooleanValue booleanValue -> {
                return Boolean.toString(
                    booleanValue.value());
            }
            case ByteValue number -> {
                return Byte.toString(number.value());
            }
            case ShortValue number -> {
                return Short.toString(number.value());
            }
            case IntegerValue number -> {
                return Integer.toString(number.value());
            }
            case LongValue number -> {
                return number.value() + "L";
            }
            case FloatValue number -> {
                return number.value() + "f";
            }
            case DoubleValue number -> {
                return Double.toString(number.value());
            }
            case ArrayReference array -> {
                return array.referenceType().name()
                    + "[length="
                    + array.length()
                    + "]";
            }
            case ObjectReference object -> {
                return object.referenceType().name()
                    + "@"
                    + Long.toHexString(
                        object.uniqueID());
            }
            default -> {
            }
        }

        return String.valueOf(value);
    }

    private String typeOf(Value value) {
        return value == null
            ? "null"
            : value.type().name();
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\"", "\\\"");
    }
}
