package com.valaryrpg.core.module.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filtro parole, regex, IP e link. Compilato una volta dal YAML.
 */
public final class ChatFilter {

    public enum Hit {
        WORD, REGEX, IP, LINK
    }

    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"
    );

    private final List<Pattern> words;
    private final List<Pattern> regex;
    private final List<Pattern> links;
    private final boolean blockIps;
    private final boolean blockLinks;

    private ChatFilter(
            List<Pattern> words,
            List<Pattern> regex,
            List<Pattern> links,
            boolean blockIps,
            boolean blockLinks
    ) {
        this.words = List.copyOf(words);
        this.regex = List.copyOf(regex);
        this.links = List.copyOf(links);
        this.blockIps = blockIps;
        this.blockLinks = blockLinks;
    }

    public static ChatFilter compile(
            List<String> words,
            List<String> regex,
            boolean blockIps,
            boolean blockLinks,
            List<String> linkPatterns
    ) {
        List<Pattern> wordPatterns = new ArrayList<>();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String trimmed = word.trim();
            if (trimmed.contains(" ")) {
                wordPatterns.add(Pattern.compile(Pattern.quote(trimmed), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } else {
                wordPatterns.add(Pattern.compile(
                        "\\b" + Pattern.quote(trimmed) + "\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                ));
            }
        }
        return new ChatFilter(
                wordPatterns,
                compileAll(regex),
                compileAll(linkPatterns),
                blockIps,
                blockLinks
        );
    }

    public Optional<Hit> match(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (Pattern pattern : this.words) {
            if (pattern.matcher(text).find()) {
                return Optional.of(Hit.WORD);
            }
        }
        for (Pattern pattern : this.regex) {
            if (pattern.matcher(text).find()) {
                return Optional.of(Hit.REGEX);
            }
        }
        if (this.blockIps && IPV4.matcher(text).find()) {
            return Optional.of(Hit.IP);
        }
        if (this.blockLinks) {
            for (Pattern pattern : this.links) {
                if (pattern.matcher(text).find()) {
                    return Optional.of(Hit.LINK);
                }
            }
        }
        return Optional.empty();
    }

    private static List<Pattern> compileAll(List<String> raw) {
        List<Pattern> compiled = new ArrayList<>();
        if (raw == null) {
            return compiled;
        }
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(value));
            } catch (PatternSyntaxException ignored) {
                // YAML invalido: salta la regola, non blocca il modulo
            }
        }
        return compiled;
    }
}
