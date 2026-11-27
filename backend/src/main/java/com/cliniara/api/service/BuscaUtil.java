package com.cliniara.api.service;

import java.util.Locale;

public final class BuscaUtil {

    private BuscaUtil() {
    }

    public static String paraLikeContendo(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escapado = q.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapado + "%";
    }

    public static String paraLikeSomenteDigitos(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String digitos = q.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        return "%" + digitos + "%";
    }
}
