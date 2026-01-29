package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.subsea.SubseaTreeMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Subsea Tree (Christmas Tree) equipment class.
 *
 * <p>
 * A subsea tree is a system of valves and connectors installed on top of a subsea wellhead to
 * control production from the well. Key functions include:
 * </p>
 * <ul>
 * <li>Well control and shut-in capability</li>
 * <li>Production and annulus access</li>
 * <li>Chemical injection points</li>
 * <li>Monitoring (pressure, temperature sensors)</li>
 * <li>Connection interface for flowlines/jumpers</li>
 * </ul>
 *
 * <h2>Tree Types</h2>
 * <ul>
 * <li><b>Vertical Tree</b>: Tubing hanger inside wellhead, tree on top</li>
 * <li><b>Horizontal Tree</b>: Tubing hanger inside tree body</li>
 * <li><b>Dual Bore Tree</b>: Two production bores</li>
 * <li><b>Mudline Tree</b>: Simplified tree for shallow water</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API Spec 17D - Design and Operation of Subsea Production Systems</li>
 * <li>API RP 17A - Design and Operation of Subsea Production Systems</li>
 * <li>ISO 13628-4 - Subsea Wellhead and Tree Equipment</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create horizontal tree
 * SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
 * tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
 * tree.setWaterDepth(450.0);
 * tree.setDesignPressure(690.0); // 10000 psi
 * tree.setDesignTemperature(150.0);
 *
 * // Configure valves
 * tree.setProductionMasterValveOpen(true);
 * tree.setProductionWingValveOpen(true);
 * tree.setChokeOpening(75.0);
 *
 * tree.run();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaTreeMechanicalDesign
 * @see SubseaManifold
 */
