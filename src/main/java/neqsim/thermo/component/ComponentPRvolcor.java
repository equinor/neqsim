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
  // private double calcc;
  public double[] Cij = new double[MAX_NUMBER_OF_COMPONENTS];
  public double Ci = 0;

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
    c = (0.1154 - 0.4406 * (0.29056 - 0.08775 * getAcentricFactor())) * R * criticalTemperature
        / criticalPressure;

  }

  /** {@inheritDoc} */
  @Override
  public void init(double temp, double pres, double totMoles, double beta, int type) {
    super.init(temp, pres, totMoles, beta, type);
    c = calcc();
  }


  // ADDED THIS PUBLIC VOID called Finit it in order to apply the Ci...Dont know if this is right
  // @Override
  public void Finit(PhasePrEosvolcor phase, double temp, double pres, double totMoles, double beta,
      int numberOfComponents, int type) {
    super.Finit(phase,temp,pres,totMoles,beta,numberOfComponents,type);
    
      for (int j = 0; j < numberOfComponents; j++) {
       //Cij[j] = ((PhasePrEosvolcor)phase).calcCij(componentNumber, j, phase, temp, pres, numberOfComponents);
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

  // derivative of C with regards to mole fraction
  public double getCi() {
    return Ci;
  }

  /// ** {@inheritDoc} */ do I need this inheritDoc thing?
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi()
        + ((PhasePrEosvolcor) phase).FC() * getCi();
  }

  // Remember this trick above that professor showed to you...you can call whichever new F
  // expression
  // you need by first specifying the type "PhasePrEosvolcor"

}
