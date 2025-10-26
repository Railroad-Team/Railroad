package dev.railroadide.railroad.java;

import dev.railroadide.railroad.utility.JavaVersion;

public record JDK(String path, String name, JavaVersion version) {

    @Override
    public String toString() {
        return "JDK{" +
            "path='" + path + '\'' +
            ", name='" + name + '\'' +
            ", version=" + version +
            '}';
    }
}
