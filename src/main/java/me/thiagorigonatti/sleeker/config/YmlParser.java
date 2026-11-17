/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.config;

import java.util.HashMap;
import java.util.Map;

public class YmlParser {

    public final Map<String, Object> toBeFilled = new HashMap<>();

    public Map<String, Object> getParsed() {
        return toBeFilled;
    }

    public void parse(final Map<?, ?> obj, final String parentKey) {

        for (final Map.Entry<?, ?> entry : obj.entrySet()) {

            if (entry.getValue() != null && entry.getValue() instanceof final Map<?, ?> map)

                parse(map, (parentKey == null ? "" : parentKey + ".") + entry.getKey());

            else

                toBeFilled.put((parentKey == null ? "" : parentKey + ".") + entry.getKey(), entry.getValue());

        }
    }
}

