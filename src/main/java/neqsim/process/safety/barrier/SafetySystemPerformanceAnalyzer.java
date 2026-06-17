package neqsim.process.safety.barrier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.SafetyInstrumentedFunction;
import neqsim.process.measurementdevice.FireDetector;
import neqsim.process.measurementdevice.GasDetector;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.barrier.SafetyBarrier.BarrierStatus;
import neqsim.process.safety.barrier.SafetySystemPerformanceReport.BarrierAssessment;
import neqsim.process.safety.barrier.SafetySystemPerformanceReport.FindingSeverity;
import neqsim.process.safety.risk.sis.SILVerificationResult;

/**
 * Assesses active and passive safety-system barrier performance against evidence and demands.
 *
 * <p>
 * The analyzer bridges the existing barrier-register model with NeqSim instrument and SIS
 * implementations. It can consume measurement devices such as {@link FireDetector} and
 * {@link GasDetector}, event/voting SIFs from {@link SafetyInstrumentedFunction}, and scenario
 * demand cases from consequence or document extraction work. The output is a traceable
 * {@link SafetySystemPerformanceReport} suitable for STID-derived safety critical system reviews.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetySystemPerformanceAnalyzer {
  private final BarrierRegister register;
  private final List<SafetySystemDemand> demandCases = new ArrayList<SafetySystemDemand>();
  private final List<MeasurementDeviceInterface> measurementDevices =
      new ArrayList<MeasurementDeviceInterface>();
  private final List<SafetyInstrumentedFunction> safetyInstrumentedFunctions =
      new ArrayList<SafetyInstrumentedFunction>();
  private final List<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction> quantitativeSafetyInstrumentedFunctions =
      new ArrayList<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction>();

  /**
   * Creates an analyzer for a barrier register.
   *
   * @param register source barrier register
   * @throws IllegalArgumentException if register is null
   */
  public SafetySystemPerformanceAnalyzer(BarrierRegister register) {
    if (register == null) {
      throw new IllegalArgumentException("Barrier register cannot be null");
    }
    this.register = register;
  }

  /**
   * Adds a demand case to be assessed against matching barriers.
   *
   * @param demandCase demand case with calculated or documented demand/capacity data
   * @return this analyzer
   */
  public SafetySystemPerformanceAnalyzer addDemandCase(SafetySystemDemand demandCase) {
    if (demandCase != null) {
      demandCases.add(demandCase);
    }
    return this;
  }

  /**
   * Adds a measurement device that may provide detector or instrument performance evidence.
   *
   * @param measurementDevice measurement device to include
   * @return this analyzer
   */
  public SafetySystemPerformanceAnalyzer addMeasurementDevice(
      MeasurementDeviceInterface measurementDevice) {
    if (measurementDevice != null) {
      measurementDevices.add(measurementDevice);
    }
    return this;
  }

  /**
   * Adds all measurement devices from a process system.
   *
   * @param process process system containing measurement devices
   * @return this analyzer
   */
  public SafetySystemPerformanceAnalyzer addMeasurementDevices(ProcessSystem process) {
    if (process != null) {
      for (MeasurementDeviceInterface measurementDevice : process.getMeasurementDevices()) {
        addMeasurementDevice(measurementDevice);
      }
    }
    return this;
  }

  /**
   * Adds an event/voting safety instrumented function.
   *
   * @param safetyInstrumentedFunction SIF to include in detector and voting checks
   * @return this analyzer
   */
  public SafetySystemPerformanceAnalyzer addSafetyInstrumentedFunction(
      SafetyInstrumentedFunction safetyInstrumentedFunction) {
    if (safetyInstrumentedFunction != null) {
      safetyInstrumentedFunctions.add(safetyInstrumentedFunction);
    }
    return this;
  }

  /**
   * Adds a quantitative SIL/PFD safety instrumented function.
   *
   * @param safetyInstrumentedFunction quantitative SIF with claimed SIL, PFD, architecture, and
   *        protected-equipment metadata
   * @return this analyzer
   */
  public SafetySystemPerformanceAnalyzer addQuantitativeSafetyInstrumentedFunction(
      neqsim.process.safety.risk.sis.SafetyInstrumentedFunction safetyInstrumentedFunction) {
    if (safetyInstrumentedFunction != null) {
      quantitativeSafetyInstrumentedFunctions.add(safetyInstrumentedFunction);
    }
    return this;
  }

  /**
   * Runs the performance assessment for all barriers in the register.
   *
   * @return safety-system performance report
   */
  public SafetySystemPerformanceReport analyze() {
    SafetySystemPerformanceReport report =
        new SafetySystemPerformanceReport(register.getRegisterId()).setName(register.getName());
    for (SafetyBarrier barrier : register.getBarriers()) {
      List<SafetySystemDemand> matchingDemandCases = getMatchingDemandCases(barrier);
      if (matchingDemandCases.isEmpty()) {
        report.addAssessment(assessBarrier(barrier, null));
      } else {
        for (SafetySystemDemand demandCase : matchingDemandCases) {
          report.addAssessment(assessBarrier(barrier, demandCase));
        }
      }
    }
    return report;
  }

  /**
   * Gets demand cases that match a barrier.
   *
   * @param barrier barrier to match against
   * @return matching demand cases
   */
  private List<SafetySystemDemand> getMatchingDemandCases(SafetyBarrier barrier) {
    List<SafetySystemDemand> matches = new ArrayList<SafetySystemDemand>();
    for (SafetySystemDemand demandCase : demandCases) {
      if (demandCase.matches(barrier)) {
        matches.add(demandCase);
      }
    }
    return matches;
  }

  /**
   * Assesses one barrier against one optional demand case.
   *
   * @param barrier barrier to assess
   * @param demandCase demand case, or null for a generic register assessment
   * @return barrier assessment
   */
  private BarrierAssessment assessBarrier(SafetyBarrier barrier, SafetySystemDemand demandCase) {
    SafetySystemCategory category = determineCategory(barrier, demandCase);
    BarrierAssessment assessment = new BarrierAssessment(barrier.getId())
        .setBarrierName(barrier.getName()).setCategory(category);
    if (demandCase != null) {
      assessment.setDemandId(demandCase.getDemandId());
    }

    assessment.addMetric("barrierStatus", barrier.getStatus().name());
    assessment.addMetric("barrierPfd", barrier.getPfd());
    assessment.addMetric("barrierEffectiveness", barrier.getEffectiveness());
    assessment.addMetric("hasTraceableEvidence", barrier.hasTraceableEvidence());

    evaluateBarrierCredit(barrier, assessment);
    evaluatePerformanceStandard(barrier, demandCase, assessment);
    evaluateDemandCapacity(category, demandCase, assessment);
    evaluateInstrumentation(category, barrier, demandCase, assessment);
    evaluateDemandCompleteness(category, demandCase, assessment);
    return assessment;
  }

  /**
   * Evaluates whether the barrier can be credited based on status and evidence.
   *
   * @param barrier barrier to evaluate
   * @param assessment assessment to update
   */
  private void evaluateBarrierCredit(SafetyBarrier barrier, BarrierAssessment assessment) {
    if (barrier.getStatus() != BarrierStatus.AVAILABLE) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Barrier is not AVAILABLE and should not be credited without explicit impairment review.",
          "Restore the barrier, document compensating measures, or remove credit from LOPA/QRA.");
    }
    if (!barrier.hasTraceableEvidence()) {
      assessment.addFinding(FindingSeverity.WARNING, "Barrier has no traceable document evidence.",
          "Link STID, P&ID, C&E, SRS, inspection, or vendor evidence before claiming credit.");
    }
  }

  /**
   * Evaluates quantitative performance-standard criteria.
   *
   * @param barrier barrier to evaluate
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void evaluatePerformanceStandard(SafetyBarrier barrier, SafetySystemDemand demandCase,
      BarrierAssessment assessment) {
    PerformanceStandard standard = barrier.getPerformanceStandard();
    if (standard == null) {
      assessment.addFinding(FindingSeverity.WARNING,
          "No performance standard is linked to the barrier.",
          "Create or link a performance standard with PFD, availability, response time, and acceptance criteria.");
      return;
    }

    assessment.addMetric("performanceStandardId", standard.getId());
    checkPfdRequirement(barrier, standard, demandCase, assessment);
    checkAvailabilityRequirement(standard, demandCase, assessment);
    checkResponseTimeRequirement(standard, demandCase, assessment);
    checkEffectivenessRequirement(barrier, demandCase, assessment);
  }

  /**
   * Checks the PFD requirement.
   *
   * @param barrier barrier to evaluate
   * @param standard linked performance standard
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void checkPfdRequirement(SafetyBarrier barrier, PerformanceStandard standard,
      SafetySystemDemand demandCase, BarrierAssessment assessment) {
    double targetPfd = selectFirstFinite(
        demandCase == null ? Double.NaN : demandCase.getRequiredPfd(), standard.getTargetPfd());
    double actualPfd = selectFirstFinite(
        demandCase == null ? Double.NaN : demandCase.getActualPfd(), barrier.getPfd());
    if (Double.isNaN(targetPfd)) {
      return;
    }
    assessment.addMetric("targetPfd", targetPfd);
    assessment.addMetric("actualPfd", actualPfd);
    if (Double.isNaN(actualPfd)) {
      assessment.addFinding(FindingSeverity.WARNING,
          "Target PFD is defined but actual barrier PFD is missing.",
          "Add SIL verification, vendor reliability data, or documented PFD to the barrier.");
    } else if (actualPfd > targetPfd) {
      assessment.addFinding(FindingSeverity.FAIL, "Actual PFD exceeds the target PFD.",
          "Improve architecture, proof testing, diagnostics, or final-element reliability.");
    }
  }

  /**
   * Checks the availability requirement.
   *
   * @param standard linked performance standard
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void checkAvailabilityRequirement(PerformanceStandard standard,
      SafetySystemDemand demandCase, BarrierAssessment assessment) {
    double requiredAvailability =
        selectFirstFinite(demandCase == null ? Double.NaN : demandCase.getRequiredAvailability(),
            standard.getRequiredAvailability());
    double actualAvailability =
        demandCase == null ? Double.NaN : demandCase.getActualAvailability();
    if (Double.isNaN(requiredAvailability)) {
      return;
    }
    assessment.addMetric("requiredAvailability", requiredAvailability);
    assessment.addMetric("actualAvailability", actualAvailability);
    if (Double.isNaN(actualAvailability)) {
      assessment.addFinding(FindingSeverity.WARNING,
          "Required availability is defined but actual availability is missing.",
          "Add uptime, impairment, bypass, proof-test, or inspection evidence for this barrier.");
    } else if (actualAvailability < requiredAvailability) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Actual availability is below the required availability.",
          "Reduce impairment duration, improve maintenance, or add redundancy.");
    }
  }

  /**
   * Checks the response-time requirement.
   *
   * @param standard linked performance standard
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void checkResponseTimeRequirement(PerformanceStandard standard,
      SafetySystemDemand demandCase, BarrierAssessment assessment) {
    double requiredResponse = selectFirstFinite(
        demandCase == null ? Double.NaN : demandCase.getRequiredResponseTimeSeconds(),
        standard.getResponseTimeSeconds());
    double actualResponse =
        demandCase == null ? Double.NaN : demandCase.getActualResponseTimeSeconds();
    if (Double.isNaN(requiredResponse)) {
      return;
    }
    assessment.addMetric("requiredResponseTimeSeconds", requiredResponse);
    assessment.addMetric("actualResponseTimeSeconds", actualResponse);
    if (Double.isNaN(actualResponse)) {
      assessment.addFinding(FindingSeverity.WARNING,
          "Required response time is defined but actual response time is missing.",
          "Add detector, logic, valve stroke, deluge valve, or PFP endurance response evidence.");
    } else if (actualResponse > requiredResponse) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Actual response time exceeds the required response time.",
          "Reduce detection, logic, valve, or deluge activation time for this function.");
    }
  }

  /**
   * Checks effectiveness requirements when available.
   *
   * @param barrier barrier to evaluate
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void checkEffectivenessRequirement(SafetyBarrier barrier, SafetySystemDemand demandCase,
      BarrierAssessment assessment) {
    if (demandCase == null || Double.isNaN(demandCase.getRequiredEffectiveness())) {
      return;
    }
    double actualEffectiveness =
        selectFirstFinite(demandCase.getActualEffectiveness(), barrier.getEffectiveness());
    assessment.addMetric("requiredEffectiveness", demandCase.getRequiredEffectiveness());
    assessment.addMetric("actualEffectiveness", actualEffectiveness);
    if (Double.isNaN(actualEffectiveness)) {
      assessment.addFinding(FindingSeverity.WARNING,
          "Required effectiveness is defined but actual effectiveness is missing.",
          "Document barrier effectiveness from test records, standards, simulations, or vendor data.");
    } else if (actualEffectiveness < demandCase.getRequiredEffectiveness()) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Actual effectiveness is below the required effectiveness.",
          "Improve coverage, capacity, reliability, or credited effectiveness for this barrier.");
    }
  }

  /**
   * Evaluates demand versus capacity values.
   *
   * @param category safety-system category
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void evaluateDemandCapacity(SafetySystemCategory category, SafetySystemDemand demandCase,
      BarrierAssessment assessment) {
    if (demandCase == null) {
      return;
    }
    assessment.addMetric("scenario", demandCase.getScenario());
    assessment.addMetric("demandValue", demandCase.getDemandValue());
    assessment.addMetric("capacityValue", demandCase.getCapacityValue());
    assessment.addMetric("demandUnit", demandCase.getDemandUnit());
    if (!Double.isNaN(demandCase.getDemandValue())
        && !Double.isNaN(demandCase.getCapacityValue())) {
      double margin = demandCase.getCapacityValue() - demandCase.getDemandValue();
      assessment.addMetric("capacityMargin", margin);
      if (margin < 0.0) {
        assessment.addFinding(FindingSeverity.FAIL,
            "Calculated demand exceeds documented barrier capacity.",
            remediationForCapacityFailure(category));
      }
    }
  }

  /**
   * Evaluates linked instruments and SIF voting status.
   *
   * @param category safety-system category
   * @param barrier barrier to evaluate
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void evaluateInstrumentation(SafetySystemCategory category, SafetyBarrier barrier,
      SafetySystemDemand demandCase, BarrierAssessment assessment) {
    List<MeasurementDeviceInterface> devices = findRelatedMeasurementDevices(barrier, category);
    double maxResponseTime = Double.NaN;
    for (MeasurementDeviceInterface device : devices) {
      assessment.addInstrumentTag(instrumentIdentifier(device));
      double responseTime = getInstrumentResponseTime(device);
      if (!Double.isNaN(responseTime)) {
        maxResponseTime =
            Double.isNaN(maxResponseTime) ? responseTime : Math.max(maxResponseTime, responseTime);
      }
    }
    assessment.addMetric("relatedInstrumentCount", devices.size());
    assessment.addMetric("maxInstrumentResponseTimeSeconds", maxResponseTime);
    if (category == SafetySystemCategory.FIRE_GAS_DETECTION && devices.isEmpty()) {
      assessment.addFinding(FindingSeverity.WARNING,
          "No linked fire or gas detector measurement device was found.",
          "Link FireDetector or GasDetector objects by instrument tag, detector name, or barrier equipment tags.");
    }

    if (demandCase != null && Double.isNaN(demandCase.getActualResponseTimeSeconds())
        && !Double.isNaN(maxResponseTime)) {
      double requiredResponse = demandCase.getRequiredResponseTimeSeconds();
      if (!Double.isNaN(requiredResponse) && maxResponseTime > requiredResponse) {
        assessment.addFinding(FindingSeverity.FAIL,
            "Linked detector response time exceeds the demand-case requirement.",
            "Review detector selection, setpoints, voting delay, and cause-and-effect response time.");
      }
    }

    List<SafetyInstrumentedFunction> sifs = findRelatedSafetyInstrumentedFunctions(barrier);
    for (SafetyInstrumentedFunction sif : sifs) {
      assessment.addSafetyInstrumentedFunction(sif.getName());
      evaluateSafetyInstrumentedFunction(sif, assessment);
    }
    assessment.addMetric("relatedSafetyInstrumentedFunctionCount", sifs.size());

    List<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction> quantitativeSifs =
        findRelatedQuantitativeSafetyInstrumentedFunctions(barrier);
    for (neqsim.process.safety.risk.sis.SafetyInstrumentedFunction sif : quantitativeSifs) {
      assessment.addSafetyInstrumentedFunction(quantitativeSifIdentifier(sif));
      evaluateQuantitativeSafetyInstrumentedFunction(sif, assessment);
    }
    assessment.addMetric("relatedQuantitativeSafetyInstrumentedFunctionCount",
        quantitativeSifs.size());
  }

  /**
   * Evaluates category-specific demand completeness.
   *
   * @param category safety-system category
   * @param demandCase demand case, or null
   * @param assessment assessment to update
   */
  private void evaluateDemandCompleteness(SafetySystemCategory category,
      SafetySystemDemand demandCase, BarrierAssessment assessment) {
    if (demandCase != null) {
      return;
    }
    if (category == SafetySystemCategory.FIREWATER_DELUGE
        || category == SafetySystemCategory.PASSIVE_FIRE_PROTECTION) {
      assessment.addFinding(FindingSeverity.WARNING,
          "No scenario demand/capacity case is linked to this fire-protection barrier.",
          "Add NeqSim fire/explosion load or document-derived capacity data before checking margin.");
    }
  }

  /**
   * Evaluates one event/voting safety instrumented function.
   *
   * @param sif safety instrumented function
   * @param assessment assessment to update
   */
  private void evaluateSafetyInstrumentedFunction(SafetyInstrumentedFunction sif,
      BarrierAssessment assessment) {
    int detectorCount = sif.getDetectors().size();
    int bypassed = 0;
    int faulty = 0;
    int tripped = 0;
    for (Detector detector : sif.getDetectors()) {
      if (detector.isBypassed()) {
        bypassed++;
      }
      if (detector.isFaulty()) {
        faulty++;
      }
      if (detector.isTripped()) {
        tripped++;
      }
    }
    assessment.addMetric(sif.getName() + ".votingLogic", sif.getVotingLogic().getNotation());
    assessment.addMetric(sif.getName() + ".detectorCount", detectorCount);
    assessment.addMetric(sif.getName() + ".bypassedDetectorCount", bypassed);
    assessment.addMetric(sif.getName() + ".faultyDetectorCount", faulty);
    assessment.addMetric(sif.getName() + ".trippedDetectorCount", tripped);
    if (sif.isOverridden()) {
      assessment.addFinding(FindingSeverity.FAIL, "Safety instrumented function is overridden.",
          "Remove override or document approved compensating measures before claiming barrier credit.");
    }
    if (detectorCount != sif.getVotingLogic().getTotalSensors()) {
      assessment.addFinding(FindingSeverity.WARNING,
          "SIF detector count does not match configured voting logic total sensor count.",
          "Align detector list with the cause-and-effect or SIS voting architecture.");
    }
    int healthyChannels = detectorCount - bypassed - faulty;
    if (healthyChannels < sif.getVotingLogic().getRequiredTrips()) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Healthy detector channels are insufficient for the configured voting logic.",
          "Restore bypassed or faulty detectors, or place the system in a controlled impaired state.");
    } else if (bypassed > 0 || faulty > 0) {
      assessment.addFinding(FindingSeverity.WARNING,
          "SIF has bypassed or faulty detector channels.",
          "Review impairment controls and confirm remaining voting architecture is acceptable.");
    }
  }

  /**
   * Evaluates one quantitative SIL/PFD safety instrumented function.
   *
   * @param sif quantitative safety instrumented function
   * @param assessment assessment to update
   */
  private void evaluateQuantitativeSafetyInstrumentedFunction(
      neqsim.process.safety.risk.sis.SafetyInstrumentedFunction sif, BarrierAssessment assessment) {
    SILVerificationResult verification = new SILVerificationResult(sif);
    String prefix = quantitativeSifIdentifier(sif);
    assessment.addMetric(prefix + ".claimedSIL", verification.getClaimedSIL());
    assessment.addMetric(prefix + ".achievedSIL", verification.getAchievedSIL());
    assessment.addMetric(prefix + ".silAchieved", verification.isSilAchieved());
    assessment.addMetric(prefix + ".pfdAverage", verification.getPfdAverage());
    assessment.addMetric(prefix + ".pfdUpper", verification.getPfdUpper());
    assessment.addMetric(prefix + ".riskReductionFactor", sif.getRiskReductionFactor());
    assessment.addMetric(prefix + ".architecture", sif.getArchitecture());
    assessment.addMetric(prefix + ".proofTestIntervalHours", sif.getTestIntervalHours());
    assessment.addMetric(prefix + ".protectedEquipment", sif.getProtectedEquipment());
    if (!verification.isSilAchieved() || verification.hasErrors()) {
      assessment.addFinding(FindingSeverity.FAIL,
          "Quantitative SIL/PFD verification does not meet the claimed safety integrity.",
          "Review component reliability, architecture, proof-test interval, and final-element data before claiming SIF credit.");
    }
    for (SILVerificationResult.VerificationIssue issue : verification.getWarnings()) {
      assessment.addFinding(FindingSeverity.WARNING,
          "Quantitative SIF verification warning: " + issue.getDescription(),
          issue.getRecommendation());
    }
  }

  /**
   * Determines a safety-system category from demand data, barrier text, and SCE type.
   *
   * @param barrier barrier to classify
   * @param demandCase demand case, or null
   * @return inferred category
   */
  private SafetySystemCategory determineCategory(SafetyBarrier barrier,
      SafetySystemDemand demandCase) {
    if (demandCase != null && demandCase.getCategory() != SafetySystemCategory.UNKNOWN) {
      return demandCase.getCategory();
    }
    String text = collectBarrierText(barrier);
    if (containsAny(text, "deluge", "firewater", "fire water", "sprinkler", "water spray")) {
      return SafetySystemCategory.FIREWATER_DELUGE;
    }
    if (containsAny(text, "fire detector", "gas detector", "f&g", "fgs", "fire and gas",
        "detection")) {
      return SafetySystemCategory.FIRE_GAS_DETECTION;
    }
    if (containsAny(text, "pfp", "passive fire", "fire proof", "fireproof", "coating")) {
      return SafetySystemCategory.PASSIVE_FIRE_PROTECTION;
    }
    if (containsAny(text, "psd", "process shutdown", "shutdown valve", "process trip")) {
      return SafetySystemCategory.PSD;
    }
    if (containsAny(text, "blowdown", "esd", "emergency shutdown")) {
      return SafetySystemCategory.ESD_BLOWDOWN;
    }
    if (containsAny(text, "psv", "relief", "flare", "vent")) {
      return SafetySystemCategory.RELIEF_FLARE;
    }
    if (containsAny(text, "firewall", "fire wall", "blast wall", "structural")) {
      return SafetySystemCategory.STRUCTURAL_FIREWALL;
    }
    if (containsAny(text, "hipps", "high integrity pressure")) {
      return SafetySystemCategory.HIPPS;
    }
    return inferCategoryFromSafetyCriticalElement(barrier);
  }

  /**
   * Infers category from SCE type when text classification was inconclusive.
   *
   * @param barrier barrier to classify
   * @return inferred category, or UNKNOWN
   */
  private SafetySystemCategory inferCategoryFromSafetyCriticalElement(SafetyBarrier barrier) {
    for (SafetyCriticalElement element : register.getSafetyCriticalElements()) {
      if (element.getBarrier(barrier.getId()) == null) {
        continue;
      }
      if (element.getType() == SafetyCriticalElement.ElementType.INSTRUMENTED_FUNCTION) {
        return SafetySystemCategory.ESD_BLOWDOWN;
      }
      if (element.getType() == SafetyCriticalElement.ElementType.FIRE_PROTECTION) {
        return SafetySystemCategory.FIREWATER_DELUGE;
      }
      if (element.getType() == SafetyCriticalElement.ElementType.STRUCTURAL) {
        return SafetySystemCategory.STRUCTURAL_FIREWALL;
      }
    }
    return SafetySystemCategory.UNKNOWN;
  }

  /**
   * Collects searchable text from a barrier and its performance standard.
   *
   * @param barrier barrier to inspect
   * @return lower-case text bundle
   */
  private String collectBarrierText(SafetyBarrier barrier) {
    StringBuilder builder = new StringBuilder();
    builder.append(barrier.getId()).append(' ');
    builder.append(barrier.getName()).append(' ');
    builder.append(barrier.getDescription()).append(' ');
    builder.append(barrier.getSafetyFunction()).append(' ');
    if (barrier.getPerformanceStandard() != null) {
      builder.append(barrier.getPerformanceStandard().getTitle()).append(' ');
      builder.append(barrier.getPerformanceStandard().getSafetyFunction()).append(' ');
      for (String criterion : barrier.getPerformanceStandard().getAcceptanceCriteria()) {
        builder.append(criterion).append(' ');
      }
    }
    return builder.toString().toLowerCase(Locale.ROOT);
  }

  /**
   * Checks whether text contains any keyword.
   *
   * @param text lower-case text to search
   * @param keywords lower-case or mixed-case keywords
   * @return true if any keyword is present
   */
  private boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds measurement devices related to a barrier.
   *
   * @param barrier barrier to match
   * @param category safety-system category
   * @return related measurement devices
   */
  private List<MeasurementDeviceInterface> findRelatedMeasurementDevices(SafetyBarrier barrier,
      SafetySystemCategory category) {
    List<MeasurementDeviceInterface> related = new ArrayList<MeasurementDeviceInterface>();
    for (MeasurementDeviceInterface device : measurementDevices) {
      if (!isRelevantMeasurementDevice(device, category)) {
        continue;
      }
      if (matchesBarrierTags(device, barrier)) {
        related.add(device);
      }
    }
    return related;
  }

  /**
   * Checks whether a measurement device is relevant to a category.
   *
   * @param device measurement device
   * @param category safety-system category
   * @return true when the device is relevant
   */
  private boolean isRelevantMeasurementDevice(MeasurementDeviceInterface device,
      SafetySystemCategory category) {
    if (category == SafetySystemCategory.FIRE_GAS_DETECTION) {
      return device instanceof FireDetector || device instanceof GasDetector;
    }
    return true;
  }

  /**
   * Checks whether a measurement device matches barrier equipment tags.
   *
   * @param device measurement device
   * @param barrier barrier to match
   * @return true when device name, tag, or location matches
   */
  private boolean matchesBarrierTags(MeasurementDeviceInterface device, SafetyBarrier barrier) {
    for (String tag : barrier.getLinkedEquipmentTags()) {
      if (matchesText(device.getName(), tag) || matchesText(device.getTag(), tag)
          || matchesText(getDeviceLocation(device), tag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a measurement-device location when available.
   *
   * @param device measurement device
   * @return location text, or empty string
   */
  private String getDeviceLocation(MeasurementDeviceInterface device) {
    if (device instanceof FireDetector) {
      return ((FireDetector) device).getLocation();
    }
    if (device instanceof GasDetector) {
      return ((GasDetector) device).getLocation();
    }
    return "";
  }

  /**
   * Gets the best identifier for a measurement device.
   *
   * @param device measurement device
   * @return tag when present, otherwise device name
   */
  private String instrumentIdentifier(MeasurementDeviceInterface device) {
    return device.getTag() == null || device.getTag().trim().isEmpty() ? device.getName()
        : device.getTag();
  }

  /**
   * Gets the response time of a detector-like measurement device.
   *
   * @param device measurement device
   * @return response time in seconds, or NaN when not available
   */
  private double getInstrumentResponseTime(MeasurementDeviceInterface device) {
    if (device instanceof FireDetector) {
      return ((FireDetector) device).getDetectionDelay();
    }
    if (device instanceof GasDetector) {
      return ((GasDetector) device).getResponseTime();
    }
    return Double.NaN;
  }

  /**
   * Finds event/voting SIFs related to a barrier.
   *
   * @param barrier barrier to match
   * @return related safety instrumented functions
   */
  private List<SafetyInstrumentedFunction> findRelatedSafetyInstrumentedFunctions(
      SafetyBarrier barrier) {
    List<SafetyInstrumentedFunction> related = new ArrayList<SafetyInstrumentedFunction>();
    for (SafetyInstrumentedFunction sif : safetyInstrumentedFunctions) {
      if (matchesSafetyInstrumentedFunction(sif, barrier)) {
        related.add(sif);
      }
    }
    return related;
  }

  /**
   * Finds quantitative SIFs related to a barrier.
   *
   * @param barrier barrier to match
   * @return related quantitative safety instrumented functions
   */
  private List<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction> findRelatedQuantitativeSafetyInstrumentedFunctions(
      SafetyBarrier barrier) {
    List<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction> related =
        new ArrayList<neqsim.process.safety.risk.sis.SafetyInstrumentedFunction>();
    for (neqsim.process.safety.risk.sis.SafetyInstrumentedFunction sif : quantitativeSafetyInstrumentedFunctions) {
      if (matchesQuantitativeSafetyInstrumentedFunction(sif, barrier)) {
        related.add(sif);
      }
    }
    return related;
  }

  /**
   * Checks whether a SIF matches a barrier.
   *
   * @param sif safety instrumented function
   * @param barrier barrier to match
   * @return true when SIF or detector names match barrier data
   */
  private boolean matchesSafetyInstrumentedFunction(SafetyInstrumentedFunction sif,
      SafetyBarrier barrier) {
    if (matchesText(sif.getName(), barrier.getId())
        || matchesText(sif.getName(), barrier.getName())) {
      return true;
    }
    for (Detector detector : sif.getDetectors()) {
      for (String tag : barrier.getLinkedEquipmentTags()) {
        if (matchesText(detector.getName(), tag)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks whether a quantitative SIF matches a barrier.
   *
   * @param sif quantitative SIF
   * @param barrier barrier to match
   * @return true when SIF identity or protected-equipment metadata matches barrier data
   */
  private boolean matchesQuantitativeSafetyInstrumentedFunction(
      neqsim.process.safety.risk.sis.SafetyInstrumentedFunction sif, SafetyBarrier barrier) {
    if (matchesText(sif.getId(), barrier.getId()) || matchesText(sif.getName(), barrier.getId())
        || matchesText(sif.getName(), barrier.getName())
        || matchesText(sif.getDescription(), barrier.getSafetyFunction())) {
      return true;
    }
    for (String equipment : sif.getProtectedEquipment()) {
      for (String tag : barrier.getLinkedEquipmentTags()) {
        if (matchesText(equipment, tag)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Gets a stable identifier for a quantitative SIF.
   *
   * @param sif quantitative SIF
   * @return non-empty SIF identifier
   */
  private String quantitativeSifIdentifier(
      neqsim.process.safety.risk.sis.SafetyInstrumentedFunction sif) {
    if (sif.getName() != null && !sif.getName().trim().isEmpty()) {
      return sif.getName().trim();
    }
    if (sif.getId() != null && !sif.getId().trim().isEmpty()) {
      return sif.getId().trim();
    }
    return "quantitativeSIF";
  }

  /**
   * Checks whether two text values match by exact or containment comparison.
   *
   * @param value first text value
   * @param pattern second text value
   * @return true when values match
   */
  private boolean matchesText(String value, String pattern) {
    String left = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    String right = pattern == null ? "" : pattern.trim().toLowerCase(Locale.ROOT);
    if (left.isEmpty() || right.isEmpty()) {
      return false;
    }
    return left.equals(right) || left.contains(right) || right.contains(left);
  }

  /**
   * Selects the first finite value from two candidates.
   *
   * @param first first candidate
   * @param second second candidate
   * @return first finite value, second finite value, or NaN
   */
  private double selectFirstFinite(double first, double second) {
    if (!Double.isNaN(first) && !Double.isInfinite(first)) {
      return first;
    }
    if (!Double.isNaN(second) && !Double.isInfinite(second)) {
      return second;
    }
    return Double.NaN;
  }

  /**
   * Builds remediation text for demand/capacity failures.
   *
   * @param category safety-system category
   * @return remediation text
   */
  private String remediationForCapacityFailure(SafetySystemCategory category) {
    if (category == SafetySystemCategory.FIREWATER_DELUGE) {
      return "Increase deluge application rate, verify pump/header capacity, or reduce protected demand.";
    }
    if (category == SafetySystemCategory.PASSIVE_FIRE_PROTECTION) {
      return "Increase PFP rating, extend protected area, reduce exposure, or add active mitigation.";
    }
    if (category == SafetySystemCategory.STRUCTURAL_FIREWALL) {
      return "Increase fire/blast wall rating or revise layout to reduce load.";
    }
    return "Increase barrier capacity or reduce scenario demand before claiming performance margin.";
  }
}
