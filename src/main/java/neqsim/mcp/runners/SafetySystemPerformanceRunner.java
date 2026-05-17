package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.SafetyInstrumentedFunction;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.process.measurementdevice.FireDetector;
import neqsim.process.measurementdevice.GasDetector;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.safety.barrier.DocumentEvidence;
import neqsim.process.safety.barrier.SafetySystemCategory;
import neqsim.process.safety.barrier.SafetySystemDemand;
import neqsim.process.safety.barrier.SafetySystemPerformanceAnalyzer;
import neqsim.process.safety.barrier.SafetySystemPerformanceReport;

/**
 * MCP runner for safety-system barrier performance analysis from JSON input.
 *
 * <p>
 * The runner accepts an evidence-linked barrier register plus optional STID-derived demand cases,
 * fire/gas detectors, cause-and-effect voting SIFs, and quantitative SIL/PFD SIF definitions. It
 * returns the {@link SafetySystemPerformanceReport} together with extraction templates for agents
 * reading C&amp;E charts, SRS documents, firewater datasheets, detector layouts, and PFP schedules.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SafetySystemPerformanceRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private SafetySystemPerformanceRunner() {}

  /**
   * Runs the safety-system performance analyzer from JSON.
   *
   * @param json JSON input with a register, demands, instruments, and optional SIF data
   * @return JSON string with a safety-system performance report and templates
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      JsonObject registerJson = getRegisterJson(input);
      BarrierRegisterRunner.ParsedRegister parsed =
          BarrierRegisterRunner.parseRegister(registerJson);
      SafetySystemPerformanceAnalyzer analyzer =
          new SafetySystemPerformanceAnalyzer(parsed.register);

      List<SafetySystemDemand> demandCases = parseDemandCases(input, parsed);
      for (SafetySystemDemand demandCase : demandCases) {
        analyzer.addDemandCase(demandCase);
      }
      List<MeasurementDeviceInterface> devices = parseMeasurementDevices(input);
      for (MeasurementDeviceInterface device : devices) {
        analyzer.addMeasurementDevice(device);
      }
      addSafetyInstrumentedFunctions(input, analyzer);

      SafetySystemPerformanceReport report = analyzer.analyze();
      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("standard",
          "NORSOK S-001 / ISO 13702 / TR1055-style performance standards / IEC 61511");
      out.add("summary", buildSummary(report, demandCases.size(), devices.size()));
      out.add("performanceReport", GSON.toJsonTree(report.toMap()));
      out.add("standardsTemplates", buildStandardsTemplates());
      out.add("stidExtractionTemplates", BarrierRegisterRunner.buildDocumentExtractionTemplate());
      out.add("registerExport", GSON.toJsonTree(parsed.register.toMap()));
      return GSON.toJson(out);
    } catch (Exception ex) {
      return errorJson("Safety-system performance analysis failed: " + ex.getMessage());
    }
  }

  /**
   * Gets the register JSON from supported top-level aliases.
   *
   * @param input top-level input
   * @return register JSON object
   */
  private static JsonObject getRegisterJson(JsonObject input) {
    if (input.has("register") && input.get("register").isJsonObject()) {
      return input.getAsJsonObject("register");
    }
    if (input.has("barrierRegister") && input.get("barrierRegister").isJsonObject()) {
      return input.getAsJsonObject("barrierRegister");
    }
    return input;
  }

  /**
   * Parses demand cases from JSON.
   *
   * @param input top-level input
   * @param parsed parsed register context for evidence references
   * @return demand-case list
   */
  private static List<SafetySystemDemand> parseDemandCases(JsonObject input,
      BarrierRegisterRunner.ParsedRegister parsed) {
    List<SafetySystemDemand> demands = new ArrayList<SafetySystemDemand>();
    JsonArray demandArray = mergedArray(input, "demands", "demandCases");
    for (JsonElement element : demandArray) {
      if (element.isJsonObject()) {
        demands.add(parseDemandCase(element.getAsJsonObject(), parsed));
      }
    }
    return demands;
  }

  /**
   * Parses one demand case.
   *
   * @param input demand-case JSON
   * @param parsed parsed register context for evidence references
   * @return demand case
   */
  private static SafetySystemDemand parseDemandCase(JsonObject input,
      BarrierRegisterRunner.ParsedRegister parsed) {
    SafetySystemDemand demand = new SafetySystemDemand(optString(input, "demandId", "D-UNNAMED"))
        .setBarrierId(optString(input, "barrierId", ""))
        .setEquipmentTag(optString(input, "equipmentTag", ""))
        .setScenario(optString(input, "scenario", ""))
        .setCategory(parseSafetySystemCategory(optString(input, "category", "UNKNOWN")));
    setDemandNumber(input, "demandValue", demand, DemandNumber.DEMAND_VALUE);
    setDemandNumber(input, "capacityValue", demand, DemandNumber.CAPACITY_VALUE);
    demand.setDemandUnit(optString(input, "demandUnit", ""));
    setDemandNumber(input, "requiredResponseTimeSeconds", demand, DemandNumber.REQUIRED_RESPONSE);
    setDemandNumber(input, "actualResponseTimeSeconds", demand, DemandNumber.ACTUAL_RESPONSE);
    setDemandNumber(input, "requiredAvailability", demand, DemandNumber.REQUIRED_AVAILABILITY);
    setDemandNumber(input, "actualAvailability", demand, DemandNumber.ACTUAL_AVAILABILITY);
    setDemandNumber(input, "requiredEffectiveness", demand, DemandNumber.REQUIRED_EFFECTIVENESS);
    setDemandNumber(input, "actualEffectiveness", demand, DemandNumber.ACTUAL_EFFECTIVENESS);
    setDemandNumber(input, "requiredPfd", demand, DemandNumber.REQUIRED_PFD);
    setDemandNumber(input, "actualPfd", demand, DemandNumber.ACTUAL_PFD);
    addEvidenceToDemand(input, parsed, demand);
    return demand;
  }

  /**
   * Applies one optional numeric demand field.
   *
   * @param input demand JSON
   * @param field JSON field name
   * @param demand demand case to update
   * @param number target number selector
   */
  private static void setDemandNumber(JsonObject input, String field, SafetySystemDemand demand,
      DemandNumber number) {
    if (!hasNumber(input, field)) {
      return;
    }
    double value = input.get(field).getAsDouble();
    switch (number) {
      case DEMAND_VALUE:
        demand.setDemandValue(value);
        break;
      case CAPACITY_VALUE:
        demand.setCapacityValue(value);
        break;
      case REQUIRED_RESPONSE:
        demand.setRequiredResponseTimeSeconds(value);
        break;
      case ACTUAL_RESPONSE:
        demand.setActualResponseTimeSeconds(value);
        break;
      case REQUIRED_AVAILABILITY:
        demand.setRequiredAvailability(value);
        break;
      case ACTUAL_AVAILABILITY:
        demand.setActualAvailability(value);
        break;
      case REQUIRED_EFFECTIVENESS:
        demand.setRequiredEffectiveness(value);
        break;
      case ACTUAL_EFFECTIVENESS:
        demand.setActualEffectiveness(value);
        break;
      case REQUIRED_PFD:
        demand.setRequiredPfd(value);
        break;
      case ACTUAL_PFD:
        demand.setActualPfd(value);
        break;
      default:
        break;
    }
  }

  /**
   * Adds referenced or embedded evidence to a demand case.
   *
   * @param input demand JSON
   * @param parsed parsed register context
   * @param demand demand case to update
   */
  private static void addEvidenceToDemand(JsonObject input,
      BarrierRegisterRunner.ParsedRegister parsed, SafetySystemDemand demand) {
    for (JsonElement ref : optArray(input, "evidenceRefs")) {
      DocumentEvidence evidence = parsed.evidenceById.get(ref.getAsString());
      if (evidence != null) {
        demand.addEvidence(evidence);
      }
    }
    for (JsonElement element : optArray(input, "evidence")) {
      if (element.isJsonObject()) {
        demand.addEvidence(parseEvidence(element.getAsJsonObject()));
      }
    }
  }

  /**
   * Parses top-level measurement devices.
   *
   * @param input top-level input
   * @return measurement devices
   */
  private static List<MeasurementDeviceInterface> parseMeasurementDevices(JsonObject input) {
    List<MeasurementDeviceInterface> devices = new ArrayList<MeasurementDeviceInterface>();
    JsonArray deviceArray = mergedArray(input, "measurementDevices", "detectors");
    for (JsonElement element : deviceArray) {
      if (element.isJsonObject()) {
        devices.add(parseMeasurementDevice(element.getAsJsonObject()));
      }
    }
    return devices;
  }

  /**
   * Parses one measurement device.
   *
   * @param input measurement-device JSON
   * @return fire or gas detector
   */
  private static MeasurementDeviceInterface parseMeasurementDevice(JsonObject input) {
    String type = optString(input, "type", "gas").toLowerCase();
    String name = optString(input, "name", optString(input, "tag", "DET-UNNAMED"));
    String location = optString(input, "location", optString(input, "coverageZone", ""));
    if (type.contains("fire")) {
      FireDetector detector = new FireDetector(name, location);
      detector.setTag(optString(input, "tag", name));
      if (hasNumber(input, "detectionDelaySeconds")) {
        detector.setDetectionDelay(input.get("detectionDelaySeconds").getAsDouble());
      }
      if (hasNumber(input, "responseTimeSeconds")) {
        detector.setDetectionDelay(input.get("responseTimeSeconds").getAsDouble());
      }
      if (hasNumber(input, "detectionThreshold")) {
        detector.setDetectionThreshold(input.get("detectionThreshold").getAsDouble());
      }
      if (hasNumber(input, "signalLevel")) {
        detector.setSignalLevel(input.get("signalLevel").getAsDouble());
      }
      if (optBoolean(input, "detected", false)) {
        detector.detectFire();
      }
      return detector;
    }
    GasDetector detector =
        new GasDetector(name, parseGasType(optString(input, "gasType", type)), location);
    detector.setTag(optString(input, "tag", name));
    detector.setGasSpecies(optString(input, "gasSpecies", "hydrocarbon"));
    if (hasNumber(input, "responseTimeSeconds")) {
      detector.setResponseTime(input.get("responseTimeSeconds").getAsDouble());
    }
    if (hasNumber(input, "gasConcentration")) {
      detector.setGasConcentration(input.get("gasConcentration").getAsDouble());
    }
    if (hasNumber(input, "lowerExplosiveLimit")) {
      detector.setLowerExplosiveLimit(input.get("lowerExplosiveLimit").getAsDouble());
    }
    return detector;
  }

  /**
   * Adds event/voting and quantitative SIFs from JSON to the analyzer.
   *
   * @param input top-level input
   * @param analyzer analyzer to update
   */
  private static void addSafetyInstrumentedFunctions(JsonObject input,
      SafetySystemPerformanceAnalyzer analyzer) {
    for (JsonElement element : mergedArray(input, "logicSifs", "eventVotingSifs")) {
      if (element.isJsonObject()) {
        analyzer.addSafetyInstrumentedFunction(parseLogicSif(element.getAsJsonObject()));
      }
    }
    for (JsonElement element : mergedArray(input, "quantitativeSifs",
        "quantitativeSafetyInstrumentedFunctions")) {
      if (element.isJsonObject()) {
        analyzer.addQuantitativeSafetyInstrumentedFunction(
            parseQuantitativeSif(element.getAsJsonObject()));
      }
    }
    for (JsonElement element : optArray(input, "safetyInstrumentedFunctions")) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject sif = element.getAsJsonObject();
      if (isQuantitativeSif(sif)) {
        analyzer.addQuantitativeSafetyInstrumentedFunction(parseQuantitativeSif(sif));
      } else {
        analyzer.addSafetyInstrumentedFunction(parseLogicSif(sif));
      }
    }
  }

  /**
   * Parses an event/voting SIF definition.
   *
   * @param input SIF JSON
   * @return event/voting SIF
   */
  private static SafetyInstrumentedFunction parseLogicSif(JsonObject input) {
    SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction(
        optString(input, "name", optString(input, "id", "SIF-UNNAMED")), parseVotingLogic(
            optString(input, "votingLogic", optString(input, "architecture", "1oo1"))));
    if (hasNumber(input, "maxBypassedDetectors")) {
      sif.setMaxBypassedDetectors(input.get("maxBypassedDetectors").getAsInt());
    }
    for (JsonElement element : optArray(input, "detectors")) {
      if (element.isJsonObject()) {
        sif.addDetector(parseLogicDetector(element.getAsJsonObject()));
      }
    }
    if (optBoolean(input, "overridden", false) || optBoolean(input, "override", false)) {
      sif.setOverride(true);
    }
    return sif;
  }

  /**
   * Parses one detector used inside an event/voting SIF.
   *
   * @param input detector JSON
   * @return detector object
   */
  private static Detector parseLogicDetector(JsonObject input) {
    Detector detector = new Detector(optString(input, "name", optString(input, "tag", "DET")),
        parseDetectorType(optString(input, "type", "GAS")),
        parseAlarmLevel(optString(input, "alarmLevel", "HIGH")), optDouble(input, "setpoint", 1.0),
        optString(input, "unit", ""));
    if (hasNumber(input, "measuredValue")) {
      detector.update(input.get("measuredValue").getAsDouble());
    }
    if (optBoolean(input, "tripped", false)) {
      detector.trip();
    }
    if (optBoolean(input, "bypassed", false)) {
      detector.setBypass(true);
    }
    if (optBoolean(input, "faulty", false)) {
      detector.setFaulty(true);
    }
    return detector;
  }

  /**
   * Parses one quantitative SIL/PFD SIF definition.
   *
   * @param input quantitative SIF JSON
   * @return quantitative safety instrumented function
   */
  private static neqsim.process.safety.risk.sis.SafetyInstrumentedFunction parseQuantitativeSif(
      JsonObject input) {
    int claimedSil =
        input.has("claimedSIL") ? input.get("claimedSIL").getAsInt() : optInt(input, "sil", 1);
    String architecture = optString(input, "architecture", "1oo1");
    double testIntervalHours = optDouble(input, "proofTestInterval_hours",
        optDouble(input, "proofTestIntervalHours", 8760.0));
    double pfd = parseQuantitativePfd(input, architecture, testIntervalHours);
    neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.Builder builder =
        neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.builder()
            .id(optString(input, "id", "")).name(optString(input, "name", "SIF-UNNAMED"))
            .description(optString(input, "description", "Safety instrumented function"))
            .sil(claimedSil).pfd(pfd).testIntervalHours(testIntervalHours)
            .mttr(optDouble(input, "mttr", optDouble(input, "mttrHours", 0.0)))
            .architecture(architecture).initiatingEvent(optString(input, "initiatingEvent", ""))
            .safeState(optString(input, "safeState", ""))
            .spuriousTripRate(optDouble(input, "spuriousTripRate", 0.0))
            .notes(optString(input, "notes", ""));
    builder.category(parseSifCategory(optString(input, "category", "OTHER")));
    builder.protectedEquipment(parseStringList(input, "protectedEquipment"));
    return builder.build();
  }

  /**
   * Parses or calculates PFDavg for a quantitative SIF.
   *
   * @param input SIF JSON
   * @param architecture default architecture
   * @param testIntervalHours proof-test interval in hours
   * @return PFDavg
   */
  private static double parseQuantitativePfd(JsonObject input, String architecture,
      double testIntervalHours) {
    if (hasNumber(input, "pfdAvg")) {
      return input.get("pfdAvg").getAsDouble();
    }
    if (hasNumber(input, "pfd")) {
      return input.get("pfd").getAsDouble();
    }
    JsonArray components = optArray(input, "components");
    if (components.size() == 0) {
      throw new IllegalArgumentException(
          "Quantitative SIF must include pfdAvg, pfd, or components");
    }
    double total = 0.0;
    for (JsonElement element : components) {
      JsonObject component = element.getAsJsonObject();
      if (hasNumber(component, "pfd")) {
        total += component.get("pfd").getAsDouble();
      } else if (hasNumber(component, "lambdaDU_per_hr")) {
        String componentArchitecture = optString(component, "architecture", architecture);
        total += computePfdForArchitecture(componentArchitecture,
            component.get("lambdaDU_per_hr").getAsDouble(), testIntervalHours);
      } else {
        throw new IllegalArgumentException(
            "Each quantitative SIF component must include pfd or lambdaDU_per_hr");
      }
    }
    return total;
  }

  /**
   * Builds a compact summary for the runner output.
   *
   * @param report safety-system performance report
   * @param demandCount number of demand cases parsed
   * @param deviceCount number of measurement devices parsed
   * @return summary JSON
   */
  private static JsonObject buildSummary(SafetySystemPerformanceReport report, int demandCount,
      int deviceCount) {
    JsonObject summary = new JsonObject();
    summary.addProperty("registerId", report.getRegisterId());
    summary.addProperty("name", report.getName());
    summary.addProperty("overallVerdict", report.getOverallVerdict().name());
    summary.addProperty("assessmentCount", report.getAssessments().size());
    summary.addProperty("passCount",
        report.countAssessments(SafetySystemPerformanceReport.Verdict.PASS));
    summary.addProperty("warningCount",
        report.countAssessments(SafetySystemPerformanceReport.Verdict.PASS_WITH_WARNINGS));
    summary.addProperty("failCount",
        report.countAssessments(SafetySystemPerformanceReport.Verdict.FAIL));
    summary.addProperty("demandCaseCount", demandCount);
    summary.addProperty("measurementDeviceCount", deviceCount);
    return summary;
  }

  /**
   * Builds standards-performance templates included in the runner output.
   *
   * @return standards template JSON
   */
  private static JsonObject buildStandardsTemplates() {
    JsonObject templates = new JsonObject();
    templates.add("NORSOK-S-001",
        buildStandardTemplate("NORSOK-S-001",
            "Technical safety screening template for safety critical systems",
            new String[] {"safetyFunction", "barrierId", "equipmentTags", "availability",
                "responseTimeSeconds", "demandCapacityMargin", "evidenceRefs"}));
    templates.add("ISO-13702",
        buildStandardTemplate("ISO-13702",
            "Control and mitigation of fires and explosions screening template",
            new String[] {"fireScenario", "delugeZone", "detectorCoverage", "PFPFireRatingMinutes",
                "firewaterCapacity", "impairmentStatus", "evidenceRefs"}));
    templates.add("TR1055-STYLE",
        buildStandardTemplate("TR1055-STYLE", "Barrier performance standard follow-up template",
            new String[] {"performanceRequirement", "acceptanceCriteria", "testInterval",
                "responsibleDiscipline", "verificationMethod", "currentStatus", "gapAction"}));
    return templates;
  }

  /**
   * Builds one standards template record.
   *
   * @param code standard or template code
   * @param description template description
   * @param fields expected fields
   * @return standard template JSON
   */
  private static JsonObject buildStandardTemplate(String code, String description,
      String[] fields) {
    JsonObject template = new JsonObject();
    template.addProperty("code", code);
    template.addProperty("description", description);
    template.add("fields", toStringArray(fields));
    template.addProperty("status", "screening-template");
    return template;
  }

  /**
   * Parses embedded document evidence.
   *
   * @param input evidence JSON
   * @return document evidence
   */
  private static DocumentEvidence parseEvidence(JsonObject input) {
    return new DocumentEvidence(optString(input, "evidenceId", ""),
        optString(input, "documentId", ""), optString(input, "documentTitle", ""),
        optString(input, "revision", ""), optString(input, "section", ""), optInt(input, "page", 0),
        optString(input, "sourceReference", ""), optString(input, "excerpt", ""),
        optDouble(input, "confidence", 0.0));
  }

  /**
   * Checks whether a SIF JSON object is quantitative.
   *
   * @param input SIF JSON
   * @return true when SIL/PFD fields are present
   */
  private static boolean isQuantitativeSif(JsonObject input) {
    return input.has("claimedSIL") || input.has("pfdAvg") || input.has("pfd")
        || input.has("components");
  }

  /**
   * Computes PFD for a detector/component architecture.
   *
   * @param architecture architecture string
   * @param lambdaDU dangerous undetected failure rate per hour
   * @param testInterval proof-test interval in hours
   * @return PFDavg
   */
  private static double computePfdForArchitecture(String architecture, double lambdaDU,
      double testInterval) {
    if ("1oo2".equalsIgnoreCase(architecture)) {
      return neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.calculatePfd1oo2(lambdaDU,
          testInterval);
    }
    if ("2oo3".equalsIgnoreCase(architecture)) {
      return neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.calculatePfd2oo3(lambdaDU,
          testInterval);
    }
    return neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.calculatePfd1oo1(lambdaDU,
        testInterval);
  }

  /**
   * Parses a safety-system category safely.
   *
   * @param value category text
   * @return category
   */
  private static SafetySystemCategory parseSafetySystemCategory(String value) {
    try {
      return SafetySystemCategory.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return SafetySystemCategory.UNKNOWN;
    }
  }

  /**
   * Parses gas-detector type safely.
   *
   * @param value gas-detector type text
   * @return gas-detector type
   */
  private static GasDetector.GasType parseGasType(String value) {
    try {
      String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
      if (normalized.contains("TOXIC")) {
        return GasDetector.GasType.TOXIC;
      }
      if (normalized.contains("OXYGEN")) {
        return GasDetector.GasType.OXYGEN;
      }
      return GasDetector.GasType.valueOf(normalized);
    } catch (Exception ex) {
      return GasDetector.GasType.COMBUSTIBLE;
    }
  }

  /**
   * Parses voting logic safely.
   *
   * @param value voting-logic text or notation
   * @return voting logic
   */
  private static VotingLogic parseVotingLogic(String value) {
    for (VotingLogic logic : VotingLogic.values()) {
      if (logic.getNotation().equalsIgnoreCase(value)) {
        return logic;
      }
    }
    try {
      return VotingLogic.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return VotingLogic.ONE_OUT_OF_ONE;
    }
  }

  /**
   * Parses detector type safely.
   *
   * @param value detector type text
   * @return detector type
   */
  private static DetectorType parseDetectorType(String value) {
    try {
      return DetectorType.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return DetectorType.GAS;
    }
  }

  /**
   * Parses detector alarm level safely.
   *
   * @param value alarm-level text
   * @return alarm level
   */
  private static AlarmLevel parseAlarmLevel(String value) {
    try {
      return AlarmLevel.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      if ("HH".equalsIgnoreCase(value)) {
        return AlarmLevel.HIGH_HIGH;
      }
      if ("LL".equalsIgnoreCase(value)) {
        return AlarmLevel.LOW_LOW;
      }
      return AlarmLevel.HIGH;
    }
  }

  /**
   * Parses quantitative SIF category safely.
   *
   * @param value SIF category text
   * @return SIF category
   */
  private static neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.SIFCategory parseSifCategory(
      String value) {
    try {
      return neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.SIFCategory
          .valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.SIFCategory.OTHER;
    }
  }

  /**
   * Parses a string array field.
   *
   * @param input JSON object
   * @param field array field
   * @return string list
   */
  private static List<String> parseStringList(JsonObject input, String field) {
    List<String> values = new ArrayList<String>();
    for (JsonElement element : optArray(input, field)) {
      values.add(element.getAsString());
    }
    return values;
  }

  /**
   * Merges two optional top-level arrays.
   *
   * @param input JSON object
   * @param first first field name
   * @param second second field name
   * @return merged array
   */
  private static JsonArray mergedArray(JsonObject input, String first, String second) {
    JsonArray merged = new JsonArray();
    for (JsonElement element : optArray(input, first)) {
      merged.add(element);
    }
    for (JsonElement element : optArray(input, second)) {
      merged.add(element);
    }
    return merged;
  }

  /**
   * Gets optional string value.
   *
   * @param input JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field value or default
   */
  private static String optString(JsonObject input, String field, String defaultValue) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      return defaultValue;
    }
    return input.get(field).getAsString();
  }

  /**
   * Gets optional integer value.
   *
   * @param input JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field value or default
   */
  private static int optInt(JsonObject input, String field, int defaultValue) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      return defaultValue;
    }
    return input.get(field).getAsInt();
  }

  /**
   * Gets optional double value.
   *
   * @param input JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field value or default
   */
  private static double optDouble(JsonObject input, String field, double defaultValue) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      return defaultValue;
    }
    return input.get(field).getAsDouble();
  }

  /**
   * Gets optional boolean value.
   *
   * @param input JSON object
   * @param field field name
   * @param defaultValue default value
   * @return field value or default
   */
  private static boolean optBoolean(JsonObject input, String field, boolean defaultValue) {
    if (!input.has(field) || input.get(field).isJsonNull()) {
      return defaultValue;
    }
    return input.get(field).getAsBoolean();
  }

  /**
   * Gets optional array value.
   *
   * @param input JSON object
   * @param field field name
   * @return field value or empty array
   */
  private static JsonArray optArray(JsonObject input, String field) {
    if (!input.has(field) || input.get(field).isJsonNull() || !input.get(field).isJsonArray()) {
      return new JsonArray();
    }
    return input.getAsJsonArray(field);
  }

  /**
   * Checks whether a numeric field is present.
   *
   * @param input JSON object
   * @param field field name
   * @return true when field is present and numeric
   */
  private static boolean hasNumber(JsonObject input, String field) {
    return input.has(field) && !input.get(field).isJsonNull() && input.get(field).isJsonPrimitive()
        && input.get(field).getAsJsonPrimitive().isNumber();
  }

  /**
   * Converts string values to a JSON array.
   *
   * @param values string values
   * @return JSON array
   */
  private static JsonArray toStringArray(String[] values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Builds an error JSON response.
   *
   * @param message error message
   * @return JSON string
   */
  private static String errorJson(String message) {
    JsonObject err = new JsonObject();
    err.addProperty("status", "error");
    err.addProperty("message", message);
    return err.toString();
  }

  /** Numeric demand-field selector. */
  private enum DemandNumber {
    /** Scenario demand value. */
    DEMAND_VALUE,
    /** Barrier capacity value. */
    CAPACITY_VALUE,
    /** Required response time. */
    REQUIRED_RESPONSE,
    /** Actual response time. */
    ACTUAL_RESPONSE,
    /** Required availability. */
    REQUIRED_AVAILABILITY,
    /** Actual availability. */
    ACTUAL_AVAILABILITY,
    /** Required effectiveness. */
    REQUIRED_EFFECTIVENESS,
    /** Actual effectiveness. */
    ACTUAL_EFFECTIVENESS,
    /** Required PFD. */
    REQUIRED_PFD,
    /** Actual PFD. */
    ACTUAL_PFD
  }
}
