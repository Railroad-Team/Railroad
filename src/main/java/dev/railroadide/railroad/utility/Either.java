package dev.railroadide.railroad.utility;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A simple implementation of the Either type, which can hold either a left value or a right value.
 *
 * @param left The left value.
 * @param right The right value.
 * @param isLeft A boolean indicating whether the value is a left value (true) or a right value (false).
 * @param <L> The type of the left value.
 * @param <R> The type of the right value.
 */
public record Either<L, R>(L left, R right, boolean isLeft) {
    /**
     * Creates a new Either instance with a left value.
     *
     * @param value The left value.
     * @param <L> The type of the left value.
     * @param <R> The type of the right value.
     * @return A new Either instance containing the left value.
     */
    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(value, null, true);
    }

    /**
     * Creates a new Either instance with a right value.
     *
     * @param value The right value.
     * @param <L> The type of the left value.
     * @param <R> The type of the right value.
     * @return A new Either instance containing the right value.
     */
    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(null, value, false);
    }

    /**
     * Checks if the Either instance contains a left value.
     *
     * @return true if the instance contains a left value, false otherwise.
     */
    public boolean isLeft() {
        return isLeft;
    }

    /**
     * Checks if the Either instance contains a right value.
     *
     * @return true if the instance contains a right value, false otherwise.
     */
    public boolean isRight() {
        return !isLeft;
    }

    @Override
    public L left() {
        if (!isLeft)
            throw new IllegalStateException("Not a Left value");

        return left;
    }

    @Override
    public R right() {
        if (isLeft)
            throw new IllegalStateException("Not a Right value");

        return right;
    }

    /**
     * Maps the value contained in the Either instance to a new value using the provided mapping functions.
     *
     * @param leftMapper The function to apply if the instance contains a left value.
     * @param rightMapper The function to apply if the instance contains a right value.
     * @param <T> The type of the resulting value after mapping.
     * @return The mapped value.
     */
    public <T> T map(
        Function<? super L, ? extends T> leftMapper,
        Function<? super R, ? extends T> rightMapper
    ) {
        return isLeft ? leftMapper.apply(left) : rightMapper.apply(right);
    }

    @Override
    public @NotNull String toString() {
        return isLeft ? "Left(" + left + ")" : "Right(" + right + ")";
    }
}
