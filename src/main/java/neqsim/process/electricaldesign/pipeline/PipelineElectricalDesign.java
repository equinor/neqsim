package neqsim.process.electricaldesign.pipeline;

import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * Electrical design for pipelines.
 *
 * <p>
 * Extends {@link ElectricalDesign} with pipeline-specific electrical requirements. Pipelines have
 * no rotating equipment but may have significant electrical loads:
 * </p>
 * <ul>
 * <li><b>Electrical heat tracing (EHT):</b> 10-40 W/m depending on pipe diameter and temperature
 * differential, used for hydrate prevention, wax control, or maintaining process temperature</li>
 * <li><b>Cathodic protection (CP):</b> Impressed current cathodic protection systems for corrosion
 * prevention, typically 0.5-5 kW per transformer-rectifier unit</li>
 * <li><b>Instrumentation:</b> Pressure/temperature transmitters along pipeline route, typically
 * 0.5-2 kW total</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipelineElectricalDesign extends ElectricalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // === Heat tracing ===
  private boolean hasHeatTracing = false;
  private double heatTracingWPerM = 20.0;

  // === Cathodic protection ===
  private boolean hasCathodicProtection = false;
  private double cathodicProtectionKW = 2.0;

  // === Instrumentation ===
  private double instrumentationKW = 1.0;

  private double totalAuxiliaryKW;

  /**
   * Constructor for PipelineElectricalDesign.
   *
   * @param processEquipment the pipeline equipment
   */
  public PipelineElectricalDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Pipelines have no rotating equipment. Shaft power is zero.
   * </p>
   */
  @Override
  protected double getProcessShaftPowerKW() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
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

    double current = getFullLoadCurrentA();
    if (current > 0) {
      getPowerCable().sizeCable(current, getRatedVoltageV(), getPowerCable().getLengthM(), "Tray",
          40.0);
      getSwitchgear().sizeSwitchgear(current, totalAuxiliaryKW, getRatedVoltageV(), false);
    }
  }

  /**
   * Calculate total auxiliary electrical loads for the pipeline.
   */
  private void calculateAuxiliaryLoads() {
    double heatTracingKW = 0.0;
    if (hasHeatTracing) {
      double lengthM = getPipelineLength();
      heatTracingKW = (heatTracingWPerM * lengthM) / 1000.0;
    }

    double cpKW = hasCathodicProtection ? cathodicProtectionKW : 0.0;
    totalAuxiliaryKW = heatTracingKW + cpKW + instrumentationKW;
  }

  /**
   * Get the pipeline length in metres from the process equipment.
   *
   * @return pipeline length in metres, or 0 if not available
   */
  private double getPipelineLength() {
    if (getProcessEquipment() instanceof AdiabaticPipe) {
      return ((AdiabaticPipe) getProcessEquipment()).getLength();
    }
    if (getProcessEquipment() instanceof PipeBeggsAndBrills) {
      return ((PipeBeggsAndBrills) getProcessEquipment()).getLength();
    }
    return 0.0;
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

  /**
   * Get heat tracing power in watts per metre.
   *
   * @return heat tracing power in W/m
   */
  public double getHeatTracingWPerM() {
    return heatTracingWPerM;
  }

  /**
   * Set heat tracing power in watts per metre.
   *
   * @param heatTracingWPerM heat tracing power in W/m (typical range: 10-40)
   */
  public void setHeatTracingWPerM(double heatTracingWPerM) {
    this.heatTracingWPerM = heatTracingWPerM;
  }

  /**
   * Check if cathodic protection is enabled.
   *
   * @return true if cathodic protection is enabled
   */
  public boolean isHasCathodicProtection() {
    return hasCathodicProtection;
  }

  /**
   * Set whether cathodic protection is enabled.
   *
   * @param hasCathodicProtection true to enable cathodic protection
   */
  public void setHasCathodicProtection(boolean hasCathodicProtection) {
    this.hasCathodicProtection = hasCathodicProtection;
  }

  /**
   * Get cathodic protection system power in kW.
   *
   * @return cathodic protection power in kW
   */
  public double getCathodicProtectionKW() {
    return cathodicProtectionKW;
  }

  /**
   * Set cathodic protection system power in kW.
   *
   * @param cathodicProtectionKW cathodic protection power in kW (typical range: 0.5-5)
   */
  public void setCathodicProtectionKW(double cathodicProtectionKW) {
    this.cathodicProtectionKW = cathodicProtectionKW;
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
}
