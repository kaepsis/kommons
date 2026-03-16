package dev.kaepsis.onlyconfig.config.parser.impl;

import dev.kaepsis.onlyconfig.config.parser.IConfigParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class YamlParser implements IConfigParser {

    private final Yaml yaml;

    public YamlParser() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        this.yaml = new Yaml(options);
    }

    @Override
    public Map<String, Object> load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object data = yaml.load(in);
            if (data == null) return new LinkedHashMap<>();
            if (!(data instanceof Map)) {
                throw new IllegalStateException("Root YAML is not a map");
            }
            return castMap(data);
        }
    }

    @Override
    public Map<String, Object> load(InputStream inputStream) {
        Object data = yaml.load(inputStream);
        if (data == null) {
            return new LinkedHashMap<>();
        }
        if (!(data instanceof Map<?, ?>)) {
            throw new IllegalStateException("Root YAML is not a map");
        }
        Map<?, ?> rawMap = (Map<?, ?>) data;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalStateException("YAML map contains non-string key: " + entry.getKey());
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public void save(Path path, Map<String, Object> data) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(data, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}
