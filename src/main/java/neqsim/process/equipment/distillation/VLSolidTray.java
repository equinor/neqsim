package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * VLSolidTray class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class VLSolidTray extends SimpleTray {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double heatInput = 0.0;
  private double temperature = 273.15;

  /**
   * <p>
   * Constructor for VLSolidTray.
   * </p>
   *
   * @param name name of tray
   */
  public VLSolidTray(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    int pp = 0;
    if (streams.size() == 3) {
      pp = 1;
    }
    for (int k = pp; k < streams.size(); k++) {
      (streams.get(k).getThermoSystem()).setTemperature(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setHeatInput(double heatinp) {
    this.heatInput = heatinp;
  }

  /** {@inheritDoc} */
  @Override
  public double calcMixStreamEnthalpy() {
    double enthalpy = heatInput;

    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
    }
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double enthalpy = 0.0;
    if (streams.isEmpty()) {
      throw new IllegalStateException("VLSolidTray has no inlet streams");
    }

    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    mixedStream.setThermoSystem(thermoSystem2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 0) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);

      mixStream();

      enthalpy = calcMixStreamEnthalpy();
      mixedStream.getThermoSystem().setSolidPhaseCheck("CO2");
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      testOps.PHsolidFlash(enthalpy);
    } else {
      testOps.TPflash();
    }
    mixedStream.getThermoSystem().setSolidPhaseCheck(false);
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return createPhaseStream("gas");
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    return createLiquidStream();
  }

  /**
   * Create a stream from the requested phase type.
   *
   * @param phaseTypeName phase type name to locate
   * @return stream for the phase, or a zero-flow stream when absent
   */
  private StreamInterface createPhaseStream(String phaseTypeName) {
    SystemInterface system = mixedStream.getThermoSystem();
    int phaseIndex = findPhaseIndex(system, phaseTypeName);
    if (phaseIndex < 0) {
      return createZeroStream(system);
    }
    return new Stream("", system.phaseToSystem(phaseIndex));
  }

  /**
   * Create a liquid-like phase stream.
   *
   * @return stream for the liquid-like phase, or a zero-flow stream when absent
   */
  private StreamInterface createLiquidStream() {
    SystemInterface system = mixedStream.getThermoSystem();
    int phaseIndex = findLiquidPhaseIndex(system);
    if (phaseIndex < 0) {
      return createZeroStream(system);
    }
    return new Stream("", system.phaseToSystem(phaseIndex));
  }

  /**
   * Find a phase by type name.
   *
   * @param system thermodynamic system to inspect
   * @param phaseTypeName phase type name to locate
   * @return phase index, or {@code -1} when absent
   */
  private int findPhaseIndex(SystemInterface system, String phaseTypeName) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseTypeName.equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Find a liquid-like phase.
   *
   * @param system thermodynamic system to inspect
   * @return liquid-like phase index, or {@code -1} when absent
   */
  private int findLiquidPhaseIndex(SystemInterface system) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      String phaseTypeName = system.getPhase(phaseIndex).getPhaseTypeName();
      if ("liquid".equals(phaseTypeName) || "oil".equals(phaseTypeName)
          || "aqueous".equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (!"gas".equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Create a zero-flow stream from a tray system template.
   *
   * @param system tray system template
   * @return zero-flow stream
   */
  private StreamInterface createZeroStream(SystemInterface system) {
    SystemInterface zeroSystem = system.clone();
    zeroSystem.setTotalNumberOfMoles(0.0);
    zeroSystem.init(0);
    return new Stream("", zeroSystem);
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }
}
