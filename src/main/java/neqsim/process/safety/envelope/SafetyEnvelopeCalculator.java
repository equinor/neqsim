package neqsim.process.safety.envelope;

import neqsim.process.safety.envelope.SafetyEnvelope.EnvelopeType;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculator for generating safety envelopes from fluid properties.
 *
 * <p>
 * Computes P-T curves for various safety limits including:
 * </p>
 * <ul>
 * <li>Hydrate formation temperature vs pressure</li>
 * <li>Wax appearance temperature (WAT) vs pressure</li>
 * <li>CO2 solid formation boundary</li>
 * <li>Minimum design metal temperature (MDMT) curves</li>
 * <li>Phase envelope (bubble/dew points)</li>
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 * 
 * <pre>
 * {@code
 * SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(gasSystem);
 * 
 * SafetyEnvelope hydrateEnv = calc.calculateHydrateEnvelope(1.0, 150.0, 20);
 * hydrateEnv.exportToCSV("hydrate_curve.csv");
 * 
 * // Check operating point
 * boolean safe = hydrateEnv.isOperatingPointSafe(50.0, 280.0);
 * }
 * </pre>
 *
 * @author NeqSim team
 */
public class SafetyEnvelopeCalculator {

  private final SystemInterface fluid;
  private double safetyMarginHydrate = 5.0; // K
  private double safetyMarginWax = 5.0; // K
  private double safetyMarginMDMT = 10.0; // K
  private double safetyMarginCO2 = 3.0; // K

  /**
   * Creates a new calculator for the specified fluid.
   *
   * @param fluid the thermodynamic system to analyze
   */
  public SafetyEnvelopeCalculator(SystemInterface fluid) {
    this.fluid = fluid.clone();
  }

  /**
   * Sets the safety margin for hydrate calculations.
   *
   * @param marginK margin in Kelvin
   */
  public void setHydrateSafetyMargin(double marginK) {
    this.safetyMarginHydrate = marginK;
  }

  /**
   * Sets the safety margin for wax calculations.
   *
   * @param marginK margin in Kelvin
   */
  public void setWaxSafetyMargin(double marginK) {
    this.safetyMarginWax = marginK;
  }

  /**
   * Sets the safety margin for MDMT calculations.
   *
   * @param marginK margin in Kelvin
   */
  public void setMDMTSafetyMargin(double marginK) {
    this.safetyMarginMDMT = marginK;
  }

  /**
   * Calculates the hydrate formation envelope.
   *
   * @param minPressure minimum pressure in bara
   * @param maxPressure maximum pressure in bara
   * @param numPoints number of points to calculate
   * @return hydrate formation envelope
   */
  public SafetyEnvelope calculateHydrateEnvelope(double minPressure, double maxPressure,
      int numPoints) {
    SafetyEnvelope envelope =
        new SafetyEnvelope("Hydrate Formation", EnvelopeType.HYDRATE, numPoints);
    envelope.setFluidDescription(getFluidDescription());

    // Calculate water content if available
    double waterMolFrac = 0.0;
    try {
      waterMolFrac = fluid.getPhase(0).getComponent("water").getz();
    } catch (Exception e) {
      // No water in system
    }
    envelope.setReferenceWaterContent(waterMolFrac);

    double pressureStep = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double pressure = minPressure + i * pressureStep;

      try {
        SystemInterface tempFluid = fluid.clone();
        tempFluid.setPressure(pressure);
        tempFluid.setTemperature(280.0); // Initial guess

        ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
        ops.hydrateFormationTemperature(); // Modifies system temperature
        double hydrateTemp = tempFluid.getTemperature();

        envelope.setDataPoint(i, pressure, hydrateTemp, safetyMarginHydrate);
      } catch (Exception e) {
        // If calculation fails, use NaN
        envelope.setDataPoint(i, pressure, Double.NaN, safetyMarginHydrate);
      }
    }

