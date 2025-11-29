package neqsim.thermo.phase;

import org.netlib.util.doubleW;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentEOSCGEos;

/** Phase implementation using the EOS-CG mixture model. */
public class PhaseEOSCGEos extends PhaseGERG2008Eos {
  private static final long serialVersionUID = 1000;

  public PhaseEOSCGEos() {
    thermoPropertyModelName = "EOS-CG";
  }

  @Override
  public PhaseEOSCGEos clone() {
    return (PhaseEOSCGEos) super.clone();
  }

  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentEOSCGEos(name, moles, molesInPhase, compNumber);
  }

  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    IPHASE = pt == PhaseType.LIQUID ? -1 : -2;
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (!okVolume) {
      IPHASE = pt == PhaseType.LIQUID ? -2 : -1;
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
    if (initType >= 1) {
      double[] temp = new double[18];
      temp = getProperties_EOSCG();
      a0 = getAlpha0_EOSCG();
      ar = getAlphares_EOSCG();

      pressure = temp[0] / 100;
      Z = temp[1];
      W = temp[11];
      JTcoef = temp[13];
      kappa = temp[14];
      gibbsEnergy = temp[12];
      internalEnery = temp[6];
      enthalpy = temp[7];
      entropy = temp[8];
      CpGERG2008 = temp[10];
      CvGERG2008 = temp[9];
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    }
  }

  @Override
  public double dFdN(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdN(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  @Override
  public double dFdNdN(int i, int j) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdN(j, this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  @Override
  public double dFdNdV(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdV(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  @Override
  public double dFdNdT(int i) {
    return ((ComponentEosInterface) getComponent(i)).dFdNdT(this, this.getNumberOfComponents(),
        temperature, pressure);
  }

  @Override
  public double getdPdTVn() {
    return (getNumberOfMolesInPhase() / getVolume()) * R * (1 + ar[0][1].val - ar[1][1].val);
  }
}
