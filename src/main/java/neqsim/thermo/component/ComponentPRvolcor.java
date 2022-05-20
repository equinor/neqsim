/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEosvolcor;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentPRvolcor extends ComponentPR {

  private static final long serialVersionUID = 1000;
  private double c;
  //private double calcc;
  public double[] Cij = new double[MAX_NUMBER_OF_COMPONENTS];
  public double Ci=0;

  public ComponentPRvolcor(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /// ** {@inheritDoc} */
  // @Override
  public double calcc() {
    return (0.1154 - 0.4406 * (0.29056 - 0.08775 * getAcentricFactor())) * R * criticalTemperature
        / criticalPressure;
  }

  public ComponentPRvolcor(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);

    // a = .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    // b = .077803333 * R * criticalTemperature / criticalPressure;
    c = (0.1154 - 0.4406 * (0.29056 - 0.08775 * getAcentricFactor())) * R * criticalTemperature
        / criticalPressure;
    // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
    // acentricFactor;

    // delta1 = 1.0 + Math.sqrt(2.0);
    // delta2 = 1.0 - Math.sqrt(2.0);
    // setAttractiveParameter(new AttractiveTermPr(this));

    // double[] surfTensInfluenceParamtemp = {1.3192, 1.6606, 1.1173, 0.8443};
    // this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double temp, double pres, double totMoles, double beta, int type) {
    super.init(temp, pres, totMoles, beta, type);
    a = calca();
    b = calcb();
    c = calcc();
    reducedTemperature = reducedTemperature(temp);
    reducedPressure = reducedPressure(pres);
    aT = a * alpha(temp);
    if (type >= 2) {
      aDiffT = diffaT(temp);
      aDiffDiffT = diffdiffaT(temp);
    }
  }


  //ADDED THIS PUBLIC VOID called Finit it in order to apply the Ci...Dont know if this is right
  //@Override
  public void Finit(PhasePrEosvolcor phase, double temp, double pres, double totMoles, double beta,
          int numberOfComponents, int type) {
      Bi = phase.calcBi(componentNumber, phase, temp, pres, numberOfComponents);
      Ai = phase.calcAi(componentNumber, phase, temp, pres, numberOfComponents);
      Ci = phase.calcCi(componentNumber, phase, temp, pres, numberOfComponents);
      if (type >= 2) {
          AiT = phase.calcAiT(componentNumber, phase, temp, pres, numberOfComponents);
      }
      double totVol = phase.getMolarVolume() * phase.getNumberOfMolesInPhase();
      voli = -(-R * temp * dFdNdV(phase, numberOfComponents, temp, pres)
              + R * temp / (phase.getMolarVolume() * phase.getNumberOfMolesInPhase()))
              / (-R * temp * phase.dFdVdV()
                      - phase.getNumberOfMolesInPhase() * R * temp / (totVol * totVol));

      if (type >= 3) {
          for (int j = 0; j < numberOfComponents; j++) {
              Aij[j] = phase.calcAij(componentNumber, j, phase, temp, pres, numberOfComponents);
              Bij[j] = phase.calcBij(componentNumber, j, phase, temp, pres, numberOfComponents);
              Cij[j] = phase.calcCij(componentNumber, j, phase, temp, pres, numberOfComponents);
          }
      }
  }

  @Override
  public double getVolumeCorrection() {
    return 0.0;
  }

  /** {@inheritDoc} */
  // @Override
  public double getc() {
    return c;
  }

  //derivative of C with regards to mole fraction
  public double getCi() {
    return Ci;
}

  /// ** {@inheritDoc} */ do I need this inheritDoc thing?
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi()
        + PhasePrEosvolcor.FC() * getCi();
  }

  // it was here
  // @Override
  // public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature, double
  // pressure) {
  // return super.dFdN(phase,numberOfComponents,temperature,pressure);
  // }

  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdT(phase, numberOfComponents, temperature, pressure);
  }

  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return super.dFdNdV(phase, numberOfComponents, temperature, pressure);
  }

}
