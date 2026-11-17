/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class Yml {

    public Map<String, Object> read(final InputStream inputStream) {

        final Yaml yaml = new Yaml();

        final Map<String, Object> obj = yaml.load(inputStream);

        final YmlParser BBYmlParser = new YmlParser();
        BBYmlParser.parse(obj, null);

        return BBYmlParser.getParsed();
    }
}
