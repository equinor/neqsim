package neqsim.thermo.characterization;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PlusCharacterize class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PlusCharacterize implements java.io.Serializable, CharacteriseInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PlusCharacterize.class);

  double[] TBPfractions = null;
  boolean firsttime = true;
  double MPlus = 300.0;
  double zPlus = 0.3;
  double densPlus = 0.98;
  private double densLastTBP = 0.78;
  int[] carbonNumberVector = null;
  protected boolean pseudocomponents = true;
  int firstPlusFractionNumber = 1;
  int lastPlusFractionNumber = 80;
  int numberOfPseudocomponents = 5; // (lastPlusFractionNumber-firstPlusFractionNumber)*50;
  int length = 0;
  double[] coefs = {4.4660105006, -1.1266303727, 0.80, 0.0408709562};
  double[] SRKcoefs = {4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785};
  double[] PRcoefs = {4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785};
  double[] plusCoefs = {0.0007774204804, -0.02390179};
  SystemInterface system = null;

  /**
   * <p>
   * Constructor for PlusCharacterize.
   * </p>
   */
  public PlusCharacterize() {}

  /**
   * <p>
   * Constructor for PlusCharacterize.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public PlusCharacterize(SystemInterface system) {
    this.system = system;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasPlusFraction() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
        return true;
      }
    }
    return false;
  }

  /**
   * <p>
   * setHeavyTBPtoPlus.
   * </p>
   */
  public void setHeavyTBPtoPlus() {
    int plusCompNumber = 0;
    int compNumber = 0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      try {
        if (system.getPhase(0).getComponent(i).isIsTBPfraction()) {
          Integer firstPlusNumber = Integer.valueOf(0);
          if (system.getPhase(0).getComponent(i).getComponentName().substring(3, 4).equals("_")) {
            firstPlusNumber = Integer
                .valueOf(system.getPhase(0).getComponent(i).getComponentName().substring(1, 3));
          } else {
            firstPlusNumber = Integer
                .valueOf(system.getPhase(0).getComponent(i).getComponentName().substring(1, 2));
          }
          if (plusCompNumber < firstPlusNumber.intValue()) {
            plusCompNumber = firstPlusNumber.intValue();
            compNumber = i;
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      system.getPhase(i).getComponent(compNumber).setIsTBPfraction(false);
      system.getPhase(i).getComponent(compNumber).setIsPlusFraction(true);

      MPlus = system.getPhase(i).getComponent(compNumber).getMolarMass();
      zPlus = system.getPhase(i).getComponent(compNumber).getz();
      densPlus = system.getPhase(i).getComponent(compNumber).getNormalLiquidDensity();
    }
    coefs[2] = system.getPhase(0).getComponent(compNumber - 1).getNormalLiquidDensity() + 0.03;
    densLastTBP = system.getPhase(0).getComponent(compNumber - 1).getNormalLiquidDensity();
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      try {
        if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
          Integer firstPlusNumber = Integer.valueOf(0);
          if (system.getPhase(0).getComponent(i).getComponentName().substring(3, 4).equals("_")) {
            firstPlusNumber = Integer
                .valueOf(system.getPhase(0).getComponent(i).getComponentName().substring(1, 3));
          } else {
            firstPlusNumber = Integer
                .valueOf(system.getPhase(0).getComponent(i).getComponentName().substring(1, 2));
          }
          if (firstPlusFractionNumber < firstPlusNumber.intValue()) {
            firstPlusFractionNumber = firstPlusNumber.intValue();
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    logger.info("first plus fraction number " + firstPlusFractionNumber);

    // NewtonSolveABCDplus solver = new NewtonSolveABCDplus(system, this);
    // NewtonSolveCDplus solver2 = new NewtonSolveCDplus(system, this);
    // solver.solve();
    // solver2.solve();

    // NewtonSolveABCD2 solver3 = new NewtonSolveABCD2(system, this);
    // solver3.solve();
  }

  /** {@inheritDoc} */
  @Override
  public double[] getCoefs() {
    return this.coefs;
  }

  /** {@inheritDoc} */
  @Override
  public double getCoef(int i) {
    return this.coefs[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setCoefs(double[] coefs) {
    System.arraycopy(coefs, 0, this.coefs, 0, coefs.length);

    if (firsttime) {
      if (coefs.length == 3) {
        double Dtot = 0.0;
        for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
          Dtot += (getDensPlus() - this.getCoef(2)) / Math.log(i); // (this.getCoef(2)+this.getCoef(3)*Math.log(i)-this.getCoef(2))/Math.log(i);
        }
        double lengthPlus = this.getLastPlusFractionNumber() - this.getFirstPlusFractionNumber();
        logger.info("length plus " + lengthPlus);
        Dtot /= lengthPlus;
        logger.info("D " + Dtot);
        this.coefs[3] = Dtot;
      }
      firsttime = false;
    }

    double mSum = 0.0;
    double densSum = 0.0;
    int iter = 0;
    do {
      iter++;
      mSum = 0.0;
      densSum = 0.0;
      for (int i = this.getFirstPlusFractionNumber(); i < this.getLastPlusFractionNumber(); i++) {
        double ztemp = Math.exp(this.getCoef(0) + this.getCoef(1) * (i));
        double M = PVTsimMolarMass[i - 6] / 1000.0;
        double dens = this.getCoef(2) + this.getCoef(3) * Math.log(i);
        mSum += ztemp * M;
        densSum += (ztemp * M / dens);
      }
      densSum = mSum / densSum;

      this.coefs[3] += 1.0 * (densPlus - densSum) / densSum * this.coefs[3];
      // System.out.println("coef " + this.coefs[3]);
    } while (Math.abs(densPlus - densSum) > 1e-6 && iter < 1000);
  }

  /** {@inheritDoc} */
  @Override
  public void setCoefs(double coef, int i) {
    this.coefs[i] = coef;
  }

  /**
   * Getter for property length.
   *
   * @return Value of property length.
   */
  public int getLength() {
    return length;
  }

  /** {@inheritDoc} */
  @Override
  public void generatePlusFractions(int start, int end, double zplus, double Mplus) {}

  /** {@inheritDoc} */
  @Override
  public void addHeavyEnd() {}

  /** {@inheritDoc} */
  @Override
  public void generateTBPFractions() {}

  /** {@inheritDoc} */
  @Override
  public void addCharacterizedPlusFraction() {
    if (!pseudocomponents) {
      numberOfPseudocomponents = getLastPlusFractionNumber() - getFirstPlusFractionNumber() + 1;
    }

    double[] zPlus = new double[numberOfPseudocomponents];
    double[] MPlus = new double[numberOfPseudocomponents];

    double weightFrac = 0.0;
    double weightTot = 0.0;

    for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
      weightTot += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
    }

    double meanWeightFrac = weightTot / (numberOfPseudocomponents + 0.000001);
    zPlus = new double[numberOfPseudocomponents];
    MPlus = new double[numberOfPseudocomponents];
    int k = 0;
    int firstPS = firstPlusFractionNumber;
    double Maverage = 0.0;

    double denstemp1 = 0.0;
    double denstemp2 = 0.0;
    double totalNumberOfMoles = system.getNumberOfMoles();

    for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
      zPlus[k] += Math.exp(getCoef(0) + getCoef(1) * i);
      MPlus[k] += PVTsimMolarMass[i - 6] / 1000.0;
      denstemp1 += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
      denstemp2 += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0
          / (getCoef(2) + getCoef(3) * Math.log(i));
      // System.out.println("dens " + denstemp1/denstemp2);
      Maverage += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
      weightFrac += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
      // System.out.println("weigth " + weightFrac + " i" + i);
      if (weightFrac >= meanWeightFrac || !pseudocomponents
          || i == getLastPlusFractionNumber() - 1) {
        String name = (i == firstPS) ? "PC" + Integer.toString(firstPS)
            : "PC" + Integer.toString(firstPS) + "-" + Integer.toString(i);
        system.addTBPfraction(name, totalNumberOfMoles * zPlus[k], Maverage / zPlus[k],
            denstemp1 / denstemp2);
        denstemp1 = 0.0;
        denstemp2 = 0.0;
        weightFrac = 0.0;
        Maverage = 0.0;
        k++;
        firstPS = i + 1;
      }
    }

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
        system.removeComponent(system.getPhase(0).getComponent(i).getName());
        break;
      }
    }
  }

  /**
   * <p>
   * addPseudoTBPfraction.
   * </p>
   *
   * @param start a int
   * @param end a int
   */
  public void addPseudoTBPfraction(int start, int end) {}

  /**
   * Getter for property carbonNumberVector.
   *
   * @return Value of property carbonNumberVector.
   */
  public int[] getCarbonNumberVector() {
    return this.carbonNumberVector;
  }

  /**
   * Setter for property carbonNumberVector.
   *
   * @param carbonNumberVector New value of property carbonNumberVector.
   */
  public void setCarbonNumberVector(int[] carbonNumberVector) {
    this.carbonNumberVector = carbonNumberVector;
  }

  /** {@inheritDoc} */
  @Override
  public int getFirstPlusFractionNumber() {
    return firstPlusFractionNumber;
  }

  /** {@inheritDoc} */
  @Override
  public int getLastPlusFractionNumber() {
    return lastPlusFractionNumber;
  }

  /**
   * Setter for property firstPlusFractionNumber.
   *
   * @param firstPlusFractionNumber New value of property firstPlusFractionNumber.
   */
  public void setFirstPlusFractionNumber(int firstPlusFractionNumber) {
    this.firstPlusFractionNumber = firstPlusFractionNumber;
  }

  /**
   * Getter for property startPlus.
   *
   * @return Value of property startPlus.
   */
  public int getStartPlus() {
    return firstPlusFractionNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double getMPlus() {
    return MPlus;
  }

  /** {@inheritDoc} */
  @Override
  public void setMPlus(double MPlus) {
    this.MPlus = MPlus;
  }

  /** {@inheritDoc} */
  @Override
  public double getZPlus() {
    return zPlus;
  }

  /** {@inheritDoc} */
  @Override
  public void setZPlus(double zPlus) {
    this.zPlus = zPlus;
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

  /**
   * Setter for property densPlus.
   *
   * @param densPlus New value of property densPlus.
   */
  public void setDensPlus(double densPlus) {
    this.densPlus = densPlus;
  }

  /** {@inheritDoc} */
  @Override
  public boolean groupTBPfractions() {
    return true;
  }

  /**
   * Getter for property numberOfPseudocomponents.
   *
   * @return Value of property numberOfPseudocomponents.
   */
  public int getNumberOfPseudocomponents() {
    return numberOfPseudocomponents;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfPseudocomponents(int numberOfPseudocomponents) {
    this.numberOfPseudocomponents = numberOfPseudocomponents;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isPseudocomponents() {
    return pseudocomponents;
  }

  /** {@inheritDoc} */
  @Override
  public void setPseudocomponents(boolean pseudocomponents) {
    this.pseudocomponents = pseudocomponents;
  }

  /** {@inheritDoc} */
  @Override
  public void removeTBPfraction() {
    ArrayList<String> list = new ArrayList<String>();
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      double boilpoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
      if (boilpoint >= 273.15 + 69.0) {
        list.add(system.getPhase(0).getComponent(i).getName());
      }
    }

    for (int i = 0; i < list.size(); i++) {
      try {
        system.removeComponent(list.get(i));
        logger.info("removing " + list.get(i));
      } catch (Exception ex) {
        logger.error("not able to remove " + list.get(i), ex);
        // return;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addTBPFractions() {}

  /** {@inheritDoc} */
  @Override
  public double getDensLastTBP() {
    return densLastTBP;
  }

  /** {@inheritDoc} */
  @Override
  public void setDensLastTBP(double densLastTBP) {
    this.densLastTBP = densLastTBP;
  }

  /**
   * <p>
   * characterizePlusFraction.
   * </p>
   */
  public void characterizePlusFraction() {
    system.init(0);
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
        MPlus = system.getPhase(0).getComponent(i).getMolarMass();
        zPlus = system.getPhase(0).getComponent(i).getz();
        densPlus = system.getPhase(0).getComponent(i).getNormalLiquidDensity();
      }
    }

    coefs[0] = 0.1;
    coefs[1] = Math.log(zPlus) / getFirstPlusFractionNumber();
    solve();
  }
}
