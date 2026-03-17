package neqsim.process.electricaldesign.heatexchanger;

import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;

/**
 * Electrical design for heat exchangers (heaters, coolers, and heat exchangers).
 *
 * <p>
 * Extends {@link ElectricalDesign} with heat-exchanger-specific electrical requirements. The
 * electrical design depends on the type of heat exchanger:
 * </p>
 * <ul>
 * <li><b>Electric heater:</b> Full heating duty is electrical (resistance heating elements)</li>
 * <li><b>Air cooler:</b> Fan motor(s) sized based on cooling duty — typically 0.5-2% of thermal
 * duty, minimum 2 kW</li>
 * <li><b>Shell-and-tube:</b> Cooling water pump and instrumentation only</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HeatExchangerElectricalDesign extends ElectricalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Type of heat exchanger for electrical sizing.
   */
  public enum HeatExchangerType {
    /** Electric resistance heater — full duty is electrical. */
    ELECTRIC_HEATER,
    /** Air-cooled heat exchanger with fan motors. */
    AIR_COOLER,
    /** Shell-and-tube or plate heat exchanger — minimal electrical loads. */
    SHELL_AND_TUBE
  }

  private HeatExchangerType heatExchangerType = HeatExchangerType.SHELL_AND_TUBE;
  private int numberOfFans = 2;
  private double fanEfficiency = 0.65;
  private double instrumentationKW = 1.5;
  private double coolingWaterPumpKW = 0.0;
  private double totalAuxiliaryKW;

  /**
   * Constructor for HeatExchangerElectricalDesign.
   *
   * @param processEquipment the heat exchanger equipment
   */
  public HeatExchangerElectricalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    autoDetectType();
  }

  /**
   * Auto-detect the heat exchanger type from the equipment class.
   */
  private void autoDetectType() {
    if (getProcessEquipment() instanceof Cooler) {
      heatExchangerType = HeatExchangerType.AIR_COOLER;
    } else if (getProcessEquipment() instanceof Heater) {
      heatExchangerType = HeatExchangerType.ELECTRIC_HEATER;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For electric heaters, the shaft power represents the heating element power derived from the
   * process duty. For air coolers, this returns fan motor shaft power. For shell-and-tube, returns
   * zero (auxiliary loads only).
   * </p>
   */
  @Override
  protected double getProcessShaftPowerKW() {
    if (!(getProcessEquipment() instanceof Heater)) {
      return 0.0;
    }
    Heater heater = (Heater) getProcessEquipment();
    double dutyW = Math.abs(heater.getDuty());
    double dutyKW = dutyW / 1000.0;

    switch (heatExchangerType) {
      case ELECTRIC_HEATER:
        // Full thermal duty supplied as electrical power
        return dutyKW;
      case AIR_COOLER:
        // Fan power is typically 0.5-2% of cooling duty, minimum 2 kW per fan
        double fanPowerKW = Math.max(2.0 * numberOfFans, dutyKW * 0.01);
        return fanPowerKW;
      case SHELL_AND_TUBE:
      default:
        return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    double power = getProcessShaftPowerKW();
    switch (heatExchangerType) {
      case ELECTRIC_HEATER:
        if (power > 200) {
          setRatedVoltageV(6600);
        } else if (power > 75) {
          setRatedVoltageV(690);
        } else {
          setRatedVoltageV(400);
        }
        break;
      case AIR_COOLER:
        if (power > 200) {
          setRatedVoltageV(690);
        } else {
          setRatedVoltageV(400);
        }
        break;
      case SHELL_AND_TUBE:
      default:
        setRatedVoltageV(400);
        break;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (heatExchangerType == HeatExchangerType.ELECTRIC_HEATER
        || heatExchangerType == HeatExchangerType.AIR_COOLER) {
      // Motor-driven or direct electrical — use base class motor/cable sizing
      super.calcDesign();
    } else {
      // Shell-and-tube: auxiliary loads only, no motor
      readDesignSpecifications();
      setShaftPowerKW(0.0);
    }
    calculateAuxiliaryLoads();

    double totalKW = getElectricalInputKW() + totalAuxiliaryKW;
    if (heatExchangerType == HeatExchangerType.SHELL_AND_TUBE) {
      setElectricalInputKW(totalAuxiliaryKW);
      if (totalAuxiliaryKW > 0 && getPowerFactor() > 0) {
        setApparentPowerKVA(totalAuxiliaryKW / getPowerFactor());
        setReactivePowerKVAR(getApparentPowerKVA() * Math.sin(Math.acos(getPowerFactor())));
      }
    }
  }

  /**
   * Calculate auxiliary loads (instrumentation, CW pump).
   */
  private void calculateAuxiliaryLoads() {
    totalAuxiliaryKW = instrumentationKW;
    if (heatExchangerType == HeatExchangerType.SHELL_AND_TUBE) {
      totalAuxiliaryKW += coolingWaterPumpKW;
    }
  }

  /**
   * Get the total auxiliary power in kW.
   *
   * @return total auxiliary power in kW
   */
  public double getTotalAuxiliaryKW() {
    return totalAuxiliaryKW;
  }

  /**
   * Get the total connected load including main duty and auxiliaries.
   *
   * @return total connected load in kW
   */
  public double getTotalConnectedLoadKW() {
    return getElectricalInputKW() + totalAuxiliaryKW;
  }

  /**
   * Get the heat exchanger type.
   *
   * @return heat exchanger type
   */
  public HeatExchangerType getHeatExchangerType() {
    return heatExchangerType;
  }

  /**
   * Set the heat exchanger type.
   *
   * @param heatExchangerType heat exchanger type
   */
  public void setHeatExchangerType(HeatExchangerType heatExchangerType) {
    this.heatExchangerType = heatExchangerType;
  }

  /**
   * Get the number of fans (for air coolers).
   *
   * @return number of fans
   */
  public int getNumberOfFans() {
    return numberOfFans;
  }

  /**
   * Set the number of fans (for air coolers).
   *
   * @param numberOfFans number of fans
   */
  public void setNumberOfFans(int numberOfFans) {
    this.numberOfFans = numberOfFans;
  }

  /**
   * Get the fan efficiency.
   *
   * @return fan efficiency (0-1)
   */
  public double getFanEfficiency() {
    return fanEfficiency;
  }

  /**
   * Set the fan efficiency.
   *
   * @param fanEfficiency fan efficiency (0-1)
   */
  public void setFanEfficiency(double fanEfficiency) {
    this.fanEfficiency = fanEfficiency;
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
   * Get cooling water pump power in kW.
   *
   * @return cooling water pump power in kW
   */
  public double getCoolingWaterPumpKW() {
    return coolingWaterPumpKW;
  }

  /**
   * Set cooling water pump power in kW.
   *
   * @param coolingWaterPumpKW cooling water pump power in kW
   */
  public void setCoolingWaterPumpKW(double coolingWaterPumpKW) {
    this.coolingWaterPumpKW = coolingWaterPumpKW;
  }
}
