package neqsim.process.engineering;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.materials.MaterialsReviewEngine;
import neqsim.process.materials.MaterialsReviewReport;
import neqsim.process.mechanicaldesign.DesignConditions;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyHandoff;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyRunner;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.thermo.system.SystemInterface;

/** Runs simulation-backed, review-governed engineering calculations for DEXPI generation. */
public final class SimulationEngineeringDesignRunner {
  private static final Logger logger = LogManager.getLogger(SimulationEngineeringDesignRunner.class);
  private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

  private SimulationEngineeringDesignRunner() {
  }

  /**
   * Runs all calculations for which the project contains sufficient inputs.
   *
   * <p>
   * Equipment mechanical design and materials screening are run directly from the process. Explicit overpressure and
   * blowdown/flare studies are run when attached to the project. A conservative blocked-outlet PSV screening is also
   * generated when an equipment item has declared design pressure, relief set pressure, a simulated inlet flow and a
   * fluid. No relief, flare, SIL, voting or shutdown value is invented when its required basis is absent.
   * </p>
   *
   * @param project governed engineering project
   * @return calculation handoff
   */
  public static SimulationEngineeringDesignReport run(EngineeringProject project) {
    if (project == null) {
      throw new IllegalArgumentException("project must not be null");
    }
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "neqsim_engineering_calculations.v1");
    root.addProperty("projectId", project.getProjectId());
    root.addProperty("projectName", project.getName());
    root.addProperty("documentStatus", "CALCULATED_AND_PROPOSED_REVIEW_REQUIRED");
    root.addProperty("calculationAuthority", "NeqSim process simulation and engineering calculators");
    root.addProperty("approvalAuthority", "Accountable project engineering disciplines");

    JsonArray equipmentResults = new JsonArray();
    int calculatedEquipmentCount = calculateMechanicalDesigns(project, equipmentResults);
    root.add("equipmentMechanicalDesign", equipmentResults);

    JsonArray settingEnvelopes = calculateSettingEnvelopes(project);
    root.add("tripSettingEnvelopes", settingEnvelopes);

    JsonArray reliefResults = new JsonArray();
    Set<String> explicitlyStudiedTags = new HashSet<String>();
    for (OverpressureProtectionStudy study : project.getOverpressureStudies()) {
      explicitlyStudiedTags.add(study.getItem().getName());
      try {
        addReliefResult(reliefResults, study.evaluate(), "PROJECT_DEFINED_SCENARIOS");
      } catch (Exception ex) {
        reliefResults.add(calculationFailure(study.getItem().getName(), "RELIEF_STUDY", ex));
      }
    }
    addAutomaticBlockedOutletScreening(project, explicitlyStudiedTags, reliefResults);
    root.add("overpressureAndPsvSizing", reliefResults);

    JsonArray blowdownResults = new JsonArray();
    for (DynamicBlowdownFlareStudyDataSource input : project.getBlowdownFlareStudies()) {
      try {
        DynamicBlowdownFlareStudyHandoff handoff = DynamicBlowdownFlareStudyRunner.builder().build().run(input);
        blowdownResults.add(GSON.toJsonTree(handoff.toMap()));
      } catch (Exception ex) {
        blowdownResults.add(calculationFailure(input.getStudyId(), "DYNAMIC_BLOWDOWN_FLARE_STUDY", ex));
      }
    }
    root.add("dynamicBlowdownAndFlareSizing", blowdownResults);

