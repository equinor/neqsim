package neqsim.PVTsimulation.util.parameterfitting;

import neqsim.PVTsimulation.simulation.SaturationPressure;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardtFunction;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SaturationPressureFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SaturationPressureFunction extends LevenbergMarquardtFunction {
  double molarMass = 0.0;

  /**
   * <p>
   * Constructor for SaturationPressureFunction.
   * </p>
   */
  public SaturationPressureFunction() {
    params = new double[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcValue(double[] dependentValues) {
    int plusNumber = 0;
    molarMass = params[0];
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
        plusNumber = i;
      }
    }
    SystemInterface tempSystem = system.clone();
    tempSystem.resetCharacterisation();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(system.getMixingRule());

    tempSystem.getPhase(0).getComponent(plusNumber).setMolarMass(molarMass);
    tempSystem.getPhase(1).getComponent(plusNumber).setMolarMass(molarMass);
    tempSystem.setTemperature(dependentValues[0]);
    tempSystem.setPressure(50.0);
    tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(system.getMixingRule());
    tempSystem.init(0);
    tempSystem.init(1);
    // \\tempSystem.display();
    SaturationPressure satCalc = new SaturationPressure(tempSystem);
    double satPres = satCalc.calcSaturationPressure();
    // tempSystem.display();
    return satPres;
  }

  /** {@inheritDoc} */
  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }
}
