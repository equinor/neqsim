package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.mechanicaldesign.StudyClass.DeliverableType;
import neqsim.process.mechanicaldesign.designstandards.FireProtectionDesign;
import neqsim.process.mechanicaldesign.designstandards.NoiseAssessment;
import neqsim.process.processmodel.ProcessFlowDiagramExporter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Generates the full engineering deliverables package for a field development study.
 *
 * <p>
 * Given a {@link ProcessSystem} and a {@link StudyClass}, this class generates all required deliverables and bundles
 * them into a single structured output. The deliverable set is controlled by the study class:
 * </p>
 * <ul>
 * <li><b>Class A (FEED/Detail):</b> PFD, thermal utilities, alarm/trip schedule, spare parts, fire scenarios, noise
 * assessment, instrument schedule, IEC 81346 reference designation schedule</li>
 * <li><b>Class B (Concept/Pre-FEED):</b> PFD, thermal utilities, fire scenarios</li>
 * <li><b>Class C (Screening):</b> PFD only</li>
 * </ul>
 *
 * <p>
 * Fire scenarios and the noise assessment are derived from the process itself: one fire scenario per separator, gas
 * scrubber or tank (operating pressure, geometry, liquid level, fluid density and heating value, a 25 mm screening
 * release hole), and one noise entry per compressor and pump (actual absorbed power). The worst-case vessel and the
 * loudest compressor are also reported under the original top-level keys. A representative placeholder, flagged in its
 * {@code basis} field, is used only when the process has no vessels or no rotating machines.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * EngineeringDeliverablesPackage pkg = new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
 * pkg.generate();
 * String json = pkg.toJson();
 * String pfd = pkg.getPfdDot();
 * }
 * </pre>
 *
 * @author esol
 * @version 1.0
 * @see StudyClass
 * @see ThermalUtilitySummary
 * @see AlarmTripScheduleGenerator
 * @see SparePartsInventory
 * @see ProcessFlowDiagramExporter
 * @see FireProtectionDesign
 * @see NoiseAssessment
 */
