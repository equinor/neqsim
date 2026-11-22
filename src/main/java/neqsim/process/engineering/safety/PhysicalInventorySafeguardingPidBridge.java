package neqsim.process.engineering.safety;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.pid.PidProposalStatus;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.IsolationActionProposal;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.SectionProposal;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StandardApplicability;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StudyResult;

/**
 * Materializes physical-inventory safeguarding proposals into the existing governed P&amp;ID and
 * engineering-requirement contracts.
 *
 * <p>
 * The bridge adds no approval, SIL target, voting architecture or safety credit. It preserves the exact calculated
 * range, alarm/trip, response-budget, physical-inventory and provenance values in P&amp;ID attributes so existing
 * DEXPI/package exporters can carry them forward.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class PhysicalInventorySafeguardingPidBridge {
  private static final String RULE_ID = "DYN-C032-PHYSICAL-INVENTORY-SAFEGUARDING";

  private PhysicalInventorySafeguardingPidBridge() {
  }

  /**
   * Creates a standalone governed P&amp;ID proposal model from a completed study.
   *
   * @param result completed physical-inventory safeguarding study
   * @param profileId controlled P&amp;ID profile identity
   * @return traceable review-required P&amp;ID proposal model
   */
  public static PidDesignModel materialize(StudyResult result, String profileId) {
    requireReady(result);
    PidDesignModel model = new PidDesignModel(result.getProjectId(), requireText(profileId, "profileId"));
    for (SectionProposal proposal : result.getProposals()) {
      addSection(model, result, proposal);
    }
    return model;
  }

  /**
   * Creates review-required engineering requirements for the existing engineering package.
   *
   * @param result completed physical-inventory safeguarding study
   * @return deterministic pressure measurement, alarm, trip and control requirement list
   */
  public static List<EngineeringRequirement> requirements(StudyResult result) {
    requireReady(result);
    List<EngineeringRequirement> requirements = new ArrayList<EngineeringRequirement>();
    for (SectionProposal proposal : result.getProposals()) {
      requirements.add(requirement(result, proposal, EngineeringRequirement.Type.INSTRUMENT,
          proposal.getRequirementId() + "-MEASUREMENT", "Measure isolation-section pressure",
          "Provide a traceable pressure measurement covering the declared design envelope."));
      requirements.add(requirement(result, proposal, EngineeringRequirement.Type.CONTROL,
          proposal.getRequirementId() + "-CONTROL", "Control isolation-section pressure",
          "Verify pressure feedback control, tuning, interactions and constraints in the executable dynamic model."));
      requirements.add(requirement(result, proposal, EngineeringRequirement.Type.ALARM,
          proposal.getRequirementId() + "-ALARM", "High-pressure operator alarm",
          "Warn before the proposed high-high trip while retaining the explicit trip-to-design margin."));
      requirements.add(requirement(result, proposal, EngineeringRequirement.Type.TRIP,
          proposal.getRequirementId() + "-TRIP", "High-high pressure isolation demand",
          "Initiate review-required boundary actions within the externally established process safety time."));
    }
    return requirements;
  }

  /**
   * Adds one pressure loop and its physical boundary actions.
   *
   * @param model target P&amp;ID model
   * @param result governing study result
   * @param proposal quantified section proposal
   */
  private static void addSection(PidDesignModel model, StudyResult result, SectionProposal proposal) {
    String section = proposal.getIsolationSectionId();
    PidElement transmitter = base(proposal.getPressureTransmitterTag(), PidElementType.MEASUREMENT, proposal,
        "Isolation-section pressure transmitter",
        "Measure the supplied physical isolation section over the calculated range.")
        .attribute("measuredVariable", "PRESSURE").attribute("rangeLowerBara", Double.valueOf(0.0))
        .attribute("rangeUpperBara", Double.valueOf(proposal.getPressureRangeUpperBara()))
        .attribute("responseRequirementSeconds", Double.valueOf(proposal.getSensorResponseRequirementSeconds()));
    PidElement controller = base(proposal.getPressureControllerTag(), PidElementType.CONTROLLER, proposal,
        "Isolation-section pressure indicating controller",
        "Feedback control candidate; tuning, interactions and constraints require executable dynamic verification.")
        .attribute("controlMethodology", "FEEDBACK_PRESSURE_CONTROL")
        .attribute("tuningStatus", "DYNAMIC_STUDY_REQUIRED");
    PidElement alarm = base(proposal.getHighAlarmTag(), PidElementType.ALARM, proposal, "High-pressure operator alarm",
        "Warn before the proposed high-high pressure trip.")
        .attribute("setPointBara", Double.valueOf(proposal.getHighAlarmBara()))
        .attribute("alarmManagementReview", "IEC_62682_PROJECT_REVIEW_REQUIRED");
    PidElement trip = base(proposal.getHighHighTripTag(), PidElementType.TRIP, proposal,
        "High-high pressure isolation demand",
        "Demand the review-required physical boundary actions within the declared response budget.")
        .attribute("setPointBara", Double.valueOf(proposal.getHighHighTripBara()))
        .attribute("tripToDesignMarginBar", Double.valueOf(proposal.getTripToDesignMarginBar()))
        .attribute("logicResponseRequirementSeconds", Double.valueOf(proposal.getLogicResponseRequirementSeconds()))
        .attribute("processSafetyTimeSeconds", Double.valueOf(proposal.getProcessSafetyTimeSeconds()))
        .attribute("silTarget", "RISK_ASSESSMENT_REQUIRED").attribute("voting", "SRS_INPUT_REQUIRED")
        .attribute("independence", "IEC_61511_LIFECYCLE_REVIEW_REQUIRED");
    transmitter.connect(controller.getId()).connect(alarm.getId()).connect(trip.getId());

    addStandards(transmitter, result);
    addStandards(controller, result);
    addStandards(alarm, result);
    addStandards(trip, result);
    model.add(transmitter).add(controller).add(alarm).add(trip);

    int actionNumber = 1;
    for (IsolationActionProposal action : proposal.getIsolationActions()) {
      PidElement finalElement = base(action.getDeviceTag(), type(action.getDeviceType()), proposal,
          "Physical isolation boundary device " + action.getDeviceTag(), action.getRationale())
          .attribute("physicalInventoryDeviceId", action.getDeviceId())
          .attribute("physicalInventoryDeviceType", action.getDeviceType())
          .attribute("suppliedFailState", action.getSuppliedFailState())
          .attribute("candidateFailureAction", action.getCandidateAction())
          .attribute("alternativeFailureAction", action.getAlternativeAction())
          .attribute("finalElementResponseRequirementSeconds",
              Double.valueOf(proposal.getFinalElementResponseRequirementSeconds()))
          .attribute("causeEffectRow", "C&E-" + normalize(section) + "-" + actionNumber++);
      addStandards(finalElement, result);
      trip.connect(finalElement.getId());
      model.add(finalElement);
    }
  }

  /**
   * Constructs a common traceable P&amp;ID proposal element.
   *
   * @param tag element tag
   * @param type element type
   * @param proposal governing quantified proposal
   * @param description concise description
   * @param rationale engineering rationale
   * @return configured element
   */
  private static PidElement base(String tag, PidElementType type, SectionProposal proposal, String description,
      String rationale) {
    return new PidElement("pid:dyn-c032:" + normalize(tag), tag, type).equipment(proposal.getEquipmentTag())
        .description(description).provenance(RULE_ID, rationale).status(PidProposalStatus.REVIEW_REQUIRED)
        .requirement(proposal.getRequirementId()).attribute("proposalPurpose", "PHYSICAL_INVENTORY_SAFEGUARDING")
        .attribute("isolationSectionId", proposal.getIsolationSectionId())
        .attribute("scenarioId", proposal.getScenarioId()).attribute("scenarioExecutionStatus", "NOT_EXECUTED")
        .attribute("resultId", proposal.getResultId()).attribute("operatingCaseId", proposal.getOperatingCaseId())
        .attribute("operatingCaseRevision", proposal.getOperatingCaseRevision())
        .attribute("operatingEvidenceReference", proposal.getOperatingEvidenceReference())
        .attribute("isolatedVolumeM3", Double.valueOf(proposal.getIsolatedVolumeM3()))
        .attribute("inventoryMassKg", Double.valueOf(proposal.getInventoryMassKg()))
        .attribute("inventoryTurnoverSeconds", Double.valueOf(proposal.getInventoryTurnoverSeconds()))
        .attribute("approvalStatus", "REVIEW_REQUIRED").attribute("safetyCredit", Boolean.FALSE);
  }

  /**
   * Adds the caller-supplied standards register references to one P&amp;ID element.
   *
   * @param element target element
   * @param result governing study result
   */
  private static void addStandards(PidElement element, StudyResult result) {
    for (StandardApplicability standard : result.getStandardsApplicability()) {
      element.standard(standard.getStandardId() + ":" + standard.getEdition()).attribute(
          "standardEvidence:" + normalize(standard.getStandardId()),
          standard.getEvidenceStatus().name() + ":" + standard.getEvidenceReference());
    }
  }

  /**
   * Constructs one requirement with the study standards register attached.
   *
   * @param result governing study result
   * @param proposal section proposal
   * @param type requirement discipline
   * @param id stable requirement identity
   * @param title concise title
   * @param rationale engineering rationale
   * @return review-required requirement
   */
  private static EngineeringRequirement requirement(StudyResult result, SectionProposal proposal,
      EngineeringRequirement.Type type, String id, String title, String rationale) {
    EngineeringRequirement requirement = new EngineeringRequirement(id, proposal.getEquipmentTag(), type, title,
        rationale + " Physical section " + proposal.getIsolationSectionId() + ", scenario " + proposal.getScenarioId()
            + ", result " + proposal.getResultId() + ".");
    for (StandardApplicability standard : result.getStandardsApplicability()) {
      requirement.addStandardReference(standard.getStandardId() + ":" + standard.getEdition());
    }
    return requirement;
  }

  /**
   * Maps a DYN-C029 physical device type to an existing P&amp;ID element type.
   *
   * @param deviceType DYN-C029 device type
   * @return P&amp;ID element type
   */
  private static PidElementType type(String deviceType) {
    if ("emergency_shutdown_valve".equals(deviceType) || "process_shutdown_valve".equals(deviceType)) {
      return PidElementType.SHUTDOWN_VALVE;
    }
    if ("check_valve".equals(deviceType)) {
      return PidElementType.CHECK_VALVE;
    }
    return PidElementType.ISOLATION_VALVE;
  }

  /**
   * Requires a completed, unblocked study result.
   *
   * @param result study result
   */
  private static void requireReady(StudyResult result) {
    if (result == null || !result.isReadyForDisciplineReview()) {
      throw new IllegalArgumentException("study result must be unblocked and ready for discipline review");
    }
  }

  /**
   * Requires nonblank text.
   *
   * @param value candidate text
   * @param name field name
   * @return trimmed text
   */
  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Normalizes an identity for generated P&amp;ID IDs and attributes.
   *
   * @param value source identity
   * @return lowercase dash-separated token
   */
  private static String normalize(String value) {
    return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
}
