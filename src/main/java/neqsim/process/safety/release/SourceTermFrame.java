package neqsim.process.safety.release;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.safety.release.ReleaseFlowResult.Station;

/**
 * Self-contained versioned safety-source frame. JSON contains explicit SI units, diagnostic failures and deterministic
 * ordering. No external simulator is needed.
 */
public final class SourceTermFrame implements Serializable {
  private static final long serialVersionUID = 1L;
  /** Version-one contract identifier. */
  public static final String SCHEMA_VERSION = "neqsim_safety_source_term.v1";
  /** Stable schema identity; the matching schema is shipped in the JAR. */
  public static final String SCHEMA_URI = "urn:neqsim:schema:safety-source-term:v1";
  private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
  private final String json;

  /** Frame lifecycle includes process freshness as well as release-model status. */
  public enum Status {
    /** Required calculation checks passed. */
    VALID,
    /** An optional diagnostic was unavailable. */
    VALID_WITH_WARNINGS,
    /** Required calculation failed. */
    INVALID,
    /** Requested model cannot represent the state. */
    UNSUPPORTED,
    /** Current process calculation could not be established. */
    STALE,
    /** Release opening explicitly disabled by the caller. */
    DISABLED
  }

  private SourceTermFrame(JsonObject payload) {
    JsonObject ordered = canonical(payload).getAsJsonObject();
    ordered.addProperty("fingerprint", digest(JSON.toJson(ordered)));
    json = JSON.toJson(ordered);
  }

  /**
   * Creates a frame from a release result. Invalid results contain no numeric source payload.
   *
   * @param scenarioId stable scenario identity
   * @param sourceId stable release-source identity
   * @param calculationId frame or session calculation UUID
   * @param sequence monotonically increasing sequence within a session
   * @param simulationTimeS simulation time in s
   * @param generatedAt UTC generation instant
   * @param request release geometry and thermodynamic-model provenance
   * @param result release result
   * @param provenance additional stable source/process metadata
   * @return immutable frame
   */
  public static SourceTermFrame calculated(String scenarioId, String sourceId, UUID calculationId, long sequence,
      double simulationTimeS, Instant generatedAt, ReleaseFlowRequest request, ReleaseFlowResult result,
      Map<String, String> provenance) {
    if (request == null || result == null) {
      throw new IllegalArgumentException("Request and result required");
    }
    JsonObject root = header(scenarioId, sourceId, calculationId, sequence, simulationTimeS, generatedAt, provenance);
    root.addProperty("status", result.getStatus().name());
    JsonObject model = new JsonObject();
    model.addProperty("id", result.getModelId());
    model.addProperty("version", result.getModelVersion());
    model.addProperty("fluidModel", request.getFluid().getModelName());
    model.addProperty("evidenceLevel", "UNQUALIFIED");
    model.add("evidence", evidence(result.getEvidence()));
    root.add("model", model);
    JsonArray diagnostics = new JsonArray();
    for (ReleaseFlowResult.Diagnostic diagnostic : result.getDiagnostics()) {
      diagnostics.add(diagnostic(diagnostic.getCode(), diagnostic.getMessage()));
    }
    root.add("diagnostics", diagnostics);
    if (result.isUsable()) {
      JsonObject source = new JsonObject();
      source.add("diameter", quantity(request.getDiameterM(), "m"));
      source.add("physicalArea", quantity(request.getAreaM2(), "m2"));
      source.add("effectiveArea", quantity(request.getEffectiveAreaM2(), "m2"));
      source.add("dischargeCoefficient", quantity(request.getDischargeCoefficient(), "1"));
      source.add("backPressure", quantity(request.getBackPressurePa(), "Pa"));
      if (request.hasFlowPath()) {
        source.add("flowPathLength", quantity(request.getFlowPathLengthM(), "m"));
        source.add("darcyFrictionFactor", quantity(request.getDarcyFrictionFactor(), "1"));
      }
      source.add("massFlowRate", quantity(result.getMassFlowRateKgS(), "kg/s"));
      source.addProperty("choked", result.isChoked());
      ReleaseState exit = result.getStations().get(Station.ORIFICE_EXIT);
      source.add("advectiveMomentumRate", quantity(result.getMassFlowRateKgS() * exit.getVelocityMs(), "N"));
      if (result.getThroatSoundSpeedMs() != null) {
        source.add("throatEquilibriumSoundSpeed", quantity(result.getThroatSoundSpeedMs(), "m/s"));
      }
      JsonObject stations = new JsonObject();
      for (Map.Entry<Station, ReleaseState> entry : result.getStations().entrySet()) {
        stations.add(entry.getKey().name(), state(entry.getValue()));
      }
      source.add("stations", stations);
      root.add("source", source);
    }
    return new SourceTermFrame(root);
  }

