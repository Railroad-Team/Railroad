package io.github.railroad.ide.classparser;

import io.github.railroad.ide.classparser.stub.ClassStub;
import org.objectweb.asm.ClassReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ClassStubParser {
    public static ClassStub parse(Path pathToClassFile) throws ClassScanException {
        try {
            return parse(new ClassReader(Files.newInputStream(pathToClassFile)));
        } catch (ClassScanException exception) {
            throw new ClassScanException("Failed to parse class file: %s".formatted(pathToClassFile), exception.getCause());
        } catch (Exception exception) {
            throw new ClassScanException("Failed to parse class file: %s".formatted(pathToClassFile), exception);
        }
    }

    public static ClassStub parse(ClassReader reader) {
        var visitor = new ClassStubVisitor();
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        ClassStub stub = visitor.createClassStub();
        if (stub == null)
            throw new ClassScanException("Class metadata unavailable for class: %s".formatted(reader.getClassName()));

        return stub;
    }
}
