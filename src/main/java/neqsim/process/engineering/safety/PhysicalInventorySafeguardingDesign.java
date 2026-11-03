package neqsim.process.engineering.safety;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

/**
 * Converts a DYN-C029 physical inventory and declared operating limits into governed instrumentation and safeguarding
 * proposals.
 *
 * <p>
 * The study deliberately does not infer pipe routes, contained volumes, fail positions, SIL targets, voting,
 * independence or approved set points. Numerical margins and response allocations come from an explicit
 * {@link DesignPolicy}; plant geometry and isolation evidence come from the versioned
 * {@code plant_layout_physical_inventory.v1} document. Results are proposals for discipline review and carry no safety
 * credit.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class PhysicalInventorySafeguardingDesign {
  /** Supported DYN-C029 physical-inventory schema. */
  public static final String INVENTORY_SCHEMA = "plant_layout_physical_inventory.v1";
  /** Result schema emitted by this study. */
  public static final String RESULT_SCHEMA = "neqsim_physical_inventory_safeguarding_design.v1";

  private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting()
      .serializeSpecialFloatingPointValues().create();

  private PhysicalInventorySafeguardingDesign() {
  }

  /** Evidence maturity recorded for a standards-applicability row. */
  public enum StandardEvidenceStatus {
    /** Edition and applicability are controlled project inputs. */
    PROJECT_ADOPTED,
    /** Only public catalog metadata has been checked. */
    CATALOG_METADATA_ONLY,
    /** Detailed licensed requirements still require review. */
    DETAILED_REQUIREMENTS_REVIEW_REQUIRED
  }

  /** Review state of a generated proposal. */
  public enum ProposalStatus {
    /** Proposal is suitable only for accountable discipline review. */
    REVIEW_REQUIRED,
    /** Proposal is blocked by missing or non-controlled input evidence. */
    BLOCKED
  }

  /** One standards applicability, edition, clause and evidence register row. */
  public static final class StandardApplicability implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String standardId;
    private final String edition;
    private final String applicability;
    private final String clauseReference;
    private final String evidenceReference;
    private final StandardEvidenceStatus evidenceStatus;

    /**
     * Creates one caller-reviewed standards register row.
     *
     * @param standardId stable standard identifier, for example {@code IEC 61511}
     * @param edition adopted or candidate edition
     * @param applicability declared project applicability boundary
     * @param clauseReference inspected clause reference, or {@code NOT_APPLIED}
     * @param evidenceReference controlled evidence or public catalog reference
     * @param evidenceStatus evidence maturity
     */
    public StandardApplicability(String standardId, String edition, String applicability, String clauseReference,
        String evidenceReference, StandardEvidenceStatus evidenceStatus) {
      this.standardId = requireText(standardId, "standardId");
      this.edition = requireText(edition, "edition");
      this.applicability = requireText(applicability, "applicability");
      this.clauseReference = requireText(clauseReference, "clauseReference");
      this.evidenceReference = requireText(evidenceReference, "evidenceReference");
      if (evidenceStatus == null) {
        throw new IllegalArgumentException("evidenceStatus must not be null");
      }
      this.evidenceStatus = evidenceStatus;
    }

    /** @return stable standard identifier */
    public String getStandardId() {
      return standardId;
    }

    /** @return adopted or candidate edition */
    public String getEdition() {
      return edition;
    }

    /** @return declared applicability boundary */
    public String getApplicability() {
      return applicability;
    }

    /** @return inspected clause reference or {@code NOT_APPLIED} */
    public String getClauseReference() {
      return clauseReference;
    }

    /** @return evidence reference */
    public String getEvidenceReference() {
      return evidenceReference;
    }

    /** @return evidence maturity */
    public StandardEvidenceStatus getEvidenceStatus() {
      return evidenceStatus;
    }

    /** @return deterministic serialized register row */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("standardId", standardId);
      value.put("edition", edition);
      value.put("applicability", applicability);
      value.put("clauseReference", clauseReference);
      value.put("evidenceReference", evidenceReference);
      value.put("evidenceStatus", evidenceStatus.name());
      return value;
    }
  }

  /** Explicit numerical policy used to calculate proposed ranges, margins and action budgets. */
  public static final class DesignPolicy implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String policyId;
    private final String revision;
    private final String evidenceReference;
    private final double pressureRangeMarginFraction;
    private final double alarmFractionToDesignLimit;
    private final double tripFractionToDesignLimit;
    private final double sensorFractionOfProcessSafetyTime;
    private final double logicFractionOfProcessSafetyTime;
    private final double finalElementFractionOfProcessSafetyTime;
    private final double reserveFractionOfProcessSafetyTime;
    private final double[] pressureRangeCandidatesBara;

    /**
     * Creates an explicit proposal policy.
     *
     * @param policyId stable policy identity
     * @param revision policy revision
     * @param evidenceReference source of the project-selected numerical values
     * @param pressureRangeMarginFraction fraction added above design pressure for range selection
     * @param alarmFractionToDesignLimit alarm position between maximum operating and design pressure
     * @param tripFractionToDesignLimit trip position between maximum operating and design pressure
     * @param sensorFractionOfProcessSafetyTime transmitter allocation fraction
     * @param logicFractionOfProcessSafetyTime logic-solver allocation fraction
     * @param finalElementFractionOfProcessSafetyTime final-element allocation fraction
     * @param reserveFractionOfProcessSafetyTime unallocated verification reserve fraction
     * @param pressureRangeCandidatesBara reviewed available upper-range candidates in bara
     */
    public DesignPolicy(String policyId, String revision, String evidenceReference, double pressureRangeMarginFraction,
        double alarmFractionToDesignLimit, double tripFractionToDesignLimit, double sensorFractionOfProcessSafetyTime,
        double logicFractionOfProcessSafetyTime, double finalElementFractionOfProcessSafetyTime,
        double reserveFractionOfProcessSafetyTime, double[] pressureRangeCandidatesBara) {
      this.policyId = requireText(policyId, "policyId");
      this.revision = requireText(revision, "revision");
      this.evidenceReference = requireText(evidenceReference, "evidenceReference");
      requireFiniteNonNegative(pressureRangeMarginFraction, "pressureRangeMarginFraction");
      requireOpenFraction(alarmFractionToDesignLimit, "alarmFractionToDesignLimit");
      requireOpenFraction(tripFractionToDesignLimit, "tripFractionToDesignLimit");
      if (alarmFractionToDesignLimit >= tripFractionToDesignLimit) {
        throw new IllegalArgumentException("alarm fraction must be below trip fraction");
      }
      requireFraction(sensorFractionOfProcessSafetyTime, "sensorFractionOfProcessSafetyTime");
      requireFraction(logicFractionOfProcessSafetyTime, "logicFractionOfProcessSafetyTime");
      requireFraction(finalElementFractionOfProcessSafetyTime, "finalElementFractionOfProcessSafetyTime");
      requireFraction(reserveFractionOfProcessSafetyTime, "reserveFractionOfProcessSafetyTime");
      double allocation = sensorFractionOfProcessSafetyTime + logicFractionOfProcessSafetyTime
          + finalElementFractionOfProcessSafetyTime + reserveFractionOfProcessSafetyTime;
      if (Math.abs(allocation - 1.0) > 1.0e-12) {
        throw new IllegalArgumentException("response allocation fractions must sum to one");
      }
      if (pressureRangeCandidatesBara == null || pressureRangeCandidatesBara.length == 0) {
        throw new IllegalArgumentException("pressureRangeCandidatesBara must not be empty");
      }
      this.pressureRangeCandidatesBara = pressureRangeCandidatesBara.clone();
      double previous = 0.0;
      for (double candidate : this.pressureRangeCandidatesBara) {
        requirePositive(candidate, "pressureRangeCandidateBara");
        if (candidate <= previous) {
          throw new IllegalArgumentException("pressure range candidates must be strictly increasing");
        }
        previous = candidate;
      }
      this.pressureRangeMarginFraction = pressureRangeMarginFraction;
      this.alarmFractionToDesignLimit = alarmFractionToDesignLimit;
      this.tripFractionToDesignLimit = tripFractionToDesignLimit;
      this.sensorFractionOfProcessSafetyTime = sensorFractionOfProcessSafetyTime;
      this.logicFractionOfProcessSafetyTime = logicFractionOfProcessSafetyTime;
      this.finalElementFractionOfProcessSafetyTime = finalElementFractionOfProcessSafetyTime;
      this.reserveFractionOfProcessSafetyTime = reserveFractionOfProcessSafetyTime;
    }

    /** @return stable policy identity */
    public String getPolicyId() {
      return policyId;
    }

    /** @return policy revision */
    public String getRevision() {
      return revision;
    }

    /** @return source of the project-selected numerical values */
    public String getEvidenceReference() {
      return evidenceReference;
    }

    /** @return pressure range margin fraction */
    public double getPressureRangeMarginFraction() {
      return pressureRangeMarginFraction;
    }

    /** @return alarm interpolation fraction */
    public double getAlarmFractionToDesignLimit() {
      return alarmFractionToDesignLimit;
    }

    /** @return trip interpolation fraction */
    public double getTripFractionToDesignLimit() {
      return tripFractionToDesignLimit;
    }

    /** @return transmitter allocation fraction */
    public double getSensorFractionOfProcessSafetyTime() {
      return sensorFractionOfProcessSafetyTime;
    }

    /** @return logic-solver allocation fraction */
    public double getLogicFractionOfProcessSafetyTime() {
      return logicFractionOfProcessSafetyTime;
    }

    /** @return final-element allocation fraction */
    public double getFinalElementFractionOfProcessSafetyTime() {
      return finalElementFractionOfProcessSafetyTime;
    }

    /** @return verification-reserve allocation fraction */
    public double getReserveFractionOfProcessSafetyTime() {
      return reserveFractionOfProcessSafetyTime;
    }

    /** @return defensive copy of reviewed range candidates */
    public double[] getPressureRangeCandidatesBara() {
      return pressureRangeCandidatesBara.clone();
    }

    /** @return deterministic serialized policy */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("policyId", policyId);
      value.put("revision", revision);
      value.put("evidenceReference", evidenceReference);
      value.put("pressureRangeMarginFraction", pressureRangeMarginFraction);
      value.put("alarmFractionToDesignLimit", alarmFractionToDesignLimit);
      value.put("tripFractionToDesignLimit", tripFractionToDesignLimit);
      value.put("sensorFractionOfProcessSafetyTime", sensorFractionOfProcessSafetyTime);
      value.put("logicFractionOfProcessSafetyTime", logicFractionOfProcessSafetyTime);
      value.put("finalElementFractionOfProcessSafetyTime", finalElementFractionOfProcessSafetyTime);
      value.put("reserveFractionOfProcessSafetyTime", reserveFractionOfProcessSafetyTime);
      List<Double> ranges = new ArrayList<Double>();
      for (double candidate : pressureRangeCandidatesBara) {
        ranges.add(Double.valueOf(candidate));
      }
      value.put("pressureRangeCandidatesBara", ranges);
      return value;
    }
  }

  /** Operating and design limits for one declared physical isolation section. */
  public static final class SectionOperatingBasis implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String isolationSectionId;
    private final String measuredEquipmentTag;
    private final String operatingCaseId;
    private final String operatingCaseRevision;
    private final String evidenceReference;
    private final double normalPressureBara;
    private final double maximumOperatingPressureBara;
    private final double designPressureBara;
    private final double fluidDensityKgPerM3;
    private final double maximumInflowKgPerS;
    private final double processSafetyTimeSeconds;

    /**
     * Creates a section-specific operating basis.
     *
     * @param isolationSectionId DYN-C029 isolation-section identity
     * @param measuredEquipmentTag equipment carrying the proposed pressure measurement
     * @param operatingCaseId governing process/design case identity
     * @param operatingCaseRevision governing case revision
     * @param evidenceReference source calculation or report
     * @param normalPressureBara normal operating pressure in bara
     * @param maximumOperatingPressureBara maximum declared operating pressure in bara
     * @param designPressureBara declared design pressure in bara
     * @param fluidDensityKgPerM3 declared inventory density in kg/m3
     * @param maximumInflowKgPerS declared governing inflow in kg/s
     * @param processSafetyTimeSeconds externally established process safety time in seconds
     */
    public SectionOperatingBasis(String isolationSectionId, String measuredEquipmentTag, String operatingCaseId,
        String operatingCaseRevision, String evidenceReference, double normalPressureBara,
        double maximumOperatingPressureBara, double designPressureBara, double fluidDensityKgPerM3,
        double maximumInflowKgPerS, double processSafetyTimeSeconds) {
      this.isolationSectionId = requireText(isolationSectionId, "isolationSectionId");
      this.measuredEquipmentTag = requireText(measuredEquipmentTag, "measuredEquipmentTag");
      this.operatingCaseId = requireText(operatingCaseId, "operatingCaseId");
      this.operatingCaseRevision = requireText(operatingCaseRevision, "operatingCaseRevision");
      this.evidenceReference = requireText(evidenceReference, "evidenceReference");
      requirePositive(normalPressureBara, "normalPressureBara");
      requirePositive(maximumOperatingPressureBara, "maximumOperatingPressureBara");
      requirePositive(designPressureBara, "designPressureBara");
      requirePositive(fluidDensityKgPerM3, "fluidDensityKgPerM3");
      requirePositive(maximumInflowKgPerS, "maximumInflowKgPerS");
      requirePositive(processSafetyTimeSeconds, "processSafetyTimeSeconds");
      if (normalPressureBara > maximumOperatingPressureBara || maximumOperatingPressureBara >= designPressureBara) {
        throw new IllegalArgumentException("pressure basis must satisfy normal <= maximum operating < design pressure");
      }
      this.normalPressureBara = normalPressureBara;
      this.maximumOperatingPressureBara = maximumOperatingPressureBara;
      this.designPressureBara = designPressureBara;
      this.fluidDensityKgPerM3 = fluidDensityKgPerM3;
      this.maximumInflowKgPerS = maximumInflowKgPerS;
      this.processSafetyTimeSeconds = processSafetyTimeSeconds;
    }

    /** @return DYN-C029 isolation-section identity */
    public String getIsolationSectionId() {
      return isolationSectionId;
    }

    /** @return equipment carrying the proposed pressure measurement */
    public String getMeasuredEquipmentTag() {
      return measuredEquipmentTag;
    }

    /** @return governing operating/design case identity */
    public String getOperatingCaseId() {
      return operatingCaseId;
    }

    /** @return governing case revision */
    public String getOperatingCaseRevision() {
      return operatingCaseRevision;
    }

    /** @return source calculation or report */
    public String getEvidenceReference() {
      return evidenceReference;
    }

    /** @return normal operating pressure in bara */
    public double getNormalPressureBara() {
      return normalPressureBara;
    }

    /** @return maximum declared operating pressure in bara */
    public double getMaximumOperatingPressureBara() {
      return maximumOperatingPressureBara;
    }

    /** @return declared design pressure in bara */
    public double getDesignPressureBara() {
      return designPressureBara;
    }

    /** @return declared inventory density in kg/m3 */
    public double getFluidDensityKgPerM3() {
      return fluidDensityKgPerM3;
    }

    /** @return declared governing inflow in kg/s */
    public double getMaximumInflowKgPerS() {
      return maximumInflowKgPerS;
    }

    /** @return externally established process safety time in seconds */
    public double getProcessSafetyTimeSeconds() {
      return processSafetyTimeSeconds;
    }

    /** @return deterministic serialized operating basis */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("isolationSectionId", isolationSectionId);
      value.put("measuredEquipmentTag", measuredEquipmentTag);
      value.put("operatingCaseId", operatingCaseId);
      value.put("operatingCaseRevision", operatingCaseRevision);
      value.put("evidenceReference", evidenceReference);
      value.put("normalPressureBara", normalPressureBara);
      value.put("maximumOperatingPressureBara", maximumOperatingPressureBara);
      value.put("designPressureBara", designPressureBara);
      value.put("fluidDensityKgPerM3", fluidDensityKgPerM3);
      value.put("maximumInflowKgPerS", maximumInflowKgPerS);
      value.put("processSafetyTimeSeconds", processSafetyTimeSeconds);
      return value;
    }
  }

  /** Complete caller-controlled study definition. */
  public static final class StudyInput implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String projectId;
    private final String revision;
    private final DesignPolicy designPolicy;
    private final Map<String, SectionOperatingBasis> operatingBasisBySection;
    private final List<StandardApplicability> standardsApplicability;

    /**
     * Creates a complete study input.
     *
     * @param projectId controlled engineering-project identity
     * @param revision controlled project revision
     * @param designPolicy explicit numerical proposal policy
     * @param operatingBases section operating/design bases
     * @param standardsApplicability standards applicability/evidence register
     */
    public StudyInput(String projectId, String revision, DesignPolicy designPolicy,
        List<SectionOperatingBasis> operatingBases, List<StandardApplicability> standardsApplicability) {
      this.projectId = requireText(projectId, "projectId");
      this.revision = requireText(revision, "revision");
      if (designPolicy == null) {
        throw new IllegalArgumentException("designPolicy must not be null");
      }
      if (operatingBases == null || operatingBases.isEmpty()) {
        throw new IllegalArgumentException("operatingBases must not be empty");
      }
      if (standardsApplicability == null || standardsApplicability.isEmpty()) {
        throw new IllegalArgumentException("standardsApplicability must not be empty");
      }
      this.designPolicy = designPolicy;
      Map<String, SectionOperatingBasis> bases = new LinkedHashMap<String, SectionOperatingBasis>();
      for (SectionOperatingBasis basis : operatingBases) {
        if (basis == null) {
          throw new IllegalArgumentException("operatingBases must not contain null");
        }
        if (bases.put(basis.getIsolationSectionId(), basis) != null) {
          throw new IllegalArgumentException("duplicate operating basis for " + basis.getIsolationSectionId());
        }
      }
      this.operatingBasisBySection = Collections.unmodifiableMap(bases);
      List<StandardApplicability> standards = new ArrayList<StandardApplicability>();
      Set<String> standardIds = new LinkedHashSet<String>();
      for (StandardApplicability standard : standardsApplicability) {
        if (standard == null || !standardIds.add(standard.getStandardId())) {
          throw new IllegalArgumentException("standardsApplicability contains null or duplicate standard");
        }
        standards.add(standard);
      }
      this.standardsApplicability = Collections.unmodifiableList(standards);
    }

    /** @return controlled engineering-project identity */
    public String getProjectId() {
      return projectId;
    }

    /** @return controlled project revision */
    public String getRevision() {
      return revision;
    }

    /** @return explicit numerical proposal policy */
    public DesignPolicy getDesignPolicy() {
      return designPolicy;
    }

    /** @return immutable section operating/design bases */
    public Map<String, SectionOperatingBasis> getOperatingBasisBySection() {
      return operatingBasisBySection;
    }

    /** @return immutable standards applicability/evidence register */
    public List<StandardApplicability> getStandardsApplicability() {
      return standardsApplicability;
    }

    /** @return deterministic serialized input */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("projectId", projectId);
      value.put("revision", revision);
      value.put("designPolicy", designPolicy.toMap());
      List<Map<String, Object>> bases = new ArrayList<Map<String, Object>>();
      for (SectionOperatingBasis basis : operatingBasisBySection.values()) {
        bases.add(basis.toMap());
      }
      value.put("operatingBases", bases);
      List<Map<String, Object>> standards = new ArrayList<Map<String, Object>>();
      for (StandardApplicability standard : standardsApplicability) {
        standards.add(standard.toMap());
      }
      value.put("standardsApplicability", standards);
      return value;
    }
  }

  /** One fail-closed study blocker. */
  public static final class Blocker implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String code;
    private final String subject;
    private final String message;

    Blocker(String code, String subject, String message) {
      this.code = requireText(code, "code");
      this.subject = requireText(subject, "subject");
      this.message = requireText(message, "message");
    }

    /** @return stable blocker code */
    public String getCode() {
      return code;
    }

    /** @return affected subject */
    public String getSubject() {
      return subject;
    }

    /** @return review action */
    public String getMessage() {
      return message;
    }

    /** @return deterministic serialized blocker */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("code", code);
      value.put("subject", subject);
      value.put("message", message);
      return value;
    }
  }

  /** Candidate action for one supplied physical isolation device. */
  public static final class IsolationActionProposal implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String deviceId;
    private final String deviceTag;
    private final String deviceType;
    private final String suppliedFailState;
    private final String candidateAction;
    private final String alternativeAction;
    private final String rationale;

    IsolationActionProposal(InventoryDevice device) {
      deviceId = device.id;
      deviceTag = device.tag;
      deviceType = device.deviceType;
      suppliedFailState = device.failState;
      candidateAction = device.failState;
      if ("CLOSED".equals(device.failState)) {
        alternativeAction = "OPEN";
        rationale = "Controlled fail-closed input limits continued section inflow; review upstream blocked-in and relief consequences against the fail-open alternative.";
      } else if ("OPEN".equals(device.failState)) {
        alternativeAction = "CLOSED";
        rationale = "Controlled fail-open input preserves an open path; review continued release or inflow consequences against the fail-closed alternative.";
      } else {
        alternativeAction = "PROJECT_HAZARD_REVIEW_REQUIRED";
        rationale = "The supplied device state is retained without inferring a fail action.";
      }
    }

    /** @return physical-inventory device identity */
    public String getDeviceId() {
      return deviceId;
    }

    /** @return device tag */
    public String getDeviceTag() {
      return deviceTag;
    }

    /** @return physical device type */
    public String getDeviceType() {
      return deviceType;
    }

    /** @return supplied controlled fail state */
    public String getSuppliedFailState() {
      return suppliedFailState;
    }

    /** @return candidate action copied from the supplied fail state */
    public String getCandidateAction() {
      return candidateAction;
    }

    /** @return retained alternative for accountable review */
    public String getAlternativeAction() {
      return alternativeAction;
    }

    /** @return explicit candidate-versus-alternative rationale */
    public String getRationale() {
      return rationale;
    }

    /** @return deterministic serialized action proposal */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("deviceId", deviceId);
      value.put("deviceTag", deviceTag);
      value.put("deviceType", deviceType);
      value.put("suppliedFailState", suppliedFailState);
      value.put("candidateAction", candidateAction);
      value.put("alternativeAction", alternativeAction);
      value.put("rationale", rationale);
      value.put("approvalStatus", ProposalStatus.REVIEW_REQUIRED.name());
      return value;
    }
  }

  /** Quantified proposal for one physical isolation section. */
  public static final class SectionProposal implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String isolationSectionId;
    private final String equipmentTag;
    private final String requirementId;
    private final String scenarioId;
    private final String resultId;
    private final String pressureTransmitterTag;
    private final String pressureControllerTag;
    private final String highAlarmTag;
    private final String highHighTripTag;
    private final double isolatedVolumeM3;
    private final double inventoryMassKg;
    private final double inventoryTurnoverSeconds;
    private final double normalPressureBara;
    private final double maximumOperatingPressureBara;
    private final double designPressureBara;
    private final double pressureRangeUpperBara;
    private final double highAlarmBara;
    private final double highHighTripBara;
    private final double tripToDesignMarginBar;
    private final double processSafetyTimeSeconds;
    private final double sensorResponseRequirementSeconds;
    private final double logicResponseRequirementSeconds;
    private final double finalElementResponseRequirementSeconds;
    private final double verificationReserveSeconds;
    private final double responseBudgetResidualSeconds;
    private final List<IsolationActionProposal> isolationActions;
    private final List<String> routeIds;
    private final List<String> equipmentVolumeIds;
    private final String operatingCaseId;
    private final String operatingCaseRevision;
    private final String operatingEvidenceReference;

    SectionProposal(InventorySection section, SectionOperatingBasis basis, DesignPolicy policy,
        Map<String, InventoryDevice> devices) {
      isolationSectionId = section.id;
      equipmentTag = basis.getMeasuredEquipmentTag();
      String stable = normalize(section.id).toUpperCase(Locale.ROOT);
      requirementId = "REQ-" + stable + "-OVERPRESSURE";
      scenarioId = "SCN-" + stable + "-BLOCKED-OUTLET";
      resultId = "CALC-" + stable + "-PROPOSAL-BASIS";
      pressureTransmitterTag = "PT-" + stable;
      pressureControllerTag = "PIC-" + stable;
      highAlarmTag = "PAH-" + stable;
      highHighTripTag = "PSHH-" + stable;
      isolatedVolumeM3 = section.totalIsolatedVolumeM3;
      inventoryMassKg = isolatedVolumeM3 * basis.getFluidDensityKgPerM3();
      inventoryTurnoverSeconds = inventoryMassKg / basis.getMaximumInflowKgPerS();
      normalPressureBara = basis.getNormalPressureBara();
      maximumOperatingPressureBara = basis.getMaximumOperatingPressureBara();
      designPressureBara = basis.getDesignPressureBara();
      pressureRangeUpperBara = selectPressureRange(designPressureBara * (1.0 + policy.getPressureRangeMarginFraction()),
          policy.getPressureRangeCandidatesBara());
      double pressureWindow = designPressureBara - maximumOperatingPressureBara;
      highAlarmBara = maximumOperatingPressureBara + pressureWindow * policy.getAlarmFractionToDesignLimit();
      highHighTripBara = maximumOperatingPressureBara + pressureWindow * policy.getTripFractionToDesignLimit();
      tripToDesignMarginBar = designPressureBara - highHighTripBara;
      processSafetyTimeSeconds = basis.getProcessSafetyTimeSeconds();
      sensorResponseRequirementSeconds = processSafetyTimeSeconds * policy.getSensorFractionOfProcessSafetyTime();
      logicResponseRequirementSeconds = processSafetyTimeSeconds * policy.getLogicFractionOfProcessSafetyTime();
      finalElementResponseRequirementSeconds = processSafetyTimeSeconds
          * policy.getFinalElementFractionOfProcessSafetyTime();
      verificationReserveSeconds = processSafetyTimeSeconds * policy.getReserveFractionOfProcessSafetyTime();
      responseBudgetResidualSeconds = Math.abs(processSafetyTimeSeconds - sensorResponseRequirementSeconds
          - logicResponseRequirementSeconds - finalElementResponseRequirementSeconds - verificationReserveSeconds);
      List<IsolationActionProposal> actions = new ArrayList<IsolationActionProposal>();
      for (String deviceId : section.boundaryDeviceIds) {
        actions.add(new IsolationActionProposal(devices.get(deviceId)));
      }
      isolationActions = Collections.unmodifiableList(actions);
      routeIds = immutableCopy(section.routeIds);
      equipmentVolumeIds = immutableCopy(section.equipmentVolumeIds);
      operatingCaseId = basis.getOperatingCaseId();
      operatingCaseRevision = basis.getOperatingCaseRevision();
      operatingEvidenceReference = basis.getEvidenceReference();
    }

    /** @return isolation section identity */
    public String getIsolationSectionId() {
      return isolationSectionId;
    }

    /** @return equipment carrying the proposed measurement */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /** @return requirement identity */
    public String getRequirementId() {
      return requirementId;
    }

    /** @return blocked-outlet scenario identity */
    public String getScenarioId() {
      return scenarioId;
    }

    /** @return proposal calculation identity */
    public String getResultId() {
      return resultId;
    }

    /** @return proposed pressure-transmitter tag */
    public String getPressureTransmitterTag() {
      return pressureTransmitterTag;
    }

    /** @return proposed pressure-controller tag */
    public String getPressureControllerTag() {
      return pressureControllerTag;
    }

    /** @return proposed high-alarm tag */
    public String getHighAlarmTag() {
      return highAlarmTag;
    }

    /** @return proposed high-high-trip tag */
    public String getHighHighTripTag() {
      return highHighTripTag;
    }

    /** @return supplied isolated volume in m3 */
    public double getIsolatedVolumeM3() {
      return isolatedVolumeM3;
    }

    /** @return calculated inventory mass in kg */
    public double getInventoryMassKg() {
      return inventoryMassKg;
    }

    /** @return calculated inventory turnover time in seconds */
    public double getInventoryTurnoverSeconds() {
      return inventoryTurnoverSeconds;
    }

    /** @return normal operating pressure in bara */
    public double getNormalPressureBara() {
      return normalPressureBara;
    }

    /** @return maximum operating pressure in bara */
    public double getMaximumOperatingPressureBara() {
      return maximumOperatingPressureBara;
    }

    /** @return design pressure in bara */
    public double getDesignPressureBara() {
      return designPressureBara;
    }

    /** @return selected pressure range upper value in bara */
    public double getPressureRangeUpperBara() {
      return pressureRangeUpperBara;
    }

    /** @return calculated proposed high-alarm value in bara */
    public double getHighAlarmBara() {
      return highAlarmBara;
    }

    /** @return calculated proposed high-high trip value in bara */
    public double getHighHighTripBara() {
      return highHighTripBara;
    }

    /** @return positive trip-to-design margin in bar */
    public double getTripToDesignMarginBar() {
      return tripToDesignMarginBar;
    }

    /** @return externally established process safety time in seconds */
    public double getProcessSafetyTimeSeconds() {
      return processSafetyTimeSeconds;
    }

    /** @return proposed sensor response requirement in seconds */
    public double getSensorResponseRequirementSeconds() {
      return sensorResponseRequirementSeconds;
    }

    /** @return proposed logic response requirement in seconds */
    public double getLogicResponseRequirementSeconds() {
      return logicResponseRequirementSeconds;
    }

    /** @return proposed final-element response requirement in seconds */
    public double getFinalElementResponseRequirementSeconds() {
      return finalElementResponseRequirementSeconds;
    }

    /** @return retained verification reserve in seconds */
    public double getVerificationReserveSeconds() {
      return verificationReserveSeconds;
    }

    /** @return floating-point response-budget closure residual in seconds */
    public double getResponseBudgetResidualSeconds() {
      return responseBudgetResidualSeconds;
    }

    /** @return immutable physical isolation-action proposals */
    public List<IsolationActionProposal> getIsolationActions() {
      return isolationActions;
    }

    /** @return immutable route identities */
    public List<String> getRouteIds() {
      return routeIds;
    }

    /** @return immutable equipment-volume identities */
    public List<String> getEquipmentVolumeIds() {
      return equipmentVolumeIds;
    }

    /** @return governing operating/design case identity */
    public String getOperatingCaseId() {
      return operatingCaseId;
    }

    /** @return governing operating/design case revision */
    public String getOperatingCaseRevision() {
      return operatingCaseRevision;
    }

    /** @return operating/design case evidence reference */
    public String getOperatingEvidenceReference() {
      return operatingEvidenceReference;
    }

    /** @return deterministic serialized proposal */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("isolationSectionId", isolationSectionId);
      value.put("equipmentTag", equipmentTag);
      value.put("requirementId", requirementId);
      value.put("scenarioId", scenarioId);
      value.put("scenarioExecutionStatus", "NOT_EXECUTED");
      value.put("resultId", resultId);
      value.put("proposalStatus", ProposalStatus.REVIEW_REQUIRED.name());
      value.put("pressureTransmitterTag", pressureTransmitterTag);
      value.put("pressureControllerTag", pressureControllerTag);
      value.put("highAlarmTag", highAlarmTag);
      value.put("highHighTripTag", highHighTripTag);
      value.put("isolatedVolumeM3", isolatedVolumeM3);
      value.put("inventoryMassKg", inventoryMassKg);
      value.put("inventoryTurnoverSeconds", inventoryTurnoverSeconds);
      value.put("normalPressureBara", normalPressureBara);
      value.put("maximumOperatingPressureBara", maximumOperatingPressureBara);
      value.put("designPressureBara", designPressureBara);
      value.put("pressureRangeUpperBara", pressureRangeUpperBara);
      value.put("highAlarmBara", highAlarmBara);
      value.put("highHighTripBara", highHighTripBara);
      value.put("tripToDesignMarginBar", tripToDesignMarginBar);
      value.put("processSafetyTimeSeconds", processSafetyTimeSeconds);
      value.put("sensorResponseRequirementSeconds", sensorResponseRequirementSeconds);
      value.put("logicResponseRequirementSeconds", logicResponseRequirementSeconds);
      value.put("finalElementResponseRequirementSeconds", finalElementResponseRequirementSeconds);
      value.put("verificationReserveSeconds", verificationReserveSeconds);
      value.put("responseBudgetResidualSeconds", responseBudgetResidualSeconds);
      value.put("controlMethodology",
          "Pressure feedback control with disturbance, interaction, tuning and constraint verification required");
      value.put("silTarget", "RISK_ASSESSMENT_REQUIRED");
      value.put("voting", "SRS_INPUT_REQUIRED");
      value.put("independence", "IEC_61511_LIFECYCLE_REVIEW_REQUIRED");
      value.put("routeIds", routeIds);
      value.put("equipmentVolumeIds", equipmentVolumeIds);
      List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
      for (IsolationActionProposal action : isolationActions) {
        actions.add(action.toMap());
      }
      value.put("isolationActions", actions);
      value.put("operatingCaseId", operatingCaseId);
      value.put("operatingCaseRevision", operatingCaseRevision);
      value.put("operatingEvidenceReference", operatingEvidenceReference);
      value.put("rationale",
          "Range, alarm/trip margins and response allocations are calculated only from the declared operating/design basis and explicit proposal policy");
      return value;
    }
  }

  /** Complete deterministic study result. */
  public static final class StudyResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String projectId;
    private final String revision;
    private final String inventoryScopeId;
    private final String inventoryScopeRevision;
    private final String inputFingerprint;
    private final DesignPolicy designPolicy;
    private final List<StandardApplicability> standardsApplicability;
    private final List<SectionProposal> proposals;
    private final List<Blocker> blockers;

    StudyResult(StudyInput input, InventoryDocument inventory, String inputFingerprint, List<SectionProposal> proposals,
        List<Blocker> blockers) {
      projectId = input.getProjectId();
      revision = input.getRevision();
      inventoryScopeId = inventory.scope == null ? "UNDECLARED" : inventory.scope.id;
      inventoryScopeRevision = inventory.scope == null ? "UNDECLARED" : inventory.scope.revision;
      this.inputFingerprint = inputFingerprint;
      designPolicy = input.getDesignPolicy();
      standardsApplicability = Collections
          .unmodifiableList(new ArrayList<StandardApplicability>(input.getStandardsApplicability()));
      this.proposals = Collections.unmodifiableList(new ArrayList<SectionProposal>(proposals));
      this.blockers = Collections.unmodifiableList(new ArrayList<Blocker>(blockers));
    }

    /** @return controlled project identity */
    public String getProjectId() {
      return projectId;
    }

    /** @return controlled project revision */
    public String getRevision() {
      return revision;
    }

    /** @return physical inventory scope identity */
    public String getInventoryScopeId() {
      return inventoryScopeId;
    }

    /** @return physical inventory scope revision */
    public String getInventoryScopeRevision() {
      return inventoryScopeRevision;
    }

    /** @return SHA-256 fingerprint of the inventory and controlled study input */
    public String getInputFingerprint() {
      return inputFingerprint;
    }

    /** @return explicit proposal policy */
    public DesignPolicy getDesignPolicy() {
      return designPolicy;
    }

    /** @return immutable standards applicability/evidence register */
    public List<StandardApplicability> getStandardsApplicability() {
      return standardsApplicability;
    }

    /** @return immutable quantified section proposals */
    public List<SectionProposal> getProposals() {
      return proposals;
    }

    /** @return immutable blockers */
    public List<Blocker> getBlockers() {
      return blockers;
    }

    /** @return true when every declared section has a proposal and no blocker */
    public boolean isReadyForDisciplineReview() {
      return blockers.isEmpty() && !proposals.isEmpty();
    }

    /** @return deterministic machine-readable result */
    public Map<String, Object> toMap() {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("schemaVersion", RESULT_SCHEMA);
      value.put("projectId", projectId);
      value.put("revision", revision);
      value.put("inventoryScopeId", inventoryScopeId);
      value.put("inventoryScopeRevision", inventoryScopeRevision);
      value.put("inputFingerprintSha256", inputFingerprint);
      value.put("studyStatus", isReadyForDisciplineReview() ? "READY_FOR_DISCIPLINE_REVIEW" : "BLOCKED");
      value.put("designPolicy", designPolicy.toMap());
      List<Map<String, Object>> standards = new ArrayList<Map<String, Object>>();
      for (StandardApplicability standard : standardsApplicability) {
        standards.add(standard.toMap());
      }
      value.put("standardsApplicability", standards);
      List<Map<String, Object>> proposalValues = new ArrayList<Map<String, Object>>();
      for (SectionProposal proposal : proposals) {
        proposalValues.add(proposal.toMap());
      }
      value.put("proposals", proposalValues);
      List<Map<String, Object>> blockerValues = new ArrayList<Map<String, Object>>();
      for (Blocker blocker : blockers) {
        blockerValues.add(blocker.toMap());
      }
      value.put("blockers", blockerValues);
      value.put("safetyCredit", Boolean.FALSE);
      value.put("fitnessForConstruction", Boolean.FALSE);
      value.put("accountableReviewBoundary",
          "HAZOP completeness, scenario credibility, SIL/IPL/voting/independence, detector coverage, vendor data, construction and operation approval remain external");
      return value;
    }

    /** @return deterministic pretty-printed JSON result */
    public String toJson() {
      return GSON.toJson(toMap());
    }

    /**
     * Writes the deterministic result JSON.
     *
     * @param path destination file
     * @throws IOException if the destination cannot be written
     */
    public void write(Path path) throws IOException {
      if (path == null) {
        throw new IllegalArgumentException("path must not be null");
      }
      Files.write(path, toJson().getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Runs the proposal study from a DYN-C029 physical-inventory JSON document.
   *
   * @param physicalInventoryJson path to {@code plant_layout_physical_inventory.v1}
   * @param input controlled operating, policy and standards input
   * @return deterministic fail-closed study result
   * @throws IOException if the inventory cannot be read
   */
  public static StudyResult run(Path physicalInventoryJson, StudyInput input) throws IOException {
    if (physicalInventoryJson == null || input == null) {
      throw new IllegalArgumentException("physicalInventoryJson and input must not be null");
    }
    Reader reader = Files.newBufferedReader(physicalInventoryJson, StandardCharsets.UTF_8);
    try {
      return run(reader, input);
    } finally {
      reader.close();
    }
  }

  /**
   * Runs the proposal study from a character stream.
   *
   * @param physicalInventoryJson DYN-C029 inventory JSON stream
   * @param input controlled operating, policy and standards input
   * @return deterministic fail-closed study result
   */
  public static StudyResult run(Reader physicalInventoryJson, StudyInput input) {
    if (physicalInventoryJson == null || input == null) {
      throw new IllegalArgumentException("physicalInventoryJson and input must not be null");
    }
    InventoryDocument inventory;
    String rawInventory = readAll(physicalInventoryJson);
    try {
      inventory = GSON.fromJson(rawInventory, InventoryDocument.class);
    } catch (JsonParseException ex) {
      throw new IllegalArgumentException("physical inventory JSON is invalid", ex);
    }
    if (inventory == null) {
      throw new IllegalArgumentException("physical inventory JSON is empty");
    }
    List<Blocker> blockers = validateInventory(inventory);
    List<SectionProposal> proposals = new ArrayList<SectionProposal>();
    Map<String, InventoryDevice> devices = devicesById(inventory.isolationDevices);
    Map<String, InventoryEquipmentVolume> equipmentVolumes = equipmentVolumesById(inventory.equipmentVolumes);
    if (blockers.isEmpty()) {
      Set<String> sectionIds = new LinkedHashSet<String>();
      for (InventorySection section : orderedSections(inventory.isolationSections)) {
        sectionIds.add(section.id);
        SectionOperatingBasis basis = input.getOperatingBasisBySection().get(section.id);
        if (basis == null) {
          blockers.add(new Blocker("SAFEGUARD-OPERATING-BASIS-MISSING", section.id,
              "No governing operating/design basis was supplied for the physical isolation section."));
          continue;
        }
        if (!sectionContainsEquipment(section, basis.getMeasuredEquipmentTag(), equipmentVolumes)) {
          blockers.add(new Blocker("SAFEGUARD-MEASUREMENT-LOCATION-OUTSIDE-SECTION", section.id,
              "Measured equipment " + basis.getMeasuredEquipmentTag()
                  + " is not part of the supplied isolation-section equipment inventory."));
          continue;
        }
        try {
          proposals.add(new SectionProposal(section, basis, input.getDesignPolicy(), devices));
        } catch (IllegalArgumentException ex) {
          blockers.add(new Blocker("SAFEGUARD-PROPOSAL-CALCULATION-BLOCKED", section.id, ex.getMessage()));
        }
      }
      for (String basisSection : input.getOperatingBasisBySection().keySet()) {
        if (!sectionIds.contains(basisSection)) {
          blockers.add(new Blocker("SAFEGUARD-INVENTORY-SECTION-MISSING", basisSection,
              "Operating basis references a section absent from the physical inventory."));
        }
      }
    }
    if (!blockers.isEmpty()) {
      proposals.clear();
    }
    String fingerprint = sha256(rawInventory.trim() + "\n" + GSON.toJson(input.toMap()));
    return new StudyResult(input, inventory, fingerprint, proposals, blockers);
  }

  /**
   * Reads the complete physical inventory so its fingerprint covers fields outside the Java calculation projection,
   * including source geometry and revision metadata.
   *
   * @param reader physical-inventory reader
   * @return complete input text
   */
  private static String readAll(Reader reader) {
    StringBuilder value = new StringBuilder();
    char[] buffer = new char[4096];
    try {
      int count;
      while ((count = reader.read(buffer)) >= 0) {
        value.append(buffer, 0, count);
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("physical inventory JSON could not be read", ex);
    }
    return value.toString();
  }

  /**
   * Validates the DYN-C029 readiness and controlled-evidence contract.
   *
   * @param inventory parsed physical inventory
   * @return ordered fail-closed blocker list
   */
  private static List<Blocker> validateInventory(InventoryDocument inventory) {
    List<Blocker> blockers = new ArrayList<Blocker>();
    if (!INVENTORY_SCHEMA.equals(inventory.schemaVersion)) {
      blockers.add(new Blocker("SAFEGUARD-INVENTORY-SCHEMA-UNSUPPORTED", "physicalInventory",
          "Expected " + INVENTORY_SCHEMA + " but received " + String.valueOf(inventory.schemaVersion) + "."));
      return blockers;
    }
    if (!"COMPLETE".equals(inventory.status) || !inventory.readyForDynamicSafeguardingDesign) {
      blockers.add(new Blocker("SAFEGUARD-INVENTORY-NOT-READY", "physicalInventory",
          "DYN-C029 inventory must be COMPLETE and ready_for_dynamic_safeguarding_design=true."));
    }
    if (inventory.scope == null || blank(inventory.scope.id) || blank(inventory.scope.revision)
        || blank(inventory.scope.sourceReference)) {
      blockers.add(new Blocker("SAFEGUARD-INVENTORY-SCOPE-MISSING", "physicalInventory",
          "Physical inventory scope identity, revision and source reference are required."));
    }
    if (inventory.blockers != null && !inventory.blockers.isEmpty()) {
      blockers.add(new Blocker("SAFEGUARD-INVENTORY-UPSTREAM-BLOCKERS", "physicalInventory",
          "DYN-C029 inventory contains unresolved completeness blockers."));
    }
    if (inventory.isolationSections == null || inventory.isolationSections.isEmpty()) {
      blockers.add(new Blocker("SAFEGUARD-ISOLATION-SECTIONS-MISSING", "physicalInventory",
          "At least one physical isolation section is required."));
      return blockers;
    }
    Map<String, InventoryRoute> routes = routesById(inventory.routes);
    Map<String, InventoryEquipmentVolume> equipmentVolumes = equipmentVolumesById(inventory.equipmentVolumes);
    Map<String, InventoryDevice> devices = devicesById(inventory.isolationDevices);
    Set<String> sectionIds = new LinkedHashSet<String>();
    for (InventorySection section : inventory.isolationSections) {
      if (section == null || blank(section.id) || !sectionIds.add(section.id)) {
        blockers.add(new Blocker("SAFEGUARD-ISOLATION-SECTION-ID-INVALID", "physicalInventory",
            "Isolation-section identities must be nonblank and unique."));
        continue;
      }
      if (!finitePositive(section.totalIsolatedVolumeM3)) {
        blockers.add(new Blocker("SAFEGUARD-ISOLATED-VOLUME-INVALID", section.id,
            "A positive total_isolated_volume_m3 is required."));
      }
      if (!controlled(section.source)) {
        blockers.add(new Blocker("SAFEGUARD-ISOLATION-SECTION-NOT-CONTROLLED", section.id,
            "Isolation-section provenance must be CONTROLLED_PROJECT_INPUT."));
      }
      if (section.routeIds == null || section.routeIds.isEmpty()) {
        blockers.add(new Blocker("SAFEGUARD-PHYSICAL-ROUTES-MISSING", section.id,
            "Isolation section must reference at least one supplied physical route."));
      } else {
        for (String routeId : section.routeIds) {
          InventoryRoute route = routes.get(routeId);
          if (route == null) {
            blockers.add(new Blocker("SAFEGUARD-PHYSICAL-ROUTE-MISSING", section.id,
                "Physical route " + routeId + " is absent from the inventory."));
          } else if (!finitePositive(route.lengthM) || !finitePositive(route.pipeVolumeM3)
              || !controlled(route.source)) {
            blockers.add(new Blocker("SAFEGUARD-PHYSICAL-ROUTE-NOT-CONTROLLED", routeId,
                "Route length, pipe volume and CONTROLLED_PROJECT_INPUT provenance are required."));
          }
        }
      }
      if (section.equipmentVolumeIds == null || section.equipmentVolumeIds.isEmpty()) {
        blockers.add(new Blocker("SAFEGUARD-EQUIPMENT-VOLUMES-MISSING", section.id,
            "Isolation section must reference at least one supplied equipment volume."));
      } else {
        for (String volumeId : section.equipmentVolumeIds) {
          InventoryEquipmentVolume volume = equipmentVolumes.get(volumeId);
          if (volume == null) {
            blockers.add(new Blocker("SAFEGUARD-EQUIPMENT-VOLUME-MISSING", section.id,
                "Equipment volume " + volumeId + " is absent from the inventory."));
          } else if (!finitePositive(volume.volumeM3) || blank(volume.equipmentTag) || !controlled(volume.source)) {
            blockers.add(new Blocker("SAFEGUARD-EQUIPMENT-VOLUME-NOT-CONTROLLED", volumeId,
                "Equipment tag, positive volume and CONTROLLED_PROJECT_INPUT provenance are required."));
          }
        }
      }
      if (section.boundaryDeviceIds == null || section.boundaryDeviceIds.size() < 2) {
        blockers.add(new Blocker("SAFEGUARD-BOUNDARY-DEVICES-INCOMPLETE", section.id,
            "At least two physical boundary devices are required."));
      } else {
        for (String deviceId : section.boundaryDeviceIds) {
          InventoryDevice device = devices.get(deviceId);
          if (device == null) {
            blockers.add(new Blocker("SAFEGUARD-BOUNDARY-DEVICE-MISSING", section.id,
                "Boundary device " + deviceId + " is absent from the physical inventory."));
          } else {
            if (!controlled(device.source)) {
              blockers.add(new Blocker("SAFEGUARD-BOUNDARY-DEVICE-NOT-CONTROLLED", deviceId,
                  "Boundary-device provenance must be CONTROLLED_PROJECT_INPUT."));
            }
            if (blank(device.failState) || "UNKNOWN".equals(device.failState)) {
              blockers.add(new Blocker("SAFEGUARD-FAIL-STATE-MISSING", deviceId,
                  "Boundary-device fail state must be supplied; it is never inferred."));
            }
          }
        }
      }
    }
    return blockers;
  }

  /**
   * Indexes supplied isolation devices by stable identity.
   *
   * @param devices parsed isolation-device list
   * @return ordered identity index
   */
  private static Map<String, InventoryDevice> devicesById(List<InventoryDevice> devices) {
    Map<String, InventoryDevice> result = new LinkedHashMap<String, InventoryDevice>();
    if (devices != null) {
      for (InventoryDevice device : devices) {
        if (device != null && !blank(device.id)) {
          result.put(device.id, device);
        }
      }
    }
    return result;
  }

  /**
   * Indexes supplied physical routes by stable identity.
   *
   * @param routes parsed route list
   * @return ordered identity index
   */
  private static Map<String, InventoryRoute> routesById(List<InventoryRoute> routes) {
    Map<String, InventoryRoute> result = new LinkedHashMap<String, InventoryRoute>();
    if (routes != null) {
      for (InventoryRoute route : routes) {
        if (route != null && !blank(route.id)) {
          result.put(route.id, route);
        }
      }
    }
    return result;
  }

  /**
   * Indexes supplied equipment volumes by stable identity.
   *
   * @param volumes parsed equipment-volume list
   * @return ordered identity index
   */
  private static Map<String, InventoryEquipmentVolume> equipmentVolumesById(List<InventoryEquipmentVolume> volumes) {
    Map<String, InventoryEquipmentVolume> result = new LinkedHashMap<String, InventoryEquipmentVolume>();
    if (volumes != null) {
      for (InventoryEquipmentVolume volume : volumes) {
        if (volume != null && !blank(volume.id)) {
          result.put(volume.id, volume);
        }
      }
    }
    return result;
  }

  /**
   * Tests whether the proposed measurement location belongs to the supplied physical section.
   *
   * @param section physical isolation section
   * @param equipmentTag proposed measured equipment tag
   * @param equipmentVolumes physical equipment-volume index
   * @return true when the section references the measured equipment
   */
  private static boolean sectionContainsEquipment(InventorySection section, String equipmentTag,
      Map<String, InventoryEquipmentVolume> equipmentVolumes) {
    if (section.equipmentVolumeIds == null) {
      return false;
    }
    for (String volumeId : section.equipmentVolumeIds) {
      InventoryEquipmentVolume volume = equipmentVolumes.get(volumeId);
      if (volume != null && equipmentTag.equals(volume.equipmentTag)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns physical sections in deterministic identity order.
   *
   * @param sections parsed section list
   * @return ordered copy
   */
  private static List<InventorySection> orderedSections(List<InventorySection> sections) {
    List<InventorySection> result = new ArrayList<InventorySection>(sections);
    Collections.sort(result, new Comparator<InventorySection>() {
      @Override
      public int compare(InventorySection left, InventorySection right) {
        return left.id.compareTo(right.id);
      }
    });
    return result;
  }

  /**
   * Selects the smallest declared pressure range covering the required upper value.
   *
   * @param requiredUpperBara required upper range value in bara
   * @param candidates reviewed increasing range candidates in bara
   * @return selected upper range value in bara
   */
  private static double selectPressureRange(double requiredUpperBara, double[] candidates) {
    for (double candidate : candidates) {
      if (candidate >= requiredUpperBara) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("No declared pressure range candidate covers " + requiredUpperBara + " bara.");
  }

  /**
   * Calculates a deterministic SHA-256 hexadecimal digest.
   *
   * @param value normalized input text
   * @return lowercase SHA-256 digest
   */
  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : bytes) {
        result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  /**
   * Returns an immutable defensive string-list copy.
   *
   * @param source source list, possibly null
   * @return immutable copy
   */
  private static List<String> immutableCopy(List<String> source) {
    return source == null ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<String>(source));
  }

  /**
   * Normalizes a stable identity for generated tags.
   *
   * @param value source identity
   * @return alphanumeric dash-separated tag token
   */
  private static String normalize(String value) {
    return value.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  /**
   * Tests whether a physical-inventory source is controlled project input.
   *
   * @param source parsed evidence source
   * @return true only for controlled project input
   */
  private static boolean controlled(InventorySource source) {
    return source != null && "CONTROLLED_PROJECT_INPUT".equals(source.evidenceStatus) && !blank(source.reference)
        && !blank(source.revision);
  }

  /**
   * Tests whether text is absent.
   *
   * @param value candidate text
   * @return true for null or blank text
   */
  private static boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Tests whether a value is finite and positive.
   *
   * @param value candidate numeric value
   * @return true for finite values above zero
   */
  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  /**
   * Requires nonblank text.
   *
   * @param value candidate value
   * @param name field name
   * @return trimmed text
   */
  private static String requireText(String value, String name) {
    if (blank(value)) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Requires a finite positive number.
   *
   * @param value candidate value
   * @param name field name
   */
  private static void requirePositive(double value, String name) {
    if (!finitePositive(value)) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Requires a finite non-negative number.
   *
   * @param value candidate value
   * @param name field name
   */
  private static void requireFiniteNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /**
   * Requires a closed unit-interval fraction.
   *
   * @param value candidate fraction
   * @param name field name
   */
  private static void requireFraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be in [0, 1]");
    }
  }

  /**
   * Requires an open unit-interval fraction.
   *
   * @param value candidate fraction
   * @param name field name
   */
  private static void requireOpenFraction(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0 || value >= 1.0) {
      throw new IllegalArgumentException(name + " must be in (0, 1)");
    }
  }

  /** Parsed DYN-C029 inventory root. */
  private static final class InventoryDocument {
    @SerializedName("schema_version")
    private String schemaVersion;
    private String status;
    @SerializedName("ready_for_dynamic_safeguarding_design")
    private boolean readyForDynamicSafeguardingDesign;
    private InventoryScope scope;
    private List<InventoryRoute> routes;
    @SerializedName("equipment_volumes")
    private List<InventoryEquipmentVolume> equipmentVolumes;
    @SerializedName("isolation_devices")
    private List<InventoryDevice> isolationDevices;
    @SerializedName("isolation_sections")
    private List<InventorySection> isolationSections;
    private List<Map<String, Object>> blockers;
  }

  /** Parsed physical-inventory scope. */
  private static final class InventoryScope {
    private String id;
    private String revision;
    @SerializedName("source_reference")
    private String sourceReference;
  }

  /** Parsed physical evidence source. */
  private static final class InventorySource {
    private String reference;
    private String revision;
    @SerializedName("evidence_status")
    private String evidenceStatus;
  }

  /** Parsed physical isolation device. */
  private static final class InventoryDevice {
    private String id;
    private String tag;
    @SerializedName("device_type")
    private String deviceType;
    @SerializedName("fail_state")
    private String failState;
    private InventorySource source;
  }

  /** Parsed controlled physical route. */
  private static final class InventoryRoute {
    private String id;
    @SerializedName("length_m")
    private double lengthM;
    @SerializedName("pipe_volume_m3")
    private double pipeVolumeM3;
    private InventorySource source;
  }

  /** Parsed controlled physical equipment volume. */
  private static final class InventoryEquipmentVolume {
    private String id;
    @SerializedName("equipment_tag")
    private String equipmentTag;
    @SerializedName("volume_m3")
    private double volumeM3;
    private InventorySource source;
  }

  /** Parsed physical isolation section. */
  private static final class InventorySection {
    private String id;
    @SerializedName("route_ids")
    private List<String> routeIds;
    @SerializedName("equipment_volume_ids")
    private List<String> equipmentVolumeIds;
    @SerializedName("boundary_device_ids")
    private List<String> boundaryDeviceIds;
    @SerializedName("total_isolated_volume_m3")
    private double totalIsolatedVolumeM3;
    private InventorySource source;
  }
}
