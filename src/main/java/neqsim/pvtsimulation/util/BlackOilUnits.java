package neqsim.pvtsimulation.util;

/**
 * Unit system enumeration for Black Oil correlations.
 *
 * <p>
 * Supports conversion between field units (US oilfield) and SI/metric units commonly used in
 * NeqSim.
 *
 * @author ESOL
 */
public enum BlackOilUnits {

  /**
   * Field units (US oilfield standard).
   * 
   * <ul>
   * <li>Pressure: psia</li>
   * <li>Temperature: °F</li>
   * <li>GOR: scf/STB</li>
   * <li>FVF: bbl/STB or ft³/scf</li>
   * <li>Viscosity: cP</li>
   * <li>Density: lb/ft³</li>
   * </ul>
   */
  FIELD,

  /**
   * SI/Metric units.
   * 
   * <ul>
   * <li>Pressure: bara</li>
   * <li>Temperature: °C</li>
   * <li>GOR: Sm³/Sm³</li>
   * <li>FVF: m³/Sm³</li>
   * <li>Viscosity: Pa·s (1 Pa·s = 1000 cP)</li>
   * <li>Density: kg/m³</li>
   * </ul>
   */
  SI,

  /**
   * NeqSim internal units.
   * 
   * <ul>
   * <li>Pressure: bara</li>
   * <li>Temperature: K</li>
   * <li>GOR: Sm³/Sm³</li>
   * <li>FVF: m³/Sm³</li>
   * <li>Viscosity: Pa·s</li>
   * <li>Density: kg/m³</li>
   * </ul>
   */
  NEQSIM;

  // ==================== PRESSURE CONVERSIONS ====================

