//made with love by nyxon

package com.modern.core.util;

public final class LegacyFormats {

  private LegacyFormats() {
  }

  public static String toMiniMessage(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    if (raw.indexOf('&') < 0 && raw.indexOf('§') < 0) {
      return raw;
    }
    String input = raw.replace('§', '&');
    StringBuilder out = new StringBuilder(input.length() + 24);
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c != '&' || i + 1 >= input.length()) {
        out.append(c);
        continue;
      }
      char code = Character.toLowerCase(input.charAt(i + 1));
      if (code == '#' && i + 7 < input.length()) {
        String hex = input.substring(i + 2, i + 8);
        if (isHex(hex)) {
          out.append('<').append('#').append(hex).append('>');
          i += 7;
          continue;
        }
      }
      if (code == 'x' && i + 13 < input.length()) {
        String hex = extractXHex(input, i + 2);
        if (hex != null) {
          out.append('<').append('#').append(hex).append('>');
          i += 13;
          continue;
        }
      }
      String tag = switch (code) {
        case '0' -> "<black>";
        case '1' -> "<dark_blue>";
        case '2' -> "<dark_green>";
        case '3' -> "<dark_aqua>";
        case '4' -> "<dark_red>";
        case '5' -> "<dark_purple>";
        case '6' -> "<gold>";
        case '7' -> "<gray>";
        case '8' -> "<dark_gray>";
        case '9' -> "<blue>";
        case 'a' -> "<green>";
        case 'b' -> "<aqua>";
        case 'c' -> "<red>";
        case 'd' -> "<light_purple>";
        case 'e' -> "<yellow>";
        case 'f' -> "<white>";
        case 'k' -> "<obfuscated>";
        case 'l' -> "<bold>";
        case 'm' -> "<strikethrough>";
        case 'n' -> "<underlined>";
        case 'o' -> "<italic>";
        case 'r' -> "<reset>";
        default -> null;
      };
      if (tag != null) {
        out.append(tag);
        i++;
        continue;
      }
      out.append(c);
    }
    return out.toString();
  }

  private static String extractXHex(String input, int start) {

    if (start + 11 >= input.length()) {
      return null;
    }
    char[] hex = new char[6];
    for (int n = 0; n < 6; n++) {
      int at = start + n * 2;
      if (input.charAt(at) != '&') {
        return null;
      }
      char digit = input.charAt(at + 1);
      if (!isHexChar(digit)) {
        return null;
      }
      hex[n] = Character.toLowerCase(digit);
    }
    return new String(hex);
  }

  private static boolean isHex(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (!isHexChar(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isHexChar(char c) {
    return (c >= '0' && c <= '9')
        || (c >= 'a' && c <= 'f')
        || (c >= 'A' && c <= 'F');
  }
}
