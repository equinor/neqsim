package neqsim.process.mechanicaldesign.designstandards;

import java.io.Serializable;

/**
 * Equipment noise prediction per NORSOK S-002 and IEC 61672.
 *
 * <p>
 * Estimates sound power level (SWL) and sound pressure level (SPL) for common process equipment
 * types including valves, compressors, pumps, flares, and piping. Supports aggregate noise
 * assessment at a receiver location. Based on correlations from API 521 (flares), IEC 60534-8-3
 * (valves), and NORSOK S-002 (general equipment).
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class NoiseAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Maximum allowable noise level for personnel protection in dB(A). NORSOK S-002 8-hr. */
  public static final double NORSOK_MAX_CONTINUOUS_DBA = 83.0;

  /** Maximum peak noise for equipment areas in dB(A). */
  public static final double NORSOK_MAX_EQUIPMENT_AREA_DBA = 90.0;

  /** Reference pressure for SPL calculation in Pa (20 micropascals). */
  private static final double P_REF = 2.0e-5;

  /** Reference power for SWL calculation in Watts. */
  private static final double W_REF = 1.0e-12;

  /**
   * Estimate valve noise per IEC 60534-8-3.
   *
   * @param massFlowKgS mass flow through valve in kg/s
   * @param upstreamPressureBar upstream pressure in bara
   * @param downstreamPressureBar downstream pressure in bara
   * @param molecularWeight molecular weight in kg/kmol
   * @param temperatureK temperature in K
   * @param pipeDiameterM downstream pipe diameter in meters
   * @return sound pressure level at 1m downstream in dB(A)
   */
  public static double valveNoise(double massFlowKgS, double upstreamPressureBar,
      double downstreamPressureBar, double molecularWeight, double temperatureK,
      double pipeDiameterM) {
    double pressureRatio = upstreamPressureBar / Math.max(downstreamPressureBar, 0.01);

    // Simplified IEC 60534-8-3 correlation
    // Acoustic power ~ m_dot * (dP/P1)^3.6 * c / (rho * d^2)
    double deltaP = upstreamPressureBar - downstreamPressureBar;
    double sonicVelocity = Math.sqrt(1.3 * 8314.0 * temperatureK / molecularWeight);
    double density = (upstreamPressureBar * 1.0e5 * molecularWeight) / (8314.0 * temperatureK);

    double mechanicalStreamPower = massFlowKgS * deltaP * 1.0e5 / Math.max(density, 0.01);
    double acousticEfficiency;

    if (pressureRatio < 2.0) {
      acousticEfficiency = 1.0e-4 * Math.pow(pressureRatio - 1.0, 2);
    } else {
      acousticEfficiency = 1.0e-3 * (pressureRatio - 1.0);
    }
    acousticEfficiency = Math.min(acousticEfficiency, 0.01);

    double acousticPowerW = mechanicalStreamPower * acousticEfficiency;
    double swlDb = 10.0 * Math.log10(Math.max(acousticPowerW, W_REF) / W_REF);

    // SPL at 1m: assume hemispherical radiation, pipe transmission loss ~10 dB
    double pipeTransmissionLoss = 10.0 + 10.0 * Math.log10(Math.max(pipeDiameterM * 0.003, 0.001));
    double spl1m = swlDb - 10.0 * Math.log10(2.0 * Math.PI) - pipeTransmissionLoss;

    return Math.max(spl1m, 0.0);
  }

  /**
   * Estimate compressor noise per NORSOK S-002 / API 617 guidelines.
   *
   * @param powerKW shaft power in kW
   * @param compressorType "CENTRIFUGAL", "RECIPROCATING", or "SCREW"
   * @return sound pressure level at 1m in dB(A)
   */
  public static double compressorNoise(double powerKW, String compressorType) {
    // Based on NORSOK S-002 correlations
    // Lw = 95 + 10*log10(P_kW) for centrifugal (typical)
    double baseLw;
    if ("RECIPROCATING".equals(compressorType)) {
      baseLw = 100.0 + 10.0 * Math.log10(Math.max(powerKW, 1.0));
    } else if ("SCREW".equals(compressorType)) {
      baseLw = 97.0 + 10.0 * Math.log10(Math.max(powerKW, 1.0));
    } else {
      baseLw = 95.0 + 10.0 * Math.log10(Math.max(powerKW, 1.0));
    }
    // SPL at 1m from SWL (hemispherical)
    return baseLw - 10.0 * Math.log10(2.0 * Math.PI);
  }

  /**
   * Estimate pump noise.
   *
   * @param powerKW pump power in kW
   * @param pumpType "CENTRIFUGAL" or "POSITIVE_DISPLACEMENT"
   * @return sound pressure level at 1m in dB(A)
   */
  public static double pumpNoise(double powerKW, String pumpType) {
    double baseLw;
    if ("POSITIVE_DISPLACEMENT".equals(pumpType)) {
      baseLw = 98.0 + 10.0 * Math.log10(Math.max(powerKW, 1.0));
    } else {
      baseLw = 92.0 + 10.0 * Math.log10(Math.max(powerKW, 1.0));
    }
    return baseLw - 10.0 * Math.log10(2.0 * Math.PI);
  }

  /**
   * Estimate flare noise per API 521.
   *
   * @param heatReleaseMW total heat release in MW
   * @param tipDiameterM flare tip diameter in meters
   * @param exitVelocityMS exit gas velocity in m/s
   * @return sound pressure level at 100m in dB(A)
   */
  public static double flareNoise(double heatReleaseMW, double tipDiameterM,
      double exitVelocityMS) {
    // API 521 correlation: Lw = 119.054 + 23.8 * log10(Uj * D) (combustion noise)
    // where Uj = exit velocity in ft/s, D = diameter in feet
    double uj_ft = exitVelocityMS * 3.2808;
    double d_ft = tipDiameterM * 3.2808;
    double lwCombustion = 119.054 + 23.8 * Math.log10(Math.max(uj_ft * d_ft, 0.01));

    // Jet noise: Lw = 126.154 + 10 * log10(D^2 * Uj^4 * rho / c^4)
    // Simplified: dominates at high velocities
    double lwJet = 86.0 + 40.0 * Math.log10(Math.max(exitVelocityMS, 1.0))
        + 20.0 * Math.log10(Math.max(tipDiameterM, 0.01));

    double totalLw =
        10.0 * Math.log10(Math.pow(10.0, lwCombustion / 10.0) + Math.pow(10.0, lwJet / 10.0));

    // SPL at 100m (spherical radiation + atmospheric absorption)
    double spl100m = totalLw - 20.0 * Math.log10(100.0) - 10.0 * Math.log10(4.0 * Math.PI);

    return Math.max(spl100m, 0.0);
  }

  /**
   * Calculate aggregate noise level from multiple sources at a receiver point.
   *
   * @param splValues array of individual SPL values in dB(A)
   * @return combined SPL in dB(A)
   */
  public static double aggregateNoise(double[] splValues) {
    if (splValues == null || splValues.length == 0) {
      return 0.0;
    }
    double sumIntensity = 0.0;
    for (double spl : splValues) {
      sumIntensity += Math.pow(10.0, spl / 10.0);
    }
    return 10.0 * Math.log10(sumIntensity);
  }

  /**
   * Estimate SPL at a given distance from a known SPL at 1m.
   *
   * @param spl1m SPL at 1 meter in dB(A)
   * @param distanceM distance to receiver in meters
   * @return SPL at the distance in dB(A)
   */
  public static double splAtDistance(double spl1m, double distanceM) {
    if (distanceM <= 0) {
      return spl1m;
    }
    // Geometric divergence (hemispherical)
    return spl1m - 20.0 * Math.log10(Math.max(distanceM, 1.0));
  }

  /**
   * Checks whether the computed noise level exceeds the NORSOK S-002 limit.
   *
   * @param splDbA sound pressure level in dB(A)
   * @return true if the level exceeds 83 dB(A) continuous exposure limit
   */
  public static boolean exceedsNorsokLimit(double splDbA) {
    return splDbA > NORSOK_MAX_CONTINUOUS_DBA;
  }
}
