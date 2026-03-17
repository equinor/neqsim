package neqsim.process.electricaldesign.separator;

import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;

/**
 * Electrical design for separators.
 *
 * <p>
 * Extends {@link ElectricalDesign} with separator-specific electrical requirements. Separators have
 * no rotating equipment (no shaft power) but consume electrical power through auxiliary loads:
 * </p>
 * <ul>
 * <li>Actuated control valves (level, pressure, dump): typically 0.5-2 kW each</li>
 * <li>Instrumentation (level transmitters, pressure, temperature, flow): 1-3 kW total</li>
 * <li>Lighting (hazardous area rated): 0.5-1 kW</li>
 * <li>Heat tracing (if required for viscous fluids or hydrate prevention): 5-20 kW</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SeparatorElectricalDesign extends ElectricalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // === Auxiliary loads ===
  private int numberOfControlValves = 3;
  private double controlValvePowerKW = 1.0;
  private double instrumentationKW = 2.0;
  private double lightingKW = 0.5;
  private double heatTracingKW = 0.0;
  private boolean hasHeatTracing = false;
  private double totalAuxiliaryKW;

  /**
   * Constructor for SeparatorElectricalDesign.
   *
   * @param processEquipment the separator equipment
   */
  public SeparatorElectricalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Separators have no rotating equipment, so shaft power is zero. All electrical loads are
   * auxiliary.
   * </p>
   */
  @Override
  protected double getProcessShaftPowerKW() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Separators use low voltage for auxiliary loads
    setRatedVoltageV(400);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    readDesignSpecifications();
    calculateAuxiliaryLoads();
    setShaftPowerKW(0.0);
    setElectricalInputKW(totalAuxiliaryKW);

    if (totalAuxiliaryKW > 0 && getPowerFactor() > 0) {
      setApparentPowerKVA(totalAuxiliaryKW / getPowerFactor());
      setReactivePowerKVAR(getApparentPowerKVA() * Math.sin(Math.acos(getPowerFactor())));
    }

    // Size cable for auxiliary power feed
    double current = getFullLoadCurrentA();
    if (current > 0) {
      getPowerCable().sizeCable(current, getRatedVoltageV(), getPowerCable().getLengthM(), "Tray",
          40.0);
      getSwitchgear().sizeSwitchgear(current, totalAuxiliaryKW, getRatedVoltageV(), false);
    }
  }

  /**
   * Calculate the total auxiliary electrical loads for the separator.
   */
  private void calculateAuxiliaryLoads() {
    double valveLoad = numberOfControlValves * controlValvePowerKW;
    double tracing = hasHeatTracing ? heatTracingKW : 0.0;
    totalAuxiliaryKW = valveLoad + instrumentationKW + lightingKW + tracing;
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
   * Get the number of actuated control valves.
   *
   * @return number of control valves
   */
  public int getNumberOfControlValves() {
    return numberOfControlValves;
  }

  /**
   * Set the number of actuated control valves.
   *
   * @param numberOfControlValves number of control valves
   */
  public void setNumberOfControlValves(int numberOfControlValves) {
    this.numberOfControlValves = numberOfControlValves;
  }

  /**
   * Get power per control valve actuator in kW.
   *
   * @return power per valve in kW
   */
  public double getControlValvePowerKW() {
    return controlValvePowerKW;
  }

  /**
   * Set power per control valve actuator in kW.
   *
   * @param controlValvePowerKW power per valve in kW
   */
  public void setControlValvePowerKW(double controlValvePowerKW) {
    this.controlValvePowerKW = controlValvePowerKW;
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
   * Get lighting power in kW.
   *
   * @return lighting power in kW
   */
  public double getLightingKW() {
    return lightingKW;
  }

  /**
   * Set lighting power in kW.
   *
   * @param lightingKW lighting power in kW
   */
  public void setLightingKW(double lightingKW) {
    this.lightingKW = lightingKW;
  }

  /**
   * Get heat tracing power in kW.
   *
   * @return heat tracing power in kW
   */
  public double getHeatTracingKW() {
    return heatTracingKW;
  }

  /**
   * Set heat tracing power in kW.
   *
   * @param heatTracingKW heat tracing power in kW
   */
  public void setHeatTracingKW(double heatTracingKW) {
    this.heatTracingKW = heatTracingKW;
  }

  /**
   * Check if heat tracing is enabled.
   *
   * @return true if heat tracing is enabled
   */
  public boolean isHasHeatTracing() {
    return hasHeatTracing;
  }

  /**
   * Set whether heat tracing is enabled.
   *
   * @param hasHeatTracing true to enable heat tracing
   */
  public void setHasHeatTracing(boolean hasHeatTracing) {
    this.hasHeatTracing = hasHeatTracing;
  }
}
