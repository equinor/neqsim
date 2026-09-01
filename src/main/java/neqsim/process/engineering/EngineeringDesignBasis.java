package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Project-level basis governing generated engineering information. */
public final class EngineeringDesignBasis implements Serializable {
  private static final long serialVersionUID = 1000L;
  private String jurisdiction = "Unspecified";
  private String facilityType = "Process facility";
  private String projectPhase = "Concept study";
  private final List<EngineeringStandard> standards = new ArrayList<EngineeringStandard>();
  private final List<String> designCases = new ArrayList<String>();

  /**
   * Sets the regulatory jurisdiction.
   *
   * @param jurisdiction regulatory jurisdiction
   * @return this basis
   */
  public EngineeringDesignBasis setJurisdiction(String jurisdiction) {
    this.jurisdiction = Objects.requireNonNull(jurisdiction, "jurisdiction");
    return this;
  }

  /**
   * Sets the facility classification.
   *
   * @param facilityType facility classification
   * @return this basis
   */
  public EngineeringDesignBasis setFacilityType(String facilityType) {
    this.facilityType = Objects.requireNonNull(facilityType, "facilityType");
    return this;
  }

  /**
   * Sets the engineering maturity or project phase.
   *
   * @param projectPhase engineering maturity or project phase
   * @return this basis
   */
  public EngineeringDesignBasis setProjectPhase(String projectPhase) {
    this.projectPhase = Objects.requireNonNull(projectPhase, "projectPhase");
    return this;
  }

  /**
   * Adds an applicable standard.
   *
   * @param standard applicable standard
   * @return this basis
   */
  public EngineeringDesignBasis addStandard(EngineeringStandard standard) {
    standards.add(Objects.requireNonNull(standard, "standard"));
    return this;
  }

  /**
   * Adds a governing operating or accidental case.
   *
   * @param designCase governing operating or accidental case
   * @return this basis
   */
  public EngineeringDesignBasis addDesignCase(String designCase) {
    Objects.requireNonNull(designCase, "designCase");
    if (!designCase.trim().isEmpty() && !designCases.contains(designCase)) {
      designCases.add(designCase);
    }
    return this;
  }

  /** @return regulatory jurisdiction */
  public String getJurisdiction() {
    return jurisdiction;
  }

  /** @return facility classification */
  public String getFacilityType() {
    return facilityType;
  }

  /** @return engineering maturity or project phase */
  public String getProjectPhase() {
    return projectPhase;
  }

  /** @return immutable applicable-standard list */
  public List<EngineeringStandard> getStandards() {
    return Collections.unmodifiableList(standards);
  }

  /** @return immutable governing-case list */
  public List<String> getDesignCases() {
    return Collections.unmodifiableList(designCases);
  }
}
