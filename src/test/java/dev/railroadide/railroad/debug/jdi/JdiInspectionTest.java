package dev.railroadide.railroad.debug.jdi;

import com.sun.jdi.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

public class JdiInspectionTest {
    @Test
    public void staticFieldWatchWorksWithoutReceiverOrLocalMetadata() throws Exception {
        IntegerValue value = proxy(IntegerValue.class, (_, method, _) -> {
            throw new UnsupportedOperationException(method.getName());
        });
        ReferenceType declaringType = proxy(ReferenceType.class, (_, method, _) -> {
            if (method.getName().equals("getValue"))
                return value;
            throw new UnsupportedOperationException(method.getName());
        });
        Field field = proxy(Field.class, (_, method, _) -> switch (method.getName()) {
            case "isStatic" -> true;
            case "declaringType" -> declaringType;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ReferenceType frameType = proxy(ReferenceType.class, (_, method, args) -> {
            if (method.getName().equals("fieldByName"))
                return args[0].equals("COUNT") ? field : null;
            throw new UnsupportedOperationException(method.getName());
        });
        Location location = proxy(Location.class, (_, method, _) -> {
            if (method.getName().equals("declaringType"))
                return frameType;
            throw new UnsupportedOperationException(method.getName());
        });
        StackFrame frame = proxy(StackFrame.class, (_, method, _) -> switch (method.getName()) {
            case "visibleVariableByName" -> throw new AbsentInformationException();
            case "thisObject" -> null;
            case "location" -> location;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var evaluator = new ExpressionEvaluator();
        assertSame(value, evaluator.evaluate(frame, "COUNT"));
        assertThrows(EvaluationException.class, () -> evaluator.evaluate(frame, "missing"));
    }

    @Test
    public void oldExpansionHandleCannotAliasNewSuspensionValue() {
        ReferenceType type = proxy(ReferenceType.class, (_, method, _) -> {
            if (method.getName().equals("name"))
                return "Example";
            throw new UnsupportedOperationException(method.getName());
        });
        ObjectReference object = proxy(ObjectReference.class, (_, method, _) -> switch (method.getName()) {
            case "referenceType" -> type;
            case "uniqueID" -> 1L;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var inspector = new VariableInspector();
        inspector.beginSuspension(1);
        long old = inspector.createVariable("first", "Example", object).childrenReference();
        inspector.endSuspension();
        inspector.beginSuspension(2);
        long current = inspector.createVariable("second", "Example", object).childrenReference();
        assertTrue(current > old);
        assertThrows(IllegalArgumentException.class, () -> inspector.expand(old, 0, 100));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
