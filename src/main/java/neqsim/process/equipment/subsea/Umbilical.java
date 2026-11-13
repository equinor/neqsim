package neqsim.process.equipment.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.mechanicaldesign.subsea.UmbilicalElectricalCalculator;
import neqsim.process.mechanicaldesign.subsea.UmbilicalHydraulicsCalculator;
import neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign;
import neqsim.process.mechanicaldesign.subsea.UmbilicalStructuralCalculator;

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
 * <h2>Physics</h2>
 * <ul>
 * <li>Tube wall thickness from internal design pressure (burst) and external hydrostatic pressure (DNV-ST-F101 collapse
 * equation with ovality)</li>
 * <li>Cross-section from element packing efficiency, inner/outer sheath and helical armour wires; weights in air (empty
 * and fluid-filled) and in seawater from the actual materials</li>
 * <li>Liquid line friction pressure drop, hydrostatic head and pressure-step response of long hydraulic and chemical
 * lines (laminar/turbulent Darcy-Weisbach, acoustic transit and diffusion response)</li>
 * <li>Three-phase power cable as a distributed line with skin effect and charging current</li>
 * <li>Free-hanging catenary tension, armour capacity and touchdown bend radius (see
 * {@link UmbilicalMechanicalDesign})</li>
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
 * // Line hydraulics and mechanical design
 * umbilical.setInjectionRate("MEG", 5.0, "m3/hr");
 * double megDropBar = umbilical.calculatePressureDrop("MEG");
 * UmbilicalMechanicalDesign design = umbilical.getMechanicalDesign();
 * design.calcDesign();
 * }</pre>
 *
 * @author ESOL
 * @version 2.0
 * @see UmbilicalMechanicalDesign
 */
public class Umbilical extends ProcessEquipmentBaseClass {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Density of steel tubes in kg/m3. */
  private static final double TUBE_STEEL_DENSITY = 7800.0;
  /** Density of copper in kg/m3. */
  private static final double COPPER_DENSITY = 8960.0;
  /** Density of core insulation (XLPE/EPR) in kg/m3. */
  private static final double INSULATION_DENSITY = 1000.0;
  /** Density of polymer filler and sheaths in kg/m3. */
  private static final double POLYMER_DENSITY = 950.0;
  /** Thickness of the sheath around a multi-core cable in mm. */
  private static final double CABLE_SHEATH_MM = 1.5;
  /** Outer diameter of a fibre-in-metal-tube element in mm. */
  private static final double FIBER_TUBE_OD_MM = 3.0;
  /** Wall thickness of a fibre-in-metal-tube element in mm. */
  private static final double FIBER_TUBE_WALL_MM = 0.3;
  /** Effective wall modulus of reinforced thermoplastic hose in Pa. */
  private static final double HOSE_MODULUS_PA = 2.0e9;

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
  public static class UmbilicalElement implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Element type (hydraulic, chemical, electrical, fiber). */
    private String elementType;
    /** Element name/function. */
    private String name;
    /** Inner diameter in mm (for tubes/hoses). */
    private double innerDiameterMm;
    /** Outer diameter in mm (user value, 0 to compute). */
    private double outerDiameterMm;
    /** Wall thickness in mm (user value, 0 to compute). */
    private double wallThicknessMm;
    /** Design pressure in bar (for tubes/hoses). */
    private double designPressureBar;
    /** Material. */
    private String material;
    /** Number of cores (for cables). */
    private int numberOfCores;
    /** Voltage rating in V (for electrical). */
    private double voltageRating;
    /** Conductor area per core in mm2 (for electrical). */
    private double conductorAreaMm2;
    /** Core insulation thickness in mm, 0 for the voltage-class default. */
    private double insulationThicknessMm;
    /** Number of fibers (for fiber optic). */
    private int numberOfFibers;
    /** Line fluid (tubes and hoses). */
    private UmbilicalFluid fluid;
    /** Design flow rate in m3/hr (tubes and hoses). */
    private double designFlowRateM3PerHr;

    /** Wall thickness used in the last geometry update in mm. */
    private double resolvedWallThicknessMm;
    /** Outer diameter used in the last geometry update in mm. */
    private double resolvedOuterDiameterMm;
    /** Mass of the element without line fluid in kg/m. */
    private double emptyMassKgPerM;
    /** Mass of the line fluid in kg/m. */
    private double fluidMassKgPerM;

    /**
     * Constructor for tubes/hoses.
     *
     * @param elementType element type
     * @param name element name
     * @param innerDiameterMm inner diameter in mm
     * @param designPressureBar design pressure in bar
     */
    public UmbilicalElement(String elementType, String name, double innerDiameterMm, double designPressureBar) {
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
      this.conductorAreaMm2 = UmbilicalElectricalCalculator.defaultConductorAreaMm2(voltageRating);
    }

    /**
     * Get element type.
     *
     * @return hydraulic, chemical, electrical or fiber
     */
    public String getElementType() {
      return elementType;
    }

