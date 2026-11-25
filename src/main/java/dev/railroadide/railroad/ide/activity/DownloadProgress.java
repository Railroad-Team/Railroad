package dev.railroadide.railroad.ide.activity;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.function.LongConsumer;
import java.util.Map;

/**
 * Shows one known-length transfer at a time while a resolver downloads several artifacts concurrently.
 */
public final class DownloadProgress {
    private final ActivityProgress progress;
    private final SequencedMap<String, Transfer> transfers = new LinkedHashMap<>();
    private LongConsumer displayed;

    public DownloadProgress(ActivityProgress progress) {
        this.progress = progress;
    }

    /**
     * A negative total preserves a length supplied earlier for the same URL.
     */
    public synchronized void update(String url, long completed, long total) {
        Transfer transfer = transfers.get(url);
        if (transfer == null && total <= 0)
            return;

        if (transfer == null) {
            transfer = new Transfer();
            transfers.put(url, transfer);
        }

        boolean resized = total > 0 && total != transfer.total;
        if (resized) {
            transfer.total = total;
        }

        transfer.completed = Math.clamp(Math.max(transfer.completed, completed), 0, transfer.total);
        if (transfers.firstEntry().getValue() == transfer) {
            if (resized || displayed == null) {
                show(url, transfer);
            } else {
                displayed.accept(transfer.completed);
            }
        }
    }

    public synchronized void finish(String url) {
        if (transfers.isEmpty())
            return;

        boolean visible = transfers.firstEntry().getKey().equals(url);
        transfers.remove(url);
        if (visible) {
            Map.Entry<String, Transfer> next = transfers.firstEntry();
            if (next == null) {
                displayed = null;
                progress.indeterminate("");
            } else {
                show(next.getKey(), next.getValue());
            }
        }
    }

    private void show(String url, Transfer transfer) {
        String name = "";
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                name = path.substring(path.lastIndexOf('/') + 1);
            }
        } catch (IllegalArgumentException _) {
        }

        displayed = progress.begin(name, ActivityProgress.Unit.BYTES, transfer.completed, transfer.total);
    }

    private static final class Transfer {
        private long completed;
        private long total;
    }
}