  /**
   * Creates a process failure/stale/disabled frame without reusing previous source quantities.
   *
   * @param scenarioId scenario identity
   * @param sourceId release-source identity
   * @param calculationId attempted calculation UUID
   * @param sequence session sequence
   * @param simulationTimeS last coherent simulation time in s
   * @param generatedAt UTC generation instant
   * @param status INVALID, UNSUPPORTED, STALE or DISABLED
   * @param code stable diagnostic code
   * @param message failure or lifecycle explanation
   * @param provenance process metadata
   * @return immutable frame with no source payload
   */
  public static SourceTermFrame unavailable(String scenarioId, String sourceId, UUID calculationId, long sequence,
      double simulationTimeS, Instant generatedAt, Status status, String code, String message,
      Map<String, String> provenance) {
    if (status == null || status == Status.VALID || status == Status.VALID_WITH_WARNINGS) {
      throw new IllegalArgumentException("Unavailable frame requires a failure or disabled status");
    }
    JsonObject root = header(scenarioId, sourceId, calculationId, sequence, simulationTimeS, generatedAt, provenance);
    root.addProperty("status", status.name());
    JsonArray diagnostics = new JsonArray();
    diagnostics.add(diagnostic(code, message));
    root.add("diagnostics", diagnostics);
    return new SourceTermFrame(root);
  }

  /**
   * Adds explicit source coordinates without changing the receiver.
   *
   * @param referenceFrame coordinate-system identifier, including origin/axis convention
   * @param positionM three Cartesian coordinates in m
   * @param direction three components of a unit direction vector
   * @return frame with location and a recomputed fingerprint
   */
  public SourceTermFrame withLocation(String referenceFrame, double[] positionM, double[] direction) {
    requireText(referenceFrame, "referenceFrame");
    if (positionM == null || direction == null || positionM.length != 3 || direction.length != 3) {
      throw new IllegalArgumentException("Three coordinates and three direction components required");
    }
    JsonObject location = new JsonObject();
    location.addProperty("referenceFrame", referenceFrame);
    JsonArray position = new JsonArray();
    JsonArray vector = new JsonArray();
    double norm = 0.0;
    for (int i = 0; i < 3; i++) {
      position.add(quantity(positionM[i], "m"));
      if (!Double.isFinite(direction[i])) {
        throw new IllegalArgumentException("Nonfinite direction");
      }
      vector.add(direction[i]);
      norm += direction[i] * direction[i];
    }
    if (Math.abs(norm - 1.0) > 1e-8) {
      throw new IllegalArgumentException("Direction must have unit length");
    }
    location.add("position", position);
    location.add("direction", vector);
    JsonObject root = payload();
    root.remove("fingerprint");
    root.add("location", location);
    return new SourceTermFrame(root);
  }

  private static JsonObject header(String scenario, String source, UUID id, long sequence, double time, Instant instant,
      Map<String, String> provenance) {
    requireText(scenario, "scenarioId");
    requireText(source, "sourceId");
    if (id == null || instant == null || sequence < 0 || !Double.isFinite(time) || time < 0.0 || provenance == null) {
      throw new IllegalArgumentException("UUID, instant, nonnegative sequence/time and provenance required");
    }
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("schemaUri", SCHEMA_URI);
    root.addProperty("scenarioId", scenario);
    root.addProperty("sourceId", source);
    root.addProperty("calculationId", id.toString());
    root.addProperty("sequence", sequence);
    root.addProperty("generatedAt", instant.toString());
    root.add("simulationTime", quantity(time, "s"));
    JsonObject metadata = new JsonObject();
    for (Map.Entry<String, String> entry : new TreeMap<String, String>(provenance).entrySet()) {
      requireText(entry.getKey(), "provenance key");
      requireText(entry.getValue(), "provenance value");
      metadata.addProperty(entry.getKey(), entry.getValue());
    }
    root.add("provenance", metadata);
    return root;
  }

