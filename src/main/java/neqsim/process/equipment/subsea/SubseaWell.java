package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.subsea.WellMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Subsea well equipment class.
 *
 * <p>
 * Represents a subsea well including wellbore, casing, tubing and completion. The well uses an
 * internal {@link AdiabaticTwoPhasePipe} to model tubing flow performance (VLP), and provides well
 * design properties for mechanical design and cost estimation.
 * </p>
 *
 * <h2>Well Types</h2>
 * <ul>
 * <li><b>OIL_PRODUCER</b> - Oil production well</li>
 * <li><b>GAS_PRODUCER</b> - Gas production well</li>
 * <li><b>WATER_INJECTOR</b> - Water injection well</li>
 * <li><b>GAS_INJECTOR</b> - Gas injection well</li>
 * <li><b>OBSERVATION</b> - Observation/monitoring well</li>
 * </ul>
 *
 * <h2>Completion Types</h2>
 * <ul>
 * <li><b>CASED_PERFORATED</b> - Standard cased and perforated completion</li>
 * <li><b>OPEN_HOLE</b> - Open hole completion with screens or gravel pack</li>
 * <li><b>GRAVEL_PACK</b> - Gravel pack completion for sand control</li>
 * <li><b>ICD</b> - Inflow control device completion</li>
 * <li><b>AICD</b> - Autonomous inflow control device (water/gas shut-off)</li>
 * <li><b>MULTI_ZONE</b> - Multi-zone intelligent completion</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>NORSOK D-010 - Well Integrity in Drilling and Well Operations</li>
 * <li>API 5CT - Casing and Tubing</li>
 * <li>ISO 11960 - Steel Pipes for Use as Casing or Tubing</li>
 * <li>API RP 90 - Annular Casing Pressure Management</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * SubseaWell well = new SubseaWell("Producer-1", reservoirStream);
 * well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
 * well.setMeasuredDepth(3800.0);
 * well.setTrueVerticalDepth(3200.0);
 * well.setWaterDepth(350.0);
 * well.setCompletionType(SubseaWell.CompletionType.CASED_PERFORATED);
 * well.setTubingDiameter(0.1397); // 5.5 inch
 * well.getPipeline().setDiameter(0.1397);
 * well.getPipeline().setLength(3800.0);
 * well.run();
 *
 * // Mechanical design and cost
 * well.initMechanicalDesign();
 * WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
 * design.calcDesign();
 * design.calculateCostEstimate();
 * String report = design.toJson();
 * }</pre>
 *
 * @author asmund
 * @version 2.0
 * @see WellMechanicalDesign
 * @see SubseaTree
 */
