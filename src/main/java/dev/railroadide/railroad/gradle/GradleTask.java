package dev.railroadide.railroad.gradle;

import java.util.Map;

public record GradleTask(String name, String description, Map<String, String> options) {

}
