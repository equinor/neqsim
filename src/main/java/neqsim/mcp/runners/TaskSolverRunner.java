package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Task solver and multi-domain composition runner.
 *
 * <p>
 * Two capabilities in one class:
 * </p>
 * <ul>
 * <li><b>solve_task</b> — Takes a high-level engineering task description and structured parameters, classifies
 * supported keywords into a fixed plan of existing runners, executes each step with shared caller input, optionally
 * validates the collected output, and returns a structured engineering report.</li>
 * <li><b>compose_workflow</b> — Chains multiple domain runners in sequence (e.g., Reservoir → Process → Pipeline →
 * Economics) with automatic data flow between stages.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TaskSolverRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private TaskSolverRunner() {
  }

  /**
   * Solves a high-level engineering task by planning and executing a sequence of runner steps.
   *
   * <p>
   * Input JSON format:
   * </p>
   *
   * <pre>
   * {
   *   "task": "Design a 3-stage compression system from 5 to 150 bara",
   *   "fluid": { "components": {...}, "eos": "srk", "temperature": 298.15, "pressure": 5.0 },
   *   "parameters": { "stages": 3, "outletPressure": 150.0, "intercoolerTemp": 40.0 },
   *   "validate": true
   * }
   * </pre>
   *
   * @param json the task description JSON
   * @return JSON with classification, plan, step results, validation, and summary
   */
  public static String solveTask(String json) {
    long startTime = System.currentTimeMillis();

    if (json == null || json.trim().isEmpty()) {
      return errorJson("TASK_ERROR", "Task JSON is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      if (!input.has("task") || input.get("task").isJsonNull()
          || !input.get("task").isJsonPrimitive()
          || !input.get("task").getAsJsonPrimitive().isString()
          || input.get("task").getAsString().trim().isEmpty()) {
        return errorJson("MISSING_TASK", "Provide a non-blank string 'task' description");
      }
      String task = input.get("task").getAsString().trim();

      // Step 1: Classify only the keyword families backed by explicit fixed plans.
      String taskType = classifyTask(task);
      if (taskType == null) {
        return errorJson("UNSUPPORTED_TASK",
            "Task description does not match a supported fixed-plan keyword family");
      }

      // Step 2: Build execution plan
      List<PlanStep> plan = buildPlan(taskType);

      // Step 3: Execute each step
      List<StepResult> stepResults = new ArrayList<StepResult>();
      JsonObject carryForward = new JsonObject();

      // Pass through the original fluid and parameters as base context
      if (input.has("fluid")) {
        carryForward.add("fluid", input.get("fluid"));
      }
      if (input.has("parameters")) {
        carryForward.add("parameters", input.get("parameters"));
      }

      for (PlanStep step : plan) {
        long stepStart = System.currentTimeMillis();
        String stepInput = buildStepInput(step, input, carryForward);
        String stepOutput = executeStep(step, stepInput);
        long stepTime = System.currentTimeMillis() - stepStart;

        StepResult result = new StepResult(step.name, step.runner, stepOutput, stepTime);

        // Parse output and carry forward key results
        try {
          JsonObject parsed = JsonParser.parseString(stepOutput).getAsJsonObject();
          if (parsed.has("data")) {
            carryForward.add(step.name + "_result", parsed.get("data"));
          } else {
            carryForward.add(step.name + "_result", parsed);
          }
          result.success = isSuccessfulResponse(parsed);
        } catch (Exception e) {
          result.success = false;
          result.errorMessage = e.getMessage();
        }

        stepResults.add(result);

        // Stop on error unless the step is optional
        if (!result.success && !step.optional) {
          break;
        }
      }

      // Step 4: Validate combined results
      boolean shouldValidate = !input.has("validate") || input.get("validate").getAsBoolean();
      String validationReport = null;
      if (shouldValidate) {
        validationReport = EngineeringValidator.validate(GSON.toJson(carryForward), taskType);
      }

      // Step 5: Build structured report
      return buildTaskReport(task, taskType, plan, stepResults, carryForward, validationReport,
          System.currentTimeMillis() - startTime);

    } catch (Exception e) {
      return errorJson("TASK_ERROR", "Task solving failed: " + e.getMessage());
    }
  }

  /**
   * Composes a multi-domain workflow by chaining specified runners in sequence.
   *
   * <p>
   * Input JSON format:
   * </p>
   *
   * <pre>
   * {
   *   "workflow": "reservoir-to-economics",
   *   "fluid": { ... },
   *   "steps": [
   *     { "runner": "flash", "input": { "temperature": 25, "pressure": 60 } },
   *     { "runner": "process", "input": { "process": [...] } },
   *     { "runner": "pipeline", "input": { "length": 50000, "diameter": 0.3 } }
   *   ]
   * }
   * </pre>
   *
   * @param json the workflow JSON
   * @return JSON with all step results and combined output
   */
  public static String composeWorkflow(String json) {
    long startTime = System.currentTimeMillis();

    if (json == null || json.trim().isEmpty()) {
      return errorJson("WORKFLOW_ERROR", "Workflow JSON is null or empty");
    }

    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String workflowName = input.has("workflow") ? input.get("workflow").getAsString() : "custom";

      if (!input.has("steps") || !input.get("steps").isJsonArray()) {
        return errorJson("MISSING_STEPS", "No 'steps' array in workflow. Provide [{runner, input}, ...]");
      }

      JsonArray steps = input.getAsJsonArray("steps");
      List<StepResult> results = new ArrayList<StepResult>();
      JsonObject carryForward = new JsonObject();

      if (input.has("fluid")) {
        carryForward.add("fluid", input.get("fluid"));
      }

      for (int i = 0; i < steps.size(); i++) {
        JsonObject stepDef = steps.get(i).getAsJsonObject();
        String runnerName = stepDef.has("runner") ? stepDef.get("runner").getAsString() : "";
        String stepName = stepDef.has("name") ? stepDef.get("name").getAsString() : "step_" + (i + 1);

        // Build step input: merge step-specific input with carry-forward data
        JsonObject stepInput = new JsonObject();
        if (carryForward.has("fluid")) {
          JsonElement fluid = carryForward.get("fluid");
          stepInput.add("fluid", fluid.deepCopy());
          if (fluid.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : fluid.getAsJsonObject().entrySet()) {
              stepInput.add(entry.getKey(), entry.getValue().deepCopy());
            }
          }
        }
        if (stepDef.has("input")) {
          JsonObject specific = stepDef.getAsJsonObject("input");
          for (Map.Entry<String, JsonElement> entry : specific.entrySet()) {
            stepInput.add(entry.getKey(), entry.getValue());
          }
        }

        long stepStart = System.currentTimeMillis();
        PlanStep ps = new PlanStep(stepName, runnerName, "User-defined step", false);
        String output = executeStep(ps, GSON.toJson(stepInput));
        long stepTime = System.currentTimeMillis() - stepStart;

        StepResult sr = new StepResult(stepName, runnerName, output, stepTime);
        try {
          JsonObject parsed = JsonParser.parseString(output).getAsJsonObject();
          carryForward.add(stepName + "_result", parsed);
          sr.success = !parsed.has("errors");
        } catch (Exception e) {
          sr.success = false;
          sr.errorMessage = e.getMessage();
        }
        results.add(sr);

        if (!sr.success) {
          break;
        }
      }

      // Build response
      JsonObject response = new JsonObject();
      response.addProperty("workflow", workflowName);
      response.addProperty("totalSteps", steps.size());
      response.addProperty("completedSteps", results.size());
      response.addProperty("totalTimeMs", System.currentTimeMillis() - startTime);
      response.addProperty("success", !results.isEmpty() && results.get(results.size() - 1).success);

      JsonArray stepsArray = new JsonArray();
      for (StepResult sr : results) {
        stepsArray.add(sr.toJson());
      }
      response.add("steps", stepsArray);
      response.add("combinedData", carryForward);

      return GSON.toJson(response);

    } catch (Exception e) {
      return errorJson("WORKFLOW_ERROR", "Workflow execution failed: " + e.getMessage());
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Task classification
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Classifies a task description into a category.
   *
   * @param task the task description
   * @return the task category, or {@code null} when no fixed plan is supported
   */
  private static String classifyTask(String task) {
    String lower = task.toLowerCase();

    if (lower.contains("compress") || lower.contains("compressor")) {
      return "compression";
    }
    if (lower.contains("separat")) {
      return "separation";
    }
    if (lower.contains("dehydra") || lower.contains("teg") || lower.contains("glycol")) {
      return "dehydration";
    }
    if (lower.contains("pipeline") || lower.contains("flowline") || lower.contains("pipe")) {
      return "pipeline";
    }
    if (lower.contains("pvt") || lower.contains("phase envelope") || lower.contains("flash")) {
      return "pvt";
    }
    if (lower.contains("hydrate") || lower.contains("wax") || lower.contains("flow assurance")) {
      return "flow_assurance";
    }
    if (lower.contains("reservoir") || lower.contains("depletion")) {
      return "reservoir";
    }
    if (lower.contains("economic") || lower.contains("npv") || lower.contains("cost")) {
      return "economics";
    }
    if (lower.contains("dynamic") || lower.contains("transient") || lower.contains("blowdown")) {
      return "dynamic";
    }
    return null;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Plan building
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Builds an execution plan based on task type.
   *
   * @param taskType the classified task type
   * @return list of plan steps
   */
  private static List<PlanStep> buildPlan(String taskType) {
    List<PlanStep> plan = new ArrayList<PlanStep>();

    switch (taskType) {
    case "compression":
      plan.add(new PlanStep("flash_feed", "flash", "Flash the feed fluid to establish inlet conditions", false));
      plan.add(
          new PlanStep("process_simulation", "process", "Build and run compression process with intercooling", false));
      plan.add(new PlanStep("validate", "validate", "Validate compressor design parameters", true));
      break;

    case "separation":
      plan.add(new PlanStep("flash_feed", "flash", "Flash the feed to establish phase split", false));
      plan.add(new PlanStep("process_simulation", "process", "Build and run separation train", false));
      plan.add(new PlanStep("validate", "validate", "Validate separator design parameters", true));
      break;

    case "dehydration":
      plan.add(new PlanStep("flash_feed", "flash", "Flash wet gas feed", false));
      plan.add(new PlanStep("water_content", "flow_assurance", "Calculate water content and dew point", false));
      plan.add(new PlanStep("process_simulation", "process", "Build TEG dehydration process", false));
      break;

    case "pipeline":
      plan.add(new PlanStep("flash_feed", "flash", "Flash fluid at pipeline inlet conditions", false));
      plan.add(new PlanStep("pipeline_simulation", "pipeline", "Run pipeline hydraulics", false));
      plan.add(new PlanStep("flow_assurance_check", "flow_assurance", "Check hydrate and wax risk", true));
      break;

    case "pvt":
      plan.add(new PlanStep("pvt_study", "pvt", "Run PVT analysis", false));
      break;

    case "flow_assurance":
      plan.add(new PlanStep("flash_feed", "flash", "Flash fluid at operating conditions", false));
      plan.add(new PlanStep("flow_assurance", "flow_assurance", "Run flow assurance analysis", false));
      break;

    case "economics":
      plan.add(new PlanStep("economics", "economics", "Run field development economics", false));
      break;

    case "reservoir":
      plan.add(new PlanStep("reservoir_sim", "reservoir", "Run reservoir simulation", false));
      break;

    case "dynamic":
      plan.add(new PlanStep("dynamic_sim", "dynamic", "Run dynamic simulation", false));
      break;

    default:
      plan.add(new PlanStep("flash_feed", "flash", "Flash the feed fluid", false));
      plan.add(new PlanStep("process_simulation", "process", "Build and run process", false));
      break;
    }

    return plan;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Step execution
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Builds input JSON for a specific step.
   *
   * @param step the plan step
   * @param originalInput the original task input
   * @param carryForward data carried from previous steps
   * @return JSON input for the step runner
   */
  private static String buildStepInput(PlanStep step, JsonObject originalInput, JsonObject carryForward) {
    JsonObject stepInput = new JsonObject();

    // Preserve the shared fluid and expose its canonical model/components to
    // runners whose native JSON contract uses top-level fields.
    JsonElement fluid = null;
    if (originalInput.has("fluid")) {
      fluid = originalInput.get("fluid");
    } else if (carryForward.has("fluid")) {
      fluid = carryForward.get("fluid");
    }
    if (fluid != null) {
      stepInput.add("fluid", fluid.deepCopy());
      if (fluid.isJsonObject()) {
        for (Map.Entry<String, JsonElement> entry : fluid.getAsJsonObject().entrySet()) {
          stepInput.add(entry.getKey(), entry.getValue().deepCopy());
        }
      }
    }

    // Include parameters
    if (originalInput.has("parameters")) {
      JsonObject params = originalInput.getAsJsonObject("parameters");
      for (Map.Entry<String, JsonElement> entry : params.entrySet()) {
        stepInput.add(entry.getKey(), entry.getValue());
      }
    }

    // Include step-specific overrides
    if (originalInput.has(step.name)) {
      JsonObject overrides = originalInput.getAsJsonObject(step.name);
      for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
        stepInput.add(entry.getKey(), entry.getValue());
      }
    }

    // Include process definition if available
    if (originalInput.has("process")) {
      stepInput.add("process", originalInput.get("process"));
    }

    // Include previous results for context
    if (step.name.contains("validate")) {
      stepInput = carryForward.deepCopy();
    }

    return GSON.toJson(stepInput);
  }

  /**
   * Executes a single step by routing to the appropriate runner.
   *
   * @param step the plan step
   * @param inputJson the step input JSON
   * @return the runner output JSON
   */
  private static String executeStep(PlanStep step, String inputJson) {
    try {
      switch (step.runner) {
      case "flash":
        return FlashRunner.run(inputJson);
      case "process":
        return ProcessRunner.run(inputJson);
      case "pvt":
        return PVTRunner.run(inputJson);
      case "flow_assurance":
        return FlowAssuranceRunner.run(inputJson);
      case "pipeline":
        return PipelineRunner.run(inputJson);
      case "reservoir":
        return ReservoirRunner.run(inputJson);
      case "economics":
        return FieldDevelopmentRunner.run(inputJson);
      case "dynamic":
        return DynamicRunner.run(inputJson);
      case "standards":
        return StandardsRunner.run(inputJson);
      case "bioprocess":
        return BioprocessRunner.run(inputJson);
      case "validate":
        return EngineeringValidator.validate(inputJson, "task");
      default:
        return errorJson("UNKNOWN_RUNNER", "Unknown runner: " + step.runner);
      }
    } catch (Exception e) {
      return errorJson("STEP_ERROR", "Step '" + step.name + "' failed: " + e.getMessage());
    }
  }

  /**
   * Determines whether a runner response represents a successful step.
   *
   * @param response parsed runner response
   * @return true only when no explicit error, blocked status, or false success is present
   */
  private static boolean isSuccessfulResponse(JsonObject response) {
    if (response.has("errors") && response.get("errors").isJsonArray()
        && response.getAsJsonArray("errors").size() > 0) {
      return false;
    }
    if (response.has("status") && response.get("status").isJsonPrimitive()) {
      String status = response.get("status").getAsString();
      if ("error".equalsIgnoreCase(status) || "blocked".equalsIgnoreCase(status)) {
        return false;
      }
    }
    return !response.has("success") || !response.get("success").isJsonPrimitive()
        || response.get("success").getAsBoolean();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Report building
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Builds the final task report.
   *
   * @param task the original task description
   * @param taskType the classified type
   * @param plan the execution plan
   * @param results the step results
   * @param carryForward the combined data
   * @param validationReport the validation report JSON, or null
   * @param totalTimeMs total execution time
   * @return the complete report JSON
   */
  private static String buildTaskReport(String task, String taskType, List<PlanStep> plan, List<StepResult> results,
      JsonObject carryForward, String validationReport, long totalTimeMs) {

    JsonObject report = new JsonObject();
    report.addProperty("task", task);
    report.addProperty("taskType", taskType);
    report.addProperty("totalTimeMs", totalTimeMs);
    report.addProperty("totalSteps", plan.size());
    report.addProperty("completedSteps", results.size());

    // Overall success
    boolean allSuccess = true;
    for (StepResult r : results) {
      if (!r.success) {
        allSuccess = false;
        break;
      }
    }
    report.addProperty("status", allSuccess ? "success" : "error");
    report.addProperty("success", allSuccess);
    if (!allSuccess) {
      JsonArray errors = new JsonArray();
      JsonObject error = new JsonObject();
      error.addProperty("code", "TASK_STEP_FAILED");
      error.addProperty("message", "A required task step failed");
      for (StepResult result : results) {
        if (!result.success) {
          error.addProperty("step", result.stepName);
          try {
            JsonObject output = JsonParser.parseString(result.output).getAsJsonObject();
            if (output.has("errors") && output.get("errors").isJsonArray()
                && output.getAsJsonArray("errors").size() > 0) {
              JsonObject nested = output.getAsJsonArray("errors").get(0).getAsJsonObject();
              if (nested.has("code")) {
                error.addProperty("causeCode", nested.get("code").getAsString());
              }
              if (nested.has("message")) {
                error.addProperty("causeMessage", nested.get("message").getAsString());
              }
            }
          } catch (Exception ignored) {
            // Keep the stable task-level diagnostic when a runner returned non-JSON.
          }
          break;
        }
      }
      errors.add(error);
      report.add("errors", errors);
    }

    // Plan
    JsonArray planArray = new JsonArray();
    for (PlanStep ps : plan) {
      JsonObject p = new JsonObject();
      p.addProperty("step", ps.name);
      p.addProperty("runner", ps.runner);
      p.addProperty("description", ps.description);
      p.addProperty("optional", ps.optional);
      planArray.add(p);
    }
    report.add("plan", planArray);

    // Step results
    JsonArray stepsArray = new JsonArray();
    for (StepResult sr : results) {
      stepsArray.add(sr.toJson());
    }
    report.add("stepResults", stepsArray);

    // Validation
    if (validationReport != null) {
      try {
        report.add("validation", JsonParser.parseString(validationReport));
      } catch (Exception e) {
        report.addProperty("validationError", e.getMessage());
      }
    }

    // Combined data summary
    report.add("combinedData", carryForward);

    return GSON.toJson(report);
  }

  /**
   * Creates a standard error JSON.
   *
   * @param code the error code
   * @param message the error message
   * @return error JSON string
   */
  private static String errorJson(String code, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    errors.add(err);
    error.add("errors", errors);
    return GSON.toJson(error);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Inner types
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * A step in an execution plan.
   */
  static class PlanStep {
    /** Step name/identifier. */
    final String name;
    /** Runner to execute. */
    final String runner;
    /** Description of what this step does. */
    final String description;
    /** Whether the step is optional (failure doesn't stop the plan). */
    final boolean optional;

    /**
     * Creates a plan step.
     *
     * @param name the step name
     * @param runner the runner name
     * @param description the description
     * @param optional whether optional
     */
    PlanStep(String name, String runner, String description, boolean optional) {
      this.name = name;
      this.runner = runner;
      this.description = description;
      this.optional = optional;
    }
  }

  /**
   * Result of executing a step.
   */
  static class StepResult {
    /** Step name. */
    final String stepName;
    /** Runner used. */
    final String runner;
    /** Raw output JSON. */
    final String output;
    /** Execution time in milliseconds. */
    final long timeMs;
    /** Whether the step succeeded. */
    boolean success = true;
    /** Error message if failed. */
    String errorMessage;

    /**
     * Creates a step result.
     *
     * @param stepName the step name
     * @param runner the runner name
     * @param output the raw output
     * @param timeMs the time in ms
     */
    StepResult(String stepName, String runner, String output, long timeMs) {
      this.stepName = stepName;
      this.runner = runner;
      this.output = output;
      this.timeMs = timeMs;
    }

    /**
     * Converts to JSON representation.
     *
     * @return JsonObject
     */
    JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("step", stepName);
      obj.addProperty("runner", runner);
      obj.addProperty("success", success);
      obj.addProperty("timeMs", timeMs);
      if (errorMessage != null) {
        obj.addProperty("error", errorMessage);
      }
      try {
        obj.add("output", JsonParser.parseString(output));
      } catch (Exception e) {
        obj.addProperty("rawOutput", output);
      }
      return obj;
    }
  }
}
