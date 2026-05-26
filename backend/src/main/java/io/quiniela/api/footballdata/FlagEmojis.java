package io.quiniela.api.footballdata;

import java.util.Map;

/**
 * Convert a FIFA three-letter team code (TLA) to a flag emoji.
 *
 * <p>FIFA TLAs mostly overlap with ISO 3166-1 alpha-3 country codes but with notable differences:
 * Germany is {@code GER} (not {@code DEU}), the Czech Republic is {@code CZE}, England is {@code
 * ENG} (separate from the UK), etc. We maintain an explicit FIFA-TLA → ISO 3166-1 alpha-2 map and
 * convert the alpha-2 to a regional-indicator-pair emoji.
 *
 * <p>Constituent countries of the UK (ENG/WAL/SCO/NIR) fall back to 🇬🇧 — the dedicated
 * subdivision emojis (🏴󠁧󠁢󠁥󠁮󠁧󠁿 etc.) don't render reliably in older mobile fonts and our
 * friends are on phones.
 */
public final class FlagEmojis {

  private FlagEmojis() {}

  private static final Map<String, String> FIFA_TO_ALPHA2 =
      Map.ofEntries(
          Map.entry("ALG", "DZ"), // Algeria
          Map.entry("ARG", "AR"),
          Map.entry("AUS", "AU"),
          Map.entry("AUT", "AT"),
          Map.entry("BEL", "BE"),
          Map.entry("BIH", "BA"), // Bosnia-Herzegovina (alpha-3 form)
          Map.entry("BRA", "BR"),
          Map.entry("CAN", "CA"),
          Map.entry("CHI", "CL"), // Chile
          Map.entry("CIV", "CI"), // Côte d'Ivoire
          Map.entry("CMR", "CM"), // Cameroon
          Map.entry("COD", "CD"), // Congo DR (alpha-3 form)
          Map.entry("COL", "CO"),
          Map.entry("CPV", "CV"), // Cape Verde
          Map.entry("CRC", "CR"), // Costa Rica
          Map.entry("CRO", "HR"), // Croatia
          Map.entry("CUR", "CW"), // Curaçao (FIFA form)
          Map.entry("CUW", "CW"), // Curaçao (alpha-3 form)
          Map.entry("CZE", "CZ"),
          Map.entry("DEN", "DK"), // Denmark
          Map.entry("ECU", "EC"),
          Map.entry("EGY", "EG"),
          Map.entry("ENG", "GB"), // England → UK fallback
          Map.entry("ESP", "ES"),
          Map.entry("FRA", "FR"),
          Map.entry("GER", "DE"), // Germany (FIFA != ISO alpha-3)
          Map.entry("GHA", "GH"),
          Map.entry("HAI", "HT"), // Haiti
          Map.entry("HON", "HN"), // Honduras
          Map.entry("HUN", "HU"),
          Map.entry("IRN", "IR"), // Iran
          Map.entry("IRQ", "IQ"),
          Map.entry("ITA", "IT"),
          Map.entry("JAM", "JM"),
          Map.entry("JOR", "JO"), // Jordan
          Map.entry("JPN", "JP"),
          Map.entry("KOR", "KR"), // South Korea
          Map.entry("KSA", "SA"), // Saudi Arabia
          Map.entry("MAR", "MA"), // Morocco
          Map.entry("MEX", "MX"),
          Map.entry("NED", "NL"), // Netherlands
          Map.entry("NGA", "NG"), // Nigeria
          Map.entry("NIR", "GB"), // Northern Ireland → UK fallback
          Map.entry("NOR", "NO"),
          Map.entry("NZL", "NZ"),
          Map.entry("PAN", "PA"),
          Map.entry("PAR", "PY"), // Paraguay
          Map.entry("PER", "PE"),
          Map.entry("POL", "PL"),
          Map.entry("POR", "PT"),
          Map.entry("QAT", "QA"),
          Map.entry("RSA", "ZA"), // South Africa (FIFA form)
          Map.entry("SCO", "GB"), // Scotland → UK fallback
          Map.entry("SEN", "SN"),
          Map.entry("SRB", "RS"),
          Map.entry("SUI", "CH"), // Switzerland (FIFA SUI from Suisse)
          Map.entry("SVK", "SK"), // Slovakia
          Map.entry("SVN", "SI"), // Slovenia
          Map.entry("SWE", "SE"),
          Map.entry("TUN", "TN"),
          Map.entry("TUR", "TR"),
          Map.entry("UKR", "UA"),
          Map.entry("URU", "UY"), // Uruguay (FIFA form)
          Map.entry("URY", "UY"), // Uruguay (alpha-3 form)
          Map.entry("USA", "US"),
          Map.entry("UZB", "UZ"), // Uzbekistan
          Map.entry("VEN", "VE"),
          Map.entry("WAL", "GB")); // Wales → UK fallback

  /** Returns the flag emoji for a FIFA TLA, or null if no mapping is known. */
  public static String toEmoji(String tla) {
    if (tla == null) return null;
    String alpha2 = FIFA_TO_ALPHA2.get(tla.toUpperCase());
    if (alpha2 == null || alpha2.length() != 2) return null;
    return toFlagFromAlpha2(alpha2);
  }

  /** Converts an ISO 3166-1 alpha-2 country code to a flag emoji string. */
  static String toFlagFromAlpha2(String alpha2) {
    int a = 0x1F1E6 + (Character.toUpperCase(alpha2.charAt(0)) - 'A');
    int b = 0x1F1E6 + (Character.toUpperCase(alpha2.charAt(1)) - 'A');
    return new String(Character.toChars(a)) + new String(Character.toChars(b));
  }
}
