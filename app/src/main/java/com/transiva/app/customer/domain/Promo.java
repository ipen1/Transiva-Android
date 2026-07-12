package com.transiva.app.customer.domain;

public final class Promo {
    public final String title;
    public final String description;
    public final String code;

    public Promo(String title, String description, String code) {
        this.title = title;
        this.description = description;
        this.code = code;
    }
}
