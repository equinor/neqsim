package neqsim.thermo.mixingrule;

import java.util.Locale;

/** Selects the binary-interaction parameterization used by the Soreide-Whitson mixing rule. */
public enum SoreideWhitsonParameterization {
  /** Original Soreide-Whitson correlation retained for backward compatibility. */
  LEGACY,

  /** Aqueous CO2-water correlation published by Chabab et al. (2019). */
  CHABAB_2019,

  /**
   * Drop-in aqueous and non-aqueous BIPs published by Burgoyne and Nielsen (2026).
   *
   * <p>
   * This parameterization covers CO2, H2S, methane, nitrogen, hydrogen, ethane, propane, and n-butane paired with
   * water. Other pairs retain their existing NeqSim interaction parameter.
   * </p>
   */
  BURGOYNE_NIELSEN_2026;

  /**
   * Resolve a parameterization from its API name.
   *
   * @param name parameterization name, such as {@code LEGACY}, {@code CHABAB_2019}, or {@code BURGOYNE_NIELSEN_2026}
   * @return matching parameterization
   * @throws IllegalArgumentException if {@code name} is null or unsupported
   */
  public static SoreideWhitsonParameterization byName(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Soreide-Whitson parameterization name cannot be null");
    }
    String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if ("M_SW".equals(normalized) || "MSW".equals(normalized)) {
      normalized = "CHABAB_2019";
    } else if ("BN_2026".equals(normalized) || "BN2026".equals(normalized)) {
      normalized = "BURGOYNE_NIELSEN_2026";
    }
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unsupported Soreide-Whitson parameterization: " + name
          + ". Supported values are LEGACY, CHABAB_2019, and BURGOYNE_NIELSEN_2026.", ex);
    }
  }
}
