package com.sumirelabs.kita.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z_][A-Z_0-9]*)(?::-([^}]*))?}");
    private ConfigLoader() {}

    public static KitaConfig load(Path path) throws Exception {
        // Substitute after YAML parsing so punctuation/newlines in secrets cannot alter the structure.
        var mapper = new ObjectMapper(new YAMLFactory());
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

    private static void expand(com.fasterxml.jackson.databind.JsonNode node, Map<String, String> environment) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.properties().forEach(entry -> {
                if (entry.getValue().isTextual()) object.put(entry.getKey(), substitute(entry.getValue().asText(), environment));
                else expand(entry.getValue(), environment);
            });
        } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                var child = array.get(index);
                if (child.isTextual()) array.set(index, new com.fasterxml.jackson.databind.node.TextNode(substitute(child.asText(), environment)));
                else expand(child, environment);
            }
        }
    }
}
