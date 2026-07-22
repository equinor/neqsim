package neqsim.process.mechanicaldesign.designstandards;

import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * JointEfficiencyPipelineStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class JointEfficiencyPipelineStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for JointEfficiencyPipelineStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public JointEfficiencyPipelineStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
  }

  /**
   * getJEFactor.
   *
   * @return the JEFactor
   */
  public double getJEFactor() {
    return JEFactor;
  }

  /**
   * setJEFactor.
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
   * readJointEfficiencyStandard.
   *
   * @param typeName a {@link java.lang.String} object
   * @param radiagraphType a {@link java.lang.String} object
   */
  public void readJointEfficiencyStandard(String typeName, String radiagraphType) {
    // ... to be implemented
    JEFactor = 1.0;
  }
}
