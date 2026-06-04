package neqsim.pvtsimulation.simulation;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SaturationPressure class.
 * </p>
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

  /**
   * <p>
   * Constructor for SaturationPressure.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturationTemperature(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * calcSaturationPressure.
   * </p>
   *
   * @return a double
   */
  public double calcSaturationTemperature() {
    boolean isMultiPhaseCheckChanged = false;
    if (!getThermoSystem().doMultiPhaseCheck()) {
      isMultiPhaseCheckChanged = true;
      getThermoSystem().setMultiPhaseCheck(true);
    }

    try {
      double twoPhaseTemperature = Double.NaN;
      double singlePhaseTemperature = Double.NaN;
      double previousTemperature = MINIMUM_SEARCH_TEMPERATURE_K;
      boolean previousIsTwoPhase = isTwoPhaseAtTemperature(previousTemperature);

      for (double trialTemperature = previousTemperature
          + SEARCH_TEMPERATURE_STEP_K; trialTemperature <= MAXIMUM_SEARCH_TEMPERATURE_K; trialTemperature +=
              SEARCH_TEMPERATURE_STEP_K) {
        boolean trialIsTwoPhase = isTwoPhaseAtTemperature(trialTemperature);
        if (previousIsTwoPhase && !trialIsTwoPhase) {
          twoPhaseTemperature = previousTemperature;
          singlePhaseTemperature = trialTemperature;
        }
        previousTemperature = trialTemperature;
        previousIsTwoPhase = trialIsTwoPhase;
      }

      if (previousTemperature < MAXIMUM_SEARCH_TEMPERATURE_K) {
        boolean trialIsTwoPhase = isTwoPhaseAtTemperature(MAXIMUM_SEARCH_TEMPERATURE_K);
        if (previousIsTwoPhase && !trialIsTwoPhase) {
          twoPhaseTemperature = previousTemperature;
          singlePhaseTemperature = MAXIMUM_SEARCH_TEMPERATURE_K;
        }
      }

      if (Double.isNaN(twoPhaseTemperature) || Double.isNaN(singlePhaseTemperature)) {
        getThermoSystem().setTemperature(MAXIMUM_SEARCH_TEMPERATURE_K);
        thermoOps.TPflash();
        return getThermoSystem().getTemperature();
      }

      return refineUpperSaturationTemperature(twoPhaseTemperature, singlePhaseTemperature);
    } finally {
      if (isMultiPhaseCheckChanged) {
        getThermoSystem().setMultiPhaseCheck(false);
      }
    }
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
   * @param singlePhaseTemperature higher temperature known to be outside the two-phase region, in
   *        kelvin
   * @return refined upper saturation temperature in kelvin
   */
  private double refineUpperSaturationTemperature(double twoPhaseTemperature,
      double singlePhaseTemperature) {
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
    } while (Math.abs(maxTemp - minTemp) > TEMPERATURE_TOLERANCE_K
        && iteration < MAXIMUM_BISECTION_ITERATIONS);
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
   * <p>
   * main.
   * </p>
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
     * TuningInterface tuning = new TuneToSaturation(satPresSim);
     * tuning.setSaturationConditions(saturationTemperature, saturationPressure); tuning.run();
     * tuning.getSimulation().getThermoSystem().display();
     */
  }
}
