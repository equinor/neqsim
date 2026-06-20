package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * ValveDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ValveDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  public double valveCvMax = 1.0;

  /**
   * <p>
   * Getter for the field <code>valveCvMax</code>.
   * </p>
   *
   * @return a double
   */
  public double getValveCvMax() {
    return valveCvMax;
  }

  /**
   * <p>
   * Constructor for ValveDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public ValveDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
  }
}
