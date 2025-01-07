package neqsim.process.equipment.separator.sectiontype;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.separator.sectiontype.SepDesignSection;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * SeparatorSection class.
 * </p>
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
   * <p>
   * Constructor for SeparatorSection.
   * </p>
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
   * <p>
   * calcEfficiency.
   * </p>
   *
   * @return a double
   */
  public double calcEfficiency() {
    return efficiency;
  }

  /**
   * <p>
   * Getter for the field <code>efficiency</code>.
   * </p>
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
   * <p>
   * Setter for the field <code>efficiency</code>.
   * </p>
   *
   * @param efficiency the efficiency to set
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * <p>
   * isCalcEfficiency.
   * </p>
   *
   * @return the calcEfficiency
   */
  public boolean isCalcEfficiency() {
    return calcEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>calcEfficiency</code>.
   * </p>
   *
   * @param calcEfficiency the calcEfficiency to set
   */
  public void setCalcEfficiency(boolean calcEfficiency) {
    this.calcEfficiency = calcEfficiency;
  }

  /**
   * <p>
   * getMinimumLiquidSealHeight.
   * </p>
   *
   * @return a double
   */
  public double getMinimumLiquidSealHeight() {
    return getPressureDrop() * 1e5 / neqsim.thermo.ThermodynamicConstantsInterface.gravity
        / (getSeparator().getThermoSystem().getPhase(1).getPhysicalProperties().getDensity()
            - getSeparator().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
  }

  /**
   * <p>
   * getPressureDrop.
   * </p>
   *
   * @return the pressureDrop
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * <p>
   * setPressureDrop.
   * </p>
   *
   * @param pressureDrop the pressureDrop to set
   */
  public void setPressureDrop(double pressureDrop) {
    this.pressureDrop = pressureDrop;
  }

  /**
   * <p>
   * Getter for the field <code>mechanicalDesign</code>.
   * </p>
   *
   * @return the mechanicalDesign
   */
  public SepDesignSection getMechanicalDesign() {
    return new SepDesignSection(this);
  }

  /**
   * <p>
   * Getter for the field <code>separator</code>.
   * </p>
   *
   * @return the separator
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * <p>
   * Setter for the field <code>separator</code>.
   * </p>
   *
   * @param separator the separator to set
   */
  public void setSeparator(Separator separator) {
    this.separator = separator;
  }

  /**
   * <p>
   * Getter for the field <code>outerDiameter</code>.
   * </p>
   *
   * @return the outerDiameter
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>outerDiameter</code>.
   * </p>
   *
   * @param outerDiameter the outerDiameter to set
   */
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }
}
