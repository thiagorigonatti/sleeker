/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;

import jakarta.validation.constraints.NotNull;

public interface HeaderAddeable {
    void addHeader(@NotNull CharSequence httpHeaderName, @NotNull CharSequence httpHeaderValue);
}
