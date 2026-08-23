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
 * ChemicalReaction class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChemicalReaction extends NamedBaseClass implements neqsim.thermo.ThermodynamicConstantsInterface {
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
  String reference = "";
  double G = 0;
  double lnK = 0;
  int numberOfReactants = 0;

  /**
   * Constructor for ChemicalReaction.
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
    this(name, names, stocCoefs, K, r, activationEnergy, refT, "");
  }

  /**
   * Constructor for a chemical reaction with parameter provenance.
   *
   * @param name reaction name
   * @param names component names
   * @param stocCoefs stoichiometric coefficients
   * @param K equilibrium-constant correlation coefficients
   * @param r rate factor
   * @param activationEnergy activation energy
   * @param refT reference temperature in kelvin
   * @param reference literature or data reference stored with the parameters
   */
  public ChemicalReaction(String name, String[] names, double[] stocCoefs, double[] K, double r,
      double activationEnergy, double refT, String reference) {
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
    this.reference = reference == null ? "" : reference;

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
   * Get the literature or data reference stored with the equilibrium parameters.
   *
   * @return reference identifier, or an empty string when no reference was supplied
   */
  public String getReference() {
    return reference == null ? "" : reference;
  }

  /**
   * Get a defensive copy of the equilibrium-constant correlation coefficients.
   *
   * <p>
   * The coefficients are used by {@link #getK(PhaseInterface)} in the order defined by the reaction database.
   * </p>
   *
   * @return copied equilibrium-constant coefficient array
   */
  public double[] getEquilibriumConstantCoefficients() {
    return K.clone();
  }

  /**
   * Get the reference temperature stored with the reaction parameters.
   *
   * @return reference temperature in kelvin
   */
  public double getReferenceTemperature() {
    return refT;
  }

  /**
   * Getter for the field <code>reactantNames</code>.
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
   * Getter for the field <code>rateFactor</code>.
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
   * Getter for the field <code>stocCoefs</code>.
   *
   * @return an array of type double
   */
  public double[] getStocCoefs() {
    return this.stocCoefs;
  }

  /**
   * Getter for the field <code>productNames</code>.
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getProductNames() {
    return productNames;
  }

  /**
   * Getter for the field <code>names</code>.
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getNames() {
    return names;
  }

  /**
   * calcKx.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double calcKx(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    double kx = 1.0;
    PhaseInterface phase = system.getPhase(phaseNumb);
    for (int i = 0; i < names.length; i++) {
      ComponentInterface component = phase.getComponent(names[i]);
      kx *= Math.pow(getReactionConcentration(system, phase, component), stocCoefs[i]);
    }
    return kx;
  }

  /**
   * calcKgamma.
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
        kgamma *= Math.pow(system.getPhase(phaseNumb).getActivityCoefficient(
            system.getPhase(phaseNumb).getComponent(names[i]).getComponentNumber(),
            system.getPhase(phaseNumb).getComponent("water").getComponentNumber()), stocCoefs[i]);
      }
    }
    return kgamma;
  }

  /**
   * Calculate the mineral saturation ratio from reactant activities.
   *
   * <p>
   * The ratio is {@code IAP / Ksp}. Reactant activities follow the system-selected concentration convention and include
   * activity coefficients. Values below one are undersaturated and values above one are supersaturated.
   * </p>
   *
   * @param system thermodynamic system containing the dissolved mineral species
   * @param phaseNumb aqueous phase in which saturation is evaluated
   * @return mineral saturation ratio {@code IAP / Ksp}
   */
  public double getSaturationRatio(SystemInterface system, int phaseNumb) {
    return Math.exp(calcLogSaturationRatio(system, phaseNumb));
  }

  /**
   * Calculate the natural logarithm of the mineral saturation ratio directly in log space.
   *
   * <p>
   * Only negative-stoichiometry reactants contribute to the ion activity product because the mineral product is not a
   * dissolved phase component. Log-space evaluation retains a finite diagnostic for trace activities when the
   * corresponding linear saturation ratio underflows.
   * </p>
   *
   * @param system thermodynamic system containing the dissolved mineral species
   * @param phaseNumb aqueous phase in which saturation is evaluated
   * @return natural logarithm of {@code IAP / Ksp}
   */
  public double calcLogSaturationRatio(SystemInterface system, int phaseNumb) {
    PhaseInterface phase = system.getPhase(phaseNumb);
    double logSaturationRatio = -Math.log(getK(phase));
    for (int componentIndex = 0; componentIndex < names.length; componentIndex++) {
      if (stocCoefs[componentIndex] < 0.0) {
        ComponentInterface component = phase.getComponent(names[componentIndex]);
        logSaturationRatio -= stocCoefs[componentIndex] * getLogReactionActivity(system, phase, component);
      }
    }
    return logSaturationRatio;
  }

  /**
   * calcK.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNumb a int
   * @return a double
   */
  public double calcK(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
    return calcKx(system, phaseNumb) * calcKgamma(system, phaseNumb);
  }

  /**
   * Calculate the natural logarithm of the reaction quotient directly in log space.
   *
   * <p>
   * This method follows the same system-selected concentration/activity convention as
   * {@link #calcK(SystemInterface, int)}, but avoids overflow and underflow when ionic species are present at trace
   * concentrations.
   * </p>
   *
   * @param system thermodynamic system containing the reaction species
   * @param phaseNumb phase in which the reaction quotient is evaluated
   * @return natural logarithm of the reaction quotient
   */
  public double calcLogReactionQuotient(SystemInterface system, int phaseNumb) {
    PhaseInterface phase = system.getPhase(phaseNumb);
    double logReactionQuotient = 0.0;
    for (int componentIndex = 0; componentIndex < names.length; componentIndex++) {
      double stoichiometricCoefficient = stocCoefs[componentIndex];
      if (stoichiometricCoefficient == 0.0) {
        continue;
      }
      ComponentInterface component = phase.getComponent(names[componentIndex]);
      logReactionQuotient += stoichiometricCoefficient * getLogReactionActivity(system, phase, component);
    }
    return logReactionQuotient;
  }

  /**
   * Calculate the signed logarithmic reaction-equilibrium residual, {@code ln(Q/K)}.
   *
   * @param system thermodynamic system containing the reaction species
   * @param phaseNumb phase in which the reaction residual is evaluated
   * @return signed residual {@code ln(Q) - ln(K)}
   */
  public double calcLogReactionResidual(SystemInterface system, int phaseNumb) {
    return calcLogReactionQuotient(system, phaseNumb) - Math.log(getK(system.getPhase(phaseNumb)));
  }

  /**
   * Get the dimensionless concentration used by the system's reaction-equilibrium convention.
   *
   * <p>
   * Pitzer equilibrium constants use solute molality divided by the standard molality of 1 mol/kg. Solvent activities
   * remain on the mole-fraction convention. Other models retain the established mole-fraction concentration path.
   * </p>
   *
   * @param system thermodynamic system selecting the reaction convention
   * @param phase reactive phase
   * @param component reaction component
   * @return dimensionless reaction concentration
   */
  private double getReactionConcentration(SystemInterface system, PhaseInterface phase, ComponentInterface component) {
    if (system.getChemicalReactionConcentrationBasis() == ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY
        && !"solvent".equalsIgnoreCase(component.getReferenceStateType())) {
      return component.getMolality(phase);
    }
    return component.getx();
  }

  /**
   * Get the logarithm of a reaction activity using the system-selected standard-state convention.
   *
   * @param system thermodynamic system selecting the reaction convention
   * @param phase reactive phase
   * @param component reaction component
   * @return logarithm of the dimensionless reaction activity
   */
  private double getLogReactionActivity(SystemInterface system, PhaseInterface phase, ComponentInterface component) {
    double logActivity = Math.log(getReactionConcentration(system, phase, component));
    if (component.calcActivity()) {
      int waterComponentNumber = phase.getComponent("water").getComponentNumber();
      logActivity += phase.getLogActivityCoefficient(component.getComponentNumber(), waterComponentNumber);
    }
    return logActivity;
  }

  /**
   * Generaters initial estimates for the molenumbers.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param Amatrix an array of type double
   * @param chemRefPot an array of type double
   */
  public void initMoleNumbers(PhaseInterface phase, ComponentInterface[] components, double[][] Amatrix,
      double[] chemRefPot) {
    Matrix tempAmatrix = new Matrix(Amatrix.length, names.length);
    Matrix tempNmatrix = new Matrix(names.length, 1);
    Matrix tempRefPotmatrix = new Matrix(names.length, 1);

    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < components.length; j++) {
        // System.out.println("names: " + names[i] + " " +
        // system.getPhases()[0].getComponent(j).getName());
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
   * init.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void init(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    lnK = K[0] + K[1] / (temperature) + K[2] * Math.log(temperature) + K[3] * temperature;
    // System.out.println("K: " + Math.exp(lnK));

    for (int i = 0; i < names.length; i++) {
      moles[i] = 0.0;
    }
    for (int i = 0; i < names.length; i++) {
      for (int j = 0; j < phase.getNumberOfComponents(); j++) {
        if (this.names[i].equals(phase.getComponent(j).getName())) {
          moles[i] = phase.getComponent(j).getNumberOfMolesInPhase();
        }
      }
    }
  }

  /**
   * checkK.
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
   * reactantsContains.
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
