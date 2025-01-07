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
      System.out.println("total enthalpy k : " + streams.get(k).getThermoSystem().getEnthalpy());
    }
    System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double enthalpy = 0.0;

    // ((Stream) streams.get(0)).getThermoSystem().display();

    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    // System.out.println("total number of moles " +
    // thermoSystem2.getTotalNumberOfMoles());
    mixedStream.setThermoSystem(thermoSystem2);
    // thermoSystem2.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() > 0) {
      // mixedStream.getThermoSystem().setSolidPhaseCheck("CO2");
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);

      mixStream();

      enthalpy = calcMixStreamEnthalpy();
      // mixedStream.getThermoSystem().display();
      // System.out.println("temp guess " + guessTemperature());
      mixedStream.getThermoSystem().setSolidPhaseCheck("CO2");
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      testOps.PHsolidFlash(enthalpy);
      mixedStream.getThermoSystem().display();
      // System.out.println("filan temp " + mixedStream.getTemperature());
    } else {
      testOps.TPflash();
    }
    mixedStream.getThermoSystem().setSolidPhaseCheck(false);
    // System.out.println("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy());
    // System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " +
    // mixedStream.getThermoSystem().getTemperature());

    // System.out.println("beta " + mixedStream.getThermoSystem().getBeta());
    // outStream.setThermoSystem(mixedStream.getThermoSystem());
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return new Stream("", mixedStream.getThermoSystem().phaseToSystem(0));
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    return new Stream("", mixedStream.getThermoSystem().phaseToSystem(1));
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
