package neqsim.process.mechanicaldesign.designstandards;

import java.util.Objects;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;

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
