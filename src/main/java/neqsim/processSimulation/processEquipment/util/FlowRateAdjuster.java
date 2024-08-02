package neqsim.processSimulation.processEquipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Adjuster class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class FlowRateAdjuster extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(FlowRateAdjuster.class);

  String name = "Flow Rate Adjuster";

  public double desiredGasFlow;
  public double desiredOilFlow;
  public double desiredWaterFlow;
  private String unit;

  ProcessEquipmentInterface adjustedEquipment = null;
  ProcessEquipmentInterface targetEquipment = null;

  String adjustedVariable = "";
  String adjustedVariableUnit = "";
  double maxAdjustedValue = 1e10;
  double minAdjustedValue = -1e10;
  String targetVariable = "";
  String targetPhase = "";
  String targetComponent = "";

  double targetValue = 0.0;
  String targetUnit = "";
  private double tolerance = 1e-6;
  double inputValue = 0.0;
  double oldInputValue = 0.0;
  private double error = 1e6;
  private double oldError = 1.0e6;

  int iterations = 0;
  private boolean activateWhenLess = false;
  Stream waterStream;
  double waterDensity;

  /**
   * <p>
   * Constructor for FlowRateAdjuster.
   * </p>
   */
  @Deprecated
  public FlowRateAdjuster() {
    this("Flow Rate Adjuster");
  }

  /**
   * <p>
   * Constructor for FlowRateAdjuster.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public FlowRateAdjuster(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for FlowRateAdjuster.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public FlowRateAdjuster(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * <p>
   * setAdjustedVariable.
   * </p>
   *
   * @param desiredGasFlow a {@link java.lang.Double} object
   * @param desiredOilFlow a {@link java.lang.Double} object
   * @param desiredWaterFlow a {@link java.lang.Double} object
   * @param unit a {@link java.lang.String} object
   */
  public void setAdjustedFlowRates(Double desiredGasFlow, Double desiredOilFlow,
      Double desiredWaterFlow, String unit) {
    this.desiredGasFlow = desiredGasFlow;
    this.desiredOilFlow = desiredOilFlow;
    this.desiredWaterFlow = desiredWaterFlow;
    this.unit = unit;
  }

  /**
   * <p>
   * setAdjustedVariable.
   * </p>
   *
   * @param desiredGasFlow a {@link java.lang.Double} object
   * @param desiredOilFlow a {@link java.lang.Double} object
   * @param unit a {@link java.lang.String} object
   */
  public void setAdjustedFlowRates(Double desiredGasFlow, Double desiredOilFlow, String unit) {
    this.setAdjustedFlowRates(desiredGasFlow, desiredOilFlow, 0.0, unit);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface adjustedFluid = inStream.getFluid();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(adjustedFluid);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    SystemInterface gasFluid = adjustedFluid.phaseToSystem(0).clone();
    SystemInterface oilFluid = adjustedFluid.phaseToSystem(1).clone();

    double temperature = inStream.getTemperature("C");
    double pressure = inStream.getPressure("bara");

    if (desiredWaterFlow > 0.0) {
      SystemInterface waterFluid = adjustedFluid.phaseToSystem(2).clone();
      waterFluid.initPhysicalProperties();
      waterDensity = waterFluid.getDensity("kg/m3");

      waterStream = new Stream("Water Stream", waterFluid);
      waterStream.setTemperature(temperature, "C");
      waterStream.setPressure(pressure, "bara");

    }
    gasFluid.initPhysicalProperties();
    oilFluid.initPhysicalProperties();

    double oilDensity = oilFluid.getDensity("kg/m3");

    Stream gasStream = new Stream("Gas Stream", gasFluid);
    gasStream.setTemperature(temperature, "C");
    gasStream.setPressure(pressure, "bara");

    Stream oilStream = new Stream("Oil Stream", oilFluid);
    oilStream.setTemperature(temperature, "C");
    oilStream.setPressure(pressure, "bara");

    if (unit.equals("Sm3/hr")) {
      gasStream.setFlowRate(desiredGasFlow, unit);
      oilStream.setFlowRate(desiredOilFlow * oilDensity, "kg/hr");
      if (desiredWaterFlow > 0.0) {
        waterStream.setFlowRate(desiredWaterFlow * waterDensity, "kg/hr");
      }
    } else {
      gasStream.setFlowRate(desiredGasFlow, unit);
      oilStream.setFlowRate(desiredOilFlow, unit);
      if (desiredWaterFlow > 0.0) {
        waterStream.setFlowRate(desiredWaterFlow, unit);
      }
    }
    gasStream.run();
    oilStream.run();
    if (desiredWaterFlow > 0.0) {
      waterStream.run();
    }

    Mixer wellStramMixer = new StaticMixer("Stream mixer");
    wellStramMixer.addStream(gasStream);
    wellStramMixer.addStream(oilStream);
    if (desiredWaterFlow > 0.0) {
      wellStramMixer.addStream(waterStream);
    }
    wellStramMixer.run();

    outStream.setThermoSystem(wellStramMixer.getOutletStream().getFluid());
    outStream.run();
    outStream.setCalculationIdentifier(id);
  }
}
