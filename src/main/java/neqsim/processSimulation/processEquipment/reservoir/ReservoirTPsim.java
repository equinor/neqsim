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
  StreamInterface gasOutStream = null;
  StreamInterface oilOutStream = null;

  private double pressure = 100.0;
  private double temperature = 100.0;
  private String tUnit = "K";
  private String pUnit = "bar";

  private String prodPhaseName = "gas";

  public SystemInterface getReserervourFluid() {
    return reservoirFluid;
  }

  public ReservoirTPsim(String name, SystemInterface reservoirFluid) {
    super(name);
    this.reservoirFluid = reservoirFluid;
  }



  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    reservoirFluid.setTemperature(temperature, tUnit);
    reservoirFluid.setPressure(pressure, pUnit);

    ThermodynamicOperations operations = new ThermodynamicOperations(reservoirFluid);
    operations.TPflash();

    if (reservoirFluid.hasPhaseType("gas")) {
      gasOutStream = new Stream(getName() + "_gas", reservoirFluid.phaseToSystem("gas"));
    }

    if (reservoirFluid.hasPhaseType("oil")) {
      oilOutStream = new Stream(getName() + "_oil", reservoirFluid.phaseToSystem("oil"));
    }

  }

  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  public StreamInterface getOilOutStream() {
    return oilOutStream;
  }


  public double getPressure() {
    return pressure;
  }



  public void setPressure(double reservoirPressure, String pUnit) {
    this.pressure = reservoirPressure;
    this.pUnit = pUnit;
  }



  public double getReservoirTemperature() {
    return temperature;
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



}


