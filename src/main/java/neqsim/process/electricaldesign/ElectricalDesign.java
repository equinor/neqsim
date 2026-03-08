package neqsim.process.electricaldesign;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.electricaldesign.components.ElectricalCable;
import neqsim.process.electricaldesign.components.ElectricalMotor;
import neqsim.process.electricaldesign.components.HazardousAreaClassification;
import neqsim.process.electricaldesign.components.Switchgear;
import neqsim.process.electricaldesign.components.Transformer;
import neqsim.process.electricaldesign.components.VariableFrequencyDrive;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Base class for electrical design of process equipment.
 *
 * <p>
 * Mirrors the {@link neqsim.process.mechanicaldesign.MechanicalDesign} pattern. Each piece of
 * process equipment can have an associated electrical design that sizes motors, VFDs, cables,
 * switchgear, and transformers based on the process duty.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ElectricalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process equipment this electrical design belongs to. */
  private ProcessEquipmentInterface processEquipment;

  // === Power requirements (derived from process) ===
  private double shaftPowerKW;
  private double electricalInputKW;
  private double apparentPowerKVA;
  private double reactivePowerKVAR;
  private double powerFactor = 0.85;

  // === Voltage and frequency ===
  private double ratedVoltageV = 400.0;
  private double frequencyHz = 50.0;
  private int phases = 3;

  // === Electrical components ===
  private ElectricalMotor motor;
  private VariableFrequencyDrive vfd;
  private ElectricalCable powerCable;
  private ElectricalCable controlCable;
  private Switchgear switchgear;
  private Transformer transformer;
  private HazardousAreaClassification hazArea;

  // === Design margins ===
  private double motorSizingMargin = 1.10;
  private double cableDeratingFactor = 1.0;
  private double diversityFactor = 1.0;
  private boolean continuousDuty = true;
  private boolean useVFD = false;

  // === Standards ===
  private String motorStandard = "IEC";
  private String cableStandard = "IEC 60502";
  private String hazAreaStandard = "IECEx";

  /**
   * Constructor for ElectricalDesign.
   *
   * @param processEquipment the process equipment this design belongs to
   */
  public ElectricalDesign(ProcessEquipmentInterface processEquipment) {
    this.processEquipment = processEquipment;
    this.motor = new ElectricalMotor();
    this.powerCable = new ElectricalCable();
    this.controlCable = new ElectricalCable();
    this.switchgear = new Switchgear();
    this.hazArea = new HazardousAreaClassification();
  }

  /**
   * Run the electrical design calculation.
   *
   * <p>
   * Sizes the motor, optional VFD, cables, and switchgear based on the process equipment's shaft
   * power requirement.
   * </p>
   */
  public void calcDesign() {
    readDesignSpecifications();
    shaftPowerKW = getProcessShaftPowerKW();

    if (shaftPowerKW <= 0) {
      return;
    }

    // 1. Size motor
    motor.sizeMotor(shaftPowerKW, motorSizingMargin, motorStandard);
    motor.setRatedVoltageV(ratedVoltageV);
    motor.setFrequencyHz(frequencyHz);

    // 2. Size VFD if applicable
    if (useVFD) {
      if (vfd == null) {
        vfd = new VariableFrequencyDrive();
      }
      vfd.sizeVFD(motor);
    }

    // 3. Calculate total electrical input
    double motorEfficiency = motor.getEfficiencyPercent() / 100.0;
    double motorInput = shaftPowerKW / motorEfficiency;
    if (useVFD && vfd != null) {
      electricalInputKW = vfd.getElectricalInputKW(motorInput);
      powerFactor = vfd.getInputPowerFactor();
    } else {
      electricalInputKW = motorInput;
      powerFactor = motor.getPowerFactorFL();
    }

    // 4. Calculate apparent and reactive power
    if (powerFactor > 0) {
      apparentPowerKVA = electricalInputKW / powerFactor;
      reactivePowerKVAR = apparentPowerKVA * Math.sin(Math.acos(powerFactor));
    }

    // 5. Size power cable
    double fullLoadCurrent = getFullLoadCurrentA();
    powerCable.sizeCable(fullLoadCurrent, ratedVoltageV, powerCable.getLengthM(), "Tray", 40.0);

    // 6. Size switchgear
    switchgear.sizeSwitchgear(fullLoadCurrent, motor.getRatedPowerKW(), ratedVoltageV, useVFD);
  }

  /**
   * Read design specifications from data sources.
   *
   * <p>
   * Subclasses can override to load equipment-specific electrical specifications.
   * </p>
   */
  public void readDesignSpecifications() {}

  /**
   * Get shaft power from the process equipment.
   *
   * <p>
   * Default returns the manually set shaftPowerKW value. Equipment-specific subclasses should
   * override this to read from the process equipment directly.
   * </p>
   *
   * @return shaft power in kW
   */
  protected double getProcessShaftPowerKW() {
    return shaftPowerKW;
  }

  /**
   * Calculate the full-load current in amperes.
   *
   * @return full-load current in A
   */
  public double getFullLoadCurrentA() {
    if (ratedVoltageV <= 0 || powerFactor <= 0) {
      return 0.0;
    }
    if (phases == 3) {
      return (electricalInputKW * 1000.0) / (Math.sqrt(3) * ratedVoltageV * powerFactor);
    } else {
      return (electricalInputKW * 1000.0) / (ratedVoltageV * powerFactor);
    }
  }

  /**
   * Calculate the starting current in amperes.
   *
   * @return starting current in A
   */
  public double getStartingCurrentA() {
    if (useVFD) {
      return getFullLoadCurrentA() * 1.0;
    }
    return getFullLoadCurrentA() * motor.getLockedRotorCurrentMultiplier();
  }

  /**
   * Get total electrical losses (motor + VFD) in kW.
   *
   * @return total electrical losses in kW
   */
  public double getTotalElectricalLossesKW() {
    return electricalInputKW - shaftPowerKW;
  }

  /**
   * Serialize the electrical design to JSON.
   *
   * @return JSON string with all electrical design data
   */
  public String toJson() {
    ElectricalDesignResponse response = new ElectricalDesignResponse(this);
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(JsonParser.parseString(response.toJson()));
  }

  // === Getters and Setters ===

  /**
   * Get the process equipment.
   *
   * @return the process equipment
   */
  public ProcessEquipmentInterface getProcessEquipment() {
    return processEquipment;
  }

  /**
   * Get shaft power in kW.
   *
   * @return shaft power in kW
   */
  public double getShaftPowerKW() {
    return shaftPowerKW;
  }

  /**
   * Set shaft power in kW.
   *
   * @param shaftPowerKW shaft power in kW
   */
  public void setShaftPowerKW(double shaftPowerKW) {
    this.shaftPowerKW = shaftPowerKW;
  }

  /**
   * Get electrical input power in kW.
   *
   * @return electrical input power in kW
   */
  public double getElectricalInputKW() {
    return electricalInputKW;
  }

  /**
   * Set electrical input power in kW.
   *
   * @param electricalInputKW electrical input power in kW
   */
  public void setElectricalInputKW(double electricalInputKW) {
    this.electricalInputKW = electricalInputKW;
  }

  /**
   * Get apparent power in kVA.
   *
   * @return apparent power in kVA
   */
  public double getApparentPowerKVA() {
    return apparentPowerKVA;
  }

  /**
   * Set apparent power in kVA.
   *
   * @param apparentPowerKVA apparent power in kVA
   */
  public void setApparentPowerKVA(double apparentPowerKVA) {
    this.apparentPowerKVA = apparentPowerKVA;
  }

  /**
   * Get reactive power in kVAR.
   *
   * @return reactive power in kVAR
   */
  public double getReactivePowerKVAR() {
    return reactivePowerKVAR;
  }

  /**
   * Set reactive power in kVAR.
   *
   * @param reactivePowerKVAR reactive power in kVAR
   */
  public void setReactivePowerKVAR(double reactivePowerKVAR) {
    this.reactivePowerKVAR = reactivePowerKVAR;
  }

  /**
   * Get the power factor (cos phi).
   *
   * @return power factor
   */
  public double getPowerFactor() {
    return powerFactor;
  }

  /**
   * Set the power factor (cos phi).
   *
   * @param powerFactor power factor
   */
  public void setPowerFactor(double powerFactor) {
    this.powerFactor = powerFactor;
  }

  /**
   * Get rated voltage in volts.
   *
   * @return rated voltage in V
   */
  public double getRatedVoltageV() {
    return ratedVoltageV;
  }

  /**
   * Set rated voltage in volts.
   *
   * @param ratedVoltageV rated voltage in V
   */
  public void setRatedVoltageV(double ratedVoltageV) {
    this.ratedVoltageV = ratedVoltageV;
  }

  /**
   * Get supply frequency in Hz.
   *
   * @return frequency in Hz
   */
  public double getFrequencyHz() {
    return frequencyHz;
  }

  /**
   * Set supply frequency in Hz.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Get number of phases.
   *
   * @return number of phases (1 or 3)
   */
  public int getPhases() {
    return phases;
  }

  /**
   * Set number of phases.
   *
   * @param phases number of phases (1 or 3)
   */
  public void setPhases(int phases) {
    this.phases = phases;
  }

  /**
   * Get the electrical motor.
   *
   * @return the motor
   */
  public ElectricalMotor getMotor() {
    return motor;
  }

  /**
   * Set the electrical motor.
   *
   * @param motor the motor
   */
  public void setMotor(ElectricalMotor motor) {
    this.motor = motor;
  }

  /**
   * Get the variable frequency drive.
   *
   * @return the VFD, or null if not used
   */
  public VariableFrequencyDrive getVfd() {
    return vfd;
  }

  /**
   * Set the variable frequency drive.
   *
   * @param vfd the VFD
   */
  public void setVfd(VariableFrequencyDrive vfd) {
    this.vfd = vfd;
  }

  /**
   * Get the power cable.
   *
   * @return the power cable
   */
  public ElectricalCable getPowerCable() {
    return powerCable;
  }

  /**
   * Set the power cable.
   *
   * @param powerCable the power cable
   */
  public void setPowerCable(ElectricalCable powerCable) {
    this.powerCable = powerCable;
  }

  /**
   * Get the control cable.
   *
   * @return the control cable
   */
  public ElectricalCable getControlCable() {
    return controlCable;
  }

  /**
   * Set the control cable.
   *
   * @param controlCable the control cable
   */
  public void setControlCable(ElectricalCable controlCable) {
    this.controlCable = controlCable;
  }

  /**
   * Get the switchgear.
   *
   * @return the switchgear
   */
  public Switchgear getSwitchgear() {
    return switchgear;
  }

  /**
   * Set the switchgear.
   *
   * @param switchgear the switchgear
   */
  public void setSwitchgear(Switchgear switchgear) {
    this.switchgear = switchgear;
  }

  /**
   * Get the transformer.
   *
   * @return the transformer, or null if not applicable
   */
  public Transformer getTransformer() {
    return transformer;
  }

  /**
   * Set the transformer.
   *
   * @param transformer the transformer
   */
  public void setTransformer(Transformer transformer) {
    this.transformer = transformer;
  }

  /**
   * Get the hazardous area classification.
   *
   * @return hazardous area classification
   */
  public HazardousAreaClassification getHazArea() {
    return hazArea;
  }

  /**
   * Set the hazardous area classification.
   *
   * @param hazArea hazardous area classification
   */
  public void setHazArea(HazardousAreaClassification hazArea) {
    this.hazArea = hazArea;
  }

  /**
   * Get the motor sizing margin.
   *
   * @return motor sizing margin (e.g. 1.10 for 10%)
   */
  public double getMotorSizingMargin() {
    return motorSizingMargin;
  }

  /**
   * Set the motor sizing margin.
   *
   * @param motorSizingMargin motor sizing margin (e.g. 1.10 for 10%)
   */
  public void setMotorSizingMargin(double motorSizingMargin) {
    this.motorSizingMargin = motorSizingMargin;
  }

  /**
   * Get the cable derating factor.
   *
   * @return cable derating factor
   */
  public double getCableDeratingFactor() {
    return cableDeratingFactor;
  }

  /**
   * Set the cable derating factor.
   *
   * @param cableDeratingFactor cable derating factor
   */
  public void setCableDeratingFactor(double cableDeratingFactor) {
    this.cableDeratingFactor = cableDeratingFactor;
  }

  /**
   * Get the diversity factor for load list contribution.
   *
   * @return diversity factor (0-1)
   */
  public double getDiversityFactor() {
    return diversityFactor;
  }

  /**
   * Set the diversity factor for load list contribution.
   *
   * @param diversityFactor diversity factor (0-1)
   */
  public void setDiversityFactor(double diversityFactor) {
    this.diversityFactor = diversityFactor;
  }

  /**
   * Check if equipment is continuous duty.
   *
   * @return true if continuous duty (S1)
   */
  public boolean isContinuousDuty() {
    return continuousDuty;
  }

  /**
   * Set continuous duty flag.
   *
   * @param continuousDuty true for continuous duty (S1)
   */
  public void setContinuousDuty(boolean continuousDuty) {
    this.continuousDuty = continuousDuty;
  }

  /**
   * Check if VFD is used.
   *
   * @return true if VFD is used
   */
  public boolean isUseVFD() {
    return useVFD;
  }

  /**
   * Set whether VFD is used.
   *
   * @param useVFD true to use VFD
   */
  public void setUseVFD(boolean useVFD) {
    this.useVFD = useVFD;
  }

  /**
   * Get the motor standard.
   *
   * @return motor standard ("IEC" or "NEMA")
   */
  public String getMotorStandard() {
    return motorStandard;
  }

  /**
   * Set the motor standard.
   *
   * @param motorStandard motor standard ("IEC" or "NEMA")
   */
  public void setMotorStandard(String motorStandard) {
    this.motorStandard = motorStandard;
  }

  /**
   * Get the cable standard.
   *
   * @return cable standard
   */
  public String getCableStandard() {
    return cableStandard;
  }

  /**
   * Set the cable standard.
   *
   * @param cableStandard cable standard
   */
  public void setCableStandard(String cableStandard) {
    this.cableStandard = cableStandard;
  }

  /**
   * Get the hazardous area classification standard.
   *
   * @return hazardous area standard
   */
  public String getHazAreaStandard() {
    return hazAreaStandard;
  }

  /**
   * Set the hazardous area classification standard.
   *
   * @param hazAreaStandard hazardous area standard
   */
  public void setHazAreaStandard(String hazAreaStandard) {
    this.hazAreaStandard = hazAreaStandard;
  }
}
