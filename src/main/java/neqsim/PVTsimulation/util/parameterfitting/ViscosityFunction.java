package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;

/**
 * <p>
 * ViscosityFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ViscosityFunction extends LevenbergMarquardtFunction {
  double molarMass = 0.0;
  boolean includeWaxEmulsionViscosity = true;

  /**
   * <p>
   * Constructor for ViscosityFunction.
   * </p>
   */
  public ViscosityFunction() {
    params = new double[1];
  }

  /**
   * <p>
   * Constructor for ViscosityFunction.
   * </p>
   *
   * @param includeWax a boolean
   */
  public ViscosityFunction(boolean includeWax) {
    params = new double[1];
    includeWaxEmulsionViscosity = includeWax;
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    thermoOps.TPflash();
    system.initPhysicalProperties();
    double waxFraction = 0.0;
    if (system.hasPhaseType("wax") && includeWaxEmulsionViscosity) {
      waxFraction = system.getWtFraction(system.getPhaseNumberOfPhase("wax"));
      return system.getPhase(0).getPhysicalProperties().getViscosityOfWaxyOil(waxFraction,
          dependentValues[0]); // %wax
    }
    // system.display();
    return system.getPhase(0).getPhysicalProperties().getViscosity(); // %wax
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;

    ((FrictionTheoryViscosityMethod) system.getPhase(0).getPhysicalProperties().getViscosityModel())
        .setTBPviscosityCorrection(value);
  }
}
