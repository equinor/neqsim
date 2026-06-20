package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Structured, never-throwing result of building a multi-area {@link ProcessModel} from JSON.
 *
 * <p>
 * This is the model-level counterpart to {@link SimulationResult}. While {@link ProcessModel#fromJson(String)} only
 * logs per-area build failures and {@link ProcessModel#fromJsonAndRun(String)} throws when execution fails, this result
 * object surfaces per-area {@link SimulationResult}s, inter-area link warnings, and an optional run-status report so
 * automated agents can build a plant from extracted (e.g. document- or historian-derived) JSON and degrade gracefully
 * instead of crashing mid-pipeline.
 * </p>
 *
 * <h2>Success Response:</h2>
 *
 * <pre>{@code { "status": "success", "areaCount": 2, "failedAreas": [], "areaResults": { ... },
 * "interAreaLinkWarnings": [], "warnings": [] } }</pre>
 *
 * <h2>Error Response:</h2>
 *
 * <pre>{@code { "status": "error", "errors": [ { "code": "MISSING_AREAS", "message": "JSON must
 * contain an 'areas' object" } ], "warnings": [] } }</pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessModelResult {

  /** Schema version of the structured JSON emitted by {@link #toJson()}. */
  public static final String SCHEMA_VERSION = "1.0";

  /**
   * Status of the model build result.
   */
  public enum Status {
    /** All requested areas built successfully. */
    SUCCESS,
    /** Build failed: input invalid or every area failed to build. */
    ERROR
  }

  private final Status status;
  private final transient ProcessModel model;
  private final Map<String, SimulationResult> areaResults;
  private final List<String> failedAreas;
  private final List<String> interAreaLinkWarnings;
  private final List<SimulationResult.ErrorDetail> errors;
  private final List<String> warnings;
  private final String runStatusJson;

  /**
   * Private constructor — use the static factory methods.
   *
   * @param status the result status
   * @param model the built model (nullable on hard error)
   * @param areaResults per-area build results keyed by area name
   * @param failedAreas names of areas that failed to build
   * @param interAreaLinkWarnings warnings produced while wiring inter-area stream links
   * @param errors model-level errors
   * @param warnings model-level warnings
   * @param runStatusJson optional run-status JSON when the model was also executed (nullable)
   */
  private ProcessModelResult(Status status, ProcessModel model, Map<String, SimulationResult> areaResults,
      List<String> failedAreas, List<String> interAreaLinkWarnings, List<SimulationResult.ErrorDetail> errors,
      List<String> warnings, String runStatusJson) {
    this.status = status;
    this.model = model;
    this.areaResults = areaResults != null ? new LinkedHashMap<String, SimulationResult>(areaResults)
	: new LinkedHashMap<String, SimulationResult>();
    this.failedAreas = failedAreas != null ? new ArrayList<String>(failedAreas) : new ArrayList<String>();
    this.interAreaLinkWarnings = interAreaLinkWarnings != null ? new ArrayList<String>(interAreaLinkWarnings)
	: new ArrayList<String>();
    this.errors = errors != null ? new ArrayList<SimulationResult.ErrorDetail>(errors)
	: new ArrayList<SimulationResult.ErrorDetail>();
    this.warnings = warnings != null ? new ArrayList<String>(warnings) : new ArrayList<String>();
    this.runStatusJson = runStatusJson;
  }

  /**
   * Creates a success result.
   *
   * @param model the built model
   * @param areaResults per-area build results keyed by area name
   * @param failedAreas names of areas that failed to build (may be empty)
   * @param interAreaLinkWarnings warnings produced while wiring inter-area stream links
   * @param warnings model-level warnings
   * @param runStatusJson optional run-status JSON when the model was executed (nullable)
   * @return the success result
   */
  public static ProcessModelResult success(ProcessModel model, Map<String, SimulationResult> areaResults,
      List<String> failedAreas, List<String> interAreaLinkWarnings, List<String> warnings, String runStatusJson) {
    return new ProcessModelResult(Status.SUCCESS, model, areaResults, failedAreas, interAreaLinkWarnings,
	Collections.<SimulationResult.ErrorDetail>emptyList(), warnings, runStatusJson);
  }

  /**
   * Creates a failure result with a single error.
   *
   * @param code error code (e.g. "MISSING_AREAS", "JSON_PARSE_ERROR")
   * @param message human-readable error description
   * @param remediation actionable fix description
   * @return the failure result
   */
  public static ProcessModelResult error(String code, String message, String remediation) {
    List<SimulationResult.ErrorDetail> errs = new ArrayList<SimulationResult.ErrorDetail>();
    errs.add(new SimulationResult.ErrorDetail(code, message, null, remediation));
    return new ProcessModelResult(Status.ERROR, null, null, null, null, errs, Collections.<String>emptyList(), null);
  }

  /**
   * Creates a failure result that still carries per-area diagnostics.
   *
   * @param errors model-level errors
   * @param areaResults per-area build results keyed by area name
   * @param failedAreas names of areas that failed to build
   * @param warnings model-level warnings
   * @return the failure result
   */
  public static ProcessModelResult failure(List<SimulationResult.ErrorDetail> errors,
      Map<String, SimulationResult> areaResults, List<String> failedAreas, List<String> warnings) {
    return new ProcessModelResult(Status.ERROR, null, areaResults, failedAreas, null, errors, warnings, null);
  }

  /**
   * Returns true if the model build completed successfully.
   *
   * @return true if status is SUCCESS
   */
  public boolean isSuccess() {
    return status == Status.SUCCESS;
  }

  /**
   * Returns true if the model build failed.
   *
   * @return true if status is ERROR
   */
  public boolean isError() {
    return status == Status.ERROR;
  }

  /**
   * Gets the result status.
   *
   * @return the status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Gets the built ProcessModel.
   *
   * @return the model, or null if the build failed before any area was created
   */
  public ProcessModel getModel() {
    return model;
  }

  /**
   * Gets the per-area build results.
   *
   * @return unmodifiable map of area name to its {@link SimulationResult}
   */
  public Map<String, SimulationResult> getAreaResults() {
    return Collections.unmodifiableMap(areaResults);
  }

  /**
   * Gets the names of areas that failed to build.
   *
   * @return unmodifiable list of failed area names
   */
  public List<String> getFailedAreas() {
    return Collections.unmodifiableList(failedAreas);
  }

  /**
   * Gets the warnings produced while wiring inter-area stream links.
   *
   * @return unmodifiable list of inter-area link warnings
   */
  public List<String> getInterAreaLinkWarnings() {
    return Collections.unmodifiableList(interAreaLinkWarnings);
  }

  /**
   * Gets the model-level errors.
   *
   * @return unmodifiable list of errors
   */
  public List<SimulationResult.ErrorDetail> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /**
   * Gets the model-level warnings.
   *
   * @return unmodifiable list of warnings
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Returns true if any warnings (model-level or inter-area) were recorded.
   *
   * @return true if warnings exist
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty() || !interAreaLinkWarnings.isEmpty();
  }

  /**
   * Gets the run-status JSON captured when the model was executed.
   *
   * @return run-status JSON string, or null when the model was built but not run
   */
  public String getRunStatusJson() {
    return runStatusJson;
  }

  /**
   * Converts this result to a JSON object.
   *
   * @return JSON representation of the model build result
   */
  public JsonObject toJsonObject() {
    JsonObject obj = new JsonObject();
    obj.addProperty("schemaVersion", SCHEMA_VERSION);
    obj.addProperty("status", status == Status.SUCCESS ? "success" : "error");
    obj.addProperty("areaCount", areaResults.size());

    JsonArray failed = new JsonArray();
    for (String area : failedAreas) {
      failed.add(area);
    }
    obj.add("failedAreas", failed);

    JsonObject areas = new JsonObject();
    for (Map.Entry<String, SimulationResult> entry : areaResults.entrySet()) {
      JsonObject areaObj = new JsonObject();
      SimulationResult areaResult = entry.getValue();
      areaObj.addProperty("status", areaResult.isSuccess() ? "success" : "error");
      JsonArray areaErrors = new JsonArray();
      for (SimulationResult.ErrorDetail err : areaResult.getErrors()) {
	areaErrors.add(err.toJsonObject());
      }
      areaObj.add("errors", areaErrors);
      JsonArray areaWarnings = new JsonArray();
      for (String warning : areaResult.getWarnings()) {
	areaWarnings.add(warning);
      }
      areaObj.add("warnings", areaWarnings);
      areas.add(entry.getKey(), areaObj);
    }
    obj.add("areaResults", areas);

    JsonArray linkWarnings = new JsonArray();
    for (String warning : interAreaLinkWarnings) {
      linkWarnings.add(warning);
    }
    obj.add("interAreaLinkWarnings", linkWarnings);

    JsonArray errorArray = new JsonArray();
    for (SimulationResult.ErrorDetail err : errors) {
      errorArray.add(err.toJsonObject());
    }
    obj.add("errors", errorArray);

    JsonArray warningArray = new JsonArray();
    for (String warning : warnings) {
      warningArray.add(warning);
    }
    obj.add("warnings", warningArray);

    if (runStatusJson != null) {
      try {
	obj.add("runStatus", com.google.gson.JsonParser.parseString(runStatusJson));
      } catch (RuntimeException ex) {
	obj.addProperty("runStatus", runStatusJson);
      }
    }
    return obj;
  }

  /**
   * Converts this result to a pretty-printed JSON string.
   *
   * @return JSON string representation
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toJsonObject());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return toJson();
  }
}
