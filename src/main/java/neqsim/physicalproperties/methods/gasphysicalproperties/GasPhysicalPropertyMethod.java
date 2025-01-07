package neqsim.physicalproperties.methods.gasphysicalproperties;

import neqsim.physicalproperties.methods.PhysicalPropertyMethod;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * GasPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class GasPhysicalPropertyMethod extends PhysicalPropertyMethod {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  protected PhysicalProperties gasPhase;
  public double[][] binaryMolecularDiameter;
  public double[][] binaryEnergyParameter;
  public double[][] binaryMolecularMass;

  /**
   * <p>
   * Constructor for GasPhysicalPropertyMethod.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public GasPhysicalPropertyMethod(PhysicalProperties gasPhase) {
    super(gasPhase);
    binaryMolecularDiameter = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
        .getPhase().getNumberOfComponents()];
    binaryMolecularMass = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
        .getPhase().getNumberOfComponents()];
    binaryEnergyParameter = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
        .getPhase().getNumberOfComponents()];

    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        binaryMolecularMass[i][j] =
            2.0 * Math.pow((1.0 / gasPhase.getPhase().getComponent(i).getMolarMass() / 1000.0
                + 1.0 / gasPhase.getPhase().getComponent(j).getMolarMass() / 1000.0), -1.0);
        binaryMolecularDiameter[i][j] =
            (gasPhase.getPhase().getComponent(i).getLennardJonesMolecularDiameter()
                + gasPhase.getPhase().getComponent(j).getLennardJonesMolecularDiameter()) / 2.0;
        binaryEnergyParameter[i][j] =
            Math.pow(gasPhase.getPhase().getComponent(i).getLennardJonesEnergyParameter()
                * gasPhase.getPhase().getComponent(j).getLennardJonesEnergyParameter(), 0.5);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(PhysicalProperties phase) {
    this.gasPhase = phase;
  }
}
