package neqsim.process.safety.opendrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic NORSOK S-001 Clause 9 review engine for open-drain systems.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainReviewEngine {
  /** Standard identifier used by all assessments. */
  public static final String NORSOK_S001 = "NORSOK S-001:2020+AC:2021";
  /** General open-drain clause. */
  public static final String CLAUSE_9_4_1 = "9.4.1";
  /** Drain tanks and seals clause. */
  public static final String CLAUSE_9_4_2 = "9.4.2";
  /** Drain boxes, pipes, and fire divisions clause. */
  public static final String CLAUSE_9_4_3 = "9.4.3";

  /**
   * Evaluates normalized open-drain review input.
   *
   * @param input normalized open-drain review input
   * @return aggregated review report
   */
  public OpenDrainReviewReport evaluate(OpenDrainReviewInput input) {
    OpenDrainReviewInput safeInput = input == null ? new OpenDrainReviewInput() : input;
    OpenDrainReviewReport report = new OpenDrainReviewReport(safeInput.getProjectName());
    for (OpenDrainReviewItem item : safeInput.getItems()) {
      OpenDrainReviewResult result = evaluateItem(item, safeInput.getDefaultLiquidLeakRateKgPerS());
      report.addResult(result);
    }
    report.finalizeVerdict();
    return report;
  }

  /**
   * Evaluates one open-drain review item.
   *
   * @param item review item
   * @param defaultLiquidLeakRateKgPerS default worst credible process fire leak rate in kg/s
   * @return item review result
   */
  public OpenDrainReviewResult evaluateItem(OpenDrainReviewItem item,
      double defaultLiquidLeakRateKgPerS) {
    OpenDrainReviewItem safeItem = item == null ? new OpenDrainReviewItem() : item;
    OpenDrainReviewResult result = new OpenDrainReviewResult(safeItem);
    checkDesignStandardEvidence(safeItem, result);
    checkSpillCollection(safeItem, result);
    checkCapacity(safeItem, result, defaultLiquidLeakRateKgPerS);
    checkBackpressureAndBackflow(safeItem, result);
    checkBunding(safeItem, result);
    checkFloaterSpreading(safeItem, result);
    checkHazardousDrainTank(safeItem, result);
    checkDrainSeparation(safeItem, result);
    checkSealsAndVentRouting(safeItem, result);
    checkHelideckDrainage(safeItem, result);
    checkDrillingProductionSeparation(safeItem, result);
    checkTemporaryStorageDrainage(safeItem, result);
    checkFireDivisionIntegrity(safeItem, result);
    checkUtilityIndependence(safeItem, result);
    checkOperationalEvidence(safeItem, result);
    result.setConfidence(calculateConfidence(safeItem));
    result.finalizeVerdict();
    return result;
  }

  /**
   * Checks documented design standards.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkDesignStandardEvidence(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    List<String> standards =
        item.getStringList("designStandards", "standards", "drainageSystemStandards");
    String combined = joinLower(standards);
    OpenDrainAssessment assessment;
    if (standards.isEmpty()) {
      assessment = OpenDrainAssessment.warning("OD-S001-9.4.1-STANDARDS", CLAUSE_9_4_1, "MEDIUM",
          "Drain design standards were not documented in the normalized evidence.",
          "Link STID/P&ID or design-basis evidence showing NORSOK S-001, NORSOK P-002, and ISO 13702 coverage.");
    } else if (combined.contains("p-002") && combined.contains("13702")) {
      assessment = OpenDrainAssessment.pass("OD-S001-9.4.1-STANDARDS", CLAUSE_9_4_1,
          "Drainage design references NORSOK P-002 and ISO 13702.",
          "Keep the source references in the evidence register.");
    } else {
      assessment = OpenDrainAssessment.warning("OD-S001-9.4.1-STANDARDS", CLAUSE_9_4_1, "MEDIUM",
          "Drainage design references are incomplete for NORSOK S-001 Clause 9.",
          "Add missing evidence for NORSOK P-002 and ISO 13702 drainage and fire-fighting assumptions.");
    }
    assessment.addDetail("standards", new ArrayList<String>(standards));
    result.addAssessment(assessment);
  }

  /**
   * Checks that flammable or hazardous liquid spills are collected and drained.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkSpillCollection(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    if (!requiresDrainageReview(item)) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.1-COLLECTION",
          CLAUSE_9_4_1, "The item is not identified as a flammable or hazardous liquid source."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.1-COLLECTION", CLAUSE_9_4_1,
        "hasOpenDrainMeasures",
        "Open-drain measures collect and drain spills of flammable or hazardous liquid.",
        "Document catchment, drain boxes, gutters, or decks that direct spills to the open-drain system.");
  }

  /**
   * Checks open-drain capacity for fire water and process-fire leak load.
   *
   * @param item review item
   * @param result result receiving assessments
   * @param defaultLiquidLeakRateKgPerS default liquid leak rate in kg/s
   */
  private void checkCapacity(OpenDrainReviewItem item, OpenDrainReviewResult result,
      double defaultLiquidLeakRateKgPerS) {
    double fireWater =
        item.getDouble(0.0, "fireWaterCapacityKgPerS", "fireWaterRateKgPerS", "delugeLoadKgPerS");
    double leakRate = item.getDouble(defaultLiquidLeakRateKgPerS, "liquidLeakRateKgPerS",
        "worstCredibleLeakKgPerS", "processFireLeakKgPerS");
    double required = Math.max(0.0, fireWater) + Math.max(0.0, leakRate);
    boolean hasCapacity =
        item.hasAny("drainageCapacityKgPerS", "openDrainCapacityKgPerS", "capacityKgPerS");
    double capacity = item.getDouble(Double.NaN, "drainageCapacityKgPerS",
        "openDrainCapacityKgPerS", "capacityKgPerS");
    OpenDrainAssessment assessment;
    if (!hasCapacity) {
      assessment = OpenDrainAssessment.warning("OD-S001-9.4.1-CAPACITY", CLAUSE_9_4_1, "HIGH",
          "Open-drain hydraulic capacity was not documented.",
          "Add drainage capacity from hydraulic design or STID line-list sizing and compare against fire water plus process fire leak load.");
    } else if (capacity + 1.0e-12 < required) {
      assessment = OpenDrainAssessment.fail("OD-S001-9.4.1-CAPACITY", CLAUSE_9_4_1, "CRITICAL",
          "Open-drain capacity is lower than the required fire water plus process leak load.",
          "Increase drain-box, pipe, sump, caisson, or pump capacity, or document a lower credible simultaneous load.");
    } else {
      assessment = OpenDrainAssessment.pass("OD-S001-9.4.1-CAPACITY", CLAUSE_9_4_1,
          "Open-drain capacity meets or exceeds the required load.",
          "Retain capacity basis and credible leak assumptions in the review evidence.");
    }
    assessment.addDetail("fireWaterCapacityKgPerS", fireWater);
    assessment.addDetail("liquidLeakRateKgPerS", leakRate);
    assessment.addDetail("requiredCapacityKgPerS", required);
    if (hasCapacity) {
      assessment.addDetail("drainageCapacityKgPerS", capacity);
    }
    if (!item.hasAny("fireWaterCapacityKgPerS", "fireWaterRateKgPerS", "delugeLoadKgPerS")) {
      assessment.addDetail("warning",
          "No fire-water load was supplied; capacity check used process leak load only.");
    }
    result.addAssessment(assessment);
  }

  /**
   * Checks backpressure and backflow prevention.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkBackpressureAndBackflow(OpenDrainReviewItem item,
      OpenDrainReviewResult result) {
    addRequiredBoolean(result, item, "OD-S001-9.4.1-BACKFLOW", CLAUSE_9_4_1, "backflowPrevented",
        "Drain layout prevents backpressure and backflow during deluge/firefighting.",
        "Document hydraulic grade line, check valves, elevation breaks, or segregated routing that prevent reverse flow.");
  }

  /**
   * Checks bunding around flammable liquid tanks and vessels.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkBunding(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    boolean tankArea = item.getBoolean(false, "hasFlammableLiquidTanks", "tankArea",
        "flammableLiquidVesselsPresent");
    if (!tankArea) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.1-BUNDING", CLAUSE_9_4_1,
          "No flammable liquid tank or vessel area is identified."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.1-BUNDING", CLAUSE_9_4_1,
        "bundingAndDrainageAdequate",
        "Flammable liquid tanks and vessels have bunding and drainage.",
        "Confirm bund height, perimeter coverage, drain route, and capacity basis for tank or vessel spill cases.");
  }

  /**
   * Checks spreading controls for floating facilities.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkFloaterSpreading(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    if (!item.getBoolean(false, "isFloater", "floatingFacility", "floater")) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.1-FLOATER", CLAUSE_9_4_1,
          "The item is not identified as part of a floating facility."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.1-FLOATER", CLAUSE_9_4_1,
        "preventsCrossAreaSpreading",
        "Drainage arrangement prevents flammable liquid spreading to other fire areas on a floater.",
        "Document coaming, deck camber, scuppers, and route segregation between adjacent fire areas.");
  }

  /**
   * Checks hazardous-area collection tank purge or inerting.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkHazardousDrainTank(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    boolean applicable = item.getBoolean(false, "hazardousAreaCollectionTank", "hazardousDrainTank",
        "collectionTankInHazardousService")
        || (item.getBoolean(false, "hazardousArea")
            && item.getBoolean(false, "collectionTankPresent"));
    if (!applicable) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-INERTING", CLAUSE_9_4_2,
          "No hazardous-area drain collection tank is identified."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.2-INERTING", CLAUSE_9_4_2, "purgedOrInerted",
        "Collection tanks receiving hazardous-area drains are purged with inert gas.",
        "Provide inert-gas purge design or an equivalent documented basis for preventing explosive atmosphere in the tank.");
  }

  /**
   * Checks separation between closed/open drains and hazardous/non-hazardous drains.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkDrainSeparation(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    addRequiredBoolean(result, item, "OD-S001-9.4.2-CLOSED-OPEN", CLAUSE_9_4_2,
        "closedOpenDrainInteractionPrevented", "Closed and open drain systems cannot interact.",
        "Document physical separation, seal philosophy, or barriers between closed-drain and open-drain systems.");

    Boolean separated = item.getBooleanObject("hazardousNonHazardousPhysicallySeparated");
    Boolean combined =
        item.getBooleanObject("hazardousNonHazardousCombined", "hazardousNonHazardousShareSump");
    OpenDrainAssessment assessment;
    if (Boolean.TRUE.equals(combined) && !Boolean.TRUE.equals(separated)) {
      assessment = OpenDrainAssessment.fail("OD-S001-9.4.2-HAZ-NONHAZ", CLAUSE_9_4_2, "CRITICAL",
          "Hazardous and non-hazardous open drains are combined without documented separation.",
          "Separate hazardous and non-hazardous open drains or document backflow prevention in a common sump/caisson arrangement.");
    } else if (separated == null && combined == null) {
      assessment = OpenDrainAssessment.warning("OD-S001-9.4.2-HAZ-NONHAZ", CLAUSE_9_4_2, "HIGH",
          "Hazardous and non-hazardous drain segregation was not documented.",
          "Add STID/P&ID evidence showing separate routing or common sump safeguards.");
    } else {
      assessment = OpenDrainAssessment.pass("OD-S001-9.4.2-HAZ-NONHAZ", CLAUSE_9_4_2,
          "Hazardous and non-hazardous drain segregation is documented.",
          "Keep the segregation basis and common-sump safeguards traceable.");
    }
    assessment.addDetail("hazardousNonHazardousCombined", combined);
    assessment.addDetail("hazardousNonHazardousPhysicallySeparated", separated);
    result.addAssessment(assessment);

    if (item.getBoolean(false, "commonCaissonOrSump", "sharedCaisson", "sharedSump")) {
      addRequiredBoolean(result, item, "OD-S001-9.4.2-COMMON-SUMP", CLAUSE_9_4_2,
          "nonHazardousBackflowPrevented",
          "Backflow from hazardous to non-hazardous drains is prevented in the common sump/caisson.",
          "Document non-return devices, elevation breaks, seals, or hydraulic checks for the common sump/caisson.");
    } else {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-COMMON-SUMP",
          CLAUSE_9_4_2, "No common sump or caisson is identified."));
    }
  }

  /**
   * Checks liquid/fire seals and vent routing.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkSealsAndVentRouting(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    addRequiredBoolean(result, item, "OD-S001-9.4.2-SEAL-BACKPRESSURE", CLAUSE_9_4_2,
        "sealDesignedForMaxBackpressure", "Liquid seal is designed for maximum backpressure.",
        "Check seal height, hydraulic grade line, and maximum firefighting/drainage backpressure.");
    addRequiredBoolean(result, item, "OD-S001-9.4.2-VENT", CLAUSE_9_4_2, "ventTerminatedSafe",
        "Drain tank or seal venting terminates at a safe location or back to origin area.",
        "Route atmospheric vents safely and ensure seal vents do not transfer flammable vapour between fire areas.");
    if (item.getBoolean(false, "multipleFireAreas", "crossesFireAreas", "fireAreaInterface")) {
      addRequiredBoolean(result, item, "OD-S001-9.4.2-FIRE-SEALS", CLAUSE_9_4_2,
          "liquidFireSealsBetweenFireAreas",
          "Liquid/fire seals prevent gas spread between fire areas.",
          "Provide fire-area liquid seals and route seal venting back to the originating area or another safe location.");
    } else {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-FIRE-SEALS",
          CLAUSE_9_4_2, "No fire-area interface is identified."));
    }
  }

  /**
   * Checks helideck dedicated drain requirement.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkHelideckDrainage(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    if (!isHelideck(item)) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-HELIDECK", CLAUSE_9_4_2,
          "The item is not a helideck drain."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.2-HELIDECK", CLAUSE_9_4_2,
        "dedicatedPipeDrainage", "Helideck drain has dedicated pipe drainage.",
        "Provide dedicated helideck drain piping and avoid shared routes that can transfer fire liquids or vapour.");
  }

  /**
   * Checks separation between drilling and production open-drain systems.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkDrillingProductionSeparation(OpenDrainReviewItem item,
      OpenDrainReviewResult result) {
    if (!item.getBoolean(false, "drillingArea", "drillingDrain")
        && !containsIgnoreCase(item.getAreaType(), "drilling")) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-DRILLING-PROD",
          CLAUSE_9_4_2, "The item is not identified as a drilling drain."));
      return;
    }
    Boolean connected = item.getBooleanObject("drillingProductionOpenDrainConnected",
        "connectedToProductionOpenDrain");
    OpenDrainAssessment assessment;
    if (connected == null) {
      assessment = OpenDrainAssessment.warning("OD-S001-9.4.2-DRILLING-PROD", CLAUSE_9_4_2, "HIGH",
          "Drilling-production open-drain separation was not documented.",
          "Add P&ID/STID evidence showing drilling open drains are not connected to production open drains.");
    } else if (connected.booleanValue()) {
      assessment = OpenDrainAssessment.fail("OD-S001-9.4.2-DRILLING-PROD", CLAUSE_9_4_2, "CRITICAL",
          "Drilling open drain is connected to production open drain.",
          "Separate drilling and production open drains in accordance with NORSOK S-001 Clause 9.4.2.");
    } else {
      assessment = OpenDrainAssessment.pass("OD-S001-9.4.2-DRILLING-PROD", CLAUSE_9_4_2,
          "Drilling and production open drains are separated.",
          "Keep the separation evidence in the review record.");
    }
    assessment.addDetail("drillingProductionOpenDrainConnected", connected);
    result.addAssessment(assessment);
  }

  /**
   * Checks tote-tank and temporary flammable liquid storage drainage.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkTemporaryStorageDrainage(OpenDrainReviewItem item,
      OpenDrainReviewResult result) {
    if (!item.getBoolean(false, "temporaryFlammableLiquidStorage", "toteTankStorage")) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.2-TOTE", CLAUSE_9_4_2,
          "No temporary flammable liquid storage or tote tank area is identified."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.2-TOTE", CLAUSE_9_4_2,
        "adequateCollectionDrainForTote",
        "Temporary flammable-liquid storage has adequate collection and drainage.",
        "Document collection tray, bunding, drain route, and capacity basis for tote or temporary storage areas.");
  }

  /**
   * Checks fire-division integrity around drain boxes and drain piping.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkFireDivisionIntegrity(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    if (!item.getBoolean(false, "drainBoxInFireDivision", "drainPipePenetratesFireDivision",
        "fireDivisionPenetration")) {
      result.addAssessment(OpenDrainAssessment.notApplicable("OD-S001-9.4.3-FIRE-DIVISION",
          CLAUSE_9_4_3, "No drain-box or pipe fire-division penetration is identified."));
      return;
    }
    addRequiredBoolean(result, item, "OD-S001-9.4.3-FIRE-DIVISION", CLAUSE_9_4_3,
        "fireDivisionIntegrityMaintained", "Drain boxes and piping do not impair fire divisions.",
        "Document fire sealing, fire dampers, insulation, or routing that preserves the fire-division rating.");
  }

  /**
   * Checks that open-drain function is not utility dependent.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkUtilityIndependence(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    Boolean dependency = item.getBooleanObject("openDrainDependsOnUtility", "dependsOnUtility",
        "requiresUtilityForDrainage");
    OpenDrainAssessment assessment;
    if (dependency == null) {
      assessment = OpenDrainAssessment.info("OD-S001-9.4.1-UTILITY", CLAUSE_9_4_1,
          "Utility dependency was not supplied in the normalized evidence.",
          "Confirm open-drain function under loss of utility for safety-critical scenarios.");
    } else if (dependency.booleanValue()) {
      assessment = OpenDrainAssessment.fail("OD-S001-9.4.1-UTILITY", CLAUSE_9_4_1, "HIGH",
          "Open-drain function depends on utility availability.",
          "Remove the utility dependency or document fail-safe drainage capacity for loss-of-utility cases.");
    } else {
      assessment = OpenDrainAssessment.pass("OD-S001-9.4.1-UTILITY", CLAUSE_9_4_1,
          "Open-drain function is not dependent on utility availability.",
          "Keep the utility-independence basis in the evidence record.");
    }
    assessment.addDetail("openDrainDependsOnUtility", dependency);
    result.addAssessment(assessment);
  }

  /**
   * Checks optional tagreader or historian evidence.
   *
   * @param item review item
   * @param result result receiving assessments
   */
  private void checkOperationalEvidence(OpenDrainReviewItem item, OpenDrainReviewResult result) {
    boolean hasOperationalEvidence = item.hasAny("observedBackflowEvents", "sumpHighLevelEvents",
        "observedBackpressureBara", "maximumAllowedBackpressureBara", "tagreaderSource");
    if (!hasOperationalEvidence) {
      result.addAssessment(OpenDrainAssessment.info("OD-OPS-TAGREADER", "Operational evidence",
          "No tagreader or historian evidence was supplied; standards review uses STID/design evidence only.",
          "Use tagreader evidence when available to check actual sump levels, backpressure, pump status, and valve states during deluge or high-level events."));
      return;
    }
    double backflowEvents = item.getDouble(0.0, "observedBackflowEvents");
    double highLevelEvents = item.getDouble(0.0, "sumpHighLevelEvents");
    double observedBackpressure = item.getDouble(Double.NaN, "observedBackpressureBara");
    double maximumBackpressure = item.getDouble(Double.NaN, "maximumAllowedBackpressureBara");
    OpenDrainAssessment assessment;
    if (backflowEvents > 0.0) {
      assessment = OpenDrainAssessment.fail("OD-OPS-TAGREADER", "Operational evidence", "HIGH",
          "Historian evidence shows observed backflow events.",
          "Investigate backflow cause and update the standards review with corrective actions.");
    } else if (!Double.isNaN(observedBackpressure) && !Double.isNaN(maximumBackpressure)
        && observedBackpressure > maximumBackpressure) {
      assessment = OpenDrainAssessment.fail("OD-OPS-TAGREADER", "Operational evidence", "HIGH",
          "Observed drain backpressure exceeds the documented allowable limit.",
          "Check drain hydraulics, sump/caisson level, pump availability, and check-valve performance.");
    } else if (highLevelEvents > 0.0) {
      assessment = OpenDrainAssessment.warning("OD-OPS-TAGREADER", "Operational evidence", "MEDIUM",
          "Historian evidence shows sump high-level events.",
          "Review operational causes and confirm that high-level events do not compromise open-drain safety functions.");
    } else {
      assessment = OpenDrainAssessment.pass("OD-OPS-TAGREADER", "Operational evidence",
          "Operational tagreader evidence does not indicate backflow, excessive backpressure, or sump high-level events.",
          "Keep the tagreader export reference with the review evidence.");
    }
    assessment.addDetail("observedBackflowEvents", backflowEvents);
    assessment.addDetail("sumpHighLevelEvents", highLevelEvents);
    assessment.addDetail("observedBackpressureBara", observedBackpressure);
    assessment.addDetail("maximumAllowedBackpressureBara", maximumBackpressure);
    result.addAssessment(assessment);
  }

  /**
   * Adds a boolean requirement assessment.
   *
   * @param result result receiving the assessment
   * @param item review item
   * @param requirementId requirement identifier
   * @param clause clause identifier
   * @param key boolean evidence key
   * @param passMessage message when requirement is met
   * @param recommendation recommendation for missing or failing evidence
   */
  private void addRequiredBoolean(OpenDrainReviewResult result, OpenDrainReviewItem item,
      String requirementId, String clause, String key, String passMessage, String recommendation) {
    Boolean documented = item.getBooleanObject(key);
    OpenDrainAssessment assessment;
    if (documented == null) {
      assessment = OpenDrainAssessment.warning(requirementId, clause, "HIGH",
          "Required open-drain evidence is missing: " + key + ".", recommendation);
    } else if (!documented.booleanValue()) {
      assessment = OpenDrainAssessment.fail(requirementId, clause, "CRITICAL",
          "Open-drain requirement is not met: " + key + " is false.", recommendation);
    } else {
      assessment = OpenDrainAssessment.pass(requirementId, clause, passMessage,
          "Keep the source evidence traceable in the STID/P&ID review package.");
    }
    assessment.addDetail(key, documented);
    result.addAssessment(assessment);
  }

  /**
   * Calculates evidence confidence from key fields and source references.
   *
   * @param item review item
   * @return confidence from 0 to 1
   */
  private double calculateConfidence(OpenDrainReviewItem item) {
    double score = 0.20;
    if (!item.getSourceReferences().isEmpty()) {
      score += 0.20;
    }
    if (item.hasAny("drainageCapacityKgPerS", "openDrainCapacityKgPerS", "capacityKgPerS")) {
      score += 0.15;
    }
    if (item.hasAny("hasOpenDrainMeasures", "backflowPrevented",
        "closedOpenDrainInteractionPrevented")) {
      score += 0.20;
    }
    if (item.hasAny("designStandards", "standards", "drainageSystemStandards")) {
      score += 0.10;
    }
    if (item.hasAny("observedBackflowEvents", "sumpHighLevelEvents", "tagreaderSource")) {
      score += 0.15;
    }
    return Math.min(1.0, score);
  }

  /**
   * Determines whether the item is in scope for open-drain review.
   *
   * @param item review item
   * @return true when the item should be reviewed
   */
  private boolean requiresDrainageReview(OpenDrainReviewItem item) {
    Boolean source = item.getBooleanObject("sourceHasFlammableOrHazardousLiquid",
        "flammableOrHazardousLiquidSource");
    if (source != null) {
      return source.booleanValue();
    }
    String searchable = (item.getAreaType() + " " + item.getDrainSystemType()).toLowerCase();
    return searchable.contains("process") || searchable.contains("hazard")
        || searchable.contains("hydrocarbon") || searchable.contains("tank")
        || searchable.contains("well") || searchable.contains("drilling")
        || searchable.contains("helideck") || searchable.contains("storage")
        || searchable.trim().isEmpty();
  }

  /**
   * Tests whether an item is a helideck drain.
   *
   * @param item review item
   * @return true when item is helideck-related
   */
  private boolean isHelideck(OpenDrainReviewItem item) {
    return item.getBoolean(false, "helideckDrain", "helideckArea")
        || containsIgnoreCase(item.getAreaType(), "helideck")
        || containsIgnoreCase(item.getAreaId(), "helideck");
  }

  /**
   * Joins strings into lower-case text.
   *
   * @param values values to join
   * @return lower-case combined text
   */
  private String joinLower(List<String> values) {
    StringBuilder builder = new StringBuilder();
    for (String value : values) {
      if (value != null) {
        builder.append(value.toLowerCase()).append(' ');
      }
    }
    return builder.toString();
  }

  /**
   * Tests whether text contains a token ignoring case.
   *
   * @param text text to search
   * @param token token to find
   * @return true when token is present
   */
  private boolean containsIgnoreCase(String text, String token) {
    return text != null && token != null && text.toLowerCase().contains(token.toLowerCase());
  }
}
