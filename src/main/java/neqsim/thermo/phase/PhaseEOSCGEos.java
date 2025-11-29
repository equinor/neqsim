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
  }

  @Override
  public double[] getProperties_GERG2008() {
    return getProperties_EOSCG();
  }

  @Override
  public doubleW[] getAlpha0_GERG2008() {
    return getAlpha0_EOSCG();
  }

  @Override
  public doubleW[][] getAlphares_GERG2008() {
    return getAlphares_EOSCG();
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
