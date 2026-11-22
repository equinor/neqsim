package neqsim.process.mechanicaldesign.subsea;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Screening estimate of the shut-in tubing head pressure (SITHP) of a gas or gas-condensate well and classification of
 * the pressure rating needed by the subsea tree and tie-back system.
 *
 * <p>
 * The estimate integrates the static fluid column from the reservoir to the seabed. At each step the fluid density is
 * taken from a TP flash at the local pressure and a linear temperature profile, so the compressibility and phase
 * behaviour of the actual fluid are used instead of an average gradient. The method ignores friction (no flow), annulus
 * effects and temperature perturbation by the well history, so it is intended for early design-basis work such as
 * deciding whether a 10 ksi or 15 ksi system, or a high integrity pressure protection system (API STD 17O), is needed.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ShutInPressureEstimator {
  /** Standard gravity in m/s2. */
  private static final double GRAVITY = 9.80665;

  /** Pressure rating of a 5 ksi system in bara. */
  public static final double RATING_5KSI_BARA = 344.7;

  /** Pressure rating of a 10 ksi system in bara. */
  public static final double RATING_10KSI_BARA = 689.5;

  /** Pressure rating of a 15 ksi system in bara. */
  public static final double RATING_15KSI_BARA = 1034.2;

  /** Pressure rating of a 20 ksi system in bara. */
  public static final double RATING_20KSI_BARA = 1379.0;

  /**
   * Private constructor for a static utility class.
   */
  private ShutInPressureEstimator() {
  }

  /**
   * Estimates the shut-in tubing head pressure by integrating the static fluid column.
   *
   * @param fluid fluid with components and mixing rule set; it is cloned and not modified
   * @param reservoirPressureBara pressure at the reservoir datum in bara
   * @param reservoirTemperatureK temperature at the reservoir datum in K
   * @param seabedTemperatureK temperature at the seabed in K
   * @param columnHeightM vertical height from the reservoir datum to the seabed in m
   * @param steps number of integration steps, at least 1
   * @return shut-in pressure at the seabed in bara
   * @throws IllegalArgumentException if an input is not physical
   */
  public static double estimateShutInTubingHeadPressure(SystemInterface fluid, double reservoirPressureBara,
      double reservoirTemperatureK, double seabedTemperatureK, double columnHeightM, int steps) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (reservoirPressureBara <= 0.0 || columnHeightM < 0.0 || steps < 1) {
      throw new IllegalArgumentException(
          "reservoir pressure must be positive, column height non-negative and steps at least 1");
    }
    double dz = columnHeightM / steps;
    double pressure = reservoirPressureBara;
    for (int i = 0; i < steps; i++) {
      double fractionAboveDatum = (i + 0.5) / steps;
      double temperature = reservoirTemperatureK - (reservoirTemperatureK - seabedTemperatureK) * fractionAboveDatum;
      SystemInterface local = fluid.clone();
      local.setTemperature(temperature);
      local.setPressure(pressure);
      ThermodynamicOperations ops = new ThermodynamicOperations(local);
      ops.TPflash();
      local.initProperties();
      double density = local.getDensity("kg/m3");
      pressure -= density * GRAVITY * dz / 1.0e5;
      if (pressure <= 0.0) {
        return 0.0;
      }
    }
    return pressure;
  }

  /**
   * Classifies the pressure rating class needed for a shut-in pressure.
   *
   * @param pressureBara shut-in pressure in bara
   * @param marginFraction required margin below the rating as a fraction (for example 0.05 for 5 percent)
   * @return the lowest standard rating class that covers the pressure with the margin: "5 ksi", "10 ksi", "15 ksi", "20
   * ksi" or "above 20 ksi"
   */
  public static String classifyPressureRating(double pressureBara, double marginFraction) {
    double required = pressureBara * (1.0 + Math.max(marginFraction, 0.0));
    if (required <= RATING_5KSI_BARA) {
      return "5 ksi";
    }
    if (required <= RATING_10KSI_BARA) {
      return "10 ksi";
    }
    if (required <= RATING_15KSI_BARA) {
      return "15 ksi";
    }
    if (required <= RATING_20KSI_BARA) {
      return "20 ksi";
    }
    return "above 20 ksi";
  }
}