    /**
     * Get element name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Get inner diameter.
     *
     * @return inner diameter in mm
     */
    public double getInnerDiameterMm() {
      return innerDiameterMm;
    }

    /**
     * Get the outer diameter, the user value if set, otherwise the value from the last geometry update
     * ({@link Umbilical#run(UUID)}).
     *
     * @return outer diameter in mm, 0 if not yet resolved
     */
    public double getOuterDiameterMm() {
      return outerDiameterMm > 0.0 ? outerDiameterMm : resolvedOuterDiameterMm;
    }

    /**
     * Set the outer diameter, overriding the computed value.
     *
     * @param outerDiameterMm outer diameter in mm
     */
    public void setOuterDiameterMm(double outerDiameterMm) {
      this.outerDiameterMm = outerDiameterMm;
    }

    /**
     * Get the wall thickness, the user value if set, otherwise the value from the last geometry update.
     *
     * @return wall thickness in mm
     */
    public double getWallThicknessMm() {
      return wallThicknessMm > 0.0 ? wallThicknessMm : resolvedWallThicknessMm;
    }

    /**
     * Set the wall thickness, overriding the pressure-based sizing.
     *
     * @param wallThicknessMm wall thickness in mm
     */
    public void setWallThicknessMm(double wallThicknessMm) {
      this.wallThicknessMm = wallThicknessMm;
    }

    /**
     * Get design pressure.
     *
     * @return design pressure in bar
     */
    public double getDesignPressureBar() {
      return designPressureBar;
    }

    /**
     * Get material.
     *
     * @return material
     */
    public String getMaterial() {
      return material;
    }

    /**
     * Set material.
     *
     * @param material material name
     */
    public void setMaterial(String material) {
      this.material = material;
    }

    /**
     * Get number of cores.
     *
     * @return number of cores
     */
    public int getNumberOfCores() {
      return numberOfCores;
    }

    /**
     * Get voltage rating.
     *
     * @return voltage rating in V
     */
    public double getVoltageRating() {
      return voltageRating;
    }

    /**
     * Get conductor area per core.
     *
     * @return area in mm2
     */
    public double getConductorAreaMm2() {
      return conductorAreaMm2;
    }

    /**
     * Set conductor area per core.
     *
     * @param conductorAreaMm2 area in mm2
     */
    public void setConductorAreaMm2(double conductorAreaMm2) {
      this.conductorAreaMm2 = conductorAreaMm2;
    }

    /**
     * Get core insulation thickness, the user value or the voltage-class default.
     *
     * @return thickness in mm
     */
    public double getInsulationThicknessMm() {
      return insulationThicknessMm > 0.0 ? insulationThicknessMm
          : UmbilicalElectricalCalculator.defaultInsulationThicknessMm(voltageRating);
    }

    /**
     * Set core insulation thickness.
     *
     * @param insulationThicknessMm thickness in mm, 0 for the voltage-class default
     */
    public void setInsulationThicknessMm(double insulationThicknessMm) {
      this.insulationThicknessMm = insulationThicknessMm;
    }

    /**
     * Get number of fibers.
     *
     * @return number of fibers
     */
    public int getNumberOfFibers() {
      return numberOfFibers;
    }

    /**
     * Set number of fibers.
     *
     * @param numberOfFibers number of fibers
     */
    public void setNumberOfFibers(int numberOfFibers) {
      this.numberOfFibers = numberOfFibers;
    }

    /**
     * Get the line fluid.
     *
     * @return fluid, null for cables and fibres
     */
    public UmbilicalFluid getFluid() {
      return fluid;
    }

    /**
     * Set the line fluid.
     *
     * @param fluid line fluid
     */
    public void setFluid(UmbilicalFluid fluid) {
      this.fluid = fluid;
    }

    /**
     * Get design flow rate.
     *
     * @return flow rate in m3/hr
     */
    public double getDesignFlowRateM3PerHr() {
      return designFlowRateM3PerHr;
    }

    /**
     * Set design flow rate.
     *
     * @param designFlowRateM3PerHr flow rate in m3/hr
     */
    public void setDesignFlowRateM3PerHr(double designFlowRateM3PerHr) {
      this.designFlowRateM3PerHr = designFlowRateM3PerHr;
    }

    /**
     * Get the mass of the element without line fluid.
     *
     * @return mass in kg/m
     */
    public double getEmptyMassKgPerM() {
      return emptyMassKgPerM;
    }

    /**
     * Get the mass of the line fluid.
     *
     * @return mass in kg/m
     */
    public double getFluidMassKgPerM() {
      return fluidMassKgPerM;
    }

    /**
     * Check if this is a liquid-filled tube or hose.
     *
     * @return true for hydraulic and chemical lines
     */
    public boolean isFluidLine() {
      return "hydraulic".equals(elementType) || "chemical".equals(elementType);
    }