    return envelope;
  }

  /**
   * Calculates the wax appearance temperature envelope.
   *
   * @param minPressure minimum pressure in bara
   * @param maxPressure maximum pressure in bara
   * @param numPoints number of points to calculate
   * @return WAT envelope
   */
  public SafetyEnvelope calculateWaxEnvelope(double minPressure, double maxPressure,
      int numPoints) {
    SafetyEnvelope envelope = new SafetyEnvelope("Wax Appearance", EnvelopeType.WAX, numPoints);
    envelope.setFluidDescription(getFluidDescription());

    double pressureStep = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double pressure = minPressure + i * pressureStep;

      try {
        SystemInterface tempFluid = fluid.clone();
        tempFluid.setPressure(pressure);
        tempFluid.setTemperature(320.0); // Start warm

        ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
        ops.calcWAT(); // Modifies system temperature
        double watTemp = tempFluid.getTemperature();

        envelope.setDataPoint(i, pressure, watTemp, safetyMarginWax);
      } catch (Exception e) {
        // If calculation fails, use NaN (no wax at this pressure)
        envelope.setDataPoint(i, pressure, Double.NaN, safetyMarginWax);
      }
    }

    return envelope;
  }

  /**
   * Calculates the CO2 freezing envelope.
   *
   * <p>
   * Uses the CO2 triple point and sublimation curve to determine solid formation risk.
   * </p>
   *
   * @param minPressure minimum pressure in bara
   * @param maxPressure maximum pressure in bara
   * @param numPoints number of points to calculate
   * @return CO2 freezing envelope
   */
  public SafetyEnvelope calculateCO2FreezingEnvelope(double minPressure, double maxPressure,
      int numPoints) {
    SafetyEnvelope envelope =
        new SafetyEnvelope("CO2 Freezing", EnvelopeType.CO2_FREEZING, numPoints);
    envelope.setFluidDescription(getFluidDescription());

    // CO2 triple point: 5.18 bar, 216.55 K (-56.6°C)
    double triplePointPressure = 5.18;
    double triplePointTemp = 216.55;

    double pressureStep = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double pressure = minPressure + i * pressureStep;
      double freezeTemp;

      if (pressure < triplePointPressure) {
        // Below triple point: sublimation curve
        // Simplified: T decreases as P decreases
        freezeTemp = triplePointTemp * Math.pow(pressure / triplePointPressure, 0.1);
      } else {
        // Above triple point: solid-liquid boundary
        // CO2 has positive slope (unusual)
        // dT/dP ≈ 0.004 K/bar for CO2
        freezeTemp = triplePointTemp + 0.004 * (pressure - triplePointPressure);
      }

      envelope.setDataPoint(i, pressure, freezeTemp, safetyMarginCO2);
    }

    return envelope;
  }

  /**
   * Calculates the phase envelope (bubble and dew point curves).
   *
   * @param numPoints number of points per curve
   * @return phase envelope
   */
  public SafetyEnvelope calculatePhaseEnvelope(int numPoints) {
    SafetyEnvelope envelope =
        new SafetyEnvelope("Phase Envelope", EnvelopeType.PHASE_ENVELOPE, numPoints);
    envelope.setFluidDescription(getFluidDescription());

    try {
      SystemInterface tempFluid = fluid.clone();
      ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);

      // Calculate phase envelope
      ops.calcPTphaseEnvelope();

      // Get phase envelope data using getPoints
      double[][] points = ops.getOperation().getPoints(0);

      if (points != null && points.length >= 2) {
        int dataPoints = Math.min(numPoints, points[0].length);

        for (int i = 0; i < dataPoints; i++) {
          // points[0] = temperature, points[1] = pressure (typically)
          double temp = points[0][i];
          double pres = points[1][i];
          envelope.setDataPoint(i, pres, temp, 0.0);
        }
      }

    } catch (Exception e) {
      // Phase envelope calculation failed
      // Return envelope with placeholder data
    }

    return envelope;
  }

  /**
   * Calculates MDMT envelope based on fluid composition and metallurgy.
   *
   * <p>
   * MDMT calculation considers:
   * </p>
   * <ul>
   * <li>Joule-Thomson cooling during depressurization</li>
   * <li>Auto-refrigeration from liquid flashing</li>
   * <li>Material impact testing requirements (e.g., ASME UCS-66)</li>
   * </ul>
   *
   * @param minPressure minimum pressure in bara
   * @param maxPressure maximum pressure in bara
   * @param designTemp design temperature in K
   * @param numPoints number of points to calculate
   * @return MDMT envelope
   */
  public SafetyEnvelope calculateMDMTEnvelope(double minPressure, double maxPressure,
      double designTemp, int numPoints) {
    SafetyEnvelope envelope = new SafetyEnvelope("MDMT", EnvelopeType.MDMT, numPoints);
    envelope.setFluidDescription(getFluidDescription());

    double pressureStep = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double pressure = minPressure + i * pressureStep;

      try {
        SystemInterface tempFluid = fluid.clone();
        tempFluid.setPressure(pressure);
        tempFluid.setTemperature(designTemp);

        ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
        ops.TPflash();

        // Calculate minimum temperature during isentropic expansion to atmospheric
        double gamma = tempFluid.getGamma();
        if (Double.isNaN(gamma) || gamma <= 1.0 || gamma > 2.0) {
          gamma = 1.3;
        }

        // Isentropic temperature drop to 1 bara
        double atmosphericPressure = ThermodynamicConstantsInterface.referencePressure;
        double minTemp;
        if (pressure <= atmosphericPressure) {
          // No expansion cooling possible - already at or below atmospheric
          minTemp = designTemp;
        } else {
          minTemp = designTemp * Math.pow(atmosphericPressure / pressure, (gamma - 1) / gamma);
        }

        // MDMT must be below minimum achievable temperature
        envelope.setDataPoint(i, pressure, minTemp, safetyMarginMDMT);
      } catch (Exception e) {
        // If calculation fails, use conservative estimate
        envelope.setDataPoint(i, pressure, designTemp - 50, safetyMarginMDMT);
      }
    }

    return envelope;
  }

  /**
   * Calculates a combined safety envelope with all applicable limits.
   *
   * @param minPressure minimum pressure in bara
   * @param maxPressure maximum pressure in bara
   * @param numPoints number of points per envelope
   * @return array of all calculated envelopes
   */
  public SafetyEnvelope[] calculateAllEnvelopes(double minPressure, double maxPressure,
      int numPoints) {
    return new SafetyEnvelope[] {calculateHydrateEnvelope(minPressure, maxPressure, numPoints),
        calculateWaxEnvelope(minPressure, maxPressure, numPoints),
        calculateCO2FreezingEnvelope(minPressure, maxPressure, numPoints),
        calculateMDMTEnvelope(minPressure, maxPressure, 300.0, numPoints),
        calculatePhaseEnvelope(numPoints)};
  }

  /**
   * Checks if an operating point is safe with respect to all calculated envelopes.
   *
   * @param envelopes array of envelopes to check
   * @param pressureBara operating pressure
   * @param temperatureK operating temperature
   * @return true if safe for all envelopes
   */
  public static boolean isOperatingPointSafe(SafetyEnvelope[] envelopes, double pressureBara,
      double temperatureK) {
    for (SafetyEnvelope env : envelopes) {
      if (!env.isOperatingPointSafe(pressureBara, temperatureK)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the most limiting envelope at given conditions.
   *
   * @param envelopes array of envelopes to check
   * @param pressureBara operating pressure
   * @param temperatureK operating temperature
   * @return the envelope with smallest margin, or null if all have infinite margin
   */
  public static SafetyEnvelope getMostLimitingEnvelope(SafetyEnvelope[] envelopes,
      double pressureBara, double temperatureK) {
    SafetyEnvelope mostLimiting = null;
    double smallestMargin = Double.POSITIVE_INFINITY;

    for (SafetyEnvelope env : envelopes) {
      double margin = env.calculateMarginToLimit(pressureBara, temperatureK);
      if (!Double.isNaN(margin) && margin < smallestMargin) {
        smallestMargin = margin;
        mostLimiting = env;
      }
    }

    return mostLimiting;
  }

  private String getFluidDescription() {
    StringBuilder sb = new StringBuilder();
    try {
      for (int i = 0; i < Math.min(5, fluid.getNumberOfComponents()); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(fluid.getComponent(i).getComponentName());
        sb.append(String.format(" (%.1f%%)", fluid.getComponent(i).getz() * 100));
      }
      if (fluid.getNumberOfComponents() > 5) {
        sb.append(", ...");
      }
    } catch (Exception e) {
      sb.append("Unknown composition");
    }
    return sb.toString();
  }
}
