package neqsim.process.mechanicaldesign.pump;

import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Response class for pump mechanical design JSON export.
 *
 * <p>
 * Extends {@link MechanicalDesignResponse} with pump-specific parameters including hydraulic data,
 * driver sizing, NPSH requirements, and seal specifications per API 610.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class PumpMechanicalDesignResponse extends MechanicalDesignResponse {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Pump-Specific Parameters
  // ============================================================================

  /** Pump type (overhung, between bearings, vertically suspended). */
  private String pumpType;

  /** API 610 type code (OH1, OH2, BB1, VS1, etc.). */
  private String api610TypeCode;

  /** Seal type (packed, single mechanical, double mechanical, etc.). */
  private String sealType;

  /** Number of stages. */
  private int numberOfStages;

  /** Impeller diameter [mm]. */
  private double impellerDiameter;

  /** Impeller width [mm]. */
  private double impellerWidth;

  /** Shaft diameter at impeller [mm]. */
  private double shaftDiameter;

  /** Rated speed [rpm]. */
  private double ratedSpeed;

  /** Specific speed (Ns). */
  private double specificSpeed;

  /** Suction specific speed (Nss). */
  private double suctionSpecificSpeed;

  /** Required driver power [kW]. */
  private double driverPower;

  /** Driver power margin factor. */
  private double driverMargin;

  /** Rated flow [m³/h]. */
  private double ratedFlow;

  /** Rated head [m]. */
  private double ratedHead;

  /** Best efficiency point flow [m³/h]. */
  private double bepFlow;

  /** Best efficiency point head [m]. */
  private double bepHead;

  /** Pump efficiency at rated point. */
  private double efficiency;

  /** Net positive suction head required [m]. */
  private double npshr;

  /** Net positive suction head available [m]. */
  private double npsha;

  /** NPSH margin [m]. */
  private double npshMargin;

  /** Casing wall thickness [mm]. */
  private double casingWallThickness;

  /** Suction nozzle size [inches]. */
  private double suctionNozzleSize;

  /** Discharge nozzle size [inches]. */
  private double dischargeNozzleSize;

  /** Minimum continuous flow [m³/h]. */
  private double minimumContinuousFlow;

  /** Suction pressure [bara]. */
  private double suctionPressure;

  /** Discharge pressure [bara]. */
  private double dischargePressure;

  /** Differential pressure [bar]. */
  private double differentialPressure;

  /** Fluid temperature [°C]. */
  private double fluidTemperature;

  /** Fluid density [kg/m³]. */
  private double fluidDensity;

  /** Fluid viscosity [cP]. */
  private double fluidViscosity;

  // ============================================================================
  // Process Design Parameters (added for TR3500 compliance)
  // ============================================================================

  /** NPSH margin factor. */
  private double npshMarginFactor;

  /** Hydraulic power margin factor. */
  private double hydraulicPowerMargin;

  /** Preferred Operating Region low boundary fraction of BEP. */
  private double porLowFraction;

  /** Preferred Operating Region high boundary fraction of BEP. */
  private double porHighFraction;

  /** Allowable Operating Region low boundary fraction of BEP. */
  private double aorLowFraction;

  /** Allowable Operating Region high boundary fraction of BEP. */
  private double aorHighFraction;

  /** Maximum suction specific speed. */
  private double maxSuctionSpecificSpeed;

  /** Head margin factor. */
  private double headMarginFactor;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public PumpMechanicalDesignResponse() {
    super();
    setEquipmentType("Pump");
    setDesignStandard("API 610");
  }

  /**
   * Constructor from PumpMechanicalDesign.
   *
   * @param mecDesign the pump mechanical design
   */
  public PumpMechanicalDesignResponse(PumpMechanicalDesign mecDesign) {
    super(mecDesign);
    setEquipmentType("Pump");
    setDesignStandard("API 610");
    populateFromPumpDesign(mecDesign);
  }

  /**
   * Populate pump-specific fields from PumpMechanicalDesign.
   *
   * @param mecDesign the pump mechanical design
   */
  public void populateFromPumpDesign(PumpMechanicalDesign mecDesign) {
    if (mecDesign == null) {
      return;
    }

    // Use only methods that exist in PumpMechanicalDesign
    this.numberOfStages = mecDesign.getNumberOfStages();
    this.impellerDiameter = mecDesign.getImpellerDiameter();
    this.shaftDiameter = mecDesign.getShaftDiameter();
    this.ratedSpeed = mecDesign.getRatedSpeed();
    this.specificSpeed = mecDesign.getSpecificSpeed();
    this.suctionSpecificSpeed = mecDesign.getSuctionSpecificSpeed();
    this.driverPower = mecDesign.getDriverPower();
    this.npshr = mecDesign.getNpshRequired();
    this.suctionNozzleSize = mecDesign.getSuctionNozzleSize();
    this.dischargeNozzleSize = mecDesign.getDischargeNozzleSize();
    this.minimumContinuousFlow = mecDesign.getMinimumFlow();

    if (mecDesign.getPumpType() != null) {
      this.pumpType = mecDesign.getPumpType().name();
    }
    if (mecDesign.getSealType() != null) {
      this.sealType = mecDesign.getSealType().name();
    }

    // Populate process design parameters
    this.npshMarginFactor = mecDesign.getNpshMarginFactor();
    this.hydraulicPowerMargin = mecDesign.getHydraulicPowerMargin();
    this.porLowFraction = mecDesign.getPorLowFraction();
    this.porHighFraction = mecDesign.getPorHighFraction();
    this.aorLowFraction = mecDesign.getAorLowFraction();
    this.aorHighFraction = mecDesign.getAorHighFraction();
    this.maxSuctionSpecificSpeed = mecDesign.getMaxSuctionSpecificSpeed();
    this.headMarginFactor = mecDesign.getHeadMarginFactor();
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  public String getPumpType() {
    return pumpType;
  }

  public void setPumpType(String pumpType) {
    this.pumpType = pumpType;
  }

  public String getApi610TypeCode() {
    return api610TypeCode;
  }

  public void setApi610TypeCode(String api610TypeCode) {
    this.api610TypeCode = api610TypeCode;
  }

  public String getSealType() {
    return sealType;
  }

  public void setSealType(String sealType) {
    this.sealType = sealType;
  }

  public int getNumberOfStages() {
    return numberOfStages;
  }

  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
  }

  public double getImpellerWidth() {
    return impellerWidth;
  }

  public void setImpellerWidth(double impellerWidth) {
    this.impellerWidth = impellerWidth;
  }

  public double getShaftDiameter() {
    return shaftDiameter;
  }

  public void setShaftDiameter(double shaftDiameter) {
    this.shaftDiameter = shaftDiameter;
  }

  public double getRatedSpeed() {
    return ratedSpeed;
  }

  public void setRatedSpeed(double ratedSpeed) {
    this.ratedSpeed = ratedSpeed;
  }

  public double getSpecificSpeed() {
    return specificSpeed;
  }

  public void setSpecificSpeed(double specificSpeed) {
    this.specificSpeed = specificSpeed;
  }

  public double getSuctionSpecificSpeed() {
    return suctionSpecificSpeed;
  }

  public void setSuctionSpecificSpeed(double suctionSpecificSpeed) {
    this.suctionSpecificSpeed = suctionSpecificSpeed;
  }

  public double getDriverPower() {
    return driverPower;
  }

  public void setDriverPower(double driverPower) {
    this.driverPower = driverPower;
  }

  public double getDriverMargin() {
    return driverMargin;
  }

  public void setDriverMargin(double driverMargin) {
    this.driverMargin = driverMargin;
  }

  public double getRatedFlow() {
    return ratedFlow;
  }

  public void setRatedFlow(double ratedFlow) {
    this.ratedFlow = ratedFlow;
  }

  public double getRatedHead() {
    return ratedHead;
  }

  public void setRatedHead(double ratedHead) {
    this.ratedHead = ratedHead;
  }

  public double getBepFlow() {
    return bepFlow;
  }

  public void setBepFlow(double bepFlow) {
    this.bepFlow = bepFlow;
  }

  public double getBepHead() {
    return bepHead;
  }

  public void setBepHead(double bepHead) {
    this.bepHead = bepHead;
  }

  public double getEfficiency() {
    return efficiency;
  }

  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  public double getNpshr() {
    return npshr;
  }

  public void setNpshr(double npshr) {
    this.npshr = npshr;
  }

  public double getNpsha() {
    return npsha;
  }

  public void setNpsha(double npsha) {
    this.npsha = npsha;
  }

  public double getNpshMargin() {
    return npshMargin;
  }

  public void setNpshMargin(double npshMargin) {
    this.npshMargin = npshMargin;
  }

  public double getCasingWallThickness() {
    return casingWallThickness;
  }

  public void setCasingWallThickness(double casingWallThickness) {
    this.casingWallThickness = casingWallThickness;
  }

  public double getSuctionNozzleSize() {
    return suctionNozzleSize;
  }

  public void setSuctionNozzleSize(double suctionNozzleSize) {
    this.suctionNozzleSize = suctionNozzleSize;
  }

  public double getDischargeNozzleSize() {
    return dischargeNozzleSize;
  }

  public void setDischargeNozzleSize(double dischargeNozzleSize) {
    this.dischargeNozzleSize = dischargeNozzleSize;
  }

  public double getMinimumContinuousFlow() {
    return minimumContinuousFlow;
  }

  public void setMinimumContinuousFlow(double minimumContinuousFlow) {
    this.minimumContinuousFlow = minimumContinuousFlow;
  }

  public double getSuctionPressure() {
    return suctionPressure;
  }

  public void setSuctionPressure(double suctionPressure) {
    this.suctionPressure = suctionPressure;
  }

  public double getDischargePressure() {
    return dischargePressure;
  }

  public void setDischargePressure(double dischargePressure) {
    this.dischargePressure = dischargePressure;
  }

  public double getDifferentialPressure() {
    return differentialPressure;
  }

  public void setDifferentialPressure(double differentialPressure) {
    this.differentialPressure = differentialPressure;
  }

  public double getFluidTemperature() {
    return fluidTemperature;
  }

  public void setFluidTemperature(double fluidTemperature) {
    this.fluidTemperature = fluidTemperature;
  }

  public double getFluidDensity() {
    return fluidDensity;
  }

  public void setFluidDensity(double fluidDensity) {
    this.fluidDensity = fluidDensity;
  }

  public double getFluidViscosity() {
    return fluidViscosity;
  }

  public void setFluidViscosity(double fluidViscosity) {
    this.fluidViscosity = fluidViscosity;
  }

  // ============================================================================
  // Getters and Setters for Process Design Parameters
  // ============================================================================

  public double getNpshMarginFactor() {
    return npshMarginFactor;
  }

  public void setNpshMarginFactor(double npshMarginFactor) {
    this.npshMarginFactor = npshMarginFactor;
  }

  public double getHydraulicPowerMargin() {
    return hydraulicPowerMargin;
  }

  public void setHydraulicPowerMargin(double hydraulicPowerMargin) {
    this.hydraulicPowerMargin = hydraulicPowerMargin;
  }

  public double getPorLowFraction() {
    return porLowFraction;
  }

  public void setPorLowFraction(double porLowFraction) {
    this.porLowFraction = porLowFraction;
  }

  public double getPorHighFraction() {
    return porHighFraction;
  }

  public void setPorHighFraction(double porHighFraction) {
    this.porHighFraction = porHighFraction;
  }

  public double getAorLowFraction() {
    return aorLowFraction;
  }

  public void setAorLowFraction(double aorLowFraction) {
    this.aorLowFraction = aorLowFraction;
  }

  public double getAorHighFraction() {
    return aorHighFraction;
  }

  public void setAorHighFraction(double aorHighFraction) {
    this.aorHighFraction = aorHighFraction;
  }

  public double getMaxSuctionSpecificSpeed() {
    return maxSuctionSpecificSpeed;
  }

  public void setMaxSuctionSpecificSpeed(double maxSuctionSpecificSpeed) {
    this.maxSuctionSpecificSpeed = maxSuctionSpecificSpeed;
  }

  public double getHeadMarginFactor() {
    return headMarginFactor;
  }

  public void setHeadMarginFactor(double headMarginFactor) {
    this.headMarginFactor = headMarginFactor;
  }
}
