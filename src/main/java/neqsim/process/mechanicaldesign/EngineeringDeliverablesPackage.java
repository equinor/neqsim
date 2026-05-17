package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.StudyClass.DeliverableType;
import neqsim.process.mechanicaldesign.designstandards.FireProtectionDesign;
import neqsim.process.mechanicaldesign.designstandards.NoiseAssessment;
import neqsim.process.processmodel.ProcessFlowDiagramExporter;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Generates the full engineering deliverables package for a field development study.
 *
 * <p>
 * Given a {@link ProcessSystem} and a {@link StudyClass}, this class generates all required
 * deliverables and bundles them into a single structured output. The deliverable set is controlled
 * by the study class:
 * </p>
 * <ul>
 * <li><b>Class A (FEED/Detail):</b> PFD, thermal utilities, alarm/trip schedule, spare parts, fire
 * scenarios, noise assessment</li>
 * <li><b>Class B (Concept/Pre-FEED):</b> PFD, thermal utilities, fire scenarios</li>
 * <li><b>Class C (Screening):</b> PFD only</li>
 * </ul>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * EngineeringDeliverablesPackage pkg =
 *     new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
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
  private final Map<DeliverableType, DeliverableStatus> statusMap =
      new LinkedHashMap<DeliverableType, DeliverableStatus>();

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
   * Each deliverable is generated independently; a failure in one does not prevent others from
   * being generated. Check individual status via {@link #getStatusMap()}.
   * </p>
   */
  public void generate() {
    logger.info("Generating engineering deliverables for {} with {}",
        processSystem.getClass().getSimpleName(), studyClass);

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
    logger.info("Deliverable generation complete: {}/{} successful", getSuccessCount(),
        statusMap.size());
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
   * Generate fire scenario assessment.
   */
  private void generateFireScenarios() {
    // Assess representative fire scenarios for the process
    String equipmentName = "Representative Equipment";
    double inventoryKg = 5000.0;
    double operatingPressureBara = 60.0;
    double vesselVolumeM3 = 10.0;
    double poolDiameterM = 5.0;
    double releaseRateKgS = 10.0;
    double heatingValueKJKg = 50000.0;
    double massBurningRateKgM2s = 0.055;

    FireProtectionDesign.FireScenarioResult scenario =
        FireProtectionDesign.assessFireScenarios(equipmentName, inventoryKg, operatingPressureBara,
            vesselVolumeM3, poolDiameterM, releaseRateKgS, heatingValueKJKg, massBurningRateKgM2s);
    JsonObject obj = new JsonObject();
    obj.add("fireScenario", JsonParser.parseString(scenario.toJson()));
    obj.addProperty("inventoryKg", inventoryKg);
    obj.addProperty("releaseRateKgS", releaseRateKgS);
    obj.addProperty("poolDiameterM", poolDiameterM);
    fireScenarioJson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
        .create().toJson(obj);
  }

  /**
   * Generate noise assessment.
   */
  private void generateNoiseAssessment() {
    // Assess representative noise for the process equipment
    double compressorPowerKW = 1000.0;

    JsonObject obj = new JsonObject();
    double compNoise = NoiseAssessment.compressorNoise(compressorPowerKW, "centrifugal");
    obj.addProperty("compressorSwlDbA", compNoise);
    obj.addProperty("compressorPowerKW", compressorPowerKW);

    double splAt1m = compNoise - 8.0; // Approximate point source at 1m
    double spl25m = NoiseAssessment.splAtDistance(splAt1m, 25.0);
    obj.addProperty("compressorSplAt25mDbA", spl25m);

    double splWithAttenuation =
        NoiseAssessment.splAtDistanceWithAttenuation(compNoise, 100.0, 20.0, 60.0);
    obj.addProperty("compressorSplAt100mWithAttenDbA", splWithAttenuation);

    obj.addProperty("norsokLimitContinuousDbA", NoiseAssessment.NORSOK_MAX_CONTINUOUS_DBA);
    obj.addProperty("norsokLimitEquipmentAreaDbA", NoiseAssessment.NORSOK_MAX_EQUIPMENT_AREA_DBA);

    noiseAssessmentJson = new GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(obj);
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
   * Generate IEC 81346 reference designation schedule for all equipment. Auto-assigns functional,
   * product and location designations.
   */
  private void generateReferenceDesignationSchedule() {
    referenceDesignationGenerator =
        new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator();
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
      root.add("referenceDesignationSchedule",
          JsonParser.parseString(referenceDesignationGenerator.toJson()));
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  @Override
  public String toString() {
    return String.format("EngineeringDeliverablesPackage[%s, %s, %d/%d deliverables]", studyClass,
        generated ? "generated" : "not generated", getSuccessCount(), statusMap.size());
  }
}
