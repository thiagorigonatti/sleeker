/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.util;

import java.util.List;
import java.util.function.Consumer;

public class AsciiArt {

    public static String colorTranslate(String text) {
        return text.replace("&0", ColorCode.C_0)
                .replace("&1", ColorCode.C_1)
                .replace("&2", ColorCode.C_2)
                .replace("&3", ColorCode.C_3)
                .replace("&4", ColorCode.C_4)
                .replace("&5", ColorCode.C_5)
                .replace("&6", ColorCode.C_6)
                .replace("&7", ColorCode.C_7)
                .replace("&8", ColorCode.C_8)
                .replace("&9", ColorCode.C_9)
                .replace("&a", ColorCode.C_A)
                .replace("&b", ColorCode.C_B)
                .replace("&c", ColorCode.C_C)
                .replace("&d", ColorCode.C_D)
                .replace("&e", ColorCode.C_E)
                .replace("&f", ColorCode.C_F);
    }

    public static void printLogo(Consumer<String> consumer) {

        List<String> logoLines = List.of(
                colorTranslate("&0    &3__&b___&2__&a__.&e__                 &b__          &fv0.0.15  \033[0m"),
                colorTranslate("&0   &3/   &b__&2__&a_/|  &e|   &5__&d__   &3__&b__ |  &a| _&e_ &c__&4__&5__&d__&9__&b_&0   \033[0m"),
                colorTranslate("&0   &b\\&3_&b__&2__  &a\\ &e|  | &5_/ &d__ &9\\&3_/ &b__ \\|  &a|/ &e/&c/ &4__ &5\\&d_  &9_&b_ \\  \033[0m"),
                colorTranslate("&b   /        &e\\|  &c|_&5\\  &d_&9__&3/&b\\  ___/&a|    &e<&c\\  &4_&5__&d/|  &b| \\/  \033[0m"),
                colorTranslate("&b  /__&2__&a__&e_  /|&4__&5__/&d\\_&9__  &b>\\_&2__  &a>__&e|_ \\&c\\_&5__  &9>__&b|     \033[0m"),
                colorTranslate("&e             Authored by &9Thiago &bRigonatti             \033[0m")
        );

        logoLines.forEach(consumer);
    }
}
