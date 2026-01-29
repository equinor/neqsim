package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.subsea.FlexiblePipeMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Flexible Pipe equipment class.
 *
 * <p>
 * A flexible pipe is an assembly of concentric layers of polymers and metal wires that provides
 * flexibility while maintaining pressure containment. Used for:
 * </p>
 * <ul>
 * <li>Dynamic risers (floating production systems)</li>
 * <li>Static flowlines</li>
 * <li>Jumpers</li>
 * <li>Export lines</li>
 * </ul>
 *
 * <h2>Flexible Pipe Types</h2>
 * <ul>
 * <li><b>Unbonded</b>: Layers can move relative to each other</li>
 * <li><b>Bonded</b>: Layers bonded together (typically for low pressure)</li>
 * </ul>
 *
 * <h2>Layer Structure (Unbonded)</h2>
 * <ol>
 * <li>Carcass (interlocked steel strip)</li>
 * <li>Internal pressure sheath (polymer barrier)</li>
 * <li>Pressure armor (interlocked/helical steel)</li>
 * <li>Tensile armor (helical steel wires)</li>
 * <li>Anti-wear tape</li>
 * <li>Outer sheath (polymer)</li>
 * </ol>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17B - Recommended Practice for Flexible Pipe</li>
 * <li>API Spec 17J - Specification for Unbonded Flexible Pipe</li>
 * <li>API Spec 17K - Specification for Bonded Flexible Pipe</li>
 * <li>DNV-ST-F201 - Dynamic Risers</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create unbonded flexible riser
 * FlexiblePipe riser = new FlexiblePipe("Gas Export Riser", productionStream);
 * riser.setPipeType(FlexiblePipe.PipeType.UNBONDED);
 * riser.setApplication(FlexiblePipe.Application.DYNAMIC_RISER);
 * riser.setInnerDiameterInches(8.0);
 * riser.setLength(1500.0);
 * riser.setDesignPressure(350.0);
 *
 * // Set service
 * riser.setServiceType(FlexiblePipe.ServiceType.GAS);
 * riser.setH2SContent(0.5); // 0.5%
 * riser.setCO2Content(2.0); // 2%
 *
 * riser.run();
 *
 * // Get mechanical design
 * FlexiblePipeMechanicalDesign design = riser.getMechanicalDesign();
 * design.calcDesign();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FlexiblePipeMechanicalDesign
 * @see SubseaJumper
 */
