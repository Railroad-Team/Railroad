# Captured Java source analysis

PR #653 adapts the Java providers to the DocumentStore contract merged in #652.
The scope is captured Java **source** dependencies. It does not introduce a new
module-resolution model or binary-artifact snapshot infrastructure.

## Request inputs and compatibility

Workspace overloads of diagnostics, completion and signature help derive their Java
source declarations and bodies from the supplied capture, including unsaved overlays
and closed files. They do not query the live project source index. Legacy document-only
provider overloads retain their existing behavior.

Source types take precedence over older compiled types of the same name. Completion
also resolves inherited members from captured source bodies rather than reopening disk.
Weak workspace/document caches reuse extraction without retaining an unbounded source
version history. Classpath ordering comes from the captured configuration.

The adapter preserves the existing project-wide source-index scope. Per-module visibility,
per-module toolchain selection and module dependency graphs are deferred. When JDT staging
finds different source bodies with the same package and filename, it omits that ambiguous
unit rather than letting map iteration choose one module's declaration. Such a dependency
may have no signature result until module-aware analysis is implemented.

External dependency/toolchain files are assumed stable during a request, as in the legacy
pipeline. Semantic diagnostics/completion cache parsed library and JDK symbols; JDT uses
the configured binary paths. Detecting in-place JAR/JDK replacement and pinning external
artifact bytes across features are deferred. A captured Java source workspace is not an
atomic snapshot of the entire compiler environment. Language-neutral binary document
snapshots in DocumentStore remain unchanged.

## Signature staging and lifecycle

JDT receives immutable captured dependency trees. Its current compilation unit is supplied
directly from the captured buffer, so editing that unit does not rewrite its dependencies.
A four-entry source-only LRU cache reuses those trees. Evicted trees remain readable while
a parser holds a lease; the final reader removes an evicted tree. Process shutdown retires
all entries. New-tree failures and cancellation clean partially materialized directories.
Interrupted workers stop staging and JDT receives a cancellation-aware progress monitor.

No binary archive cache or new module graph is introduced by this staging cache.

## Validation and profiling

Focused regressions cover retained unsaved dependencies, inheritance after disk deletion,
source-over-binary precedence, classpath ordering, ambiguous source-unit handling,
interruption, materialization failure, source-tree reuse and eviction with active readers.
Existing editor stale-result and document-only provider tests remain applicable.

Run the standalone profile against Railroad's actual Java sources with:

```powershell
.\gradlew.bat -I validation/java-workspace-profile.gradle profileCapturedJavaSignatures --console=plain
```

The harness verifies a signature resolved from another captured Railroad source file,
then measures repeated requests and requests after editing the current buffer. It uses
Gradle's test runtime classpath and a 4 GiB heap. Timings are observations, not CI thresholds.
Recorded measurements and full-test evidence are in `java-workspace-analysis.json`.

The pre-existing self-analysis baseline is independent of this source adapter. This PR
must not add diagnostics to that baseline and does not claim to repair its existing errors.
