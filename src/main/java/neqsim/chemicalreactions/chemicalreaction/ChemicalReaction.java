/*
 * ChemicalReaction.java
 *
 * Created on 4. februar 2001, 15:32
 */

package neqsim.chemicalreactions.chemicalreaction;

import Jama.Matrix;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * ChemicalReaction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChemicalReaction extends NamedBaseClass
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  String[] names;
  String[] reactantNames;
  String[] productNames;
  double[] stocCoefs = new double[4];
  double[] reacCoefs;
  double[] prodCoefs;
  double[] moles;
  boolean shiftSignK = false;
  double[] K = new double[4];
  double rateFactor = 0;
  double activationEnergy;
  double refT;
  double G = 0;
  double lnK = 0;
  int numberOfReactants = 0;

  /**
   * <p>
   * Constructor for ChemicalReaction.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param names an array of {@link java.lang.String} objects
   * @param stocCoefs an array of type double
   * @param K an array of type double
   * @param r a double
   * @param activationEnergy a double
   * @param refT a double
   */
  public ChemicalReaction(String name, String[] names, double[] stocCoefs, double[] K, double r,
      double activationEnergy, double refT) {
    /*
     * this.names = names; this.stocCoefs = stocCoefs; this.K = K;
     */
    super(name);
    this.names = new String[names.length];
    this.moles = new double[names.length];
    this.stocCoefs = new double[stocCoefs.length];
    this.K = new double[K.length];
    this.rateFactor = r;
    this.refT = refT;
    this.activationEnergy = activationEnergy;

    System.arraycopy(names, 0, this.names, 0, names.length);
    System.arraycopy(stocCoefs, 0, this.stocCoefs, 0, stocCoefs.length);
    System.arraycopy(K, 0, this.K, 0, K.length);
    numberOfReactants = 0;

    for (int i = 0; i < names.length; i++) {
      // System.out.println("stoc coef: " + this.stocCoefs[i]);
      if (stocCoefs[i] < 0) {
        numberOfReactants++;
      }
    }

    reactantNames = new String[numberOfReactants];
    productNames = new String[names.length - numberOfReactants];
    // this.reacCoefs = new double[numberOfReactants];
    // this.prodCoefs = new double[names.length - numberOfReactants];
    int k = 0;
    int l = 0;
    for (int i = 0; i < names.length; i++) {
      if (stocCoefs[i] < 0) {
        // reacCoefs[k] = stocCoefs[i];
        reactantNames[k++] = this.names[i];
      } else {
        // prodCoefs[l] = stocCoefs[i];
        productNames[l++] = this.names[i];
      }
    }
  }

  /**
   * <p>
   * Getter for the field <code>reactantNames</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getReactantNames() {
    return reactantNames;
  }

  /**
   * reaction constant at reference temperature.
   *
   * @return a double
   */
  public double getRateFactor() {
    return rateFactor;
  }

  /**
   * <p>
   * Getter for the field <code>rateFactor</code>.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getRateFactor(PhaseInterface phase) {
    // return rateFactor * Math.exp(-activationEnergy/R*(1.0/phase.getTemperature()
    // - 1.0/refT));
    return 2.576e9 * Math.exp(-6024.0 / phase.getTemperature()) / 1000.0;
  }

  /**
   * <p>
   * Getter for the field <code>stocCoefs</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getStocCoefs() {
    return this.stocCoefs;
  }

  /**
   * <p>
   * Getter for the field <code>productNames</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getProductNames() {
    return productNames;
  }

  /**
   * <p>
   * Getter for the field <code>names</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getNames() {
    return names;
  }

  /**
   * <p>
   * calcKx.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double calcKx(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    double kx = 1.0;
    for (int i = 0; i < names.length; i++) {
      // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
      kx *= Math.pow(system.getPhase(phaseNumb).getComponent(names[i]).getx(), stocCoefs[i]);
    }
    return kx;
  }

  /**
   * <p>
   * calcKgamma.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double calcKgamma(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    double kgamma = 1.0;
    for (int i = 0; i < names.length; i++) {
      // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
      if (system.getPhase(phaseNumb).getComponent(names[i]).calcActivity()) {
        kgamma *= Math.pow(
            system.getPhase(phaseNumb).getActivityCoefficient(
                system.getPhase(phaseNumb).getComponent(names[i]).getComponentNumber(),
                system.getPhase(phaseNumb).getComponent("water").getComponentNumber()),
            stocCoefs[i]);
      }
    }
    return kgamma;
  }

  /**
   * <p>
   * getSaturationRatio.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double getSaturationRatio(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    double ksp = 1.0;
    for (int i = 0; i < names.length; i++) {
      // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
      if (stocCoefs[i] < 0) {
        ksp *= Math.pow(system.getPhase(phaseNumb).getComponent(names[i]).getx(), -stocCoefs[i]);
      }
    }
    ksp /= (getK(system.getPhase(phaseNumb)));
    return ksp;
  }

  /**
   * <p>
   * calcK.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double calcK(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    return calcKx(system, phaseNumb) * calcKgamma(system, phaseNumb);
  }

  /**
   * Generaters initial estimates for the molenumbers.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param Amatrix an array of type double
   * @param chemRefPot an array of type double
   */
  public void initMoleNumbers(PhaseInterface phase, ComponentInterface[] components,
      double[][] Amatrix, double[] chemRefPot) {
    Matrix tempAmatrix = new Matrix(Amatrix.length, names.length);
    Matrix tempNmatrix = new Matrix(names.length, 1);
    Matrix tempRefPotmatrix = new Matrix(names.length, 1);

    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < components.length; j++) {
        // System.out.println("names: " + names[i] + " " +
        // system.getPhases()[0].getComponentName(j));
        if (this.names[i].equals(components[j].getName())) {
          for (int k = 0; k < Amatrix.length; k++) {
            tempAmatrix.set(k, i, Amatrix[k][j]);
          }
          tempNmatrix.set(i, 0, components[j].getNumberOfMolesInPhase());
          tempRefPotmatrix.set(i, 0, chemRefPot[j]);
        }
      }
    }

    // Matrix tempBmatrix = tempAmatrix.times(tempNmatrix);

    // System.out.println("atemp: ");
    // tempAmatrix.print(10,2);
    // tempNmatrix.print(10,2);
    // tempBmatrix.print(10,2);
    // tempRefPotmatrix.print(10,2);

    // set AprodMetrix and setAreacMatrix

    Matrix tempAProdmatrix = new Matrix(Amatrix.length, productNames.length);
    Matrix tempAReacmatrix = new Matrix(Amatrix.length, reactantNames.length);
    // Matrix tempNProdmatrix = new Matrix(Amatrix.length, 1);
    // Matrix tempNReacmatrix = new Matrix(Amatrix.length, 1);

    for (int i = 0; i < Amatrix.length; i++) {
      for (int k = 0; k < reactantNames.length; k++) {
        tempAReacmatrix.set(i, k, tempAmatrix.get(i, k));
      }
    }

    for (int i = 0; i < Amatrix.length; i++) {
      for (int k = 0; k < productNames.length; k++) {
        tempAProdmatrix.set(i, k, tempAmatrix.get(i, names.length - 1 - k));
      }
    }

    // Matrix tempNProdmatrix = tempAProdmatrix.solve(tempBmatrix);
    // Matrix tempNReacmatrix = tempAReacmatrix.solve(tempBmatrix);

    // System.out.println("btemp: ");
    // tempNProdmatrix.print(10,2);
    // tempNReacmatrix.print(10,2);
    // tempAProdmatrix.print(10,2);
    // tempAReacmatrix.print(10,2);
  }

  /**
   * <p>
   * init.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void init(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    lnK = K[0] + K[1] / (temperature) + K[2] * Math.log(temperature) + K[3] * temperature;
    // System.out.println("K: " + Math.exp(lnK));
    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < phase.getNumberOfComponents(); j++) {
        if (this.names[i].equals(phase.getComponentName(j))) {
          moles[i] = phase.getComponent(j).getNumberOfMolesInPhase();
        }
      }
    }

    double cK = lnK;
    for (int i = 0; i < names.length; i++) {
      cK -= Math.log(moles[i] / phase.getNumberOfMolesInPhase()) * stocCoefs[i];
    }

    if (Math.exp(cK) > 1) {
      for (int i = 0; i < stocCoefs.length; i++) {
        stocCoefs[i] = -stocCoefs[i];
      }
      lnK = -lnK;
      shiftSignK = !shiftSignK;
    }
  }

  /**
   * <p>
   * checkK.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void checkK(SystemInterface system) {
    // double cK=Math.log(getK(system.getTemperature()));
    // for(int i=0;i<names.length;i++){
    // // cK -=
    // Math.log(moles[i]/system.getPhases()[0].getNumberOfMolesInPhase())*stocCoefs[i];
    // }
    // System.out.println("ck: " +cK);
  }

  /**
   * <p>
   * reactantsContains.
   * </p>
   *
   * @param names an array of {@link java.lang.String} objects
   * @return a boolean
   */
  public boolean reactantsContains(String[] names) {
    boolean test = false;
    /*
     * if(reactantNames.length>names.length || productNames.length>names.length ){ return false; }
     */

    for (int j = 0; j < reactantNames.length; j++) {
      for (int i = 0; i < names.length; i++) {
        if (names[i].equals(reactantNames[j])) {
          test = true;
          break;
        } else {
          test = false;
        }
      }
      if (!test) {
        break;
      }
    }

    if (!test) {
      for (int j = 0; j < productNames.length; j++) {
        for (int i = 0; i < names.length; i++) {
          if (names[i].equals(productNames[j])) {
            test = true;
            break;
          } else {
            test = false;
          }
        }
        if (!test) {
          break;
        }
      }
    }

    return test;
  }

  /**
   * Setter for property rateFactor.
   *
   * @param rateFactor New value of property rateFactor.
   */
  public void setRateFactor(double rateFactor) {
    this.rateFactor = rateFactor;
  }

  /**
   * Getter for property activationEnergy.
   *
   * @return Value of property activationEnergy.
   */
  public double getActivationEnergy() {
    return activationEnergy;
  }

  /**
   * Setter for property activationEnergy.
   *
   * @param activationEnergy New value of property activationEnergy.
   */
  public void setActivationEnergy(double activationEnergy) {
    this.activationEnergy = activationEnergy;
  }

  /**
   * Getter for property reactionHeat. Van't HOffs equation dh = d lnK/dT * R * T^2
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return Value of property reactionHeat.
   */
  public double getReactionHeat(PhaseInterface phase) {
    double diffKt =
        -K[1] / Math.pow(phase.getTemperature(), 2.0) + K[2] / phase.getTemperature() + K[3];
    double sign = (shiftSignK = true) ? -1.0 : 1.0;
    return sign * diffKt * Math.pow(phase.getTemperature(), 2.0) * R;
  }

  /**
   * Getter for property k.
   *
   * @return Value of property k.
   */
  public double[] getK() {
    return this.K;
  }

  /**
   * <p>
   * getK.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getK(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    lnK = K[0] + K[1] / (temperature) + K[2] * Math.log(temperature) + K[3] * temperature;
    if (shiftSignK) {
      lnK = -lnK;
    }
    // System.out.println("K " + Math.exp(lnK));
    return Math.exp(lnK);
  }

  /**
   * Setter for property k.
   *
   * @param k New value of property k.
   */
  public void setK(double[] k) {
    this.K = k;
  }

  /**
   * <p>
   * setK.
   * </p>
   *
   * @param i a int
   * @param Kd a double
   */
  public void setK(int i, double Kd) {
    this.K[i] = Kd;
  }
}
