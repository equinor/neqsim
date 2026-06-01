package neqsim.process.safety.processsafetysystem;

import java.util.Locale;

/**
 * Deterministic NORSOK S-001 Clause 10 review engine for process safety systems.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ProcessSafetySystemReviewEngine {
  /** Standard identifier used by all assessments. */
  public static final String NORSOK_S001 = "NORSOK S-001:2020+AC:2021";
  /** Process safety system role and interfaces. */
  public static final String CLAUSE_10_1 = "10.1";
  /** Means of protection and process safety system basis. */
  public static final String CLAUSE_10_3 = "10.3";
  /** Process safety principles. */
  public static final String CLAUSE_10_4_1 = "10.4.1";
  /** PSD valves and shutdown actions. */
  public static final String CLAUSE_10_4_2 = "10.4.2";
  /** PSV protection. */
  public static final String CLAUSE_10_4_3 = "10.4.3";
  /** Alarms and operator actions. */
  public static final String CLAUSE_10_4_4 = "10.4.4";
  /** Response time. */
  public static final String CLAUSE_10_4_5 = "10.4.5";
  /** Logic solver. */
  public static final String CLAUSE_10_4_6 = "10.4.6";
  /** Instrumented secondary pressure protection. */
  public static final String CLAUSE_10_4_7 = "10.4.7";
  /** Required utilities. */
  public static final String CLAUSE_10_4_8 = "10.4.8";
  /** PSD principles and independence. */
  public static final String CLAUSE_10_4_9 = "10.4.9";
  /** Survivability. */
  public static final String CLAUSE_10_4_10 = "10.4.10";
  /** Safety lifecycle traceability from hazard analysis to operation. */
  public static final String CLAUSE_LIFECYCLE = "Safety lifecycle";

  /**
   * Evaluates normalized process safety system review input.
   *
   * @param input normalized review input
   * @return aggregated review report
   */
  public ProcessSafetySystemReviewReport evaluate(ProcessSafetySystemReviewInput input) {
    ProcessSafetySystemReviewInput safeInput = input == null ? new ProcessSafetySystemReviewInput()
        : input;
    ProcessSafetySystemReviewReport report =
        new ProcessSafetySystemReviewReport(safeInput.getProjectName());
    report.addResult(evaluateCoverage(safeInput));
    for (ProcessSafetySystemReviewItem item : safeInput.getItems()) {
      report.addResult(evaluateItem(item));
    }
    report.finalizeVerdict();
    return report;
  }

  /**
   * Evaluates one process safety system review item.
   *
   * @param item review item
   * @return item review result
   */
  public ProcessSafetySystemReviewResult evaluateItem(ProcessSafetySystemReviewItem item) {
    ProcessSafetySystemReviewItem safeItem = item == null ? new ProcessSafetySystemReviewItem()
        : item;
    ProcessSafetySystemReviewResult result = new ProcessSafetySystemReviewResult(safeItem);
    checkSafetyLifecycle(safeItem, result);
    checkRoleAndInterfaces(safeItem, result);
    checkMeansOfProtection(safeItem, result);
    checkProcessSafetyPrinciples(safeItem, result);
    checkPsdValves(safeItem, result);
    checkPsvProtection(safeItem, result);
    checkAlarmActions(safeItem, result);
    checkResponseTime(safeItem, result);
    checkLogicSolver(safeItem, result);
    checkSecondaryPressureProtection(safeItem, result);
    checkUtilityDependencies(safeItem, result);
    checkPsdPrinciples(safeItem, result);
    checkSurvivability(safeItem, result);
    checkOperationalEvidence(safeItem, result);
    checkEvidenceQuality(safeItem, result);
    result.setConfidence(calculateConfidence(safeItem));
    result.finalizeVerdict();
    return result;
  }

  /**
   * Evaluates whether the input has broad Clause 10 coverage.
   *
   * @param input review input
   * @return synthetic coverage result
   */
  private ProcessSafetySystemReviewResult evaluateCoverage(ProcessSafetySystemReviewInput input) {
    ProcessSafetySystemReviewItem coverageItem = new ProcessSafetySystemReviewItem()
        .setFunctionId("CLAUSE10-COVERAGE").setFunctionType("COVERAGE")
        .setEquipmentTag("process-safety-system");
    ProcessSafetySystemReviewResult result = new ProcessSafetySystemReviewResult(coverageItem);
    addCoverageAssessment(result, input, "PSS-COVERAGE-PSD", CLAUSE_10_4_2, "PSD",
        "PSD valves and shutdown actions are represented in the review input.",
        "Add C&E/PSD valve evidence covering shutdown actions and final elements.");
    addCoverageAssessment(result, input, "PSS-COVERAGE-PSV", CLAUSE_10_4_3, "PSV",
        "PSV protection evidence is represented in the review input.",
        "Add PSV list or relief-load evidence for protected equipment.");
    addCoverageAssessment(result, input, "PSS-COVERAGE-ALARM", CLAUSE_10_4_4, "ALARM",
        "Alarms and operator actions are represented in the review input.",
        "Add alarm/action response evidence from alarm lists, C&E, or operating procedures.");
    addCoverageAssessment(result, input, "PSS-COVERAGE-SIF", CLAUSE_10_4_6, "SIF",
        "Logic solver or SIF evidence is represented in the review input.",
        "Add SRS, logic solver, or C&E evidence for instrumented safety functions.");
    addCoverageAssessment(result, input, "PSS-COVERAGE-SECONDARY", CLAUSE_10_4_7,
        "SECONDARY_PRESSURE_PROTECTION",
        "Instrumented secondary pressure protection evidence is represented when applicable.",
        "Add Clause 10.4.7 pressure, frequency, leakage, and proof-test evidence when this protection concept is used.");
    result.setConfidence(1.0);
    result.finalizeVerdict();
    return result;
  }

  /**
   * Adds a coverage assessment for a function type.
   *
   * @param result result receiving the assessment
   * @param input review input
   * @param requirementId requirement identifier
   * @param clause clause identifier
   * @param typeToken function type token
   * @param passMessage pass message
   * @param recommendation recommendation for missing evidence
   */
  private void addCoverageAssessment(ProcessSafetySystemReviewResult result,
      ProcessSafetySystemReviewInput input, String requirementId, String clause, String typeToken,
      String passMessage, String recommendation) {
    if (hasType(input, typeToken)) {
      result.addAssessment(ProcessSafetySystemAssessment.pass(requirementId, clause, passMessage,
          "Keep the source references and normalized evidence with the review record."));
    } else {
      result.addAssessment(ProcessSafetySystemAssessment.warning(requirementId, clause, "MEDIUM",
          "No " + typeToken + " evidence item was found in the normalized Clause 10 input.",
          recommendation));
    }
  }

  /**
   * Checks role and interface evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkRoleAndInterfaces(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-10.1-ROLE", CLAUSE_10_1,
        new String[] {"processSafetyRoleDefined", "roleDefined", "safetyFunctionDefined"},
        "Process safety role and safety function are defined.",
        "Document the safety function, protected equipment, initiators, and executive actions.");
    addRequiredBoolean(result, item, "PSS-10.1-INTERFACES", CLAUSE_10_1,
        new String[] {"interfacesDefined", "systemInterfacesDefined", "interfaceRegisterDocumented"},
        "Interfaces to PSD, PSV, SIF, alarms, utilities, and operations are defined.",
        "Add interface evidence from C&E, SRS, utility dependency matrices, and operating procedures.");
  }

  /**
   * Checks lifecycle traceability from hazard identification to operation.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkSafetyLifecycle(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-LIFECYCLE-HAZID-HAZOP-LOPA", CLAUSE_LIFECYCLE,
        new String[] {"hazidHazopLopaCompleted", "hazardAnalysisCompleted",
            "riskAssessmentCompleted", "lopaCompleted"},
        "HAZID, HAZOP, LOPA, or equivalent hazard analysis evidence defines the demand basis.",
        "Attach HAZID/HAZOP/LOPA references that identify the scenario, consequence, demand frequency, and credited protection layers.");
    addRequiredBoolean(result, item, "PSS-LIFECYCLE-SRS", CLAUSE_LIFECYCLE,
        new String[] {"srsDefinesRequiredFunctions", "srsDocumented", "srsReferenceDocumented",
            "silRequirementsDocumented"},
        "SRS evidence defines the required safety function, SIL/PFD, response time, and proof-test basis.",
        "Link each safety function to an SRS entry with initiator, trip, final element, safe state, SIL/PFD, response time, and proof-test interval.");
    addRequiredBoolean(result, item, "PSS-LIFECYCLE-DESIGN-IMPLEMENTATION", CLAUSE_LIFECYCLE,
        new String[] {"sisEsdFgsDesignImplemented", "safetySystemDesignImplemented",
            "designImplementationVerified", "causeAndEffectImplemented"},
        "SIS, ESD, FGS, or PSD design implementation evidence is documented.",
        "Attach design implementation evidence such as C&E implementation, logic solver configuration, final-element data, and FGS/ESD/SIS architecture records.");
    addRequiredBoolean(result, item, "PSS-LIFECYCLE-VERIFY-TEST-OPERATE", CLAUSE_LIFECYCLE,
        new String[] {"verificationTestingOperationConfirmed", "verificationPlanDocumented",
            "proofTestProgramDocumented", "operationsMaintenancePlanDocumented"},
        "Verification, testing, and operational maintenance evidence is documented.",
        "Attach FAT/SAT, proof-test procedure and status, bypass/override controls, operations/maintenance plan, and live instrument-data status where available.");
  }

  /**
   * Checks means of protection and design basis evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkMeansOfProtection(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-10.3-PROTECTION-LAYERS", CLAUSE_10_3,
        new String[] {"protectionLayersDocumented", "meansOfProtectionDocumented",
            "protectionStrategyDocumented"},
        "Means of protection are documented and linked to the hazard scenario.",
        "Document the credited layers: process design, PSD, PSV, SIF, alarms, operator action, and survivability.");
    addRequiredBoolean(result, item, "PSS-10.3-DESIGN-BASIS", CLAUSE_10_3,
        new String[] {"designBasisDocumented", "scenarioBasisDocumented", "hazardBasisDocumented"},
        "Design and scenario basis are documented.",
        "Add cause, consequence, operating envelope, and scenario demand basis from the design review.");
  }

  /**
   * Checks process safety principles.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkProcessSafetyPrinciples(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-10.4.1-PRINCIPLES", CLAUSE_10_4_1,
        new String[] {"processSafetyPrinciplesDocumented", "safeStateDefined",
            "failSafeStateDocumented"},
        "Process safety principles, safe state, and fail-safe behavior are documented.",
        "Add documented safe-state and fail-safe behavior for the process safety function.");
    addRequiredBoolean(result, item, "PSS-10.4.1-BYPASS", CLAUSE_10_4_1,
        new String[] {"bypassManagementDocumented", "overrideManagementDocumented",
            "impairmentManagementDocumented"},
        "Bypass, override, and impairment management are documented.",
        "Add bypass/override register controls, compensating measures, and approval evidence.");
  }

  /**
   * Checks PSD valve and shutdown action evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkPsdValves(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isPsdItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.2-PSD",
          CLAUSE_10_4_2, "The item is not identified as a PSD or shutdown valve function."));
      return;
    }
    addRequiredBoolean(result, item, "PSS-10.4.2-SHUTDOWN-ACTION", CLAUSE_10_4_2,
        new String[] {"shutdownActionDefined", "psdActionDefined", "executiveActionDefined"},
        "Shutdown action is defined for the PSD function.",
        "Add C&E evidence showing initiator, trip, final element, and safe-state action.");
    addRequiredBoolean(result, item, "PSS-10.4.2-FAILSAFE", CLAUSE_10_4_2,
        new String[] {"psdValveFailsSafe", "valveFailsSafe", "finalElementFailsSafe"},
        "PSD valve or final element fails to the documented safe state.",
        "Document actuator fail action and energy-loss behavior for each PSD final element.");
    addRequiredBoolean(result, item, "PSS-10.4.2-ISOLATION", CLAUSE_10_4_2,
        new String[] {"psdValveIsolationAdequate", "shutdownIsolationAdequate",
            "leakTightClassDocumented"},
        "PSD valve isolation and leakage class are documented as adequate.",
        "Add valve specification, seat leakage class, and isolation philosophy evidence.");
  }

  /**
   * Checks PSV protection evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkPsvProtection(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isPsvItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.3-PSV",
          CLAUSE_10_4_3, "The item is not identified as a PSV or relief protection function."));
      return;
    }
    addRequiredBoolean(result, item, "PSS-10.4.3-SIZING-BASIS", CLAUSE_10_4_3,
        new String[] {"psvSizingBasisDocumented", "reliefScenarioDocumented",
            "reliefLoadDocumented"},
        "PSV relief scenario and sizing basis are documented.",
        "Add relief load calculation, governing scenario, set pressure, protected volume, and relief route evidence.");
    addRequiredBoolean(result, item, "PSS-10.4.3-PROTECTED-EQUIPMENT", CLAUSE_10_4_3,
        new String[] {"protectedEquipmentDocumented", "protectedEquipmentTagDocumented"},
        "Protected equipment and pressure boundary are documented.",
        "Link each PSV to protected equipment and design pressure in the PSV list or relief register.");
    double requiredLoad = item.getDouble(Double.NaN, "requiredReliefLoadKgPerS",
        "reliefLoadKgPerS", "requiredCapacityKgPerS");
    double capacity = item.getDouble(Double.NaN, "psvCapacityKgPerS", "ratedCapacityKgPerS",
        "reliefCapacityKgPerS");
    if (Double.isFinite(requiredLoad) && Double.isFinite(capacity)) {
      ProcessSafetySystemAssessment assessment;
      if (capacity + 1.0e-12 >= requiredLoad) {
        assessment = ProcessSafetySystemAssessment.pass("PSS-10.4.3-CAPACITY", CLAUSE_10_4_3,
            "PSV rated capacity meets or exceeds the documented relief load.",
            "Keep PSV sizing calculation and selected orifice evidence traceable.");
      } else {
        assessment = ProcessSafetySystemAssessment.fail("PSS-10.4.3-CAPACITY", CLAUSE_10_4_3,
            "CRITICAL", "PSV rated capacity is below the documented relief load.",
            "Resize the PSV, reduce the relief load, or document a valid alternate protection concept.");
      }
      assessment.addDetail("requiredReliefLoadKgPerS", requiredLoad);
      assessment.addDetail("psvCapacityKgPerS", capacity);
      result.addAssessment(assessment);
    } else {
      result.addAssessment(ProcessSafetySystemAssessment.warning("PSS-10.4.3-CAPACITY",
          CLAUSE_10_4_3, "HIGH", "PSV relief load and rated capacity were not both supplied.",
          "Provide NeqSim/API 520 sizing output or PSV datasheet capacity for capacity margin review."));
    }
  }

  /**
   * Checks alarm and operator action evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkAlarmActions(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isAlarmItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.4-ALARM",
          CLAUSE_10_4_4, "The item is not identified as an alarm or operator action."));
      return;
    }
    addRequiredBoolean(result, item, "PSS-10.4.4-ACTION", CLAUSE_10_4_4,
        new String[] {"alarmActionDefined", "operatorActionDefined", "alarmResponseDefined"},
        "Alarm response and operator action are defined.",
        "Document alarm setpoint, operator response, time available, and consequence of no action.");
    addRequiredBoolean(result, item, "PSS-10.4.4-SETPOINT", CLAUSE_10_4_4,
        new String[] {"alarmSetpointDocumented", "tripSetpointDocumented",
            "actionLimitDocumented"},
        "Alarm or action setpoint is documented.",
        "Add alarm list or C&E evidence showing setpoint, priority, and response action.");
    compareTimes(result, item, "PSS-10.4.4-OPERATOR-TIME", CLAUSE_10_4_4,
        "operatorResponseTimeSeconds", "availableOperatorResponseTimeSeconds",
        "operator response time", "available operator response time");
  }

  /**
   * Checks response time evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkResponseTime(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isActionOrInstrumentedItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.5-RESPONSE",
          CLAUSE_10_4_5, "The item is not an action or instrumented safety function."));
      return;
    }
    compareTimes(result, item, "PSS-10.4.5-RESPONSE", CLAUSE_10_4_5,
        "actualResponseTimeSeconds", "requiredResponseTimeSeconds", "actual response time",
        "required response time");
  }

  /**
   * Checks logic solver evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkLogicSolver(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isLogicItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.6-LOGIC",
          CLAUSE_10_4_6, "The item is not identified as a logic solver, SIF, or PSD function."));
      return;
    }
    addRequiredBoolean(result, item, "PSS-10.4.6-CERTIFIED", CLAUSE_10_4_6,
        new String[] {"logicSolverCertified", "logicSolverSuitable", "sisLogicSolverCertified"},
        "Logic solver suitability and certification evidence are documented.",
        "Add SRS/vendor/IEC 61511 evidence showing logic solver suitability for the safety function.");
    addRequiredBoolean(result, item, "PSS-10.4.6-INDEPENDENCE", CLAUSE_10_4_6,
        new String[] {"logicSolverIndependent", "independentFromControlSystem",
            "sisBpcsIndependenceDocumented"},
        "Logic solver independence from the control system is documented.",
        "Document independence between SIS/PSD logic and non-safety control functions.");
    addRequiredBoolean(result, item, "PSS-10.4.6-TESTED", CLAUSE_10_4_6,
        new String[] {"causeAndEffectTested", "logicFunctionTested", "sifProofTested"},
        "Cause-and-effect or logic function test evidence is documented.",
        "Attach FAT/SAT/proof-test evidence for the logic solver and final elements.");
  }

  /**
   * Checks instrumented secondary pressure protection evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkSecondaryPressureProtection(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isSecondaryPressureItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.7-SECONDARY",
          CLAUSE_10_4_7,
          "The item is not identified as instrumented secondary pressure protection."));
      return;
    }
    S001SecondaryPressureProtectionCriteria criteria =
        S001SecondaryPressureProtectionCriteria.fromItem(item);
    if (criteria.isEmpty()) {
      result.addAssessment(ProcessSafetySystemAssessment.warning("PSS-10.4.7-SECONDARY",
          CLAUSE_10_4_7, "HIGH",
          "Secondary pressure protection criteria were not supplied.",
          "Provide event/design/test pressure, annual demand frequency, leakage basis, and proof-test evidence."));
      return;
    }
    S001SecondaryPressureProtectionResult check = criteria.evaluate();
    ProcessSafetySystemAssessment assessment;
    if (check.isAcceptable()) {
      assessment = ProcessSafetySystemAssessment.pass("PSS-10.4.7-SECONDARY", CLAUSE_10_4_7,
          "Instrumented secondary pressure protection satisfies the supplied Clause 10.4.7 screening criteria.",
          "Keep pressure, frequency, leakage, and proof-test evidence traceable.");
    } else if (!check.isPressureBasisComplete() || !check.isFrequencyConfigured()) {
      assessment = ProcessSafetySystemAssessment.warning("PSS-10.4.7-SECONDARY", CLAUSE_10_4_7,
          "HIGH", "Secondary pressure protection evidence is incomplete.",
          "Provide complete pressure basis and annual frequency target before crediting this protection concept.");
    } else {
      assessment = ProcessSafetySystemAssessment.fail("PSS-10.4.7-SECONDARY", CLAUSE_10_4_7,
          "CRITICAL",
          "Instrumented secondary pressure protection does not satisfy the supplied screening criteria.",
          "Revise the protection concept, frequency allocation, pressure basis, leakage route, or proof-test interval.");
    }
    assessment.addDetail("secondaryPressureProtection", check.toMap());
    result.addAssessment(assessment);
  }

  /**
   * Checks required utility evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkUtilityDependencies(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-10.4.8-UTILITY-LIST", CLAUSE_10_4_8,
        new String[] {"requiredUtilitiesIdentified", "utilityDependenciesIdentified",
            "utilityMatrixDocumented"},
        "Required utilities and dependencies are identified.",
        "Add power, instrument air, hydraulics, nitrogen, communication, and HVAC dependency evidence.");
    Boolean utilityDependent = item.getBooleanObject("utilityDependent", "dependsOnUtility",
        "requiresUtilityForAction");
    if (Boolean.TRUE.equals(utilityDependent)) {
      addRequiredBoolean(result, item, "PSS-10.4.8-UTILITY-LOSS", CLAUSE_10_4_8,
          new String[] {"failSafeOnUtilityLoss", "safeOnUtilityFailure",
              "utilityFailureSafeStateDocumented"},
          "Safety function reaches a safe state on loss of required utility.",
          "Document fail action, stored energy, UPS autonomy, or alternate utility capacity.");
    } else {
      result.addAssessment(ProcessSafetySystemAssessment.info("PSS-10.4.8-UTILITY-LOSS",
          CLAUSE_10_4_8, "No positive utility dependency was supplied for this item.",
          "Confirm the utility dependency matrix remains complete for all safety functions."));
    }
  }

  /**
   * Checks PSD principle evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkPsdPrinciples(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    if (!isPsdItem(item)) {
      result.addAssessment(ProcessSafetySystemAssessment.notApplicable("PSS-10.4.9-PSD",
          CLAUSE_10_4_9, "The item is not identified as a PSD function."));
      return;
    }
    addRequiredBoolean(result, item, "PSS-10.4.9-PSD-INDEPENDENCE", CLAUSE_10_4_9,
        new String[] {"psdIndependentFromControl", "psdIndependenceDocumented",
            "independentFromControlSystem"},
        "PSD independence from normal process control is documented.",
        "Add PSD/BPCS independence evidence from the automation architecture and C&E matrix.");
    addRequiredBoolean(result, item, "PSS-10.4.9-PSD-MANUAL", CLAUSE_10_4_9,
        new String[] {"manualShutdownAvailable", "manualInitiationDocumented",
            "manualPsdAvailable"},
        "Manual PSD initiation or equivalent manual action is documented.",
        "Document manual shutdown initiation points, operator access, and action effect.");
  }

  /**
   * Checks survivability evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkSurvivability(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    addRequiredBoolean(result, item, "PSS-10.4.10-SURVIVABILITY", CLAUSE_10_4_10,
        new String[] {"survivabilityRequirementDocumented", "fireBlastSurvivabilityDocumented",
            "survivabilityDocumented"},
        "Survivability requirement for the process safety function is documented.",
        "Add fire/blast/PFP/endurance requirement linked to the major accident scenario.");
    compareMinimum(result, item, "PSS-10.4.10-ENDURANCE", CLAUSE_10_4_10,
      "requiredSurvivabilityTimeMin", "survivabilityTimeMin", "required survivability time",
      "documented survivability time");
  }

  /**
   * Checks optional tagreader or instrument-data evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkOperationalEvidence(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    boolean hasOperationalEvidence = item.hasAny("tagreaderSource", "instrumentDataSource",
        "bypassActive", "overrideActive", "proofTestOverdue", "tripDemandFailures",
        "observedResponseTimeSeconds");
    if (!hasOperationalEvidence) {
      result.addAssessment(ProcessSafetySystemAssessment.info("PSS-OPS-INSTRUMENT-DATA",
          "Operational evidence",
          "No tagreader or instrument-data evidence was supplied for this item.",
          "Use tagreader or instrument data reader outputs to verify bypass, override, proof-test, and response-time status when available."));
      return;
    }
    boolean bypassActive = item.getBoolean(false, "bypassActive", "sifBypassed", "psdBypassed");
    boolean overrideActive = item.getBoolean(false, "overrideActive", "sifOverridden",
        "psdOverridden");
    boolean proofTestOverdue = item.getBoolean(false, "proofTestOverdue", "testOverdue");
    double tripDemandFailures = item.getDouble(0.0, "tripDemandFailures", "failedDemandCount");
    ProcessSafetySystemAssessment assessment;
    if (bypassActive || overrideActive) {
      assessment = ProcessSafetySystemAssessment.fail("PSS-OPS-INSTRUMENT-DATA",
          "Operational evidence", "HIGH",
          "Instrument data shows active bypass or override on a process safety function.",
          "Remove bypass/override or document approved compensating measures before claiming barrier credit.");
    } else if (proofTestOverdue || tripDemandFailures > 0.0) {
      assessment = ProcessSafetySystemAssessment.warning("PSS-OPS-INSTRUMENT-DATA",
          "Operational evidence", "MEDIUM",
          "Instrument data shows overdue testing or failed trip-demand history.",
          "Review proof-test schedule, impairment controls, and failure investigation records.");
    } else {
      assessment = ProcessSafetySystemAssessment.pass("PSS-OPS-INSTRUMENT-DATA",
          "Operational evidence",
          "Instrument data does not indicate bypass, override, overdue testing, or failed demands.",
          "Keep the tagreader or instrument data export with the review evidence.");
    }
    assessment.addDetail("bypassActive", Boolean.valueOf(bypassActive));
    assessment.addDetail("overrideActive", Boolean.valueOf(overrideActive));
    assessment.addDetail("proofTestOverdue", Boolean.valueOf(proofTestOverdue));
    assessment.addDetail("tripDemandFailures", tripDemandFailures);
    result.addAssessment(assessment);
  }

  /**
   * Checks technical-documentation and source-reference quality.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkEvidenceQuality(ProcessSafetySystemReviewItem item,
      ProcessSafetySystemReviewResult result) {
    ProcessSafetySystemAssessment sourceAssessment;
    if (item.getSourceReferences().isEmpty()) {
      sourceAssessment = ProcessSafetySystemAssessment.warning("PSS-EVIDENCE-SOURCES",
          "Evidence quality", "MEDIUM", "No technical-document source references are attached.",
          "Attach STID, C&E, SRS, PSV list, instrument data, or tagreader source references.");
    } else {
      sourceAssessment = ProcessSafetySystemAssessment.pass("PSS-EVIDENCE-SOURCES",
          "Evidence quality", "Technical-document source references are attached.",
          "Keep private source identifiers outside public task logs when needed.");
    }
    sourceAssessment.addDetail("sourceReferenceCount", item.getSourceReferences().size());
    result.addAssessment(sourceAssessment);
  }

  /**
   * Adds a boolean requirement assessment.
   *
   * @param result result receiving the assessment
   * @param item review item
   * @param requirementId requirement identifier
   * @param clause clause identifier
   * @param keys boolean evidence keys
   * @param passMessage message when requirement is met
   * @param recommendation recommendation for missing or failing evidence
   */
  private void addRequiredBoolean(ProcessSafetySystemReviewResult result,
      ProcessSafetySystemReviewItem item, String requirementId, String clause, String[] keys,
      String passMessage, String recommendation) {
    Boolean documented = item.getBooleanObject(keys);
    ProcessSafetySystemAssessment assessment;
    if (documented == null) {
      assessment = ProcessSafetySystemAssessment.warning(requirementId, clause, "HIGH",
          "Required process safety evidence is missing: " + joinKeys(keys) + ".",
          recommendation);
    } else if (!documented.booleanValue()) {
      assessment = ProcessSafetySystemAssessment.fail(requirementId, clause, "CRITICAL",
          "Process safety requirement is not met: " + joinKeys(keys) + " is false.",
          recommendation);
    } else {
      assessment = ProcessSafetySystemAssessment.pass(requirementId, clause, passMessage,
          "Keep the source evidence traceable in the Clause 10 review package.");
    }
    assessment.addDetail("evidenceKeys", joinKeys(keys));
    assessment.addDetail("value", documented);
    result.addAssessment(assessment);
  }

  /**
   * Compares two time values.
   *
   * @param result result receiving assessment
   * @param item review item
   * @param requirementId requirement identifier
   * @param clause clause identifier
   * @param actualKey key for actual or required value depending on wording
   * @param limitKey key for limit value depending on wording
   * @param actualLabel actual value label
   * @param limitLabel limit value label
   */
  private void compareTimes(ProcessSafetySystemReviewResult result, ProcessSafetySystemReviewItem item,
      String requirementId, String clause, String actualKey, String limitKey, String actualLabel,
      String limitLabel) {
    double actual = item.getDouble(Double.NaN, actualKey);
    double limit = item.getDouble(Double.NaN, limitKey);
    ProcessSafetySystemAssessment assessment;
    if (!Double.isFinite(actual) || !Double.isFinite(limit)) {
      assessment = ProcessSafetySystemAssessment.warning(requirementId, clause, "MEDIUM",
          "Time evidence is incomplete for " + actualLabel + " versus " + limitLabel + ".",
          "Provide both values with units from C&E, SRS, alarm response, or instrument data.");
    } else if (actual <= limit) {
      assessment = ProcessSafetySystemAssessment.pass(requirementId, clause,
          actualLabel + " is within " + limitLabel + ".",
          "Keep response-time evidence traceable to test or design documents.");
    } else {
      assessment = ProcessSafetySystemAssessment.fail(requirementId, clause, "HIGH",
          actualLabel + " exceeds " + limitLabel + ".",
          "Reduce response time, change setpoints/actions, or revise the credited protection layer.");
    }
    assessment.addDetail(actualKey, actual);
    assessment.addDetail(limitKey, limit);
    result.addAssessment(assessment);
  }

  /**
   * Compares a documented capacity or duration against a minimum required value.
   *
   * @param result result receiving assessment
   * @param item review item
   * @param requirementId requirement identifier
   * @param clause clause identifier
   * @param minimumKey key for the minimum required value
   * @param providedKey key for the documented provided value
   * @param minimumLabel minimum value label
   * @param providedLabel provided value label
   */
  private void compareMinimum(ProcessSafetySystemReviewResult result,
      ProcessSafetySystemReviewItem item, String requirementId, String clause, String minimumKey,
      String providedKey, String minimumLabel, String providedLabel) {
    double minimum = item.getDouble(Double.NaN, minimumKey);
    double provided = item.getDouble(Double.NaN, providedKey);
    ProcessSafetySystemAssessment assessment;
    if (!Double.isFinite(minimum) || !Double.isFinite(provided)) {
      assessment = ProcessSafetySystemAssessment.warning(requirementId, clause, "MEDIUM",
          "Minimum-value evidence is incomplete for " + providedLabel + " versus "
              + minimumLabel + ".",
          "Provide both values with units from fire/blast, PFP, SRS, or endurance documentation.");
    } else if (provided + 1.0e-12 >= minimum) {
      assessment = ProcessSafetySystemAssessment.pass(requirementId, clause,
          providedLabel + " meets or exceeds " + minimumLabel + ".",
          "Keep endurance and survivability evidence traceable to the design scenario.");
    } else {
      assessment = ProcessSafetySystemAssessment.fail(requirementId, clause, "HIGH",
          providedLabel + " is below " + minimumLabel + ".",
          "Increase survivability, revise the credited scenario, or add compensating protection.");
    }
    assessment.addDetail(minimumKey, minimum);
    assessment.addDetail(providedKey, provided);
    result.addAssessment(assessment);
  }

  /**
   * Calculates evidence confidence from key fields and source references.
   *
   * @param item review item
   * @return confidence from 0 to 1
   */
  private double calculateConfidence(ProcessSafetySystemReviewItem item) {
    double score = 0.15;
    if (!item.getSourceReferences().isEmpty()) {
      score += 0.20;
    }
    if (item.hasAny("designBasisDocumented", "scenarioBasisDocumented")) {
      score += 0.15;
    }
    if (item.hasAny("hazidHazopLopaCompleted", "srsDefinesRequiredFunctions",
        "verificationTestingOperationConfirmed")) {
      score += 0.10;
    }
    if (item.hasAny("requiredResponseTimeSeconds", "actualResponseTimeSeconds",
        "operatorResponseTimeSeconds")) {
      score += 0.15;
    }
    if (item.hasAny("logicSolverCertified", "psvCapacityKgPerS",
        "maximumEventPressureBara")) {
      score += 0.15;
    }
    if (item.hasAny("tagreaderSource", "instrumentDataSource", "bypassActive")) {
      score += 0.10;
    }
    return Math.min(1.0, score);
  }

  /**
   * Tests whether the input has an item matching a type token.
   *
   * @param input review input
   * @param typeToken type token
   * @return true when an item matches the type
   */
  private boolean hasType(ProcessSafetySystemReviewInput input, String typeToken) {
    for (ProcessSafetySystemReviewItem item : input.getItems()) {
      if (containsIgnoreCase(item.getFunctionType(), typeToken) || hasTypeEvidence(item, typeToken)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests whether an item has evidence keys for a type.
   *
   * @param item review item
   * @param typeToken type token
   * @return true when item has matching evidence keys
   */
  private boolean hasTypeEvidence(ProcessSafetySystemReviewItem item, String typeToken) {
    if ("PSD".equals(typeToken)) {
      return isPsdItem(item);
    }
    if ("PSV".equals(typeToken)) {
      return isPsvItem(item);
    }
    if ("ALARM".equals(typeToken)) {
      return isAlarmItem(item);
    }
    if ("SIF".equals(typeToken)) {
      return isLogicItem(item);
    }
    if ("SECONDARY_PRESSURE_PROTECTION".equals(typeToken)) {
      return isSecondaryPressureItem(item);
    }
    return false;
  }

  /**
   * Tests whether an item is PSD-related.
   *
   * @param item review item
   * @return true when item is PSD-related
   */
  private boolean isPsdItem(ProcessSafetySystemReviewItem item) {
    return containsAny(item.getFunctionType(), "psd", "process shutdown", "shutdown")
        || item.hasAny("psdValveTag", "shutdownActionDefined", "psdValveFailsSafe");
  }

  /**
   * Tests whether an item is PSV-related.
   *
   * @param item review item
   * @return true when item is PSV-related
   */
  private boolean isPsvItem(ProcessSafetySystemReviewItem item) {
    return containsAny(item.getFunctionType(), "psv", "relief", "safety valve")
        || item.hasAny("psvTag", "psvSizingBasisDocumented", "requiredReliefLoadKgPerS");
  }

  /**
   * Tests whether an item is alarm-related.
   *
   * @param item review item
   * @return true when item is alarm-related
   */
  private boolean isAlarmItem(ProcessSafetySystemReviewItem item) {
    return containsAny(item.getFunctionType(), "alarm", "operator action")
        || item.hasAny("alarmTag", "alarmActionDefined", "operatorActionDefined");
  }

  /**
   * Tests whether an item is an action or instrumented item.
   *
   * @param item review item
   * @return true when response time is applicable
   */
  private boolean isActionOrInstrumentedItem(ProcessSafetySystemReviewItem item) {
    return isPsdItem(item) || isAlarmItem(item) || isLogicItem(item)
        || isSecondaryPressureItem(item) || item.hasAny("requiredResponseTimeSeconds",
            "actualResponseTimeSeconds");
  }

  /**
   * Tests whether an item is logic-solver related.
   *
   * @param item review item
   * @return true when item is logic-solver related
   */
  private boolean isLogicItem(ProcessSafetySystemReviewItem item) {
    return containsAny(item.getFunctionType(), "sif", "logic", "sis", "psd")
        || item.hasAny("logicSolverCertified", "logicSolverIndependent", "causeAndEffectTested");
  }

  /**
   * Tests whether an item is secondary pressure protection related.
   *
   * @param item review item
   * @return true when item is secondary pressure protection related
   */
  private boolean isSecondaryPressureItem(ProcessSafetySystemReviewItem item) {
    return containsAny(item.getFunctionType(), "secondary_pressure", "secondary pressure",
        "10.4.7", "overpressure") || item.hasAny("maximumEventPressureBara",
            "eventPressureBara", "demandFrequencyPerYear");
  }

  /**
   * Joins keys for report text.
   *
   * @param keys keys to join
   * @return comma-separated keys
   */
  private String joinKeys(String[] keys) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < keys.length; index++) {
      if (index > 0) {
        builder.append(", ");
      }
      builder.append(keys[index]);
    }
    return builder.toString();
  }

  /**
   * Tests whether text contains any keyword ignoring case.
   *
   * @param text text to search
   * @param keywords keywords to find
   * @return true when any keyword is present
   */
  private boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (containsIgnoreCase(text, keyword)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests whether text contains a token ignoring case.
   *
   * @param text text to search
   * @param token token to find
   * @return true when token is present
   */
  private boolean containsIgnoreCase(String text, String token) {
    return text != null && token != null
        && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
  }
}