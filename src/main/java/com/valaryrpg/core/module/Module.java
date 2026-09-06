package com.valaryrpg.core.module;

/**
 * Contratto di un modulo funzionale (worlds, homes, kits, ...).
 * Ogni modulo è attivabile da config.yml e può essere ricaricato a caldo.
 */
public interface Module {

    String id();

    void enable();

    void disable();

    void reload();
}