  private static void requireText(String text, String name) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " required");
    }
  }

  private static JsonObject diagnostic(String code, String message) {
    requireText(code, "diagnostic code");
    requireText(message, "diagnostic message");
    JsonObject item = new JsonObject();
    item.addProperty("code", code);
    item.addProperty("message", message);
    return item;
  }

  private static JsonObject evidence(ReleaseModelEvidence manifest) {
    JsonObject value = new JsonObject();
    value.addProperty("manifestId", manifest.getManifestId());
    value.add("applicability", strings(manifest.getApplicability()));
    value.add("limitations", strings(manifest.getLimitations()));
    value.addProperty("independentEvidence", manifest.hasIndependentEvidence());
    JsonArray records = new JsonArray();
    for (ReleaseModelEvidence.Record record : manifest.getRecords()) {
      JsonObject item = new JsonObject();
      item.addProperty("id", record.getId());
      item.addProperty("type", record.getType().name());
      item.addProperty("reference", record.getReference());
      item.addProperty("description", record.getDescription());
      item.addProperty("independent", record.isIndependent());
      records.add(item);
    }
    value.add("records", records);
    return value;
  }

  private static JsonArray strings(Iterable<String> values) {
    JsonArray result = new JsonArray();
    for (String value : values) {
      result.add(value);
    }
    return result;
  }

  private static JsonObject quantity(double value, String unit) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Nonfinite quantity: " + unit);
    }
    JsonObject quantity = new JsonObject();
    quantity.addProperty("value", value == 0.0 ? 0.0 : value);
    quantity.addProperty("unit", unit);
    return quantity;
  }

  private static JsonObject fractions(Map<String, Double> values) {
    JsonObject result = new JsonObject();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      result.addProperty(entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static JsonObject quantities(Map<String, Double> values, String unit) {
    JsonObject result = new JsonObject();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      result.add(entry.getKey(), quantity(entry.getValue(), unit));
    }
    return result;
  }

  private static JsonObject state(ReleaseState state) {
    JsonObject value = new JsonObject();
    value.add("pressure", quantity(state.getPressurePa(), "Pa"));
    value.add("temperature", quantity(state.getTemperatureK(), "K"));
    value.add("density", quantity(state.getDensityKgM3(), "kg/m3"));
    value.add("specificEnthalpy", quantity(state.getEnthalpyJkg(), "J/kg"));
    value.add("specificEntropy", quantity(state.getEntropyJkgK(), "J/(kg K)"));
    value.add("velocity", quantity(state.getVelocityMs(), "m/s"));
    value.add("massFlux", quantity(state.getMassFluxKgM2s(), "kg/(m2 s)"));
    value.add("componentMoleFractions", fractions(state.getComponentMoleFractions()));
    value.add("componentMassFractions", fractions(state.getComponentMassFractions()));
    value.add("phaseMassFractions", fractions(state.getPhaseMassFractions()));
    value.add("phaseDensities", quantities(state.getPhaseDensitiesKgM3(), "kg/m3"));
    if (!state.getPhaseVelocitiesMs().isEmpty()) {
      value.add("phaseVelocities", quantities(state.getPhaseVelocitiesMs(), "m/s"));
    }
    return value;
  }

  private static JsonElement canonical(JsonElement value) {
    if (value.isJsonObject()) {
      JsonObject sorted = new JsonObject();
      Map<String, JsonElement> entries = new TreeMap<String, JsonElement>();
      for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
        entries.put(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
        sorted.add(entry.getKey(), canonical(entry.getValue()));
      }
      return sorted;
    }
    if (value.isJsonArray()) {
      JsonArray array = new JsonArray();
      for (JsonElement item : value.getAsJsonArray()) {
        array.add(canonical(item));
      }
      return array;
    }
    return value;
  }

  private static String digest(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte item : bytes) {
        hex.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private JsonObject payload() {
    return JsonParser.parseString(json).getAsJsonObject();
  }

  /** @return compact deterministic JSON, including SHA-256 fingerprint */
  public String toJson() {
    return json;
  }

  /** @return exactly one compact JSON frame followed by LF */
  public String toNdjson() {
    return json + "\n";
  }

  /** @return frame status */
  public Status getStatus() {
    return Status.valueOf(payload().get("status").getAsString());
  }

  /** @return session sequence */
  public long getSequence() {
    return payload().get("sequence").getAsLong();
  }

  /** @return simulation time in s */
  public double getSimulationTimeS() {
    return payload().getAsJsonObject("simulationTime").get("value").getAsDouble();
  }

  /**
   * Checks version and content fingerprint before further parsing. This is not full schema validation.
   *
   * @param json compact or whitespace-formatted JSON
   * @throws IllegalArgumentException for unknown version, malformed envelope or fingerprint mismatch
   */
  public static void verifyEnvelope(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (!SCHEMA_VERSION.equals(root.get("schemaVersion").getAsString())
          || !SCHEMA_URI.equals(root.get("schemaUri").getAsString())) {
        throw new IllegalArgumentException("Unsupported source-term schema");
      }
      String fingerprint = root.remove("fingerprint").getAsString();
      if (!fingerprint.equals(digest(JSON.toJson(canonical(root))))) {
        throw new IllegalArgumentException("Source-term fingerprint mismatch");
      }
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid source-term envelope: " + ex.getMessage(), ex);
    }
  }

  /** @return CSV header for the deliberately reduced time-series view */
  public static String csvHeader() {
    return "scenarioId,sourceId,sequence,simulationTime_s,status,massFlowRate_kg_s\n";
  }

  /** @return quoted CSV row; unavailable flow is empty, never zero */
  public String toCsvRow() {
    JsonObject root = payload();
    String rate = root.has("source")
        ? root.getAsJsonObject("source").getAsJsonObject("massFlowRate").get("value").getAsString()
        : "";
    return quote(root.get("scenarioId").getAsString()) + "," + quote(root.get("sourceId").getAsString()) + ","
        + getSequence() + "," + getSimulationTimeS() + "," + getStatus().name() + "," + rate + "\n";
  }

  private static String quote(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
