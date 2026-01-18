package neqsim.process.design.template;

import neqsim.process.design.ProcessBasis;
import neqsim.process.design.ProcessTemplate;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Template for creating amine-based CO2 capture systems.
 *
 * <p>
 * This template creates a standard amine-based CO2 absorption unit consisting of an absorber
 * column, solvent regeneration system, and heat integration equipment. Supports various amine
 * solvents including MEA, MDEA, and proprietary blends.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Amine absorber with configurable stages and amine type</li>
 * <li>Rich amine flash drum for hydrocarbon recovery</li>
 * <li>Lean-rich heat exchanger for heat recovery</li>
 * <li>Regenerator column with reboiler</li>
 * <li>Lean amine cooler and pump</li>
 * <li>CO2 compression option</li>
 * </ul>
 *
 * <h2>Supported Amine Types</h2>
 * <ul>
 * <li>MEA (Monoethanolamine) - 15-30 wt%</li>
 * <li>DEA (Diethanolamine) - 25-35 wt%</li>
 * <li>MDEA (Methyldiethanolamine) - 35-50 wt%</li>
 * <li>MDEA+PZ (Activated MDEA) - Enhanced kinetics</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * ProcessBasis basis = new ProcessBasis();
 * basis.setFeedFluid(flueGasFluid);
 * basis.setParameter("amineType", "MDEA");
 * basis.setParameter("amineConcentration", 0.45); // 45 wt%
 * basis.setParameter("co2RemovalTarget", 0.90); // 90% removal
 * 
 * CO2CaptureTemplate template = new CO2CaptureTemplate();
 * ProcessSystem capture = template.create(basis);
 * capture.run();
 * }</pre>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>GPSA Engineering Data Book - Chapter 21</li>
 * <li>API RP 945 - Corrosion in amine units</li>
 * <li>NACE MR0175/ISO 15156 - Materials selection</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CO2CaptureTemplate implements ProcessTemplate {

  /** Default number of theoretical stages in absorber. */
  private static final int DEFAULT_ABSORBER_STAGES = 20;

  /** Default number of theoretical stages in regenerator. */
  private static final int DEFAULT_REGENERATOR_STAGES = 12;

  /** Default reboiler temperature in Celsius. */
  private static final double DEFAULT_REBOILER_TEMP_C = 120.0;

  /** Default amine concentration (mass fraction). */
  private static final double DEFAULT_AMINE_CONCENTRATION = 0.30;

  /** Default lean amine temperature in Celsius. */
  private static final double DEFAULT_LEAN_AMINE_TEMP_C = 40.0;

  /** Default CO2 loading in lean amine (mol CO2/mol amine). */
  private static final double DEFAULT_LEAN_LOADING = 0.20;

  /** Default approach temperature for heat exchangers in K. */
  private static final double DEFAULT_APPROACH_TEMP_K = 10.0;

  /**
   * Amine type enumeration.
   */
  public enum AmineType {
    /** Monoethanolamine. */
    MEA("MEA", 0.30, 118.0, 0.45),
    /** Diethanolamine. */
    DEA("DEA", 0.35, 115.0, 0.50),
    /** Methyldiethanolamine. */
    MDEA("MDEA", 0.45, 120.0, 0.55),
    /** Activated MDEA with piperazine. */
    MDEA_PZ("MDEA+PZ", 0.40, 118.0, 0.60);

    private final String name;
    private final double typicalConcentration;
    private final double reboilerTemp;
    private final double maxRichLoading;

    AmineType(String name, double typicalConcentration, double reboilerTemp,
        double maxRichLoading) {
      this.name = name;
      this.typicalConcentration = typicalConcentration;
      this.reboilerTemp = reboilerTemp;
      this.maxRichLoading = maxRichLoading;
    }

    /**
     * Gets the amine name.
     * 
     * @return amine name
     */
    public String getAmineName() {
      return name;
    }

    /**
     * Gets typical amine concentration (mass fraction).
     * 
     * @return typical concentration
     */
    public double getTypicalConcentration() {
      return typicalConcentration;
    }

    /**
     * Gets recommended reboiler temperature in Celsius.
     * 
     * @return reboiler temperature
     */
    public double getReboilerTemp() {
      return reboilerTemp;
    }

    /**
     * Gets maximum rich loading (mol CO2/mol amine).
     * 
     * @return maximum rich loading
     */
    public double getMaxRichLoading() {
      return maxRichLoading;
    }
  }

  /** Selected amine type. */
  private AmineType amineType = AmineType.MDEA;

  /**
   * Creates a new CO2CaptureTemplate with default MDEA amine.
   */
  public CO2CaptureTemplate() {
    // Default constructor
  }

  /**
   * Creates a new CO2CaptureTemplate with specified amine type.
   *
   * @param amineType the amine type to use
   */
  public CO2CaptureTemplate(AmineType amineType) {
    this.amineType = amineType;
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem create(ProcessBasis basis) {
    ProcessSystem process = new ProcessSystem();

    // Get parameters from basis
    SystemInterface feedFluid = basis.getFeedFluid();
    if (feedFluid == null) {
      throw new IllegalArgumentException("ProcessBasis must have a feed fluid defined");
    }

    double feedPressure = basis.getFeedPressure();
    if (Double.isNaN(feedPressure) || feedPressure <= 0) {
      feedPressure = 1.5; // Default 1.5 bara (near atmospheric for flue gas)
    }

    double feedTemperature = basis.getFeedTemperature();
    if (Double.isNaN(feedTemperature) || feedTemperature <= 0) {
      feedTemperature = 40.0 + 273.15; // Default 40°C
    }

    // Get amine parameters
    String amineTypeStr = basis.getParameterString("amineType", amineType.getAmineName());
    amineType = parseAmineType(amineTypeStr);

    double amineConcentration =
        basis.getParameter("amineConcentration", amineType.getTypicalConcentration());
    double reboilerTemp = basis.getParameter("reboilerTemperature", amineType.getReboilerTemp());
    double leanAmineTemp = basis.getParameter("leanAmineTemperature", DEFAULT_LEAN_AMINE_TEMP_C);
    double co2RemovalTarget = basis.getParameter("co2RemovalTarget", 0.90);
    int absorberStages = (int) basis.getParameter("absorberStages", DEFAULT_ABSORBER_STAGES);
    int regeneratorStages =
        (int) basis.getParameter("regeneratorStages", DEFAULT_REGENERATOR_STAGES);

    // Get flow rate
    double gasFlowRate = basis.getFeedFlowRate();
    if (gasFlowRate <= 0) {
      gasFlowRate = 100000.0; // Default 100000 kg/hr
    }

    // Estimate amine circulation rate
    double amineRate =
        estimateAmineCirculationRate(feedFluid, gasFlowRate, amineConcentration, co2RemovalTarget);

    // Create feed gas stream
    SystemInterface feedGas = feedFluid.clone();
    feedGas.setPressure(feedPressure, "bara");
    feedGas.setTemperature(feedTemperature, "K");
    Stream feedGasStream = new Stream("Feed Gas", feedGas);
    feedGasStream.setFlowRate(gasFlowRate, "kg/hr");
    process.add(feedGasStream);

    // Create lean amine stream
    SystemInterface leanAmine = createAmineFluid(amineType, amineConcentration, feedPressure);
    leanAmine.setTemperature(leanAmineTemp + 273.15, "K");
    Stream leanAmineStream = new Stream("Lean Amine", leanAmine);
    leanAmineStream.setFlowRate(amineRate, "kg/hr");
    process.add(leanAmineStream);

    // Absorber column (using SimpleTEGAbsorber as base)
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("CO2 Absorber");
    absorber.addGasInStream(feedGasStream);
    absorber.addSolventInStream(leanAmineStream);
    absorber.setNumberOfStages(absorberStages);
    process.add(absorber);

    // Rich amine flash drum
    ThrottlingValve richAmineValve =
        new ThrottlingValve("Rich Amine Valve", absorber.getSolventOutStream());
    richAmineValve.setOutletPressure(3.0); // Flash at 3 bara
    process.add(richAmineValve);

    Separator flashDrum = new Separator("Rich Amine Flash Drum", richAmineValve.getOutletStream());
    process.add(flashDrum);

    // Lean-rich heat exchanger
    HeatExchanger leanRichHX = new HeatExchanger("Lean-Rich HX");
    leanRichHX.setFeedStream(0, flashDrum.getLiquidOutStream());
    // Hot side connected later to regenerated amine
    process.add(leanRichHX);

    // Regenerator reboiler (simplified as heater + separator)
    Heater reboiler = new Heater("Regenerator Reboiler", leanRichHX.getOutStream(0));
    reboiler.setOutTemperature(reboilerTemp + 273.15);
    process.add(reboiler);

    Separator regenerator = new Separator("Regenerator", reboiler.getOutletStream());
    process.add(regenerator);

    // Connect hot side of lean-rich HX
    leanRichHX.setFeedStream(1, regenerator.getLiquidOutStream());

    // Lean amine pump
    Pump aminePump = new Pump("Lean Amine Pump", leanRichHX.getOutStream(1));
    aminePump.setOutletPressure(feedPressure + 1.0);
    process.add(aminePump);

    // Lean amine cooler
    Cooler amineCooler = new Cooler("Lean Amine Cooler", aminePump.getOutletStream());
    amineCooler.setOutTemperature(leanAmineTemp + 273.15);
    process.add(amineCooler);

    // CO2 product stream from regenerator overhead
    // Could add condenser and compression here

    return process;
  }

  /**
   * Creates an amine solvent fluid.
   *
   * @param type amine type
   * @param concentration amine mass fraction
   * @param pressure system pressure in bara
   * @return amine fluid system
   */
  private SystemInterface createAmineFluid(AmineType type, double concentration, double pressure) {
    SystemInterface amineFluid = new SystemSrkCPAstatoil(273.15 + 40.0, pressure);

    switch (type) {
      case MEA:
        amineFluid.addComponent("MEA", concentration);
        break;
      case DEA:
        amineFluid.addComponent("DEA", concentration);
        break;
      case MDEA:
      case MDEA_PZ:
        amineFluid.addComponent("MDEA", concentration);
        if (type == AmineType.MDEA_PZ) {
          // Add piperazine for activated MDEA
          amineFluid.addComponent("piperazine", 0.05);
        }
        break;
      default:
        amineFluid.addComponent("MDEA", concentration);
    }

    amineFluid.addComponent("water", 1.0 - concentration);
    amineFluid.addComponent("CO2", 0.001); // Small amount to establish equilibrium
    amineFluid.setMixingRule(10); // CPA mixing rule
    amineFluid.setMultiPhaseCheck(true);
    return amineFluid;
  }

  /**
   * Parses amine type from string.
   *
   * @param amineStr amine type string
   * @return AmineType enum value
   */
  private AmineType parseAmineType(String amineStr) {
    if (amineStr == null) {
      return AmineType.MDEA;
    }
    String upper = amineStr.toUpperCase().trim();
    for (AmineType type : AmineType.values()) {
      if (type.getAmineName().equalsIgnoreCase(upper)) {
        return type;
      }
    }
    return AmineType.MDEA;
  }

  /**
   * Estimates amine circulation rate for given CO2 removal.
   *
   * @param feedFluid feed fluid system
   * @param gasFlowRate gas flow rate in kg/hr
   * @param amineConcentration amine mass fraction
   * @param removalTarget CO2 removal fraction (0-1)
   * @return estimated amine circulation rate in kg/hr
   */
  private double estimateAmineCirculationRate(SystemInterface feedFluid, double gasFlowRate,
      double amineConcentration, double removalTarget) {
    // Estimate CO2 in feed
    double co2Fraction = 0.15; // Default 15%
    for (int i = 0; i < feedFluid.getNumberOfComponents(); i++) {
      String name = feedFluid.getComponent(i).getName().toLowerCase();
      if (name.equals("co2") || name.contains("carbon dioxide")) {
        co2Fraction = feedFluid.getComponent(i).getz();
        break;
      }
    }

    // CO2 to be removed (kg/hr)
    double co2Removed = gasFlowRate * co2Fraction * removalTarget;

    // Amine requirement based on loading
    double richLoading = amineType.getMaxRichLoading() * 0.9; // Target 90% of max
    double leanLoading = DEFAULT_LEAN_LOADING;
    double deltaLoading = richLoading - leanLoading;

    // Mol amine per mol CO2
    double molAminePerMolCO2 = 1.0 / deltaLoading;

    // Amine molecular weight (approximate)
    double amineMW = 119.0; // MDEA MW
    if (amineType == AmineType.MEA) {
      amineMW = 61.0;
    } else if (amineType == AmineType.DEA) {
      amineMW = 105.0;
    }

    // Amine rate (kg/hr)
    double co2MW = 44.0;
    double molCO2 = co2Removed / co2MW;
    double molAmine = molCO2 * molAminePerMolCO2;
    double pureAmineRate = molAmine * amineMW;

    // Total solution rate
    return pureAmineRate / amineConcentration;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isApplicable(SystemInterface fluid) {
    if (fluid == null) {
      return false;
    }

    // Check if fluid contains CO2
    boolean hasCO2 = false;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getName().toLowerCase();
      if (name.equals("co2") || name.contains("carbon dioxide")) {
        hasCO2 = true;
        break;
      }
    }

    return hasCO2;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getRequiredEquipmentTypes() {
    return new String[] {"SimpleTEGAbsorber", "Separator", "HeatExchanger", "Heater", "Pump",
        "Cooler", "ThrottlingValve"};
  }

  /** {@inheritDoc} */
  @Override
  public String[] getExpectedOutputs() {
    return new String[] {"Treated Gas - CO2-depleted gas stream",
        "CO2 Product - High purity CO2 from regenerator overhead",
        "Flash Gas - Hydrocarbon-rich gas from flash drum",
        "Heat Duty - Reboiler heat requirement"};
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "Amine-Based CO2 Capture";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Amine-based CO2 capture system with absorber, flash drum, "
        + "lean-rich heat exchanger, and regeneration column. "
        + "Supports MEA, DEA, MDEA, and activated MDEA solvents. "
        + "Suitable for flue gas treatment and natural gas sweetening.";
  }

  /**
   * Calculates specific reboiler duty for amine regeneration.
   *
   * @param amineType type of amine
   * @param richLoading rich amine loading (mol CO2/mol amine)
   * @param leanLoading lean amine loading (mol CO2/mol amine)
   * @return specific reboiler duty in GJ/ton CO2
   */
  public static double calculateSpecificReboilerDuty(AmineType amineType, double richLoading,
      double leanLoading) {
    // Typical specific duties (GJ/ton CO2):
    // MEA: 3.5-4.0, DEA: 3.0-3.5, MDEA: 2.5-3.0, MDEA+PZ: 2.3-2.8
    double baseduty;
    switch (amineType) {
      case MEA:
        baseduty = 3.8;
        break;
      case DEA:
        baseduty = 3.2;
        break;
      case MDEA:
        baseduty = 2.8;
        break;
      case MDEA_PZ:
        baseduty = 2.5;
        break;
      default:
        baseduty = 3.0;
    }

    // Adjust for loading difference
    double deltaLoading = richLoading - leanLoading;
    double normalDelta = amineType.getMaxRichLoading() - DEFAULT_LEAN_LOADING;
    double loadingFactor = normalDelta / Math.max(deltaLoading, 0.1);

    return baseduty * loadingFactor;
  }

  /**
   * Estimates amine loss rate.
   *
   * @param amineType type of amine
   * @param gasFlowRate gas flow rate in MMscfd
   * @return amine loss rate in kg/MMscf
   */
  public static double estimateAmineLoss(AmineType amineType, double gasFlowRate) {
    // Typical amine losses (kg/MMscf):
    // Mechanical losses: 0.5-2.0
    // Vapor losses: 0.1-0.5 (higher for MEA)
    // Degradation: 0.2-0.5
    double mechanicalLoss = 1.0;
    double vaporLoss;
    double degradationLoss;

    switch (amineType) {
      case MEA:
        vaporLoss = 0.4;
        degradationLoss = 0.4;
        break;
      case DEA:
        vaporLoss = 0.2;
        degradationLoss = 0.3;
        break;
      case MDEA:
      case MDEA_PZ:
        vaporLoss = 0.1;
        degradationLoss = 0.2;
        break;
      default:
        vaporLoss = 0.2;
        degradationLoss = 0.3;
    }

    return mechanicalLoss + vaporLoss + degradationLoss;
  }
}
