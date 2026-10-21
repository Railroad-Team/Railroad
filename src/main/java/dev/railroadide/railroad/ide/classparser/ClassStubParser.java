package dev.railroadide.railroad.ide.classparser;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import org.objectweb.asm.ClassReader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses JVM class files into lightweight class metadata stubs.
 */
public class ClassStubParser {
    /**
     * Visits a class file to collect its declaration and member metadata.
     *
     * @param pathToClassFile the path to the class file
     * @return the parsed class stub
     * @throws ClassScanException if the bytecode cannot be read or class metadata is unavailable
     */
    public static ClassStub parse(Path pathToClassFile) throws ClassScanException {
        try {
            return parse(new ClassReader(Files.newInputStream(pathToClassFile)));
        } catch (ClassScanException exception) {
            throw new ClassScanException("Failed to parse class file: %s".formatted(pathToClassFile),
                exception.getCause());
        } catch (Exception exception) {
            throw new ClassScanException("Failed to parse class file: %s".formatted(pathToClassFile), exception);
        }
    }

    /**
     * Visits a class file to collect its declaration and member metadata.
     *
     * @param reader the ASM reader containing class bytecode
     * @return the parsed class stub
     * @throws ClassScanException if the bytecode cannot be read or class metadata is unavailable
     */
    public static ClassStub parse(ClassReader reader) {
        var visitor = new ClassStubVisitor();
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        ClassStub stub = visitor.createClassStub();
        if (stub == null)
            throw new ClassScanException("Class metadata unavailable for class: %s".formatted(reader.getClassName()));

        return stub;
    }
}
