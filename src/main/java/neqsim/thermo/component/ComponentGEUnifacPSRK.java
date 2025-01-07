package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGEUnifacPSRK class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEUnifacPSRK extends ComponentGEUnifac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] bij = new double[1][1];
  double[][] cij = new double[1][1];

  /**
   * <p>
   * Constructor for ComponentGEUnifacPSRK.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGEUnifacPSRK(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * calcaij.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double calcaij(PhaseInterface phase, int i, int j) {
    return ((PhaseGEUnifac) phase).getAij(i, j)
        + ((PhaseGEUnifac) phase).getBij(i, j) * phase.getTemperature()
        + ((PhaseGEUnifac) phase).getCij(i, j) * Math.pow(phase.getTemperature(), 2.0);
  }

  /**
   * <p>
   * calcaijdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double calcaijdT(PhaseInterface phase, int i, int j) {
    return ((PhaseGEUnifac) phase).getBij(i, j)
        + 2.0 * ((PhaseGEUnifac) phase).getCij(i, j) * phase.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void calclnGammak(int k, PhaseInterface phase) {
    double sum1Comp = 0.0;
    double sum1Mix = 0.0;
    double sum3Comp = 0.0;

    double sum3Mix = 0.0;
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      sum1Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature()
          * calcaij(phase, getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      sum1Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature()
          * calcaij(phase, getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      double sum2Comp = 0.0;
      double sum2Mix = 0.0;
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        sum2Comp += getUnifacGroup(j).getQComp() * Math.exp(-1.0 / phase.getTemperature()
            * calcaij(phase, getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
        sum2Mix += getUnifacGroup(j).getQMix() * Math.exp(-1.0 / phase.getTemperature()
            * calcaij(phase, getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
      }
      sum3Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature()
          * calcaij(phase, getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Comp;
      sum3Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature()
          * calcaij(phase, getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Mix;
    }
    double tempGammaComp = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Comp) - sum3Comp);
    double tempGammaMix = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Mix) - sum3Mix);
    getUnifacGroup(k).setLnGammaComp(tempGammaComp);
    getUnifacGroup(k).setLnGammaMix(tempGammaMix);
  }

  /**
   * <p>
   * calclnGammakdT.
   * </p>
   *
   * @param k a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calclnGammakdT(int k, PhaseInterface phase) {
    double sum1Comp = 0.0;
    double sum1Mix = 0.0;
    double sum3Comp = 0.0;

    double sum3Mix = 0.0;
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      sum1Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature()
          * calcaijdT(phase, getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      sum1Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature()
          * calcaijdT(phase, getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      double sum2Comp = 0.0;
      double sum2Mix = 0.0;
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        sum2Comp +=
            getUnifacGroup(j).getQComp() * Math.exp(-1.0 / phase.getTemperature() * calcaijdT(phase,
                getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
        sum2Mix +=
            getUnifacGroup(j).getQMix() * Math.exp(-1.0 / phase.getTemperature() * calcaijdT(phase,
                getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
      }
      sum3Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature()
          * calcaijdT(phase, getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Comp;
      sum3Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature()
          * calcaijdT(phase, getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Mix;
    }
    double tempGammaComp = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Comp) - sum3Comp);
    double tempGammaMix = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Mix) - sum3Mix);
    getUnifacGroup(k).setLnGammaCompdT(tempGammaComp);
    getUnifacGroup(k).setLnGammaMixdT(tempGammaMix);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    int initType = phase.getInitType();
    double lngammaCombinational = 0.0;
    double lngammaResidual = 0.0;
    double lngammaResidualdT = 0.0;
    dlngammadn = new double[numberOfComponents];
    dlngammadt = 0.0;
    ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();
    double temp1 = 0;
    double temp2 = 0;
    double suml = 0.0;
    double V = 0.0;

    double F = 0.0;
    for (int j = 0; j < numberOfComponents; j++) {
      temp1 += compArray[j].getx() * compArray[j].getR();
      temp2 += (compArray[j].getQ() * compArray[j].getx());
      suml += compArray[j].getx() * (10.0 / 2.0 * (compArray[j].getR() - compArray[j].getQ())
          - (compArray[j].getR() - 1.0));
    }

    V = this.getx() * this.getR() / temp1;
    F = this.getx() * this.getQ() / temp2;
    double li = 10.0 / 2.0 * (getR() - getQ()) - (getR() - 1.0);
    // System.out.println("li " + li);
    lngammaCombinational =
        (10.0 / 2.0 * getQ() * Math.log(F / V) + li - V / getx() * suml) + Math.log(V / getx());
    // System.out.println("ln gamma comb " + lngammaCombinational);

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      getUnifacGroup(i).calcXComp(this);
      // getUnifacGroup(i).calcXMix((PhaseGEUnifac) phase);
      getUnifacGroup(i).calcQComp(this);
      getUnifacGroup(i).calcQMix((PhaseGEUnifac) phase);
    }

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      calclnGammak(i, phase);
    }

    lngammaResidual = 0.0;
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      lngammaResidual += getUnifacGroup(i).getN()
          * (getUnifacGroup(i).getLnGammaMix() - getUnifacGroup(i).getLnGammaComp());
    }

    lngamma = lngammaResidual + lngammaCombinational;
    gamma = Math.exp(lngamma);

    if (initType > 1) {
      lngammaResidualdT = 0.0;
      for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
        calclnGammakdT(i, phase);
        lngammaResidualdT += getUnifacGroup(i).getN()
            * (getUnifacGroup(i).getLnGammaMixdT() - getUnifacGroup(i).getLnGammaCompdT());
      }
      dlngammadt = lngammaResidualdT;
    }

    return gamma;
  }
}