  /**
   * Convert pressure to psia.
   *
   * @param pressure Pressure value
   * @param fromUnit Source unit system
   * @return Pressure in psia
   */
  public static double toPsia(double pressure, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return pressure; // Already psia
      case SI:
      case NEQSIM:
        return pressure * 14.5038; // bara to psia
      default:
        return pressure;
    }
  }

  /**
   * Convert pressure from psia.
   *
   * @param psia Pressure in psia
   * @param toUnit Target unit system
   * @return Pressure in target units
   */
  public static double fromPsia(double psia, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return psia; // Already psia
      case SI:
      case NEQSIM:
        return psia / 14.5038; // psia to bara
      default:
        return psia;
    }
  }

  // ==================== TEMPERATURE CONVERSIONS ====================

  /**
   * Convert temperature to Fahrenheit.
   *
   * @param temperature Temperature value
   * @param fromUnit Source unit system
   * @return Temperature in °F
   */
  public static double toFahrenheit(double temperature, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return temperature; // Already °F
      case SI:
        return temperature * 9.0 / 5.0 + 32.0; // °C to °F
      case NEQSIM:
        return (temperature - 273.15) * 9.0 / 5.0 + 32.0; // K to °F
      default:
        return temperature;
    }
  }

  /**
   * Convert temperature from Fahrenheit.
   *
   * @param fahrenheit Temperature in °F
   * @param toUnit Target unit system
   * @return Temperature in target units
   */
  public static double fromFahrenheit(double fahrenheit, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return fahrenheit; // Already °F
      case SI:
        return (fahrenheit - 32.0) * 5.0 / 9.0; // °F to °C
      case NEQSIM:
        return (fahrenheit - 32.0) * 5.0 / 9.0 + 273.15; // °F to K
      default:
        return fahrenheit;
    }
  }

  /**
   * Convert temperature to Rankine.
   *
   * @param temperature Temperature value
   * @param fromUnit Source unit system
   * @return Temperature in °R
   */
  public static double toRankine(double temperature, BlackOilUnits fromUnit) {
    return toFahrenheit(temperature, fromUnit) + 459.67;
  }

  // ==================== GOR CONVERSIONS ====================

  /** Conversion factor: scf/STB to Sm³/Sm³. */
  private static final double SCF_STB_TO_SM3_SM3 = 0.178108;

  /**
   * Convert GOR to scf/STB.
   *
   * @param gor GOR value
   * @param fromUnit Source unit system
   * @return GOR in scf/STB
   */
  public static double toScfPerStb(double gor, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return gor; // Already scf/STB
      case SI:
      case NEQSIM:
        return gor / SCF_STB_TO_SM3_SM3; // Sm³/Sm³ to scf/STB
      default:
        return gor;
    }
  }

  /**
   * Convert GOR from scf/STB.
   *
   * @param scfPerStb GOR in scf/STB
   * @param toUnit Target unit system
   * @return GOR in target units
   */
  public static double fromScfPerStb(double scfPerStb, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return scfPerStb; // Already scf/STB
      case SI:
      case NEQSIM:
        return scfPerStb * SCF_STB_TO_SM3_SM3; // scf/STB to Sm³/Sm³
      default:
        return scfPerStb;
    }
  }

  // ==================== VISCOSITY CONVERSIONS ====================

  /**
   * Convert viscosity to cP.
   *
   * <p>
   * Note: SI viscosity is in Pa·s (= 1000 cP), NEQSIM also uses Pa·s. FIELD uses cP.
   *
   * @param viscosity Viscosity value
   * @param fromUnit Source unit system
   * @return Viscosity in cP
   */
  public static double toCentipoise(double viscosity, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return viscosity; // Already cP
      case SI:
      case NEQSIM:
        return viscosity * 1000.0; // Pa·s to cP (1 Pa·s = 1000 cP)
      default:
        return viscosity;
    }
  }

  /**
   * Convert viscosity from cP.
   *
   * <p>
   * Note: SI viscosity is in Pa·s (= 1000 cP), NEQSIM also uses Pa·s. FIELD uses cP.
   *
   * @param centipoise Viscosity in cP
   * @param toUnit Target unit system
   * @return Viscosity in target units (Pa·s for SI/NEQSIM, cP for FIELD)
   */
  public static double fromCentipoise(double centipoise, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return centipoise; // Already cP
      case SI:
      case NEQSIM:
        return centipoise / 1000.0; // cP to Pa·s (1 cP = 0.001 Pa·s)
      default:
        return centipoise;
    }
  }

  // ==================== DENSITY CONVERSIONS ====================

  /** Conversion factor: lb/ft³ to kg/m³. */
  private static final double LB_FT3_TO_KG_M3 = 16.01846;

  /**
   * Convert density to lb/ft³.
   *
   * @param density Density value
   * @param fromUnit Source unit system
   * @return Density in lb/ft³
   */
  public static double toLbPerFt3(double density, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return density; // Already lb/ft³
      case SI:
      case NEQSIM:
        return density / LB_FT3_TO_KG_M3; // kg/m³ to lb/ft³
      default:
        return density;
    }
  }

  /**
   * Convert density from lb/ft³.
   *
   * @param lbPerFt3 Density in lb/ft³
   * @param toUnit Target unit system
   * @return Density in target units
   */
  public static double fromLbPerFt3(double lbPerFt3, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return lbPerFt3; // Already lb/ft³
      case SI:
      case NEQSIM:
        return lbPerFt3 * LB_FT3_TO_KG_M3; // lb/ft³ to kg/m³
      default:
        return lbPerFt3;
    }
  }

  // ==================== FVF CONVERSIONS ====================

  /**
   * Oil FVF is dimensionless (volume ratio), same in all unit systems.
   *
   * @param bo Oil FVF
   * @param fromUnit Source unit system (ignored)
   * @return Oil FVF (unchanged)
   */
  public static double convertBo(double bo, BlackOilUnits fromUnit) {
    return bo; // Dimensionless ratio
  }

  /** Conversion factor: rcf/scf to rm³/Sm³. */
  private static final double RCF_SCF_TO_RM3_SM3 = 1.0;

  /**
   * Gas FVF: rcf/scf ≈ rm³/Sm³ (both are volume ratios at similar conditions).
   *
   * @param bg Gas FVF
   * @param fromUnit Source unit system (ignored for simple ratio)
   * @return Gas FVF (unchanged)
   */
  public static double convertBg(double bg, BlackOilUnits fromUnit) {
    return bg; // Dimensionless ratio
  }

  // ==================== COMPRESSIBILITY CONVERSIONS ====================

  /**
   * Convert compressibility to 1/psi.
   *
   * @param compressibility Compressibility value
   * @param fromUnit Source unit system
   * @return Compressibility in 1/psi
   */
  public static double toPerPsi(double compressibility, BlackOilUnits fromUnit) {
    switch (fromUnit) {
      case FIELD:
        return compressibility; // Already 1/psi
      case SI:
      case NEQSIM:
        return compressibility / 14.5038; // 1/bar to 1/psi
      default:
        return compressibility;
    }
  }

  /**
   * Convert compressibility from 1/psi.
   *
   * @param perPsi Compressibility in 1/psi
   * @param toUnit Target unit system
   * @return Compressibility in target units
   */
  public static double fromPerPsi(double perPsi, BlackOilUnits toUnit) {
    switch (toUnit) {
      case FIELD:
        return perPsi; // Already 1/psi
      case SI:
      case NEQSIM:
        return perPsi * 14.5038; // 1/psi to 1/bar
      default:
        return perPsi;
    }
  }
}
