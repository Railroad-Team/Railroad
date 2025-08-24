package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.ide.sst.ast.AstNode;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record Either<L, R>(L left, R right, boolean isLeft) {
    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(value, null, true);
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(null, value, false);
    }

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

    public <T> T map(Function<? super L, ? extends T> leftMapper,
                     Function<? super R, ? extends T> rightMapper) {
        return isLeft ? leftMapper.apply(left) : rightMapper.apply(right);
    }

    @Override
    public @NotNull String toString() {
        return isLeft ? "Left(" + left + ")" : "Right(" + right + ")";
    }
}