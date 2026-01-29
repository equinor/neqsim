package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.subsea.SubseaJumperMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Subsea Jumper equipment class.
 *
 * <p>
 * A subsea jumper is a short section of pipe used to connect subsea equipment such as:
 * </p>
 * <ul>
 * <li>Well to manifold connections</li>
 * <li>Manifold to PLET connections</li>
 * <li>PLET to flowline connections</li>
 * <li>Christmas tree to manifold connections</li>
 * </ul>
 *
 * <h2>Jumper Types</h2>
 * <ul>
 * <li><b>Rigid Jumper</b>: Steel pipe, typically M-shaped or inverted-U configuration</li>
 * <li><b>Flexible Jumper</b>: Unbonded flexible pipe for dynamic or static applications</li>
 * <li><b>Hybrid Jumper</b>: Combination of rigid and flexible sections</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17G - Subsea Production System Design</li>
 * <li>API RP 17B - Recommended Practice for Flexible Pipe</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>DNV-OS-F201 - Dynamic Risers</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create rigid jumper from well to manifold
 * SubseaJumper jumper = new SubseaJumper("Well-1 Jumper", wellStream);
 * jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
 * jumper.setNominalBoreInches(6.0);
 * jumper.setLength(50.0);
 * jumper.setDesignPressure(350.0);
 * jumper.run();
 *
 * // Get mechanical design
 * SubseaJumperMechanicalDesign design = jumper.getMechanicalDesign();
 * design.calcDesign();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaJumperMechanicalDesign
 * @see PLET
 * @see SubseaManifold
 */
