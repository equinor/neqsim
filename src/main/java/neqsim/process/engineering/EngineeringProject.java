package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.engineering.EngineeringValidationReport.Severity;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Governed engineering representation associated with a runnable NeqSim process.
 *
 * <p>
 * The process model remains the calculation authority. This class holds the design basis, deterministic engineering
 * proposals, standards traceability, and approval state needed for DEXPI and engineering-deliverable generation.
 * </p>
 */
public final class EngineeringProject implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId = UUID.randomUUID().toString();
  private final String name;
  private final ProcessSystem processSystem;
  private final EngineeringDesignBasis designBasis;
  private final List<EngineeringRequirement> requirements = new ArrayList<EngineeringRequirement>();

  EngineeringProject(String name, ProcessSystem processSystem, EngineeringDesignBasis designBasis) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    if (designBasis == null) {
      throw new IllegalArgumentException("designBasis must not be null");
    }
    this.name = name;
    this.processSystem = processSystem;
    this.designBasis = designBasis;
  }

  /**
   * Adds a requirement from a deterministic or project-specific rule pack.
   *
   * @param requirement requirement to add
   */
  public void addRequirement(EngineeringRequirement requirement) {
    if (requirement == null) {
      throw new IllegalArgumentException("requirement must not be null");
    }
    requirements.add(requirement);
  }

  /** @return immutable project identifier */
  public String getProjectId() {
    return projectId;
  }

  /** @return engineering project name */
  public String getName() {
    return name;
  }

  /** @return associated runnable process system */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /** @return project design basis */
  public EngineeringDesignBasis getDesignBasis() {
    return designBasis;
  }

  /** @return immutable generated-requirement list */
  public List<EngineeringRequirement> getRequirements() {
    return Collections.unmodifiableList(requirements);
  }

  /** Returns requirements associated with one equipment tag. */
  public List<EngineeringRequirement> getRequirementsForEquipment(String equipmentTag) {
    List<EngineeringRequirement> result = new ArrayList<EngineeringRequirement>();
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getEquipmentTag().equals(equipmentTag)) {
        result.add(requirement);
      }
    }
    return result;
  }

  /**
   * Validates topology identifiers, minimum design data, compressor protection data, and approval governance.
   *
   * @return validation report
   */
  public EngineeringValidationReport validate() {
    EngineeringValidationReport report = new EngineeringValidationReport();
    Set<String> tags = new HashSet<String>();
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      String tag = unit.getName();
      if (tag == null || tag.trim().isEmpty()) {
        report.add(Severity.ERROR, "ENG-TAG-001", "", "Equipment has no tag name");
        continue;
      }
      if (!tags.add(tag)) {
        report.add(Severity.ERROR, "ENG-TAG-002", tag, "Equipment tag is not unique");
      }
      if (unit.getDesignConditions() == null || unit.getDesignConditions().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-DESIGN-001", tag,
            "Nameplate design conditions have not been specified or approved");
      }
      if (unit instanceof Compressor) {
        Compressor compressor = (Compressor) unit;
        if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
          report.add(Severity.WARNING, "ENG-COMP-001", tag, "No active vendor or design compressor map is available");
        }
        if (compressor.getAntiSurge() == null || !compressor.getAntiSurge().isActive()) {
          report.add(Severity.REVIEW, "ENG-COMP-002", tag, "Antisurge protection is not active in the process model");
        }
      }
    }
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        report.add(Severity.REVIEW, "ENG-APPROVAL-001", requirement.getEquipmentTag(),
            requirement.getId() + " requires engineering review");
      }
      if ((requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS)
          && "SIL_UNASSIGNED".equals(requirement.getSilTarget())) {
        report.add(Severity.INFORMATION, "ENG-SIL-001", requirement.getEquipmentTag(),
            requirement.getId() + " has no SIL target; determine through HAZOP/LOPA and the SRS");
      }
    }
    return report;
  }

  /** Serializes the governed engineering manifest without serializing the complete process object graph. */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("projectId", projectId);
    root.addProperty("name", name);
    root.addProperty("processName", processSystem.getName());

    JsonObject basis = new JsonObject();
    basis.addProperty("jurisdiction", designBasis.getJurisdiction());
    basis.addProperty("facilityType", designBasis.getFacilityType());
    basis.addProperty("projectPhase", designBasis.getProjectPhase());
    JsonArray cases = new JsonArray();
    for (String designCase : designBasis.getDesignCases()) {
      cases.add(designCase);
    }
    basis.add("designCases", cases);
    JsonArray standards = new JsonArray();
    for (EngineeringStandard standard : designBasis.getStandards()) {
      JsonObject item = new JsonObject();
      item.addProperty("code", standard.getCode());
      item.addProperty("edition", standard.getEdition());
      item.addProperty("title", standard.getTitle());
      item.addProperty("application", standard.getApplication());
      standards.add(item);
    }
    basis.add("standards", standards);
    root.add("designBasis", basis);

    root.add("requirements", new GsonBuilder().create().toJsonTree(requirements));
    root.add("validation", new GsonBuilder().create().toJsonTree(validate().getFindings()));
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