public class EngineeringDeliverablesPackage implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(EngineeringDeliverablesPackage.class);

  /** Screening release hole diameter for fire scenarios in mm (medium leak category). */
  private static final double FIRE_HOLE_DIAMETER_MM = 25.0;

  /** Discharge coefficient for gas release through the screening hole. */
  private static final double GAS_DISCHARGE_COEFFICIENT = 0.85;

  /** Discharge coefficient for liquid release through the screening hole. */
  private static final double LIQUID_DISCHARGE_COEFFICIENT = 0.61;

  /** Atmospheric pressure in bara. */
  private static final double ATMOSPHERIC_BARA = 1.01325;

  /** Universal gas constant in J/(mol K). */
  private static final double GAS_CONSTANT = 8.314462618;

  /** Pool depth used to convert spilled liquid volume to pool area in m. */
  private static final double POOL_DEPTH_M = 0.02;

  /** Maximum pool diameter (drained deck or bund area) in m. */
  private static final double MAX_POOL_DIAMETER_M = 30.0;

  /** Pool fire mass burning rate in kg/(m2 s) for light hydrocarbons. */
  private static final double MASS_BURNING_RATE_KG_M2S = 0.055;

  /** Default lower heating value in kJ/kg when ISO 6976 cannot be evaluated. */
  private static final double DEFAULT_HEATING_VALUE_KJ_KG = 50000.0;

  /** Liquid hold-up time used to size a vessel without geometry in s. */
  private static final double FALLBACK_LIQUID_HOLDUP_S = 180.0;

  /** Liquid volume fraction assumed when the vessel level is unknown. */
  private static final double FALLBACK_LIQUID_FRACTION = 0.5;

  /** Minimum vessel volume when geometry is unset in m3. */
  private static final double FALLBACK_MIN_VOLUME_M3 = 1.0;

  /** The process system to generate deliverables for. */
  private final ProcessSystem processSystem;

  /** Study class that determines which deliverables are generated. */
  private final StudyClass studyClass;

  /** PFD in Graphviz DOT format. */
  private String pfdDot;

  /** Thermal utility summary. */
  private ThermalUtilitySummary thermalUtilities;

  /** Alarm/trip schedule generator. */
  private AlarmTripScheduleGenerator alarmTripSchedule;

  /** Spare parts inventory. */
  private SparePartsInventory sparePartsInventory;

  /** Fire scenario results as JSON (from FireProtectionDesign). */
  private String fireScenarioJson;

  /** Noise assessment results as JSON. */
  private String noiseAssessmentJson;

  /** Instrument schedule generator. */
  private InstrumentScheduleGenerator instrumentSchedule;

  /** IEC 81346 reference designation generator. */
  private neqsim.process.equipment.iec81346.ReferenceDesignationGenerator referenceDesignationGenerator;

  /** Generation status for each deliverable. */
  private final Map<DeliverableType, DeliverableStatus> statusMap = new LinkedHashMap<DeliverableType, DeliverableStatus>();

  /** Whether generation has been run. */
  private boolean generated;

  /**
   * Status of a single deliverable generation.
   */
  public static class DeliverableStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private final DeliverableType type;
    private boolean success;
    private String message;
    private long durationMs;

    /**
     * Constructor.
     *
     * @param type the deliverable type
     */
    public DeliverableStatus(DeliverableType type) {
      this.type = type;
    }

    /**
     * Get the deliverable type.
     *
     * @return deliverable type
     */
    public DeliverableType getType() {
      return type;
    }

    /**
     * Check if generation was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Get the status message.
     *
     * @return message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Get the generation duration in milliseconds.
     *
     * @return duration in ms
     */
    public long getDurationMs() {
      return durationMs;
    }
  }

  /**
   * Create a deliverables package for the given process system and study class.
   *
   * @param processSystem the process system (must have been run)
   * @param studyClass the study class determining required deliverables
   * @throws IllegalArgumentException if processSystem is null
   */
  public EngineeringDeliverablesPackage(ProcessSystem processSystem, StudyClass studyClass) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.studyClass = studyClass != null ? studyClass : StudyClass.CLASS_A;
  }

  /**
   * Generate all required deliverables for the configured study class.
   *
   * <p>
   * Each deliverable is generated independently; a failure in one does not prevent others from being generated. Check
   * individual status via {@link #getStatusMap()}.
   * </p>
   */
  public void generate() {
    logger.info("Generating engineering deliverables for {} with {}", processSystem.getClass().getSimpleName(),
        studyClass);

    for (DeliverableType type : studyClass.getRequiredDeliverables()) {
      DeliverableStatus status = new DeliverableStatus(type);
      long start = System.currentTimeMillis();

      try {
        switch (type) {
        case PFD:
          generatePfd();
          break;
        case THERMAL_UTILITIES:
          generateThermalUtilities();
          break;
        case ALARM_TRIP_SCHEDULE:
          generateAlarmTripSchedule();
          break;
        case SPARE_PARTS:
          generateSparePartsInventory();
          break;
        case FIRE_SCENARIOS:
          generateFireScenarios();
          break;
        case NOISE_ASSESSMENT:
          generateNoiseAssessment();
          break;
        case INSTRUMENT_SCHEDULE:
          generateInstrumentSchedule();
          break;
        case REFERENCE_DESIGNATION_SCHEDULE:
          generateReferenceDesignationSchedule();
          break;
        default:
          break;
        }
        status.success = true;
        status.message = "Generated successfully";
      } catch (Exception e) {
        status.success = false;
        status.message = "Generation failed: " + e.getMessage();
        logger.warn("Failed to generate {}: {}", type.getDisplayName(), e.getMessage());
      }

      status.durationMs = System.currentTimeMillis() - start;
      statusMap.put(type, status);
    }

    generated = true;
    logger.info("Deliverable generation complete: {}/{} successful", getSuccessCount(), statusMap.size());
  }

  /**
   * Generate the process flow diagram.
   */
  private void generatePfd() {
    ProcessFlowDiagramExporter exporter = new ProcessFlowDiagramExporter(processSystem);
    exporter.setTitle("PFD - " + processSystem.getClass().getSimpleName());
    pfdDot = exporter.toDot();
  }

  /**
   * Generate the thermal utility summary.
   */
  private void generateThermalUtilities() {
    thermalUtilities = new ThermalUtilitySummary(processSystem);
    thermalUtilities.calcUtilities();
  }

  /**
   * Generate the alarm/trip schedule.
   */
  private void generateAlarmTripSchedule() {
    alarmTripSchedule = new AlarmTripScheduleGenerator(processSystem);
    alarmTripSchedule.generate();
  }

  /**
   * Generate the spare parts inventory.
   */
  private void generateSparePartsInventory() {
    sparePartsInventory = new SparePartsInventory(processSystem);
    sparePartsInventory.generateInventory();
  }

  /**
   * Generate the fire scenario assessment from the vessels in the process.
   *
   * <p>
   * One {@link FireProtectionDesign#assessFireScenarios} entry is built for every {@link Separator} (including
   * subclasses such as three-phase separators and gas scrubbers) and every {@link Tank} in the process, using the
   * vessel operating pressure, geometry, liquid level and fluid. The worst case (largest total hydrocarbon inventory)
   * is also written to the backward-compatible top-level {@code fireScenario} key. When the process contains no vessel,
   * a representative placeholder scenario is generated and flagged as such in its {@code basis} field.
   * </p>
   */
  private void generateFireScenarios() {
    JsonArray scenarios = new JsonArray();
    JsonObject worst = null;
    double worstTotalInventory = -1.0;

    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      JsonObject scenario = null;
      try {
        if (unit instanceof Separator) {
          scenario = buildSeparatorFireScenario((Separator) unit);
        } else if (unit instanceof Tank) {
          scenario = buildTankFireScenario((Tank) unit);
        }
      } catch (Exception e) {
        logger.warn("Fire scenario for {} could not be derived: {}", unit.getName(), e.getMessage());
      }
      if (scenario == null) {
        continue;
      }
      scenarios.add(scenario);
      double total = scenario.get("totalHydrocarbonInventoryKg").getAsDouble();
      if (total > worstTotalInventory) {
        worstTotalInventory = total;
        worst = scenario;
      }
    }

    if (worst == null) {
      worst = buildPlaceholderFireScenario();
      scenarios.add(worst);
    }

    JsonObject obj = new JsonObject();
    obj.add("fireScenario", worst.get("fireScenario"));
    obj.addProperty("worstCaseEquipment", worst.get("equipmentName").getAsString());
    obj.addProperty("worstCaseBasis", "largest total hydrocarbon inventory (liquid + gas)");
    obj.addProperty("inventoryKg", worst.get("inventoryKg").getAsDouble());
    obj.addProperty("releaseRateKgS", worst.get("releaseRateKgS").getAsDouble());
    obj.addProperty("poolDiameterM", worst.get("poolDiameterM").getAsDouble());
    obj.addProperty("numberOfScenarios", scenarios.size());
    obj.add("scenarios", scenarios);
    JsonArray assumptions = new JsonArray();
    assumptions.add("Release from a " + FIRE_HOLE_DIAMETER_MM + " mm screening hole (medium leak per API RP 581 / "
        + "OGP 434-1 hole-size categories); gas Cd " + GAS_DISCHARGE_COEFFICIENT + " (choked or subsonic orifice), "
        + "liquid Cd " + LIQUID_DISCHARGE_COEFFICIENT + " (Bernoulli orifice) to atmospheric pressure");
    assumptions.add("Jet fire uses the gas release rate when the vessel holds gas, otherwise the liquid release rate");
    assumptions.add("Pool diameter: equivalent circle of the hydrocarbon liquid inventory spread to "
        + POOL_DEPTH_M * 1000.0 + " mm depth, capped at " + MAX_POOL_DIAMETER_M + " m (drained deck/bund area)");
    assumptions.add("Pool fire mass burning rate " + MASS_BURNING_RATE_KG_M2S + " kg/(m2 s) (light hydrocarbon)");
    assumptions.add("Heating value: ISO 6976 inferior (lower) calorific value of the vessel gas phase (oil phase if "
        + "no gas), mass basis at 15 C; default " + DEFAULT_HEATING_VALUE_KJ_KG + " kJ/kg when unavailable");
    assumptions.add("Vessel volume excludes heads; when geometry is unset the volume is sized for "
        + FALLBACK_LIQUID_HOLDUP_S + " s liquid hold-up at " + FALLBACK_LIQUID_FRACTION
        + " liquid volume fraction (minimum " + FALLBACK_MIN_VOLUME_M3 + " m3)");
    obj.add("assumptions", assumptions);
    fireScenarioJson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(obj);
  }

  /**
   * Build the fire scenario for a separator from its geometry, liquid level and fluid.
   *
   * @param separator the separator (or subclass) to assess
   * @return scenario JSON object, or null if the separator has no fluid
   */
  private JsonObject buildSeparatorFireScenario(Separator separator) {
    SystemInterface system = separator.getThermoSystem();
    if (system == null) {
      return null;
    }
    double volumeM3;
    double liquidFraction;
    String volumeBasis;
    String levelBasis;
    double diameter = separator.getInternalDiameter();
    double length = separator.getSeparatorLength();
    if (separator.hasGeometry() && length > 0.0) {
      volumeM3 = Math.PI / 4.0 * diameter * diameter * length;
      volumeBasis = "vessel geometry: ID " + diameter + " m x tan-tan length " + length + " m (heads excluded)";
      liquidFraction = Math.max(0.0, Math.min(1.0, separator.calcLiquidVolume() / volumeM3));
      levelBasis = "separator liquid level (" + separator.getOrientation() + " orientation)";
    } else {
      volumeM3 = -1.0;
      liquidFraction = -1.0;
      volumeBasis = null;
      levelBasis = null;
    }
    return buildVesselFireScenario(separator.getName(), separator.getClass().getSimpleName(), system, volumeM3,
        volumeBasis, liquidFraction, levelBasis);
  }

  /**
   * Build the fire scenario for a storage tank from its volume, liquid level and fluid.
   *
   * @param tank the tank to assess
   * @return scenario JSON object, or null if the tank has no fluid
   */
  private JsonObject buildTankFireScenario(Tank tank) {
    SystemInterface system = tank.getThermoSystem();
    if (system == null) {
      return null;
    }
    double volumeM3 = tank.getVolume();
    double level = tank.getLiquidLevel();
    if (volumeM3 > 0.0) {
      return buildVesselFireScenario(tank.getName(), tank.getClass().getSimpleName(), system, volumeM3,
          "tank volume setting", Math.max(0.0, Math.min(1.0, level)), "tank liquid level fraction");
    }
    return buildVesselFireScenario(tank.getName(), tank.getClass().getSimpleName(), system, -1.0, null, -1.0, null);
  }

  /**
   * Build a fire scenario for a vessel from its fluid and (optional) geometry.
   *
   * @param name equipment name
   * @param type equipment class name
   * @param vesselSystem the fluid held in the vessel at operating conditions
   * @param volumeM3 vessel volume in m3, or a negative value when geometry is unknown
   * @param volumeBasis description of the volume basis, or null when geometry is unknown
   * @param liquidFraction liquid volume fraction in the vessel, or a negative value when unknown
   * @param levelBasis description of the liquid fraction basis, or null when unknown
   * @return scenario JSON object with inputs, bases and the {@code fireScenario} result
   */
  private JsonObject buildVesselFireScenario(String name, String type, SystemInterface vesselSystem, double volumeM3,
      String volumeBasis, double liquidFraction, String levelBasis) {
    SystemInterface fluid = vesselSystem.clone();
    fluid.initProperties();
    double pressureBara = fluid.getPressure("bara");
    double temperatureK = fluid.getTemperature();

    PhaseInterface gasPhase = null;
    double liquidVolFlow = 0.0;
    double hcLiquidVolFlow = 0.0;
    double hcLiquidMassFlow = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      if (phase.getType() == PhaseType.GAS) {
        gasPhase = phase;
      } else {
        double volFlow = phase.getFlowRate("m3/sec");
        liquidVolFlow += volFlow;
        if (phase.getType() != PhaseType.AQUEOUS) {
          hcLiquidVolFlow += volFlow;
          hcLiquidMassFlow += phase.getFlowRate("kg/sec");
        }
      }
    }
    double hcLiquidDensity = hcLiquidVolFlow > 0.0 ? hcLiquidMassFlow / hcLiquidVolFlow : 0.0;
    double hcShareOfLiquid = liquidVolFlow > 0.0 ? hcLiquidVolFlow / liquidVolFlow : 0.0;
    double gasDensity = gasPhase != null ? gasPhase.getDensity("kg/m3") : 0.0;

    if (volumeM3 <= 0.0) {
      double holdup = liquidVolFlow * FALLBACK_LIQUID_HOLDUP_S;
      volumeM3 = Math.max(holdup / FALLBACK_LIQUID_FRACTION, FALLBACK_MIN_VOLUME_M3);
      volumeBasis = "geometry not set - screening volume for " + FALLBACK_LIQUID_HOLDUP_S + " s liquid hold-up at "
          + FALLBACK_LIQUID_FRACTION + " liquid fraction (minimum " + FALLBACK_MIN_VOLUME_M3 + " m3)";
    }
    if (liquidFraction < 0.0) {
      liquidFraction = liquidVolFlow > 0.0 ? FALLBACK_LIQUID_FRACTION : 0.0;
      levelBasis = liquidVolFlow > 0.0
          ? "liquid level unknown - assumed " + FALLBACK_LIQUID_FRACTION + " liquid volume fraction"
          : "no liquid phase in vessel";
    }
    if (liquidVolFlow <= 0.0) {
      liquidFraction = 0.0;
      levelBasis = "no liquid phase in vessel";
    }

    double hcLiquidVolume = liquidFraction * volumeM3 * hcShareOfLiquid;
    double liquidInventoryKg = hcLiquidVolume * hcLiquidDensity;
    double gasInventoryKg = gasPhase != null ? (1.0 - liquidFraction) * volumeM3 * gasDensity : 0.0;

    double holeArea = Math.PI / 4.0 * Math.pow(FIRE_HOLE_DIAMETER_MM / 1000.0, 2.0);
    double gasRelease = gasPhase != null ? gasOrificeRelease(gasPhase, pressureBara, temperatureK, holeArea) : 0.0;
    double liquidRelease = hcLiquidDensity > 0.0
        ? LIQUID_DISCHARGE_COEFFICIENT * holeArea
            * Math.sqrt(2.0 * hcLiquidDensity * Math.max(pressureBara - ATMOSPHERIC_BARA, 0.0) * 1.0e5)
        : 0.0;
    double releaseRate = gasPhase != null ? gasRelease : liquidRelease;
    String releasePhase = gasPhase != null ? "gas" : (hcLiquidDensity > 0.0 ? "liquid" : "none");

    String heatingBasis;
    double heatingValue = lowerHeatingValueKJKg(fluid, gasPhase != null ? "gas" : "oil");
    if (heatingValue > 0.0) {
      heatingBasis = "ISO 6976 LCV of vessel " + (gasPhase != null ? "gas" : "oil") + " phase (mass basis, 15 C)";
    } else {
      heatingValue = DEFAULT_HEATING_VALUE_KJ_KG;
      heatingBasis = "default hydrocarbon LHV - ISO 6976 calculation not available for this fluid";
    }

    double poolDiameter = 0.0;
    String poolBasis = "no hydrocarbon liquid - pool fire not credible";
    if (hcLiquidVolume > 0.0) {
      poolDiameter = Math.min(Math.sqrt(4.0 * hcLiquidVolume / (Math.PI * POOL_DEPTH_M)), MAX_POOL_DIAMETER_M);
      poolBasis = "liquid inventory " + round(hcLiquidVolume) + " m3 spread to " + POOL_DEPTH_M * 1000.0
          + " mm depth, capped at " + MAX_POOL_DIAMETER_M + " m";
    }

    FireProtectionDesign.FireScenarioResult result = FireProtectionDesign.assessFireScenarios(name, liquidInventoryKg,
        pressureBara, volumeM3, poolDiameter, releaseRate, heatingValue, MASS_BURNING_RATE_KG_M2S);

    JsonObject obj = new JsonObject();
    obj.addProperty("equipmentName", name);
    obj.addProperty("equipmentType", type);
    obj.addProperty("basis", "derived from process unit operation");
    obj.addProperty("operatingPressureBara", pressureBara);
    obj.addProperty("operatingTemperatureC", temperatureK - 273.15);
    obj.addProperty("vesselVolumeM3", volumeM3);
    obj.addProperty("volumeBasis", volumeBasis);
    obj.addProperty("liquidVolumeFraction", liquidFraction);
    obj.addProperty("liquidLevelBasis", levelBasis);
    obj.addProperty("hydrocarbonShareOfLiquidVolume", hcShareOfLiquid);
    obj.addProperty("liquidDensityKgM3", hcLiquidDensity);
    obj.addProperty("liquidInventoryKg", liquidInventoryKg);
    obj.addProperty("gasDensityKgM3", gasDensity);
    obj.addProperty("gasInventoryKg", gasInventoryKg);
    obj.addProperty("inventoryKg", liquidInventoryKg);
    obj.addProperty("totalHydrocarbonInventoryKg", liquidInventoryKg + gasInventoryKg);
    obj.addProperty("releaseHoleDiameterMm", FIRE_HOLE_DIAMETER_MM);
    obj.addProperty("gasReleaseRateKgS", gasRelease);
    obj.addProperty("liquidReleaseRateKgS", liquidRelease);
    obj.addProperty("releaseRateKgS", releaseRate);
    obj.addProperty("releasePhase", releasePhase);
    obj.addProperty("releaseBasis", FIRE_HOLE_DIAMETER_MM + " mm screening hole to atmosphere");
    obj.addProperty("heatingValueKJKg", heatingValue);
    obj.addProperty("heatingValueBasis", heatingBasis);
    obj.addProperty("poolDiameterM", poolDiameter);
    obj.addProperty("poolDiameterBasis", poolBasis);
    obj.addProperty("massBurningRateKgM2s", MASS_BURNING_RATE_KG_M2S);
    obj.add("fireScenario", JsonParser.parseString(result.toJson()));
    return obj;
  }

  /**
   * Build the representative placeholder fire scenario used when the process has no vessels.
   *
   * @return scenario JSON object flagged as a placeholder
   */
  private JsonObject buildPlaceholderFireScenario() {
    String name = "Representative Equipment";
    double inventoryKg = 5000.0;
    double pressureBara = 60.0;
    double volumeM3 = 10.0;
    double poolDiameterM = 5.0;
    double releaseRateKgS = 10.0;
    FireProtectionDesign.FireScenarioResult result = FireProtectionDesign.assessFireScenarios(name, inventoryKg,
        pressureBara, volumeM3, poolDiameterM, releaseRateKgS, DEFAULT_HEATING_VALUE_KJ_KG, MASS_BURNING_RATE_KG_M2S);
    JsonObject obj = new JsonObject();
    obj.addProperty("equipmentName", name);
    obj.addProperty("equipmentType", "placeholder");
    obj.addProperty("basis", "representative placeholder - no vessels found");
    obj.addProperty("operatingPressureBara", pressureBara);
    obj.addProperty("vesselVolumeM3", volumeM3);
    obj.addProperty("inventoryKg", inventoryKg);
    obj.addProperty("totalHydrocarbonInventoryKg", inventoryKg);
    obj.addProperty("releaseRateKgS", releaseRateKgS);
    obj.addProperty("heatingValueKJKg", DEFAULT_HEATING_VALUE_KJ_KG);
    obj.addProperty("poolDiameterM", poolDiameterM);
    obj.addProperty("massBurningRateKgM2s", MASS_BURNING_RATE_KG_M2S);
    obj.add("fireScenario", JsonParser.parseString(result.toJson()));
    return obj;
  }

  /**
   * Calculate the gas mass release rate through a sharp-edged orifice to atmosphere.
   *
   * @param gas the gas phase at vessel conditions (properties initialised)
   * @param pressureBara upstream pressure in bara
   * @param temperatureK upstream temperature in K
   * @param holeAreaM2 hole area in m2
   * @return mass release rate in kg/s (choked flow when the pressure ratio is above critical)
   */
  private static double gasOrificeRelease(PhaseInterface gas, double pressureBara, double temperatureK,
      double holeAreaM2) {
    if (pressureBara <= ATMOSPHERIC_BARA) {
      return 0.0;
    }
    double gamma = gas.getGamma();
    if (Double.isNaN(gamma) || gamma <= 1.0 || gamma > 2.0) {
      gamma = 1.3;
    }
    double z = gas.getZ();
    if (Double.isNaN(z) || z <= 0.0) {
      z = 1.0;
    }
    double molarMass = gas.getMolarMass();
    double pressurePa = pressureBara * 1.0e5;
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    double rt = z * GAS_CONSTANT * temperatureK / molarMass;
    if (ATMOSPHERIC_BARA / pressureBara <= criticalRatio) {
      return GAS_DISCHARGE_COEFFICIENT * holeAreaM2 * pressurePa
          * Math.sqrt(gamma / rt * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (gamma - 1.0)));
    }
    double ratio = ATMOSPHERIC_BARA / pressureBara;
    return GAS_DISCHARGE_COEFFICIENT * holeAreaM2 * pressurePa * Math.sqrt(
        2.0 * gamma / ((gamma - 1.0) * rt) * (Math.pow(ratio, 2.0 / gamma) - Math.pow(ratio, (gamma + 1.0) / gamma)));
  }

  /**
   * Calculate the ISO 6976 lower heating value of one phase of a fluid on a mass basis.
   *
   * @param fluid the fluid with an initialised phase split
   * @param phaseName phase to evaluate ("gas" or "oil")
   * @return lower heating value in kJ/kg, or 0.0 if it cannot be calculated
   */
  private static double lowerHeatingValueKJKg(SystemInterface fluid, String phaseName) {
    try {
      if (!fluid.hasPhaseType(phaseName)) {
        return 0.0;
      }
      SystemInterface phaseSystem = fluid.phaseToSystem(phaseName);
      Standard_ISO6976 standard = new Standard_ISO6976(phaseSystem, 15.0, 15.0, "mass");
      standard.setReferenceState("real");
      standard.calculate();
      double lhv = standard.getValue("InferiorCalorificValue", "kJ/kg");
      if (Double.isNaN(lhv) || Double.isInfinite(lhv) || lhv < 5000.0 || lhv > 130000.0) {
        return 0.0;
      }
      return lhv;
    } catch (Exception e) {
      logger.debug("ISO 6976 heating value unavailable: {}", e.getMessage());
      return 0.0;
    }
  }

  /**
   * Round a value to three decimals for text output.
   *
   * @param value the value
   * @return rounded value
   */
  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  /**
   * Generate the noise assessment from the rotating machines in the process.
   *
   * <p>
   * Every {@link Compressor} and {@link Pump} is assessed with {@link NoiseAssessment} using its actual absorbed power.
   * The loudest compressor (or loudest machine when there are no compressors) is also written to the
   * backward-compatible top-level keys. When the process has no rotating machines a representative 1000 kW compressor
   * is used and flagged in the {@code basis} field.
   * </p>
   */
  private void generateNoiseAssessment() {
    JsonArray machines = new JsonArray();
    JsonObject loudestCompressor = null;
    JsonObject loudestMachine = null;
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      JsonObject machine = null;
      try {
        if (unit instanceof Compressor) {
          double powerKW = Math.abs(((Compressor) unit).getPower("kW"));
          machine = buildMachineNoise(unit.getName(), "compressor", powerKW,
              NoiseAssessment.compressorNoise(powerKW, "CENTRIFUGAL"), "derived from process unit operation");
        } else if (unit instanceof Pump) {
          double powerKW = Math.abs(((Pump) unit).getPower("kW"));
          machine = buildMachineNoise(unit.getName(), "pump", powerKW,
              NoiseAssessment.pumpNoise(powerKW, "CENTRIFUGAL"), "derived from process unit operation");
        }
      } catch (Exception e) {
        logger.warn("Noise for {} could not be derived: {}", unit.getName(), e.getMessage());
      }
      if (machine == null || Double.isNaN(machine.get("powerKW").getAsDouble())) {
        continue;
      }
      machines.add(machine);
      double level = machine.get("splAt1mDbA").getAsDouble();
      if (loudestMachine == null || level > loudestMachine.get("splAt1mDbA").getAsDouble()) {
        loudestMachine = machine;
      }
      if ("compressor".equals(machine.get("machineType").getAsString())
          && (loudestCompressor == null || level > loudestCompressor.get("splAt1mDbA").getAsDouble())) {
        loudestCompressor = machine;
      }
    }
    if (loudestMachine == null) {
      double powerKW = 1000.0;
      loudestMachine = buildMachineNoise("Representative Compressor", "compressor", powerKW,
          NoiseAssessment.compressorNoise(powerKW, "CENTRIFUGAL"),
          "representative placeholder - no compressors or pumps found");
      machines.add(loudestMachine);
    }
    JsonObject governing = loudestCompressor != null ? loudestCompressor : loudestMachine;

    JsonObject obj = new JsonObject();
    obj.addProperty("compressorSwlDbA", governing.get("splAt1mDbA").getAsDouble());
    obj.addProperty("compressorPowerKW", governing.get("powerKW").getAsDouble());
    obj.addProperty("compressorSplAt25mDbA", governing.get("splAt25mDbA").getAsDouble());
    obj.addProperty("compressorSplAt100mWithAttenDbA", governing.get("splAt100mWithAttenDbA").getAsDouble());
    obj.addProperty("governingMachine", governing.get("equipmentName").getAsString());
    obj.addProperty("governingMachineType", governing.get("machineType").getAsString());
    obj.addProperty("basis", governing.get("basis").getAsString());
    obj.addProperty("norsokLimitContinuousDbA", NoiseAssessment.NORSOK_MAX_CONTINUOUS_DBA);
    obj.addProperty("norsokLimitEquipmentAreaDbA", NoiseAssessment.NORSOK_MAX_EQUIPMENT_AREA_DBA);
    obj.addProperty("numberOfMachines", machines.size());
    obj.add("machines", machines);
    JsonArray assumptions = new JsonArray();
    assumptions.add("Machine noise from absolute shaft power with NoiseAssessment correlations (centrifugal type)");
    assumptions.add("SPL at 25 m: point source from (SPL at 1 m - 8 dB) with spherical spreading");
    assumptions.add("SPL at 100 m: with atmospheric attenuation at 20 C and 60 % relative humidity");
    assumptions.add("Valve, flare and piping noise are not included in this screening");
    obj.add("assumptions", assumptions);

    noiseAssessmentJson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(obj);
  }

  /**
   * Build the noise JSON entry for one rotating machine.
   *
   * @param name equipment name
   * @param machineType "compressor" or "pump"
   * @param powerKW absolute shaft power in kW
   * @param splAt1m sound level at 1 m in dB(A) from {@link NoiseAssessment}
   * @param basis description of the data basis
   * @return machine noise JSON object
   */
  private static JsonObject buildMachineNoise(String name, String machineType, double powerKW, double splAt1m,
      String basis) {
    JsonObject obj = new JsonObject();
    obj.addProperty("equipmentName", name);
    obj.addProperty("machineType", machineType);
    obj.addProperty("basis", basis);
    obj.addProperty("powerKW", powerKW);
    obj.addProperty("splAt1mDbA", splAt1m);
    obj.addProperty("splAt25mDbA", NoiseAssessment.splAtDistance(splAt1m - 8.0, 25.0));
    obj.addProperty("splAt100mWithAttenDbA", NoiseAssessment.splAtDistanceWithAttenuation(splAt1m, 100.0, 20.0, 60.0));
    obj.addProperty("exceedsNorsokEquipmentAreaLimitAt1m", splAt1m > NoiseAssessment.NORSOK_MAX_EQUIPMENT_AREA_DBA);
    return obj;
  }

  /**
   * Generate instrument schedule with live measurement devices.
   */
  private void generateInstrumentSchedule() {
    instrumentSchedule = new InstrumentScheduleGenerator(processSystem);
    instrumentSchedule.setRegisterOnProcess(true);
    instrumentSchedule.generate();
  }

  /**
   * Generate IEC 81346 reference designation schedule for all equipment. Auto-assigns functional, product and location
   * designations.
   */
  private void generateReferenceDesignationSchedule() {
    referenceDesignationGenerator = new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator();
    referenceDesignationGenerator.generate(processSystem);
  }

  /**
   * Get the number of successfully generated deliverables.
   *
   * @return count of successful deliverables
   */
  public int getSuccessCount() {
    int count = 0;
    for (DeliverableStatus status : statusMap.values()) {
      if (status.isSuccess()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Check if all required deliverables were generated successfully.
   *
   * @return true if all succeeded
   */
  public boolean isComplete() {
    if (!generated) {
      return false;
    }
    for (DeliverableStatus status : statusMap.values()) {
      if (!status.isSuccess()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if generation has been run.
   *
   * @return true if generate() has been called
   */
  public boolean isGenerated() {
    return generated;
  }

  /**
   * Get the study class.
   *
   * @return study class
   */
  public StudyClass getStudyClass() {
    return studyClass;
  }

  /**
   * Get the PFD in Graphviz DOT format.
   *
   * @return DOT string or null if not generated
   */
  public String getPfdDot() {
    return pfdDot;
  }

  /**
   * Get the thermal utility summary.
   *
   * @return thermal utility summary or null if not generated/required
   */
  public ThermalUtilitySummary getThermalUtilities() {
    return thermalUtilities;
  }

  /**
   * Get the alarm/trip schedule generator.
   *
   * @return alarm/trip schedule or null if not generated/required
   */
  public AlarmTripScheduleGenerator getAlarmTripSchedule() {
    return alarmTripSchedule;
  }

  /**
   * Get the spare parts inventory.
   *
   * @return spare parts inventory or null if not generated/required
   */
  public SparePartsInventory getSparePartsInventory() {
    return sparePartsInventory;
  }

  /**
   * Get the fire scenario assessment JSON.
   *
   * @return fire scenario JSON or null if not generated/required
   */
  public String getFireScenarioJson() {
    return fireScenarioJson;
  }

  /**
   * Get the noise assessment JSON.
   *
   * @return noise assessment JSON or null if not generated/required
   */
  public String getNoiseAssessmentJson() {
    return noiseAssessmentJson;
  }

  /**
   * Get the instrument schedule generator.
   *
   * @return instrument schedule or null if not generated/required
   */
  public InstrumentScheduleGenerator getInstrumentSchedule() {
    return instrumentSchedule;
  }

  /**
   * Get the IEC 81346 reference designation generator.
   *
   * @return reference designation generator or null if not generated/required
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator getReferenceDesignationGenerator() {
    return referenceDesignationGenerator;
  }

  /**
   * Get the generation status map.
   *
   * @return map of deliverable type to generation status
   */
  public Map<DeliverableType, DeliverableStatus> getStatusMap() {
    return new LinkedHashMap<DeliverableType, DeliverableStatus>(statusMap);
  }

  /**
   * Get a list of deliverable types that failed generation.
   *
   * @return list of failed deliverable types
   */
  public List<DeliverableType> getFailedDeliverables() {
    List<DeliverableType> failed = new ArrayList<DeliverableType>();
    for (Map.Entry<DeliverableType, DeliverableStatus> entry : statusMap.entrySet()) {
      if (!entry.getValue().isSuccess()) {
        failed.add(entry.getKey());
      }
    }
    return failed;
  }

  /**
   * Produce a complete JSON report of all generated deliverables.
   *
   * @return JSON string with all deliverable outputs
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("studyClass", studyClass.name());
    root.addProperty("studyClassDisplayName", studyClass.getDisplayName());
    root.addProperty("generated", generated);
    root.addProperty("complete", isComplete());
    root.addProperty("successCount", getSuccessCount());
    root.addProperty("totalRequired", statusMap.size());

    // Status summary
    JsonArray statusArr = new JsonArray();
    for (Map.Entry<DeliverableType, DeliverableStatus> entry : statusMap.entrySet()) {
      JsonObject s = new JsonObject();
      s.addProperty("deliverable", entry.getKey().getDisplayName());
      s.addProperty("success", entry.getValue().isSuccess());
      s.addProperty("message", entry.getValue().getMessage());
      s.addProperty("durationMs", entry.getValue().getDurationMs());
      statusArr.add(s);
    }
    root.add("deliverableStatus", statusArr);

    // Individual deliverable outputs
    if (pfdDot != null) {
      root.addProperty("pfdDot", pfdDot);
    }
    if (thermalUtilities != null) {
      root.add("thermalUtilities", JsonParser.parseString(thermalUtilities.toJson()));
    }
    if (alarmTripSchedule != null) {
      root.add("alarmTripSchedule", JsonParser.parseString(alarmTripSchedule.toJson()));
    }
    if (sparePartsInventory != null) {
      root.add("sparePartsInventory", JsonParser.parseString(sparePartsInventory.toJson()));
    }
    if (fireScenarioJson != null) {
      root.add("fireScenarios", JsonParser.parseString(fireScenarioJson));
    }
    if (noiseAssessmentJson != null) {
      root.add("noiseAssessment", JsonParser.parseString(noiseAssessmentJson));
    }
    if (instrumentSchedule != null) {
      root.add("instrumentSchedule", JsonParser.parseString(instrumentSchedule.toJson()));
    }
    if (referenceDesignationGenerator != null) {
      root.add("referenceDesignationSchedule", JsonParser.parseString(referenceDesignationGenerator.toJson()));
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }

  @Override
  public String toString() {
    return String.format("EngineeringDeliverablesPackage[%s, %s, %d/%d deliverables]", studyClass,
        generated ? "generated" : "not generated", getSuccessCount(), statusMap.size());
  }
}