    try {
      MaterialsReviewReport materials = new MaterialsReviewEngine().evaluate(project.getProcessSystem(),
          project.getMaterialsReviewInput());
      root.add("materialsAndCorrosionScreening", GSON.toJsonTree(materials.toMap()));
    } catch (Exception ex) {
      root.add("materialsAndCorrosionScreening", calculationFailure(project.getName(), "MATERIALS_REVIEW", ex));
    }
    root.add("unresolvedEngineering", unresolvedEngineering(project, reliefResults, blowdownResults));
    root.add("governance", governance());
    return new SimulationEngineeringDesignReport(root, calculatedEquipmentCount, reliefResults.size(),
        blowdownResults.size());
  }

  private static int calculateMechanicalDesigns(EngineeringProject project, JsonArray results) {
    int completed = 0;
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      JsonObject item = equipmentIdentity(unit);
      item.add("simulationOperatingPoint", operatingPoint(unit));
      JsonArray limitations = new JsonArray();
      limitations.add("Single simulated operating point; all governing design and accidental cases must be added.");
      limitations.add("Vendor, fabrication, nozzle, piping stress and project specification checks remain required.");
      item.add("basisLimitations", limitations);
      DesignConditions designConditions = unit.getDesignConditions();
      if (designConditions != null && !designConditions.isEmpty()) {
        item.add("declaredDesignConditions", GSON.toJsonTree(designConditions));
      }
      try {
        unit.initMechanicalDesign();
        MechanicalDesign mechanicalDesign = unit.getMechanicalDesign();
        if (mechanicalDesign == null) {
          item.addProperty("status", "NOT_CALCULATED_NO_MECHANICAL_DESIGN_MODEL");
        } else {
          mechanicalDesign.calcDesign();
          item.addProperty("status", "CALCULATED_SCREENING_REVIEW_REQUIRED");
          item.addProperty("method", mechanicalDesign.getClass().getName());
          item.add("result", parseJson(mechanicalDesign.toJson()));
          if (unit instanceof PipeLineInterface) {
            NorsokP002LineSizingValidator.LineSizingResult lineSizing = new NorsokP002LineSizingValidator()
                .validate((PipeLineInterface) unit);
            JsonObject lineSizingJson = GSON.toJsonTree(lineSizing.toMap()).getAsJsonObject();
            lineSizingJson.addProperty("approvalStatus", "REVIEW_REQUIRED");
            item.add("norsokP002LineSizingValidation", lineSizingJson);
          }
          completed++;
        }
      } catch (Exception ex) {
        item.addProperty("status", "NOT_CALCULATED_INPUT_OR_MODEL_ERROR");
        item.addProperty("dataGap", safeMessage(ex));
        logger.warn("Mechanical design calculation skipped for {}: {}", unit.getName(), ex.getMessage());
      }
      results.add(item);
    }
    return completed;
  }

  private static JsonObject equipmentIdentity(ProcessEquipmentInterface unit) {
    JsonObject item = new JsonObject();
    item.addProperty("equipmentTag", unit.getName());
    item.addProperty("equipmentClass", unit.getClass().getName());
    item.addProperty("resultMaturity", "SCREENING_OR_PRE_FEED");
    return item;
  }

  private static JsonObject operatingPoint(ProcessEquipmentInterface unit) {
    JsonObject point = new JsonObject();
    try {
      point.addProperty("pressureBara", unit.getPressure("bara"));
    } catch (Exception ex) {
      point.addProperty("pressureStatus", "NOT_AVAILABLE");
    }
    try {
      point.addProperty("temperatureC", unit.getTemperature("C"));
    } catch (Exception ex) {
      point.addProperty("temperatureStatus", "NOT_AVAILABLE");
    }
    double inletMassFlow = inletMassFlow(unit.getInletStreams());
    if (Double.isFinite(inletMassFlow)) {
      point.addProperty("totalInletMassFlowKgPerS", inletMassFlow);
    }
    point.addProperty("dataOrigin", "SIMULATION");
    return point;
  }

  private static JsonArray calculateSettingEnvelopes(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() != EngineeringRequirement.Type.TRIP) {
        continue;
      }
      JsonObject result = new JsonObject();
      result.addProperty("requirementId", requirement.getId());
      result.addProperty("equipmentTag", requirement.getEquipmentTag());
      result.addProperty("approvalStatus", "REVIEW_REQUIRED");
      result.addProperty("silTarget", requirement.getSilTarget());
      result.addProperty("votingArchitecture", "NOT_ASSIGNED_UNLESS_SUPPORTED_BY_SRS");
      ProcessEquipmentInterface unit = project.getProcessSystem().getUnit(requirement.getEquipmentTag());
      if (unit == null) {
        result.addProperty("status", "NOT_CALCULATED_EQUIPMENT_NOT_FOUND");
      } else if (isHighPressureTrip(requirement)) {
        addHighPressureEnvelope(result, unit);
      } else if (isHighTemperatureTrip(requirement)) {
        addHighTemperatureEnvelope(result, unit);
      } else {
        result.addProperty("status", "NOT_CALCULATED_HAZOP_VENDOR_OR_GEOMETRY_INPUT_REQUIRED");
      }
      results.add(result);
    }
    return results;
  }

  private static void addHighPressureEnvelope(JsonObject result, ProcessEquipmentInterface unit) {
    DesignConditions design = unit.getDesignConditions();
    double upper = Double.NaN;
    String upperBasis = "";
    if (design != null && design.isReliefSetPressureSet()) {
      upper = design.getReliefSetPressure();
      upperBasis = "DECLARED_RELIEF_SET_PRESSURE";
    } else if (design != null && design.isDesignPressureSet()) {
      upper = design.getDesignPressure();
      upperBasis = "DECLARED_DESIGN_PRESSURE";
    }
    try {
      double operating = unit.getPressure("bara");
      result.addProperty("normalOperatingPressureBara", operating);
      if (Double.isFinite(upper) && upper > operating) {
        result.addProperty("lowerExclusiveBoundBara", operating);
        result.addProperty("upperExclusiveBoundBara", upper);
        result.addProperty("upperBoundBasis", upperBasis);
        result.addProperty("status", "CALCULATED_FEASIBLE_RANGE_REVIEW_REQUIRED");
        result.addProperty("note", "Select final set point with instrument uncertainty, process response, relief "
            + "accumulation and HAZOP/LOPA margins.");
      } else {
        result.addProperty("status", "NOT_CALCULATED_DESIGN_OR_RELIEF_PRESSURE_REQUIRED");
      }
    } catch (Exception ex) {
      result.addProperty("status", "NOT_CALCULATED_OPERATING_PRESSURE_REQUIRED");
    }
  }

  private static void addHighTemperatureEnvelope(JsonObject result, ProcessEquipmentInterface unit) {
    DesignConditions design = unit.getDesignConditions();
    try {
      double operating = unit.getTemperature("C");
      result.addProperty("normalOperatingTemperatureC", operating);
      if (design != null && design.isMaxDesignTemperatureSet() && design.getMaxDesignTemperature() > operating) {
        result.addProperty("lowerExclusiveBoundC", operating);
        result.addProperty("upperExclusiveBoundC", design.getMaxDesignTemperature());
        result.addProperty("upperBoundBasis", "DECLARED_MAXIMUM_DESIGN_TEMPERATURE");
        result.addProperty("status", "CALCULATED_FEASIBLE_RANGE_REVIEW_REQUIRED");
        result.addProperty("note",
            "Select final set point with sensor uncertainty, thermal inertia, material and " + "vendor limits.");
      } else {
        result.addProperty("status", "NOT_CALCULATED_MAXIMUM_DESIGN_TEMPERATURE_REQUIRED");
      }
    } catch (Exception ex) {
      result.addProperty("status", "NOT_CALCULATED_OPERATING_TEMPERATURE_REQUIRED");
    }
  }

  private static boolean isHighPressureTrip(EngineeringRequirement requirement) {
    String id = requirement.getId();
    return id.endsWith("PRESSURE-HH-TRIP") || id.endsWith("DISCHARGE-P-HH");
  }

  private static boolean isHighTemperatureTrip(EngineeringRequirement requirement) {
    String id = requirement.getId();
    return id.endsWith("DISCHARGE-T-HH") || id.endsWith("OUTLET-T-HH");
  }

  private static void addAutomaticBlockedOutletScreening(EngineeringProject project, Set<String> excludedTags,
      JsonArray results) {
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || unit instanceof Stream || excludedTags.contains(unit.getName())) {
        continue;
      }
      DesignConditions design = unit.getDesignConditions();
      List<StreamInterface> inlets = unit.getInletStreams();
      double inletMassFlow = inletMassFlow(inlets);
      SystemInterface fluid = firstFluid(inlets);
      if (design == null || !design.isDesignPressureSet() || !design.isReliefSetPressureSet()
          || !Double.isFinite(inletMassFlow) || inletMassFlow <= 0.0 || fluid == null) {
        continue;
      }
      if (design.getDesignPressure() <= 0.0 || design.getReliefSetPressure() <= 0.0
          || design.getReliefSetPressure() > design.getDesignPressure()) {
        JsonObject failure = equipmentIdentity(unit);
        failure.addProperty("calculation", "BLOCKED_OUTLET_PSV_SCREENING");
        failure.addProperty("status", "NOT_CALCULATED_INVALID_DECLARED_PRESSURE_BASIS");
        failure.addProperty("dataGap", "Relief set pressure must be positive and not exceed design pressure.");
        results.add(failure);
        continue;
      }
      try {
        ProtectedItem item = new ProtectedItem(unit.getName(), design.getDesignPressure())
            .setReliefSetPressureBara(design.getReliefSetPressure());
        if (design.isMaxDesignTemperatureSet()) {
          item.setDesignTemperatureC(design.getMaxDesignTemperature());
        }
        BlockedOutletRelief blockedOutlet = new BlockedOutletRelief().setName("Automatically screened blocked outlet")
            .setInflowRateKgPerS(inletMassFlow).setReliefPressureBara(design.getReliefSetPressure()).setFluid(fluid);
        OverpressureStudyResult result = new OverpressureProtectionStudy(item).addScenario(blockedOutlet.calculate())
            .evaluate();
        addReliefResult(results, result, "AUTO_SCREENING_FULL_SIMULATED_INFLOW");
      } catch (Exception ex) {
        JsonObject failure = equipmentIdentity(unit);
        failure.addProperty("status", "NOT_CALCULATED_BLOCKED_OUTLET_MODEL_ERROR");
        failure.addProperty("dataGap", safeMessage(ex));
        results.add(failure);
      }
    }
  }

  private static void addReliefResult(JsonArray results, OverpressureStudyResult result, String basis) {
    JsonObject wrapper = new JsonObject();
    wrapper.addProperty("equipmentTag", result.getItem().getName());
    wrapper.addProperty("status", Double.isFinite(result.getRequiredAreaM2()) ? "CALCULATED_PSV_SIZE_REVIEW_REQUIRED"
        : "NOT_SIZED_INCOMPLETE_SIZING_BASIS");
    wrapper.addProperty("basis", basis);
    wrapper.addProperty("approvalStatus", "REVIEW_REQUIRED");
    wrapper.add("result", parseJson(result.toJson()));
    results.add(wrapper);
  }

  private static double inletMassFlow(List<StreamInterface> streams) {
    if (streams == null || streams.isEmpty()) {
      return Double.NaN;
    }
    double total = 0.0;
    boolean found = false;
    for (StreamInterface stream : streams) {
      if (stream == null) {
        continue;
      }
      try {
        double flow = stream.getFlowRate("kg/sec");
        if (Double.isFinite(flow) && flow >= 0.0) {
          total += flow;
          found = true;
        }
      } catch (Exception ex) {
        // A missing stream state is reported by returning NaN when no other inlet is usable.
      }
    }
    return found ? total : Double.NaN;
  }

  private static SystemInterface firstFluid(List<StreamInterface> streams) {
    if (streams == null) {
      return null;
    }
    for (StreamInterface stream : streams) {
      if (stream != null) {
        try {
          if (stream.getFluid() != null) {
            return stream.getFluid();
          }
        } catch (Exception ex) {
          // Continue to the next inlet.
        }
      }
    }
    return null;
  }

  private static JsonArray unresolvedEngineering(EngineeringProject project, JsonArray relief, JsonArray blowdown) {
    JsonArray gaps = new JsonArray();
    if (relief.size() == 0) {
      gaps.add(gap("RELIEF_SCENARIOS", "No equipment had both a declared relief basis and a usable relief study.",
          "Add credible API 521 scenarios or declared design/set pressures for blocked-outlet screening."));
    }
    if (blowdown.size() == 0) {
      gaps.add(gap("BLOWDOWN_FLARE_INPUT", "No readiness-gated dynamic blowdown/flare study was attached.",
          "Add vessel inventory, fluid, BDV/orifice, header, fire and flare evidence."));
    }
    if (!hasPipelineEquipment(project)) {
      gaps.add(gap("PIPING_GEOMETRY",
          "The process contains no explicit PipeLineInterface equipment with diameter, wall thickness and length.",
          "Add pipe/pipeline equipment and line-list/design conditions for NORSOK P-002 and mechanical sizing."));
    }
    boolean silMissing = false;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      silMissing = silMissing || ((requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS)
          && "SIL_UNASSIGNED".equals(requirement.getSilTarget()));
    }
    if (silMissing) {
      gaps.add(gap("SIL_AND_VOTING", "One or more safety functions have no externally justified SIL target.",
          "Import approved HAZOP/LOPA/SIL-assessment and SRS references before selecting voting architecture."));
    }
    if (hasUnassignedValveFailureAction(project)) {
      gaps.add(gap("VALVE_FAILURE_ACTION", "One or more modeled valves have no declared fail-safe action.",
          "Determine fail action from HAZOP consequences, utility failure cases and approved control philosophy."));
    }
    gaps.add(gap("FINAL_SHUTDOWN_ACTIONS",
        "Generated cause-and-effect actions are proposals and are not a final shutdown sequence.",
        "Confirm isolation boundaries, sequencing, delays, permissives, reset and restart in HAZOP/LOPA and the SRS."));
    gaps.add(gap("FINAL_ENGINEERING_APPROVAL",
        "Calculated sizes, material recommendations, trip ranges, valve actions and shutdown effects are proposals.",
        "Complete discipline checking, vendor verification, HAZOP/LOPA and accountable approval."));
    return gaps;
  }

  private static JsonObject gap(String code, String description, String action) {
    JsonObject gap = new JsonObject();
    gap.addProperty("code", code);
    gap.addProperty("description", description);
    gap.addProperty("requiredAction", action);
    return gap;
  }

  private static JsonObject governance() {
    JsonObject governance = new JsonObject();
    governance.addProperty("calculatedValues", "REVIEW_REQUIRED");
    governance.addProperty("ruleInferredValues", "PROPOSED");
    governance.addProperty("silVotingAndShutdownActions", "HAZOP_LOPA_SRS_REQUIRED");
    governance.addProperty("vendorDependentLimits", "VENDOR_CONFIRMATION_REQUIRED");
    governance.addProperty("fitnessForConstruction", false);
    return governance;
  }

  private static boolean hasPipelineEquipment(EngineeringProject project) {
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit instanceof PipeLineInterface) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasUnassignedValveFailureAction(EngineeringProject project) {
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit == null || !unit.getClass().getSimpleName().contains("Valve")) {
        continue;
      }
      DesignConditions design = unit.getDesignConditions();
      if (design == null || !design.isFailureActionSet()) {
        return true;
      }
    }
    return false;
  }

  private static JsonObject calculationFailure(String itemId, String calculation, Exception ex) {
    JsonObject failure = new JsonObject();
    failure.addProperty("itemId", itemId);
    failure.addProperty("calculation", calculation);
    failure.addProperty("status", "NOT_CALCULATED_INPUT_OR_MODEL_ERROR");
    failure.addProperty("dataGap", safeMessage(ex));
    failure.addProperty("approvalStatus", "REVIEW_REQUIRED");
    return failure;
  }

  private static JsonElement parseJson(String json) {
    try {
      return JsonParser.parseString(json);
    } catch (Exception ex) {
      return GSON.toJsonTree(json);
    }
  }

  private static String safeMessage(Exception ex) {
    String message = ex.getMessage();
    return message == null || message.trim().isEmpty() ? ex.getClass().getSimpleName() : message;
  }
}
