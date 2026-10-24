package neqsim.pvtsimulation.simulation;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.unit.TemperatureUnit;

/**
 * Isothermal gas-injection swelling experiment on the bubble-point branch.
 *
 * <p>
 * Cumulative injected mole percentages are relative to the original oil moles, not to the final mixture. Each run
 * starts from the constructor's initial oil snapshot and leaves the input templates unchanged. This calculation does
 * not follow a critical-point transition onto a dew-point branch or determine MMP.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SwellingTest extends BasePVTsimulation {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SwellingTest.class);

  double[] gasInjected = null;
  private double[] pressures = null;
  private double[] relativeOilVolume = null;
  SystemInterface injectionGas;
  /** Whether a temperature setter was called, including with an invalid NaN value. */
  private boolean temperatureConfigured = false;

  /**
   * Constructor for SwellingTest.
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SwellingTest(SystemInterface tempSystem) {
    super(tempSystem);
  }

  /**
   * Set the injection-gas template. Its amount and thermodynamic state are not modified by the experiment.
   *
   * @param injectionGas a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setInjectionGas(SystemInterface injectionGas) {
    this.injectionGas = injectionGas;
  }

  /**
   * Set cumulative injected-gas amounts as mole percent of the original oil.
   *
   * @param gasInjected nonempty, nonnegative, nondecreasing cumulative mole percentages
   * @throws IllegalArgumentException if the schedule is empty, nonfinite, negative or decreasing
   */
  public void setCummulativeMolePercentGasInjected(double[] gasInjected) {
    validateSchedule(gasInjected);
    this.gasInjected = gasInjected.clone();
    setPressures(new double[gasInjected.length]);
    setRelativeOilVolume(new double[gasInjected.length]);
  }

  /**
   * Set the experiment temperature in Kelvin, replacing any previously configured unit.
   *
   * @param temperature experiment temperature in Kelvin; validated by runCalc
   */
  @Override
  public void setTemperature(double temperature) {
    setTemperature(temperature, "K");
  }

  /**
   * Configure an explicit experiment temperature, distinct from omitting the temperature setting.
   *
   * @param temperature experiment temperature; validated by runCalc
   * @param temperatureUnit source temperature unit supported by TemperatureUnit
   */
  @Override
  public void setTemperature(double temperature, String temperatureUnit) {
    super.setTemperature(temperature, temperatureUnit);
    temperatureConfigured = true;
  }

  /**
   * Run all additions at the requested temperature, or the initial oil temperature when none is set.
   *
   * @throws IllegalArgumentException if the schedule, temperature or input amounts are invalid
   * @throws IllegalStateException if a bubble-point calculation fails
   */
  public void runCalc() {
    validateSchedule(gasInjected);
    pressures = new double[gasInjected.length];
    relativeOilVolume = new double[gasInjected.length];
    Arrays.fill(pressures, Double.NaN);
    Arrays.fill(relativeOilVolume, Double.NaN);
    setThermoSystem(getBaseThermoSystem().clone());
    thermoOps = new ThermodynamicOperations(getThermoSystem());
    if (temperatureConfigured || !Double.isNaN(temperature)) {
      double kelvin = new TemperatureUnit(temperature, temperatureUnit).getSIvalue();
      if (!Double.isFinite(kelvin) || kelvin <= 0.0) {
        throw new IllegalArgumentException("Swelling test requires a positive finite absolute temperature");
      }
      getThermoSystem().setTemperature(kelvin);
    }
    double experimentTemperature = getThermoSystem().getTemperature();
    double oilMoles = getThermoSystem().getTotalNumberOfMoles();
    if (!Double.isFinite(experimentTemperature) || experimentTemperature <= 0.0 || !Double.isFinite(oilMoles)
        || oilMoles <= 0.0) {
      throw new IllegalArgumentException("Swelling test requires positive finite temperature and oil moles");
    }
    boolean addsGas = gasInjected[gasInjected.length - 1] > 0.0;
    if (addsGas && (injectionGas == null || !Double.isFinite(injectionGas.getTotalNumberOfMoles())
        || injectionGas.getTotalNumberOfMoles() <= 0.0)) {
      throw new IllegalArgumentException("Positive additions require an injection gas with positive finite moles");
    }

    double originalOilVolume = bubblePointVolume("initial oil");
    double[] calculatedPressures = new double[gasInjected.length];
    double[] calculatedVolumes = new double[gasInjected.length];
    double oldInjected = 0.0;
    double currentVolume = originalOilVolume;
    for (int i = 0; i < gasInjected.length; i++) {
      double increment = gasInjected[i] - oldInjected;
      if (increment > 0.0) {
        SystemInterface addition = injectionGas.clone();
        addition.setTotalNumberOfMoles(oilMoles * increment / 100.0);
        addition.init(0);
        getThermoSystem().addFluid(addition);
        getThermoSystem().setTemperature(experimentTemperature);
        currentVolume = bubblePointVolume("addition " + i);
      }
      oldInjected = gasInjected[i];
      calculatedPressures[i] = getThermoSystem().getPressure();
      calculatedVolumes[i] = currentVolume / originalOilVolume;
    }
    pressures = calculatedPressures;
    relativeOilVolume = calculatedVolumes;
  }

  /**
   * Validate a cumulative addition schedule before changing the experiment state.
   *
   * @param schedule cumulative injected mole percentages
   * @throws IllegalArgumentException if any entry is invalid
   */
  private void validateSchedule(double[] schedule) {
    if (schedule == null || schedule.length == 0) {
      throw new IllegalArgumentException("Swelling test requires a nonempty cumulative addition schedule");
    }
    double previous = 0.0;
    for (double amount : schedule) {
      if (!Double.isFinite(amount) || amount < previous) {
        throw new IllegalArgumentException(
            "Cumulative injected mole percentages must be finite, nonnegative and nondecreasing");
      }
      previous = amount;
    }
  }

  /**
   * Calculate a nontrivial bubble point and its oil volume, propagating a failed solve.
   *
   * @param stage description of the reference or addition stage
   * @return positive density-corrected total volume in cubic metres
   * @throws IllegalStateException if a bubble point cannot be calculated
   */
  private double bubblePointVolume(String stage) {
    try {
      thermoOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      throw new IllegalStateException("Swelling test could not calculate bubble point for " + stage, ex);
    }
    getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double volume = getThermoSystem().getCorrectedVolume();
    if (!Double.isFinite(volume) || volume <= 0.0) {
      throw new IllegalStateException("Swelling test returned invalid bubble-point volume for " + stage);
    }
    return volume;
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface oilSystem = new SystemSrkEos(298.0, 50);
    oilSystem.addComponent("methane", 5.01);
    oilSystem.addComponent("propane", 0.01);
    oilSystem.addTBPfraction("C10", 100.0, 145.0 / 1000.0, 0.82);
    oilSystem.addTBPfraction("C12", 120.0, 175.0 / 1000.0, 0.85);
    oilSystem.createDatabase(true);
    oilSystem.setMixingRule(2);

    SystemInterface gasSystem = new SystemSrkEos(298.0, 50);
    gasSystem.addComponent("methane", 110.0);
    gasSystem.addComponent("ethane", 10.0);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);

    SwellingTest test = new SwellingTest(oilSystem);
    test.setInjectionGas(gasSystem);
    test.setTemperature(298.15);
    test.setCummulativeMolePercentGasInjected(
        new double[] {0.0, 0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.4, 0.5, 1.0, 10.3, 22.0});
    test.runCalc();

    test.getThermoSystem().display();
  }

  /**
   * Getter for the field <code>relativeOilVolume</code>.
   *
   * @return the relativeOilVolume
   */
  public double[] getRelativeOilVolume() {
    return relativeOilVolume;
  }

  /**
   * Setter for the field <code>relativeOilVolume</code>.
   *
   * @param relativeOilVolume the relativeOilVolume to set
   */
  public void setRelativeOilVolume(double[] relativeOilVolume) {
    this.relativeOilVolume = relativeOilVolume;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getPressures() {
    return pressures;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressures(double[] pressures) {
    this.pressures = pressures;
  }
}
