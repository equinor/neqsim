package neqsim.process.equipment.separator.sectiontype;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.separator.sectiontype.SepDesignSection;
import neqsim.util.NamedBaseClass;

/**
 * SeparatorSection class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorSection extends NamedBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double efficiency = 0.95;
  public Separator separator = null;
  private boolean calcEfficiency = false;
  private double pressureDrop = 33 / 5.0 * 1e-3; // bar
  String type;
  public double outerDiameter = 1.0;

  /**
   * Constructor for SeparatorSection.
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   * @param sep a {@link neqsim.process.equipment.separator.Separator} object
   */
  public SeparatorSection(String name, String type, Separator sep) {
    super(name);
    this.type = type;
    this.separator = sep;
  }

  /**
   * calcEfficiency.
   *
   * @return a double
   */
  public double calcEfficiency() {
    return efficiency;
  }

  /**
   * Gets the section type identifier (e.g. "mesh", "vane", "manway", "valve", "nozzle").
   *
   * @return the section type
   */
  public String getType() {
    return type;
  }

  /**
   * Getter for the field <code>efficiency</code>.
   *
   * @return the efficiency
   */
  public double getEfficiency() {
    if (isCalcEfficiency()) {
      return calcEfficiency();
    }
    return efficiency;
  }

  /**
   * Setter for the field <code>efficiency</code>.
   *
   * @param efficiency the efficiency to set
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * isCalcEfficiency.
   *
   * @return the calcEfficiency
   */
  public boolean isCalcEfficiency() {
    return calcEfficiency;
  }

  /**
   * Setter for the field <code>calcEfficiency</code>.
   *
   * @param calcEfficiency the calcEfficiency to set
   */
  public void setCalcEfficiency(boolean calcEfficiency) {
    this.calcEfficiency = calcEfficiency;
  }

  /**
   * getMinimumLiquidSealHeight.
   *
   * @return a double
   */
  public double getMinimumLiquidSealHeight() {
    return getPressureDrop() * 1e5 / neqsim.thermo.ThermodynamicConstantsInterface.gravity
        / (getSeparator().getThermoSystem().getPhase(1).getPhysicalProperties().getDensity()
            - getSeparator().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
  }

  /**
   * getPressureDrop.
   *
   * @return the pressureDrop
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * setPressureDrop.
   *
   * @param pressureDrop the pressureDrop to set
   */
  public void setPressureDrop(double pressureDrop) {
    this.pressureDrop = pressureDrop;
  }

  /**
   * Getter for the field <code>mechanicalDesign</code>.
   *
   * @return the mechanicalDesign
   */
  public SepDesignSection getMechanicalDesign() {
    return new SepDesignSection(this);
  }

  /**
   * Getter for the field <code>separator</code>.
   *
   * @return the separator
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Setter for the field <code>separator</code>.
   *
   * @param separator the separator to set
   */
  public void setSeparator(Separator separator) {
    this.separator = separator;
  }

  /**
   * Getter for the field <code>outerDiameter</code>.
   *
   * @return the outerDiameter
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * Setter for the field <code>outerDiameter</code>.
   *
   * @param outerDiameter the outerDiameter to set
   */
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }
}
