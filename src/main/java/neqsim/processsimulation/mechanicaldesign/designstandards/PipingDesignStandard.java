package neqsim.processsimulation.mechanicaldesign.designstandards;

import neqsim.processsimulation.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * PipingDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PipingDesignStandard extends DesignStandard {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PipingDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processsimulation.mechanicaldesign.MechanicalDesign} object
   */
  public PipingDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
  }
}
