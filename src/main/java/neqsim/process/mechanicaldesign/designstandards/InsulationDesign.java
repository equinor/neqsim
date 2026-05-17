package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/**
 * Thermal insulation design per NORSOK R-004 and CINI Manual.
 *
 * <p>
 * Calculates insulation thickness for various design purposes: heat conservation, personnel
 * protection, process temperature maintenance, frost protection, and fire protection. Includes
 * material selection guidance and weight impact estimation.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>NORSOK R-004: Piping and equipment insulation</li>
 * <li>CINI Manual: Netherlands Insulation Industry (standard reference for insulation
 * thickness)</li>
 * <li>ASTM C585: Inner and Outer Diameters of Rigid Thermal Insulation</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class InsulationDesign implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Insulation purpose.
   */
  public enum InsulationPurpose {
    /** Heat conservation to minimize energy loss. */
    HEAT_CONSERVATION,
    /** Personnel protection (surface temp below 60 degC). */
    PERSONNEL_PROTECTION,
    /** Process temperature maintenance. */
    PROCESS_MAINTENANCE,
    /** Frost protection. */
    FROST_PROTECTION,
    /** Fire protection (passive fire protection). */
    FIRE_PROTECTION
  }

  /**
   * Insulation material type with thermal conductivity correlations.
   */
  public enum InsulationMaterial {
    /** Mineral wool (rockwool) - widely used, max ~700 degC. */
    MINERAL_WOOL(0.035, 0.045, 250, 700, 100.0),
    /** Calcium silicate - rigid, good for high temp. */
    CALCIUM_SILICATE(0.055, 0.075, 250, 1000, 240.0),
    /** PIR (polyisocyanurate) foam - excellent insulator, low temp only. */
    PIR_FOAM(0.022, 0.025, 40, 140, 40.0),
    /** Aerogel blanket - best performance, thin profile. */
    AEROGEL(0.014, 0.020, 140, 650, 120.0),
    /** Cellular glass (Foamglas) - for cryogenic service. */
    CELLULAR_GLASS(0.040, 0.050, 130, 430, 115.0),
    /** Expanded perlite - for cryogenic service. */
    EXPANDED_PERLITE(0.045, 0.060, 50, 650, 65.0);

    private final double conductivityAtLow;
    private final double conductivityAtHigh;
    private final double densityKgM3;
    private final double maxTempC;
    private final double typicalDensityKgM3;

    InsulationMaterial(double kLow, double kHigh, double densityKgM3, double maxTempC,
        double typicalDensityKgM3) {
      this.conductivityAtLow = kLow;
      this.conductivityAtHigh = kHigh;
      this.densityKgM3 = densityKgM3;
      this.maxTempC = maxTempC;
      this.typicalDensityKgM3 = typicalDensityKgM3;
    }

    /**
     * Get thermal conductivity at a given mean temperature.
     *
     * @param meanTempC mean temperature in Celsius
     * @return thermal conductivity in W/(m*K)
     */
    public double getConductivity(double meanTempC) {
      // Linear interpolation between low and high temperature values
      double fraction = Math.max(0, Math.min(1.0, meanTempC / maxTempC));
      return conductivityAtLow + fraction * (conductivityAtHigh - conductivityAtLow);
    }

    /**
     * Gets the maximum service temperature.
     *
     * @return maximum service temperature in Celsius
     */
    public double getMaxTempC() {
      return maxTempC;
    }

    /**
     * Gets the typical installed density.
     *
     * @return density in kg/m3
     */
    public double getTypicalDensityKgM3() {
      return typicalDensityKgM3;
    }
  }

  /** Maximum personnel protection surface temperature in Celsius (NORSOK R-004). */
  public static final double MAX_PERSONNEL_PROTECTION_TEMP_C = 60.0;

  /** Ambient temperature default in Celsius. */
  private static final double DEFAULT_AMBIENT_TEMP_C = 20.0;

  /** Wind speed for outdoor heat loss calculation in m/s. */
  private static final double DEFAULT_WIND_SPEED = 5.0;

  /**
   * Calculate required insulation thickness for a flat surface (vessel, tank).
   *
   * @param processTempC process temperature in Celsius
   * @param ambientTempC ambient temperature in Celsius
   * @param material insulation material
   * @param purpose insulation purpose
   * @param windSpeedMS wind speed in m/s (for outdoor installations)
   * @return required insulation thickness in mm
   */
  public static double flatSurfaceThickness(double processTempC, double ambientTempC,
      InsulationMaterial material, InsulationPurpose purpose, double windSpeedMS) {

    double targetSurfaceTemp;
    double maxHeatLossWm2;

    switch (purpose) {
      case PERSONNEL_PROTECTION:
        targetSurfaceTemp = MAX_PERSONNEL_PROTECTION_TEMP_C;
        maxHeatLossWm2 = Double.MAX_VALUE;
        break;
      case HEAT_CONSERVATION:
        targetSurfaceTemp = ambientTempC + 5.0; // aim for near-ambient surface
        maxHeatLossWm2 = 40.0; // typical limit W/m2
        break;
      case FROST_PROTECTION:
        targetSurfaceTemp = 5.0; // keep above freezing
        maxHeatLossWm2 = Double.MAX_VALUE;
        break;
      default:
        targetSurfaceTemp = ambientTempC + 10.0;
        maxHeatLossWm2 = 60.0;
        break;
    }

    // Outer surface heat transfer coefficient (natural + forced convection)
    double hOuter = 5.7 + 3.8 * windSpeedMS; // W/(m2*K), CINI simplified

    double deltaT = Math.abs(processTempC - ambientTempC);
    if (deltaT < 1.0) {
      return 0.0; // no insulation needed
    }

    double meanTemp = (processTempC + targetSurfaceTemp) / 2.0;
    double kInsulation = material.getConductivity(meanTemp);

    // From heat balance: q = (Tp - Ts) / (t/k) = (Ts - Ta) * h
    // Solve for thickness t: t = k * (Tp - Ts) / (h * (Ts - Ta))
    double surfaceDeltaT = targetSurfaceTemp - ambientTempC;
    if (surfaceDeltaT < 0.5) {
      surfaceDeltaT = 0.5;
    }

    double thicknessM = kInsulation * (processTempC - targetSurfaceTemp) / (hOuter * surfaceDeltaT);
    double thicknessMm = thicknessM * 1000.0;

    // Round up to standard increments (25mm)
    thicknessMm = Math.ceil(thicknessMm / 25.0) * 25.0;
    return Math.max(thicknessMm, 25.0);
  }

  /**
   * Calculate required insulation thickness for a pipe.
   *
   * @param processTempC process temperature in Celsius
   * @param ambientTempC ambient temperature in Celsius
   * @param pipeODMm pipe outer diameter in mm
   * @param material insulation material
   * @param purpose insulation purpose
   * @param windSpeedMS wind speed in m/s
   * @return required insulation thickness in mm
   */
  public static double pipeThickness(double processTempC, double ambientTempC, double pipeODMm,
      InsulationMaterial material, InsulationPurpose purpose, double windSpeedMS) {

    double targetSurfaceTemp;
    if (purpose == InsulationPurpose.PERSONNEL_PROTECTION) {
      targetSurfaceTemp = MAX_PERSONNEL_PROTECTION_TEMP_C;
    } else if (purpose == InsulationPurpose.FROST_PROTECTION) {
      targetSurfaceTemp = 5.0;
    } else {
      targetSurfaceTemp = ambientTempC + 5.0;
    }

    double deltaT = Math.abs(processTempC - ambientTempC);
    if (deltaT < 1.0) {
      return 0.0;
    }

    double hOuter = 5.7 + 3.8 * windSpeedMS;
    double meanTemp = (processTempC + targetSurfaceTemp) / 2.0;
    double kInsulation = material.getConductivity(meanTemp);

    // Cylindrical geometry: iterative (try thicknesses from 25mm to 300mm)
    double pipeRadiusM = pipeODMm / 2000.0;
    double bestThickness = 25.0;

    for (double tMm = 25.0; tMm <= 300.0; tMm += 25.0) {
      double insRadiusM = pipeRadiusM + tMm / 1000.0;
      double rInsulation = Math.log(insRadiusM / pipeRadiusM) / (2.0 * Math.PI * kInsulation);
      double rConvection = 1.0 / (2.0 * Math.PI * insRadiusM * hOuter);
      double qPerMeter = (processTempC - ambientTempC) / (rInsulation + rConvection);
      double surfaceTemp = ambientTempC + qPerMeter * rConvection;

      if (processTempC > ambientTempC && surfaceTemp <= targetSurfaceTemp) {
        bestThickness = tMm;
        break;
      } else if (processTempC < ambientTempC && surfaceTemp >= targetSurfaceTemp) {
        bestThickness = tMm;
        break;
      }
      bestThickness = tMm;
    }

    return bestThickness;
  }

  /**
   * Calculate heat loss from an insulated pipe per meter of length.
   *
   * @param processTempC process temperature in Celsius
   * @param ambientTempC ambient temperature in Celsius
   * @param pipeODMm pipe outer diameter in mm
   * @param insulationThicknessMm insulation thickness in mm
   * @param material insulation material
   * @param windSpeedMS wind speed in m/s
   * @return heat loss in W/m
   */
  public static double pipeHeatLossPerMeter(double processTempC, double ambientTempC,
      double pipeODMm, double insulationThicknessMm, InsulationMaterial material,
      double windSpeedMS) {
    double pipeRadiusM = pipeODMm / 2000.0;
    double insRadiusM = pipeRadiusM + insulationThicknessMm / 1000.0;
    double meanTemp = (processTempC + ambientTempC) / 2.0;
    double kIns = material.getConductivity(meanTemp);
    double hOuter = 5.7 + 3.8 * windSpeedMS;

    double rInsulation = Math.log(insRadiusM / pipeRadiusM) / (2.0 * Math.PI * kIns);
    double rConvection = 1.0 / (2.0 * Math.PI * insRadiusM * hOuter);

    return Math.abs(processTempC - ambientTempC) / (rInsulation + rConvection);
  }

  /**
   * Calculate insulation weight per meter of pipe.
   *
   * @param pipeODMm pipe outer diameter in mm
   * @param insulationThicknessMm insulation thickness in mm
   * @param material insulation material
   * @return weight in kg/m (insulation only, excludes cladding)
   */
  public static double pipeInsulationWeightPerMeter(double pipeODMm, double insulationThicknessMm,
      InsulationMaterial material) {
    double pipeRadiusM = pipeODMm / 2000.0;
    double insRadiusM = pipeRadiusM + insulationThicknessMm / 1000.0;
    double areaM2 = Math.PI * (insRadiusM * insRadiusM - pipeRadiusM * pipeRadiusM);
    double claddingWeight = 2.0 * Math.PI * insRadiusM * 0.0007 * 7850.0; // 0.7mm steel cladding
    return areaM2 * material.getTypicalDensityKgM3() + claddingWeight;
  }

  /**
   * Select recommended insulation material based on process temperature and purpose.
   *
   * @param processTempC process temperature in Celsius
   * @param purpose insulation purpose
   * @return recommended insulation material
   */
  public static InsulationMaterial selectMaterial(double processTempC, InsulationPurpose purpose) {
    if (processTempC < -100.0) {
      return InsulationMaterial.EXPANDED_PERLITE;
    } else if (processTempC < -40.0) {
      return InsulationMaterial.CELLULAR_GLASS;
    } else if (processTempC < 100.0 && purpose != InsulationPurpose.FIRE_PROTECTION) {
      return InsulationMaterial.PIR_FOAM;
    } else if (processTempC > 500.0) {
      return InsulationMaterial.CALCIUM_SILICATE;
    } else {
      return InsulationMaterial.MINERAL_WOOL;
    }
  }
}
