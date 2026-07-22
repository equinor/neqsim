package neqsim.process.engineering;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesignCalculator;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyHandoff;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyRunner;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;
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
    root.addProperty("schemaVersion", "neqsim_engineering_calculations.v3");
    root.addProperty("projectId", project.getProjectId());
    root.addProperty("projectName", project.getName());
    root.addProperty("documentStatus", "CALCULATED_AND_PROPOSED_REVIEW_REQUIRED");
    root.addProperty("calculationAuthority", "NeqSim process simulation and engineering calculators");
    root.addProperty("approvalAuthority", "Accountable project engineering disciplines");

    JsonArray equipmentResults = new JsonArray();
    int calculatedEquipmentCount = calculateMechanicalDesigns(project, equipmentResults);
    root.add("equipmentMechanicalDesign", equipmentResults);

    JsonArray lineResults = calculateLineDesigns(project);
    root.add("pipingLineListAndMechanicalDesign", lineResults);

    JsonArray settingEnvelopes = calculateSettingEnvelopes(project);
    root.add("tripSettingEnvelopes", settingEnvelopes);

    JsonArray safetyFunctionResults = calculateSafetyFunctions(project);
    root.add("silAndVotingVerification", safetyFunctionResults);

    JsonArray shutdownResults = calculateShutdownSequences(project);
    root.add("shutdownSequenceVerification", shutdownResults);

    JsonArray reliefResults = new JsonArray();
    Set<String> explicitlyStudiedTags = new HashSet<String>();
    Map<String, Set<ReliefCause>> successfullyEvaluatedReliefCauses = new LinkedHashMap<String, Set<ReliefCause>>();
    for (OverpressureProtectionStudy study : project.getOverpressureStudies()) {
      explicitlyStudiedTags.add(study.getItem().getName());
      try {
        OverpressureStudyResult studyResult = study.evaluate();
        addReliefResult(reliefResults, studyResult, "PROJECT_DEFINED_SCENARIOS");
        if (!Double.isFinite(studyResult.getRequiredAreaM2())) {
          continue;
        }
        Set<ReliefCause> causes = successfullyEvaluatedReliefCauses.get(study.getItem().getName());
        if (causes == null) {
          causes = new LinkedHashSet<ReliefCause>();
          successfullyEvaluatedReliefCauses.put(study.getItem().getName(), causes);
        }
        for (ReliefScenario scenario : study.getScenarios()) {
          if (hasCompleteReliefScenarioCalculationBasis(scenario)) {
            causes.add(scenario.getCause());
          }
        }
      } catch (Exception ex) {
        reliefResults.add(calculationFailure(study.getItem().getName(), "RELIEF_STUDY", ex));
      }
    }
    addAutomaticBlockedOutletScreening(project, explicitlyStudiedTags, reliefResults,
        successfullyEvaluatedReliefCauses);
    root.add("overpressureAndPsvSizing", reliefResults);
    JsonArray reliefCoverage = calculateReliefCoverage(project, successfullyEvaluatedReliefCauses);
    root.add("reliefScenarioCoverage", reliefCoverage);
    JsonArray reliefDeviceResults = calculateInstalledReliefDevices(project);
    root.add("installedReliefDeviceVerification", reliefDeviceResults);
    root.add("reliefDisposalNetworkLoads", calculateReliefDisposalLoads(project));

    JsonArray blowdownResults = new JsonArray();
    for (DynamicBlowdownFlareStudyDataSource input : project.getBlowdownFlareStudies()) {
      try {
        DynamicBlowdownFlareStudyHandoff handoff = DynamicBlowdownFlareStudyRunner.builder().build().run(input);
        JsonObject result = GSON.toJsonTree(handoff.toMap()).getAsJsonObject();
        boolean calculated = handoff.getCalculationReadiness() != null
            && handoff.getCalculationReadiness().isReadyForCalculation() && handoff.getResult() != null;
        result.addProperty("status", calculated ? "CALCULATED_REVIEW_REQUIRED" : "NOT_CALCULATED_NOT_READY");
        blowdownResults.add(result);
      } catch (Exception ex) {
        blowdownResults.add(calculationFailure(input.getStudyId(), "DYNAMIC_BLOWDOWN_FLARE_STUDY", ex));
      }
    }
    root.add("dynamicBlowdownAndFlareSizing", blowdownResults);

    if (!project.getExecutableDesignCases().isEmpty() || !project.getCoupledReliefBlowdownFlareStudies().isEmpty()
        || !project.getDynamicSafetyScenarios().isEmpty() || !project.getEngineeringDesignModules().isEmpty()) {
      try {
        root.add("coordinatedEngineeringSimulation",
            GSON.toJsonTree(EngineeringSimulationRunner
                .run(project, EngineeringCaseRunOptions.builder().parallelism(1).requireConvergence(true).build())
                .toMap()));
      } catch (RuntimeException ex) {
        root.add("coordinatedEngineeringSimulation",
            calculationFailure(project.getProjectId(), "COORDINATED_ENGINEERING_SIMULATION", ex));
      }
    }

    try {
      MaterialsReviewReport materials = new MaterialsReviewEngine().evaluate(project.getProcessSystem(),
          project.getMaterialsReviewInput());
      root.add("materialsAndCorrosionScreening", GSON.toJsonTree(materials.toMap()));
    } catch (Exception ex) {
      root.add("materialsAndCorrosionScreening", calculationFailure(project.getName(), "MATERIALS_REVIEW", ex));
    }
    JsonArray readiness = calculateReadiness(project, lineResults, reliefCoverage, safetyFunctionResults,
        blowdownResults);
    root.add("engineeringReadiness", readiness);
    root.add("readinessSummary", readinessSummary(readiness));
    root.add("engineeringCoverageMatrix", GSON.toJsonTree(EngineeringCoverageMatrix.evaluate(project)));
    root.add("engineeringEvidenceStatus", calculateEvidenceStatus(project));
    root.add("unresolvedEngineering", unresolvedEngineering(project, reliefCoverage, blowdownResults));
    root.add("governance", governance());
    return new SimulationEngineeringDesignReport(root, calculatedEquipmentCount, reliefResults.size(),
        blowdownResults.size());
  }

  private static boolean hasCompleteReliefScenarioCalculationBasis(ReliefScenario scenario) {
    if (scenario == null || !scenario.isCredible() || scenario.getCause() == null
        || !(scenario.getReliefRateKgPerS() > 0.0)) {
      return false;
    }
    if (scenario.getPhase() == ReliefPhase.VAPOUR) {
      return scenario.getReliefTemperatureK() > 0.0 && scenario.getMolarMassKgPerMol() > 0.0
          && scenario.getCompressibility() > 0.0 && scenario.getSpecificHeatRatio() > 1.0;
    }
    if (scenario.getPhase() == ReliefPhase.TWO_PHASE) {
      return scenario.getGasMassFraction() >= 0.0 && scenario.getGasMassFraction() <= 1.0
          && scenario.getGasDensityKgPerM3() > 0.0 && scenario.getLiquidDensityKgPerM3() > 0.0
          && scenario.getLatentHeatJPerKg() > 0.0 && scenario.getLiquidHeatCapacityJPerKgK() > 0.0;
    }
    return scenario.getDensityKgPerM3() > 0.0 || scenario.getLiquidDensityKgPerM3() > 0.0;
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

  private static JsonArray calculateLineDesigns(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (LineDesignInput input : project.getLineDesignInputs()) {
      JsonObject item = GSON.toJsonTree(input.toMap()).getAsJsonObject();
      JsonArray missing = GSON.toJsonTree(input.getMissingFields()).getAsJsonArray();
      item.add("missingFields", missing);
      ProcessEquipmentInterface unit = project.getProcessSystem().getUnit(input.getEquipmentTag());
      if (!(unit instanceof PipeLineInterface)) {
        item.addProperty("status", "NOT_CALCULATED_PIPELINE_EQUIPMENT_NOT_FOUND");
        results.add(item);
        continue;
      }
      PipeLineInterface pipe = (PipeLineInterface) unit;
      try {
        JsonObject simulationGeometry = new JsonObject();
        simulationGeometry.addProperty("innerDiameterM", pipe.getDiameter());
        simulationGeometry.addProperty("lengthM", pipe.getLength());
        simulationGeometry.addProperty("equivalentFittingsLengthM", input.getEquivalentFittingsLengthM());
        simulationGeometry.addProperty("elevationChangeM", pipe.getElevation());
        simulationGeometry.addProperty("roughnessM", pipe.getPipeWallRoughness());
        simulationGeometry.addProperty("velocityMPerS", pipe.getVelocity());
        simulationGeometry.addProperty("pressureDropBar", pipe.getPressureDrop());
        item.add("simulationHydraulicsAndGeometry", simulationGeometry);

        TopsidePipingMechanicalDesignCalculator calculator = new TopsidePipingMechanicalDesignCalculator();
        calculator.setDesignPressure(input.getDesignPressureBara(), "bara");
        calculator.setDesignTemperature(input.getDesignTemperatureC());
        calculator.setOuterDiameter(input.getOuterDiameterM());
        calculator.setNominalWallThickness(input.getNominalWallThicknessM());
        calculator.setCorrosionAllowance(input.getCorrosionAllowanceM());
        calculator.setMaterialGrade(input.getMaterialGrade());
        calculator.setPipelineLength(pipe.getLength() + input.getEquivalentFittingsLengthM());
        calculator.setInstallationTemperature(input.getInstallationTemperatureC());
        List<StreamInterface> inlets = unit.getInletStreams();
        calculator.setMassFlowRate(inletMassFlow(inlets));
        SystemInterface fluid = firstFluid(inlets);
        if (fluid != null) {
          calculator.setOperatingTemperature(fluid.getTemperature("C"));
          calculator.setMixtureDensity(fluid.getDensity("kg/m3"));
          calculator.setLiquidFraction(liquidVolumeFraction(fluid));
        }
        boolean accepted = calculator.performDesignVerification();
        JsonObject mechanical = GSON.toJsonTree(calculator.toMap()).getAsJsonObject();
        mechanical.addProperty("mechanicalScreeningAccepted", accepted);
        mechanical.addProperty("providedWallThicknessM", input.getNominalWallThicknessM());
        mechanical.addProperty("providedSupportSpacingM", input.getProposedSupportSpacingM());
        mechanical.addProperty("approvalStatus", "REVIEW_REQUIRED");
        item.add("asmeB31_3MechanicalScreening", mechanical);

        NorsokP002LineSizingValidator.LineSizingResult lineSizing = new NorsokP002LineSizingValidator().validate(pipe);
        item.add("norsokP002HydraulicScreening", GSON.toJsonTree(lineSizing.toMap()));
        item.addProperty("status",
            input.getMissingFields().isEmpty() ? "CALCULATED_REVIEW_REQUIRED" : "CALCULATED_WITH_INCOMPLETE_LINE_LIST");
      } catch (Exception ex) {
        item.addProperty("status", "NOT_CALCULATED_INPUT_OR_MODEL_ERROR");
        item.addProperty("dataGap", safeMessage(ex));
      }
      results.add(item);
    }
    return results;
  }

  private static double liquidVolumeFraction(SystemInterface fluid) {
    double fraction = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      String type = fluid.getPhase(i).getType().toString().toLowerCase();
      if (!type.contains("gas")) {
        fraction += fluid.getVolumeFraction(i);
      }
    }
    return Math.max(0.0, Math.min(1.0, fraction));
  }

  private static JsonArray calculateSafetyFunctions(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      JsonObject item = GSON.toJsonTree(design.toMap()).getAsJsonObject();
      boolean requirementFound = false;
      for (EngineeringRequirement requirement : project.getRequirements()) {
        requirementFound = requirementFound || requirement.getId().equals(design.getRequirementId());
      }
      item.addProperty("requirementFound", requirementFound);
      boolean targetMet = design.getAchievedSil() >= design.getTargetSil();
      boolean architectureMet = design.areArchitecturalConstraintsMet();
      item.addProperty("targetMet", targetMet);
      item.addProperty("architecturalConstraintsMet", architectureMet);
      String status = "CALCULATED_WITH_INCOMPLETE_SIF_BASIS";
      if (!requirementFound) {
        status = "NOT_CALCULATED_REQUIREMENT_NOT_FOUND";
      } else if (design.getMissingFields().isEmpty()) {
        status = targetMet && architectureMet ? "CALCULATED_PFD_AND_ARCHITECTURE_REVIEW_REQUIRED"
            : !targetMet ? "CALCULATED_PFD_TARGET_NOT_MET_REVIEW_REQUIRED"
                : "CALCULATED_ARCHITECTURAL_CONSTRAINT_NOT_MET_REVIEW_REQUIRED";
      }
      item.addProperty("status", status);
      item.addProperty("governanceNote", "SIL target must originate from approved risk assessment; component data, "
          + "systematic capability and independence require IEC 61511 verification.");
      results.add(item);
    }
    return results;
  }

  private static JsonArray calculateShutdownSequences(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      JsonObject item = GSON.toJsonTree(sequence.toMap()).getAsJsonObject();
      item.addProperty("status",
          !sequence.getMissingFields().isEmpty() ? "SEQUENCE_INCOMPLETE"
              : sequence.isWithinResponseTimeBudget() ? "SEQUENCE_COMPLETE_REVIEW_REQUIRED"
                  : "SEQUENCE_EXCEEDS_RESPONSE_TIME_BUDGET");
      if (project.getShutdownVerificationResults().containsKey(sequence.getSequenceId())) {
        item.add("dynamicVerification",
            GSON.toJsonTree(project.getShutdownVerificationResults().get(sequence.getSequenceId()).toMap()));
        item.addProperty("dynamicValidationStatus",
            project.getShutdownVerificationResults().get(sequence.getSequenceId()).getVerdict().name());
      } else {
        item.addProperty("dynamicValidationStatus", "NOT_RUN_USE_EMERGENCY_SHUTDOWN_TEST_RUNNER");
      }
      results.add(item);
    }
    return results;
  }

  private static JsonArray calculateInstalledReliefDevices(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      JsonObject item = GSON.toJsonTree(input.toMap()).getAsJsonObject();
      OverpressureStudyResult studyResult = findReliefStudyResult(project, input.getEquipmentTag());
      if (studyResult == null || studyResult.getGoverningScenario() == null) {
        item.addProperty("status", "NOT_CALCULATED_MATCHING_RELIEF_STUDY_REQUIRED");
        results.add(item);
        continue;
      }
      ReliefScenario scenario = studyResult.getGoverningScenario();
      item.addProperty("governingScenario", scenario.getName());
      item.addProperty("relievingPhase", scenario.getPhase().name());
      if (scenario.getPhase() == neqsim.process.safety.overpressure.ReliefPhase.TWO_PHASE
          && "NOT_APPLICABLE_OR_NOT_DEFINED".equals(input.getTwoPhaseMethod())) {
        item.addProperty("status", "NOT_CALCULATED_TWO_PHASE_METHOD_REQUIRED");
        results.add(item);
        continue;
      }
      double density = relievingDensity(scenario, studyResult.getItem().getReliefSetPressureBara());
      if (!Double.isFinite(density) || density <= 0.0 || !input.getMissingFields().isEmpty()) {
        item.addProperty("status", "NOT_CALCULATED_INCOMPLETE_INSTALLED_BASIS");
        item.addProperty("relievingDensityKgPerM3", density);
        results.add(item);
        continue;
      }
      double inletLossBara = pipingPressureLossBara(scenario.getReliefRateKgPerS(), density, input.getInletDiameterM(),
          input.getInletLengthM(), input.getInletResistanceCoefficient());
      double outletLossBara = pipingPressureLossBara(scenario.getReliefRateKgPerS(), density,
          input.getOutletDiameterM(), input.getOutletLengthM(), input.getOutletResistanceCoefficient());
      double setPressure = studyResult.getItem().getReliefSetPressureBara();
      double inletPercent = setPressure > 0.0 ? 100.0 * inletLossBara / setPressure : Double.NaN;
      double outletPercent = setPressure > 0.0 ? 100.0 * outletLossBara / setPressure : Double.NaN;
      boolean areaAdequate = input.getSelectedOrificeAreaIn2() >= studyResult.getRequiredAreaIn2();
      boolean inletAdequate = inletPercent <= input.getAllowableInletLossPercent();
      boolean outletAdequate = outletPercent <= input.getAllowableBuiltUpBackPressurePercent();
      item.addProperty("requiredAreaIn2", studyResult.getRequiredAreaIn2());
      item.addProperty("selectedAreaAdequate", areaAdequate);
      item.addProperty("relievingDensityKgPerM3", density);
      item.addProperty("estimatedInletLossBara", inletLossBara);
      item.addProperty("estimatedInletLossPercentSetPressure", inletPercent);
      item.addProperty("inletLossAcceptable", inletAdequate);
      item.addProperty("estimatedBuiltUpBackPressureBara", outletLossBara);
      item.addProperty("estimatedBuiltUpBackPressurePercentSetPressure", outletPercent);
      item.addProperty("builtUpBackPressureAcceptable", outletAdequate);
      item.addProperty("status",
          areaAdequate && inletAdequate && outletAdequate ? "INSTALLED_RELIEF_SCREEN_ACCEPTED_REVIEW_REQUIRED"
              : "INSTALLED_RELIEF_SCREEN_NOT_ACCEPTED");
      item.addProperty("method", "STEADY_DARCY_WEISBACH_SCREEN_FRICTION_FACTOR_0_02");
      item.addProperty("applicabilityNote",
          "Screening estimate only. Confirm compressible/two-phase pressure drop, "
              + "reaction forces, acoustic effects, allowable backpressure and disposal-header hydraulics in the "
              + "approved relief-system design.");
      results.add(item);
    }
    return results;
  }

  private static OverpressureStudyResult findReliefStudyResult(EngineeringProject project, String equipmentTag) {
    for (OverpressureProtectionStudy study : project.getOverpressureStudies()) {
      if (equipmentTag.equals(study.getItem().getName())) {
        try {
          return study.evaluate();
        } catch (Exception ex) {
          return null;
        }
      }
    }
    return null;
  }

  private static double relievingDensity(ReliefScenario scenario, double pressureBara) {
    if (Double.isFinite(scenario.getDensityKgPerM3()) && scenario.getDensityKgPerM3() > 0.0) {
      return scenario.getDensityKgPerM3();
    }
    if (Double.isFinite(scenario.getGasDensityKgPerM3()) && scenario.getGasDensityKgPerM3() > 0.0) {
      return scenario.getGasDensityKgPerM3();
    }
    if (Double.isFinite(scenario.getMolarMassKgPerMol()) && scenario.getMolarMassKgPerMol() > 0.0
        && Double.isFinite(scenario.getReliefTemperatureK()) && scenario.getReliefTemperatureK() > 0.0
        && Double.isFinite(scenario.getCompressibility()) && scenario.getCompressibility() > 0.0) {
      return pressureBara * 1.0e5 * scenario.getMolarMassKgPerMol()
          / (scenario.getCompressibility() * 8.314462618 * scenario.getReliefTemperatureK());
    }
    return Double.NaN;
  }

  private static double pipingPressureLossBara(double massFlowKgPerS, double densityKgPerM3, double diameterM,
      double lengthM, double resistanceCoefficient) {
    double area = Math.PI * diameterM * diameterM / 4.0;
    double velocity = massFlowKgPerS / (densityKgPerM3 * area);
    double lossCoefficient = 0.02 * lengthM / diameterM + resistanceCoefficient;
    return lossCoefficient * densityKgPerM3 * velocity * velocity / 2.0 / 1.0e5;
  }

  private static JsonArray calculateReliefDisposalLoads(EngineeringProject project) {
    Map<String, Double> groupLoads = new LinkedHashMap<String, Double>();
    Map<String, List<String>> groupContributors = new LinkedHashMap<String, List<String>>();
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      OverpressureStudyResult studyResult = findReliefStudyResult(project, input.getEquipmentTag());
      if (studyResult == null || studyResult.getGoverningScenario() == null || input.getConcurrencyGroup().isEmpty()) {
        continue;
      }
      Double load = groupLoads.get(input.getConcurrencyGroup());
      groupLoads.put(input.getConcurrencyGroup(), Double.valueOf(
          (load == null ? 0.0 : load.doubleValue()) + studyResult.getGoverningScenario().getReliefRateKgPerS()));
      List<String> contributors = groupContributors.get(input.getConcurrencyGroup());
      if (contributors == null) {
        contributors = new java.util.ArrayList<String>();
        groupContributors.put(input.getConcurrencyGroup(), contributors);
      }
      contributors.add(input.getEquipmentTag());
    }
    JsonArray results = new JsonArray();
    for (Map.Entry<String, Double> entry : groupLoads.entrySet()) {
      JsonObject item = new JsonObject();
      item.addProperty("concurrencyGroup", entry.getKey());
      item.addProperty("simultaneousMassFlowKgPerS", entry.getValue().doubleValue());
      item.add("contributors", GSON.toJsonTree(groupContributors.get(entry.getKey())));
      item.addProperty("status", "CALCULATED_LOAD_REVIEW_NETWORK_HYDRAULICS_REQUIRED");
      results.add(item);
    }
    return results;
  }

  private static JsonArray calculateEvidenceStatus(EngineeringProject project) {
    JsonArray results = new JsonArray();
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      JsonObject item = GSON.toJsonTree(evidence.toMap()).getAsJsonObject();
      item.addProperty("status",
          evidence.getMissingFields().isEmpty() ? "EVIDENCE_INDEXED_REVIEW_REQUIRED" : "EVIDENCE_RECORD_INCOMPLETE");
      results.add(item);
    }
    return results;
  }

  private static JsonArray calculateReliefCoverage(EngineeringProject project,
      Map<String, Set<ReliefCause>> successfullyEvaluatedReliefCauses) {
    JsonArray results = new JsonArray();
    for (ReliefScenarioBasis basis : project.getReliefScenarioBases()) {
      Set<ReliefCause> evaluated = new LinkedHashSet<ReliefCause>();
      Set<ReliefCause> successful = successfullyEvaluatedReliefCauses.get(basis.getEquipmentTag());
      if (successful != null) {
        evaluated.addAll(successful);
      }
      Set<ReliefCause> missingCauses = new LinkedHashSet<ReliefCause>(basis.getRequiredCauses());
      missingCauses.removeAll(evaluated);
      JsonObject item = new JsonObject();
      item.addProperty("equipmentTag", basis.getEquipmentTag());
      item.add("requiredCauses", GSON.toJsonTree(basis.getRequiredCauses()));
      item.add("evaluatedCauses", GSON.toJsonTree(evaluated));
      item.add("missingCauses", GSON.toJsonTree(missingCauses));
      item.add("missingBasisFields", GSON.toJsonTree(basis.getMissingFields()));
      int total = basis.getRequiredCauses().size();
      int complete = total - missingCauses.size();
      item.addProperty("evaluatedScenarioCount", complete);
      item.addProperty("requiredScenarioCount", total);
      item.addProperty("completionPercent", total == 0 ? 0.0 : 100.0 * complete / total);
      item.addProperty("status",
          missingCauses.isEmpty() && basis.getMissingFields().isEmpty() ? "SCENARIO_SET_COMPLETE_REVIEW_REQUIRED"
              : "SCENARIO_SET_INCOMPLETE");
      item.addProperty("hazardReviewReference", basis.getHazardReviewReference());
      item.add("evidenceReferences", GSON.toJsonTree(basis.getEvidenceReferences()));
      results.add(item);
    }
    return results;
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
      JsonArray results, Map<String, Set<ReliefCause>> successfullyEvaluatedReliefCauses) {
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
        if (Double.isFinite(result.getRequiredAreaM2())) {
          Set<ReliefCause> causes = successfullyEvaluatedReliefCauses.get(unit.getName());
          if (causes == null) {
            causes = new LinkedHashSet<ReliefCause>();
            successfullyEvaluatedReliefCauses.put(unit.getName(), causes);
          }
          causes.add(ReliefCause.BLOCKED_OUTLET);
        }
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

  private static JsonArray calculateReadiness(EngineeringProject project, JsonArray lineResults,
      JsonArray reliefCoverage, JsonArray safetyFunctionResults, JsonArray blowdownResults) {
    JsonArray readiness = new JsonArray();

    int completeLines = countStatus(lineResults, "CALCULATED_REVIEW_REQUIRED");
    int requiredLines = Math.max(countPipelineEquipment(project), project.getLineDesignInputs().size());
    readiness.add(readinessTopic("PIPING_GEOMETRY", completeLines, Math.max(1, requiredLines), "HIGH",
        "Piping / process", "REVIEW_REQUIRED"));

    int requiredRelief = sumInt(reliefCoverage, "requiredScenarioCount");
    int evaluatedRelief = sumInt(reliefCoverage, "evaluatedScenarioCount");
    readiness.add(readinessTopic("RELIEF_SCENARIOS", evaluatedRelief, Math.max(1, requiredRelief), "CRITICAL",
        "Process safety", "REVIEW_REQUIRED"));

    int tripRequirements = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS) {
        tripRequirements++;
      }
    }
    int completeSifs = countStatus(safetyFunctionResults, "CALCULATED_PFD_AND_ARCHITECTURE_REVIEW_REQUIRED");
    readiness.add(readinessTopic("SIL_AND_VOTING", completeSifs, Math.max(1, tripRequirements), "CRITICAL",
        "Functional safety", "REVIEW_REQUIRED"));

    int completeShutdown = countShutdownRequirements(project, false);
    readiness.add(readinessTopic("FINAL_SHUTDOWN_ACTIONS", completeShutdown, Math.max(1, tripRequirements), "CRITICAL",
        "Process / automation / technical safety", "REVIEW_REQUIRED"));

    int dynamicShutdown = countShutdownRequirements(project, true);
    readiness.add(readinessTopic("DYNAMIC_SAFEGUARD_VERIFICATION", dynamicShutdown, Math.max(1, tripRequirements),
        "CRITICAL", "Process / automation", "REVIEW_REQUIRED"));

    int completeEvidence = 0;
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (evidence.getMissingFields().isEmpty()) {
        completeEvidence++;
      }
    }
    readiness
        .add(readinessTopic("ENGINEERING_EVIDENCE", completeEvidence, Math.max(1, project.getEvidenceRecords().size()),
            "HIGH", "Document control / all disciplines", "REVIEW_REQUIRED"));

    readiness.add(readinessTopic("BLOWDOWN_FLARE_INPUT", countStatus(blowdownResults, "CALCULATED_REVIEW_REQUIRED"),
        Math.max(1, project.getBlowdownFlareStudies().size()), "CRITICAL", "Process safety / flare",
        "REVIEW_REQUIRED"));

    int approvalTotal = project.getRequirements().size() + project.getSafetyFunctionDesigns().size()
        + project.getShutdownSequences().size() + project.getEvidenceRecords().size();
    int approved = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getApprovalStatus() == EngineeringApprovalStatus.APPROVED) {
        approved++;
      }
    }
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      if (design.getApprovalStatus() == EngineeringApprovalStatus.APPROVED) {
        approved++;
      }
    }
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      if (sequence.getApprovalStatus() == EngineeringApprovalStatus.APPROVED) {
        approved++;
      }
    }
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (evidence.getApprovalStatus() == EngineeringApprovalStatus.APPROVED) {
        approved++;
      }
    }
    readiness.add(readinessTopic("FINAL_ENGINEERING_APPROVAL", approved, Math.max(1, approvalTotal), "MANDATORY",
        "Accountable engineering disciplines",
        approved >= Math.max(1, approvalTotal) ? "APPROVED" : "REVIEW_REQUIRED"));
    return readiness;
  }

  private static int countShutdownRequirements(EngineeringProject project, boolean requireDynamicResult) {
    int completed = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() != EngineeringRequirement.Type.TRIP
          && requirement.getType() != EngineeringRequirement.Type.FIRE_AND_GAS) {
        continue;
      }
      boolean covered = false;
      for (ShutdownSequence sequence : project.getShutdownSequences()) {
        if (!sequence.getRequirementIds().contains(requirement.getId()) || !sequence.getMissingFields().isEmpty()
            || !sequence.isWithinResponseTimeBudget()) {
          continue;
        }
        if (!requireDynamicResult) {
          covered = true;
          break;
        }
        if (project.getShutdownVerificationResults().containsKey(sequence.getSequenceId())
            && project.getShutdownVerificationResults().get(sequence.getSequenceId())
                .getVerdict() != neqsim.process.safety.esd.EmergencyShutdownTestResult.Verdict.FAIL) {
          covered = true;
          break;
        }
      }
      if (covered) {
        completed++;
      }
    }
    return completed;
  }

  private static int countPipelineEquipment(EngineeringProject project) {
    int count = 0;
    for (ProcessEquipmentInterface unit : project.getProcessSystem().getUnitOperations()) {
      if (unit instanceof PipeLineInterface) {
        count++;
      }
    }
    return count;
  }

  private static JsonObject readinessTopic(String topic, int completed, int total, String severity, String discipline,
      String approvalState) {
    JsonObject item = new JsonObject();
    int boundedComplete = Math.max(0, Math.min(completed, total));
    double percent = total <= 0 ? 0.0 : 100.0 * boundedComplete / total;
    item.addProperty("topic", topic);
    item.addProperty("completedItemCount", boundedComplete);
    item.addProperty("requiredItemCount", total);
    item.addProperty("missingInputCount", Math.max(0, total - boundedComplete));
    item.addProperty("completenessPercent", percent);
    item.addProperty("status",
        percent >= 100.0 ? "COMPLETE_REVIEW_OR_APPROVAL_REQUIRED" : percent > 0.0 ? "PARTIALLY_COMPLETE" : "NOT_READY");
    item.addProperty("severity", severity);
    item.addProperty("responsibleDiscipline", discipline);
    item.addProperty("approvalState", approvalState);
    return item;
  }

  private static JsonObject readinessSummary(JsonArray readiness) {
    JsonObject summary = new JsonObject();
    double total = 0.0;
    int ready = 0;
    for (JsonElement element : readiness) {
      double percent = element.getAsJsonObject().get("completenessPercent").getAsDouble();
      total += percent;
      if (percent >= 100.0) {
        ready++;
      }
    }
    summary.addProperty("topicCount", readiness.size());
    summary.addProperty("completeTopicCount", ready);
    summary.addProperty("overallCompletenessPercent", readiness.size() == 0 ? 0.0 : total / readiness.size());
    summary.addProperty("fitnessForConstruction", false);
    summary.addProperty("note", "Completeness measures evidence and calculation coverage, not engineering approval.");
    return summary;
  }

  private static int countStatus(JsonArray items, String status) {
    int count = 0;
    for (JsonElement element : items) {
      JsonObject item = element.getAsJsonObject();
      if (item.has("status") && status.equals(item.get("status").getAsString())) {
        count++;
      }
    }
    return count;
  }

  private static int sumInt(JsonArray items, String property) {
    int total = 0;
    for (JsonElement element : items) {
      JsonObject item = element.getAsJsonObject();
      if (item.has(property)) {
        total += item.get(property).getAsInt();
      }
    }
    return total;
  }

  private static JsonArray unresolvedEngineering(EngineeringProject project, JsonArray reliefCoverage,
      JsonArray blowdown) {
    JsonArray gaps = new JsonArray();
    int requiredRelief = sumInt(reliefCoverage, "requiredScenarioCount");
    int evaluatedRelief = sumInt(reliefCoverage, "evaluatedScenarioCount");
    if (requiredRelief == 0 || evaluatedRelief < requiredRelief) {
      gaps.add(gap("RELIEF_SCENARIOS",
          evaluatedRelief + " of " + requiredRelief
              + " hazard-review-required relief scenarios have calculation coverage.",
          "Complete the credible API 521 cause register and evaluate every required scenario."));
    }
    if (!hasCompleteInstalledReliefDesigns(project)) {
      gaps.add(
          gap("INSTALLED_RELIEF_SYSTEM", "No installed PSV or relief-device inlet/outlet piping design was attached.",
              "Add selected orifice, inlet/outlet geometry, concurrency group and controlled design evidence."));
    }
    if (blowdown.size() == 0) {
      gaps.add(gap("BLOWDOWN_FLARE_INPUT", "No readiness-gated dynamic blowdown/flare study was attached.",
          "Add vessel inventory, fluid, BDV/orifice, header, fire and flare evidence."));
    }
    if (!hasCompleteLineDesign(project)) {
      gaps.add(gap("PIPING_GEOMETRY",
          "The controlled line list is absent, incomplete, or not matched to explicit pipeline equipment.",
          "Add diameter, schedule, wall, material, piping class, design conditions and evidence references."));
    }
    boolean silMissing = false;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      silMissing = silMissing || ((requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS)
          && "SIL_UNASSIGNED".equals(requirement.getSilTarget()));
    }
    if (silMissing || !hasCompleteSafetyFunctionDesigns(project)) {
      gaps.add(gap("SIL_AND_VOTING", "One or more safety functions have no externally justified SIL target.",
          "Import LOPA/SIL-assessment and SRS references, then verify sensor, logic and final-element PFD/voting."));
    }
    if (hasUnassignedValveFailureAction(project)) {
      gaps.add(gap("VALVE_FAILURE_ACTION", "One or more modeled valves have no declared fail-safe action.",
          "Determine fail action from HAZOP consequences, utility failure cases and approved control philosophy."));
    }
    if (!hasCompleteShutdownSequences(project)) {
      gaps.add(gap("FINAL_SHUTDOWN_ACTIONS",
          "Shutdown sequences lack actions, safe state, timing budget, reset/restart logic or HAZOP/SRS traceability.",
          "Complete the cause-and-effect sequence and validate process response with the dynamic ESD test runner."));
    }
    int safetyRequirementCount = countSafetyRequirements(project);
    int dynamicRequirementCount = countShutdownRequirements(project, true);
    if (safetyRequirementCount > 0 && dynamicRequirementCount < safetyRequirementCount) {
      gaps.add(gap("DYNAMIC_SAFEGUARD_VERIFICATION",
          dynamicRequirementCount + " of " + safetyRequirementCount
              + " trip/fire-and-gas requirements have acceptable linked dynamic verification.",
          "Run EmergencyShutdownTestRunner and attach a non-failing result to every applicable shutdown sequence."));
    }
    if (!hasCompleteEvidenceRecords(project)) {
      gaps.add(gap("ENGINEERING_EVIDENCE", "No revision-controlled engineering evidence records were attached.",
          "Index HAZOP, LOPA, SRS, vendor, line-list and calculation documents with object links and revisions."));
    }
    if (!allEngineeringApproved(project)) {
      gaps.add(gap("FINAL_ENGINEERING_APPROVAL",
          "Calculated sizes, material recommendations, trip ranges, valve actions and shutdown effects are not "
              + "fully approved.",
          "Complete discipline checking, vendor verification, HAZOP/LOPA and accountable approval."));
    }
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

  private static boolean hasCompleteLineDesign(EngineeringProject project) {
    if (project.getLineDesignInputs().isEmpty()) {
      return false;
    }
    for (LineDesignInput input : project.getLineDesignInputs()) {
      if (!input.getMissingFields().isEmpty()
          || !(project.getProcessSystem().getUnit(input.getEquipmentTag()) instanceof PipeLineInterface)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasCompleteInstalledReliefDesigns(EngineeringProject project) {
    if (project.getReliefDeviceDesignInputs().isEmpty()) {
      return false;
    }
    for (ReliefDeviceDesignInput input : project.getReliefDeviceDesignInputs()) {
      if (!input.getMissingFields().isEmpty() || findReliefStudyResult(project, input.getEquipmentTag()) == null) {
        return false;
      }
    }
    return true;
  }

  private static int countSafetyRequirements(EngineeringProject project) {
    int count = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS) {
        count++;
      }
    }
    return count;
  }

  private static boolean hasCompleteEvidenceRecords(EngineeringProject project) {
    if (project.getEvidenceRecords().isEmpty()) {
      return false;
    }
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (!evidence.getMissingFields().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasCompleteSafetyFunctionDesigns(EngineeringProject project) {
    int required = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() != EngineeringRequirement.Type.TRIP
          && requirement.getType() != EngineeringRequirement.Type.FIRE_AND_GAS) {
        continue;
      }
      required++;
      boolean found = false;
      for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
        found = found || (requirement.getId().equals(design.getRequirementId()) && design.getMissingFields().isEmpty()
            && design.getAchievedSil() >= design.getTargetSil() && design.areArchitecturalConstraintsMet());
      }
      if (!found) {
        return false;
      }
    }
    return required > 0;
  }

  private static boolean hasCompleteShutdownSequences(EngineeringProject project) {
    int required = 0;
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getType() != EngineeringRequirement.Type.TRIP
          && requirement.getType() != EngineeringRequirement.Type.FIRE_AND_GAS) {
        continue;
      }
      required++;
      boolean covered = false;
      for (ShutdownSequence sequence : project.getShutdownSequences()) {
        covered = covered || (sequence.getRequirementIds().contains(requirement.getId())
            && sequence.getMissingFields().isEmpty() && sequence.isWithinResponseTimeBudget());
      }
      if (!covered) {
        return false;
      }
    }
    return required > 0;
  }

  private static boolean allEngineeringApproved(EngineeringProject project) {
    if (project.getRequirements().isEmpty()) {
      return false;
    }
    for (EngineeringRequirement requirement : project.getRequirements()) {
      if (requirement.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        return false;
      }
    }
    for (SafetyFunctionDesign design : project.getSafetyFunctionDesigns()) {
      if (design.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        return false;
      }
    }
    for (ShutdownSequence sequence : project.getShutdownSequences()) {
      if (sequence.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        return false;
      }
    }
    for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
      if (evidence.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        return false;
      }
    }
    return true;
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
