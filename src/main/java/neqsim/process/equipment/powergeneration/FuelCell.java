package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * FuelCell class representing a simple hydrogen fuel cell.
 * </p>
 *
 * @author OpenAI
 */
public class FuelCell extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FuelCell.class);

  private StreamInterface oxidantStream;
  private double efficiency = 0.6;
  private double power = 0.0;
  private double heatLoss = 0.0;

  /**
   * <p>
   * Constructor for FuelCell.
   * </p>
   */
  public FuelCell() {
    this("FuelCell");
  }

  /**
   * <p>
   * Constructor for FuelCell.
   * </p>
   *
   * @param name name of unit operation
   */
  public FuelCell(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for FuelCell.
   * </p>
   *
   * @param name name of unit operation
   * @param fuelStream inlet fuel stream
   * @param oxidantStream inlet oxidant stream
   */
  public FuelCell(String name, StreamInterface fuelStream, StreamInterface oxidantStream) {
    super(name, fuelStream);
    this.oxidantStream = oxidantStream;
  }

  /**
   * <p>Setter for the field <code>oxidantStream</code>.</p>
   *
   * @param stream oxidant stream
   */
  public void setOxidantStream(StreamInterface stream) {
    this.oxidantStream = stream;
  }

  /**
   * <p>Getter for the field <code>oxidantStream</code>.</p>
   *
   * @return oxidant stream
   */
  public StreamInterface getOxidantStream() {
    return oxidantStream;
  }

  /**
   * <p>Setter for the field <code>efficiency</code>.</p>
   *
   * @param efficiency electrical efficiency of the cell
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * <p>Getter for the field <code>efficiency</code>.</p>
   *
   * @return efficiency of the cell
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * <p>Getter for the field <code>power</code>.</p>
   *
   * @return electrical power produced [W]
   */
  public double getPower() {
    return power;
  }

  /**
   * <p>Getter for the field <code>heatLoss</code>.</p>
   *
   * @return heat lost from the cell [W]
   */
  public double getHeatLoss() {
    return heatLoss;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();
    if (oxidantStream != null) {
      system.addFluid(oxidantStream.getThermoSystem());
    }

    double h2 = 0.0;
    if (system.hasComponent("hydrogen")) {
      h2 = system.getComponent("hydrogen").getNumberOfmoles();
    }
    double o2 = 0.0;
    if (system.hasComponent("oxygen")) {
      o2 = system.getComponent("oxygen").getNumberOfmoles();
    }

    double consumedH2 = Math.min(h2, 2.0 * o2);
    double consumedO2 = consumedH2 / 2.0;

    if (consumedH2 > 0) {
      system.addComponent("water", consumedH2);
      if (system.hasComponent("hydrogen")) {
        system.addComponent("hydrogen", -consumedH2);
      }
      if (system.hasComponent("oxygen")) {
        system.addComponent("oxygen", -consumedO2);
      }
    }

    system.init(3);
    outStream.setThermoSystem(system);

    double fuelEnergy = inStream.LCV() * inStream.getFlowRate("mole/sec");
    power = efficiency * fuelEnergy;
    heatLoss = fuelEnergy - power;

    getEnergyStream().setDuty(-power);
    setCalculationIdentifier(id);
  }
}

