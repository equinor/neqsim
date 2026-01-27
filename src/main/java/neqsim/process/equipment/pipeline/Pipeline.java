/*
 * Pipeline.java
 *
 * Created on 14. mars 2001, 22:30
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Base class for pipeline simulation models.
 *
 * <p>
 * This class provides the foundation for all pipeline models in NeqSim. It
 * implements the
 * {@link PipeLineInterface} with default implementations that can be overridden
 * by specialized
 * subclasses.
 * </p>
 *
 * <h2>Subclasses</h2>
 * <ul>
 * <li>{@link AdiabaticPipe} - Single-phase adiabatic pipe</li>
 * <li>{@link AdiabaticTwoPhasePipe} - Two-phase adiabatic pipe</li>
 * <li>{@link PipeBeggsAndBrills} - Beggs and Brill multiphase correlation</li>
 * <li>{@link OnePhasePipeLine} - One-phase flow with compositional
 * tracking</li>
 * <li>{@link TwoPhasePipeLine} - Two-phase flow model</li>
 * <li>{@link TwoFluidPipe} - Two-fluid transient model</li>
 * <li>{@link WaterHammerPipe} - Water hammer transient model</li>
 * <li>{@link TubingPerformance} - Wellbore vertical lift performance</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class Pipeline extends TwoPortEquipment
    implements PipeLineInterface, neqsim.process.equipment.capacity.CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Pipeline.class);

  // ============ File output ============
  protected String fileName = "c:/test5.nc";

  // ============ Flow system (for advanced models) ============
  protected FlowSystemInterface pipe;
  protected SystemInterface system;

  // ============ Common geometry parameters ============
  /** Total pipe length in meters. */
  protected double length = 1000.0;
  /** Pipe inner diameter in meters. */
  protected double diameter = 0.1;
  /** Pipe wall roughness in meters. */
  protected double roughness = 1e-5;
  /** Pipe wall thickness in meters. */
  protected double wallThickness = 0.01;
  /** Elevation change from inlet to outlet in meters. */
  protected double elevation = 0.0;
  /** Inlet elevation above reference in meters. */
  protected double inletElevation = 0.0;
  /** Outlet elevation above reference in meters. */
  protected double outletElevation = 0.0;
  /** Pipe angle from horizontal in degrees. */
  protected double angle = 0.0;

  // ============ Heat transfer parameters ============
  /** Overall heat transfer coefficient in W/(m²·K). */
  protected double heatTransferCoefficient = 0.0;
  /** Constant surface temperature in Kelvin. */
  protected double surfaceTemperature = 288.15;
  /** Flag for adiabatic operation. */
  protected boolean adiabatic = true;
  /** Ambient temperature in Kelvin. */
  protected double ambientTemperature = 288.15;
  /** Inner (fluid-side) heat transfer coefficient in W/(m²·K). */
  protected double innerHeatTransferCoefficient = 1000.0;
  /** Outer (external) heat transfer coefficient in W/(m²·K). */
  protected double outerHeatTransferCoefficient = 10.0;

  // ============ Mechanical buildup parameters ============
  /** Pipe material. */
  protected String pipeMaterial = "carbon steel";
  /** Pipe schedule. */
  protected String pipeSchedule = "STD";
  /** Pipe wall thermal conductivity in W/(m·K). */
  protected double pipeWallConductivity = 45.0; // Carbon steel
  /** Insulation thickness in meters. */
  protected double insulationThickness = 0.0;
  /** Insulation thermal conductivity in W/(m·K). */
  protected double insulationConductivity = 0.04; // Typical PU foam
  /** Insulation type/material. */
  protected String insulationType = "none";
  /** Coating thickness in meters. */
  protected double coatingThickness = 0.0;
  /** Coating thermal conductivity in W/(m·K). */
  protected double coatingConductivity = 0.2; // Typical epoxy
  /** Burial depth in meters. */
  protected double burialDepth = 0.0;
  /** Soil thermal conductivity in W/(m·K). */
  protected double soilConductivity = 1.5;
  /** Flag for buried pipe. */
  protected boolean buried = false;

  // ============ Mechanical Design Parameters ============
  /** Design pressure in MPa. */
  protected double designPressure = 10.0;
  /** Design temperature in Celsius. */
  protected double designTemperature = 50.0;
  /** Material grade per API 5L. */
  protected String materialGrade = "X65";
  /** Design code (e.g., ASME_B31_8). */
  protected String designCode = PipeMechanicalDesignCalculator.ASME_B31_8;
  /** Location class per ASME B31.8. */
  protected int locationClass = 1;
  /** Corrosion allowance in meters. */
  protected double corrosionAllowance = 0.003;
  /** Mechanical design calculator. */
  protected transient PipeMechanicalDesignCalculator mechanicalDesignCalculator = null;

  // ============ Results ============
  /** Calculated pressure drop in bar. */
  protected double pressureDrop = 0.0;
  /** Flow regime string. */
  protected String flowRegime = "unknown";
  /** Liquid holdup fraction. */
  protected double liquidHoldup = 0.0;
  /** Reynolds number. */
  protected double reynoldsNumber = 0.0;
  /** Friction factor. */
  protected double frictionFactor = 0.0;
  /** Flow velocity in m/s. */
  protected double velocity = 0.0;

  // ============ Profile arrays ============
  /** Pressure profile along pipe. */
  protected double[] pressureProfile;
  /** Temperature profile along pipe. */
  protected double[] temperatureProfile;
  /** Liquid holdup profile along pipe. */
  protected double[] liquidHoldupProfile;

  // ============ Number of increments ============
  /** Number of computational increments. */
  protected int numberOfIncrements = 10;

  // ============ Legacy segmented geometry ============
  String flowPattern = "stratified";
  double[] times;
  boolean equilibriumHeatTransfer = true;
  boolean equilibriumMassTransfer = false;
  int numberOfLegs = 1;
  int numberOfNodesInLeg = 30;
  double[] legHeights = { 0, 0 };
  double[] legPositions = { 0.0, 1.0 };
  double[] pipeDiameters = { 0.1507588, 0.1507588 };
  double[] outerTemperature = { 278.0, 278.0 };
  double[] pipeWallRoughness = { 1e-5, 1e-5 };
  double[] outerHeatTransferCoeffs = { 1e-5, 1e-5 };
  double[] wallHeatTransferCoeffs = { 1e-5, 1e-5 };

  PipelineMechanicalDesign pipelineMechanicalDesign = null;

  /**
   * Constructor for Pipeline.
   *
   * @param name the equipment name
   */
  public Pipeline(String name) {
    super(name);
  }

  /**
   * Constructor for Pipeline with inlet stream.
   *
   * @param name     the equipment name
   * @param inStream the inlet stream
   */
  public Pipeline(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  // ============================================================================
  // GEOMETRY METHODS - Common to all pipeline models
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setLength(double length) {
    this.length = length;
  }

  /** {@inheritDoc} */
  @Override
  public double getLength() {
    return length;
  }

  /** {@inheritDoc} */
  @Override
  public void setDiameter(double diameter) {
    this.diameter = diameter;
  }

  /** {@inheritDoc} */
  @Override
  public double getDiameter() {
    return diameter;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double roughness) {
    this.roughness = roughness;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallRoughness() {
    return roughness;
  }

  /** {@inheritDoc} */
  @Override
  public void setElevation(double elevation) {
    this.elevation = elevation;
  }

  /** {@inheritDoc} */
  @Override
  public double getElevation() {
    return elevation;
  }

  /** {@inheritDoc} */
  @Override
  public void setInletElevation(double inletElevation) {
    this.inletElevation = inletElevation;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletElevation() {
    return inletElevation;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletElevation(double outletElevation) {
    this.outletElevation = outletElevation;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletElevation() {
    return outletElevation;
  }

  /** {@inheritDoc} */
  @Override
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /** {@inheritDoc} */
  @Override
  public double getAngle() {
    return angle;
  }

  /** {@inheritDoc} */
  @Override
  public void setWallThickness(double thickness) {
    this.wallThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getWallThickness() {
    return wallThickness;
  }

  // ============================================================================
  // SEGMENTED GEOMETRY - For multi-leg pipeline models
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setNumberOfLegs(int number) {
    this.numberOfLegs = number;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfLegs() {
    return numberOfLegs;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfNodesInLeg(int number) {
    this.numberOfNodesInLeg = number;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfIncrements(int numberOfIncrements) {
    this.numberOfIncrements = numberOfIncrements;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfIncrements() {
    return numberOfIncrements;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeightProfile(double[] heights) {
    if (heights.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of heights specified. Expected {}, got {}", numberOfLegs + 1,
          heights.length);
      return;
    }
    legHeights = new double[heights.length];
    System.arraycopy(heights, 0, legHeights, 0, legHeights.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setLegPositions(double[] positions) {
    if (positions.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of leg positions specified. Expected {}, got {}", numberOfLegs + 1,
          positions.length);
      return;
    }
    legPositions = new double[positions.length];
    System.arraycopy(positions, 0, legPositions, 0, legPositions.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeDiameters(double[] diameters) {
    if (diameters.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of diameters specified. Expected {}, got {}", numberOfLegs + 1,
          diameters.length);
      return;
    }
    pipeDiameters = new double[diameters.length];
    System.arraycopy(diameters, 0, pipeDiameters, 0, pipeDiameters.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double[] rough) {
    if (rough.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of roughness points specified. Expected {}, got {}",
          numberOfLegs + 1, rough.length);
      return;
    }
    pipeWallRoughness = new double[rough.length];
    System.arraycopy(rough, 0, pipeWallRoughness, 0, pipeWallRoughness.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterTemperatures(double[] outerTemp) {
    if (outerTemp.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of outer temperatures specified. Expected {}, got {}",
          numberOfLegs + 1, outerTemp.length);
      return;
    }
    outerTemperature = new double[outerTemp.length];
    System.arraycopy(outerTemp, 0, outerTemperature, 0, outerTemperature.length);
  }

  // ============================================================================
  // CALCULATION MODE METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setOutPressure(double pressure) {
    // Default implementation - subclasses may override
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    // Default implementation - subclasses may override
  }

  // ============================================================================
  // RESULTS METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletPressure(String unit) {
    return outStream.getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletTemperature(String unit) {
    return outStream.getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getVelocity() {
    return velocity;
  }

  /** {@inheritDoc} */
  @Override
  public double getSuperficialVelocity(int phaseNumber) {
    return getSuperficialVelocity(phaseNumber, 0);
  }

  /**
   * Get superficial velocity for a phase at a specific node.
   *
   * @param phaseNum phase index (0=gas, 1=liquid)
   * @param node     node index
   * @return superficial velocity in m/s
   */
  public double getSuperficialVelocity(int phaseNum, int node) {
    try {
      double d = (pipeDiameters != null && node < pipeDiameters.length) ? pipeDiameters[node] : diameter;
      return outStream.getThermoSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * outStream.getThermoSystem().getPhase(phaseNum).getMolarMass()
          / outStream.getThermoSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          / (Math.PI * d * d / 4.0);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowRegime() {
    return flowRegime;
  }

  /** {@inheritDoc} */
  @Override
  public double getLiquidHoldup() {
    return liquidHoldup;
  }

  /** {@inheritDoc} */
  @Override
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double getFrictionFactor() {
    return frictionFactor;
  }

  // ============================================================================
  // PROFILE METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public double[] getPressureProfile() {
    return pressureProfile;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getTemperatureProfile() {
    return temperatureProfile;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getLiquidHoldupProfile() {
    return liquidHoldupProfile;
  }

  // ============================================================================
  // HEAT TRANSFER METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setHeatTransferCoefficient(double coefficient) {
    this.heatTransferCoefficient = coefficient;
    this.adiabatic = (coefficient <= 0);
  }

  /** {@inheritDoc} */
  @Override
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setConstantSurfaceTemperature(double temperature) {
    this.surfaceTemperature = temperature;
    this.adiabatic = false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAdiabatic() {
    return adiabatic;
  }

  /** {@inheritDoc} */
  @Override
  public void setAdiabatic(boolean adiabatic) {
    this.adiabatic = adiabatic;
  }

  // ============================================================================
  // MECHANICAL BUILDUP METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setPipeMaterial(String material) {
    this.pipeMaterial = material;
    // Set typical conductivity based on material
    if (material.toLowerCase().contains("carbon steel")) {
      this.pipeWallConductivity = 45.0;
    } else if (material.toLowerCase().contains("stainless")) {
      this.pipeWallConductivity = 16.0;
    } else if (material.toLowerCase().contains("duplex")) {
      this.pipeWallConductivity = 15.0;
    } else if (material.toLowerCase().contains("copper")) {
      this.pipeWallConductivity = 400.0;
    } else if (material.toLowerCase().contains("titanium")) {
      this.pipeWallConductivity = 22.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getPipeMaterial() {
    return pipeMaterial;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSchedule(String schedule) {
    this.pipeSchedule = schedule;
  }

  /** {@inheritDoc} */
  @Override
  public String getPipeSchedule() {
    return pipeSchedule;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationThickness(double thickness) {
    this.insulationThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationConductivity(double conductivity) {
    this.insulationConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getInsulationConductivity() {
    return insulationConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public void setInsulationType(String insulationType) {
    this.insulationType = insulationType;
    // Set typical conductivity based on type
    if (insulationType.toLowerCase().contains("polyurethane")
        || insulationType.toLowerCase().contains("pu")) {
      this.insulationConductivity = 0.025;
    } else if (insulationType.toLowerCase().contains("mineral wool")) {
      this.insulationConductivity = 0.04;
    } else if (insulationType.toLowerCase().contains("aerogel")) {
      this.insulationConductivity = 0.015;
    } else if (insulationType.toLowerCase().contains("polypropylene")
        || insulationType.toLowerCase().contains("pp")) {
      this.insulationConductivity = 0.22;
    } else if (insulationType.toLowerCase().contains("syntactic foam")) {
      this.insulationConductivity = 0.12;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getInsulationType() {
    return insulationType;
  }

  /** {@inheritDoc} */
  @Override
  public void setCoatingThickness(double thickness) {
    this.coatingThickness = thickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoatingThickness() {
    return coatingThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setCoatingConductivity(double conductivity) {
    this.coatingConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoatingConductivity() {
    return coatingConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallConductivity(double conductivity) {
    this.pipeWallConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallConductivity() {
    return pipeWallConductivity;
  }

  // ============================================================================
  // OUTER HEAT TRANSFER METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setOuterHeatTransferCoefficient(double coefficient) {
    this.outerHeatTransferCoefficient = coefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getOuterHeatTransferCoefficient() {
    return outerHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setInnerHeatTransferCoefficient(double coefficient) {
    this.innerHeatTransferCoefficient = coefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getInnerHeatTransferCoefficient() {
    return innerHeatTransferCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterHeatTransferCoefficients(double[] coefficients) {
    setPipeOuterHeatTransferCoefficients(coefficients);
  }

  /** {@inheritDoc} */
  @Override
  public void setWallHeatTransferCoefficients(double[] coefficients) {
    setPipeWallHeatTransferCoefficients(coefficients);
  }

  /** {@inheritDoc} */
  @Override
  public void setAmbientTemperatures(double[] temperatures) {
    setOuterTemperatures(temperatures);
  }

  /** {@inheritDoc} */
  @Override
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
    this.surfaceTemperature = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateOverallHeatTransferCoefficient() {
    // Calculate overall U-value using resistance analogy for cylindrical geometry
    // U_overall = 1 / (R_inner + R_wall + R_coating + R_insulation + R_outer)

    double ri = diameter / 2.0; // Inner radius
    double ro = ri + wallThickness; // Outer radius of pipe wall
    double rc = ro + coatingThickness; // Outer radius of coating
    double rins = rc + insulationThickness; // Outer radius of insulation

    // Thermal resistances per unit length (multiply by ri to get per inner area)
    double R_inner = 1.0 / innerHeatTransferCoefficient;

    // Wall conduction resistance: ln(ro/ri) / (2*pi*k) per unit length
    // Per inner area: ri * ln(ro/ri) / k
    double R_wall = (wallThickness > 0 && pipeWallConductivity > 0)
        ? ri * Math.log(ro / ri) / pipeWallConductivity
        : 0.0;

    // Coating conduction resistance
    double R_coating = (coatingThickness > 0 && coatingConductivity > 0)
        ? ri * Math.log(rc / ro) / coatingConductivity
        : 0.0;

    // Insulation conduction resistance
    double R_insulation = (insulationThickness > 0 && insulationConductivity > 0)
        ? ri * Math.log(rins / rc) / insulationConductivity
        : 0.0;

    // Outer convection resistance (based on outer diameter)
    double R_outer = (outerHeatTransferCoefficient > 0) ? ri / (rins * outerHeatTransferCoefficient) : 0.0;

    // If buried, add soil resistance
    double R_soil = 0.0;
    if (buried && burialDepth > 0 && soilConductivity > 0) {
      // Approximate formula for buried pipe: R = ln(2*H/r_outer) / (2*pi*k_soil)
      // Per inner area: ri * ln(2*H/r_outer) / k_soil
      R_soil = ri * Math.log(2.0 * burialDepth / rins) / soilConductivity;
    }

    double R_total = R_inner + R_wall + R_coating + R_insulation + R_outer + R_soil;

    return (R_total > 0) ? 1.0 / R_total : 0.0;
  }

  // ============================================================================
  // BURIAL CONDITIONS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setBurialDepth(double depth) {
    this.burialDepth = depth;
    if (depth > 0) {
      this.buried = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getBurialDepth() {
    return burialDepth;
  }

  /** {@inheritDoc} */
  @Override
  public void setSoilConductivity(double conductivity) {
    this.soilConductivity = conductivity;
  }

  /** {@inheritDoc} */
  @Override
  public double getSoilConductivity() {
    return soilConductivity;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isBuried() {
    return buried;
  }

  /** {@inheritDoc} */
  @Override
  public void setBuried(boolean buried) {
    this.buried = buried;
  }

  // ============================================================================
  // ADVANCED CONFIGURATION
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void setOutputFileName(String name) {
    this.fileName = name;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    this.flowPattern = flowPattern;
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return pipe;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSpecification(double nominalDiameter, String specification) {
    // Default implementation - subclasses may load from database
    this.diameter = nominalDiameter / 1000.0; // Assume mm input
  }

  // ============================================================================
  // MECHANICAL DESIGN
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    pipelineMechanicalDesign = new PipelineMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public PipelineMechanicalDesign getMechanicalDesign() {
    return pipelineMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public PipeMechanicalDesignCalculator getMechanicalDesignCalculator() {
    if (mechanicalDesignCalculator == null) {
      mechanicalDesignCalculator = new PipeMechanicalDesignCalculator();
      // Initialize from current pipe settings
      mechanicalDesignCalculator.setOuterDiameter(diameter + 2 * wallThickness);
      mechanicalDesignCalculator.setDesignPressure(designPressure);
      mechanicalDesignCalculator.setDesignTemperature(designTemperature);
      mechanicalDesignCalculator.setMaterialGrade(materialGrade);
      mechanicalDesignCalculator.setDesignCode(designCode);
      mechanicalDesignCalculator.setLocationClass(locationClass);
      mechanicalDesignCalculator.setCorrosionAllowance(corrosionAllowance);
      if (wallThickness > 0) {
        mechanicalDesignCalculator.setNominalWallThickness(wallThickness);
      }
    }
    return mechanicalDesignCalculator;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignPressure(double pressure) {
    this.designPressure = pressure;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignPressure(pressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDesignPressure() {
    return designPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignPressure(double pressure, String unit) {
    String lowerUnit = unit.toLowerCase().trim();
    if ("mpa".equals(lowerUnit)) {
      this.designPressure = pressure;
    } else if ("bar".equals(lowerUnit) || "bara".equals(lowerUnit)) {
      this.designPressure = pressure * 0.1;
    } else if ("psi".equals(lowerUnit) || "psig".equals(lowerUnit)) {
      this.designPressure = pressure * 0.00689476;
    } else {
      this.designPressure = pressure;
    }
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignPressure(this.designPressure);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignTemperature(double temperature) {
    this.designTemperature = temperature;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignTemperature(temperature);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDesignTemperature() {
    return designTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setMaterialGrade(grade);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getMaterialGrade() {
    return materialGrade;
  }

  /** {@inheritDoc} */
  @Override
  public void setDesignCode(String code) {
    this.designCode = code;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setDesignCode(code);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getDesignCode() {
    return designCode;
  }

  /** {@inheritDoc} */
  @Override
  public void setLocationClass(int locationClass) {
    this.locationClass = Math.max(1, Math.min(4, locationClass));
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setLocationClass(this.locationClass);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getLocationClass() {
    return locationClass;
  }

  /** {@inheritDoc} */
  @Override
  public void setCorrosionAllowance(double allowance) {
    this.corrosionAllowance = allowance;
    if (mechanicalDesignCalculator != null) {
      mechanicalDesignCalculator.setCorrosionAllowance(allowance);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMinimumWallThickness() {
    return getMechanicalDesignCalculator().calculateMinimumWallThickness();
  }

  /** {@inheritDoc} */
  @Override
  public double calculateMAOP() {
    return getMechanicalDesignCalculator().calculateMAOP();
  }

  /** {@inheritDoc} */
  @Override
  public double getMAOP(String unit) {
    return getMechanicalDesignCalculator().getMaop(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateTestPressure() {
    return getMechanicalDesignCalculator().calculateTestPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double calculateHoopStress() {
    return getMechanicalDesignCalculator().calculateHoopStress(designPressure);
  }

  /** {@inheritDoc} */
  @Override
  public double calculateVonMisesStress(double deltaT) {
    return getMechanicalDesignCalculator().calculateVonMisesStress(designPressure, deltaT, buried);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMechanicalDesignSafe() {
    return getMechanicalDesignCalculator().isDesignSafe();
  }

  /** {@inheritDoc} */
  @Override
  public String generateMechanicalDesignReport() {
    return getMechanicalDesignCalculator().generateDesignReport();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return getOutStream().getFlowRate("m3/hr");
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return getMechanicalDesign().maxDesignVolumeFlow;
  }

  // ============================================================================
  // HEAT/MASS TRANSFER SETTINGS
  // ============================================================================

  /**
   * Set pipe outer heat transfer coefficients for each leg.
   *
   * @param heatCoefs array of heat transfer coefficients
   */
  public void setPipeOuterHeatTransferCoefficients(double[] heatCoefs) {
    if (heatCoefs.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of heat coefficients specified. Expected {}, got {}",
          numberOfLegs + 1, heatCoefs.length);
      return;
    }
    outerHeatTransferCoeffs = new double[heatCoefs.length];
    System.arraycopy(heatCoefs, 0, outerHeatTransferCoeffs, 0, outerHeatTransferCoeffs.length);
  }

  /**
   * Set pipe wall heat transfer coefficients for each leg.
   *
   * @param heatCoefs array of heat transfer coefficients
   */
  public void setPipeWallHeatTransferCoefficients(double[] heatCoefs) {
    if (heatCoefs.length != this.numberOfLegs + 1) {
      logger.warn("Wrong number of heat coefficients specified. Expected {}, got {}",
          numberOfLegs + 1, heatCoefs.length);
      return;
    }
    wallHeatTransferCoeffs = new double[heatCoefs.length];
    System.arraycopy(heatCoefs, 0, wallHeatTransferCoeffs, 0, wallHeatTransferCoeffs.length);
  }

  /**
   * Set equilibrium mass transfer flag.
   *
   * @param equilibriumMassTransfer true to enable equilibrium mass transfer
   */
  public void setEquilibriumMassTransfer(boolean equilibriumMassTransfer) {
    this.equilibriumMassTransfer = equilibriumMassTransfer;
  }

  /**
   * Set equilibrium heat transfer flag.
   *
   * @param equilibriumHeatTransfer true to enable equilibrium heat transfer
   */
  public void setEquilibriumHeatTransfer(boolean equilibriumHeatTransfer) {
    this.equilibriumHeatTransfer = equilibriumHeatTransfer;
  }

  // ============================================================================
  // RUN METHODS
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    GeometryDefinitionInterface[] pipeGemometry = new PipeData[numberOfLegs + 1];
    for (int i = 0; i < pipeDiameters.length; i++) {
      pipeGemometry[i] = new PipeData(pipeDiameters[i], pipeWallRoughness[i]);
    }
    pipe.setInletThermoSystem(system);
    pipe.setNumberOfLegs(numberOfLegs);
    pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
    pipe.setEquipmentGeometry(pipeGemometry);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegHeights(legHeights);
    pipe.setLegOuterHeatTransferCoefficients(outerHeatTransferCoeffs);
    pipe.setLegWallHeatTransferCoefficients(wallHeatTransferCoeffs);
    pipe.setLegPositions(legPositions);
    pipe.setInitialFlowPattern(flowPattern);
    pipe.createSystem();
    pipe.setEquilibriumMassTransfer(equilibriumMassTransfer);
    pipe.setEquilibriumHeatTransfer(equilibriumHeatTransfer);
    pipe.init();
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    pipe.solveTransient(2, id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }

  // ============================================================================
  // TIME SERIES (for transient simulations)
  // ============================================================================

  /**
   * Get the time array for transient simulations.
   *
   * @return array of times in seconds
   */
  public double[] getTimes() {
    return this.times;
  }

  /**
   * Set time series for transient simulation.
   *
   * @param times              array of times
   * @param systems            array of thermodynamic systems at each time
   * @param timestepininterval number of time steps in each interval
   */
  public void setTimeSeries(double[] times, SystemInterface[] systems, int timestepininterval) {
    this.times = times;
    pipe.getTimeSeries().setTimes(times);
    pipe.getTimeSeries().setInletThermoSystems(systems);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(timestepininterval);
  }

  // ============================================================================
  // THERMODYNAMIC PROPERTIES
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
  }

  // ============================================================================
  // JSON SERIALIZATION
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.PipelineResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg
        .getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.PipelineResponse res = new neqsim.process.util.monitor.PipelineResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(res);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /** Storage for capacity constraints. */
  private final java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> capacityConstraints = new java.util.LinkedHashMap<>();

  /**
   * Initializes default capacity constraints for the pipeline.
   *
   * <p>
   * NOTE: All constraints are disabled by default for backwards compatibility.
   * Enable specific
   * constraints when pipeline capacity analysis is needed (e.g., after sizing).
   * </p>
   */
  protected void initializeCapacityConstraints() {
    // Velocity constraint (SOFT limit - erosional is a guideline) - disabled by
    // default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("velocity",
        "m/s", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
        .setDesignValue(getMechanicalDesign().maxDesignVelocity > 0
            ? getMechanicalDesign().maxDesignVelocity
            : 20.0)
        .setWarningThreshold(0.9).setValueSupplier(() -> getVelocity())
        .setEnabled(false));

    // Volume flow constraint (DESIGN limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("volumeFlow",
        "m3/hr", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
        .setDesignValue(getMechanicalDesign().maxDesignVolumeFlow).setWarningThreshold(0.9)
        .setValueSupplier(() -> outStream != null ? outStream.getFlowRate("m3/hr") : 0.0)
        .setEnabled(false));

    // Pressure drop constraint (DESIGN limit) - disabled by default
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("pressureDrop",
        "bara", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
        .setDesignValue(getMechanicalDesign().maxDesignPressureDrop).setWarningThreshold(0.9)
        .setValueSupplier(() -> getPressureDrop())
        .setEnabled(false));
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    return java.util.Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
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
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
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
    for (neqsim.process.equipment.capacity.CapacityConstraint c : getCapacityConstraints()
        .values()) {
      if (!c.isEnabled()) {
        continue;
      }
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
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
}
