package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.DynamicProcessHelper;

/**
 * Stateless dynamic simulation runner for MCP integration.
 *
 * <p>
 * Takes a process JSON (same format as {@link ProcessRunner}), instruments it with controllers and
 * measurement devices using {@link DynamicProcessHelper}, then runs a transient simulation for a
 * specified duration. Returns time-series data from all transmitters and controllers.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DynamicRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private DynamicRunner() {}

  /**
   * Runs a dynamic (transient) simulation from a JSON input.
   *
   * <p>
   * Input JSON must include:
   * </p>
   * <ul>
   * <li>"processJson" — a process specification (same format as runProcess)</li>
   * <li>"duration_seconds" — total simulation duration</li>
   * <li>"timeStep_seconds" — time step for each transient step</li>
   * <li>"tuning" (optional) — controller tuning parameters</li>
   * </ul>
   *
   * @param json the JSON dynamic simulation specification
   * @return a JSON string with time-series results
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON dynamic simulation specification");
    }

    JsonObject input;
    try {
      input = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception e) {
      return errorJson("JSON_PARSE_ERROR", "Failed to parse JSON: " + e.getMessage(),
          "Ensure the JSON is well-formed");
    }

    long startTime = System.currentTimeMillis();

    try {
      // --- Build and run steady-state process first ---
      if (!input.has("processJson")) {
        return errorJson("MISSING_PROCESS", "No 'processJson' provided",
            "Provide a 'processJson' field with the process specification");
      }

      String processJsonStr = input.get("processJson").isJsonObject()
          ? GSON.toJson(input.getAsJsonObject("processJson"))
          : input.get("processJson").getAsString();

      String ssResult = ProcessRunner.run(processJsonStr);
      JsonObject ssResponse = JsonParser.parseString(ssResult).getAsJsonObject();
      if (!"success".equals(ssResponse.get("status").getAsString())) {
        return ssResult; // forward the steady-state error
      }

      // Rebuild process for dynamic simulation
      ProcessSystem process = ProcessSystem.fromJsonAndRun(processJsonStr).getProcessSystem();

      // --- Instrument with controllers ---
      double timeStep =
          input.has("timeStep_seconds") ? input.get("timeStep_seconds").getAsDouble() : 1.0;
      double duration =
          input.has("duration_seconds") ? input.get("duration_seconds").getAsDouble() : 600.0;

      DynamicProcessHelper helper = new DynamicProcessHelper(process);
      helper.setDefaultTimeStep(timeStep);

      // Apply optional tuning
      if (input.has("tuning")) {
        JsonObject tuning = input.getAsJsonObject("tuning");
        if (tuning.has("pressure")) {
          JsonObject pt = tuning.getAsJsonObject("pressure");
          helper.setPressureTuning(pt.get("kp").getAsDouble(), pt.get("ti").getAsDouble());
        }
        if (tuning.has("level")) {
          JsonObject lt = tuning.getAsJsonObject("level");
          helper.setLevelTuning(lt.get("kp").getAsDouble(), lt.get("ti").getAsDouble());
        }
        if (tuning.has("flow")) {
          JsonObject ft = tuning.getAsJsonObject("flow");
          helper.setFlowTuning(ft.get("kp").getAsDouble(), ft.get("ti").getAsDouble());
        }
        if (tuning.has("temperature")) {
          JsonObject tt = tuning.getAsJsonObject("temperature");
          helper.setTemperatureTuning(tt.get("kp").getAsDouble(), tt.get("ti").getAsDouble());
        }
      }

      helper.instrumentAndControl();

      // --- Run transient ---
      int steps = (int) Math.ceil(duration / timeStep);
      int maxSteps = Math.min(steps, 10000); // safety limit

      // Collect time-series for all transmitters
      java.util.Map<String, neqsim.process.measurementdevice.MeasurementDeviceInterface> transmitters =
          helper.getTransmitters();

      // Time-series arrays
      JsonArray timeArr = new JsonArray();
      java.util.Map<String, JsonArray> series = new java.util.LinkedHashMap<String, JsonArray>();
      for (String tag : transmitters.keySet()) {
        series.put(tag, new JsonArray());
      }

      // Record initial state
      timeArr.add(0.0);
      for (java.util.Map.Entry<String, neqsim.process.measurementdevice.MeasurementDeviceInterface> entry : transmitters
          .entrySet()) {
        series.get(entry.getKey()).add(entry.getValue().getMeasuredValue());
      }

      // Run transient steps
      for (int step = 1; step <= maxSteps; step++) {
        process.runTransient();
        double t = step * timeStep;
        timeArr.add(t);
        for (java.util.Map.Entry<String, neqsim.process.measurementdevice.MeasurementDeviceInterface> entry : transmitters
            .entrySet()) {
          series.get(entry.getKey()).add(entry.getValue().getMeasuredValue());
        }
      }

      // --- Build response ---
      JsonObject data = new JsonObject();
      data.addProperty("duration_seconds", duration);
      data.addProperty("timeStep_seconds", timeStep);
      data.addProperty("totalSteps", maxSteps);
      data.add("time_seconds", timeArr);

      JsonObject seriesObj = new JsonObject();
      for (java.util.Map.Entry<String, JsonArray> entry : series.entrySet()) {
        JsonObject tagData = new JsonObject();
        tagData.addProperty("unit", transmitters.get(entry.getKey()).getUnit());
        tagData.add("values", entry.getValue());
        seriesObj.add(entry.getKey(), tagData);
      }
      data.add("transmitters", seriesObj);

      // Controller info
      java.util.Map<String, neqsim.process.controllerdevice.ControllerDeviceInterface> controllers =
          helper.getControllers();
      JsonArray controllerArr = new JsonArray();
      for (java.util.Map.Entry<String, neqsim.process.controllerdevice.ControllerDeviceInterface> entry : controllers
          .entrySet()) {
        JsonObject ctrl = new JsonObject();
        ctrl.addProperty("tag", entry.getKey());
        controllerArr.add(ctrl);
      }
      data.add("controllers", controllerArr);

      JsonObject response = new JsonObject();
      response.addProperty("status", "success");
      response.add("data", data);

      ResultProvenance provenance = new ResultProvenance();
      provenance.setCalculationType("dynamic process simulation (transient)");
      provenance.setConverged(true);
      provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
      response.add("provenance", GSON.toJsonTree(provenance));

      return GSON.toJson(response);
    } catch (Exception e) {
      return errorJson("DYNAMIC_ERROR", "Dynamic simulation failed: " + e.getMessage(),
          "Ensure process is valid and converges in steady-state first");
    }
  }

  /**
   * Creates a standard error JSON string.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return the error JSON string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    err.addProperty("remediation", remediation);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }
}
