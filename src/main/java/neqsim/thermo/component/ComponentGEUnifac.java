/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */

package neqsim.thermo.component;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.atomelement.UNIFACgroup;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGEUnifac class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEUnifac extends ComponentGEUniquac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentGEUnifac.class);

  ArrayList<UNIFACgroup> unifacGroups = new ArrayList<UNIFACgroup>();
  UNIFACgroup[] unifacGroupsArray = new UNIFACgroup[0];
  double[] lnGammakComp = null;
  double[] lnGammakMix = null;
  double Q = 0.0;
  double R = 0.0;
  int numberOfUnifacSubGroups = 133;

  /**
   * <p>
   * Constructor for ComponentGEUnifac.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGEUnifac(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    if (!this.getClass().equals(ComponentGEUnifac.class)) {
      return;
    }
    if (name.contains("_PC")) {
      double number = getMolarMass() / 0.014;
      int intNumb = (int) Math.round(number) - 2;
      unifacGroups.add(new UNIFACgroup(1, 2));
      unifacGroups.add(new UNIFACgroup(2, intNumb));
      logger.info("adding unifac pseudo.." + intNumb);
      return;
    }

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + name + "'"));
        dataSet.next();
        dataSet.getClob("name");
      } catch (Exception ex) {
        dataSet.close();
        dataSet = database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + name + "'"));
        dataSet.next();
      }

      for (int p = 1; p < numberOfUnifacSubGroups; p++) {
        int temp = Integer.parseInt(dataSet.getString("sub" + Integer.toString(p)));
        if (temp > 0) {
          unifacGroups.add(new UNIFACgroup(p, temp));
          // System.out.println("comp " + name + " adding UNIFAC group " + p);
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * addUNIFACgroup.
   * </p>
   *
   * @param p a int
   * @param n a int
   */
  public void addUNIFACgroup(int p, int n) {
    unifacGroups.add(new UNIFACgroup(p, n));
    unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
  }

  /**
   * <p>
   * getQ.
   * </p>
   *
   * @return a double
   */
  public double getQ() {
    double sum = 0.0;

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      sum += this.getUnifacGroup(i).getQ() * getUnifacGroup(i).getN();
    }
    Q = sum;
    return sum;
  }

  /**
   * Getter for property R.
   *
   * @return Value of property R.
   */
  public double getR() {
    double sum = 0.0;

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      sum += this.getUnifacGroup(i).getR() * getUnifacGroup(i).getN();
    }
    R = sum;
    return sum;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    fugacityCoefficient = (this.getGamma(phase, numberOfComponents, temperature, pressure, pt)
        * this.getAntoineVaporPressure(temperature) / pressure);
    return fugacityCoefficient;
  }

  /**
   * <p>
   * calclnGammak.
   * </p>
   *
   * @param k a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calclnGammak(int k, PhaseInterface phase) {
    double sum1Comp = 0.0;
    double sum1Mix = 0.0;
    double sum3Comp = 0.0;

    double sum3Mix = 0.0;
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      sum1Comp += getUnifacGroup(i).getQComp()
          * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
              .getAij(getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      sum1Mix += getUnifacGroup(i).getQMix()
          * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
              .getAij(getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
      double sum2Comp = 0.0;
      double sum2Mix = 0.0;
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        sum2Comp += getUnifacGroup(j).getQComp()
            * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                .getAij(getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
        sum2Mix += getUnifacGroup(j).getQMix()
            * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                .getAij(getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
      }
      sum3Comp += getUnifacGroup(i).getQComp()
          * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
              .getAij(getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Comp;
      sum3Mix += getUnifacGroup(i).getQMix()
          * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
              .getAij(getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex()))
          / sum2Mix;
    }
    double tempGammaComp = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Comp) - sum3Comp);
    double tempGammaMix = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Mix) - sum3Mix);
    getUnifacGroup(k).setLnGammaComp(tempGammaComp);
    getUnifacGroup(k).setLnGammaMix(tempGammaMix);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    double lngammaCombinational = 0.0;
    double lngammaResidual = 0.0;
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
        Math.log(V / getx()) + 10.0 / 2.0 * getQ() * Math.log(F / V) + li - V / getx() * suml;
    // System.out.println("ln gamma comb " + lngammaCombinational);

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      getUnifacGroup(i).calcXComp(this);
      // getUnifacGroup(i).calcXMix((PhaseGEUnifac) phase);
      getUnifacGroup(i).calcQComp(this);
      getUnifacGroup(i).calcQMix((PhaseGEUnifac) phase);
    }
    lnGammakComp = new double[getNumberOfUNIFACgroups()];
    lnGammakMix = new double[getNumberOfUNIFACgroups()];
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      calclnGammak(i, phase);
    }

    lngammaResidual = 0.0;
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      lngammaResidual += getUnifacGroup(i).getN()
          * (getUnifacGroup(i).getLnGammaMix() - getUnifacGroup(i).getLnGammaComp());
    }
    lngamma = lngammaResidual + lngammaCombinational;
    // System.out.println("gamma " + Math.exp(lngamma));
    gamma = Math.exp(lngamma);

    return gamma;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    dfugdp = (Math.log(fugcoef(phase, numberOfComponents, temperature, pressure + 0.01, pt))
        - Math.log(fugcoef(phase, numberOfComponents, temperature, pressure - 0.01, pt))) / 0.02;
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    dfugdt = (Math.log(fugcoef(phase, numberOfComponents, temperature + 0.01, pressure, pt))
        - Math.log(fugcoef(phase, numberOfComponents, temperature - 0.01, pressure, pt))) / 0.02;
    return dfugdt;
  }

  /*
   * public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, PhaseType pt){ // NumericalDerivative deriv = new NumericalDerivative(); //
   * System.out.println("dfugdP : " + NumericalDerivative.fugcoefDiffPres(this, phase,
   * numberOfComponents, temperature, pressure, pt)); return
   * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents, temperature, pressure,
   * pt); }
   *
   * public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, PhaseType pt){ NumericalDerivative deriv = new NumericalDerivative(); //
   * System.out.println("dfugdT : " + NumericalDerivative.fugcoefDiffTemp(this, phase,
   * numberOfComponents, temperature, pressure, pt)); return
   * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents, temperature, pressure,
   * pt); }
   */

  /**
   * Getter for property unifacGroups.
   *
   * @return an ArrayList of {@link neqsim.thermo.atomelement.UNIFACgroup} objects. Value of
   *         property unifacGroups.
   */
  public ArrayList<UNIFACgroup> getUnifacGroups2() {
    return unifacGroups;
  }

  /**
   * <p>
   * Getter for the field <code>unifacGroups</code>.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.atomelement.UNIFACgroup} objects
   */
  public UNIFACgroup[] getUnifacGroups() {
    return unifacGroupsArray;
  }

  /**
   * <p>
   * getUnifacGroup2.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.thermo.atomelement.UNIFACgroup} object
   */
  public neqsim.thermo.atomelement.UNIFACgroup getUnifacGroup2(int i) {
    return unifacGroups.get(i);
  }

  /**
   * <p>
   * getUnifacGroup.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.thermo.atomelement.UNIFACgroup} object
   */
  public neqsim.thermo.atomelement.UNIFACgroup getUnifacGroup(int i) {
    return unifacGroupsArray[i];
  }

  /**
   * Setter for property unifacGroups.
   *
   * @param unifacGroups New value of property unifacGroups.
   */
  public void setUnifacGroups(ArrayList<UNIFACgroup> unifacGroups) {
    this.unifacGroups = unifacGroups;
    unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
  }

  /**
   * <p>
   * getNumberOfUNIFACgroups.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfUNIFACgroups() {
    return unifacGroups.size();
  }

  /**
   * Setter for property Q.
   *
   * @param Q New value of property Q.
   */
  public void setQ(double Q) {
    this.Q = Q;
  }

  /**
   * Setter for property R.
   *
   * @param R New value of property R.
   */
  public void setR(double R) {
    this.R = R;
  }
}
