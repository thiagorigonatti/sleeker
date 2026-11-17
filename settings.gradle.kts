/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

rootProject.name = "sleeker"
if (providers.gradleProperty("includeServer").orNull != "false") {
    include("server")
}
