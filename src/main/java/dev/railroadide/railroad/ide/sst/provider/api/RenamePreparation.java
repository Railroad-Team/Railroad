package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;

import java.util.Objects;

/**
 * Outcome of {@link RenameProvider#prepareRename}: either a renameable symbol or the reason the position cannot be
 * renamed.
 */
public sealed interface RenamePreparation permits RenamePreparation.Renameable, RenamePreparation.Rejected {
    /**
     * A symbol that can be renamed from the requested position.
     *
     * @param symbol stable identity of the symbol that would be renamed
     * @param kind user-facing kind of the symbol, such as {@code class} or {@code method}
     * @param name current name of the symbol
     * @param range identifier at the requested position, in the captured document revision
     */
    record Renameable(SymbolId symbol, String kind, String name, Location.TextLocation range)
        implements RenamePreparation {
        /**
         * Validates a renameable symbol.
         *
         * @param symbol stable identity of the symbol that would be renamed
         * @param kind user-facing kind of the symbol
         * @param name current name of the symbol
         * @param range identifier at the requested position
         */
        public Renameable {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(range, "range");
            if (name.isBlank())
                throw new IllegalArgumentException("Renameable symbols need a name");
        }
    }

    /**
     * A position that cannot be renamed.
     *
     * @param reason user-facing explanation
     */
    record Rejected(String reason) implements RenamePreparation {
        /**
         * Validates a rejection.
         *
         * @param reason user-facing explanation
         */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank())
                throw new IllegalArgumentException("Rejections need a reason");
        }
    }
}
