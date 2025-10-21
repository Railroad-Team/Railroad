package dev.railroadide.core.project.minecraft.pistonmeta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;
import java.util.function.BiConsumer;

public class CLIArguments {
    private final List<Argument> arguments = new ArrayList<>();

    public CLIArguments(List<Argument> arguments) {
        this.arguments.addAll(arguments);
    }

    public List<Argument> arguments() {
        return this.arguments;
    }

    public Optional<Argument> getArgument(String name) {
        return this.arguments.stream()
            .filter(argument -> argument.name().equals(name))
            .findFirst();
    }

    private static List<Argument> readKeyValues(JsonArray array, BiConsumer<List<Argument>, JsonElement> notPrimitiveHandler) {
        List<Argument> arguments = new ArrayList<>();
        if (array == null || array.isEmpty()) {
            return arguments;
        }

        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (!primitive.isString()) {
                    throw new IllegalArgumentException("Argument must be a string! " + primitive);
                }

                String key = primitive.getAsString();
                if (key.startsWith("--")) {
                    key = key.substring(2);

                    if (key.contains("=")) {
                        String[] split = key.split("=");
                        if (split.length != 2) {
                            throw new IllegalArgumentException("Argument must have a key and value! " + key);
                        }

                        arguments.add(new Argument(split[0], split[1]));
                    } else {
                        if (i + 1 >= array.size()) {
                            arguments.add(new Argument(key, ""));
                            continue;
                        }

                        JsonElement next = array.get(i + 1);
                        if (!next.isJsonPrimitive()) {
                            throw new IllegalArgumentException("Argument must have a value! " + key);
                        }

                        JsonPrimitive nextPrimitive = next.getAsJsonPrimitive();
                        if (!nextPrimitive.isString()) {
                            throw new IllegalArgumentException("Argument must have a value! " + key);
                        }

                        arguments.add(new Argument(key, nextPrimitive.getAsString()));
                        i++;
                    }
                } else if (key.startsWith("-")) {
                    key = key.substring(1);

                    if (key.contains("=")) {
                        String[] split = key.split("=");
                        arguments.add(new Argument(split[0], split[1]));
                    } else {
                        if (i + 1 >= array.size()) {
                            arguments.add(new Argument(key, ""));
                            continue;
                        }

                        JsonElement next = array.get(i + 1);
                        if (!next.isJsonPrimitive()) {
                            throw new IllegalArgumentException("Argument must have a value! " + key);
                        }

                        JsonPrimitive nextPrimitive = next.getAsJsonPrimitive();
                        if (!nextPrimitive.isString()) {
                            throw new IllegalArgumentException("Argument must have a value! " + key);
                        }

                        arguments.add(new Argument(key, nextPrimitive.getAsString()));
                        i++;
                    }
                } else {
                    throw new IllegalArgumentException("Argument must start with a '-' or '--'! " + key);
                }
            } else {
                notPrimitiveHandler.accept(arguments, element);
            }
        }

        return arguments;
    }

    public static CLIArguments fromJsonArray(JsonArray array) {
        List<Argument> args = readKeyValues(array, (arguments, element) -> {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("rules")) {
                    throw new IllegalArgumentException("Argument must have rules! " + object);
                }

                JsonArray rulesJson = object.getAsJsonArray("rules");
                List<Argument.Rule> rules = new ArrayList<>();
                for (JsonElement ruleElement : rulesJson) {
                    if (!ruleElement.isJsonObject()) {
                        throw new IllegalArgumentException("Rule must be an object! " + ruleElement);
                    }

                    JsonObject ruleObject = ruleElement.getAsJsonObject();
                    if (!ruleObject.has("action")) {
                        throw new IllegalArgumentException("Rule must have an action! " + ruleObject);
                    }

                    String actionString = ruleObject.get("action").getAsString();
                    Argument.Rule.Action action;
                    try {
                        action = Argument.Rule.Action.valueOf(actionString.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Rule action must be 'allow' or 'disallow'! " + actionString);
                    }

                    Map<String, String> os = new HashMap<>();
                    if (ruleObject.has("os")) {
                        JsonObject osObject = ruleObject.getAsJsonObject("os");
                        for (Map.Entry<String, JsonElement> entry : osObject.entrySet()) {
                            if (!entry.getValue().isJsonPrimitive()) {
                                throw new IllegalArgumentException("OS value must be a primitive! " + entry);
                            }

                            JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                            if (!primitive.isString()) {
                                throw new IllegalArgumentException("OS value must be a string! " + entry);
                            }

                            os.put(entry.getKey(), primitive.getAsString());
                        }
                    }

                    Map<String, Boolean> features = new HashMap<>();
                    if (ruleObject.has("features")) {
                        JsonObject featuresObject = ruleObject.getAsJsonObject("features");
                        for (Map.Entry<String, JsonElement> entry : featuresObject.entrySet()) {
                            if (!entry.getValue().isJsonPrimitive()) {
                                throw new IllegalArgumentException("Feature value must be a primitive! " + entry);
                            }

                            JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                            if (!primitive.isBoolean()) {
                                throw new IllegalArgumentException("Feature value must be a boolean! " + entry);
                            }

                            features.put(entry.getKey(), primitive.getAsBoolean());
                        }
                    }

                    rules.add(new Argument.Rule(action, os, features));
                }

                if (!object.has("value")) {
                    throw new IllegalArgumentException("Argument must have a value! " + object);
                }

                // can either be a string or an array of strings
                JsonElement valueElement = object.get("value");
                if (valueElement.isJsonArray()) {
                    List<Argument> valueArguments = readKeyValues(valueElement.getAsJsonArray(), (arguments1, jsonElement1) -> {
                        throw new IllegalArgumentException("Argument value must be a string! " + jsonElement1);
                    });

                    for (Argument argument : valueArguments) {
                        arguments.add(new Argument(argument.name(), argument.value(), rules));
                    }
                } else if (valueElement.isJsonPrimitive()) {
                    var valueArray = new JsonArray();
                    valueArray.add(valueElement);

                    List<Argument> valueArguments = readKeyValues(valueArray, (arguments1, jsonElement1) -> {
                        throw new IllegalArgumentException("Argument value must be a string! " + jsonElement1);
                    });

                    for (Argument argument : valueArguments) {
                        arguments.add(new Argument(argument.name(), argument.value(), rules));
                    }
                } else {
                    throw new IllegalArgumentException("Argument value must be a string! " + valueElement);
                }
            }
        });

        return new CLIArguments(args);
    }

    public static class Argument {
        private final String name;
        private final String value;
        private final List<Rule> rules = new ArrayList<>();

        public Argument(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public Argument(String name, String value, List<Rule> rules) {
            this.name = name;
            this.value = value;
            this.rules.addAll(rules);
        }

        public String name() {
            return this.name;
        }

        public String value() {
            return this.value;
        }

        public List<Rule> rules() {
            return this.rules;
        }

        public static class Rule {
            private final Action action;
            private final Map<String, String> os = new HashMap<>();
            private final Map<String, Boolean> features = new HashMap<>();

            public Rule(Action action) {
                this.action = action;
            }

            public Rule(Action action, Map<String, String> os, Map<String, Boolean> features) {
                this.action = action;
                this.os.putAll(os);
                this.features.putAll(features);
            }

            public Action action() {
                return this.action;
            }

            public Map<String, String> os() {
                return Map.copyOf(this.os);
            }

            public Map<String, Boolean> features() {
                return Map.copyOf(this.features);
            }

            public enum Action {
                ALLOW,
                DISALLOW
            }
        }
    }
}
