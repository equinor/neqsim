/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */

package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentGEUniquac class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEUniquac extends ComponentGE {
  private static final long serialVersionUID = 1000;

  double r = 0;
  double q = 0;

  static Logger logger = LogManager.getLogger(ComponentGEUniquac.class);

  /**
   * <p>
   * Constructor for ComponentGEUniquac.
   * </p>
   *
   * @param component_name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compnumber a int
   */
  public ComponentGEUniquac(String component_name, double moles, double molesInPhase,
      int compnumber) {
    super(component_name, moles, molesInPhase, compnumber);
    if (!this.getClass().equals(ComponentGEUniquac.class)) {
      return;
    }
    if (component_name.contains("_PC")) {
      // double number = getMolarMass() / 0.014;
      // int intNumb = (int) Math.round(number) - 2;
      r = 1.0;
      q = 1.0;
      return;
    }
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet =
            database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + component_name + "'"));
        dataSet.next();
        dataSet.getClob("name");
      } catch (Exception ex) {
        dataSet.close();
        dataSet =
            database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + component_name + "'"));
        dataSet.next();
      }
      r = Double.parseDouble(dataSet.getString("rUNIQUAQ"));
      q = Double.parseDouble(dataSet.getString("qUNIQUAQ"));
    } catch (Exception ex) {
      logger.error(ex.toString());
    }
  }

  /**
   * <p>
   * fugcoef.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phasetype a int
   * @return a double
   */
  public double fugcoef(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, int phasetype) {
    fugacityCoefficient =
        (this.getGamma(phase, numberOfComponents, temperature, pressure, phasetype)
            * this.getAntoineVaporPressure(temperature) / pressure);
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, int phasetype, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    return 0.0;
  }

  /**
   * <p>
   * getGamma.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phasetype a int
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, int phasetype) {
    /*
     * double V = 0, F = 0, a, gammaC = 0, gammaR = 0, temp1 = 0, temp2 = 0, temp3=0, temp4 = 0,
     * temp5=0, gamma; int j, k;
     * 
     * 
     * ComponentGEInterface[] compArray = (ComponentGEInterface[]) phase.getcomponentArray();
     * 
     * 
     * for (j=0;j< numberOfComponents;j++){
     * 
     * temp1 = temp1 + compArray[j].getx()*((ComponentGEUniquac) compArray[j]).getr(); temp2 = temp2
     * + (((ComponentGEUniquac) compArray[j]).getq() * compArray[j].getx()); }
     * 
     * 
     * V = V + this.getr() / temp1; //System.out.println("V: " + V); F = F + this.getq() / temp2;
     * //System.out.println("F: " + F);
     * 
     * gammaC = 1 - V + Math.log(V) - 5 * this.getq() * (1 - V/F + Math.log(V/F));
     * 
     * // System.out.println("gammaC: " + gammaC);
     * 
     * temp1 = 0; temp2 = 0; temp3 = 0;
     * 
     * for (k=0;k< numberOfComponents;k++){ temp4 = 0; temp4 =
     * (intparam[compArray[k].getIndex()][this.getIndex()]/temperature); temp1 = temp1 +
     * compArray[k].getq() * compArray[k].getx() *
     * Math.exp(-intparam[compArray[k].getIndex()][this.getIndex()]/temperature); temp2 = temp2 +
     * compArray[k].getq() * compArray[k].getx(); }
     * 
     * for (k=0;k< numberOfComponents;k++){ temp5 = 0; for (j=0;j< numberOfComponents;j++){ temp5 =
     * temp5 + compArray[j].getq() * compArray[j].getx() *
     * Math.exp(-intparam[compArray[j].getIndex()][compArray[k].getIndex()]/ temperature);
     * 
     * }
     * 
     * temp3 = temp3 + (compArray[k].getq() * compArray[k].getx() * Math.exp(-
     * intparam[this.getIndex()][compArray[k].getIndex()]/temperature)) / temp5; }
     * 
     * gammaR = this.getq() * (1 - Math.log(temp1/temp2) - temp3);
     * 
     * //System.out.println("gammaR: " + gammaR);
     * 
     * gamma = Math.exp(gammaR + gammaC);
     * 
     * //System.out.println("comp: " + this.getIndex() + " gamma NRTL : " +gamma);
     * //System.out.println("gamma: " + gamma);
     */
    return gamma;
  }

  /**
   * <p>
   * fugcoefDiffPres.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phasetype a int
   * @return a double
   */
  public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, int phasetype) {
    dfugdp = (Math.log(fugcoef(phase, numberOfComponents, temperature, pressure + 0.01, phasetype))
        - Math.log(fugcoef(phase, numberOfComponents, temperature, pressure - 0.01, phasetype)))
        / 0.02;
    return dfugdp;
  }

  /**
   * <p>
   * fugcoefDiffTemp.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phasetype a int
   * @return a double
   */
  public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, int phasetype) {
    dfugdt = (Math.log(fugcoef(phase, numberOfComponents, temperature + 0.01, pressure, phasetype))
        - Math.log(fugcoef(phase, numberOfComponents, temperature - 0.01, pressure, phasetype)))
        / 0.02;
    return dfugdt;
  }
  /*
   * public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, int phasetype){ // NumericalDerivative deriv = new NumericalDerivative(); //
   * System.out.println("dfugdP : " + NumericalDerivative.fugcoefDiffPres(this, phase,
   * numberOfComponents, temperature, pressure, phasetype)); return
   * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents, temperature, pressure,
   * phasetype); }
   * 
   * public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, int phasetype){ NumericalDerivative deriv = new NumericalDerivative(); //
   * System.out.println("dfugdT : " + NumericalDerivative.fugcoefDiffTemp(this, phase,
   * numberOfComponents, temperature, pressure, phasetype)); return
   * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents, temperature, pressure,
   * phasetype);
   * 
   * }
   */

  /**
   * <p>
   * Getter for the field <code>r</code>.
   * </p>
   *
   * @return a double
   */
  public double getr() {
    return r;
  }

  /**
   * <p>
   * Getter for the field <code>q</code>.
   * </p>
   *
   * @return a double
   */
  public double getq() {
    return q;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGammadt() {
    return dlngammadt;
  }

  /** {@inheritDoc} */
  @Override
  public double getlnGammadn(int k) {
    return dlngammadn[k];
  }
}
