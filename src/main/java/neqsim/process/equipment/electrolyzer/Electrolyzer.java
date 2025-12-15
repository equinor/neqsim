package neqsim.process.equipment.electrolyzer;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Electrolyzer unit converting water to hydrogen and oxygen using electrical energy.
 * </p>
 *
 * @author esol
 */
public class Electrolyzer extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Electrolyzer.class);

  private StreamInterface waterInlet;
  private Stream hydrogenOutStream;
  private Stream oxygenOutStream;

  private double cellVoltage = 1.23; // V
  private static final double FARADAY_CONSTANT = 96485.3329; // C/mol e-

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   */
  public Electrolyzer(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   * @param inletStream water inlet stream
   */
  public Electrolyzer(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   *
   * @param inletStream water inlet stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.waterInlet = inletStream;
    SystemInterface h2System =
        new Fluid().create2(new String[] {"hydrogen"}, new double[] {1.0}, "mole/sec");
    hydrogenOutStream = new Stream("hydrogenOutStream", h2System);
    SystemInterface o2System =
        new Fluid().create2(new String[] {"oxygen"}, new double[] {1.0}, "mole/sec");
    oxygenOutStream = new Stream("oxygenOutStream", o2System);

    double pressure = inletStream.getPressure("bara");
    double temperature = inletStream.getTemperature("K");
    hydrogenOutStream.setPressure(pressure, "bara");
    hydrogenOutStream.setTemperature(temperature, "K");
    oxygenOutStream.setPressure(pressure, "bara");
    oxygenOutStream.setTemperature(temperature, "K");
  }

  /**
   * <p>
   * Getter for the field <code>hydrogenOutStream</code>.
   * </p>
   *
   * @return hydrogen product stream
   */
  public StreamInterface getHydrogenOutStream() {
    return hydrogenOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>oxygenOutStream</code>.
   * </p>
   *
   * @return oxygen product stream
   */
  public StreamInterface getOxygenOutStream() {
    return oxygenOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = waterInlet.getThermoSystem().getFlowRate(unit);
    double outletFlow = hydrogenOutStream.getThermoSystem().getFlowRate(unit)
        + oxygenOutStream.getThermoSystem().getFlowRate(unit);
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double waterFlow = waterInlet.getFlowRate("mole/sec");
    double hydrogenFlow = waterFlow;
    double oxygenFlow = waterFlow / 2.0;

    hydrogenOutStream.setFlowRate(hydrogenFlow, "mole/sec");
    hydrogenOutStream.setPressure(waterInlet.getPressure("bara"), "bara");
    hydrogenOutStream.setTemperature(waterInlet.getTemperature("K"), "K");
    hydrogenOutStream.run(id);

    oxygenOutStream.setFlowRate(oxygenFlow, "mole/sec");
    oxygenOutStream.setPressure(waterInlet.getPressure("bara"), "bara");
    oxygenOutStream.setTemperature(waterInlet.getTemperature("K"), "K");
    oxygenOutStream.run(id);

    double current = hydrogenFlow * 2.0 * FARADAY_CONSTANT;
    double power = current * cellVoltage;
    energyStream.setDuty(power);
    setEnergyStream(true);
  }
}
