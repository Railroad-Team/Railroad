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

`DocumentId` does not replace the planned `DocumentUri`, `DocumentVersion`, or
`DocumentSnapshot` contracts. An ID answers *which logical document*; those contracts
answer *where it is addressed* and *which immutable revision is being observed*.

## Ownership and lifecycle

A workspace or document service owns a `DocumentIdentityRegistry`. The owner allocates
IDs when documents enter its model and passes those IDs into syntax, semantic, indexing,
and feature work. Consumers borrow IDs and must not derive replacements from a path or
content hash.

The registry can issue an unbound ID for any virtual, generated, in-memory, text, or
binary document. Physical documents use `getOrCreate(Path)`. `associate` attaches an
existing pathless identity to a physical location, while `rebind` preserves identity
when the owner moves or renames a document. `release` forgets all path associations when
the logical document leaves the workspace; it does not invalidate or recycle the ID.
Old immutable snapshots and asynchronous results may therefore safely retain it.

IDs are process-independent values and have a canonical UUID string representation.
Workspaces that require identity across restarts must persist both that value and the
workspace-specific origin association. The core registry intentionally does not choose
a persistence policy for its owner.

## Physical path resolution and failure behavior

The registry retains normalized absolute and real path spellings. Existing paths are
also compared with `Files.isSameFile`, allowing symbolic links, hard links, and other
filesystem aliases to converge when the platform can establish equivalence. A missing
path is represented by its normalized absolute spelling until it exists or is rebound.

Invalid null or malformed IDs fail immediately. Resolving or comparing an existing path
may throw `UncheckedIOException` when the filesystem cannot answer reliably. Associating
one physical location with two different IDs, or rebinding an identity from a location
it does not own, throws `IllegalStateException`; the registry never silently merges two
logical documents.

## Immutability and thread safety

`DocumentId` is an immutable value type and can be shared freely between workers.
`DocumentIdentityRegistry` serializes allocation and association operations, so concurrent
resolution of the same physical document returns one ID. Future document snapshots must
also be immutable, but snapshot content and versioning are outside this contract.

## Compatibility with the current SST

`SyntaxTree` now carries a `DocumentId`, and semantic models inherit it through their
syntax tree. Java parser overloads accept an owner-supplied ID. Incremental parsing keeps
the previous tree's ID for both incremental and full-reparse paths.

Existing parser overloads and the original `SyntaxTree(SyntaxNode)` constructor remain
source compatible. Because those entry points have no owning document context, they
allocate a fresh anonymous ID for each independently created tree. Callers that correlate
results across parses must migrate to the explicit-ID overloads. Path-keyed project index
and raw-string feature-provider migration remains part of the later snapshot and
compatibility roadmap items.
