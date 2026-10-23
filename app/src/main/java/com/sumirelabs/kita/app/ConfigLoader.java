package com.sumirelabs.kita.app;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z_0-9]*)(?::-([^}]*))?}");
    private ConfigLoader() {}

    public static KitaConfig load(Path path) throws Exception {
        // Substitute after YAML parsing so punctuation/newlines in secrets cannot alter the structure.
        var mapper = YAMLMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        var tree = mapper.readTree(Files.readString(path));
        expand(tree, System.getenv());
        var config = mapper.treeToValue(tree, KitaConfig.class);
        config.validate();
        return config;
    }

    static String substitute(String text, Map<String, String> environment) {
        var matcher = VARIABLE.matcher(text);
        return matcher.replaceAll(match -> {
            var value = environment.get(match.group(1));
            if (value == null) value = match.group(2);
            if (value == null) throw new IllegalArgumentException("Missing environment variable: " + match.group(1));
            return java.util.regex.Matcher.quoteReplacement(value);
        });
    }

    private static void expand(tools.jackson.databind.JsonNode node, Map<String, String> environment) {
        if (node instanceof tools.jackson.databind.node.ObjectNode object) {
            object.properties().forEach(entry -> {
                if (entry.getValue().isString()) object.put(entry.getKey(), substitute(entry.getValue().asString(), environment));
                else expand(entry.getValue(), environment);
            });
        } else if (node instanceof tools.jackson.databind.node.ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                var child = array.get(index);
                if (child.isString()) array.set(index, new tools.jackson.databind.node.StringNode(substitute(child.asString(), environment)));
                else expand(child, environment);
            }
        }
    }
}
