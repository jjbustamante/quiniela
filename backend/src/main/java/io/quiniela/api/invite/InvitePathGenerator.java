package io.quiniela.api.invite;

import java.security.SecureRandom;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * Builds a personal invite path slug + 6-character random suffix. Example:
 * "juan-bustamante-x1y2z3". Suffix uses a-z + 0-9 (alphabet of 36, so 36^6 ≈ 2.1B — collision-free
 * at our scale).
 */
@Component
public class InvitePathGenerator {

  private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int SUFFIX_LEN = 6;
  private static final int SLUG_MAX_LEN = 32;
  private final SecureRandom random = new SecureRandom();

  public String generate(String displayName) {
    return slugify(displayName) + "-" + randomSuffix();
  }

  private String slugify(String name) {
    if (name == null || name.isBlank()) return "user";
    String normalized =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase();
    // Remove punctuation that should be stripped (apostrophes, quotes, etc.) before dashing
    String stripped = normalized.replaceAll("[''\"'`]", "");
    String alnum = stripped.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (alnum.isEmpty()) return "user";
    return alnum.length() > SLUG_MAX_LEN ? alnum.substring(0, SLUG_MAX_LEN) : alnum;
  }

  private String randomSuffix() {
    var sb = new StringBuilder(SUFFIX_LEN);
    for (int i = 0; i < SUFFIX_LEN; i++) {
      sb.append(SUFFIX_ALPHABET.charAt(random.nextInt(SUFFIX_ALPHABET.length())));
    }
    return sb.toString();
  }
}
