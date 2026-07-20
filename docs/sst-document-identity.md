# SST document identity contract

`DocumentId` is the stable identity of one logical document. It is deliberately
independent of filesystem path spelling, URI, content, language, and revision.

## Identity rules

- Renaming, moving, saving, or editing a document retains its ID.
- A copied document receives a new ID, even when its content is identical.
- Deleting a document ends its registry lifecycle. If a new document later uses the
  same path, the owner must release the old association before resolving the new one.
- Separate in-memory, generated, decompiled, archive-entry, text, and binary documents
  receive separate IDs. None of these categories requires a filesystem path.
- Equality and hashing use only the opaque UUID value. Paths and content never
  participate in `DocumentId` equality.

`DocumentId` does not replace `DocumentUri` or the planned `DocumentVersion` and
`DocumentSnapshot` contracts. An ID answers *which logical document*, a URI answers
*where it is addressed*, and a version identifies *which immutable revision is being
observed*.

## URI rules

`DocumentUri` wraps an absolute `URI`. It supports physical `file` URIs, hierarchical
provider schemes such as `memory:/scratch/Main.java` or
`generated:/build/Generated.java`, and opaque schemes such as
`jar:file:///library.jar!/pkg/Type.class`. Providers own the meaning and lifecycle of
their virtual schemes.

URI equality identifies the same address, not necessarily the same logical document.
Physical aliases can have different URI spellings and still resolve to one `DocumentId`;
conversely, a document can acquire a new URI after a rename while retaining its ID.
Fragments, queries, archive-entry syntax, and provider-specific path structure remain
part of the URI and are not interpreted by the neutral contract.

`DocumentUri.fromPath` produces an absolute normalized file URI. `DocumentUri.virtual`
encodes a provider scheme and virtual path without consulting the filesystem. Only file
URIs can be converted to `Path`; callers must branch on the URI scheme instead of
assuming all documents are physical.

## Ownership and lifecycle

A workspace or document service owns a `DocumentIdentityRegistry`. The owner allocates
IDs when documents enter its model and passes those IDs into syntax, semantic, indexing,
and feature work. Consumers borrow IDs and must not derive replacements from a path or
content hash.

The registry can issue an unbound ID for any virtual, generated, in-memory, text, or
binary document. Documents with locations use `getOrCreate(DocumentUri)`, with a `Path`
overload retained for physical documents. `associate` attaches an existing pathless
identity to a URI, while `rebind` preserves identity when the owner moves or renames a
document. `release` forgets all URI and path associations when the logical document
leaves the workspace; it does not invalidate or recycle the ID. Old immutable snapshots
and asynchronous results may therefore safely retain it.

IDs are process-independent values and have a canonical UUID string representation.
Workspaces that require identity across restarts must persist both that value and the
workspace-specific origin association. The core registry intentionally does not choose
a persistence policy for its owner.

## Physical path resolution and failure behavior

The registry retains normalized absolute and real path spellings. Existing paths are
also compared with `Files.isSameFile`, allowing symbolic links, hard links, and other
filesystem aliases to converge when the platform can establish equivalence. A missing
path is represented by its normalized absolute spelling until it exists or is rebound.

Invalid null, malformed, blank, or relative document URIs fail immediately. Resolving or
comparing an existing path may throw `UncheckedIOException` when the filesystem cannot
answer reliably. Associating one URI with two different IDs, or rebinding an identity
from a URI it does not own, throws `IllegalStateException`; the registry never silently
merges two logical documents.

## Immutability and thread safety

`DocumentId` is an immutable value type and can be shared freely between workers.
`DocumentIdentityRegistry` serializes allocation and association operations, so concurrent
resolution of the same physical document returns one ID. Future document snapshots must
also be immutable, but snapshot content and versioning are outside this contract.

## Compatibility with the current SST

`SyntaxTree` now carries both `DocumentId` and `DocumentUri`, and semantic models inherit
them through their syntax tree. Java parser overloads accept owner-supplied identity and
location. Incremental parsing keeps both values for incremental and full-reparse paths.

Existing parser overloads and the original `SyntaxTree(SyntaxNode)` constructor remain
source compatible. Because those entry points have no owning document context, they
allocate a fresh anonymous ID and matching in-memory URI for each independently created
tree. Callers that correlate results across parses must migrate to the explicit identity
and URI overloads. Path-keyed project index and raw-string feature-provider migration
remains part of the later snapshot and compatibility roadmap items.