    /**
     * Check if this element is a metallic tube.
     *
     * @return true if the material is not a thermoplastic
     */
    public boolean isSteelTube() {
      if (material == null) {
        return true;
      }
      String m = material.toLowerCase();
      return !(m.contains("nylon") || m.contains("pa11") || m.contains("pvdf") || m.contains("peek"));
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

  /** Seawater (seabed) temperature in C. */
  private double seawaterTemperatureC = 4.0;

  /** True for a dynamic (riser) section. */
  private boolean dynamicService = false;

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

  // ============ Tube design basis ============
  /** SMYS of the tube material in MPa (super duplex). */
  private double tubeSmysMPa = 550.0;

  /** Young's modulus of the tube material in GPa. */
  private double tubeYoungsModulusGPa = 200.0;

  /** Allowable fraction of SMYS for hoop stress at design pressure. */
  private double tubeUsageFactor = 0.6;

  /** Initial ovality for collapse. */
  private double tubeOvality = 0.005;

  /** Collapse safety factor. */
  private double collapseSafetyFactor = 1.5;

  /** Minimum practical tube wall in mm. */
  private double minimumTubeWallMm = 1.0;

  /** Allowed friction drop at design flow as a fraction of the line design pressure. */
  private double maxFrictionDropFraction = 0.5;

  // ============ Outer Sheath and Armor ============
  /** Outer sheath material. */
  private String outerSheathMaterial = "HDPE";

  /** Outer sheath thickness in mm. */
  private double outerSheathThicknessMm = 5.0;

  /** Inner sheath thickness over the bundle in mm. */
  private double innerSheathThicknessMm = 2.0;

  /** Diameter of the bundled functional elements in mm (set by the geometry update). */
  private double bundleDiameterMm = 0.0;

  /** Whether has armor wires. */
  private boolean hasArmorWires = true;

  /** Armor wire material. */
  private String armorWireMaterial = "Galvanized Steel";

  /** Number of armour layers. */
  private int armorLayerCount = 2;

  /** Armour wire diameter in mm. */
  private double armorWireDiameterMm = 4.0;

  /** Armour lay angle from the axis in degrees. */
  private double armorLayAngleDeg = 15.0;

  /** Armour wire ultimate tensile strength in MPa. */
  private double armorWireTensileStrengthMPa = 1400.0;

  /** Allowable fraction of the armour wire tensile strength. */
  private double armorAllowableFraction = 0.4;

  // ============ Installation ============
  /** Installation method. */
  private String installationMethod = "Carousel";

  /** Installation vessel type. */
  private String installationVessel = "Cable Lay Vessel";

  /** Maximum installation tension in kN. */
  private double maxInstallationTensionKN = 200.0;

  /** Horizontal distance from the top to touchdown in m, NaN for half the water depth. */
  private double touchdownOffsetM = Double.NaN;

  /** Tension amplification factor for installation and dynamic effects. */
  private double tensionAmplificationFactor = 1.3;

  // ============ Power transmission ============
  /** Active power delivered at the receiving end in MW. */
  private double powerLoadMW = 0.0;

  /** Load power factor. */
  private double powerFactor = 0.9;

  /** System frequency in Hz. */
  private double frequencyHz = 50.0;

  /** Conductor temperature in C. */
  private double conductorTemperatureC = 90.0;

  /** Relative permittivity of the core insulation. */
  private double insulationPermittivity = 2.5;

  // ============ Weight ============
  /** Dry weight per meter (empty, in air) in kg/m. */
  private double dryWeightPerMeter = 25.0;

  /** Weight per meter in air with line fluids in kg/m. */
  private double filledWeightPerMeter = 25.0;

  /** Submerged weight per meter (fluid filled) in kg/m. */
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
    addHydraulicLine(name, innerDiameterMm, designPressureBar, UmbilicalFluid.forLineName(name, true));
  }

  /**
   * Add hydraulic control line with a specified fluid.
   *
   * @param name line name/function
   * @param innerDiameterMm inner diameter in mm
   * @param designPressureBar design pressure in bar
   * @param fluid hydraulic fluid
   */
  public void addHydraulicLine(String name, double innerDiameterMm, double designPressureBar, UmbilicalFluid fluid) {
    UmbilicalElement element = new UmbilicalElement("hydraulic", name, innerDiameterMm, designPressureBar);
    element.setMaterial(umbilicalType == UmbilicalType.STEEL_TUBE ? "Super Duplex" : "Nylon PA11");
    element.setFluid(fluid);
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
    addChemicalLine(name, innerDiameterMm, designPressureBar, UmbilicalFluid.forLineName(name, false));
  }

