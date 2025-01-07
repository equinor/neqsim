/*
 * TBPCharacterize.java
 *
 * Created on 3. januar 2003, 10:03
 */

package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TBPCharacterize class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TBPCharacterize extends PlusCharacterize {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  int startPlus = 7;
  int endPlus = 20;
  double[] calcTBPfractions = null;
  double[] TBPdens = null;
  double[] TBPmoles = null;
  double[] TBPdensDenom = null;
  double[] TBP_Mnom = null;
  double[] TBP_M = null;

  /**
   * <p>
   * Constructor for TBPCharacterize.
   * </p>
   */
  public TBPCharacterize() {}

  /**
   * <p>
   * Constructor for TBPCharacterize.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public TBPCharacterize(SystemInterface system) {
    this.system = system;
    firstPlusFractionNumber = 7;
    lastPlusFractionNumber = 40;
  }

  /** {@inheritDoc} */
  @Override
  public boolean groupTBPfractions() {
    system.init(0);
    double old = 0;

    TBPfractions = new double[50];
    TBP_Mnom = new double[50];
    TBPdensDenom = new double[50];
    TBPmoles = new double[50];

    int numb = 49;

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      // if (system.getPhase(0).getComponent(i).getComponentType().equals("HC")) {
      double boilpoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint("C");

      if (boilpoint >= 331.0) {
        numb = 13;
      } else if (boilpoint >= 317.0) {
        numb = 12;
      } else if (boilpoint >= 303.0) {
        numb = 11;
      } else if (boilpoint >= 287.0) {
        numb = 10;
      } else if (boilpoint >= 271.1) {
        numb = 9;
      } else if (boilpoint >= 253.9) {
        numb = 8;
      } else if (boilpoint >= 235.9) {
        numb = 7;
      } else if (boilpoint >= 216.8) {
        numb = 6;
      } else if (boilpoint >= 196.4) {
        numb = 5;
      } else if (boilpoint >= 174.6) {
        numb = 4;
      } else if (boilpoint >= 151.3) {
        numb = 3;
      } else if (boilpoint >= 126.1) {
        numb = 2;
      } else if (boilpoint >= 98.9) {
        numb = 1;
      } else if (boilpoint >= 69.2) {
        numb = 0;
      } else {
        numb = 49;
      }
      if (boilpoint > old) {
        length = numb + 1;
        old = boilpoint;
      }
      TBPmoles[numb] += system.getPhase(0).getComponent(i).getNumberOfmoles();
      TBPfractions[numb] += system.getPhase(0).getComponent(i).getz();
      TBP_Mnom[numb] += system.getPhase(0).getComponent(i).getz()
          * system.getPhase(0).getComponent(i).getMolarMass();
      TBPdensDenom[numb] += system.getPhase(0).getComponent(i).getz()
          * system.getPhase(0).getComponent(i).getMolarMass()
          / system.getPhase(0).getComponent(i).getNormalLiquidDensity();
      // }
    }
    TBPdens = new double[length];
    TBP_M = new double[length];
    carbonNumberVector = new int[length];

    for (int i = 0; i < length; i++) {
      TBPdens[i] = TBP_Mnom[i] / TBPdensDenom[i];
      TBP_M[i] = TBP_Mnom[i] / TBPfractions[i];
      carbonNumberVector[i] = getFirstPlusFractionNumber() + i;
    }

    return length > 1;
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPFractions() {
    for (int i = 0; i < TBPdens.length; i++) {
      // System.out.println("Mi " + TBP_M[i] + " dens " + TBPdens[i]);
      system.addTBPfraction("C" + Integer.toString(carbonNumberVector[i]), TBPmoles[i], TBP_M[i],
          TBPdens[i]);
    }
  }

  /**
   * <p>
   * saveCharacterizedFluid.
   * </p>
   *
   * @return a boolean
   */
  public boolean saveCharacterizedFluid() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      // double boilpoint =
      // system.getPhase(0).getComponent(i).getNormalBoilingPoint();
    }
    return true;
  }

  /**
   * Getter for property TBPfractions.
   *
   * @return Value of property TBPfractions.
   */
  public double[] getTBPfractions() {
    return this.TBPfractions;
  }

  /** {@inheritDoc} */
  @Override
  public void setCoefs(double coef, int i) {
    this.coefs[i] = coef;
  }

  /**
   * <p>
   * getTBPfractions.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getTBPfractions(int i) {
    return this.TBPfractions[i];
  }

  /**
   * Setter for property TBPfractions.
   *
   * @param TBPfractions New value of property TBPfractions.
   */
  public void setTBPfractions(double[] TBPfractions) {
    this.TBPfractions = TBPfractions;
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    NewtonSolveABCD solver = new NewtonSolveABCD(system, this);
    solver.solve();
  }

  /**
   * <p>
   * solveAB.
   * </p>
   */
  public void solveAB() {
    NewtonSolveAB solver = new NewtonSolveAB(system, this);
    solver.solve();
  }

  /** {@inheritDoc} */
  @Override
  public void setCoefs(double[] coefs) {
    this.coefs = coefs;
  }

  /**
   * Getter for property TBPdens.
   *
   * @return Value of property TBPdens.
   */
  public double[] getTBPdens() {
    return this.TBPdens;
  }

  /**
   * <p>
   * getTBPdens.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getTBPdens(int i) {
    return this.TBPdens[i];
  }

  /**
   * Setter for property TBPdens.
   *
   * @param TBPdens New value of property TBPdens.
   */
  public void setTBPdens(double[] TBPdens) {
    this.TBPdens = TBPdens;
  }

  /** {@inheritDoc} */
  @Override
  public int getLength() {
    return length;
  }

  /**
   * Getter for property TBP_M.
   *
   * @return Value of property TBP_M.
   */
  public double[] getTBP_M() {
    return this.TBP_M;
  }

  /**
   * Setter for property TBP_M.
   *
   * @param TBP_M New value of property TBP_M.
   */
  public void setTBP_M(double[] TBP_M) {
    this.TBP_M = TBP_M;
  }

  /** {@inheritDoc} */
  @Override
  public int[] getCarbonNumberVector() {
    return this.carbonNumberVector;
  }

  /** {@inheritDoc} */
  @Override
  public void setCarbonNumberVector(int[] carbonNumberVector) {
    this.carbonNumberVector = carbonNumberVector;
  }

  /**
   * Getter for property calcTBPfractions.
   *
   * @return Value of property calcTBPfractions.
   */
  public double[] getCalcTBPfractions() {
    return this.calcTBPfractions;
  }

  /**
   * Setter for property calcTBPfractions.
   *
   * @param calcTBPfractions New value of property calcTBPfractions.
   */
  public void setCalcTBPfractions(double[] calcTBPfractions) {
    this.calcTBPfractions = calcTBPfractions;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getPlusCoefs() {
    return this.plusCoefs;
  }

  /** {@inheritDoc} */
  @Override
  public double getPlusCoefs(int i) {
    return this.plusCoefs[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setPlusCoefs(double[] plusCoefs) {
    this.plusCoefs = plusCoefs;
  }

  /** {@inheritDoc} */
  @Override
  public double getDensPlus() {
    return densPlus;
  }

  /** {@inheritDoc} */
  @Override
  public void setDensPlus(double densPlus) {
    this.densPlus = densPlus;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isPseudocomponents() {
    return false;
  }

  /**
   * <p>
   * addPlusFraction.
   * </p>
   */
  public void addPlusFraction() {}

  /** {@inheritDoc} */
  @Override
  public void addHeavyEnd() {
    int old = getFirstPlusFractionNumber();
    setFirstPlusFractionNumber(length + 7);
    generateTBPFractions();
    setFirstPlusFractionNumber(old);
  }
}
