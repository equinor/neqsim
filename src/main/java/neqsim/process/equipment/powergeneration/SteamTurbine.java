package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Steam turbine for power generation from a high-pressure steam stream.
 *
 * <p>
 * Models isentropic expansion with a specified efficiency. The outlet stream conditions are
 * computed by performing an isentropic flash at the outlet pressure, then applying the isentropic
 * efficiency to determine the actual outlet enthalpy. Power output is the difference in specific
 * enthalpy multiplied by the mass flow rate.
 * </p>
 *
 * <p>
 * Typical usage in a combined-cycle or waste-heat recovery system:
 * </p>
 *
 * <pre>
 * Stream steam = new Stream("HP steam", steamFluid);
 * SteamTurbine turbine = new SteamTurbine("ST-100", steam);
 * turbine.setOutletPressure(0.05, "bara");
 * turbine.setIsentropicEfficiency(0.85);
 * turbine.run();
 * double power_kW = turbine.getPower("kW");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SteamTurbine extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SteamTurbine.class);

  private double isentropicEfficiency = 0.85;
  private double outletPressure = 1.01325; // bara
  private double power = 0.0; // Watts
  private int numberOfStages = 1;

  /**
   * Constructor for SteamTurbine.
   *
   * @param name equipment name
   */
  public SteamTurbine(String name) {
    super(name);
  }

  /**
   * Constructor for SteamTurbine with inlet stream.
   *
   * @param name equipment name
   * @param inletStream inlet steam stream
   */
  public SteamTurbine(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface inletFluid = inStream.getThermoSystem().clone();

    // Inlet conditions
    double inletEnthalpy = inletFluid.getEnthalpy();
    double inletEntropy = inletFluid.getEntropy();
    double massFlow = inletFluid.getFlowRate("kg/sec");

    // Isentropic expansion: flash at outlet pressure and inlet entropy
    SystemInterface isentropicFluid = inletFluid.clone();
    isentropicFluid.setPressure(outletPressure);

    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(isentropicFluid);
    try {
      ops.PSflash(inletEntropy);
    } catch (Exception ex) {
      logger.error("PS flash failed in SteamTurbine: " + ex.getMessage(), ex);
      // Fall back to TP flash
      ops.TPflash();
    }

    double idealOutletEnthalpy = isentropicFluid.getEnthalpy();
    double idealWork = inletEnthalpy - idealOutletEnthalpy;

    // Actual work accounting for isentropic efficiency
    double actualWork = idealWork * isentropicEfficiency;
    double actualOutletEnthalpy = inletEnthalpy - actualWork;

    // Flash outlet at actual enthalpy and outlet pressure
    SystemInterface outletFluid = inletFluid.clone();
    outletFluid.setPressure(outletPressure);
    neqsim.thermodynamicoperations.ThermodynamicOperations opsOut =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(outletFluid);
    try {
      opsOut.PHflash(actualOutletEnthalpy);
    } catch (Exception ex) {
      logger.error("PH flash failed in SteamTurbine: " + ex.getMessage(), ex);
      opsOut.TPflash();
    }

    this.power = actualWork; // Watts (positive = power produced)

    outStream.setThermoSystem(outletFluid);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Get power output of the steam turbine.
   *
   * @return power output in Watts (positive = power produced)
   */
  public double getPower() {
    return power;
  }

  /**
   * Get power output of the steam turbine in specified unit.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return power output in specified unit
   */
  public double getPower(String unit) {
    switch (unit) {
      case "kW":
        return power / 1000.0;
      case "MW":
        return power / 1.0e6;
      case "hp":
        return power / 745.7;
      default:
        return power;
    }
  }

  /**
   * Set outlet pressure.
   *
   * @param pressure outlet pressure in bara
   */
  @Override
  public void setOutletPressure(double pressure) {
    this.outletPressure = pressure;
  }

  /**
   * Set outlet pressure with unit.
   *
   * @param pressure outlet pressure
   * @param unit pressure unit ("bara", "barg", "psi")
   */
  @Override
  public void setOutletPressure(double pressure, String unit) {
    if ("barg".equals(unit)) {
      this.outletPressure = pressure + 1.01325;
    } else if ("psi".equals(unit)) {
      this.outletPressure = pressure / 14.696;
    } else {
      this.outletPressure = pressure;
    }
  }

  /**
   * Get isentropic efficiency.
   *
   * @return isentropic efficiency (0 to 1)
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * Set isentropic efficiency.
   *
   * @param efficiency isentropic efficiency (0 to 1)
   */
  public void setIsentropicEfficiency(double efficiency) {
    this.isentropicEfficiency = efficiency;
  }

  /**
   * Get the number of turbine stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set the number of turbine stages.
   *
   * @param numberOfStages number of stages
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }
}
