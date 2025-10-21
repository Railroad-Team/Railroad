package dev.railroadide.railroad.utility.network.check;

public interface NetworkCheck {
    boolean check(String address, int timeout);

    default boolean shouldLogFailures() {
        return true;
    }
}
