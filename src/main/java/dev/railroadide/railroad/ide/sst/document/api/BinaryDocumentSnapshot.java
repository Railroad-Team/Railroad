package dev.railroadide.railroad.ide.sst.document.api;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable snapshot of binary document content.
 * <p>
 * Input bytes are copied during construction. {@link #bytes()} returns a fresh read-only
 * buffer on every call, so neither content nor shared buffer position can be mutated by
 * consumers.
 */
public final class BinaryDocumentSnapshot implements DocumentSnapshot {
    private final DocumentId id;
    private final DocumentUri uri;
    private final DocumentVersion version;
    private final String languageId;
    private final byte[] content;

    /**
     * Creates an immutable binary document revision from a byte array.
     *
     * @param id stable logical document identity
     * @param uri physical or virtual document location
     * @param version content revision
     * @param languageId provider-defined non-blank language identity
     * @param content complete binary content; copied during construction
     */
    public BinaryDocumentSnapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        String languageId,
        byte[] content) {
        this.id = Objects.requireNonNull(id, "id");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.version = Objects.requireNonNull(version, "version");
        this.languageId = requireLanguageId(languageId);
        content = Objects.requireNonNull(content, "content");
        this.content = Arrays.copyOf(content, content.length);
    }

    /**
     * Creates an immutable binary document revision from the remaining bytes of a buffer.
     * The source buffer's position is not modified.
     *
     * @param id stable logical document identity
     * @param uri physical or virtual document location
     * @param version content revision
     * @param languageId provider-defined non-blank language identity
     * @param content buffer whose remaining bytes are copied
     */
    public BinaryDocumentSnapshot(
        DocumentId id,
        DocumentUri uri,
        DocumentVersion version,
        String languageId,
        ByteBuffer content) {
        this(id, uri, version, languageId, copyRemaining(content));
    }

    @Override
    public DocumentId id() {
        return id;
    }

    @Override
    public DocumentUri uri() {
        return uri;
    }

    @Override
    public DocumentVersion version() {
        return version;
    }

    @Override
    public String languageId() {
        return languageId;
    }

    /**
     * Returns a fresh read-only view of the complete binary content.
     *
     * @return read-only buffer positioned at zero
     */
    public ByteBuffer bytes() {
        return ByteBuffer.wrap(content).asReadOnlyBuffer();
    }

    /**
     * Returns an independent copy of the complete binary content.
     *
     * @return copied byte array
     */
    public byte[] copyBytes() {
        return Arrays.copyOf(content, content.length);
    }

    /**
     * Returns the content size in bytes.
     *
     * @return byte count
     */
    public int size() {
        return content.length;
    }

    private static byte[] copyRemaining(ByteBuffer content) {
        ByteBuffer source = Objects.requireNonNull(content, "content").duplicate();
        byte[] copy = new byte[source.remaining()];
        source.get(copy);
        return copy;
    }

    private static String requireLanguageId(String languageId) {
        languageId = Objects.requireNonNull(languageId, "languageId");
        if (languageId.isBlank())
            throw new IllegalArgumentException("languageId cannot be blank");
        return languageId;
    }
}
