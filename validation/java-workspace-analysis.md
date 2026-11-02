# Java workspace analysis

Depends on upstream PR #652. This follow-up separates the Java provider implementation
from DocumentStore lifecycle integration.

## Request inputs

The workspace overloads of diagnostics, completion and signature help obtain their
Java dependencies from one captured workspace. They do not query the current project
model or the live project language index. Legacy document-only overloads retain their
existing behavior for provider compatibility.

`JavaWorkspaceAnalysisProvider` extracts source declarations from the current Java text
snapshots in the capture, including unsaved overlays and closed files. It retains those
source bodies so inherited-member completion cannot fall back to a newer disk file.
Weak workspace/document caches share extraction across editors without retaining an
unbounded history of source bodies.

Classpath order and JDK selection come from the captured configuration. Configured
project compiler-output directories are excluded because their class files can describe
newer or older content than the captured sources. Immutable library/JDK indexes are
pinned for the configuration. These external binary artifacts are read when a
configuration is first analyzed; this is not an atomic snapshot of external JAR/JDK
bytes at `DocumentStore.capture()`. Replacing an artifact at the same path requires a
new configuration revision. Module selection retains the existing project-wide index
scope; per-module dependency visibility is a separate concern.

JDT signature help uses a temporary source tree containing only captured Java source
bodies. It is removed in `finally`, including when parsing fails. JDT receives the
captured dependency paths and configured JDK, and no live project source roots.
Temporary source staging is deliberately request scoped; large workspaces should be
profiled before introducing a shared staging lifecycle.

## Regression coverage

- Delay completion until after dependencies have changed and their disk file has been
  deleted. Resolve inherited members from the original unsaved source capture.
- Change a dependency after capture. Diagnostics and JDT signature help resolve its
  original signature; a new capture sees the changed dependency.
- Change classpath ordering before starting analysis of an old capture. Old and new
  requests preserve their respective binary precedence.
- Run the existing provider and editor snapshot tests to retain document-only provider
  compatibility and stale-result rejection.

The DocumentStore PR's pre-existing self-analysis baseline remains independent of this
follow-up. Neither PR closes #188's zero-error self-analysis criterion.
