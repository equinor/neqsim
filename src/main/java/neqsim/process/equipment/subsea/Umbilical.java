package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign;

/**
 * Subsea Umbilical equipment class.
 *
 * <p>
 * An umbilical is a bundled assembly of tubes, hoses, and cables used to:
 * </p>
 * <ul>
 * <li>Supply hydraulic fluid for valve actuation</li>
 * <li>Supply electrical power for subsea equipment</li>
 * <li>Transmit control signals and data</li>
 * <li>Inject chemicals (MEG, methanol, scale inhibitor, corrosion inhibitor)</li>
 * </ul>
 *
 * <h2>Umbilical Types</h2>
 * <ul>
 * <li><b>Steel Tube Umbilical (STU)</b>: Thermoplastic hoses replaced by steel tubes</li>
 * <li><b>Thermoplastic Hose Umbilical</b>: Traditional design with nylon/PE hoses</li>
 * <li><b>Integrated Production Umbilical (IPU)</b>: Combined with small bore production</li>
 * <li><b>Electro-Hydraulic</b>: Combines hydraulic and electrical functions</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17E - Specification for Subsea Umbilicals</li>
 * <li>API Spec 17E - Specification for Subsea Production Control Umbilicals</li>
 * <li>ISO 13628-5 - Subsea Umbilicals</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems (for steel tubes)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create steel tube umbilical
 * Umbilical umbilical = new Umbilical("Main Umbilical");
 * umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
 * umbilical.setLength(25000.0); // 25 km
 * umbilical.setWaterDepth(450.0);
 *
 * // Add functional elements
 * umbilical.addHydraulicLine("LP Hydraulic", 12.7, 345.0);
 * umbilical.addHydraulicLine("HP Hydraulic", 9.5, 690.0);
 * umbilical.addChemicalLine("MEG", 25.4, 150.0);
 * umbilical.addElectricalCable("Power", 3, 6600.0);
 * umbilical.addFiberOptic("Control", 12);
 *
 * // Get mechanical design
 * UmbilicalMechanicalDesign design = umbilical.getMechanicalDesign();
 * design.calcDesign();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see UmbilicalMechanicalDesign
 */
public class Umbilical extends ProcessEquipmentBaseClass {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Umbilical construction type.
   */
  public enum UmbilicalType {
    /** Steel tube umbilical. */
    STEEL_TUBE("Steel Tube"),
    /** Thermoplastic hose umbilical. */
    THERMOPLASTIC("Thermoplastic"),
    /** Integrated production umbilical. */
    INTEGRATED_PRODUCTION("Integrated Production"),
    /** Electro-hydraulic umbilical. */
    ELECTRO_HYDRAULIC("Electro-Hydraulic"),
    /** Electric only umbilical. */
    ELECTRIC_ONLY("Electric Only");

    private final String displayName;

