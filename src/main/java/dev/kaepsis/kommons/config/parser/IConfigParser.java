package dev.kaepsis.kommons.config.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

public interface IConfigParser {

    Map<String, Object> load(Path path) throws Exception;

    Map<String, Object> load(InputStream inputStream) throws IOException;

    void save(Path path, Map<String, Object> data) throws Exception;

}
