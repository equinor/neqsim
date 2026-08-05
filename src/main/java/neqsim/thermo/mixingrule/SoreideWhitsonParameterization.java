package neqsim.thermo.mixingrule;

import java.util.Locale;

/**
 * Selects the aqueous CO2-water binary-interaction parameterization used by the Soreide-Whitson
 * mixing rule.
 */
public enum SoreideWhitsonParameterization {
  /** Original Soreide-Whitson correlation retained for backward compatibility. */
  LEGACY,

  /** Aqueous CO2-water correlation published by Chabab et al. (2019). */
  CHABAB_2019;

  /**
   * Resolve a parameterization from its API name.
   *
   * @param name parameterization name, such as {@code LEGACY} or {@code CHABAB_2019}
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
    }
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unsupported Soreide-Whitson parameterization: " + name
          + ". Supported values are LEGACY and CHABAB_2019.", ex);
    }
  }
}
