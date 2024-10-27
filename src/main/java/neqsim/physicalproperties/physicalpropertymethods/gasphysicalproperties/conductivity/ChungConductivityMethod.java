package neqsim.physicalproperties.physicalpropertymethods.gasphysicalproperties.conductivity;

import neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties;

/**
 * <p>
 * ChungConductivityMethod class extending conductivity for gases.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChungConductivityMethod extends Conductivity {
  private static final long serialVersionUID = 1000;
  double conductivity = 0;
  public double[] pureComponentConductivity;

  /**
   * <p>
   * Constructor for ChungConductivityMethod.
   * </p>
   *
   * @param gasPhase a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalProperties} object
   */
  public ChungConductivityMethod(PhysicalProperties gasPhase) {
    super(gasPhase);
    pureComponentConductivity = new double[gasPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    calcPureComponentConductivity();
    double tempVar = 0;
    conductivity = 0;

    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      tempVar = 0;
      for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
        // Aij from Mason Saxena method
        double Aij = 1.0
            * Math
                .pow(
                    1.0 + Math.sqrt(pureComponentConductivity[i] / pureComponentConductivity[j])
                        * Math.pow(gasPhase.getPhase().getComponents()[i].getMolarMass()
                            / gasPhase.getPhase().getComponents()[j].getMolarMass(), 1.0 / 4.0),
                    2.0)
            / Math.sqrt(8.0 * (1.0 + gasPhase.getPhase().getComponents()[i].getMolarMass()
                / gasPhase.getPhase().getComponents()[j].getMolarMass()));
        tempVar += gasPhase.getPhase().getComponents()[j].getx() * Aij;
      }
      conductivity +=
          gasPhase.getPhase().getComponents()[i].getx() * pureComponentConductivity[i] / tempVar;
    }
    // System.out.println("conductivity " + conductivity);
    return conductivity;
  }

  /**
   * <p>
   * calcPureComponentConductivity.
   * </p>
   */
  public void calcPureComponentConductivity() {
    double tempVar2 = 0;
    double tempBeta = 0;

    double tempAlpha = 0;
    double tempZ = 0;
    for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
      // pure component conductivity
      tempVar2 =
          gasPhase.getPhase().getComponents()[i].getCv0(gasPhase.getPhase().getTemperature()) / R
              - 1.5;
      tempBeta = 0.7862 - 0.7109 * gasPhase.getPhase().getComponents()[i].getAcentricFactor()
          + 1.3168 * Math.pow(gasPhase.getPhase().getComponents()[i].getAcentricFactor(), 2.0);
      tempZ = 2.0 + 10.5 * Math.pow(
          gasPhase.getPhase().getTemperature() / gasPhase.getPhase().getComponents()[i].getTC(),
          2.0);
      tempAlpha =
          1.0 + tempVar2 * ((0.215 + 0.28288 * tempVar2 - 1.061 * tempBeta + 0.26665 * tempZ)
              / (0.6366 + tempBeta * tempZ + 1.061 * tempVar2 * tempBeta));
      pureComponentConductivity[i] = 1.0 / gasPhase.getPhase().getComponents()[i].getMolarMass()
          * gasPhase.getPureComponentViscosity(i) * 1e-7
          * gasPhase.getPhase().getComponents()[i].getCv0(gasPhase.getPhase().getTemperature())
          * 3.75 * tempAlpha
          / (gasPhase.getPhase().getComponents()[i].getCv0(gasPhase.getPhase().getTemperature())
              / R);
      if (pureComponentConductivity[i] < 1e-50) {
        pureComponentConductivity[i] = 1e-50;
      }
    }
  }
}
