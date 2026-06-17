package neqsim.mcp.runners;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.barrier.BarrierRegister;
import neqsim.process.safety.barrier.DocumentEvidence;
import neqsim.process.safety.barrier.PerformanceStandard;
import neqsim.process.safety.barrier.SafetyBarrier;
import neqsim.process.safety.barrier.SafetyCriticalElement;

/**
 * MCP runner for barrier-register validation and safety-analysis handoff generation.
 *
 * <p>
 * The runner accepts JSON extracted from technical documentation and returns an audit-focused view
 * of safety critical elements (SCEs), barriers, performance standards, document evidence, and
 * handoff blocks for LOPA, SIL, bow-tie, and QRA workflows.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BarrierRegisterRunner {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor for utility class.
   */
  private BarrierRegisterRunner() {}

  /**
   * Runs barrier-register validation and handoff generation.
   *
   * @param json JSON with a barrier register, or a top-level {@code register} object
   * @return JSON string with validation and handoff blocks
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      JsonObject registerJson = input.has("register") && input.get("register").isJsonObject()
          ? input.getAsJsonObject("register")
          : input;
      ParsedRegister parsed = parseRegister(registerJson);
      JsonObject out = new JsonObject();
      out.addProperty("status", "success");
      out.addProperty("standard", "NORSOK S-001 / IEC 61511 / ISO 31000");
      out.add("summary", buildSummary(parsed));
      out.add("validation", buildValidation(parsed.register));
      out.add("impairedBarriers", buildImpairedBarriers(parsed.register));
      out.add("equipmentBarrierMap", buildEquipmentBarrierMap(parsed.register));
      out.add("lopaHandoff", buildLopaHandoff(parsed.register));
      out.add("silHandoff", buildSilHandoff(parsed.register));
      out.add("bowTieHandoff", buildBowTieHandoff(parsed.register));
      out.add("qraHandoff", buildQraHandoff(parsed.register));
      out.add("documentExtractionTemplate", buildDocumentExtractionTemplate());
      out.add("registerExport", GSON.toJsonTree(parsed.register.toMap()));
      return GSON.toJson(out);
    } catch (Exception e) {
      return errorJson("Barrier register analysis failed: " + e.getMessage());
    }
  }

  /**
   * Parses a barrier register JSON object into model objects.
   *
   * @param input register JSON object
   * @return parsed register context
   */
  static ParsedRegister parseRegister(JsonObject input) {
    ParsedRegister parsed = new ParsedRegister();
    parsed.register = new BarrierRegister(optString(input, "registerId", "BR-UNNAMED"))
        .setName(optString(input, "name", ""));

    JsonArray evidence = optArray(input, "evidence");
    for (JsonElement element : evidence) {
      DocumentEvidence item = parseEvidence(element.getAsJsonObject());
      parsed.register.addEvidence(item);
      parsed.evidenceById.put(item.getEvidenceId(), item);
    }

    JsonArray standards = optArray(input, "performanceStandards");
    for (JsonElement element : standards) {
      PerformanceStandard standard = parsePerformanceStandard(element.getAsJsonObject(), parsed);
      parsed.register.addPerformanceStandard(standard);
      parsed.standardsById.put(standard.getId(), standard);
    }

    JsonArray barriers = optArray(input, "barriers");
    for (JsonElement element : barriers) {
      SafetyBarrier barrier = parseBarrier(element.getAsJsonObject(), parsed);
      parsed.register.addBarrier(barrier);
      parsed.barriersById.put(barrier.getId(), barrier);
    }

    JsonArray elements = optArray(input, "safetyCriticalElements");
    for (JsonElement element : elements) {
      SafetyCriticalElement sce = parseSafetyCriticalElement(element.getAsJsonObject(), parsed);
      parsed.register.addSafetyCriticalElement(sce);
      for (SafetyBarrier barrier : sce.getBarriers()) {
        parsed.barriersById.put(barrier.getId(), barrier);
      }
    }
    return parsed;
  }

  /**
   * Parses document evidence from JSON.
   *
   * @param input evidence JSON
   * @return evidence object
   */
  private static DocumentEvidence parseEvidence(JsonObject input) {
    return new DocumentEvidence(optString(input, "evidenceId", ""),
        optString(input, "documentId", ""), optString(input, "documentTitle", ""),
        optString(input, "revision", ""), optString(input, "section", ""), optInt(input, "page", 0),
        optString(input, "sourceReference", ""), optString(input, "excerpt", ""),
        optDouble(input, "confidence", 0.0));
  }

  /**
   * Parses a performance standard from JSON.
   *
   * @param input performance-standard JSON
   * @param parsed parsed register context
   * @return performance standard
   */
  private static PerformanceStandard parsePerformanceStandard(JsonObject input,
      ParsedRegister parsed) {
    PerformanceStandard standard =
        new PerformanceStandard(optString(input, "id", "")).setTitle(optString(input, "title", ""))
            .setSafetyFunction(optString(input, "safetyFunction", ""))
            .setDemandMode(parseDemandMode(optString(input, "demandMode", "OTHER")));
    if (hasNumber(input, "targetPfd")) {
      standard.setTargetPfd(input.get("targetPfd").getAsDouble());
    }
    if (hasNumber(input, "requiredAvailability")) {
      standard.setRequiredAvailability(input.get("requiredAvailability").getAsDouble());
    }
    if (hasNumber(input, "proofTestIntervalHours")) {
      standard.setProofTestIntervalHours(input.get("proofTestIntervalHours").getAsDouble());
    }
    if (hasNumber(input, "responseTimeSeconds")) {
      standard.setResponseTimeSeconds(input.get("responseTimeSeconds").getAsDouble());
    }
    JsonArray criteria = optArray(input, "acceptanceCriteria");
    for (JsonElement criterion : criteria) {
      standard.addAcceptanceCriterion(criterion.getAsString());
    }
    linkEvidenceToStandard(input, standard, parsed);
    return standard;
  }

  /**
   * Parses a safety barrier from JSON.
   *
   * @param input barrier JSON
   * @param parsed parsed register context
   * @return safety barrier
   */
  private static SafetyBarrier parseBarrier(JsonObject input, ParsedRegister parsed) {
    SafetyBarrier barrier = new SafetyBarrier(optString(input, "id", ""))
        .setName(optString(input, "name", "")).setDescription(optString(input, "description", ""))
        .setType(parseBarrierType(optString(input, "type", "PREVENTION")))
        .setStatus(parseBarrierStatus(optString(input, "status", "UNKNOWN")))
        .setSafetyFunction(optString(input, "safetyFunction", ""))
        .setOwner(optString(input, "owner", ""));
    if (hasNumber(input, "pfd")) {
      barrier.setPfd(input.get("pfd").getAsDouble());
    }
    if (hasNumber(input, "effectiveness")) {
      barrier.setEffectiveness(input.get("effectiveness").getAsDouble());
    }
    addStringArrayValues(input, "equipmentTags", new StringConsumer() {
      @Override
      public void accept(String value) {
        barrier.addEquipmentTag(value);
      }
    });
    addStringArrayValues(input, "linkedEquipmentTags", new StringConsumer() {
      @Override
      public void accept(String value) {
        barrier.addEquipmentTag(value);
      }
    });
    addStringArrayValues(input, "hazardIds", new StringConsumer() {
      @Override
      public void accept(String value) {
        barrier.addHazardId(value);
      }
    });
    addStringArrayValues(input, "linkedHazardIds", new StringConsumer() {
      @Override
      public void accept(String value) {
        barrier.addHazardId(value);
      }
    });
    linkPerformanceStandard(input, barrier, parsed);
    linkEvidenceToBarrier(input, barrier, parsed);
    return barrier;
  }

  /**
   * Parses a safety critical element from JSON.
   *
   * @param input SCE JSON
   * @param parsed parsed register context
   * @return safety critical element
   */
  private static SafetyCriticalElement parseSafetyCriticalElement(JsonObject input,
      ParsedRegister parsed) {
    final SafetyCriticalElement sce = new SafetyCriticalElement(optString(input, "id", ""))
        .setTag(optString(input, "tag", "")).setName(optString(input, "name", ""))
        .setType(parseElementType(optString(input, "type", "OTHER")))
        .setOwner(optString(input, "owner", ""));
    addStringArrayValues(input, "equipmentTags", new StringConsumer() {
      @Override
      public void accept(String value) {
        sce.addEquipmentTag(value);
      }
    });
    linkEvidenceToElement(input, sce, parsed);
    addStringArrayValues(input, "barrierRefs", new StringConsumer() {
      @Override
      public void accept(String value) {
        SafetyBarrier barrier = parsed.barriersById.get(value);
        if (barrier != null) {
          sce.addBarrier(barrier);
        }
      }
    });
    JsonArray barriers = optArray(input, "barriers");
    for (JsonElement element : barriers) {
      SafetyBarrier barrier = parseBarrier(element.getAsJsonObject(), parsed);
      sce.addBarrier(barrier);
      parsed.register.addBarrier(barrier);
      parsed.barriersById.put(barrier.getId(), barrier);
    }
    return sce;
  }

  /**
   * Links a performance standard to a barrier from references or embedded JSON.
   *
   * @param input barrier JSON
   * @param barrier barrier object
   * @param parsed parsed register context
   */
  private static void linkPerformanceStandard(JsonObject input, SafetyBarrier barrier,
      ParsedRegister parsed) {
    String standardId = optString(input, "performanceStandardId", "");
    if (!standardId.isEmpty() && parsed.standardsById.containsKey(standardId)) {
      barrier.setPerformanceStandard(parsed.standardsById.get(standardId));
    }
    if (input.has("performanceStandard") && input.get("performanceStandard").isJsonObject()) {
      PerformanceStandard standard =
          parsePerformanceStandard(input.getAsJsonObject("performanceStandard"), parsed);
      parsed.standardsById.put(standard.getId(), standard);
      parsed.register.addPerformanceStandard(standard);
      barrier.setPerformanceStandard(standard);
    }
  }

  /**
   * Adds referenced and embedded evidence to a standard.
   *
   * @param input JSON object carrying evidence fields
   * @param standard target standard
   * @param parsed parsed register context
   */
  private static void linkEvidenceToStandard(JsonObject input, PerformanceStandard standard,
      ParsedRegister parsed) {
    addEvidenceRefs(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        standard.addEvidence(evidence);
      }
    });
    addEmbeddedEvidence(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        standard.addEvidence(evidence);
      }
    });
  }

  /**
   * Adds referenced and embedded evidence to a barrier.
   *
   * @param input JSON object carrying evidence fields
   * @param barrier target barrier
   * @param parsed parsed register context
   */
  private static void linkEvidenceToBarrier(JsonObject input, SafetyBarrier barrier,
      ParsedRegister parsed) {
    addEvidenceRefs(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        barrier.addEvidence(evidence);
      }
    });
    addEmbeddedEvidence(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        barrier.addEvidence(evidence);
      }
    });
  }

  /**
   * Adds referenced and embedded evidence to an SCE.
   *
   * @param input JSON object carrying evidence fields
   * @param element target SCE
   * @param parsed parsed register context
   */
  private static void linkEvidenceToElement(JsonObject input, SafetyCriticalElement element,
      ParsedRegister parsed) {
    addEvidenceRefs(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        element.addEvidence(evidence);
      }
    });
    addEmbeddedEvidence(input, parsed, new EvidenceConsumer() {
      @Override
      public void accept(DocumentEvidence evidence) {
        element.addEvidence(evidence);
      }
    });
  }

  /**
   * Adds evidence referenced by {@code evidenceRefs}.
   *
   * @param input JSON object carrying evidenceRefs
   * @param parsed parsed register context
   * @param consumer evidence consumer
   */
  private static void addEvidenceRefs(JsonObject input, ParsedRegister parsed,
      EvidenceConsumer consumer) {
    JsonArray refs = optArray(input, "evidenceRefs");
    for (JsonElement ref : refs) {
      DocumentEvidence evidence = parsed.evidenceById.get(ref.getAsString());
      if (evidence != null) {
        consumer.accept(evidence);
      }
    }
  }

  /**
   * Adds embedded evidence objects from an {@code evidence} array.
   *
   * @param input JSON object carrying evidence
   * @param parsed parsed register context
   * @param consumer evidence consumer
   */
  private static void addEmbeddedEvidence(JsonObject input, ParsedRegister parsed,
      EvidenceConsumer consumer) {
    JsonArray evidence = optArray(input, "evidence");
    for (JsonElement element : evidence) {
      DocumentEvidence item = parseEvidence(element.getAsJsonObject());
      consumer.accept(item);
      if (!item.getEvidenceId().isEmpty()) {
        parsed.evidenceById.put(item.getEvidenceId(), item);
      }
    }
  }

  /**
   * Builds top-level summary metrics.
   *
   * @param parsed parsed register context
   * @return summary JSON
   */
  private static JsonObject buildSummary(ParsedRegister parsed) {
    JsonObject summary = new JsonObject();
    int available = 0;
    int traceable = 0;
    for (SafetyBarrier barrier : parsed.register.getBarriers()) {
      if (barrier.isAvailable()) {
        available++;
      }
      if (barrier.hasTraceableEvidence()) {
        traceable++;
      }
    }
    summary.addProperty("registerId", parsed.register.getRegisterId());
    summary.addProperty("name", parsed.register.getName());
    summary.addProperty("sceCount", parsed.register.getSafetyCriticalElements().size());
    summary.addProperty("barrierCount", parsed.register.getBarriers().size());
    summary.addProperty("availableBarrierCount", available);
    summary.addProperty("impairedBarrierCount", parsed.register.getImpairedBarriers().size());
    summary.addProperty("barriersWithTraceableEvidence", traceable);
    summary.addProperty("performanceStandardCount",
        parsed.register.getPerformanceStandards().size());
    summary.addProperty("registerEvidenceItemCount", parsed.evidenceById.size());
    return summary;
  }

  /**
   * Builds validation findings.
   *
   * @param register barrier register
   * @return validation JSON
   */
  private static JsonObject buildValidation(BarrierRegister register) {
    JsonObject validation = new JsonObject();
    JsonArray findings = new JsonArray();
    for (String finding : register.validate()) {
      JsonObject row = new JsonObject();
      row.addProperty("severity", severityForFinding(finding));
      row.addProperty("message", finding);
      row.addProperty("remediation", remediationForFinding(finding));
      findings.add(row);
    }
    validation.addProperty("valid", findings.size() == 0);
    validation.addProperty("findingCount", findings.size());
    validation.add("findings", findings);
    return validation;
  }

  /**
   * Builds impaired-barrier list.
   *
   * @param register barrier register
   * @return impaired barrier array
   */
  private static JsonArray buildImpairedBarriers(BarrierRegister register) {
    JsonArray array = new JsonArray();
    for (SafetyBarrier barrier : register.getImpairedBarriers()) {
      JsonObject row = new JsonObject();
      row.addProperty("id", barrier.getId());
      row.addProperty("name", barrier.getName());
      row.addProperty("status", barrier.getStatus().name());
      row.addProperty("canCredit", false);
      row.add("equipmentTags", toStringArray(barrier.getLinkedEquipmentTags()));
      row.add("hazardIds", toStringArray(barrier.getLinkedHazardIds()));
      array.add(row);
    }
    return array;
  }

  /**
   * Builds equipment-to-barrier mapping.
   *
   * @param register barrier register
   * @return mapping JSON object
   */
  private static JsonObject buildEquipmentBarrierMap(BarrierRegister register) {
    Map<String, JsonArray> map = new LinkedHashMap<String, JsonArray>();
    for (SafetyBarrier barrier : register.getBarriers()) {
      for (String tag : barrier.getLinkedEquipmentTags()) {
        if (!map.containsKey(tag)) {
          map.put(tag, new JsonArray());
        }
        JsonObject row = new JsonObject();
        row.addProperty("id", barrier.getId());
        row.addProperty("name", barrier.getName());
        row.addProperty("type", barrier.getType().name());
        row.addProperty("status", barrier.getStatus().name());
        row.addProperty("pfd", barrier.getPfd());
        row.addProperty("canCredit", canCreditBarrier(barrier));
        map.get(tag).add(row);
      }
    }
    JsonObject out = new JsonObject();
    for (Map.Entry<String, JsonArray> entry : map.entrySet()) {
      out.add(entry.getKey(), entry.getValue());
    }
    return out;
  }

  /**
   * Builds a LOPA handoff block from available PFD-bearing barriers.
   *
   * @param register barrier register
   * @return LOPA handoff JSON
   */
  private static JsonObject buildLopaHandoff(BarrierRegister register) {
    JsonObject out = new JsonObject();
    JsonArray layers = new JsonArray();
    JsonArray excluded = new JsonArray();
    for (SafetyBarrier barrier : register.getBarriers()) {
      if (canCreditBarrier(barrier)) {
        JsonObject layer = new JsonObject();
        layer.addProperty("name", layerName(barrier));
        layer.addProperty("pfd", barrier.getPfd());
        layer.addProperty("rrf", barrier.getRiskReductionFactor());
        layer.addProperty("barrierId", barrier.getId());
        layer.addProperty("independenceNeedsReview", true);
        layer.add("equipmentTags", toStringArray(barrier.getLinkedEquipmentTags()));
        layer.add("evidence", evidenceSummary(barrier));
        layers.add(layer);
      } else {
        JsonObject row = new JsonObject();
        row.addProperty("barrierId", barrier.getId());
        row.addProperty("name", barrier.getName());
        row.addProperty("reason", exclusionReason(barrier));
        excluded.add(row);
      }
    }
    out.addProperty("scenarioField", "scenario");
    out.addProperty("targetFrequencyField", "targetFrequency_per_year");
    out.add("layers", layers);
    out.add("excluded", excluded);
    out.addProperty("note", "Review IPL independence before claiming LOPA credit.");
    return out;
  }

  /**
   * Builds a SIL handoff block from SIF-like barriers and performance standards.
   *
   * @param register barrier register
   * @return SIL handoff JSON
   */
  private static JsonObject buildSilHandoff(BarrierRegister register) {
    JsonObject out = new JsonObject();
    JsonArray candidates = new JsonArray();
    for (SafetyBarrier barrier : register.getBarriers()) {
      PerformanceStandard standard = barrier.getPerformanceStandard();
      if (standard == null && !isFiniteProbability(barrier.getPfd())) {
        continue;
      }
      JsonObject row = new JsonObject();
      row.addProperty("name", layerName(barrier));
      row.addProperty("barrierId", barrier.getId());
      row.addProperty("claimedPfd", barrier.getPfd());
      row.addProperty("achievedSIL", silFromPfd(barrier.getPfd()));
      if (standard != null) {
        row.addProperty("performanceStandardId", standard.getId());
        row.addProperty("demandMode", standard.getDemandMode().name());
        row.addProperty("targetPfd", standard.getTargetPfd());
        row.addProperty("targetMet",
            isFiniteProbability(barrier.getPfd()) && isFiniteProbability(standard.getTargetPfd())
                && barrier.getPfd() <= standard.getTargetPfd());
        row.addProperty("proofTestInterval_hours", standard.getProofTestIntervalHours());
        row.addProperty("responseTime_seconds", standard.getResponseTimeSeconds());
      }
      row.add("equipmentTags", toStringArray(barrier.getLinkedEquipmentTags()));
      row.add("evidence", evidenceSummary(barrier));
      candidates.add(row);
    }
    out.add("candidates", candidates);
    out.addProperty("tool", "runSIL");
    return out;
  }

  /**
   * Builds a bow-tie handoff block.
   *
   * @param register barrier register
   * @return bow-tie handoff JSON
   */
  private static JsonObject buildBowTieHandoff(BarrierRegister register) {
    JsonObject out = new JsonObject();
    JsonArray barriers = new JsonArray();
    for (SafetyBarrier barrier : register.getBarriers()) {
      JsonObject row = new JsonObject();
      row.addProperty("id", barrier.getId());
      row.addProperty("description", layerName(barrier));
      row.addProperty("barrierType", barrier.getType().name());
      row.addProperty("pfd", barrier.getPfd());
      row.addProperty("functional", barrier.isAvailable());
      row.addProperty("owner", barrier.getOwner());
      row.add("hazardIds", toStringArray(barrier.getLinkedHazardIds()));
      barriers.add(row);
    }
    out.add("barriers", barriers);
    out.addProperty("tool", "BowTieModel / BowTieAnalyzer");
    return out;
  }

  /**
   * Builds a QRA screening handoff block grouped by hazard identifier.
   *
   * @param register barrier register
   * @return QRA handoff JSON
   */
  private static JsonObject buildQraHandoff(BarrierRegister register) {
    Map<String, HazardAdjustment> adjustments = new LinkedHashMap<String, HazardAdjustment>();
    for (SafetyBarrier barrier : register.getBarriers()) {
      for (String hazardId : barrier.getLinkedHazardIds()) {
        if (!adjustments.containsKey(hazardId)) {
          adjustments.put(hazardId, new HazardAdjustment(hazardId));
        }
        adjustments.get(hazardId).add(barrier);
      }
    }
    JsonObject out = new JsonObject();
    JsonArray hazards = new JsonArray();
    for (HazardAdjustment adjustment : adjustments.values()) {
      hazards.add(adjustment.toJson());
    }
    out.addProperty("screeningOnly", true);
    out.addProperty("note",
        "Use multipliers as transparent screening inputs before detailed event-tree review.");
    out.add("hazards", hazards);
    return out;
  }

  /**
   * Builds an agent extraction template for technical-document readers.
   *
   * @return extraction template JSON
   */
  static JsonObject buildDocumentExtractionTemplate() {
    JsonObject template = new JsonObject();
    template.add("sourceDocuments",
        toStringArray(new String[] {"P&ID", "C&E chart", "SRS", "SIL verification report",
            "firewater datasheet", "detector layout", "PFP schedule", "inspection report",
            "vendor datasheet"}));
    template.add("targetObjects", toStringArray(new String[] {"DocumentEvidence",
        "PerformanceStandard", "SafetyBarrier", "SafetyCriticalElement", "BarrierRegister"}));
    template.add("minimumFields",
        toStringArray(
            new String[] {"documentId", "revision", "sourceReference", "excerpt", "confidence",
                "equipmentTags", "pfd or effectiveness", "status", "performanceStandardId"}));
    template.add("causeAndEffect",
        buildExtractionSection("Cause-and-effect chart",
            new String[] {"causeId", "initiatingDetectorTag", "initiatingEvent", "votingLogic",
                "effectAction", "finalElementTag", "delaySeconds", "resetRequirement",
                "bypassOrInhibitState", "evidenceRefs"}));
    template.add("safetyRequirementsSpecification",
        buildExtractionSection("Safety requirements specification",
            new String[] {"sifId", "safetyFunction", "protectedEquipment", "safeState",
                "claimedSIL", "targetPfd", "proofTestIntervalHours", "responseTimeSeconds",
                "architecture", "components", "evidenceRefs"}));
    template.add("firewaterDatasheet",
        buildExtractionSection("Firewater and deluge datasheet",
            new String[] {"delugeZone", "protectedEquipment", "applicationRate", "rateUnit",
                "firewaterCapacity", "capacityUnit", "minimumPressure", "responseTimeSeconds",
                "designBasisFire", "standardReference", "evidenceRefs"}));
    template.add("detectorLayout",
        buildExtractionSection("Detector layout and F&G coverage",
            new String[] {"detectorTag", "detectorType", "gasSpecies", "location", "coverageZone",
                "setpoint", "setpointUnit", "responseTimeSeconds", "votingGroup", "evidenceRefs"}));
    template.add("pfpSchedule",
        buildExtractionSection("Passive fire protection schedule",
            new String[] {"protectedTag", "protectedArea", "fireRatingMinutes", "heatFluxRating",
                "ratingUnit", "material", "thickness", "thicknessUnit", "inspectionStatus",
                "evidenceRefs"}));
    return template;
  }

  /**
   * Builds one document-extraction template section.
   *
   * @param documentType source document type
   * @param fields fields agents should extract from that document type
   * @return extraction-section JSON object
   */
  private static JsonObject buildExtractionSection(String documentType, String[] fields) {
    JsonObject section = new JsonObject();
    section.addProperty("documentType", documentType);
    section.add("fields", toStringArray(fields));
    section.addProperty("evidenceRule",
        "Each extracted row should include sourceReference, excerpt, confidence, and evidenceRefs.");
    return section;
  }

  /**
   * Adds string array values to a consumer.
   *
   * @param input JSON object
   * @param field field name
   * @param consumer string consumer
   */
  private static void addStringArrayValues(JsonObject input, String field,
      StringConsumer consumer) {
    JsonArray array = optArray(input, field);
    for (JsonElement element : array) {
      consumer.accept(element.getAsString());
    }
  }

  /**
   * Gets an optional string field.
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
   * Gets an optional integer field.
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
   * Gets an optional double field.
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
   * Gets an optional array field.
   *
   * @param input JSON object
   * @param field field name
   * @return field array or empty array
   */
  private static JsonArray optArray(JsonObject input, String field) {
    if (!input.has(field) || input.get(field).isJsonNull() || !input.get(field).isJsonArray()) {
      return new JsonArray();
    }
    return input.getAsJsonArray(field);
  }

  /**
   * Checks whether a numeric JSON field is present.
   *
   * @param input JSON object
   * @param field field name
   * @return true when present and not null
   */
  private static boolean hasNumber(JsonObject input, String field) {
    return input.has(field) && !input.get(field).isJsonNull();
  }

  /**
   * Parses demand mode enum safely.
   *
   * @param value enum text
   * @return demand mode
   */
  private static PerformanceStandard.DemandMode parseDemandMode(String value) {
    try {
      return PerformanceStandard.DemandMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return PerformanceStandard.DemandMode.OTHER;
    }
  }

  /**
   * Parses barrier type safely.
   *
   * @param value enum text
   * @return barrier type
   */
  private static SafetyBarrier.BarrierType parseBarrierType(String value) {
    try {
      return SafetyBarrier.BarrierType.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return SafetyBarrier.BarrierType.PREVENTION;
    }
  }

  /**
   * Parses barrier status safely.
   *
   * @param value enum text
   * @return barrier status
   */
  private static SafetyBarrier.BarrierStatus parseBarrierStatus(String value) {
    try {
      return SafetyBarrier.BarrierStatus.valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return SafetyBarrier.BarrierStatus.UNKNOWN;
    }
  }

  /**
   * Parses SCE element type safely.
   *
   * @param value enum text
   * @return SCE element type
   */
  private static SafetyCriticalElement.ElementType parseElementType(String value) {
    try {
      return SafetyCriticalElement.ElementType
          .valueOf(value.trim().toUpperCase().replace('-', '_'));
    } catch (Exception ex) {
      return SafetyCriticalElement.ElementType.OTHER;
    }
  }

  /**
   * Checks whether a barrier can be credited in a quantitative handoff.
   *
   * @param barrier barrier to check
   * @return true when available and PFD is in (0,1]
   */
  private static boolean canCreditBarrier(SafetyBarrier barrier) {
    return barrier.isAvailable() && isFiniteProbability(barrier.getPfd())
        && barrier.getPerformanceStandard() != null && barrier.hasTraceableEvidence();
  }

  /**
   * Checks whether a value is a finite probability in (0,1].
   *
   * @param value probability value
   * @return true for finite probability
   */
  private static boolean isFiniteProbability(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0 && value <= 1.0;
  }

  /**
   * Returns a compact barrier label.
   *
   * @param barrier barrier
   * @return label
   */
  private static String layerName(SafetyBarrier barrier) {
    if (barrier.getName() != null && !barrier.getName().trim().isEmpty()) {
      return barrier.getName();
    }
    if (barrier.getDescription() != null && !barrier.getDescription().trim().isEmpty()) {
      return barrier.getDescription();
    }
    return barrier.getId();
  }

  /**
   * Builds a reason a barrier was excluded from LOPA credit.
   *
   * @param barrier barrier
   * @return exclusion reason
   */
  private static String exclusionReason(SafetyBarrier barrier) {
    if (!barrier.isAvailable()) {
      return "Barrier status is " + barrier.getStatus().name();
    }
    if (!isFiniteProbability(barrier.getPfd())) {
      return "Missing valid PFD in range (0,1]";
    }
    if (barrier.getPerformanceStandard() == null) {
      return "Missing linked performance standard";
    }
    if (!barrier.hasTraceableEvidence()) {
      return "Missing traceable document evidence";
    }
    return "Not eligible for credit";
  }

  /**
   * Maps a validation finding to a severity.
   *
   * @param finding finding text
   * @return severity string
   */
  private static String severityForFinding(String finding) {
    String text = finding.toLowerCase();
    if (text.contains("id is missing") || text.contains("contains no")) {
      return "error";
    }
    return "warning";
  }

  /**
   * Maps a validation finding to a remediation hint.
   *
   * @param finding finding text
   * @return remediation text
   */
  private static String remediationForFinding(String finding) {
    String text = finding.toLowerCase();
    if (text.contains("traceable")) {
      return "Link a DocumentEvidence item with documentId/sourceReference and excerpt.";
    }
    if (text.contains("performance standard")) {
      return "Create or reference a PerformanceStandard with acceptance criteria and evidence.";
    }
    if (text.contains("equipment tag")) {
      return "Add equipmentTags so barriers can be tied to P&ID/process equipment.";
    }
    if (text.contains("status is unknown")) {
      return "Set status to AVAILABLE, IMPAIRED, BYPASSED, or OUT_OF_SERVICE.";
    }
    return "Review extracted technical documentation and complete the missing field.";
  }

  /**
   * Converts list of strings to a JSON array.
   *
   * @param values string values
   * @return JSON array
   */
  private static JsonArray toStringArray(java.util.List<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
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
   * Builds evidence summary for a barrier.
   *
   * @param barrier barrier
   * @return evidence summary JSON
   */
  private static JsonObject evidenceSummary(SafetyBarrier barrier) {
    JsonObject summary = new JsonObject();
    summary.addProperty("traceable", barrier.hasTraceableEvidence());
    summary.addProperty("directEvidenceCount", barrier.getEvidence().size());
    summary.addProperty("hasPerformanceStandardEvidence", barrier.getPerformanceStandard() != null
        && barrier.getPerformanceStandard().hasTraceableEvidence());
    return summary;
  }

  /**
   * Maps PFDavg to achieved SIL band.
   *
   * @param pfd probability of failure on demand
   * @return SIL 0-4, where 0 means no SIL band achieved
   */
  private static int silFromPfd(double pfd) {
    if (!isFiniteProbability(pfd) || pfd > 1.0e-1) {
      return 0;
    }
    if (pfd <= 1.0e-5) {
      return 4;
    }
    if (pfd <= 1.0e-4) {
      return 4;
    }
    if (pfd <= 1.0e-3) {
      return 3;
    }
    if (pfd <= 1.0e-2) {
      return 2;
    }
    return 1;
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

  /** Parsed register context and lookup maps. */
  static class ParsedRegister {
    BarrierRegister register;
    final Map<String, DocumentEvidence> evidenceById =
        new LinkedHashMap<String, DocumentEvidence>();
    final Map<String, PerformanceStandard> standardsById =
        new LinkedHashMap<String, PerformanceStandard>();
    final Map<String, SafetyBarrier> barriersById = new LinkedHashMap<String, SafetyBarrier>();
  }

  /** Hazard-level screening adjustment. */
  private static class HazardAdjustment {
    private final String hazardId;
    private double topEventFrequencyMultiplier = 1.0;
    private double consequenceMultiplier = 1.0;
    private final JsonArray creditedBarriers = new JsonArray();

    /**
     * Creates an adjustment record.
     *
     * @param hazardId hazard identifier
     */
    HazardAdjustment(String hazardId) {
      this.hazardId = hazardId;
    }

    /**
     * Adds barrier contribution to the adjustment.
     *
     * @param barrier barrier
     */
    void add(SafetyBarrier barrier) {
      JsonObject row = new JsonObject();
      row.addProperty("barrierId", barrier.getId());
      row.addProperty("name", layerName(barrier));
      row.addProperty("type", barrier.getType().name());
      row.addProperty("status", barrier.getStatus().name());
      row.addProperty("credited", false);
      if (canCreditBarrier(barrier) && (barrier.getType() == SafetyBarrier.BarrierType.PREVENTION
          || barrier.getType() == SafetyBarrier.BarrierType.BOTH)) {
        topEventFrequencyMultiplier *= barrier.getPfd();
        row.addProperty("credited", true);
        row.addProperty("creditType", "preventiveFrequencyMultiplier");
        row.addProperty("multiplier", barrier.getPfd());
      }
      if (barrier.isAvailable() && barrier.hasTraceableEvidence()
          && (barrier.getType() == SafetyBarrier.BarrierType.MITIGATION
              || barrier.getType() == SafetyBarrier.BarrierType.BOTH)
          && isFiniteProbability(barrier.getEffectiveness())) {
        double multiplier = 1.0 - barrier.getEffectiveness();
        consequenceMultiplier *= Math.max(0.0, multiplier);
        row.addProperty("credited", true);
        row.addProperty("creditType", "mitigationConsequenceMultiplier");
        row.addProperty("multiplier", Math.max(0.0, multiplier));
      }
      creditedBarriers.add(row);
    }

    /**
     * Converts the adjustment to JSON.
     *
     * @return JSON object
     */
    JsonObject toJson() {
      JsonObject out = new JsonObject();
      out.addProperty("hazardId", hazardId);
      out.addProperty("topEventFrequencyMultiplier", topEventFrequencyMultiplier);
      out.addProperty("consequenceMultiplier", consequenceMultiplier);
      out.add("barriers", creditedBarriers);
      return out;
    }
  }

  /** String callback interface. */
  private interface StringConsumer {
    /**
     * Consumes a string value.
     *
     * @param value string value
     */
    void accept(String value);
  }

  /** Evidence callback interface. */
  private interface EvidenceConsumer {
    /**
     * Consumes an evidence object.
     *
     * @param evidence evidence object
     */
    void accept(DocumentEvidence evidence);
  }
}
