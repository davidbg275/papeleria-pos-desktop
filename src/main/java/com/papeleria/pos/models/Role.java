package com.papeleria.pos.models;

public enum Role {
    ADMIN, SELLER;

    /** Parser tolerante para strings desde JSON o UI. */
    public static Role fromString(String s) {
        if (s == null) return SELLER;
        String v = s.trim().toUpperCase();
        if (v.equals("ADMIN")) return ADMIN;
        return SELLER;
    }
}
