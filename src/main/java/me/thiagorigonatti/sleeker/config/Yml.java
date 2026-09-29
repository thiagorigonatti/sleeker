/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class Yml {

    public Map<String, Object> read(File settings) {

        Yaml yaml = new Yaml();

        try (InputStream inputStream = new FileInputStream(settings)) {

            Map<String, Object> obj = yaml.load(inputStream);

            YmlParser BBYmlParser = new YmlParser();
            BBYmlParser.parse(obj, null);

            return BBYmlParser.getParsed();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