  /**
   * Add chemical injection line with a specified fluid.
   *
   * @param name chemical name
   * @param innerDiameterMm inner diameter in mm
   * @param designPressureBar design pressure in bar
   * @param fluid injected chemical
   */
  public void addChemicalLine(String name, double innerDiameterMm, double designPressureBar, UmbilicalFluid fluid) {
    UmbilicalElement element = new UmbilicalElement("chemical", name, innerDiameterMm, designPressureBar);
    element.setMaterial(umbilicalType == UmbilicalType.STEEL_TUBE ? "Super Duplex" : "PVDF");
    element.setFluid(fluid);
    elements.add(element);
    chemicalLineCount++;
  }

  /**
   * Add electrical power/signal cable with screening defaults for conductor area and insulation from the voltage class.
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
   * Add electrical power/signal cable with a specified conductor size.
   *
   * @param name cable name/function
   * @param numberOfCores number of conductor cores
   * @param voltageRating voltage rating in V
   * @param conductorAreaMm2 copper area per core in mm2
   */
  public void addElectricalCable(String name, int numberOfCores, double voltageRating, double conductorAreaMm2) {
    addElectricalCable(name, numberOfCores, voltageRating);
    elements.get(elements.size() - 1).setConductorAreaMm2(conductorAreaMm2);
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
   * Get total number of elements.
   *
   * @return total element count
   */
  public int getElementCount() {
    return elements.size();
  }

  /**
   * Find an element by name (exact match ignoring case, otherwise first name containing the text).
   *
   * @param name element name or part of it
   * @return element
   * @throws IllegalArgumentException if no element matches
   */
  public UmbilicalElement findElement(String name) {
    String key = name == null ? "" : name.toLowerCase();
    for (UmbilicalElement e : elements) {
      if (e.getName().toLowerCase().equals(key)) {
        return e;
      }
    }
    for (UmbilicalElement e : elements) {
      if (e.getName().toLowerCase().contains(key)) {
        return e;
      }
    }
    throw new IllegalArgumentException("No umbilical element named '" + name + "'");
  }

  /**
   * Calculate total cross-sectional area of the elements (resolving element geometry first).
   *
   * @return total cross-sectional area in mm2
   */
  public double calculateTotalCrossSection() {
    double totalArea = 0.0;
    for (UmbilicalElement element : elements) {
      resolveElementGeometry(element);
      double od = element.getOuterDiameterMm();
      totalArea += Math.PI * od * od / 4.0;
    }
    return totalArea;
  }

  /**
   * Estimate the overall diameter from element packing, sheaths and armour.
   *
   * @return estimated overall diameter in mm
   */
  public double estimateOverallDiameter() {
    updateGeometryAndWeights();
    return overallDiameterMm;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Umbilical is passive equipment - calculate geometry and weights
    updateGeometryAndWeights();
    setCalculationIdentifier(id);
  }

  /**
   * Resolve wall thickness, outer diameter and mass of one element.
   *
   * @param e element
   */
  private void resolveElementGeometry(UmbilicalElement e) {
    String type = e.getElementType();
    if (e.isFluidLine()) {
      double id = e.getInnerDiameterMm();
      double wall = e.wallThicknessMm > 0.0 ? e.wallThicknessMm : requiredWallThicknessMm(e);
      e.resolvedWallThicknessMm = wall;
      e.resolvedOuterDiameterMm = id + 2.0 * wall;
      double od = e.getOuterDiameterMm();
      double density = e.isSteelTube() ? TUBE_STEEL_DENSITY : ("pvdf".equals(materialKey(e)) ? 1780.0 : 1030.0);
      e.emptyMassKgPerM = Math.PI / 4.0 * (od * od - id * id) * 1.0e-6 * density;
      e.fluidMassKgPerM = e.getFluid() == null ? 0.0 : Math.PI / 4.0 * id * id * 1.0e-6 * e.getFluid().getDensityKgM3();
    } else if ("electrical".equals(type)) {
      double dCond = UmbilicalElectricalCalculator.conductorDiameterMm(e.getConductorAreaMm2());
      double dCore = dCond + 2.0 * e.getInsulationThicknessMm();
      int n = Math.max(1, e.getNumberOfCores());
      double[] cores = new double[n];
      Arrays.fill(cores, dCore);
      double bundle = UmbilicalStructuralCalculator.bundleDiameterMm(cores);
      e.resolvedOuterDiameterMm = n == 1 ? dCore : bundle + 2.0 * CABLE_SHEATH_MM;
      double od = e.getOuterDiameterMm();
      double copper = n * e.getConductorAreaMm2() * 1.0e-6 * COPPER_DENSITY;
      double insulation = n * Math.PI / 4.0 * (dCore * dCore - dCond * dCond) * 1.0e-6 * INSULATION_DENSITY;
      double inner = n == 1 ? dCore : od - 2.0 * CABLE_SHEATH_MM;
      double filler = Math.max(0.0, Math.PI / 4.0 * (inner * inner - n * dCore * dCore)) * 1.0e-6 * POLYMER_DENSITY;
      double sheath = n == 1 ? 0.0 : Math.PI / 4.0 * (od * od - inner * inner) * 1.0e-6 * POLYMER_DENSITY;
      e.emptyMassKgPerM = copper + insulation + filler + sheath;
      e.fluidMassKgPerM = 0.0;
    } else {
      e.resolvedWallThicknessMm = FIBER_TUBE_WALL_MM;
      e.resolvedOuterDiameterMm = FIBER_TUBE_OD_MM;
      double od = e.getOuterDiameterMm();
      double id = od - 2.0 * FIBER_TUBE_WALL_MM;
      e.emptyMassKgPerM = Math.PI / 4.0 * (od * od - id * id) * 1.0e-6 * TUBE_STEEL_DENSITY;
      e.fluidMassKgPerM = 0.0;
    }
  }

  /**
   * Lower-case material key for density lookups.
   *
   * @param e element
   * @return pvdf, nylon or steel
   */
  private String materialKey(UmbilicalElement e) {
    String m = e.getMaterial() == null ? "" : e.getMaterial().toLowerCase();
    return m.contains("pvdf") ? "pvdf" : (e.isSteelTube() ? "steel" : "nylon");
  }

  /**
   * Required wall thickness of a tube or hose for internal design pressure and, for steel tubes, external hydrostatic
   * collapse of an empty tube.
   *
   * @param e fluid line element
   * @return wall thickness in mm
   */
  private double requiredWallThicknessMm(UmbilicalElement e) {
    double id = e.getInnerDiameterMm();
    if (!e.isSteelTube()) {
      return Math.max(2.0, 0.25 * id);
    }
    double burst = UmbilicalStructuralCalculator.burstWallThicknessMm(e.getDesignPressureBar() / 10.0, id, tubeSmysMPa,
        tubeUsageFactor);
    double collapse = UmbilicalStructuralCalculator.collapseWallThicknessMm(
        UmbilicalStructuralCalculator.hydrostaticPressureMPa(waterDepth), id, tubeSmysMPa,
        tubeYoungsModulusGPa * 1000.0, tubeOvality, collapseSafetyFactor);
    return Math.max(minimumTubeWallMm, Math.max(burst, collapse));
  }

  /**
   * Update element geometry, overall diameter and the weights in air and in seawater.
   */
  private void updateGeometryAndWeights() {
    if (elements.isEmpty()) {
      return;
    }
    double[] ods = new double[elements.size()];
    double elementArea = 0.0;
    double elementEmpty = 0.0;
    double elementFluid = 0.0;
    for (int i = 0; i < ods.length; i++) {
      UmbilicalElement e = elements.get(i);
      resolveElementGeometry(e);
      ods[i] = e.getOuterDiameterMm();
      elementArea += Math.PI * ods[i] * ods[i] / 4.0;
      elementEmpty += e.getEmptyMassKgPerM();
      elementFluid += e.getFluidMassKgPerM();
    }
    double bundle = UmbilicalStructuralCalculator.bundleDiameterMm(ods);
    bundleDiameterMm = bundle;
    double fillerMass = Math.max(0.0, Math.PI / 4.0 * bundle * bundle - elementArea) * 1.0e-6 * POLYMER_DENSITY;
    double core = bundle + 2.0 * innerSheathThicknessMm;
    double innerSheathMass = Math.PI / 4.0 * (core * core - bundle * bundle) * 1.0e-6 * POLYMER_DENSITY;

    int layers = hasArmorWires ? armorLayerCount : 0;
    double armored = core + 2.0 * layers * armorWireDiameterMm;
    double armorMass = layers == 0 ? 0.0
        : UmbilicalStructuralCalculator.armorMassPerMeter(core, armorWireDiameterMm, armorLayAngleDeg, layers, 7850.0);
    overallDiameterMm = armored + 2.0 * outerSheathThicknessMm;
    double outerSheathMass = Math.PI / 4.0 * (overallDiameterMm * overallDiameterMm - armored * armored) * 1.0e-6
        * POLYMER_DENSITY;

    dryWeightPerMeter = elementEmpty + fillerMass + innerSheathMass + armorMass + outerSheathMass;
    filledWeightPerMeter = dryWeightPerMeter + elementFluid;
    double displaced = Math.PI / 4.0 * overallDiameterMm * overallDiameterMm * 1.0e-6
        * UmbilicalStructuralCalculator.SEAWATER_DENSITY;
    submergedWeightPerMeter = filledWeightPerMeter - displaced;
  }

  // ============ Line hydraulics ============

  /**
   * Set the design flow rate of a fluid line.
   *
   * @param name line name
   * @param rate flow rate
   * @param unit rate unit: m3/hr, m3/h, m3/s, l/min or l/hr
   */
  public void setInjectionRate(String name, double rate, String unit) {
    findElement(name).setDesignFlowRateM3PerHr(toM3PerHr(rate, unit));
  }

  /**
   * Set the MEG injection rate on the first line whose name contains MEG.
   *
   * @param rate flow rate
   * @param unit rate unit, see {@link #setInjectionRate(String, double, String)}
   */
  public void setMEGInjectionRate(double rate, String unit) {
    setInjectionRate("MEG", rate, unit);
  }

  /**
   * Set the methanol injection rate on the first line whose name contains methanol.
   *
   * @param rate flow rate
   * @param unit rate unit, see {@link #setInjectionRate(String, double, String)}
   */
  public void setMethanolInjectionRate(double rate, String unit) {
    setInjectionRate("Methanol", rate, unit);
  }

  /**
   * Convert a flow rate to m3/hr.
   *
   * @param rate flow rate
   * @param unit unit
   * @return rate in m3/hr
   */
  private static double toM3PerHr(double rate, String unit) {
    String u = unit == null ? "" : unit.toLowerCase().replace(" ", "");
    if ("m3/hr".equals(u) || "m3/h".equals(u)) {
      return rate;
    } else if ("m3/s".equals(u)) {
      return rate * 3600.0;
    } else if ("l/min".equals(u)) {
      return rate * 0.06;
    } else if ("l/hr".equals(u) || "l/h".equals(u)) {
      return rate / 1000.0;
    }
    throw new IllegalArgumentException("Unsupported flow unit: " + unit);
  }

  /**
   * Steady hydraulics of a fluid line at its design flow rate, with the water depth as elevation difference between
   * topside and seabed and the seawater temperature as fluid temperature.
   *
   * @param name line name
   * @return line result with friction drop, hydrostatic head and regime
   * @throws IllegalArgumentException if the element is not a fluid line
   */
  public UmbilicalHydraulicsCalculator.LineResult getLineHydraulics(String name) {
    UmbilicalElement e = findElement(name);
    if (!e.isFluidLine()) {
      throw new IllegalArgumentException("Element '" + name + "' is not a fluid line");
    }
    return UmbilicalHydraulicsCalculator.calculate(e.getFluid(), e.getInnerDiameterMm(), length,
        e.getDesignFlowRateM3PerHr() / 3600.0, seawaterTemperatureC + 273.15,
        UmbilicalHydraulicsCalculator.DEFAULT_ROUGHNESS_MM, waterDepth);
  }

  /**
   * Friction pressure drop of a fluid line at its design flow rate.
   *
   * @param name line name
   * @return pressure drop in bar
   */
  public double calculatePressureDrop(String name) {
    return getLineHydraulics(name).getFrictionDropBar();
  }

  /**
   * Largest flow a fluid line can carry within a friction pressure drop budget.
   *
   * @param name line name
   * @param frictionDropBar allowed friction drop in bar
   * @return flow rate in m3/hr
   */
  public double calculateMaxFlowM3PerHr(String name, double frictionDropBar) {
    UmbilicalElement e = findElement(name);
    return 3600.0 * UmbilicalHydraulicsCalculator.flowForFrictionDrop(e.getFluid(), e.getInnerDiameterMm(), length,
        frictionDropBar, seawaterTemperatureC + 273.15, UmbilicalHydraulicsCalculator.DEFAULT_ROUGHNESS_MM);
  }

  /**
   * Response of the far end of a closed fluid line to a pressure step at the topside end.
   *
   * @param name line name
   * @param fraction fraction of the step to reach, for example 0.9
   * @return response result including the acoustic transit time
   */
  public UmbilicalHydraulicsCalculator.ResponseResult calculateLineResponse(String name, double fraction) {
    UmbilicalElement e = findElement(name);
    resolveElementGeometry(e);
    double modulus = e.isSteelTube() ? tubeYoungsModulusGPa * 1.0e9 : HOSE_MODULUS_PA;
    return UmbilicalHydraulicsCalculator.stepResponse(e.getFluid(), e.getInnerDiameterMm(), e.getWallThicknessMm(),
        modulus, length, seawaterTemperatureC + 273.15, fraction);
  }

  // ============ Power transmission ============

  /**
   * Get the power cable: the electrical element with the highest voltage rating.
   *
   * @return power cable
   * @throws IllegalStateException if there is no electrical element
   */
  public UmbilicalElement getPowerCable() {
    UmbilicalElement best = null;
    for (UmbilicalElement e : elements) {
      if ("electrical".equals(e.getElementType()) && (best == null || e.getVoltageRating() > best.getVoltageRating())) {
        best = e;
      }
    }
    if (best == null) {
      throw new IllegalStateException("Umbilical has no electrical cable");
    }
    return best;
  }

  /**
   * Solve the power cable for the configured load.
   *
   * @return power transmission result (voltage drop, losses, charging current)
   * @throws IllegalStateException if the power cable has fewer than three cores
   */
  public UmbilicalElectricalCalculator.PowerResult calculatePowerTransmission() {
    return calculatePowerTransmission(length);
  }

  /**
   * Solve the power cable for the configured load at another cable length, for step-out sweeps.
   *
   * @param cableLengthM cable length in m
   * @return power transmission result
   * @throws IllegalStateException if the power cable has fewer than three cores
   */
  public UmbilicalElectricalCalculator.PowerResult calculatePowerTransmission(double cableLengthM) {
    UmbilicalElement cable = getPowerCable();
    if (cable.getNumberOfCores() < 3) {
      throw new IllegalStateException(
          "Three-phase calculation needs at least three cores in '" + cable.getName() + "'");
    }
    return UmbilicalElectricalCalculator.threePhase(cable.getConductorAreaMm2(), cable.getInsulationThicknessMm(),
        insulationPermittivity, cable.getVoltageRating(), cableLengthM, frequencyHz, conductorTemperatureC, powerLoadMW,
        powerFactor);
  }

  /**
   * Voltage drop of the power cable at the configured load.
   *
   * @return voltage drop in percent of the receiving end voltage
   */
  public double calculateVoltageDrop() {
    return calculatePowerTransmission().getVoltageDropPercent();
  }

  /**
   * Active power loss of the power cable at the configured load.
   *
   * @return power loss in MW
   */
  public double calculatePowerLoss() {
    return calculatePowerTransmission().getLossMW();
  }

  /**
   * Voltage drop of a two-wire control or signal circuit.
   *
   * @param name cable name
   * @param currentA current in A
   * @return voltage drop in V over the out and return conductor
   */
  public double calculateControlLoopVoltageDrop(String name, double currentA) {
    UmbilicalElement cable = findElement(name);
    return UmbilicalElectricalCalculator.dcLoopVoltageDrop(cable.getConductorAreaMm2(), length, currentA,
        conductorTemperatureC);
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  @Override
  public UmbilicalMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new UmbilicalMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  @Override
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
   * Get seawater (seabed) temperature.
   *
   * @return temperature in C
   */
  public double getSeawaterTemperatureC() {
    return seawaterTemperatureC;
  }

  /**
   * Set seawater (seabed) temperature, used as fluid temperature in the line hydraulics.
   *
   * @param seawaterTemperatureC temperature in C
   */
  public void setSeawaterTemperatureC(double seawaterTemperatureC) {
    this.seawaterTemperatureC = seawaterTemperatureC;
  }

  /**
   * Check if the umbilical is in dynamic service.
   *
   * @return true for a dynamic (riser) section
   */
  public boolean isDynamicService() {
    return dynamicService;
  }

  /**
   * Set whether the umbilical is in dynamic service.
   *
   * @param dynamicService true for a dynamic (riser) section
   */
  public void setDynamicService(boolean dynamicService) {
    this.dynamicService = dynamicService;
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
   * Get the number of armour layers.
   *
   * @return number of layers
   */
  public int getArmorLayerCount() {
    return armorLayerCount;
  }

  /**
   * Set the number of armour layers (the mechanical design sets this to meet the tension).
   *
   * @param armorLayerCount number of layers
   */
  public void setArmorLayerCount(int armorLayerCount) {
    this.armorLayerCount = Math.max(0, armorLayerCount);
  }

  /**
   * Get the armour wire diameter.
   *
   * @return diameter in mm
   */
  public double getArmorWireDiameterMm() {
    return armorWireDiameterMm;
  }

  /**
   * Set the armour wire diameter.
   *
   * @param armorWireDiameterMm diameter in mm
   */
  public void setArmorWireDiameterMm(double armorWireDiameterMm) {
    this.armorWireDiameterMm = armorWireDiameterMm;
  }

  /**
   * Get the armour lay angle from the umbilical axis.
   *
   * @return lay angle in degrees
   */
  public double getArmorLayAngleDeg() {
    return armorLayAngleDeg;
  }

  /**
   * Set the armour lay angle from the umbilical axis.
   *
   * @param armorLayAngleDeg lay angle in degrees
   */
  public void setArmorLayAngleDeg(double armorLayAngleDeg) {
    this.armorLayAngleDeg = armorLayAngleDeg;
  }

  /**
   * Get the allowable armour wire stress (tensile strength times allowable fraction).
   *
   * @return stress in MPa
   */
  public double getArmorAllowableStressMPa() {
    return armorWireTensileStrengthMPa * armorAllowableFraction;
  }

  /**
   * Set the armour wire tensile strength and allowable fraction.
   *
   * @param tensileStrengthMPa ultimate tensile strength in MPa
   * @param allowableFraction allowable fraction of the tensile strength
   */
  public void setArmorWireStrength(double tensileStrengthMPa, double allowableFraction) {
    this.armorWireTensileStrengthMPa = tensileStrengthMPa;
    this.armorAllowableFraction = allowableFraction;
  }

  /**
   * Set the tube material and design basis.
   *
   * @param smysMPa specified minimum yield strength in MPa
   * @param youngsModulusGPa Young's modulus in GPa
   * @param usageFactor allowable fraction of SMYS for the design-pressure hoop stress
   * @param collapseSafetyFactor collapse safety factor
   * @param ovality initial ovality
   */
  public void setTubeDesignBasis(double smysMPa, double youngsModulusGPa, double usageFactor,
      double collapseSafetyFactor, double ovality) {
    this.tubeSmysMPa = smysMPa;
    this.tubeYoungsModulusGPa = youngsModulusGPa;
    this.tubeUsageFactor = usageFactor;
    this.collapseSafetyFactor = collapseSafetyFactor;
    this.tubeOvality = ovality;
  }

  /**
   * Get the tube SMYS.
   *
   * @return SMYS in MPa
   */
  public double getTubeSmysMPa() {
    return tubeSmysMPa;
  }

  /**
   * Get the tube Young's modulus.
   *
   * @return modulus in GPa
   */
  public double getTubeYoungsModulusGPa() {
    return tubeYoungsModulusGPa;
  }

  /**
   * Get the diameter of the bundled functional elements (inside the inner sheath).
   *
   * @return bundle diameter in mm, 0 before the first {@link #run(UUID)}
   */
  public double getBundleDiameterMm() {
    return bundleDiameterMm;
  }

  /**
   * Get the inner sheath thickness.
   *
   * @return thickness in mm
   */
  public double getInnerSheathThicknessMm() {
    return innerSheathThicknessMm;
  }

  /**
   * Get the allowed friction drop at design flow as a fraction of the line design pressure.
   *
   * @return fraction, default 0.5
   */
  public double getMaxFrictionDropFraction() {
    return maxFrictionDropFraction;
  }

  /**
   * Set the allowed friction drop at design flow as a fraction of the line design pressure.
   *
   * @param maxFrictionDropFraction fraction between 0 and 1
   */
  public void setMaxFrictionDropFraction(double maxFrictionDropFraction) {
    this.maxFrictionDropFraction = maxFrictionDropFraction;
  }

  /**
   * Get the allowable fraction of SMYS for the design-pressure hoop stress.
   *
   * @return usage factor
   */
  public double getTubeUsageFactor() {
    return tubeUsageFactor;
  }

  /**
   * Get the tube collapse safety factor.
   *
   * @return safety factor
   */
  public double getCollapseSafetyFactor() {
    return collapseSafetyFactor;
  }

  /**
   * Get the tube initial ovality.
   *
   * @return ovality
   */
  public double getTubeOvality() {
    return tubeOvality;
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
   * Get dry weight per meter (empty, in air).
   *
   * @return dry weight in kg/m
   */
  public double getDryWeightPerMeter() {
    return dryWeightPerMeter;
  }

  /**
   * Get weight per meter in air with the line fluids.
   *
   * @return filled weight in kg/m
   */
  public double getFilledWeightPerMeter() {
    return filledWeightPerMeter;
  }

  /**
   * Get submerged weight per meter of the fluid-filled umbilical.
   *
   * @return submerged weight in kg/m, negative if buoyant
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
   * Get the horizontal distance from the top to the touchdown point used for the catenary.
   *
   * @return distance in m, half the water depth unless set
   */
  public double getTouchdownOffsetM() {
    return Double.isNaN(touchdownOffsetM) ? 0.5 * waterDepth : touchdownOffsetM;
  }

  /**
   * Set the horizontal distance from the top to the touchdown point (layback).
   *
   * @param touchdownOffsetM distance in m
   */
  public void setTouchdownOffsetM(double touchdownOffsetM) {
    this.touchdownOffsetM = touchdownOffsetM;
  }

  /**
   * Get the tension amplification factor.
   *
   * @return factor applied to the static top tension
   */
  public double getTensionAmplificationFactor() {
    return tensionAmplificationFactor;
  }

  /**
   * Set the tension amplification factor for installation and dynamic effects.
   *
   * @param tensionAmplificationFactor factor applied to the static top tension
   */
  public void setTensionAmplificationFactor(double tensionAmplificationFactor) {
    this.tensionAmplificationFactor = tensionAmplificationFactor;
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

  /**
   * Set the power transmission load.
   *
   * @param powerLoadMW active power at the receiving end in MW
   */
  public void setPowerLoad(double powerLoadMW) {
    this.powerLoadMW = powerLoadMW;
  }

  /**
   * Get the power transmission load.
   *
   * @return active power at the receiving end in MW
   */
  public double getPowerLoad() {
    return powerLoadMW;
  }

  /**
   * Set the load power factor.
   *
   * @param powerFactor lagging power factor, 0 to 1
   */
  public void setPowerFactor(double powerFactor) {
    this.powerFactor = powerFactor;
  }

  /**
   * Set the system frequency.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Set the conductor temperature used for resistance.
   *
   * @param conductorTemperatureC temperature in C
   */
  public void setConductorTemperatureC(double conductorTemperatureC) {
    this.conductorTemperatureC = conductorTemperatureC;
  }
}
