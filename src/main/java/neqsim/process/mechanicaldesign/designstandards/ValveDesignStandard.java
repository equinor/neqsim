package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * ValveDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class ValveDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  public double valveCvMax = 1.0;

  /**
   * Getter for the field <code>valveCvMax</code>.
   *
   * @return a double
   */
  public double getValveCvMax() {
    return valveCvMax;
  }

  /**
   * Constructor for ValveDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public ValveDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
  }
}