    UmbilicalType(String displayName) {
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
   * Umbilical cross-section configuration.
   */
  public enum CrossSectionType {
    /** Circular cross-section. */
    CIRCULAR("Circular"),
    /** Flat/ribbon cross-section. */
    FLAT("Flat"),
    /** Bundled elements. */
    BUNDLED("Bundled");

    private final String displayName;

    CrossSectionType(String displayName) {
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
   * Functional element in umbilical.
   */
  public static class UmbilicalElement {
    /** Element type (hydraulic, chemical, electrical, fiber). */
    private String elementType;
    /** Element name/function. */
    private String name;
    /** Inner diameter in mm (for tubes/hoses). */
    private double innerDiameterMm;
    /** Outer diameter in mm. */
    private double outerDiameterMm;
    /** Design pressure in bar (for tubes/hoses). */
    private double designPressureBar;
    /** Material. */
    private String material;
    /** Number of cores (for cables). */
    private int numberOfCores;
    /** Voltage rating in V (for electrical). */
    private double voltageRating;
    /** Number of fibers (for fiber optic). */
    private int numberOfFibers;

    /**
     * Constructor for tubes/hoses.
     *
     * @param elementType element type
     * @param name element name
     * @param innerDiameterMm inner diameter in mm
     * @param designPressureBar design pressure in bar
     */
    public UmbilicalElement(String elementType, String name, double innerDiameterMm,
        double designPressureBar) {
      this.elementType = elementType;
      this.name = name;
      this.innerDiameterMm = innerDiameterMm;
      this.designPressureBar = designPressureBar;
    }

    /**
     * Constructor for electrical cables.
     *
     * @param name cable name
     * @param numberOfCores number of cores
     * @param voltageRating voltage rating in V
     */
    public UmbilicalElement(String name, int numberOfCores, double voltageRating) {
      this.elementType = "electrical";
      this.name = name;
      this.numberOfCores = numberOfCores;
      this.voltageRating = voltageRating;
    }

    // Getters and setters
    public String getElementType() {
      return elementType;
    }

    public String getName() {
      return name;
    }

    public double getInnerDiameterMm() {
      return innerDiameterMm;
    }

    public double getOuterDiameterMm() {
      return outerDiameterMm;
    }

    public void setOuterDiameterMm(double outerDiameterMm) {
      this.outerDiameterMm = outerDiameterMm;
    }

    public double getDesignPressureBar() {
      return designPressureBar;
    }

    public String getMaterial() {
      return material;
    }

    public void setMaterial(String material) {
      this.material = material;
    }

    public int getNumberOfCores() {
      return numberOfCores;
    }

    public double getVoltageRating() {
      return voltageRating;
    }

    public int getNumberOfFibers() {
      return numberOfFibers;
    }

    public void setNumberOfFibers(int numberOfFibers) {
      this.numberOfFibers = numberOfFibers;
    }
  }

  // ============ Configuration ============
  /** Umbilical type. */
  private UmbilicalType umbilicalType = UmbilicalType.STEEL_TUBE;

  /** Cross-section type. */
  private CrossSectionType crossSectionType = CrossSectionType.CIRCULAR;

  /** Water depth in meters. */
  private double waterDepth = 350.0;

  /** Umbilical length in meters. */
  private double length = 25000.0;

  /** Overall outer diameter in mm. */
  private double overallDiameterMm = 150.0;

  // ============ Functional Elements ============
  /** List of umbilical elements. */
  private List<UmbilicalElement> elements = new ArrayList<UmbilicalElement>();

  /** Number of hydraulic lines. */
  private int hydraulicLineCount = 0;

  /** Number of chemical injection lines. */
  private int chemicalLineCount = 0;

  /** Number of electrical cables. */
  private int electricalCableCount = 0;

  /** Number of fiber optic cables. */
  private int fiberOpticCount = 0;

  // ============ Outer Sheath ============
  /** Outer sheath material. */
  private String outerSheathMaterial = "HDPE";

  /** Outer sheath thickness in mm. */
  private double outerSheathThicknessMm = 5.0;

  /** Whether has armor wires. */
  private boolean hasArmorWires = true;

  /** Armor wire material. */
  private String armorWireMaterial = "Galvanized Steel";

  // ============ Installation ============
  /** Installation method. */
  private String installationMethod = "Carousel";

  /** Installation vessel type. */
  private String installationVessel = "Cable Lay Vessel";

  /** Maximum installation tension in kN. */
  private double maxInstallationTensionKN = 200.0;

  // ============ Weight ============
  /** Dry weight per meter in kg/m. */
  private double dryWeightPerMeter = 25.0;

  /** Submerged weight per meter in kg/m. */
  private double submergedWeightPerMeter = 15.0;

  /** Minimum bend radius in meters. */
  private double minimumBendRadius = 2.0;

  /** Mechanical design instance. */
  private UmbilicalMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public Umbilical() {
    super("Umbilical");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public Umbilical(String name) {
    super(name);
  }

  /**
   * Add hydraulic control line.
   *
   * @param name line name/function
   * @param innerDiameterMm inner diameter in mm
   * @param designPressureBar design pressure in bar
   */
  public void addHydraulicLine(String name, double innerDiameterMm, double designPressureBar) {
    UmbilicalElement element =
        new UmbilicalElement("hydraulic", name, innerDiameterMm, designPressureBar);
    element.setMaterial(umbilicalType == UmbilicalType.STEEL_TUBE ? "Super Duplex" : "Nylon PA11");
    elements.add(element);
    hydraulicLineCount++;
  }

  /**
   * Add chemical injection line.
   *
   * @param name chemical name (MEG, MeOH, Scale Inhibitor, etc.)
   * @param innerDiameterMm inner diameter in mm
   * @param designPressureBar design pressure in bar
   */
  public void addChemicalLine(String name, double innerDiameterMm, double designPressureBar) {
    UmbilicalElement element =
        new UmbilicalElement("chemical", name, innerDiameterMm, designPressureBar);
    element.setMaterial(umbilicalType == UmbilicalType.STEEL_TUBE ? "Super Duplex" : "PVDF");
    elements.add(element);
    chemicalLineCount++;
  }

  /**
   * Add electrical power/signal cable.
   *
   * @param name cable name/function
   * @param numberOfCores number of conductor cores
   * @param voltageRating voltage rating in V
   */
  public void addElectricalCable(String name, int numberOfCores, double voltageRating) {
    UmbilicalElement element = new UmbilicalElement(name, numberOfCores, voltageRating);
    element.setMaterial("Copper");
    elements.add(element);
    electricalCableCount++;
  }

  /**
   * Add fiber optic cable.
   *
   * @param name cable name/function
   * @param numberOfFibers number of optical fibers
   */
  public void addFiberOptic(String name, int numberOfFibers) {
    UmbilicalElement element = new UmbilicalElement("fiber", name, 0, 0);
    element.setNumberOfFibers(numberOfFibers);
    element.setMaterial("Single Mode Fiber");
    elements.add(element);
    fiberOpticCount++;
  }

  /**
   * Get total number of elements.
   *
   * @return total element count
   */
  public int getTotalElementCount() {
    return elements.size();
  }

  /**
   * Calculate total cross-sectional area.
   *
   * @return total cross-sectional area in mm²
   */
  public double calculateTotalCrossSection() {
    double totalArea = 0.0;
    for (UmbilicalElement element : elements) {
      if (element.getOuterDiameterMm() > 0) {
        totalArea += Math.PI * element.getOuterDiameterMm() * element.getOuterDiameterMm() / 4.0;
      } else if (element.getInnerDiameterMm() > 0) {
        // Estimate outer diameter
        double od = element.getInnerDiameterMm() * 1.5; // Rough estimate
        totalArea += Math.PI * od * od / 4.0;
      }
    }
    return totalArea;
  }

  /**
   * Estimate overall diameter based on elements.
   *
   * @return estimated overall diameter in mm
   */
  public double estimateOverallDiameter() {
    double totalArea = calculateTotalCrossSection();
    // Add 30% for filler and outer sheath
    double effectiveArea = totalArea * 1.3;
    // Add armor layer
    if (hasArmorWires) {
      effectiveArea *= 1.2;
    }
    // Add outer sheath
    double innerDiameter = Math.sqrt(4 * effectiveArea / Math.PI);
    return innerDiameter + 2 * outerSheathThicknessMm;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Umbilical is passive equipment - calculate properties
    overallDiameterMm = estimateOverallDiameter();

    // Calculate weights
    calculateWeights();

    setCalculationIdentifier(id);
  }

  /**
   * Calculate weights based on elements and construction.
   */
  private void calculateWeights() {
    double steelDensity = 7850.0; // kg/m³
    double hdpeDensity = 950.0; // kg/m³
    double copperDensity = 8960.0; // kg/m³
    double seawaterDensity = 1025.0; // kg/m³

    double totalDryWeight = 0.0;

    // Steel tubes/armor
    for (UmbilicalElement element : elements) {
      if ("hydraulic".equals(element.getElementType())
          || "chemical".equals(element.getElementType())) {
        if (umbilicalType == UmbilicalType.STEEL_TUBE) {
          // Steel tube weight
          double od = element.getOuterDiameterMm() > 0 ? element.getOuterDiameterMm()
              : element.getInnerDiameterMm() * 1.3;
          double id = element.getInnerDiameterMm();
          double tubeArea = Math.PI * (od * od - id * id) / 4.0 / 1e6; // m²
          totalDryWeight += tubeArea * steelDensity;
        }
      } else if ("electrical".equals(element.getElementType())) {
        // Copper cable weight (approximate)
        totalDryWeight += element.getNumberOfCores() * 0.5; // kg/m per core (rough)
      }
    }

    // Add outer sheath
    double sheathArea = Math.PI * overallDiameterMm * outerSheathThicknessMm / 1e6;
    totalDryWeight += sheathArea * hdpeDensity;

    // Add armor (if present)
    if (hasArmorWires) {
      totalDryWeight *= 1.3; // Approximate armor addition
    }

    dryWeightPerMeter = totalDryWeight;

    // Submerged weight
    double displacedVolume = Math.PI * overallDiameterMm * overallDiameterMm / 4.0 / 1e6;
    double buoyancy = displacedVolume * seawaterDensity;
    submergedWeightPerMeter = dryWeightPerMeter - buoyancy;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  public UmbilicalMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new UmbilicalMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new UmbilicalMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get umbilical type.
   *
   * @return umbilical type
   */
  public UmbilicalType getUmbilicalType() {
    return umbilicalType;
  }

  /**
   * Set umbilical type.
   *
   * @param umbilicalType umbilical type
   */
  public void setUmbilicalType(UmbilicalType umbilicalType) {
    this.umbilicalType = umbilicalType;
  }

  /**
   * Get cross-section type.
   *
   * @return cross-section type
   */
  public CrossSectionType getCrossSectionType() {
    return crossSectionType;
  }

  /**
   * Set cross-section type.
   *
   * @param crossSectionType cross-section type
   */
  public void setCrossSectionType(CrossSectionType crossSectionType) {
    this.crossSectionType = crossSectionType;
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
   * Get umbilical length.
   *
   * @return length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set umbilical length.
   *
   * @param length length in meters
   */
  public void setLength(double length) {
    this.length = length;
  }

  /**
   * Get overall outer diameter.
   *
   * @return overall diameter in mm
   */
  public double getOverallDiameterMm() {
    return overallDiameterMm;
  }

  /**
   * Set overall outer diameter.
   *
   * @param overallDiameterMm overall diameter in mm
   */
  public void setOverallDiameterMm(double overallDiameterMm) {
    this.overallDiameterMm = overallDiameterMm;
  }

  /**
   * Get elements.
   *
   * @return list of umbilical elements
   */
  public List<UmbilicalElement> getElements() {
    return elements;
  }

  /**
   * Get hydraulic line count.
   *
   * @return number of hydraulic lines
   */
  public int getHydraulicLineCount() {
    return hydraulicLineCount;
  }

  /**
   * Get chemical line count.
   *
   * @return number of chemical injection lines
   */
  public int getChemicalLineCount() {
    return chemicalLineCount;
  }

  /**
   * Get electrical cable count.
   *
   * @return number of electrical cables
   */
  public int getElectricalCableCount() {
    return electricalCableCount;
  }

  /**
   * Get fiber optic count.
   *
   * @return number of fiber optic cables
   */
  public int getFiberOpticCount() {
    return fiberOpticCount;
  }

  /**
   * Get outer sheath material.
   *
   * @return outer sheath material
   */
  public String getOuterSheathMaterial() {
    return outerSheathMaterial;
  }

  /**
   * Set outer sheath material.
   *
   * @param outerSheathMaterial outer sheath material
   */
  public void setOuterSheathMaterial(String outerSheathMaterial) {
    this.outerSheathMaterial = outerSheathMaterial;
  }

  /**
   * Check if has armor wires.
   *
   * @return true if has armor
   */
  public boolean hasArmorWires() {
    return hasArmorWires;
  }

  /**
   * Set whether has armor wires.
   *
   * @param hasArmorWires true to include armor
   */
  public void setHasArmorWires(boolean hasArmorWires) {
    this.hasArmorWires = hasArmorWires;
  }

  /**
   * Get armor wire material.
   *
   * @return armor wire material
   */
  public String getArmorWireMaterial() {
    return armorWireMaterial;
  }

  /**
   * Set armor wire material.
   *
   * @param armorWireMaterial armor wire material
   */
  public void setArmorWireMaterial(String armorWireMaterial) {
    this.armorWireMaterial = armorWireMaterial;
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
   * @param installationMethod installation method (Carousel, Reel, etc.)
   */
  public void setInstallationMethod(String installationMethod) {
    this.installationMethod = installationMethod;
  }

  /**
   * Get dry weight per meter.
   *
   * @return dry weight in kg/m
   */
  public double getDryWeightPerMeter() {
    return dryWeightPerMeter;
  }

  /**
   * Get submerged weight per meter.
   *
   * @return submerged weight in kg/m
   */
  public double getSubmergedWeightPerMeter() {
    return submergedWeightPerMeter;
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
   * Get maximum installation tension.
   *
   * @return maximum tension in kN
   */
  public double getMaxInstallationTensionKN() {
    return maxInstallationTensionKN;
  }

  /**
   * Set maximum installation tension.
   *
   * @param maxInstallationTensionKN maximum tension in kN
   */
  public void setMaxInstallationTensionKN(double maxInstallationTensionKN) {
    this.maxInstallationTensionKN = maxInstallationTensionKN;
  }

  /**
   * Get outer sheath thickness.
   *
   * @return outer sheath thickness in mm
   */
  public double getOuterSheathThicknessMm() {
    return outerSheathThicknessMm;
  }

  /**
   * Set outer sheath thickness.
   *
   * @param outerSheathThicknessMm outer sheath thickness in mm
   */
  public void setOuterSheathThicknessMm(double outerSheathThicknessMm) {
    this.outerSheathThicknessMm = outerSheathThicknessMm;
  }
}
