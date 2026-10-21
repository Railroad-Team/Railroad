package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * An ordered, mutable collection of Minecraft launch arguments and their eligibility rules.
 * Parsing retains rule metadata for callers to interpret; it does not evaluate the rules.
 */
public class CLIArguments {
    private final List<Argument> arguments = new ArrayList<>();

    /**
     * Creates an argument collection by copying the supplied list entries.
     *
     * @param arguments the arguments in launch order
     */
    public CLIArguments(List<Argument> arguments) {
        this.arguments.addAll(arguments);
    }

    /**
     * Returns the backing list of launch arguments.
     *
     * @return the mutable argument list
     */
    public List<Argument> arguments() {
        return this.arguments;
    }

    /**
     * Finds the first argument whose name matches exactly.
     *
     * @param name the argument name without leading dashes
     * @return the first matching argument, or empty when none matches
     */
    public Optional<Argument> getArgument(String name) {
        return this.arguments.stream()
            .filter(argument -> argument.name().equals(name))
            .findFirst();
    }

    /**
     * Parses dashed argument names and their values, delegating nonprimitive elements to a handler.
     * Names are stored without their leading dash or double dash.
     *
     * @param array the argument array, or null for no arguments
     * @param notPrimitiveHandler the handler for rule-bearing objects or other nonprimitive values
     * @return the parsed arguments in encounter order
     * @throws IllegalArgumentException if a primitive argument has an unsupported name or value
     */
    private static List<Argument> readKeyValues(
        JsonArray array,
        BiConsumer<List<Argument>, JsonElement> notPrimitiveHandler
    ) {
        List<Argument> arguments = new ArrayList<>();
        if (array == null || array.isEmpty())
            return arguments;

        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (!primitive.isString())
                    throw new IllegalArgumentException("Argument must be a string! " + primitive);

                String key = primitive.getAsString();
                if (key.startsWith("--")) {
                    key = key.substring(2);

                    if (key.contains("=")) {
                        String[] split = key.split("=");
                        if (split.length != 2)
                            throw new IllegalArgumentException("Argument must have a key and value! " + key);

                        arguments.add(new Argument(split[0], split[1]));
                    } else {
                        if (i + 1 >= array.size()) {
                            arguments.add(new Argument(key, ""));
                            continue;
                        }

                        JsonElement next = array.get(i + 1);
                        if (!next.isJsonPrimitive())
                            throw new IllegalArgumentException("Argument must have a value! " + key);

                        JsonPrimitive nextPrimitive = next.getAsJsonPrimitive();
                        if (!nextPrimitive.isString())
                            throw new IllegalArgumentException("Argument must have a value! " + key);

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
                        if (!next.isJsonPrimitive())
                            throw new IllegalArgumentException("Argument must have a value! " + key);

                        JsonPrimitive nextPrimitive = next.getAsJsonPrimitive();
                        if (!nextPrimitive.isString())
                            throw new IllegalArgumentException("Argument must have a value! " + key);

                        arguments.add(new Argument(key, nextPrimitive.getAsString()));
                        i++;
                    }
                } else
                    throw new IllegalArgumentException("Argument must start with a '-' or '--'! " + key);
            } else {
                notPrimitiveHandler.accept(arguments, element);
            }
        }

        return arguments;
    }

    /**
     * Parses plain launch arguments and objects containing rules and argument values.
     * Rule objects retain operating system and feature constraints without evaluating them.
     *
     * @param array the launch argument array, or null for an empty collection
     * @return the parsed argument collection
     * @throws IllegalArgumentException if an argument or rule fails the parser's format checks
     */
    public static CLIArguments fromJsonArray(JsonArray array) {
        List<Argument> args = readKeyValues(array, (arguments, element) -> {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("rules"))
                    throw new IllegalArgumentException("Argument must have rules! " + object);

                JsonArray rulesJson = object.getAsJsonArray("rules");
                List<Argument.Rule> rules = new ArrayList<>();
                for (JsonElement ruleElement : rulesJson) {
                    if (!ruleElement.isJsonObject())
                        throw new IllegalArgumentException("Rule must be an object! " + ruleElement);

                    JsonObject ruleObject = ruleElement.getAsJsonObject();
                    if (!ruleObject.has("action"))
                        throw new IllegalArgumentException("Rule must have an action! " + ruleObject);

                    String actionString = ruleObject.get("action").getAsString();
                    Argument.Rule.Action action;
                    try {
                        action = Argument.Rule.Action.valueOf(actionString.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException(
                            "Rule action must be 'allow' or 'disallow'! " + actionString);
                    }

                    Map<String, String> os = new HashMap<>();
                    if (ruleObject.has("os")) {
                        JsonObject osObject = ruleObject.getAsJsonObject("os");
                        for (Map.Entry<String, JsonElement> entry : osObject.entrySet()) {
                            if (!entry.getValue().isJsonPrimitive())
                                throw new IllegalArgumentException("OS value must be a primitive! " + entry);

                            JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                            if (!primitive.isString())
                                throw new IllegalArgumentException("OS value must be a string! " + entry);

                            os.put(entry.getKey(), primitive.getAsString());
                        }
                    }

                    Map<String, Boolean> features = new HashMap<>();
                    if (ruleObject.has("features")) {
                        JsonObject featuresObject = ruleObject.getAsJsonObject("features");
                        for (Map.Entry<String, JsonElement> entry : featuresObject.entrySet()) {
                            if (!entry.getValue().isJsonPrimitive())
                                throw new IllegalArgumentException("Feature value must be a primitive! " + entry);

                            JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                            if (!primitive.isBoolean())
                                throw new IllegalArgumentException("Feature value must be a boolean! " + entry);

                            features.put(entry.getKey(), primitive.getAsBoolean());
                        }
                    }

                    rules.add(new Argument.Rule(action, os, features));
                }

                if (!object.has("value"))
                    throw new IllegalArgumentException("Argument must have a value! " + object);

                // can either be a string or an array of strings
                JsonElement valueElement = object.get("value");
                if (valueElement.isJsonArray()) {
                    List<Argument> valueArguments = readKeyValues(valueElement.getAsJsonArray(),
                        (arguments1, jsonElement1) -> {
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
                } else
                    throw new IllegalArgumentException("Argument value must be a string! " + valueElement);
            }
        });

        return new CLIArguments(args);
    }

    /**
     * A launch argument name and value with zero or more eligibility rules.
     */
    public static class Argument {
        private final String name;
        private final String value;
        private final List<Rule> rules = new ArrayList<>();

        /**
         * Creates an argument without eligibility rules.
         *
         * @param name the argument name without leading dashes
         * @param value the argument value, or an empty string for a valueless argument
         */
        public Argument(String name, String value) {
            this.name = name;
            this.value = value;
        }

        /**
         * Creates an argument and copies the supplied eligibility rule entries.
         *
         * @param name the argument name without leading dashes
         * @param value the argument value, or an empty string for a valueless argument
         * @param rules the eligibility rules to retain in order
         */
        public Argument(String name, String value, List<Rule> rules) {
            this.name = name;
            this.value = value;
            this.rules.addAll(rules);
        }

        /**
         * Returns the argument name.
         *
         * @return the name without leading dashes
         */
        public String name() {
            return this.name;
        }

        /**
         * Returns the argument value.
         *
         * @return the stored argument value
         */
        public String value() {
            return this.value;
        }

        /**
         * Returns the backing list of eligibility rules.
         *
         * @return the mutable rule list
         */
        public List<Rule> rules() {
            return this.rules;
        }

        /**
         * An argument eligibility action with operating system and feature constraints.
         */
        public static class Rule {
            private final Action action;
            private final Map<String, String> os = new HashMap<>();
            private final Map<String, Boolean> features = new HashMap<>();

            /**
             * Creates a rule without operating system or feature constraints.
             *
             * @param action the action to apply when the rule matches
             */
            public Rule(Action action) {
                this.action = action;
            }

            /**
             * Creates a rule by copying its operating system and feature constraints.
             *
             * @param action the action to apply when the rule matches
             * @param os the operating system properties required by this rule
             * @param features the feature flags required by this rule
             */
            public Rule(Action action, Map<String, String> os, Map<String, Boolean> features) {
                this.action = action;
                this.os.putAll(os);
                this.features.putAll(features);
            }

            /**
             * Returns the action associated with this rule.
             *
             * @return the eligibility action
             */
            public Action action() {
                return this.action;
            }

            /**
             * Returns a snapshot of the operating system constraints.
             *
             * @return an immutable map of operating system property names to required values
             */
            public Map<String, String> os() {
                return Map.copyOf(this.os);
            }

            /**
             * Returns a snapshot of the feature constraints.
             *
             * @return an immutable map of feature names to required boolean values
             */
            public Map<String, Boolean> features() {
                return Map.copyOf(this.features);
            }

            /**
             * Specifies whether a matching rule enables or disables an argument.
             */
            public enum Action {
                /**
                 * Enables the argument when the rule matches.
                 */
                ALLOW,
                /**
                 * Disables the argument when the rule matches.
                 */
                DISALLOW
            }
        }
    }
}
