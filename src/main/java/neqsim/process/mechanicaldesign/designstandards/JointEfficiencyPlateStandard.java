package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * JointEfficiencyPlateStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class JointEfficiencyPlateStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for JointEfficiencyPlateStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public JointEfficiencyPlateStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
  }

  /**
   * <p>
   * getJEFactor.
   * </p>
   *
   * @return the JEFactor
   */
  public double getJEFactor() {
    return JEFactor;
  }

  /**
   * <p>
   * setJEFactor.
   * </p>
   *
   * @param JEFactor the JEFactor to set
   */
  public void setJEFactor(double JEFactor) {
    this.JEFactor = JEFactor;
  }

  String typeName = "Double welded Butt joints";
  String radiagraphType = "Fully rediographed";
  private double JEFactor = 1.0;

  /**
   * <p>
   * readJointEfficiencyStandard.
   * </p>
   *
   * @param typeName a {@link java.lang.String} object
   * @param radiagraphType a {@link java.lang.String} object
   */
  public void readJointEfficiencyStandard(String typeName, String radiagraphType) {
    // ... to be implemented
    // read from techncal requirements_mechanical table.....
    JEFactor = 1.0;
  }
}
