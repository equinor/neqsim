package neqsim.process.equipment.reservoir;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * ReservoirTPsim class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: ReservoirTPsim.java 1234 2024-05-31 10:00:00Z esolbraa $
 */
public class ReservoirTPsim extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private SystemInterface reservoirFluid = null;
  private StreamInterface outStream = null;

  private double pressure = 100.0;
  private double temperature = 100.0;
  private double flowRate = 100.0;
  private String flowUnit = "kg/hr";
  private String tUnit = "K";
  private String pUnit = "bar";

  private String prodPhaseName = "gas";

  /**
   * <p>
   * getReserervourFluid.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getReserervourFluid() {
    return reservoirFluid;
  }

  /**
   * <p>
   * Constructor for ReservoirTPsim.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param reservoirFluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ReservoirTPsim(String name, SystemInterface reservoirFluid) {
    super(name);
    this.reservoirFluid = reservoirFluid;
    outStream = new Stream(getName() + "_out", reservoirFluid.clone());
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface fluid1 = reservoirFluid.clone();
    fluid1.setTemperature(temperature, tUnit);
    fluid1.setPressure(pressure, pUnit);
    fluid1.setTotalFlowRate(flowRate, flowUnit);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid1);
    operations.TPflash();

    if (prodPhaseName.equals("gas") && fluid1.hasPhaseType("gas")) {
      outStream.setFluid(fluid1.phaseToSystem("gas"));
    } else if (prodPhaseName.equals("oil") && fluid1.hasPhaseType("oil")) {
      outStream.setFluid(fluid1.phaseToSystem("oil"));
    } else {
      outStream.setFluid(fluid1.phaseToSystem(1));
    }
  }

  /**
   * <p>
   * Getter for the field <code>outStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream() {
    return outStream;
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param reservoirPressure a double
   * @param pUnit a {@link java.lang.String} object
   */
  public void setPressure(double reservoirPressure, String pUnit) {
    this.pressure = reservoirPressure;
    this.pUnit = pUnit;
  }

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param reservoirTemperature a double
   * @param tUnit a {@link java.lang.String} object
   */
  public void setTemperature(double reservoirTemperature, String tUnit) {
    this.temperature = reservoirTemperature;
    this.tUnit = tUnit;
  }

  /**
   * <p>
   * Getter for the field <code>prodPhaseName</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getProdPhaseName() {
    return prodPhaseName;
  }

  /**
   * <p>
   * Setter for the field <code>prodPhaseName</code>.
   * </p>
   *
   * @param prodPhaseName a {@link java.lang.String} object
   */
  public void setProdPhaseName(String prodPhaseName) {
    this.prodPhaseName = prodPhaseName;
  }

  /**
   * <p>
   * Setter for the field <code>flowRate</code>.
   * </p>
   *
   * @param flowRate a double
   * @param flowUnit a {@link java.lang.String} object
   */
  public void setFlowRate(double flowRate, String flowUnit) {
    this.flowRate = flowRate;
    this.flowUnit = flowUnit;
  }
}
