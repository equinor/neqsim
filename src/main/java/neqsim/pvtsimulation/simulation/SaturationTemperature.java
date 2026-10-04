package neqsim.pvtsimulation.simulation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * SaturationTemperature class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SaturationTemperature extends BasePVTsimulation {
  /** Minimum temperature searched for saturation boundaries, in kelvin. */
  private static final double MINIMUM_SEARCH_TEMPERATURE_K = 30.0;
  /** Maximum temperature searched for saturation boundaries, in kelvin. */
  private static final double MAXIMUM_SEARCH_TEMPERATURE_K = 1200.0;
  /** Coarse temperature step used to bracket saturation boundaries, in kelvin. */
  private static final double SEARCH_TEMPERATURE_STEP_K = 10.0;
  /** Temperature tolerance used when refining the saturation boundary, in kelvin. */
  private static final double TEMPERATURE_TOLERANCE_K = 1.0e-5;
  /** Maximum bisection iterations used when refining the saturation boundary. */
  private static final int MAXIMUM_BISECTION_ITERATIONS = 500;
  /** Lower bound of an explicitly requested tuning search, in kelvin. */
  private double minimumSearchTemperature = MINIMUM_SEARCH_TEMPERATURE_K;
  /** Upper bound of an explicitly requested tuning search, in kelvin. */
  private double maximumSearchTemperature = MAXIMUM_SEARCH_TEMPERATURE_K;

  /**
   * Constructor for SaturationTemperature.
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturationTemperature(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * Sets a bounded temperature search for repeated saturation-temperature tuning.
   *
   * <p>
   * The search returns the uppermost crossing resolved by the 10 K grid inside these bounds. The caller must establish
   * that this interval contains the desired upper boundary throughout the tuning range. A local bracket cannot exclude
   * a disconnected two-phase region above the supplied maximum. Use the default global search when that assumption is
   * not justified. No flash results are cached, so changes to composition or pressure are evaluated on every call.
   * </p>
   *
   * <p>
   * If the upper endpoint is multiphase or no crossing is found, the calculation falls back to the full 30-1200 K
   * search. The same bounds are used on subsequent calls; they are not silently moved to a previous solution.
   * </p>
   *
   * @param minimumTemperature lower search bound in kelvin, at least 30 K
   * @param maximumTemperature upper search bound in kelvin, at most 1200 K and above the lower bound
   * @throws IllegalArgumentException if either bound is non-finite or outside the supported range
   */
  public void setTemperatureSearchBounds(double minimumTemperature, double maximumTemperature) {
    if (!Double.isFinite(minimumTemperature) || !Double.isFinite(maximumTemperature)
        || minimumTemperature < MINIMUM_SEARCH_TEMPERATURE_K || maximumTemperature > MAXIMUM_SEARCH_TEMPERATURE_K
        || minimumTemperature >= maximumTemperature) {
      throw new IllegalArgumentException("Temperature search bounds must satisfy 30 <= minimum < maximum <= 1200 K");
    }
    minimumSearchTemperature = minimumTemperature;
    maximumSearchTemperature = maximumTemperature;
  }

  /** Restores the default global search for the uppermost saturation boundary. */
  public void clearTemperatureSearchBounds() {
    minimumSearchTemperature = MINIMUM_SEARCH_TEMPERATURE_K;
    maximumSearchTemperature = MAXIMUM_SEARCH_TEMPERATURE_K;
  }

  /**
   * Calculates the upper saturation temperature using the global grid or explicit tuning bounds.
   *
   * @return saturation temperature in kelvin, or 1200 K if the global grid finds no boundary
   */
  public double calcSaturationTemperature() {
    boolean isMultiPhaseCheckChanged = false;
    if (!getThermoSystem().doMultiPhaseCheck()) {
      isMultiPhaseCheckChanged = true;
      getThermoSystem().setMultiPhaseCheck(true);
    }

    try {
      boolean boundedSearch = minimumSearchTemperature != MINIMUM_SEARCH_TEMPERATURE_K
          || maximumSearchTemperature != MAXIMUM_SEARCH_TEMPERATURE_K;
      if (boundedSearch) {
        double result = searchUpperSaturationTemperature(minimumSearchTemperature, maximumSearchTemperature, true);
        if (!Double.isNaN(result)) {
          return result;
        }
      }

      double result = searchUpperSaturationTemperature(MINIMUM_SEARCH_TEMPERATURE_K, MAXIMUM_SEARCH_TEMPERATURE_K,
          false);
      if (Double.isNaN(result)) {
        getThermoSystem().setTemperature(MAXIMUM_SEARCH_TEMPERATURE_K);
        thermoOps.TPflash();
        return getThermoSystem().getTemperature();
      }
      return result;
    } finally {
      if (isMultiPhaseCheckChanged) {
        getThermoSystem().setMultiPhaseCheck(false);
      }
    }
  }

  /**
   * Searches downward on the original global grid, also checking both interval endpoints.
   *
   * @param minimumTemperature lower search bound in kelvin
   * @param maximumTemperature upper search bound in kelvin
   * @param requireSinglePhaseUpperEndpoint reject a bounded interval whose upper endpoint is multiphase
   * @return refined upper boundary, or NaN when the interval does not bracket one
   */
  private double searchUpperSaturationTemperature(double minimumTemperature, double maximumTemperature,
      boolean requireSinglePhaseUpperEndpoint) {
    double higherTemperature = maximumTemperature;
    boolean higherIsTwoPhase = isTwoPhaseAtTemperature(higherTemperature);
    if (requireSinglePhaseUpperEndpoint && higherIsTwoPhase) {
      return Double.NaN;
    }
    // Retain the global grid's resolution and alignment, including for retrograde fluids.
    double trialTemperature = MINIMUM_SEARCH_TEMPERATURE_K + SEARCH_TEMPERATURE_STEP_K
        * Math.floor((maximumTemperature - MINIMUM_SEARCH_TEMPERATURE_K) / SEARCH_TEMPERATURE_STEP_K);
    if (trialTemperature >= higherTemperature) {
      trialTemperature -= SEARCH_TEMPERATURE_STEP_K;
    }
    while (higherTemperature > minimumTemperature) {
      trialTemperature = Math.max(minimumTemperature, trialTemperature);
      boolean trialIsTwoPhase = isTwoPhaseAtTemperature(trialTemperature);
      if (trialIsTwoPhase && !higherIsTwoPhase) {
        return refineUpperSaturationTemperature(trialTemperature, higherTemperature);
      }
      higherTemperature = trialTemperature;
      higherIsTwoPhase = trialIsTwoPhase;
      trialTemperature -= SEARCH_TEMPERATURE_STEP_K;
    }
    return Double.NaN;
  }

  /**
   * Checks whether the system is multiphase at a trial temperature.
   *
   * @param trialTemperature temperature to test in kelvin
   * @return true when the TP flash gives more than one phase
   */
  private boolean isTwoPhaseAtTemperature(double trialTemperature) {
    getThermoSystem().setTemperature(trialTemperature);
    thermoOps.TPflash();
    return getThermoSystem().getNumberOfPhases() > 1;
  }

  /**
   * Refines the upper saturation temperature between a two-phase and a single-phase point.
   *
   * @param twoPhaseTemperature lower temperature known to be inside a two-phase region, in kelvin
   * @param singlePhaseTemperature higher temperature known to be outside the two-phase region, in kelvin
   * @return refined upper saturation temperature in kelvin
   */
  private double refineUpperSaturationTemperature(double twoPhaseTemperature, double singlePhaseTemperature) {
    double minTemp = twoPhaseTemperature;
    double maxTemp = singlePhaseTemperature;
    int iteration = 0;
    do {
      iteration++;
      double trialTemperature = (minTemp + maxTemp) / 2.0;
      if (isTwoPhaseAtTemperature(trialTemperature)) {
        minTemp = trialTemperature;
      } else {
        maxTemp = trialTemperature;
      }
    } while (Math.abs(maxTemp - minTemp) > TEMPERATURE_TOLERANCE_K && iteration < MAXIMUM_BISECTION_ITERATIONS);
    getThermoSystem().setTemperature(maxTemp);
    thermoOps.TPflash();
    return getThermoSystem().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    super.run();
    saturationTemperature = calcSaturationTemperature();
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 20, 60.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 0.38);
    tempSystem.addTBPfraction("C7", 0.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 0.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 0.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.08, 135.3 / 1000.0, 0.7864);
    // tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2); // "HV", "UNIFAC_UMRPRU");
    tempSystem.init(0);
    tempSystem.init(1);
    // tempSystem.saveFluid(928);

    SimulationInterface satPresSim = new SaturationTemperature(tempSystem);
    satPresSim.run();
    // satPresSim.getThermoSystem().display();
    /*
     * double saturationPressure = 350.0; double saturationTemperature = 273.15 + 80;
     *
     * TuningInterface tuning = new TuneToSaturation(satPresSim); tuning.setSaturationConditions(saturationTemperature,
     * saturationPressure); tuning.run(); tuning.getSimulation().getThermoSystem().display();
     */
  }
}
