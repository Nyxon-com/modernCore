//made with love by nyxon

package com.modern.core.module.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatFilter {

  public enum Hit {
    WORD, REGEX, IP, LINK, EXPLOIT, CAPS, FLOOD, REPEAT
  }

  private static final Pattern IPV4 = Pattern.compile(
      "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"
  );
  private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]");
  private static final Pattern SECTION = Pattern.compile("§|\\\\u00a7|&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXPLOIT = Pattern.compile(
      "(?i)(\\$\\{jndi:|\\\\x[0-9a-f]{2}|%0[da]|\\$\\{.*?\\}|javascript:|data:text/html|file:///)"
  );

  private final List<Pattern> words;
  private final List<Pattern> compactWords;
  private final List<Pattern> regex;
  private final List<Pattern> links;
  private final boolean blockIps;
  private final boolean blockLinks;
  private final boolean blockExploits;
  private final int capsMinLength;
  private final double capsRatio;

  private ChatFilter(
      List<Pattern> words,
      List<Pattern> compactWords,
      List<Pattern> regex,
      List<Pattern> links,
      boolean blockIps,
      boolean blockLinks,
      boolean blockExploits,
      int capsMinLength,
      double capsRatio
  ) {
    this.words = List.copyOf(words);
    this.compactWords = List.copyOf(compactWords);
    this.regex = List.copyOf(regex);
    this.links = List.copyOf(links);
    this.blockIps = blockIps;
    this.blockLinks = blockLinks;
    this.blockExploits = blockExploits;
    this.capsMinLength = capsMinLength;
    this.capsRatio = capsRatio;
  }

  public static ChatFilter compile(
      List<String> words,
      List<String> regex,
      boolean blockIps,
      boolean blockLinks,
      List<String> linkPatterns,
      boolean blockExploits,
      int capsMinLength,
      double capsRatio
  ) {
    List<Pattern> wordPatterns = new ArrayList<>();
    List<Pattern> compactPatterns = new ArrayList<>();
    for (String word : words) {
      if (word == null || word.isBlank()) {
        continue;
      }
      String trimmed = word.trim();
      String normalized = normalize(trimmed);
      if (normalized.isBlank()) {
        continue;
      }
      if (trimmed.contains(" ")) {
        wordPatterns.add(Pattern.compile(Pattern.quote(normalized), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      } else {
        wordPatterns.add(Pattern.compile(
            "\\b" + Pattern.quote(normalized) + "\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
      }
      String compact = compact(normalized);
      if (!compact.isBlank()) {
        compactPatterns.add(Pattern.compile(Pattern.quote(compact), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      }
    }
    return new ChatFilter(
        wordPatterns,
        compactPatterns,
        compileAll(regex),
        compileAll(linkPatterns),
        blockIps,
        blockLinks,
        blockExploits,
        Math.max(0, capsMinLength),
        Math.min(1.0D, Math.max(0.0D, capsRatio))
    );
  }

  public Optional<Hit> match(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    if (this.blockExploits && (ZERO_WIDTH.matcher(text).find() || SECTION.matcher(text).find() || EXPLOIT.matcher(text).find())) {
      return Optional.of(Hit.EXPLOIT);
    }
    String normalized = normalize(text);
    for (Pattern pattern : this.words) {
      if (pattern.matcher(normalized).find()) {
        return Optional.of(Hit.WORD);
      }
    }
    String compacted = compact(normalized);
    for (Pattern pattern : this.compactWords) {
      if (pattern.matcher(compacted).find()) {
        return Optional.of(Hit.WORD);
      }
    }
    for (Pattern pattern : this.regex) {
      if (pattern.matcher(text).find() || pattern.matcher(normalized).find()) {
        return Optional.of(Hit.REGEX);
      }
    }
    if (this.blockIps && IPV4.matcher(text).find()) {
      return Optional.of(Hit.IP);
    }
    if (this.blockLinks) {
      for (Pattern pattern : this.links) {
        if (pattern.matcher(text).find() || pattern.matcher(normalized).find()) {
          return Optional.of(Hit.LINK);
        }
      }
    }
    if (capsSpam(text)) {
      return Optional.of(Hit.CAPS);
    }
    return Optional.empty();
  }

  private boolean capsSpam(String text) {
    if (this.capsMinLength <= 0 || this.capsRatio <= 0.0D) {
      return false;
    }
    int letters = 0;
    int upper = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetter(c)) {
        letters++;
        if (Character.isUpperCase(c)) {
          upper++;
        }
      }
    }
    return letters >= this.capsMinLength && ((double) upper / (double) letters) >= this.capsRatio;
  }

  static String normalize(String input) {
    String value = Normalizer.normalize(input, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
    value = ZERO_WIDTH.matcher(value).replaceAll("");
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      out.append(switch (c) {
        case '0', 'ο', 'о' -> 'o';
        case '1', '!', '|', 'ı', 'і' -> 'i';
        case '3', '€' -> 'e';
        case '4', '@', 'а' -> 'a';
        case '5', '$' -> 's';
        case '7' -> 't';
        case '8' -> 'b';
        case '9' -> 'g';
        default -> c;
      });
    }
    return out.toString().replaceAll("(.)\\1{2,}", "$1$1");
  }

  static String compact(String normalized) {
    return normalized.replaceAll("[^a-z0-9]", "");
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

      }
    }
    return compiled;
  }
}
