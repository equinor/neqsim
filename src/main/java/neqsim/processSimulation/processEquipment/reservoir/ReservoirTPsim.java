package neqsim.processSimulation.processEquipment.reservoir;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ReservoirTPsim class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: ReservoirTPsim.java 1234 2024-05-31 10:00:00Z esolbraa $
 */
public class ReservoirTPsim extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;

  SystemInterface reservoirFluid = null;
  StreamInterface outStream = null;

  private double pressure = 100.0;
  private double temperature = 100.0;
  private double flowRate = 100.0;
  private String flowUnit = "kg/hr";
  private String tUnit = "K";
  private String pUnit = "bar";

  private String prodPhaseName = "gas";

  public SystemInterface getReserervourFluid() {
    return reservoirFluid;
  }

  public ReservoirTPsim(String name, SystemInterface reservoirFluid) {
    super(name);
    this.reservoirFluid = reservoirFluid;
    outStream = new Stream(getName() + "_out", reservoirFluid.clone());
  }



  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    reservoirFluid.setTemperature(temperature, tUnit);
    reservoirFluid.setPressure(pressure, pUnit);
    reservoirFluid.setTotalFlowRate(flowRate, flowUnit);

    ThermodynamicOperations operations = new ThermodynamicOperations(reservoirFluid);
    operations.TPflash();

    if (prodPhaseName.equals("gas") && reservoirFluid.hasPhaseType("gas")) {
      outStream = new Stream(getName() + "_gas", reservoirFluid.phaseToSystem("gas"));
    } else if (prodPhaseName.equals("oil") && reservoirFluid.hasPhaseType("oil")) {
      outStream = new Stream(getName() + "_oil", reservoirFluid.phaseToSystem("oil"));
    } else {
      outStream = new Stream(getName() + "_" + prodPhaseName, reservoirFluid.phaseToSystem(1));
    }

  }

  public StreamInterface getOutStream() {
    return outStream;
  }

  public void setPressure(double reservoirPressure, String pUnit) {
    this.pressure = reservoirPressure;
    this.pUnit = pUnit;
  }

  public void setTemperature(double reservoirTemperature, String tUnit) {
    this.temperature = reservoirTemperature;
    this.tUnit = tUnit;
  }

  public String getProdPhaseName() {
    return prodPhaseName;
  }

  public void setProdPhaseName(String prodPhaseName) {
    this.prodPhaseName = prodPhaseName;
  }

  public void setFlowRate(double flowRate, String flowUnit) {
    this.flowRate = flowRate;
    this.flowUnit = flowUnit;
  }



}


