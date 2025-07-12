package neqsim.process.equipment.flare;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.unit.PowerUnit;

/**
 * Flare unit operation for combustion of a process stream.
 */
public class Flare extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;

  private double heatDuty = 0.0; // J/s
  private double co2Emission = 0.0; // kg/s

  /**
   * Default constructor.
   * 
   * @param name name of the flare
   */
  public Flare(String name) {
    super(name);
  }

  /**
   * Constructor setting inlet stream.
   * 
   * @param name name of flare
   * @param inletStream inlet stream
   */
  public Flare(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    this.inStream = stream;
    this.outStream = new Stream(stream.getName() + "_flareout", stream.getFluid().clone());
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface thermoSystem = inStream.getThermoSystem().clone();
    double flowSm3sec = inStream.getFlowRate("Sm3/sec");
    heatDuty = inStream.LCV() * flowSm3sec;

    double molesTotalPerSec = inStream.getFlowRate("mole/sec");
    double molesCarbonPerSec = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      double moleFrac = thermoSystem.getComponent(i).getz();
      double molesCompPerSec = moleFrac * molesTotalPerSec;
      double nC = thermoSystem.getComponent(i).getElements().getNumberOfElements("C");
      molesCarbonPerSec += molesCompPerSec * nC;
    }
    co2Emission = molesCarbonPerSec * 44.01e-3; // kg/s

    outStream.setThermoSystem(thermoSystem);
    setCalculationIdentifier(id);
  }

  /**
   * Get heat released from flare.
   * 
   * @return heat duty in W
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get heat released in desired unit.
   * 
   * @param unit engineering unit, e.g. "MW"
   * @return heat duty in specified unit
   */
  public double getHeatDuty(String unit) {
    PowerUnit conv = new PowerUnit(heatDuty, "W");
    return conv.getValue(unit);
  }

  /**
   * Get CO2 emissions in kg/s.
   * 
   * @return CO2 emission rate
   */
  public double getCO2Emission() {
    return co2Emission;
  }

  /**
   * Get CO2 emissions in specified unit.
   * Supported units: kg/sec, kg/hr, kg/day
   * 
   * @param unit desired unit
   * @return emission in specified unit
   */
  public double getCO2Emission(String unit) {
    switch (unit) {
      case "kg/hr":
        return co2Emission * 3600.0;
      case "kg/day":
        return co2Emission * 3600.0 * 24.0;
      default:
        return co2Emission;
    }
  }
}
