package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SimpleTray class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SimpleTray extends neqsim.process.equipment.mixer.Mixer implements TrayInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SimpleTray.class);

  double heatInput = 0.0;
  private double temperature = Double.NaN;

  private double trayPressure = -1.0;

  /**
   * <p>
   * Constructor for SimpleTray.
   * </p>
   *
   * @param name name of tray
   */
  public SimpleTray(String name) {
    super(name);
  }

  /**
   * <p>
   * init.
   * </p>
   */
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

  /**
   * <p>
   * calcMixStreamEnthalpy0.
   * </p>
   *
   * @return a double
   */
  public double calcMixStreamEnthalpy0() {
    double enthalpy = 0;

    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      // System.out.println("total enthalpy k : " + ( ((Stream)
      // streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public double calcMixStreamEnthalpy() {
    double enthalpy = heatInput;
    if (isSetEnergyStream()) {
      enthalpy -= energyStream.getDuty();
    }

    for (int k = 0; k < streams.size(); k++) {
      if (streams.get(k).getFlowRate("kg/hr") > getMinimumFlow()) {
        streams.get(k).getThermoSystem().init(3);
        enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      }
      // System.out.println("total enthalpy k : " + ( ((Stream)
      // streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /**
   * <p>
   * run2.
   * </p>
   */
  public void run2() {
    super.run();
    temperature = mixedStream.getTemperature();
  }

  /**
   * <p>
   * TPflash.
   * </p>
   */
  public void TPflash() {}

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double enthalpy = 0.0;
    // double flowRate = ((Stream)
    // streams.get(0)).getThermoSystem().getFlowRate("kg/hr");
    // ((Stream) streams.get(0)).getThermoSystem().display();
    boolean changeTo2Phase = false;
    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    if (thermoSystem2.doMultiPhaseCheck()) {
      changeTo2Phase = true;
      thermoSystem2.setMultiPhaseCheck(false);
    }
    // System.out.println("total number of moles " +
    // thermoSystem2.getTotalNumberOfMoles());
    if (trayPressure > 0)

    {
      thermoSystem2.setPressure(trayPressure);
    }
    mixedStream.setThermoSystem(thermoSystem2);
    // thermoSystem2.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 0) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);

      mixStream();
      if (trayPressure > 0) {
        mixedStream.setPressure(trayPressure, "bara");
      }
      enthalpy = calcMixStreamEnthalpy();
      // System.out.println("temp guess " + guessTemperature());
      if (!isSetOutTemperature()) {
        // mixedStream.getThermoSystem().setTemperature(guessTemperature());
      } else {
        mixedStream.setTemperature(getOutTemperature(), "K");
      }
      // System.out.println("filan temp " + mixedStream.getTemperature());
    }
    if (isSetOutTemperature()) {
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.getThermoSystem().setTemperature(getOutTemperature());
      }
      testOps.TPflash();
      mixedStream.getThermoSystem().init(2);
    } else {
      try {
        testOps.PHflash(enthalpy, 0);
      } catch (Exception ex) {
        try {
          if (!Double.isNaN(getOutTemperature())) {
            mixedStream.getThermoSystem().setTemperature(getOutTemperature());
          }
          testOps.TPflash();
        } catch (Exception ex2) {
          logger.warn("TPflash failed in SimpleTray: " + getName(), ex2);
        }
      }
    }

    if (Double.isNaN(mixedStream.getTemperature())) {
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.setTemperature(getOutTemperature());
      }
    }

    setTemperature(mixedStream.getTemperature());
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    if (mixedStream.getFluid().getNumberOfPhases() >= 3) {
      System.out
          .println("error...." + mixedStream.getFluid().getNumberOfPhases() + " phases on tray");
      logger.warn("error...." + mixedStream.getFluid().getNumberOfPhases() + " phases on tray");
    }

    if (changeTo2Phase) {
      thermoSystem2.setMultiPhaseCheck(true);
    }
  }

  /**
   * <p>
   * getGasOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasOutStream() {
    SystemInterface thermoSys = mixedStream.getThermoSystem();
    // Find gas phase by type, fallback to phase 0
    for (int i = 0; i < thermoSys.getNumberOfPhases(); i++) {
      if (thermoSys.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        try {
          return new Stream("", thermoSys.phaseToSystem(i));
        } catch (Exception e) {
          logger.warn("Failed to extract gas phase " + i + ", trying next", e);
        }
      }
    }
    // If no gas phase found, return phase 0 (original behavior)
    try {
      return new Stream("", thermoSys.phaseToSystem(0));
    } catch (Exception e) {
      logger.error("Failed to extract any gas phase", e);
      // Return a stream with the full system as fallback
      return new Stream("", thermoSys.clone());
    }
  }

  /**
   * <p>
   * getLiquidOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquidOutStream() {
    SystemInterface thermoSys = mixedStream.getThermoSystem();
    // Find liquid phase by type
    for (int i = 0; i < thermoSys.getNumberOfPhases(); i++) {
      if (thermoSys.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.LIQUID
          || thermoSys.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.OIL
          || thermoSys.getPhase(i).getType() == neqsim.thermo.phase.PhaseType.AQUEOUS) {
        try {
          return new Stream("", thermoSys.phaseToSystem(i));
        } catch (Exception e) {
          logger.warn("Failed to extract liquid phase " + i + ", trying next", e);
        }
      }
    }
    // If no liquid phase found, return phase 1 if it exists, otherwise phase 0
    if (thermoSys.getNumberOfPhases() > 1) {
      try {
        return new Stream("", thermoSys.phaseToSystem(1));
      } catch (Exception e) {
        logger.warn("Failed to extract phase 1", e);
      }
    }
    // Only one phase exists or extraction failed - return phase 0 as fallback
    try {
      return new Stream("", thermoSys.phaseToSystem(0));
    } catch (Exception e) {
      logger.error("Failed to extract any liquid phase", e);
      // Return a stream with the full system as fallback
      return new Stream("", thermoSys.clone());
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    return temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    trayPressure = pres;
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double guessTemperature() {
    if (Double.isNaN(temperature)) {
      double gtemp = 0;
      for (int k = 0; k < streams.size(); k++) {
        gtemp += streams.get(k).getThermoSystem().getTemperature()
            * streams.get(k).getThermoSystem().getNumberOfMoles()
            / mixedStream.getThermoSystem().getNumberOfMoles();
      }
      // System.out.println("guess temperature " + gtemp);
      return gtemp;
    } else {
      // System.out.println("temperature " + temperature);
      return temperature;
    }
  }

  /**
   * <p>
   * getVaporFlowRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getVaporFlowRate(String unit) {
    if (getFluid().hasPhaseType("gas")) {
      return getFluid().getPhase("gas").getFlowRate(unit);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * getLiquidFlowRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getLiquidFlowRate(String unit) {
    if (getFluid().hasPhaseType("aqueous") || getFluid().hasPhaseType("oil")) {
      return getFluid().getPhase(1).getFlowRate(unit);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * getFeedRate.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getFeedRate(String unit) {
    double feed = 0.0;
    for (int j = 0; j < getNumberOfInputStreams(); j++) {
      feed += getStream(j).getFluid().getFlowRate("kg/hr");
    }
    return feed;
  }

  /**
   * <p>
   * massBalance.
   * </p>
   *
   * Calculates the mass balance by comparing the total mass input and output.
   *
   * @return the difference between mass input and mass output
   */
  public double massBalance() {
    double massInput = 0;
    double massOutput = 0;
    int numberOfInputStreams = getNumberOfInputStreams();
    for (int j = 0; j < numberOfInputStreams; j++) {
      massInput += getStream(j).getFluid().getFlowRate("kg/hr");
    }
    massOutput = getThermoSystem().getFlowRate("kg/hr");
    return massInput - massOutput;
  }
}
