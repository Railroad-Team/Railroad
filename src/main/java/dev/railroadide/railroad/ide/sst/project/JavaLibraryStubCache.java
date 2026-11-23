package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.ClassStubParser;
import dev.railroadide.railroad.ide.classparser.ClassStubVisitor;
import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.*;
import dev.railroadide.railroad.utility.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Persists the class stubs parsed from each library archive, so an archive is only scanned again after it changes.
 * Stubs are stored in their {@linkplain BinaryStubPublication#copy(ClassStub) published} form and are restored
 * immutable, so they can be published without copying again.
 * <p>
 * A cached archive is reused while its path, size and modification time match. Entries are also tied to the class
 * stub parser: any change to the parser or stub classes invalidates every cached archive.
 */
public final class JavaLibraryStubCache {
    private static final String MAGIC = "railroad-library-stubs";
    private static final int FORMAT_VERSION = 1;
    private static final List<Class<?>> PARSER_CLASSES = List.of(
        ClassStubParser.class,
        ClassStubVisitor.class,
        BinaryStubPublication.class,
        Type.class,
        ClassStub.class,
        FieldStub.class,
        MethodStub.class,
        ConstructorStub.class,
        Parameter.class,
        TypeParameter.class,
        AnnotationStub.class);
    private static final String PARSER_VERSION = parserVersion();

    private static volatile @Nullable JavaLibraryStubCache shared;

    private final Path directory;

    /**
     * Creates a cache that keeps one file per archive in {@code directory}.
     *
     * @param directory the directory holding cached archives
     */
    public JavaLibraryStubCache(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Enables caching for every library index built afterwards. Caching is disabled until this is called.
     *
     * @param directory the directory holding cached archives
     */
    public static void enable(Path directory) {
        shared = new JavaLibraryStubCache(directory);
    }

    /**
     * Returns the cache enabled for the application, if any.
     *
     * @return the shared cache, or empty while caching is disabled
     */
    public static Optional<JavaLibraryStubCache> shared() {
        return Optional.ofNullable(shared);
    }

    /**
     * Loads the cached classes of an archive.
     *
     * @param archive the library archive
     * @param size the current size of the archive
     * @param lastModified the current modification time of the archive, in milliseconds
     * @return the cached classes in archive order, or empty when nothing matching the archive is cached
     * @throws IOException if a cached file exists but cannot be read or is malformed
     */
    public Optional<List<ArchiveClass>> load(Path archive, long size, long lastModified) throws IOException {
        Path file = cachePath(archive);
        if (Files.notExists(file))
            return Optional.empty();

        try (var input = new StubInput(
            new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16)))) {
            if (!input.header(archive, size, lastModified))
                return Optional.empty();

            int count = input.count();
            List<ArchiveClass> classes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                classes.add(new ArchiveClass(input.string(), input.classStub()));
            }

            return Optional.of(classes);
        } catch (RuntimeException exception) {
            throw new IOException("Malformed library stub cache " + file, exception);
        }
    }

    /**
     * Caches the classes parsed from an archive, replacing any earlier entry for it.
     *
     * @param archive the library archive
     * @param size the size of the archive the classes were parsed from
     * @param lastModified the modification time of the archive the classes were parsed from, in milliseconds
     * @param classes the parsed classes in archive order
     * @throws IOException if the classes cannot be written
     */
    public void save(Path archive, long size, long lastModified, List<ArchiveClass> classes) throws IOException {
        Path file = cachePath(archive);
        Files.createDirectories(directory);
        Path tempFile = Files.createTempFile(directory, "library-stubs", ".tmp");
        try {
            try (var output = new StubOutput(
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile), 1 << 16)))) {
                output.header(archive, size, lastModified);
                output.count(classes.size());
                for (ArchiveClass archiveClass : classes) {
                    output.string(archiveClass.entryName());
                    output.classStub(archiveClass.stub());
                }
            }

            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | AccessDeniedException exception) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path cachePath(Path archive) {
        return directory.resolve(sha256(key(archive).getBytes(StandardCharsets.UTF_8)) + ".bin");
    }

    private static String key(Path archive) {
        return FileUtils.normalizePath(archive).toString();
    }

    private static String parserVersion() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Class<?> type : PARSER_CLASSES) {
                hashClassFiles(type, digest);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to fingerprint the class stub parser", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /** Hashes the class file of {@code type}, its nested classes and its anonymous classes. */
    private static void hashClassFiles(Class<?> type, MessageDigest digest) throws IOException {
        String simpleBinaryName = type.getName().substring(type.getName().lastIndexOf('.') + 1);
        if (!hashResource(type, simpleBinaryName + ".class", digest))
            throw new IOException("Missing class file for " + type.getName());

        // Anonymous classes are numbered from 1 without gaps.
        int anonymous = 1;
        while (hashResource(type, simpleBinaryName + "$" + anonymous + ".class", digest)) {
            anonymous++;
        }

        for (Class<?> nested : type.getDeclaredClasses()) {
            hashClassFiles(nested, digest);
        }
    }

    private static boolean hashResource(Class<?> type, String resource, MessageDigest digest) throws IOException {
        try (InputStream stream = type.getResourceAsStream(resource)) {
            if (stream == null)
                return false;

            digest.update(stream.readAllBytes());
            return true;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /**
     * A class parsed from a library archive.
     *
     * @param entryName the archive entry the class was read from
     * @param stub the parsed class
     */
    public record ArchiveClass(String entryName, ClassStub stub) {
    }

    private static final class StubOutput implements Closeable {
        private final DataOutputStream output;
        private final Map<String, Integer> strings = new HashMap<>();

        private StubOutput(DataOutputStream output) {
            this.output = output;
        }

        private void header(Path archive, long size, long lastModified) throws IOException {
            output.writeUTF(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeUTF(PARSER_VERSION);
            string(key(archive));
            output.writeLong(size);
            output.writeLong(lastModified);
        }

        private void count(int count) throws IOException {
            output.writeInt(count);
        }

        /** Writes each distinct string once; later occurrences refer back to it by index. */
        private void string(@Nullable String value) throws IOException {
            if (value == null) {
                output.writeInt(-1);
                return;
            }

            Integer index = strings.get(value);
            if (index != null) {
                output.writeInt(index);
                return;
            }

            strings.put(value, strings.size());
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(-2);
            output.writeInt(bytes.length);
            output.write(bytes);
        }

        private void classStub(ClassStub stub) throws IOException {
            string(stub.packageName());
            string(stub.name());
            typeParameters(stub.typeParameters());
            type(stub.superClass());
            types(stub.interfaces());
            output.writeInt(stub.fields().size());
            for (FieldStub field : stub.fields()) {
                string(field.name());
                type(field.type());
                output.writeInt(field.modifiers());
                annotations(field.annotations());
            }

            output.writeInt(stub.methods().size());
            for (MethodStub method : stub.methods()) {
                string(method.name());
                type(method.returnType());
                parameters(method.parameters());
                output.writeInt(method.modifiers());
                types(method.thrownTypes());
                annotations(method.annotations());
                typeParameters(method.typeParameters());
            }

            output.writeInt(stub.constructors().size());
            for (ConstructorStub constructor : stub.constructors()) {
                parameters(constructor.parameters());
                output.writeInt(constructor.modifiers());
                types(constructor.thrownTypes());
                annotations(constructor.annotations());
                typeParameters(constructor.typeParameters());
            }

            output.writeInt(stub.modifiers());
            annotations(stub.annotations());
            string(stub.enclosingClassName());
        }

        private void parameters(List<Parameter> parameters) throws IOException {
            output.writeInt(parameters.size());
            for (Parameter parameter : parameters) {
                string(parameter.name());
                type(parameter.type());
                annotations(parameter.annotations());
            }
        }

        private void typeParameters(List<TypeParameter> typeParameters) throws IOException {
            output.writeInt(typeParameters.size());
            for (TypeParameter typeParameter : typeParameters) {
                string(typeParameter.name());
                types(typeParameter.bounds());
            }
        }

        private void types(List<Type> types) throws IOException {
            output.writeInt(types.size());
            for (Type type : types) {
                type(type);
            }
        }

        private void type(@Nullable Type type) throws IOException {
            switch (type) {
                case null -> output.writeByte(0);
                case Type.ClassType classType -> {
                    output.writeByte(1);
                    string(classType.name());
                    types(classType.typeArguments());
                }
                case Type.PrimitiveType primitive -> {
                    output.writeByte(2);
                    string(primitive.name());
                }
                case Type.ArrayType array -> {
                    output.writeByte(3);
                    type(array.componentType());
                }
                case Type.TypeVariable variable -> {
                    output.writeByte(4);
                    string(variable.name());
                }
                case Type.WildcardType wildcard -> {
                    output.writeByte(5);
                    type(wildcard.bound());
                    output.writeBoolean(wildcard.isUpperBound());
                }
            }
        }

        private void annotations(List<AnnotationStub> annotations) throws IOException {
            output.writeInt(annotations.size());
            for (AnnotationStub annotation : annotations) {
                string(annotation.name());
                output.writeInt(annotation.values().size());
                for (Map.Entry<String, Object> value : annotation.values().entrySet()) {
                    string(value.getKey());
                    annotationValue(value.getValue());
                }
            }
        }

        private void annotationValue(Object value) throws IOException {
            switch (value) {
                case String string -> {
                    output.writeByte(1);
                    string(string);
                }
                case Integer number -> {
                    output.writeByte(2);
                    output.writeInt(number);
                }
                case Long number -> {
                    output.writeByte(3);
                    output.writeLong(number);
                }
                case Float number -> {
                    output.writeByte(4);
                    output.writeFloat(number);
                }
                case Double number -> {
                    output.writeByte(5);
                    output.writeDouble(number);
                }
                case Boolean bool -> {
                    output.writeByte(6);
                    output.writeBoolean(bool);
                }
                case Byte number -> {
                    output.writeByte(7);
                    output.writeByte(number);
                }
                case Character character -> {
                    output.writeByte(8);
                    output.writeChar(character);
                }
                case Short number -> {
                    output.writeByte(9);
                    output.writeShort(number);
                }
                case org.objectweb.asm.Type type -> {
                    output.writeByte(10);
                    string(type.getDescriptor());
                }
                case List<?> list -> {
                    output.writeByte(11);
                    output.writeInt(list.size());
                    for (Object element : list) {
                        annotationValue(element);
                    }
                }
                case AnnotationStub annotation -> {
                    output.writeByte(12);
                    annotations(List.of(annotation));
                }
                default -> throw new IOException("Unsupported annotation value " + value.getClass().getName());
            }
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }

    private static final class StubInput implements Closeable {
        private final DataInputStream input;
        private final List<String> strings = new ArrayList<>();

        private StubInput(DataInputStream input) {
            this.input = input;
        }

        private boolean header(Path archive, long size, long lastModified) throws IOException {
            return MAGIC.equals(input.readUTF())
                && input.readInt() == FORMAT_VERSION
                && PARSER_VERSION.equals(input.readUTF())
                && key(archive).equals(string())
                && input.readLong() == size
                && input.readLong() == lastModified;
        }

        private int count() throws IOException {
            int count = input.readInt();
            if (count < 0)
                throw new IOException("Invalid count " + count);

            return count;
        }

        private @Nullable String string() throws IOException {
            int index = input.readInt();
            if (index == -1)
                return null;

            if (index == -2) {
                byte[] bytes = new byte[count()];
                input.readFully(bytes);
                // Names recur across archives; interning lets every restored archive share one copy.
                var value = new String(bytes, StandardCharsets.UTF_8).intern();
                strings.add(value);
                return value;
            }

            if (index < 0 || index >= strings.size())
                throw new IOException("Invalid string reference " + index);

            return strings.get(index);
        }

        private ClassStub classStub() throws IOException {
            String packageName = string();
            String name = string();
            List<TypeParameter> typeParameters = typeParameters();
            Type superClass = type();
            List<Type> interfaces = types();

            int fieldCount = count();
            List<FieldStub> fields = new ArrayList<>(fieldCount);
            for (int index = 0; index < fieldCount; index++) {
                fields.add(new FieldStub(string(), type(), input.readInt(), annotations()));
            }

            int methodCount = count();
            List<MethodStub> methods = new ArrayList<>(methodCount);
            for (int index = 0; index < methodCount; index++) {
                methods.add(new MethodStub(string(), type(), parameters(), input.readInt(), types(), annotations(),
                    typeParameters()));
            }

            int constructorCount = count();
            List<ConstructorStub> constructors = new ArrayList<>(constructorCount);
            for (int index = 0; index < constructorCount; index++) {
                constructors.add(new ConstructorStub(parameters(), input.readInt(), types(), annotations(),
                    typeParameters()));
            }

            int modifiers = input.readInt();
            List<AnnotationStub> annotations = annotations();
            return new ClassStub(packageName, name, typeParameters, superClass, interfaces,
                immutable(fields), immutable(methods), immutable(constructors), modifiers, annotations, string());
        }

        private List<Parameter> parameters() throws IOException {
            int count = count();
            List<Parameter> parameters = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                parameters.add(new Parameter(string(), type(), annotations()));
            }

            return immutable(parameters);
        }

        private List<TypeParameter> typeParameters() throws IOException {
            int count = count();
            List<TypeParameter> typeParameters = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                typeParameters.add(new TypeParameter(string(), types()));
            }

            return immutable(typeParameters);
        }

        private List<Type> types() throws IOException {
            int count = count();
            List<Type> types = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                types.add(type());
            }

            return immutable(types);
        }

        private @Nullable Type type() throws IOException {
            byte tag = input.readByte();
            return switch (tag) {
                case 0 -> null;
                case 1 -> new Type.ClassType(string(), types());
                case 2 -> Type.PrimitiveType.of(string());
                case 3 -> new Type.ArrayType(type());
                case 4 -> new Type.TypeVariable(string());
                case 5 -> new Type.WildcardType(type(), input.readBoolean());
                default -> throw new IOException("Invalid type tag " + tag);
            };
        }

        private List<AnnotationStub> annotations() throws IOException {
            int count = count();
            List<AnnotationStub> annotations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String name = string();
                int valueCount = count();
                Map<String, Object> values = new HashMap<>();
                for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                    values.put(string(), annotationValue());
                }

                annotations.add(new AnnotationStub(name, Map.copyOf(values)));
            }

            return immutable(annotations);
        }

        /** Most stub lists are empty; sharing {@link List#of()} for them keeps restored stubs small. */
        private static <T> List<T> immutable(List<T> values) {
            return values.isEmpty() ? List.of() : values.stream().toList();
        }

        private Object annotationValue() throws IOException {
            byte tag = input.readByte();
            return switch (tag) {
                case 1 -> string();
                case 2 -> input.readInt();
                case 3 -> input.readLong();
                case 4 -> input.readFloat();
                case 5 -> input.readDouble();
                case 6 -> input.readBoolean();
                case 7 -> input.readByte();
                case 8 -> input.readChar();
                case 9 -> input.readShort();
                case 10 -> org.objectweb.asm.Type.getType(string());
                case 11 -> {
                    int count = count();
                    List<Object> elements = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        elements.add(annotationValue());
                    }
                    yield List.copyOf(elements);
                }
                case 12 -> {
                    List<AnnotationStub> annotations = annotations();
                    if (annotations.size() != 1)
                        throw new IOException("Invalid nested annotation");
                    yield annotations.getFirst();
                }
                default -> throw new IOException("Invalid annotation value tag " + tag);
            };
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }
}