public class FlexiblePipe extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Flexible pipe construction type.
   */
  public enum PipeType {
    /** Unbonded flexible pipe. */
    UNBONDED("Unbonded"),
    /** Bonded flexible pipe. */
    BONDED("Bonded"),
    /** Hybrid (unbonded with composite armor). */
    HYBRID_COMPOSITE("Hybrid Composite");

    private final String displayName;

    PipeType(String displayName) {
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
   * Application type.
   */
  public enum Application {
    /** Dynamic riser application. */
    DYNAMIC_RISER("Dynamic Riser"),
    /** Static riser application. */
    STATIC_RISER("Static Riser"),
    /** Static flowline. */
    FLOWLINE("Flowline"),
    /** Jumper connection. */
    JUMPER("Jumper"),
    /** Export line. */
    EXPORT("Export"),
    /** Injection line. */
    INJECTION("Injection");

    private final String displayName;

    Application(String displayName) {
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
   * Service fluid type.
   */
  public enum ServiceType {
    /** Oil service. */
    OIL("Oil"),
    /** Gas service. */
    GAS("Gas"),
    /** Multiphase (oil, gas, water). */
    MULTIPHASE("Multiphase"),
    /** Water injection. */
    WATER_INJECTION("Water Injection"),
    /** Gas injection. */
    GAS_INJECTION("Gas Injection"),
    /** Gas lift. */
    GAS_LIFT("Gas Lift"),
    /** Chemical injection. */
    CHEMICAL("Chemical");

    private final String displayName;

    ServiceType(String displayName) {
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
   * Riser configuration.
   */
  public enum RiserConfiguration {
    /** Free hanging catenary. */
    FREE_HANGING("Free Hanging"),
    /** Lazy wave with buoyancy. */
    LAZY_WAVE("Lazy Wave"),
    /** Steep wave with buoyancy. */
    STEEP_WAVE("Steep Wave"),
    /** Lazy S with mid-water arch. */
    LAZY_S("Lazy S"),
    /** Steep S with subsea buoy. */
    STEEP_S("Steep S"),
    /** Pliant wave. */
    PLIANT_WAVE("Pliant Wave");

    private final String displayName;

    RiserConfiguration(String displayName) {
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
  /** Pipe type. */
  private PipeType pipeType = PipeType.UNBONDED;

  /** Application type. */
  private Application application = Application.DYNAMIC_RISER;

  /** Service type. */
  private ServiceType serviceType = ServiceType.MULTIPHASE;

  /** Riser configuration. */
  private RiserConfiguration riserConfiguration = RiserConfiguration.LAZY_WAVE;

  // ============ Dimensions ============
  /** Inner diameter in inches. */
  private double innerDiameterInches = 8.0;

  /** Outer diameter in mm. */
  private double outerDiameterMm = 280.0;

  /** Length in meters. */
  private double length = 1500.0;

  // ============ Design Parameters ============
  /** Design pressure in bara. */
  private double designPressure = 350.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 100.0;

  /** Minimum design temperature in Celsius. */
  private double minDesignTemperature = -30.0;

  /** Water depth in meters. */
  private double waterDepth = 450.0;

  // ============ Service Conditions ============
  /** H2S content in %. */
  private double h2sContentPercent = 0.0;

  /** CO2 content in %. */
  private double co2ContentPercent = 0.0;

  /** Whether sour service. */
  private boolean sourService = false;

  /** Maximum sand content in ppmw. */
  private double sandContentPpmw = 50.0;

  // ============ Layer Configuration ============
  /** Whether has carcass. */
  private boolean hasCarcass = true;

  /** Carcass material. */
  private String carcassMaterial = "316L";

  /** Internal sheath material. */
  private String internalSheathMaterial = "PVDF";

  /** Whether has pressure armor. */
  private boolean hasPressureArmor = true;

  /** Pressure armor material. */
  private String pressureArmorMaterial = "Carbon Steel";

  /** Number of tensile armor layers. */
  private int tensileArmorLayers = 2;

  /** Tensile armor material. */
  private String tensileArmorMaterial = "Carbon Steel";

  /** Outer sheath material. */
  private String outerSheathMaterial = "HDPE";

  // ============ End Fittings ============
  /** End fitting type. */
  private String endFittingType = "Integrated";

  /** End fitting flange rating. */
  private String endFittingFlangeRating = "API 6BX";

  // ============ Bend Stiffener ============
  /** Whether has bend stiffener. */
  private boolean hasBendStiffener = true;

  /** Bend stiffener length in meters. */
  private double bendStiffenerLength = 8.0;

  // ============ Mechanical Properties ============
  /** Minimum bend radius in meters. */
  private double minimumBendRadius = 2.5;

  /** Storage minimum bend radius in meters. */
  private double storageBendRadius = 3.5;

  /** Maximum tension capacity in kN. */
  private double maxTensionKN = 1500.0;

  /** Burst pressure in bara. */
  private double burstPressure = 700.0;

  /** Collapse pressure in bara. */
  private double collapsePressure = 200.0;

  // ============ Weight ============
  /** Dry weight per meter in kg/m. */
  private double dryWeightPerMeter = 120.0;

  /** Flooded weight per meter in kg/m. */
  private double floodedWeightPerMeter = 140.0;

  /** Submerged weight per meter in kg/m. */
  private double submergedWeightPerMeter = 85.0;

  /** Mechanical design instance. */
  private FlexiblePipeMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public FlexiblePipe() {
    super("Flexible Pipe");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public FlexiblePipe(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public FlexiblePipe(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Create dynamic riser configuration.
   *
   * @param name riser name
   * @param inStream inlet stream
   * @param configuration riser configuration
   * @return configured flexible pipe
   */
  public static FlexiblePipe createDynamicRiser(String name, StreamInterface inStream,
      RiserConfiguration configuration) {
    FlexiblePipe riser = new FlexiblePipe(name, inStream);
    riser.setApplication(Application.DYNAMIC_RISER);
    riser.setRiserConfiguration(configuration);
    riser.setHasBendStiffener(true);
    return riser;
  }

  /**
   * Create static flowline.
   *
   * @param name flowline name
   * @param inStream inlet stream
   * @param lengthMeters length in meters
   * @return configured flexible pipe
   */
  public static FlexiblePipe createStaticFlowline(String name, StreamInterface inStream,
      double lengthMeters) {
    FlexiblePipe flowline = new FlexiblePipe(name, inStream);
    flowline.setApplication(Application.FLOWLINE);
    flowline.setLength(lengthMeters);
    flowline.setHasBendStiffener(false);
    return flowline;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      return;
    }

    // Clone inlet fluid
    SystemInterface outFluid = inStream.getFluid().clone();

    // Calculate pressure drop
    double pressureDrop = calculatePressureDrop();
    double outPressure = outFluid.getPressure() - pressureDrop;
    if (outPressure > 0) {
      outFluid.setPressure(outPressure);
    }

    // Set outlet
    outStream.setFluid(outFluid);

    // Update sour service flag based on H2S
    sourService = h2sContentPercent > 0.05; // Sour if > 0.05% H2S

    setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop through flexible pipe.
   *
   * @return pressure drop in bar
   */
  private double calculatePressureDrop() {
    double frictionDrop = 0.0;

    if (inStream != null && inStream.getFluid() != null) {
      SystemInterface fluid = inStream.getFluid();
      double density = fluid.getDensity("kg/m3");
      double viscosity = fluid.getViscosity("kg/msec");

      // Get flow rate
      double massFlow = inStream.getFlowRate("kg/sec");
      double innerDiameter = innerDiameterInches * 0.0254; // Convert to meters
      double area = Math.PI * innerDiameter * innerDiameter / 4.0;
      double velocity = massFlow / (density * area);

      // Reynolds number
      double reynolds = density * velocity * innerDiameter / viscosity;

      // Roughness factor (flexible pipe has higher roughness than steel)
      double roughness = 0.0001; // meters, typical for carcass

      // Friction factor using Colebrook-White (simplified)
      double frictionFactor;
      if (reynolds < 2300) {
        frictionFactor = 64 / reynolds;
      } else {
        // Explicit approximation of Colebrook-White
        double A = (roughness / innerDiameter) / 3.7 + 5.74 / Math.pow(reynolds, 0.9);
        frictionFactor = 0.25 / Math.pow(Math.log10(A), 2);
      }

      // Darcy-Weisbach pressure drop
      frictionDrop =
          frictionFactor * (length / innerDiameter) * (density * velocity * velocity / 2) / 100000; // Convert
                                                                                                    // Pa
                                                                                                    // to
                                                                                                    // bar

      // Add elevation change for risers
      if (application == Application.DYNAMIC_RISER || application == Application.STATIC_RISER) {
        // Approximate hydrostatic component
        double elevationDrop = density * 9.81 * waterDepth / 100000; // bar
        frictionDrop += elevationDrop;
      }

      // End fitting losses
      double endFittingDrop = 0.1; // bar
      frictionDrop += 2 * endFittingDrop;
    }

    return Math.max(frictionDrop, 0.1);
  }

  /**
   * Check if suitable for sour service.
   *
   * @return true if suitable for sour service
   */
  public boolean isSuitableForSourService() {
    // Check material compatibility
    if (sourService || h2sContentPercent > 0.05) {
      // Carcass must be suitable
      if (!"316L".equals(carcassMaterial) && !"Duplex".equals(carcassMaterial)) {
        return false;
      }
      // Internal sheath must be suitable
      if (!"PVDF".equals(internalSheathMaterial) && !"PA11".equals(internalSheathMaterial)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  @Override
  public FlexiblePipeMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new FlexiblePipeMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new FlexiblePipeMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get pipe type.
   *
   * @return pipe type
   */
  public PipeType getPipeType() {
    return pipeType;
  }

  /**
   * Set pipe type.
   *
   * @param pipeType pipe type
   */
  public void setPipeType(PipeType pipeType) {
    this.pipeType = pipeType;
  }

  /**
   * Get application.
   *
   * @return application
   */
  public Application getApplication() {
    return application;
  }

  /**
   * Set application.
   *
   * @param application application type
   */
  public void setApplication(Application application) {
    this.application = application;
  }

  /**
   * Get service type.
   *
   * @return service type
   */
  public ServiceType getServiceType() {
    return serviceType;
  }

  /**
   * Set service type.
   *
   * @param serviceType service type
   */
  public void setServiceType(ServiceType serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Get riser configuration.
   *
   * @return riser configuration
   */
  public RiserConfiguration getRiserConfiguration() {
    return riserConfiguration;
  }

  /**
   * Set riser configuration.
   *
   * @param riserConfiguration riser configuration
   */
  public void setRiserConfiguration(RiserConfiguration riserConfiguration) {
    this.riserConfiguration = riserConfiguration;
  }

  /**
   * Get inner diameter.
   *
   * @return inner diameter in inches
   */
  public double getInnerDiameterInches() {
    return innerDiameterInches;
  }

  /**
   * Set inner diameter.
   *
   * @param innerDiameterInches inner diameter in inches
   */
  public void setInnerDiameterInches(double innerDiameterInches) {
    this.innerDiameterInches = innerDiameterInches;
  }

  /**
   * Get outer diameter.
   *
   * @return outer diameter in mm
   */
  public double getOuterDiameterMm() {
    return outerDiameterMm;
  }

  /**
   * Set outer diameter.
   *
   * @param outerDiameterMm outer diameter in mm
   */
  public void setOuterDiameterMm(double outerDiameterMm) {
    this.outerDiameterMm = outerDiameterMm;
  }

  /**
   * Get length.
   *
   * @return length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set length.
   *
   * @param length length in meters
   */
  public void setLength(double length) {
    this.length = length;
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
   * Get H2S content.
   *
   * @return H2S content in %
   */
  public double getH2sContentPercent() {
    return h2sContentPercent;
  }

  /**
   * Set H2S content.
   *
   * @param h2sContentPercent H2S content in %
   */
  public void setH2SContent(double h2sContentPercent) {
    this.h2sContentPercent = h2sContentPercent;
    this.sourService = h2sContentPercent > 0.05;
  }

  /**
   * Get CO2 content.
   *
   * @return CO2 content in %
   */
  public double getCo2ContentPercent() {
    return co2ContentPercent;
  }

  /**
   * Set CO2 content.
   *
   * @param co2ContentPercent CO2 content in %
   */
  public void setCO2Content(double co2ContentPercent) {
    this.co2ContentPercent = co2ContentPercent;
  }

  /**
   * Check if sour service.
   *
   * @return true if sour service
   */
  public boolean isSourService() {
    return sourService;
  }

  /**
   * Check if has carcass.
   *
   * @return true if has carcass
   */
  public boolean hasCarcass() {
    return hasCarcass;
  }

  /**
   * Set whether has carcass.
   *
   * @param hasCarcass true to include carcass
   */
  public void setHasCarcass(boolean hasCarcass) {
    this.hasCarcass = hasCarcass;
  }

  /**
   * Get carcass material.
   *
   * @return carcass material
   */
  public String getCarcassMaterial() {
    return carcassMaterial;
  }

  /**
   * Set carcass material.
   *
   * @param carcassMaterial carcass material (316L, Duplex, etc.)
   */
  public void setCarcassMaterial(String carcassMaterial) {
    this.carcassMaterial = carcassMaterial;
  }

  /**
   * Get internal sheath material.
   *
   * @return internal sheath material
   */
  public String getInternalSheathMaterial() {
    return internalSheathMaterial;
  }

  /**
   * Set internal sheath material.
   *
   * @param internalSheathMaterial internal sheath material (PVDF, PA11, etc.)
   */
  public void setInternalSheathMaterial(String internalSheathMaterial) {
    this.internalSheathMaterial = internalSheathMaterial;
  }

  /**
   * Get tensile armor layers.
   *
   * @return number of tensile armor layers
   */
  public int getTensileArmorLayers() {
    return tensileArmorLayers;
  }

  /**
   * Set tensile armor layers.
   *
   * @param tensileArmorLayers number of layers
   */
  public void setTensileArmorLayers(int tensileArmorLayers) {
    this.tensileArmorLayers = tensileArmorLayers;
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
   * Get maximum tension capacity.
   *
   * @return maximum tension in kN
   */
  public double getMaxTensionKN() {
    return maxTensionKN;
  }

  /**
   * Set maximum tension capacity.
   *
   * @param maxTensionKN maximum tension in kN
   */
  public void setMaxTensionKN(double maxTensionKN) {
    this.maxTensionKN = maxTensionKN;
  }

  /**
   * Get burst pressure.
   *
   * @return burst pressure in bara
   */
  public double getBurstPressure() {
    return burstPressure;
  }

  /**
   * Set burst pressure.
   *
   * @param burstPressure burst pressure in bara
   */
  public void setBurstPressure(double burstPressure) {
    this.burstPressure = burstPressure;
  }

  /**
   * Get collapse pressure.
   *
   * @return collapse pressure in bara
   */
  public double getCollapsePressure() {
    return collapsePressure;
  }

  /**
   * Set collapse pressure.
   *
   * @param collapsePressure collapse pressure in bara
   */
  public void setCollapsePressure(double collapsePressure) {
    this.collapsePressure = collapsePressure;
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
   * Set dry weight per meter.
   *
   * @param dryWeightPerMeter dry weight in kg/m
   */
  public void setDryWeightPerMeter(double dryWeightPerMeter) {
    this.dryWeightPerMeter = dryWeightPerMeter;
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
   * Set submerged weight per meter.
   *
   * @param submergedWeightPerMeter submerged weight in kg/m
   */
  public void setSubmergedWeightPerMeter(double submergedWeightPerMeter) {
    this.submergedWeightPerMeter = submergedWeightPerMeter;
  }

  /**
   * Check if has bend stiffener.
   *
   * @return true if has bend stiffener
   */
  public boolean hasBendStiffener() {
    return hasBendStiffener;
  }

  /**
   * Set whether has bend stiffener.
   *
   * @param hasBendStiffener true to include bend stiffener
   */
  public void setHasBendStiffener(boolean hasBendStiffener) {
    this.hasBendStiffener = hasBendStiffener;
  }

  /**
   * Get bend stiffener length.
   *
   * @return bend stiffener length in meters
   */
  public double getBendStiffenerLength() {
    return bendStiffenerLength;
  }

  /**
   * Set bend stiffener length.
   *
   * @param bendStiffenerLength bend stiffener length in meters
   */
  public void setBendStiffenerLength(double bendStiffenerLength) {
    this.bendStiffenerLength = bendStiffenerLength;
  }

  /**
   * Get end fitting type.
   *
   * @return end fitting type
   */
  public String getEndFittingType() {
    return endFittingType;
  }

  /**
   * Set end fitting type.
   *
   * @param endFittingType end fitting type (Integrated, Reattachable)
   */
  public void setEndFittingType(String endFittingType) {
    this.endFittingType = endFittingType;
  }

  /**
   * Get end fitting flange rating.
   *
   * @return flange rating
   */
  public String getEndFittingFlangeRating() {
    return endFittingFlangeRating;
  }

  /**
   * Set end fitting flange rating.
   *
   * @param endFittingFlangeRating flange rating (API 6BX, etc.)
   */
  public void setEndFittingFlangeRating(String endFittingFlangeRating) {
    this.endFittingFlangeRating = endFittingFlangeRating;
  }

  /**
   * Check if has pressure armor.
   *
   * @return true if has pressure armor
   */
  public boolean hasPressureArmor() {
    return hasPressureArmor;
  }

  /**
   * Set whether has pressure armor.
   *
   * @param hasPressureArmor true to include pressure armor
   */
  public void setHasPressureArmor(boolean hasPressureArmor) {
    this.hasPressureArmor = hasPressureArmor;
  }

  /**
   * Get minimum design temperature.
   *
   * @return minimum design temperature in Celsius
   */
  public double getMinDesignTemperature() {
    return minDesignTemperature;
  }

  /**
   * Set minimum design temperature.
   *
   * @param minDesignTemperature minimum design temperature in Celsius
   */
  public void setMinDesignTemperature(double minDesignTemperature) {
    this.minDesignTemperature = minDesignTemperature;
  }
}
