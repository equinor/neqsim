package neqsim.process.equipment.powergeneration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
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
public class SteamTurbine extends TwoPortEquipment
    implements CapacityConstrainedEquipment, AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SteamTurbine.class);

  private double isentropicEfficiency = 0.85;
  private double outletPressure = 1.01325; // bara
  private double power = 0.0; // Watts
  private int numberOfStages = 1;

  /** Rated (maximum) power output in Watts. Used for capacity constraint calculations. */
  private double ratedPowerW = 0.0;

  /** Whether this equipment has been auto-sized. */
  private boolean autoSized = false;

  /** Storage for capacity constraints. */
  private final Map<String, CapacityConstraint> capacityConstraints =
      new LinkedHashMap<String, CapacityConstraint>();

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

  /**
   * Get the rated (maximum) power output.
   *
   * @return rated power in Watts
   */
  public double getRatedPower() {
    return ratedPowerW;
  }

  /**
   * Get the rated (maximum) power output in specified unit.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return rated power in specified unit
   */
  public double getRatedPower(String unit) {
    switch (unit) {
      case "kW":
        return ratedPowerW / 1000.0;
      case "MW":
        return ratedPowerW / 1.0e6;
      case "hp":
        return ratedPowerW / 745.7;
      default:
        return ratedPowerW;
    }
  }

  /**
   * Set the rated (maximum) power output.
   *
   * @param ratedPower rated power in Watts
   */
  public void setRatedPower(double ratedPower) {
    this.ratedPowerW = ratedPower;
    initializeCapacityConstraints();
  }

  /**
   * Set the rated (maximum) power output with unit.
   *
   * @param ratedPower rated power value
   * @param unit power unit ("W", "kW", "MW", "hp")
   */
  public void setRatedPower(double ratedPower, String unit) {
    switch (unit) {
      case "kW":
        this.ratedPowerW = ratedPower * 1000.0;
        break;
      case "MW":
        this.ratedPowerW = ratedPower * 1.0e6;
        break;
      case "hp":
        this.ratedPowerW = ratedPower * 745.7;
        break;
      default:
        this.ratedPowerW = ratedPower;
    }
    initializeCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return Math.abs(power);
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return ratedPowerW > 0 ? ratedPowerW : Math.abs(power) * 1.2;
  }

  /**
   * Initialize capacity constraints for the steam turbine.
   */
  private void initializeCapacityConstraints() {
    capacityConstraints.clear();
    if (ratedPowerW > 0) {
      addCapacityConstraint(
          new CapacityConstraint("power", "kW", CapacityConstraint.ConstraintType.HARD)
              .setDesignValue(ratedPowerW / 1000.0).setMaxValue(ratedPowerW / 1000.0 * 1.1)
              .setWarningThreshold(0.9)
              .setDescription("Steam turbine power output vs rated capacity")
              .setValueSupplier(() -> Math.abs(this.power) / 1000.0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (power > 0) {
      this.ratedPowerW = Math.abs(power) * safetyFactor;
      initializeCapacityConstraints();
      autoSized = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Steam Turbine Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");
    sb.append("\n--- Operating Conditions ---\n");
    sb.append("Power Output: ").append(String.format("%.2f kW", Math.abs(power) / 1000.0))
        .append("\n");
    if (ratedPowerW > 0) {
      sb.append("Rated Power: ").append(String.format("%.2f kW", ratedPowerW / 1000.0))
          .append("\n");
      sb.append("Utilization: ")
          .append(String.format("%.1f%%", Math.abs(power) / ratedPowerW * 100)).append("\n");
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }
}
