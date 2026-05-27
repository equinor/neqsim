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
public class SaturationPressure extends BasePVTsimulation {
  /** Minimum pressure searched for saturation boundaries, in bara. */
  private static final double MINIMUM_SEARCH_PRESSURE_BARA = 1.0;
  /** Maximum pressure searched for saturation boundaries, in bara. */
  private static final double MAXIMUM_SEARCH_PRESSURE_BARA = 1000.0;
  /** Coarse pressure step used to bracket saturation boundaries, in bara. */
  private static final double SEARCH_PRESSURE_STEP_BARA = 10.0;
  /** Pressure tolerance used when refining the saturation boundary, in bara. */
  private static final double PRESSURE_TOLERANCE_BARA = 1.0e-5;
  /** Maximum bisection iterations used when refining the saturation boundary. */
  private static final int MAXIMUM_BISECTION_ITERATIONS = 500;

  /**
   * <p>
   * Constructor for SaturationPressure.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SaturationPressure(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * <p>
   * calcSaturationPressure.
   * </p>
   *
   * @return a double
   */
  public double calcSaturationPressure() {
    if (!Double.isNaN(temperature)) {
      getThermoSystem().setTemperature(temperature, temperatureUnit);
    }

    boolean isMultiPhaseCheckChanged = false;
    if (!getThermoSystem().doMultiPhaseCheck()) {
      isMultiPhaseCheckChanged = true;
      getThermoSystem().setMultiPhaseCheck(true);
    }

    try {
      double twoPhasePressure = Double.NaN;
      double singlePhasePressure = Double.NaN;
      double previousPressure = MINIMUM_SEARCH_PRESSURE_BARA;
      boolean previousIsTwoPhase = isTwoPhaseAtPressure(previousPressure);

      for (double trialPressure = previousPressure
          + SEARCH_PRESSURE_STEP_BARA; trialPressure <= MAXIMUM_SEARCH_PRESSURE_BARA; trialPressure +=
              SEARCH_PRESSURE_STEP_BARA) {
        boolean trialIsTwoPhase = isTwoPhaseAtPressure(trialPressure);
        if (previousIsTwoPhase && !trialIsTwoPhase) {
          twoPhasePressure = previousPressure;
          singlePhasePressure = trialPressure;
        }
        previousPressure = trialPressure;
        previousIsTwoPhase = trialIsTwoPhase;
      }

      if (previousPressure < MAXIMUM_SEARCH_PRESSURE_BARA) {
        boolean trialIsTwoPhase = isTwoPhaseAtPressure(MAXIMUM_SEARCH_PRESSURE_BARA);
        if (previousIsTwoPhase && !trialIsTwoPhase) {
          twoPhasePressure = previousPressure;
          singlePhasePressure = MAXIMUM_SEARCH_PRESSURE_BARA;
        }
      }

      if (Double.isNaN(twoPhasePressure) || Double.isNaN(singlePhasePressure)) {
        getThermoSystem().setPressure(MAXIMUM_SEARCH_PRESSURE_BARA);
        thermoOps.TPflash();
        return getThermoSystem().getPressure();
      }

      return refineUpperSaturationPressure(twoPhasePressure, singlePhasePressure);
    } finally {
      if (isMultiPhaseCheckChanged) {
        getThermoSystem().setMultiPhaseCheck(false);
      }
    }
  }

  /**
   * Checks whether the system is multiphase at a trial pressure.
   *
   * @param trialPressure pressure to test in bara
   * @return true when the TP flash gives more than one phase
   */
  private boolean isTwoPhaseAtPressure(double trialPressure) {
    getThermoSystem().setPressure(trialPressure);
    thermoOps.TPflash();
    return getThermoSystem().getNumberOfPhases() > 1;
  }

  /**
   * Refines the upper saturation pressure between a two-phase and a single-phase point.
   *
   * @param twoPhasePressure lower pressure known to be inside a two-phase region, in bara
   * @param singlePhasePressure higher pressure known to be outside the two-phase region, in bara
   * @return refined upper saturation pressure in bara
   */
  private double refineUpperSaturationPressure(double twoPhasePressure,
      double singlePhasePressure) {
    double minPres = twoPhasePressure;
    double maxPres = singlePhasePressure;
    int iteration = 0;
    do {
      iteration++;
      double trialPressure = (minPres + maxPres) / 2.0;
      if (isTwoPhaseAtPressure(trialPressure)) {
        minPres = trialPressure;
      } else {
        maxPres = trialPressure;
      }
    } while (Math.abs(maxPres - minPres) > PRESSURE_TOLERANCE_BARA
        && iteration < MAXIMUM_BISECTION_ITERATIONS);
    getThermoSystem().setPressure(maxPres);
    thermoOps.TPflash();
    return getThermoSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    super.run();
    saturationPressure = calcSaturationPressure();
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
    // SystemInterface tempSystem = new SystemSrkCPAstatoil(273.15 + 120, 100.0);
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 120, 100.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 1.38);
    tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
    tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(9); // "HV", "UNIFAC_UMRPRU");
    tempSystem.init(0);
    tempSystem.init(1);
    // tempSystem.saveFluid(928);

    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
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

  /** {@inheritDoc} */
  @Override
  public double getSaturationPressure() {
    return saturationPressure;
  }
}