public class SubseaWell extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;

  /**
   * Well type classification.
   */
  public enum WellType {
    /** Oil production well. */
    OIL_PRODUCER("Oil Producer"),
    /** Gas production well. */
    GAS_PRODUCER("Gas Producer"),
    /** Water injection well. */
    WATER_INJECTOR("Water Injector"),
    /** Gas injection well. */
    GAS_INJECTOR("Gas Injector"),
    /** Observation/monitoring well. */
    OBSERVATION("Observation");

    private final String displayName;

    WellType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Completion type.
   */
  public enum CompletionType {
    /** Cased and perforated. */
    CASED_PERFORATED("Cased & Perforated"),
    /** Open hole with screens. */
    OPEN_HOLE("Open Hole"),
    /** Gravel pack for sand control. */
    GRAVEL_PACK("Gravel Pack"),
    /** Inflow control devices. */
    ICD("ICD"),
    /** Autonomous inflow control devices. */
    AICD("AICD"),
    /** Multi-zone intelligent completion. */
    MULTI_ZONE("Multi-Zone");

    private final String displayName;

    CompletionType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * Rig type used for drilling.
   */
  public enum RigType {
    /** Semi-submersible drilling rig. */
    SEMI_SUBMERSIBLE("Semi-Submersible"),
    /** Drillship. */
    DRILLSHIP("Drillship"),
    /** Jack-up rig. */
    JACK_UP("Jack-Up"),
    /** Platform rig (from fixed facility). */
    PLATFORM_RIG("Platform Rig");

    private final String displayName;

    RigType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  // ============ Well Geometry ============
  /** Height (elevation change) in meters. */
  public double height = 1000.0;

  /** Well length (measured depth of tubing) in meters. */
  public double length = 1200.0;

  /** Measured depth (MD) of the well in meters. */
  private double measuredDepth = 3800.0;

  /** True vertical depth (TVD) in meters. */
  private double trueVerticalDepth = 3200.0;

  /** Water depth at wellhead in meters. */
  private double waterDepth = 350.0;

  /** Kick-off point depth in meters MD. */
  private double kickOffPoint = 1500.0;

  /** Maximum inclination angle in degrees. */
  private double maxInclination = 45.0;

  // ============ Well Classification ============
  /** Well type. */
  private WellType wellType = WellType.OIL_PRODUCER;

  /** Completion type. */
  private CompletionType completionType = CompletionType.CASED_PERFORATED;

  /** Rig type. */
  private RigType rigType = RigType.SEMI_SUBMERSIBLE;

  // ============ Casing Program ============
  /** Conductor casing OD in inches. */
  private double conductorOD = 30.0;

  /** Conductor casing depth in meters MD. */
  private double conductorDepth = 100.0;

  /** Surface casing OD in inches. */
  private double surfaceCasingOD = 20.0;

  /** Surface casing depth in meters MD. */
  private double surfaceCasingDepth = 800.0;

  /** Intermediate casing OD in inches. */
  private double intermediateCasingOD = 13.375;

  /** Intermediate casing depth in meters MD. */
  private double intermediateCasingDepth = 2500.0;

  /** Production casing OD in inches. */
  private double productionCasingOD = 9.625;

  /** Production casing depth in meters MD. */
  private double productionCasingDepth = 3800.0;

  /** Production liner OD in inches (0 = no liner). */
  private double productionLinerOD = 7.0;

  /** Production liner depth in meters MD (0 = no liner). */
  private double productionLinerDepth = 0.0;

  // ============ Tubing ============
  /** Tubing OD in inches. */
  private double tubingOD = 5.5;

  /** Tubing weight in lb/ft. */
  private double tubingWeight = 23.0;

  /** Tubing grade per API 5CT. */
  private String tubingGrade = "L80";

  // ============ Drilling ============
  /** Estimated drilling duration in days. */
  private double drillingDays = 55.0;

  /** Estimated completion duration in days. */
  private double completionDays = 20.0;

  /** Rig day rate in USD. */
  private double rigDayRate = 350000.0;

  // ============ Well Barriers ============
  /** Whether well has DHSV (downhole safety valve / SSSV). */
  private boolean hasDHSV = true;

  /** Number of well barrier elements in primary barrier. */
  private int primaryBarrierElements = 4;

  /** Number of well barrier elements in secondary barrier. */
  private int secondaryBarrierElements = 3;

  // ============ Design Conditions ============
  /** Maximum expected wellhead pressure in bara. */
  private double maxWellheadPressure = 345.0;

  /** Maximum expected bottomhole temperature in Celsius. */
  private double maxBottomholeTemperature = 120.0;

  /** Reservoir pressure in bara. */
  private double reservoirPressure = 400.0;

  /** Reservoir temperature in Celsius. */
  private double reservoirTemperature = 100.0;

  /** Internal pipeline for tubing flow model. */
  AdiabaticTwoPhasePipe pipeline;

  /** Mechanical design instance. */
  private WellMechanicalDesign mechanicalDesign;

  /**
   * Constructor for SubseaWell.
   *
   * @param name Name of well
   * @param instream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SubseaWell(String name, StreamInterface instream) {
    super(name);
    setInletStream(instream);
    pipeline = new AdiabaticTwoPhasePipe("pipeline", instream);
  }

  /**
   * Getter for the field <code>pipeline</code>.
   *
   * @return a {@link neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe} object
   */
  public AdiabaticTwoPhasePipe getPipeline() {
    return pipeline;
  }

  /**
   * Initialize mechanical design for this well.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new WellMechanicalDesign(this);
  }

  /**
   * Get mechanical design.
   *
   * @return the mechanical design instance, or null if not initialized
   */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    pipeline.run(id);
    getOutletStream().setFluid(pipeline.getOutletStream().getFluid());

    /*
     * System.out.println("stary P " ); SystemInterface fluidIn = (inStream.getFluid()).clone();
     * fluidIn.initProperties();
     *
     * double density = fluidIn.getDensity("kg/m3");
     *
     * double deltaP = density*height*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1.0e5;
     *
     * System.out.println("density " +density + " delta P " + deltaP);
     *
     * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
     *
     * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn); ops.TPflash();
     *
     * getOutStream().setFluid(fluidIn);
     */
    setCalculationIdentifier(id);
  }

  // ============ Getters and Setters ============

  /**
   * Get well type.
   *
   * @return well type
   */
  public WellType getWellType() {
    return wellType;
  }

  /**
   * Set well type.
   *
   * @param wellType well type
   */
  public void setWellType(WellType wellType) {
    this.wellType = wellType;
  }

  /**
   * Get completion type.
   *
   * @return completion type
   */
  public CompletionType getCompletionType() {
    return completionType;
  }

  /**
   * Set completion type.
   *
   * @param completionType completion type
   */
  public void setCompletionType(CompletionType completionType) {
    this.completionType = completionType;
  }

  /**
   * Get rig type.
   *
   * @return rig type
   */
  public RigType getRigType() {
    return rigType;
  }

  /**
   * Set rig type.
   *
   * @param rigType rig type
   */
  public void setRigType(RigType rigType) {
    this.rigType = rigType;
  }

  /**
   * Get measured depth in meters.
   *
   * @return measured depth
   */
  public double getMeasuredDepth() {
    return measuredDepth;
  }

  /**
   * Set measured depth in meters.
   *
   * @param measuredDepth measured depth
   */
  public void setMeasuredDepth(double measuredDepth) {
    this.measuredDepth = measuredDepth;
  }

  /**
   * Get true vertical depth in meters.
   *
   * @return true vertical depth
   */
  public double getTrueVerticalDepth() {
    return trueVerticalDepth;
  }

  /**
   * Set true vertical depth in meters.
   *
   * @param trueVerticalDepth true vertical depth
   */
  public void setTrueVerticalDepth(double trueVerticalDepth) {
    this.trueVerticalDepth = trueVerticalDepth;
  }

  /**
   * Get water depth in meters.
   *
   * @return water depth
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth in meters.
   *
   * @param waterDepth water depth
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Get kick-off point in meters MD.
   *
   * @return kick-off point depth
   */
  public double getKickOffPoint() {
    return kickOffPoint;
  }

  /**
   * Set kick-off point in meters MD.
   *
   * @param kickOffPoint kick-off point depth
   */
  public void setKickOffPoint(double kickOffPoint) {
    this.kickOffPoint = kickOffPoint;
  }

  /**
   * Get maximum inclination angle in degrees.
   *
   * @return max inclination
   */
  public double getMaxInclination() {
    return maxInclination;
  }

  /**
   * Set maximum inclination angle in degrees.
   *
   * @param maxInclination max inclination
   */
  public void setMaxInclination(double maxInclination) {
    this.maxInclination = maxInclination;
  }

  /**
   * Get conductor casing OD in inches.
   *
   * @return conductor OD
   */
  public double getConductorOD() {
    return conductorOD;
  }

  /**
   * Set conductor casing OD in inches.
   *
   * @param conductorOD conductor OD
   */
  public void setConductorOD(double conductorOD) {
    this.conductorOD = conductorOD;
  }

  /**
   * Get conductor casing depth in meters MD.
   *
   * @return conductor depth
   */
  public double getConductorDepth() {
    return conductorDepth;
  }

  /**
   * Set conductor casing depth in meters MD.
   *
   * @param conductorDepth conductor depth
   */
  public void setConductorDepth(double conductorDepth) {
    this.conductorDepth = conductorDepth;
  }

  /**
   * Get surface casing OD in inches.
   *
   * @return surface casing OD
   */
  public double getSurfaceCasingOD() {
    return surfaceCasingOD;
  }

  /**
   * Set surface casing OD in inches.
   *
   * @param surfaceCasingOD surface casing OD
   */
  public void setSurfaceCasingOD(double surfaceCasingOD) {
    this.surfaceCasingOD = surfaceCasingOD;
  }

  /**
   * Get surface casing depth in meters MD.
   *
   * @return surface casing depth
   */
  public double getSurfaceCasingDepth() {
    return surfaceCasingDepth;
  }

  /**
   * Set surface casing depth in meters MD.
   *
   * @param surfaceCasingDepth surface casing depth
   */
  public void setSurfaceCasingDepth(double surfaceCasingDepth) {
    this.surfaceCasingDepth = surfaceCasingDepth;
  }

  /**
   * Get intermediate casing OD in inches.
   *
   * @return intermediate casing OD
   */
  public double getIntermediateCasingOD() {
    return intermediateCasingOD;
  }

  /**
   * Set intermediate casing OD in inches.
   *
   * @param intermediateCasingOD intermediate casing OD
   */
  public void setIntermediateCasingOD(double intermediateCasingOD) {
    this.intermediateCasingOD = intermediateCasingOD;
  }

  /**
   * Get intermediate casing depth in meters MD.
   *
   * @return intermediate casing depth
   */
  public double getIntermediateCasingDepth() {
    return intermediateCasingDepth;
  }

  /**
   * Set intermediate casing depth in meters MD.
   *
   * @param intermediateCasingDepth intermediate casing depth
   */
  public void setIntermediateCasingDepth(double intermediateCasingDepth) {
    this.intermediateCasingDepth = intermediateCasingDepth;
  }

  /**
   * Get production casing OD in inches.
   *
   * @return production casing OD
   */
  public double getProductionCasingOD() {
    return productionCasingOD;
  }

  /**
   * Set production casing OD in inches.
   *
   * @param productionCasingOD production casing OD
   */
  public void setProductionCasingOD(double productionCasingOD) {
    this.productionCasingOD = productionCasingOD;
  }

  /**
   * Get production casing depth in meters MD.
   *
   * @return production casing depth
   */
  public double getProductionCasingDepth() {
    return productionCasingDepth;
  }

  /**
   * Set production casing depth in meters MD.
   *
   * @param productionCasingDepth production casing depth
   */
  public void setProductionCasingDepth(double productionCasingDepth) {
    this.productionCasingDepth = productionCasingDepth;
  }

  /**
   * Get production liner OD in inches (0 = no liner).
   *
   * @return production liner OD
   */
  public double getProductionLinerOD() {
    return productionLinerOD;
  }

  /**
   * Set production liner OD in inches (0 = no liner).
   *
   * @param productionLinerOD production liner OD
   */
  public void setProductionLinerOD(double productionLinerOD) {
    this.productionLinerOD = productionLinerOD;
  }

  /**
   * Get production liner depth in meters MD.
   *
   * @return production liner depth
   */
  public double getProductionLinerDepth() {
    return productionLinerDepth;
  }

  /**
   * Set production liner depth in meters MD.
   *
   * @param productionLinerDepth production liner depth
   */
  public void setProductionLinerDepth(double productionLinerDepth) {
    this.productionLinerDepth = productionLinerDepth;
  }

  /**
   * Get tubing OD in inches.
   *
   * @return tubing OD
   */
  public double getTubingOD() {
    return tubingOD;
  }

  /**
   * Set tubing OD in inches.
   *
   * @param tubingOD tubing OD
   */
  public void setTubingOD(double tubingOD) {
    this.tubingOD = tubingOD;
  }

  /**
   * Set tubing diameter in meters (convenience method for pipeline configuration).
   *
   * @param diameterM tubing diameter in meters
   */
  public void setTubingDiameter(double diameterM) {
    this.tubingOD = diameterM / 0.0254; // Convert meters to inches
    pipeline.setDiameter(diameterM);
  }

  /**
   * Get tubing weight in lb/ft.
   *
   * @return tubing weight
   */
  public double getTubingWeight() {
    return tubingWeight;
  }

  /**
   * Set tubing weight in lb/ft.
   *
   * @param tubingWeight tubing weight
   */
  public void setTubingWeight(double tubingWeight) {
    this.tubingWeight = tubingWeight;
  }

  /**
   * Get tubing grade per API 5CT.
   *
   * @return tubing grade
   */
  public String getTubingGrade() {
    return tubingGrade;
  }

  /**
   * Set tubing grade per API 5CT.
   *
   * @param tubingGrade tubing grade (e.g., "L80", "P110", "13Cr")
   */
  public void setTubingGrade(String tubingGrade) {
    this.tubingGrade = tubingGrade;
  }

  /**
   * Get estimated drilling duration in days.
   *
   * @return drilling days
   */
  public double getDrillingDays() {
    return drillingDays;
  }

  /**
   * Set estimated drilling duration in days.
   *
   * @param drillingDays drilling days
   */
  public void setDrillingDays(double drillingDays) {
    this.drillingDays = drillingDays;
  }

  /**
   * Get estimated completion duration in days.
   *
   * @return completion days
   */
  public double getCompletionDays() {
    return completionDays;
  }

  /**
   * Set estimated completion duration in days.
   *
   * @param completionDays completion days
   */
  public void setCompletionDays(double completionDays) {
    this.completionDays = completionDays;
  }

  /**
   * Get rig day rate in USD.
   *
   * @return rig day rate
   */
  public double getRigDayRate() {
    return rigDayRate;
  }

  /**
   * Set rig day rate in USD.
   *
   * @param rigDayRate rig day rate
   */
  public void setRigDayRate(double rigDayRate) {
    this.rigDayRate = rigDayRate;
  }

  /**
   * Check if well has downhole safety valve.
   *
   * @return true if DHSV installed
   */
  public boolean hasDHSV() {
    return hasDHSV;
  }

  /**
   * Set whether well has DHSV.
   *
   * @param hasDHSV true if DHSV installed
   */
  public void setHasDHSV(boolean hasDHSV) {
    this.hasDHSV = hasDHSV;
  }

  /**
   * Get number of primary barrier elements.
   *
   * @return primary barrier element count
   */
  public int getPrimaryBarrierElements() {
    return primaryBarrierElements;
  }

  /**
   * Set number of primary barrier elements.
   *
   * @param count primary barrier element count
   */
  public void setPrimaryBarrierElements(int count) {
    this.primaryBarrierElements = count;
  }

  /**
   * Get number of secondary barrier elements.
   *
   * @return secondary barrier element count
   */
  public int getSecondaryBarrierElements() {
    return secondaryBarrierElements;
  }

  /**
   * Set number of secondary barrier elements.
   *
   * @param count secondary barrier element count
   */
  public void setSecondaryBarrierElements(int count) {
    this.secondaryBarrierElements = count;
  }

  /**
   * Get maximum wellhead pressure in bara.
   *
   * @return max wellhead pressure
   */
  public double getMaxWellheadPressure() {
    return maxWellheadPressure;
  }

  /**
   * Set maximum wellhead pressure in bara.
   *
   * @param maxWellheadPressure max wellhead pressure
   */
  public void setMaxWellheadPressure(double maxWellheadPressure) {
    this.maxWellheadPressure = maxWellheadPressure;
  }

  /**
   * Get maximum bottomhole temperature in Celsius.
   *
   * @return max bottomhole temperature
   */
  public double getMaxBottomholeTemperature() {
    return maxBottomholeTemperature;
  }

  /**
   * Set maximum bottomhole temperature in Celsius.
   *
   * @param maxBottomholeTemperature max bottomhole temperature
   */
  public void setMaxBottomholeTemperature(double maxBottomholeTemperature) {
    this.maxBottomholeTemperature = maxBottomholeTemperature;
  }

  /**
   * Get reservoir pressure in bara.
   *
   * @return reservoir pressure
   */
  public double getReservoirPressure() {
    return reservoirPressure;
  }

  /**
   * Set reservoir pressure in bara.
   *
   * @param reservoirPressure reservoir pressure
   */
  public void setReservoirPressure(double reservoirPressure) {
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * Get reservoir temperature in Celsius.
   *
   * @return reservoir temperature
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  /**
   * Set reservoir temperature in Celsius.
   *
   * @param reservoirTemperature reservoir temperature
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Check if this is a production well.
   *
   * @return true if producer
   */
  public boolean isProducer() {
    return wellType == WellType.OIL_PRODUCER || wellType == WellType.GAS_PRODUCER;
  }

  /**
   * Check if this is an injection well.
   *
   * @return true if injector
   */
  public boolean isInjector() {
    return wellType == WellType.WATER_INJECTOR || wellType == WellType.GAS_INJECTOR;
  }

  /**
   * Get total number of casing strings (not counting liner).
   *
   * @return number of casing strings
   */
  public int getNumberOfCasingStrings() {
    int count = 3; // Conductor, surface, production
    if (intermediateCasingDepth > 0) {
      count++;
    }
    if (productionLinerDepth > 0) {
      count++;
    }
    return count;
  }

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 100.0), 250.00);
    testSystem.addComponent("nitrogen", 0.100);
    testSystem.addComponent("methane", 70.00);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("n-hexane", 0.1);
    testSystem.addComponent("n-heptane", 0.1);
    testSystem.addComponent("n-nonane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("nC12", 3.0);
    testSystem.addComponent("nC15", 13.0);
    testSystem.addComponent("nC20", 13.0);
    testSystem.addComponent("water", 11.0);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(testSystem, 5.0 * 1e7, 552.0 * 1e6, 10.0e6);
    StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
    producedOilStream.setFlowRate(3500.0 * 24.0 * 600.0, "kg/day");

    reservoirOps.run();

    System.out.println("water volume"
        + reservoirOps.getReservoirFluid().getPhase("aqueous").getVolume("m3") / 1.0e6);
    System.out
        .println("oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
    System.out
        .println("total produced  " + reservoirOps.getProductionTotal("MSm3 oe") + " MSm3 oe");

    SubseaWell well1 =
        new SubseaWell("oilproducer_1", reservoirOps.getOilProducer("oilproducer_1").getStream());
    well1.getPipeline().setDiameter(0.3);
    well1.getPipeline().setLength(5500.0);
    well1.getPipeline().setInletElevation(-1000.0);
    well1.getPipeline().setOutletElevation(-100.0);
    ThrottlingValve subseaChoke = new ThrottlingValve("subseaChoke", well1.getOutletStream());
    subseaChoke.setOutletPressure(90.0);
    subseaChoke.setAcceptNegativeDP(false);
    SimpleFlowLine flowLine = new SimpleFlowLine("flowLine", subseaChoke.getOutletStream());
    flowLine.getPipeline().setDiameter(0.4);
    flowLine.getPipeline().setLength(2000.0);
    flowLine.getPipeline().setInletElevation(-100.0);
    // flowLine.set
    ThrottlingValve topsideChoke = new ThrottlingValve("topsideChoke", flowLine.getOutletStream());
    topsideChoke.setOutletPressure(50.0, "bara");
    topsideChoke.setAcceptNegativeDP(false);

    Adjuster adjust = new Adjuster("adjust");
    adjust.setActivateWhenLess(true);
    adjust.setTargetVariable(flowLine.getOutletStream(), "pressure", 70.0, "bara");
    adjust.setAdjustedVariable(producedOilStream, "flow rate");

    ProcessSystem ops = new ProcessSystem();
    ops.add(well1);
    ops.add(subseaChoke);
    ops.add(flowLine);
    ops.add(topsideChoke);
    ops.add(adjust);

    ArrayList<double[]> res = new ArrayList<double[]>();
    // for(int i=0;i<152;i++) {
    // do {
    reservoirOps.runTransient(60 * 60 * 24 * 1);
    ops.run();
    res.add(new double[] {reservoirOps.getTime(), producedOilStream.getFluid().getFlowRate("kg/hr"),
        reservoirOps.getOilProductionTotal("MSm3 oe")});
    System.out.println("subsea choke DP " + subseaChoke.getDeltaPressure("bara"));
    System.out.println("topside  choke DP " + topsideChoke.getDeltaPressure("bara"));
    System.out.println("oil production " + producedOilStream.getFluid().getFlowRate("kg/hr"));
    // }
    // while(producedOilStream.getFluid().getFlowRate("kg/hr")>1.0e5);

    ProcessSystem GasOilProcess = ProcessSystem.open("c:/temp/offshorePro.neqsim");
    ((StreamInterface) GasOilProcess.getUnit("well stream"))
        .setThermoSystem(topsideChoke.getOutletStream().getFluid());
    ((StreamInterface) GasOilProcess.getUnit("well stream")).setPressure(70.0, "bara");
    ((StreamInterface) GasOilProcess.getUnit("well stream")).setTemperature(65.0, "C");
    GasOilProcess.run();

    System.out.println("power " + GasOilProcess.getPower("MW"));
    for (int i = 0; i < res.size(); i++) {
      System.out.println("time " + res.get(i)[0] + " oil production " + res.get(i)[1]
          + " total production MSm3 oe " + res.get(i)[2]);
    }
  }
}
