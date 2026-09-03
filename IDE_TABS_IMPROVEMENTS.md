# IDE Tab Improvements

The tab work should begin with correctness, then interaction features. Right now the tab UI, document state, and editor lifecycle are split across several classes, so adding context-menu handlers directly would compound existing problems.

## P0: Correctness and Architecture

- [x] Introduce an `EditorTabManager` as the single owner of opening, selecting, moving, pinning, restoring, and closing document tabs.
- [x] Create an `EditorTab` model containing document identity, path, editor/content, pinned state, preview state, dirty/save status, group, and ordering.
- [x] Move file-opening logic out of `ProjectExplorerPane.openFile()`. The existing TODO already identifies this problem.
- [x] Make `IDEStateService` authoritative. The explorer currently publishes document events directly without updating its `openDocuments` map.
- [x] Publish `OPENED`, `ACTIVATED`, `DEACTIVATED`, and `CLOSED` exactly once through the manager.
- [x] Fix the first-opened document being non-closable. It inherits `setClosable(false)` when it replaces the Welcome tab.
- [x] Restore actual editor tabs on project startup. Currently paths are loaded into state, but no corresponding tabs are created.
- [x] Persist tab order, pinned state, active tab, preview tab, and editor group, not merely the selected tab ID.
- [x] Preserve deterministic order by replacing the `HashMap` backing open documents with ordered state.
- [x] Centralize selection handling. Per-tab selection callbacks can currently clear `activeEditor` after another tab has already selected itself.
- [x] Ensure tab contents are disposed exactly once when bulk closing, moving, detaching, or shutting down.
- [x] Use a proper `DocumentIdentity` that handles normalized paths, Windows case differences, symlinks, renamed files, and non-filesystem documents.

## P0: Saving and Data Safety

- [x] Flush and await pending saves before closing. `TextEditorPane.close()` currently cancels the pending save, so closing within the 400 ms delay can lose recent edits.
- [x] Expose observable `dirty`, `saving`, `saved`, and `saveFailed` states.
- [x] Wire up Save, Save As, and Save All; the current menu items are constructed but have no actions.
- [x] On close, either complete autosave or show one consolidated Save/Discard/Cancel dialog for unsaved or failed documents.
- [x] Never silently discard a tab whose last save failed.
- [ ] Handle external changes with Reload/Keep Mine/Compare instead of ignoring them while dirty.
- [x] Handle deletion and rename without clearing the editor silently or leaving stale tab IDs.

## P1: Tab Context Menu

Every document tab should provide:

- [x] Pin / Unpin
- [x] Close
- [x] Close Others
- [x] Close Tabs to the Right
- [x] Close Tabs to the Left
- [x] Close All
- [x] Close Unmodified Tabs
- [x] Close All Unpinned
- [x] Close Saved Tabs
- [x] Reopen Closed Tab
- [x] Copy Absolute Path
- [x] Copy Project-Relative Path
- [x] Reveal in File Explorer
- [x] Open Containing Folder in Terminal
- [x] Move to Next/Previous Editor Group
- [x] Split Right / Split Down
- [x] Move to New Window

Bulk actions should protect pinned tabs by default and operate relative to the tab that was right-clicked, not whichever tab happens to be selected.

## P1: Pinning and Preview

- [x] Keep pinned tabs in a compact section on the left.
- [x] Hide their close buttons and display a pin/file icon.
- [x] Prevent unpinned tabs from being dragged into the pinned section.
- [x] Exempt pinned tabs from Close Others, Close All, and automatic eviction.
- [x] Persist pins per project.
- [ ] Add an optional preview-tab workflow: single-click opens one temporary italic tab; double-clicking, editing, or pinning makes it permanent.
- [ ] Reuse the preview slot when navigating files, search results, diagnostics, or Git changes.

## P1: Mouse and Keyboard

- [x] Middle-click closes a tab.
- [x] `Alt+Click` on a tab closes other tabs.
- [x] Drag reorders tabs reliably and auto-scrolls near strip edges.
- [x] Mouse-wheel scrolls overflowing tab strips.
- [x] `Ctrl/Cmd+W`: close active tab.
- [x] `Ctrl/Cmd+Shift+T`: reopen last closed tab.
- [x] Add next/previous tab, move tab left/right, pin tab, and close-other-tabs keybind commands.
- [x] Support `Ctrl/Cmd+1` through `8` and `9` for the last tab.
- [x] Restore editor focus and caret after keyboard switching.

## P1: Overflow and Visual Quality

- [x] Keep the selected tab visible when tabs overflow.
- [x] Add a searchable all-tabs dropdown showing icons, paths, dirty state, and pin state.
- [x] Disambiguate duplicate filenames using parent-path suffixes.
- [x] Show full path, language, save state, and useful metadata in tooltips.
- [x] Display language/file icons.
- [x] Use a dirty dot, saving indicator, and save-error marker without changing tab width.
- [ ] Make selected, hovered, pinned, preview, and attention states distinct in both themes.
- [x] Keep close buttons spatially stable so closing several adjacent tabs does not move the pointer target.
- [x] Return to a proper empty editor state when the last document closes instead of leaving a mutated Welcome tab.

## P2: Editor Groups and Sessions

- [x] Support horizontal and vertical split editor groups.
- [x] Allow dragging tabs between groups and detaching/redocking into windows.
- [ ] Share one document buffer between multiple views of the same file.
- [x] Persist groups, dividers, tab placement, selections, caret positions, folds, and scroll positions.
- [x] Maintain per-workspace-mode tab sets and navigation history.
- [x] Add a configurable tab limit with least-recently-used eviction of clean, unpinned tabs.
- [x] Keep a bounded recently closed stack.
- [ ] Offer **Synchronize Project Explorer with Active Tab**.
- [ ] Give tool tabs separate semantics: Show/Hide, Move Dock, Detach, and Reset Position rather than document close commands.

## Recommended Implementation Order

The first implementation slice should cover the `EditorTabManager` and model, state/event synchronization, and safe closing. The complete context menu and pinning should follow. This establishes the contract that preview tabs, overflow handling, editor groups, session restoration, and other advanced behavior can build on.