public class SubseaJumper extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Jumper type configurations.
   */
  public enum JumperType {
    /** Rigid M-shaped jumper. */
    RIGID_M_SHAPE("Rigid M-Shape"),
    /** Rigid inverted-U jumper. */
    RIGID_INVERTED_U("Rigid Inverted-U"),
    /** Rigid Z-shaped jumper. */
    RIGID_Z_SHAPE("Rigid Z-Shape"),
    /** Rigid straight spool. */
    RIGID_STRAIGHT("Rigid Straight"),
    /** Flexible static jumper. */
    FLEXIBLE_STATIC("Flexible Static"),
    /** Flexible dynamic jumper. */
    FLEXIBLE_DYNAMIC("Flexible Dynamic"),
    /** Hybrid rigid-flexible jumper. */
    HYBRID("Hybrid");

    private final String displayName;

    JumperType(String displayName) {
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
   * Connection hub type.
   */
  public enum HubType {
    /** Vertical connection hub. */
    VERTICAL("Vertical"),
    /** Horizontal connection hub. */
    HORIZONTAL("Horizontal"),
    /** Clamp connection. */
    CLAMP("Clamp"),
    /** Collet connection. */
    COLLET("Collet");

    private final String displayName;

    HubType(String displayName) {
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

  // ============ Configuration ============
  /** Jumper type. */
  private JumperType jumperType = JumperType.RIGID_M_SHAPE;

  /** Water depth in meters. */
  private double waterDepth = 350.0;

  /** Design pressure in bara. */
  private double designPressure = 350.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 120.0;

  /** Nominal bore size in inches. */
  private double nominalBoreInches = 6.0;

  /** Outer diameter in inches. */
  private double outerDiameterInches = 6.625;

  /** Wall thickness in mm. */
  private double wallThicknessMm = 12.7;

  /** Jumper length in meters. */
  private double length = 50.0;

  /** Horizontal span in meters. */
  private double horizontalSpan = 40.0;

  /** Vertical rise in meters. */
  private double verticalRise = 5.0;

  // ============ End Connections ============
  /** Inlet hub type. */
  private HubType inletHubType = HubType.VERTICAL;

  /** Outlet hub type. */
  private HubType outletHubType = HubType.VERTICAL;

  /** Inlet hub size in inches. */
  private double inletHubSizeInches = 6.0;

  /** Outlet hub size in inches. */
  private double outletHubSizeInches = 6.0;

  // ============ Rigid Jumper Parameters ============
  /** Material grade (e.g., X65, 316L, 6Mo). */
  private String materialGrade = "X65";

  /** Number of bends. */
  private int numberOfBends = 4;

  /** Minimum bend radius in meters. */
  private double minimumBendRadius = 1.5;

  // ============ Flexible Jumper Parameters ============
  /** Flexible pipe structure type. */
  private String flexibleStructure = "Unbonded";

  /** Minimum bend radius for flexible in meters. */
  private double flexibleMinBendRadius = 2.0;

  /** Maximum curvature for flexible. */
  private double maxCurvature = 0.5;

  // ============ Installation ============
  /** Installation method. */
  private String installationMethod = "Vessel-based";

  /** Whether installed by ROV. */
  private boolean rovInstalled = true;

  /** Whether retrievable. */
  private boolean retrievable = true;

  // ============ Weight ============
  /** Dry weight in tonnes. */
  private double dryWeight = 5.0;

  /** Submerged weight in tonnes. */
  private double submergedWeight = 4.3;

  /** Content weight in tonnes. */
  private double contentWeight = 0.5;

  /** Mechanical design instance. */
  private SubseaJumperMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public SubseaJumper() {
    super("Subsea Jumper");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public SubseaJumper(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public SubseaJumper(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Create rigid M-shaped jumper.
   *
   * @param name jumper name
   * @param inStream inlet stream
   * @param length jumper length in meters
   * @return configured jumper
   */
  public static SubseaJumper createRigidMShape(String name, StreamInterface inStream,
      double length) {
    SubseaJumper jumper = new SubseaJumper(name, inStream);
    jumper.setJumperType(JumperType.RIGID_M_SHAPE);
    jumper.setLength(length);
    jumper.setNumberOfBends(4);
    return jumper;
  }

  /**
   * Create flexible static jumper.
   *
   * @param name jumper name
   * @param inStream inlet stream
   * @param length jumper length in meters
   * @return configured jumper
   */
  public static SubseaJumper createFlexibleStatic(String name, StreamInterface inStream,
      double length) {
    SubseaJumper jumper = new SubseaJumper(name, inStream);
    jumper.setJumperType(JumperType.FLEXIBLE_STATIC);
    jumper.setLength(length);
    jumper.setFlexibleStructure("Unbonded");
    return jumper;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      return;
    }

    // Clone inlet fluid
    SystemInterface outFluid = inStream.getFluid().clone();

    // Calculate pressure drop through jumper
    double pressureDrop = calculatePressureDrop();
    double outPressure = outFluid.getPressure() - pressureDrop;
    if (outPressure > 0) {
      outFluid.setPressure(outPressure);
    }

    // Set outlet
    outStream.setFluid(outFluid);
    setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop through jumper.
   *
   * @return pressure drop in bar
   */
  private double calculatePressureDrop() {
    // Simplified calculation - in reality would use detailed hydraulics
    double frictionDrop = 0.0;

    if (inStream != null && inStream.getFluid() != null) {
      SystemInterface fluid = inStream.getFluid();
      double density = fluid.getDensity("kg/m3");
      double viscosity = fluid.getViscosity("kg/msec");

      // Get flow rate
      double massFlow = inStream.getFlowRate("kg/sec");
      double innerDiameter = (nominalBoreInches * 0.0254); // Convert to meters
      double area = Math.PI * innerDiameter * innerDiameter / 4.0;
      double velocity = massFlow / (density * area);

      // Reynolds number
      double reynolds = density * velocity * innerDiameter / viscosity;

      // Friction factor (Blasius for turbulent flow)
      double frictionFactor;
      if (reynolds < 2300) {
        frictionFactor = 64 / reynolds;
      } else {
        frictionFactor = 0.316 / Math.pow(reynolds, 0.25);
      }

      // Darcy-Weisbach pressure drop
      frictionDrop =
          frictionFactor * (length / innerDiameter) * (density * velocity * velocity / 2) / 100000; // Convert
                                                                                                    // Pa
                                                                                                    // to
                                                                                                    // bar

      // Add losses for bends
      double bendLossCoeff = 0.3; // Per bend
      double bendDrop =
          numberOfBends * bendLossCoeff * (density * velocity * velocity / 2) / 100000;
      frictionDrop += bendDrop;

      // Add connection losses
      double connectionDrop = 0.05; // bar per connection
      frictionDrop += 2 * connectionDrop; // inlet and outlet
    }

    return Math.max(frictionDrop, 0.1); // Minimum 0.1 bar
  }

  /**
   * Check if jumper type is rigid.
   *
   * @return true if rigid jumper
   */
  public boolean isRigid() {
    return jumperType == JumperType.RIGID_M_SHAPE || jumperType == JumperType.RIGID_INVERTED_U
        || jumperType == JumperType.RIGID_Z_SHAPE || jumperType == JumperType.RIGID_STRAIGHT;
  }

  /**
   * Check if jumper type is flexible.
   *
   * @return true if flexible jumper
   */
  public boolean isFlexible() {
    return jumperType == JumperType.FLEXIBLE_STATIC || jumperType == JumperType.FLEXIBLE_DYNAMIC;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  @Override
  public SubseaJumperMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new SubseaJumperMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new SubseaJumperMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get jumper type.
   *
   * @return jumper type
   */
  public JumperType getJumperType() {
    return jumperType;
  }

  /**
   * Set jumper type.
   *
   * @param jumperType jumper type
   */
  public void setJumperType(JumperType jumperType) {
    this.jumperType = jumperType;
  }

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
  }

  /**
   * Get design pressure.
   *
   * @return design pressure in bara
   */
  public double getDesignPressure() {
    return designPressure;
  }

  /**
   * Set design pressure.
   *
   * @param designPressure design pressure in bara
   */
  public void setDesignPressure(double designPressure) {
    this.designPressure = designPressure;
  }

  /**
   * Get design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Set design temperature.
   *
   * @param designTemperature design temperature in Celsius
   */
  public void setDesignTemperature(double designTemperature) {
    this.designTemperature = designTemperature;
  }

  /**
   * Get nominal bore size.
   *
   * @return nominal bore in inches
   */
  public double getNominalBoreInches() {
    return nominalBoreInches;
  }

  /**
   * Set nominal bore size.
   *
   * @param nominalBoreInches nominal bore in inches
   */
  public void setNominalBoreInches(double nominalBoreInches) {
    this.nominalBoreInches = nominalBoreInches;
  }

  /**
   * Get outer diameter.
   *
   * @return outer diameter in inches
   */
  public double getOuterDiameterInches() {
    return outerDiameterInches;
  }

  /**
   * Set outer diameter.
   *
   * @param outerDiameterInches outer diameter in inches
   */
  public void setOuterDiameterInches(double outerDiameterInches) {
    this.outerDiameterInches = outerDiameterInches;
  }

  /**
   * Get wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getWallThicknessMm() {
    return wallThicknessMm;
  }

  /**
   * Set wall thickness.
   *
   * @param wallThicknessMm wall thickness in mm
   */
  public void setWallThicknessMm(double wallThicknessMm) {
    this.wallThicknessMm = wallThicknessMm;
  }

  /**
   * Get jumper length.
   *
   * @return length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set jumper length.
   *
   * @param length length in meters
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get horizontal span.
   *
   * @return horizontal span in meters
   */
  public double getHorizontalSpan() {
    return horizontalSpan;
  }

  /**
   * Set horizontal span.
   *
   * @param horizontalSpan horizontal span in meters
   */
  public void setHorizontalSpan(double horizontalSpan) {
    this.horizontalSpan = horizontalSpan;
  }

  /**
   * Get vertical rise.
   *
   * @return vertical rise in meters
   */
  public double getVerticalRise() {
    return verticalRise;
  }

  /**
   * Set vertical rise.
   *
   * @param verticalRise vertical rise in meters
   */
  public void setVerticalRise(double verticalRise) {
    this.verticalRise = verticalRise;
  }

  /**
   * Get inlet hub type.
   *
   * @return inlet hub type
   */
  public HubType getInletHubType() {
    return inletHubType;
  }

  /**
   * Set inlet hub type.
   *
   * @param inletHubType inlet hub type
   */
  public void setInletHubType(HubType inletHubType) {
    this.inletHubType = inletHubType;
  }

  /**
   * Get outlet hub type.
   *
   * @return outlet hub type
   */
  public HubType getOutletHubType() {
    return outletHubType;
  }

  /**
   * Set outlet hub type.
   *
   * @param outletHubType outlet hub type
   */
  public void setOutletHubType(HubType outletHubType) {
    this.outletHubType = outletHubType;
  }

  /**
   * Get material grade.
   *
   * @return material grade
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set material grade.
   *
   * @param materialGrade material grade (X65, 316L, etc.)
   */
  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
  }

  /**
   * Get number of bends.
   *
   * @return number of bends
   */
  public int getNumberOfBends() {
    return numberOfBends;
  }

  /**
   * Set number of bends.
   *
   * @param numberOfBends number of bends
   */
  public void setNumberOfBends(int numberOfBends) {
    this.numberOfBends = numberOfBends;
  }

  /**
   * Get minimum bend radius.
   *
   * @return minimum bend radius in meters
   */
  public double getMinimumBendRadius() {
    return minimumBendRadius;
  }

  /**
   * Set minimum bend radius.
   *
   * @param minimumBendRadius minimum bend radius in meters
   */
  public void setMinimumBendRadius(double minimumBendRadius) {
    this.minimumBendRadius = minimumBendRadius;
  }

  /**
   * Get flexible pipe structure type.
   *
   * @return flexible structure type
   */
  public String getFlexibleStructure() {
    return flexibleStructure;
  }

  /**
   * Set flexible pipe structure type.
   *
   * @param flexibleStructure structure type (Unbonded, Bonded)
   */
  public void setFlexibleStructure(String flexibleStructure) {
    this.flexibleStructure = flexibleStructure;
  }

  /**
   * Get installation method.
   *
   * @return installation method
   */
  public String getInstallationMethod() {
    return installationMethod;
  }

  /**
   * Set installation method.
   *
   * @param installationMethod installation method
   */
  public void setInstallationMethod(String installationMethod) {
    this.installationMethod = installationMethod;
  }

  /**
   * Check if ROV installed.
   *
   * @return true if ROV installed
   */
  public boolean isRovInstalled() {
    return rovInstalled;
  }

  /**
   * Set whether ROV installed.
   *
   * @param rovInstalled true for ROV installation
   */
  public void setRovInstalled(boolean rovInstalled) {
    this.rovInstalled = rovInstalled;
  }

  /**
   * Check if retrievable.
   *
   * @return true if retrievable
   */
  public boolean isRetrievable() {
    return retrievable;
  }

  /**
   * Set whether retrievable.
   *
   * @param retrievable true if retrievable
   */
  public void setRetrievable(boolean retrievable) {
    this.retrievable = retrievable;
  }

  /**
   * Get dry weight.
   *
   * @return dry weight in tonnes
   */
  public double getDryWeight() {
    return dryWeight;
  }

  /**
   * Set dry weight.
   *
   * @param dryWeight dry weight in tonnes
   */
  public void setDryWeight(double dryWeight) {
    this.dryWeight = dryWeight;
  }

  /**
   * Get submerged weight.
   *
   * @return submerged weight in tonnes
   */
  public double getSubmergedWeight() {
    return submergedWeight;
  }

  /**
   * Set submerged weight.
   *
   * @param submergedWeight submerged weight in tonnes
   */
  public void setSubmergedWeight(double submergedWeight) {
    this.submergedWeight = submergedWeight;
  }

  /**
   * Get flexible minimum bend radius.
   *
   * @return flexible min bend radius in meters
   */
  public double getFlexibleMinBendRadius() {
    return flexibleMinBendRadius;
  }

  /**
   * Set flexible minimum bend radius.
   *
   * @param flexibleMinBendRadius min bend radius in meters
   */
  public void setFlexibleMinBendRadius(double flexibleMinBendRadius) {
    this.flexibleMinBendRadius = flexibleMinBendRadius;
  }

  /**
   * Get inlet hub size.
   *
   * @return inlet hub size in inches
   */
  public double getInletHubSizeInches() {
    return inletHubSizeInches;
  }

  /**
   * Set inlet hub size.
   *
   * @param inletHubSizeInches inlet hub size in inches
   */
  public void setInletHubSizeInches(double inletHubSizeInches) {
    this.inletHubSizeInches = inletHubSizeInches;
  }

  /**
   * Get outlet hub size.
   *
   * @return outlet hub size in inches
   */
  public double getOutletHubSizeInches() {
    return outletHubSizeInches;
  }

  /**
   * Set outlet hub size.
   *
   * @param outletHubSizeInches outlet hub size in inches
   */
  public void setOutletHubSizeInches(double outletHubSizeInches) {
    this.outletHubSizeInches = outletHubSizeInches;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    com.google.gson.JsonObject jsonObj = new com.google.gson.JsonObject();
    jsonObj.addProperty("name", getName());
    jsonObj.addProperty("componentType", "SubseaJumper");
    jsonObj.addProperty("jumperType", jumperType != null ? jumperType.toString() : null);

    // Dimensions
    com.google.gson.JsonObject dimensions = new com.google.gson.JsonObject();
    dimensions.addProperty("length_m", length);
    dimensions.addProperty("innerDiameter_m", innerDiameter);
    dimensions.addProperty("outerDiameter_m", outerDiameter);
    dimensions.addProperty("wallThickness_mm", wallThickness * 1000);
    dimensions.addProperty("nominalBoreInches", nominalBoreInches);
    jsonObj.add("dimensions", dimensions);

    // Environment
    com.google.gson.JsonObject environment = new com.google.gson.JsonObject();
    environment.addProperty("waterDepth_m", waterDepth);
    environment.addProperty("installationType",
        installationType != null ? installationType.toString() : null);
    jsonObj.add("environment", environment);

    // Material
    com.google.gson.JsonObject material = new com.google.gson.JsonObject();
    material.addProperty("materialGrade", materialGrade);
    material.addProperty("dryWeight_tonnes", dryWeight);
    jsonObj.add("material", material);

    // Process conditions
    if (inStream != null && inStream.getThermoSystem() != null) {
      com.google.gson.JsonObject process = new com.google.gson.JsonObject();
      process.addProperty("inletPressure_bar", inStream.getPressure("bara"));
      process.addProperty("inletTemperature_C", inStream.getTemperature("C"));
      process.addProperty("outletPressure_bar",
          outStream != null ? outStream.getPressure("bara") : null);
      process.addProperty("pressureDrop_bar", pressureDrop);
      jsonObj.add("processConditions", process);
    }

    return new com.google.gson.GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(jsonObj);
  }
}
