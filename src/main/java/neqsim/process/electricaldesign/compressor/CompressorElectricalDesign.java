package neqsim.process.electricaldesign.compressor;

import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.DriverType;

/**
 * Electrical design for compressors.
 *
 * <p>
 * Extends {@link ElectricalDesign} with compressor-specific electrical requirements including
 * auxiliary loads (lube oil system, seal gas system, cooling fans, instrumentation) and integration
 * with the compressor's driver type and VFD settings.
 * </p>
 *
 * <p>
 * Typical auxiliary loads for a centrifugal compressor package:
 * </p>
 * <ul>
 * <li>Lube oil pump and heater: 2-5% of main motor power</li>
 * <li>Seal gas system: 1-3% of main motor power</li>
 * <li>Cooling fans (air cooled): 3-8% of main motor power</li>
 * <li>Instrumentation and controls: 2-5 kW typically</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorElectricalDesign extends ElectricalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // === Auxiliary loads ===
  private double lubeOilPumpKW;
  private double lubeOilHeaterKW;
  private double sealGasSystemKW;
  private double coolingFanKW;
  private double instrumentationKW = 3.0;
  private double totalAuxiliaryKW;

  // === Compressor-specific settings ===
  private boolean hasLubeOilSystem = true;
  private boolean hasSealGasSystem = false;
  private boolean hasAirCooling = false;

  /**
   * Constructor for CompressorElectricalDesign.
   *
   * @param processEquipment the compressor equipment
   */
  public CompressorElectricalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    readCompressorSettings();
  }

  /**
   * Read compressor-specific settings like driver type and VFD configuration.
   */
  private void readCompressorSettings() {
    if (!(getProcessEquipment() instanceof Compressor)) {
      return;
    }
    Compressor comp = (Compressor) getProcessEquipment();

    // Check if compressor uses electric motor with VFD
    if (comp.getDriver() != null) {
      if (comp.getDriver().getDriverType() == DriverType.VFD_MOTOR) {
        setUseVFD(true);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  protected double getProcessShaftPowerKW() {
    if (getProcessEquipment() instanceof Compressor) {
      Compressor comp = (Compressor) getProcessEquipment();
      return comp.getPower("kW");
    }
    return getShaftPowerKW();
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Set voltage based on power level (common practice)
    double power = getProcessShaftPowerKW();
    if (power > 5000) {
      setRatedVoltageV(11000);
    } else if (power > 200) {
      setRatedVoltageV(6600);
    } else if (power > 75) {
      setRatedVoltageV(690);
    } else {
      setRatedVoltageV(400);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    calculateAuxiliaryLoads();
  }

  /**
   * Calculate auxiliary electrical loads for the compressor package.
   */
  private void calculateAuxiliaryLoads() {
    double mainPowerKW = getShaftPowerKW();
    if (mainPowerKW <= 0) {
      return;
    }

    // Lube oil system
    if (hasLubeOilSystem) {
      lubeOilPumpKW = Math.max(1.5, mainPowerKW * 0.02);
      lubeOilHeaterKW = Math.max(2.0, mainPowerKW * 0.01);
    }

    // Seal gas system
    if (hasSealGasSystem) {
      sealGasSystemKW = Math.max(2.0, mainPowerKW * 0.015);
    }

    // Cooling fans
    if (hasAirCooling) {
      coolingFanKW = Math.max(5.0, mainPowerKW * 0.05);
    }

    totalAuxiliaryKW =
        lubeOilPumpKW + lubeOilHeaterKW + sealGasSystemKW + coolingFanKW + instrumentationKW;
  }

  /**
   * Get the total connected load including auxiliaries.
   *
   * @return total connected load in kW
   */
  public double getTotalConnectedLoadKW() {
    return getElectricalInputKW() + totalAuxiliaryKW;
  }

  // === Getters and Setters ===

  /**
   * Get lube oil pump power in kW.
   *
   * @return lube oil pump power in kW
   */
  public double getLubeOilPumpKW() {
    return lubeOilPumpKW;
  }

  /**
   * Get lube oil heater power in kW.
   *
   * @return lube oil heater power in kW
   */
  public double getLubeOilHeaterKW() {
    return lubeOilHeaterKW;
  }

  /**
   * Get seal gas system power in kW.
   *
   * @return seal gas system power in kW
   */
  public double getSealGasSystemKW() {
    return sealGasSystemKW;
  }

  /**
   * Get cooling fan power in kW.
   *
   * @return cooling fan power in kW
   */
  public double getCoolingFanKW() {
    return coolingFanKW;
  }

  /**
   * Get instrumentation power in kW.
   *
   * @return instrumentation power in kW
   */
  public double getInstrumentationKW() {
    return instrumentationKW;
  }

  /**
   * Set instrumentation power in kW.
   *
   * @param instrumentationKW instrumentation power in kW
   */
  public void setInstrumentationKW(double instrumentationKW) {
    this.instrumentationKW = instrumentationKW;
  }

  /**
   * Get total auxiliary power in kW.
   *
   * @return total auxiliary power in kW
   */
  public double getTotalAuxiliaryKW() {
    return totalAuxiliaryKW;
  }

  /**
   * Check if lube oil system is included.
   *
   * @return true if lube oil system is included
   */
  public boolean isHasLubeOilSystem() {
    return hasLubeOilSystem;
  }

  /**
   * Set whether lube oil system is included.
   *
   * @param hasLubeOilSystem true to include lube oil system
   */
  public void setHasLubeOilSystem(boolean hasLubeOilSystem) {
    this.hasLubeOilSystem = hasLubeOilSystem;
  }

  /**
   * Check if seal gas system is included.
   *
   * @return true if seal gas system is included
   */
  public boolean isHasSealGasSystem() {
    return hasSealGasSystem;
  }

  /**
   * Set whether seal gas system is included.
   *
   * @param hasSealGasSystem true to include seal gas system
   */
  public void setHasSealGasSystem(boolean hasSealGasSystem) {
    this.hasSealGasSystem = hasSealGasSystem;
  }

  /**
   * Check if air cooling is included.
   *
   * @return true if air cooling is included
   */
  public boolean isHasAirCooling() {
    return hasAirCooling;
  }

  /**
   * Set whether air cooling is included.
   *
   * @param hasAirCooling true to include air cooling
   */
  public void setHasAirCooling(boolean hasAirCooling) {
    this.hasAirCooling = hasAirCooling;
  }
}