public class SubseaTree extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Subsea tree type configurations.
   */
  public enum TreeType {
    /** Vertical (conventional) tree. */
    VERTICAL("Vertical"),
    /** Horizontal tree. */
    HORIZONTAL("Horizontal"),
    /** Dual bore tree. */
    DUAL_BORE("Dual Bore"),
    /** Mudline suspension tree. */
    MUDLINE("Mudline"),
    /** Spool tree (compact). */
    SPOOL("Spool");

    private final String displayName;

    TreeType(String displayName) {
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
   * Pressure rating class per API 17D.
   */
  public enum PressureRating {
    /** 5000 psi (345 bar). */
    PR5000(5000, 345),
    /** 10000 psi (690 bar). */
    PR10000(10000, 690),
    /** 15000 psi (1034 bar). */
    PR15000(15000, 1034),
    /** 20000 psi (1379 bar). */
    PR20000(20000, 1379);

    private final int psi;
    private final int bar;

    PressureRating(int psi, int bar) {
      this.psi = psi;
      this.bar = bar;
    }

    /**
     * Get pressure in psi.
     *
     * @return pressure in psi
     */
    public int getPsi() {
      return psi;
    }

    /**
     * Get pressure in bar.
     *
     * @return pressure in bar
     */
    public int getBar() {
      return bar;
    }
  }

  // ============ Configuration ============
  /** Tree type. */
  private TreeType treeType = TreeType.HORIZONTAL;

  /** Pressure rating. */
  private PressureRating pressureRating = PressureRating.PR10000;

  /** Water depth in meters. */
  private double waterDepth = 350.0;

  /** Design pressure in bara. */
  private double designPressure = 690.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 150.0;

  /** Bore size in inches. */
  private double boreSizeInches = 5.0;

  // ============ Valves ============
  /** Production master valve (PMV). */
  private ThrottlingValve productionMasterValve;
  private boolean pmvOpen = true;

  /** Production wing valve (PWV). */
  private ThrottlingValve productionWingValve;
  private boolean pwvOpen = true;

  /** Production swab valve (PSV). */
  private boolean psvOpen = false;

  /** Annulus master valve (AMV). */
  private boolean amvOpen = false;

  /** Annulus wing valve (AWV). */
  private boolean awvOpen = false;

  /** Crossover valve (XOV). */
  private boolean xovOpen = false;

  /** Production choke. */
  private ThrottlingValve productionChoke;
  private double chokeOpening = 100.0;

  // ============ Actuators ============
  /** Actuator type for valves. */
  private String actuatorType = "Hydraulic";

  /** Whether has fail-safe close. */
  private boolean failSafeClose = true;

  // ============ Monitoring ============
  /** Whether has downhole pressure gauge. */
  private boolean hasDownholePressure = true;

  /** Whether has downhole temperature gauge. */
  private boolean hasDownholeTemperature = true;

  /** Whether has annulus pressure monitoring. */
  private boolean hasAnnulusPressure = true;

  /** Number of chemical injection points. */
  private int chemicalInjectionPoints = 2;

  // ============ Connection ============
  /** Tree connector type. */
  private String connectorType = "Collet";

  /** Flowline connection type. */
  private String flowlineConnectionType = "Vertical Hub";

  /** Flowline connection size in inches. */
  private double flowlineConnectionSizeInches = 6.0;

  // ============ Structure ============
  /** Tree height in meters. */
  private double treeHeight = 5.0;

  /** Tree footprint (diameter) in meters. */
  private double treeDiameter = 3.0;

  /** Dry weight in tonnes. */
  private double dryWeight = 35.0;

  /** Submerged weight in tonnes. */
  private double submergedWeight = 30.0;

  /** Mechanical design instance. */
  private SubseaTreeMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public SubseaTree() {
    super("Subsea Tree");
    initializeValves();
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public SubseaTree(String name) {
    super(name);
    initializeValves();
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream (from well)
   */
  public SubseaTree(String name, StreamInterface inStream) {
    super(name, inStream);
    initializeValves();
  }

  /**
   * Initialize internal valves.
   */
  private void initializeValves() {
    if (inStream != null) {
      productionMasterValve = new ThrottlingValve(getName() + " PMV", inStream);
      productionMasterValve.setPercentValveOpening(pmvOpen ? 100.0 : 0.0);

      productionWingValve =
          new ThrottlingValve(getName() + " PWV", productionMasterValve.getOutletStream());
      productionWingValve.setPercentValveOpening(pwvOpen ? 100.0 : 0.0);

      productionChoke =
          new ThrottlingValve(getName() + " Choke", productionWingValve.getOutletStream());
      productionChoke.setPercentValveOpening(chokeOpening);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      return;
    }

    // Reinitialize valves if needed
    if (productionMasterValve == null) {
      initializeValves();
    }

    // Update valve positions
    productionMasterValve.setInletStream(inStream);
    productionMasterValve.setPercentValveOpening(pmvOpen ? 100.0 : 0.0);

    // Check if tree is open
    if (!pmvOpen || !pwvOpen) {
      // Tree closed - no flow
      SystemInterface outFluid = inStream.getFluid().clone();
      // Set flow to zero or very low
      outStream.setFluid(outFluid);
      setCalculationIdentifier(id);
      return;
    }

    // Run valve sequence
    productionMasterValve.run(id);

    productionWingValve.setInletStream(productionMasterValve.getOutletStream());
    productionWingValve.setPercentValveOpening(100.0); // PWV is binary
    productionWingValve.run(id);

    productionChoke.setInletStream(productionWingValve.getOutletStream());
    productionChoke.setPercentValveOpening(chokeOpening);
    productionChoke.run(id);

    // Set outlet
    outStream.setFluid(productionChoke.getOutletStream().getFluid().clone());
    setCalculationIdentifier(id);
  }

  /**
   * Emergency shutdown - close all valves.
   */
  public void emergencyShutdown() {
    pmvOpen = false;
    pwvOpen = false;
    amvOpen = false;
    awvOpen = false;
    xovOpen = false;
    chokeOpening = 0.0;
  }

  /**
   * Open tree for production.
   */
  public void openForProduction() {
    pmvOpen = true;
    pwvOpen = true;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  @Override
  public SubseaTreeMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new SubseaTreeMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new SubseaTreeMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get tree type.
   *
   * @return tree type
   */
  public TreeType getTreeType() {
    return treeType;
  }

  /**
   * Set tree type.
   *
   * @param treeType tree type
   */
  public void setTreeType(TreeType treeType) {
    this.treeType = treeType;
  }

  /**
   * Get pressure rating.
   *
   * @return pressure rating
   */
  public PressureRating getPressureRating() {
    return pressureRating;
  }

  /**
   * Set pressure rating.
   *
   * @param pressureRating pressure rating
   */
  public void setPressureRating(PressureRating pressureRating) {
    this.pressureRating = pressureRating;
    this.designPressure = pressureRating.getBar();
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
   * Get bore size.
   *
   * @return bore size in inches
   */
  public double getBoreSizeInches() {
    return boreSizeInches;
  }

  /**
   * Set bore size.
   *
   * @param boreSizeInches bore size in inches
   */
  public void setBoreSizeInches(double boreSizeInches) {
    this.boreSizeInches = boreSizeInches;
  }

  /**
   * Check if PMV is open.
   *
   * @return true if PMV open
   */
  public boolean isProductionMasterValveOpen() {
    return pmvOpen;
  }

  /**
   * Set PMV position.
   *
   * @param open true to open
   */
  public void setProductionMasterValveOpen(boolean open) {
    this.pmvOpen = open;
  }

  /**
   * Check if PWV is open.
   *
   * @return true if PWV open
   */
  public boolean isProductionWingValveOpen() {
    return pwvOpen;
  }

  /**
   * Set PWV position.
   *
   * @param open true to open
   */
  public void setProductionWingValveOpen(boolean open) {
    this.pwvOpen = open;
  }

  /**
   * Get choke opening.
   *
   * @return choke opening percentage (0-100)
   */
  public double getChokeOpening() {
    return chokeOpening;
  }

  /**
   * Set choke opening.
   *
   * @param chokeOpening choke opening percentage (0-100)
   */
  public void setChokeOpening(double chokeOpening) {
    this.chokeOpening = Math.max(0, Math.min(100, chokeOpening));
  }

  /**
   * Check if AMV is open.
   *
   * @return true if AMV open
   */
  public boolean isAnnulusMasterValveOpen() {
    return amvOpen;
  }

  /**
   * Set AMV position.
   *
   * @param open true to open
   */
  public void setAnnulusMasterValveOpen(boolean open) {
    this.amvOpen = open;
  }

  /**
   * Check if AWV is open.
   *
   * @return true if AWV open
   */
  public boolean isAnnulusWingValveOpen() {
    return awvOpen;
  }

  /**
   * Set AWV position.
   *
   * @param open true to open
   */
  public void setAnnulusWingValveOpen(boolean open) {
    this.awvOpen = open;
  }

  /**
   * Check if XOV is open.
   *
   * @return true if XOV open
   */
  public boolean isCrossoverValveOpen() {
    return xovOpen;
  }

  /**
   * Set XOV position.
   *
   * @param open true to open
   */
  public void setCrossoverValveOpen(boolean open) {
    this.xovOpen = open;
  }

  /**
   * Get actuator type.
   *
   * @return actuator type
   */
  public String getActuatorType() {
    return actuatorType;
  }

  /**
   * Set actuator type.
   *
   * @param actuatorType actuator type (Hydraulic, Electric)
   */
  public void setActuatorType(String actuatorType) {
    this.actuatorType = actuatorType;
  }

  /**
   * Check if fail-safe close.
   *
   * @return true if fail-safe
   */
  public boolean isFailSafeClose() {
    return failSafeClose;
  }

  /**
   * Set fail-safe close.
   *
   * @param failSafeClose true for fail-safe
   */
  public void setFailSafeClose(boolean failSafeClose) {
    this.failSafeClose = failSafeClose;
  }

  /**
   * Get connector type.
   *
   * @return connector type
   */
  public String getConnectorType() {
    return connectorType;
  }

  /**
   * Set connector type.
   *
   * @param connectorType connector type (Collet, Clamp, etc.)
   */
  public void setConnectorType(String connectorType) {
    this.connectorType = connectorType;
  }

  /**
   * Get flowline connection type.
   *
   * @return flowline connection type
   */
  public String getFlowlineConnectionType() {
    return flowlineConnectionType;
  }

  /**
   * Set flowline connection type.
   *
   * @param flowlineConnectionType connection type (Vertical Hub, Horizontal Hub)
   */
  public void setFlowlineConnectionType(String flowlineConnectionType) {
    this.flowlineConnectionType = flowlineConnectionType;
  }

  /**
   * Get flowline connection size.
   *
   * @return connection size in inches
   */
  public double getFlowlineConnectionSizeInches() {
    return flowlineConnectionSizeInches;
  }

  /**
   * Set flowline connection size.
   *
   * @param flowlineConnectionSizeInches connection size in inches
   */
  public void setFlowlineConnectionSizeInches(double flowlineConnectionSizeInches) {
    this.flowlineConnectionSizeInches = flowlineConnectionSizeInches;
  }

  /**
   * Get tree height.
   *
   * @return tree height in meters
   */
  public double getTreeHeight() {
    return treeHeight;
  }

  /**
   * Set tree height.
   *
   * @param treeHeight tree height in meters
   */
  public void setTreeHeight(double treeHeight) {
    this.treeHeight = treeHeight;
  }

  /**
   * Get tree diameter.
   *
   * @return tree diameter in meters
   */
  public double getTreeDiameter() {
    return treeDiameter;
  }

  /**
   * Set tree diameter.
   *
   * @param treeDiameter tree diameter in meters
   */
  public void setTreeDiameter(double treeDiameter) {
    this.treeDiameter = treeDiameter;
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
   * Get chemical injection points count.
   *
   * @return number of chemical injection points
   */
  public int getChemicalInjectionPoints() {
    return chemicalInjectionPoints;
  }

  /**
   * Set chemical injection points count.
   *
   * @param chemicalInjectionPoints number of points
   */
  public void setChemicalInjectionPoints(int chemicalInjectionPoints) {
    this.chemicalInjectionPoints = chemicalInjectionPoints;
  }

  /**
   * Check if has downhole pressure gauge.
   *
   * @return true if has DHPT
   */
  public boolean hasDownholePressure() {
    return hasDownholePressure;
  }

  /**
   * Set whether has downhole pressure gauge.
   *
   * @param hasDownholePressure true to include
   */
  public void setHasDownholePressure(boolean hasDownholePressure) {
    this.hasDownholePressure = hasDownholePressure;
  }

  /**
   * Check if has downhole temperature gauge.
   *
   * @return true if has DHTT
   */
  public boolean hasDownholeTemperature() {
    return hasDownholeTemperature;
  }

  /**
   * Set whether has downhole temperature gauge.
   *
   * @param hasDownholeTemperature true to include
   */
  public void setHasDownholeTemperature(boolean hasDownholeTemperature) {
    this.hasDownholeTemperature = hasDownholeTemperature;
  }

  /**
   * Get production choke.
   *
   * @return production choke valve
   */
  public ThrottlingValve getProductionChoke() {
    return productionChoke;
  }
}
