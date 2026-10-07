package com.valaryrpg.core.webhook;

/** Entry in coda attività Discord. Classe top-level per evitare ClassNotFound su nested record. */
final class WebhookPending {

    private final String title;
    private final String body;
    private final int color;
    private final String url;

    WebhookPending(String title, String body, int color, String url) {
        this.title = title;
        this.body = body;
        this.color = color;
        this.url = url;
    }

    String title() {
        return this.title;
    }

    String body() {
        return this.body;
    }

    int color() {
        return this.color;
    }

    String url() {
        return this.url;
    }
}
