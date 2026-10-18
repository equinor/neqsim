package neqsim.process.mechanicaldesign.designstandards;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;
import neqsim.process.mechanicaldesign.data.ProcessDesignParameters;

/**
 * DesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class DesignStandard implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  public MechanicalDesign equipment = null;
  public String standardName = "";

  /**
   * Constructor for DesignStandard.
   */
  public DesignStandard() {
  }

  /**
   * Constructor for DesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public DesignStandard(String name, MechanicalDesign equipmentInn) {
    equipment = equipmentInn;
    standardName = name;
  }

  /**
   * setDesignStandardName.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setDesignStandardName(String name) {
    setStandardName(name);
  }

  /**
   * Getter for the field <code>equipment</code>.
   *
   * @return the equipment
   */
  public MechanicalDesign getEquipment() {
    return equipment;
  }

  /**
   * Setter for the field <code>equipment</code>.
   *
   * @param equipment the equipment to set
   */
  public void setEquipment(MechanicalDesign equipment) {
    this.equipment = equipment;
  }

  /**
   * Getter for the field <code>standardName</code>.
   *
   * @return the standardName
   */
  public String getStandardName() {
    return standardName;
  }

  /**
   * Setter for the field <code>standardName</code>.
   *
   * @param standardName the standardName to set
   */
  public void setStandardName(String standardName) {
    this.standardName = standardName;
  }

  /**
   * Resolves historical names for the bundled Statoil technical requirements.
   *
   * @param identifier requested company identifier
   * @return canonical identifier, or the unchanged identifier for another company
   */
  public static String normalizeCompanyIdentifier(String identifier) {
    if (identifier == null) {
      return "";
    }
    String value = identifier.trim();
    if ("Statoil".equalsIgnoreCase(value) || "Statoil_TR1414".equalsIgnoreCase(value)
        || "StatoilTR".equalsIgnoreCase(value)) {
      return "StatoilTR";
    }
    return value;
  }

  /** Actual sources for loaded process requirements. */
  private Map<String, String> processRequirementSources = new LinkedHashMap<String, String>();

  /**
   * Load an explicit layered process requirement set.
   *
   * @param equipmentType database equipment type
   * @param company company identifier
   * @return validated values with provenance
   */
  protected Map<String, ProcessDesignParameters.Value> loadProcessRequirements(String equipmentType, String company) {
    String selectedCompany = company;
    if (equipment != null && company != null && company.equals(equipment.getCompanySpecificDesignStandards())) {
      selectedCompany = equipment.getProcessDesignCompanyIdentifier();
    }
    for (StandardType type : StandardType.values()) {
      if (company != null && (company.equals(type.getCode()) || company.startsWith(type.getCode() + " "))) {
        selectedCompany = equipment == null ? "default" : equipment.getProcessDesignCompanyIdentifier();
        break;
      }
    }
    Map<String, ProcessDesignParameters.Value> values = ProcessDesignParameters.load(equipmentType, selectedCompany);
    processRequirementSources = new LinkedHashMap<String, String>();
    for (Map.Entry<String, ProcessDesignParameters.Value> entry : values.entrySet()) {
      processRequirementSources.put(entry.getKey(), entry.getValue().source());
    }
    return values;
  }

  /**
   * Return the actual company and document for loaded process requirements.
   *
   * @return immutable provenance snapshot
   */
  public Map<String, String> getProcessDesignParameterSources() {
    return processRequirementSources == null ? Collections.<String, String>emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<String, String>(processRequirementSources));
  }

  /**
   * Compute the safety margins for the associated equipment.
   *
   * @return margin result or {@link MechanicalDesignMarginResult#EMPTY} if unavailable.
   */
  public MechanicalDesignMarginResult computeSafetyMargins() {
    if (equipment == null) {
      return MechanicalDesignMarginResult.EMPTY;
    }
    return equipment.validateOperatingEnvelope();
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(equipment, standardName);
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DesignStandard other = (DesignStandard) obj;
    return Objects.equals(equipment, other.equipment) && Objects.equals(standardName, other.standardName);
  }
}
