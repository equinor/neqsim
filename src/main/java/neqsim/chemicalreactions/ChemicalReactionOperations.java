/*
 * ChemicalReactionOperations.java
 *
 * Created on 4. februar 2001, 20:06
 */

package neqsim.chemicalreactions;

import java.util.HashSet;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.chemicalreactions.chemicalequilibrium.ChemicalEquilibrium;
import neqsim.chemicalreactions.chemicalequilibrium.LinearProgrammingChemicalEquilibrium;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionList;
import neqsim.chemicalreactions.kinetics.Kinetics;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ChemicalReactionOperations class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChemicalReactionOperations
    implements neqsim.thermo.ThermodynamicConstantsInterface, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemicalReactionOperations.class);

  SystemInterface system;
  ComponentInterface[] components;
  ChemicalReactionList reactionList = new ChemicalReactionList();
  String[] componentNames;
  String[] allComponentNames;
  String[] elements;
  double[][] Amatrix;
  double[] nVector;
  int iter = 0;
  double[] bVector;
  int phase = 1;
  double[] chemRefPot;
  double[] newMoles;
  double inertMoles = 0.0;
  ChemicalEquilibrium solver;
  double deltaReactionHeat = 0.0;
  boolean firsttime = false;
  Kinetics kineticsSolver;
  LinearProgrammingChemicalEquilibrium initCalc;

  /**
   * Chemical reactions are only solved in the aqueous phase.
   *
   * <p>
   * Chemical equilibrium calculations (e.g., water dissociation for pH) are only meaningful in an
   * aqueous phase. This method returns the index of the aqueous phase if one exists, or -1 if no
   * aqueous phase is present.
   * </p>
   *
   * @return index of the reactive (aqueous) phase, or -1 if no aqueous phase exists
   */
  private int getReactivePhaseIndex() {
    int nPhases = system.getNumberOfPhases();
    if (nPhases <= 0) {
      return -1;
    }

    // Look for aqueous phase by checking actual phase type, not just asking system
    // (system.getPhaseNumberOfPhase returns 0 as default if not found, which is wrong)
    for (int i = 0; i < nPhases; i++) {
      String phaseTypeName = system.getPhase(i).getPhaseTypeName();
      if ("aqueous".equalsIgnoreCase(phaseTypeName)) {
        return i;
      }
    }

    // Fallback: look for liquid phase (used during initialization before flash assigns phase types)
    for (int i = 0; i < nPhases; i++) {
      String phaseTypeName = system.getPhase(i).getPhaseTypeName();
      if ("liquid".equalsIgnoreCase(phaseTypeName) || "oil".equalsIgnoreCase(phaseTypeName)) {
        return i;
      }
    }

    // No aqueous/liquid phase found - return -1 to signal chemical equilibrium should be skipped.
    return -1;
  }

  /**
   * Check if a phase is actually aqueous (not just liquid). This is stricter than
   * getReactivePhaseIndex() which also accepts liquid.
   *
   * @param phaseNum the phase number to check
   * @return true if the phase is aqueous, false otherwise
   */
  private boolean isPhaseAqueous(int phaseNum) {
    if (phaseNum < 0 || phaseNum >= system.getNumberOfPhases()) {
      return false;
    }
    String phaseTypeName = system.getPhase(phaseNum).getPhaseTypeName();
    return "aqueous".equalsIgnoreCase(phaseTypeName);
  }

  /**
   * Re-initialize equilibrium helpers for the selected reactive phase.
   *
   * <p>
   * The internal matrices (A, b, initial-estimate solver) depend on which phase is treated as the
   * reactive phase. When the system changes between 1/2/3-phase configurations during a flash, the
   * reactive phase index can change and we must rebuild these structures.
   * </p>
   *
   * @param phaseNum phase index to use as reactive phase
   */
  private void reinitializeForReactivePhase(int phaseNum) {
    this.phase = phaseNum;
    setReactiveComponents(phaseNum);
    reactionList.checkReactions(system.getPhase(phaseNum));
    reactionList.createReactionMatrix(system.getPhase(phaseNum), components);
    chemRefPot = calcChemRefPot(phaseNum);
    elements = getAllElements();
    initCalc = null;
    Amatrix = null;
    try {
      initCalc =
          new LinearProgrammingChemicalEquilibrium(chemRefPot, components, elements, this, phase);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    setComponents(phaseNum);
    Amatrix = calcAmatrix();
    nVector = calcNVector();
    bVector = calcBVector();
    firsttime = true;
  }

  /**
   * <p>
   * Constructor for ChemicalReactionOperations.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ChemicalReactionOperations(SystemInterface system) {
    boolean newcomps = true;
    int old = system.getPhase(0).getNumberOfComponents();
    this.system = system;

    // During initialization, use the reactive phase if available, else default to phase 0.
    // Actual chemical equilibrium solving will only occur on aqueous phases.
    int reactivePhase = getReactivePhaseIndex();
    this.phase = reactivePhase >= 0 ? reactivePhase : 0;

    do {
      // if statement added by Procede
      if (!newcomps) {
        break;
      }
      componentNames = system.getComponentNames();
      reactionList.readReactions(system);
      reactionList.removeJunkReactions(componentNames);
      reactionList.removeDependentReactions();
      allComponentNames = reactionList.getAllComponents();
      this.addNewComponents();
      if (system.getPhase(0).getNumberOfComponents() == old) {
        newcomps = false;
      }
      old = system.getPhase(0).getNumberOfComponents();
    } while (newcomps);

    components = new ComponentInterface[allComponentNames.length];
    if (components.length > 0) {
      reactivePhase = getReactivePhaseIndex();
      this.phase = reactivePhase >= 0 ? reactivePhase : 0;
      setReactiveComponents();
      reactionList.checkReactions(system.getPhase(phase));
      reactionList.createReactionMatrix(system.getPhase(phase), components);
      chemRefPot = calcChemRefPot(phase);
      elements = getAllElements();

      try {
        initCalc =
            new LinearProgrammingChemicalEquilibrium(chemRefPot, components, elements, this, phase);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      setComponents();
      Amatrix = calcAmatrix();
      nVector = calcNVector();
      bVector = calcBVector();
    } else {
      system.isChemicalSystem(false);
    }
    kineticsSolver = new Kinetics(this);
  }

  /**
   * <p>
   * Setter for the field <code>system</code>.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
  }

  /** {@inheritDoc} */
  @Override
  public ChemicalReactionOperations clone() {
    ChemicalReactionOperations clonedSystem = null;
    try {
      clonedSystem = (ChemicalReactionOperations) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return clonedSystem;
  }

  /**
   * <p>
   * Setter for the field <code>components</code>.
   * </p>
   */
  public void setComponents() {
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) {
      return; // No aqueous phase - nothing to set
    }
    this.phase = reactivePhase;
    for (int j = 0; j < components.length; j++) {
      system.getPhase(phase).getComponents()[components[j].getComponentNumber()] = components[j];
    }
  }

  /**
   * <p>
   * Setter for the field <code>components</code>.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setComponents(int phaseNum) {
    for (int j = 0; j < components.length; j++) {
      system.getPhase(phaseNum).getComponents()[components[j].getComponentNumber()] = components[j];
    }
  }

  /**
   * <p>
   * setReactiveComponents.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setReactiveComponents(int phaseNum) {
    for (int j = 0; j < components.length; j++) {
      // System.out.println("comp " + components[j].getComponentNumber());
      components[j] = system.getPhase(phaseNum).getComponent(components[j].getComponentNumber());
    }
  }

  /**
   * <p>
   * setReactiveComponents.
   * </p>
   */
  public void setReactiveComponents() {
    int reactivePhase = getReactivePhaseIndex();
    // Fall back to phase 0 if no reactive phase found (needed for initialization)
    if (reactivePhase < 0) {
      reactivePhase = 0;
    }
    this.phase = reactivePhase;
    int k = 0;
    for (int j = 0; j < componentNames.length; j++) {
      // System.out.println("component " + componentNames[j]);
      String name = componentNames[j];
      for (int i = 0; i < allComponentNames.length; i++) {
        if (name.equals(allComponentNames[i])) {
          components[k++] = system.getPhase(phase).getComponent(j);
          // System.out.println("reactive comp " +
          // system.getPhases()[1].getComponent(j).getName());
        }
      }
    }
  }

  /**
   * <p>
   * calcInertMoles.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double calcInertMoles(int phaseNum) {
    double reactiveMoles = 0;
    for (int j = 0; j < components.length; j++) {
      reactiveMoles += components[j].getNumberOfMolesInPhase();
    }
    inertMoles = system.getPhase(phaseNum).getNumberOfMolesInPhase() - reactiveMoles;
    // System.out.println("inertmoles = " + inertMoles);
    if (inertMoles < 0) {
      inertMoles = 1.0e-30;
    }
    return inertMoles;
  }

  /**
   * <p>
   * sortReactiveComponents.
   * </p>
   */
  public void sortReactiveComponents() {
    ComponentInterface tempComp;
    for (int i = 0; i < components.length; i++) {
      for (int j = i + 1; j < components.length; j++) {
        if (components[j].getGibbsEnergyOfFormation() < components[i].getGibbsEnergyOfFormation()) {
          tempComp = components[i];
          components[i] = components[j];
          components[j] = tempComp;
          // System.out.println("swich : " + i + " " + j);
        }
      }
    }
  }

  /**
   * <p>
   * addNewComponents.
   * </p>
   * 
   * <p>
   * Adds new reactive components (typically ionic species from chemical reactions) to the system.
   * Components are initialized with a small but numerically reasonable amount to enable the
   * chemical equilibrium solver to converge. Ionic species are initialized with a higher minimum to
   * provide a reasonable starting point for acid-base equilibria and help the Newton solver find
   * physically meaningful solutions.
   * </p>
   */
  public void addNewComponents() {
    boolean newComp;

    for (int i = 0; i < allComponentNames.length; i++) {
      String name = allComponentNames[i];
      newComp = true;

      for (int j = 0; j < componentNames.length; j++) {
        if (name.equals(componentNames[j])) {
          newComp = false;
          break;
        }
      }
      if (newComp) {
        // Add with a small non-zero initial amount for numerical stability.
        // The equilibrium solver will redistribute moles according to reaction equilibrium.
        // Using 1e-10 mol which is negligible compared to typical component amounts
        // but large enough for the LP/Newton solvers to work numerically.
        double initialMoles = 1.0e-10;
        system.addComponent(name, initialMoles);
      }
    }
  }

  /**
   * <p>
   * getAllElements.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getAllElements() {
    HashSet<String> elementsLocal = new HashSet<String>();
    for (int j = 0; j < components.length; j++) {
      for (int i = 0; i < components[j].getElements().getElementNames().length; i++) {
        // System.out.println("elements: " +
        // components[j].getElements().getElementNames()[i]);
        elementsLocal.add(components[j].getElements().getElementNames()[i]);
      }
    }

    String[] elementList = new String[elementsLocal.size()];
    int k = 0;
    Iterator<String> newe = elementsLocal.iterator();
    while (newe.hasNext()) {
      elementList[k++] = newe.next();
    }
    /*
     * for(int j=0;j<elementList.length;j++){ System.out.println("elements2: " +elementList[j]); }
     */
    return elementList;
  }

  /**
   * <p>
   * hasRections.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasReactions() {
    return components.length > 0;
  }

  /**
   * <p>
   * calcNVector.
   * </p>
   * 
   * <p>
   * Calculate the mole vector for reactive species in the reactive phase. This reads from the
   * current phase state to get the actual moles in the aqueous/reactive phase where chemical
   * equilibrium takes place.
   * </p>
   *
   * @return an array of type double containing moles of each reactive species in reactive phase
   */
  public double[] calcNVector() {
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) {
      reactivePhase = 0; // fallback
    }
    double[] nvec = new double[components.length];
    for (int i = 0; i < components.length; i++) {
      int compNum = components[i].getComponentNumber();
      nvec[i] = system.getPhase(reactivePhase).getComponent(compNum).getNumberOfMolesInPhase();
    }
    return nvec;
  }

  /**
   * <p>
   * calcBVector.
   * </p>
   *
   * @return an array of type double
   */
  public double[] calcBVector() {
    Matrix tempA = new Matrix(Amatrix);
    Matrix tempB = new Matrix(nVector, 1);
    Matrix tempN = tempA.times(tempB.transpose()).transpose();
    // print added by Neeraj
    // System.out.println("b matrix: ");
    // tempN.print(10,2);

    return tempN.getArray()[0];
  }

  /**
   * <p>
   * calcAmatrix.
   * </p>
   * 
   * <p>
   * Calculates the stoichiometry matrix (A) based on the components and elements in the system.
   * This matrix includes an additional row for ionic charge balance (electroneutrality constraint),
   * which ensures that the sum of positive and negative charges in the solution equals zero.
   * </p>
   *
   * @return the stoichiometry matrix A (elements + 1 x components), where the last row contains
   *         ionic charges for electroneutrality
   */
  public double[][] calcAmatrix() {
    if (components == null || elements == null) {
      return null;
    }
    // Add one extra row for ionic charge balance (electroneutrality)
    double[][] A = new double[elements.length + 1][components.length];
    for (int j = 0; j < components.length; j++) {
      String[] compElements = components[j].getElements().getElementNames();
      double[] compCoefs = components[j].getElements().getElementCoefs();
      for (int i = 0; i < elements.length; i++) {
        for (int k = 0; k < compElements.length; k++) {
          if (compElements[k].equals(elements[i])) {
            A[i][j] = compCoefs[k];
            break;
          }
        }
      }
    }
    // Add ionic charge row for electroneutrality constraint
    // This ensures sum(n_i * z_i) = 0 where z_i is the ionic charge of component i
    for (int j = 0; j < components.length; j++) {
      A[elements.length][j] = components[j].getIonicCharge();
    }
    return A;
  }

  /**
   * <p>
   * calcChemRefPot.
   * </p>
   *
   * <p>
   * Calculates reference potentials for the chemical equilibrium solver. The reference potentials
   * are derived from reaction equilibrium constants (via -RT*ln(K)) and determine the equilibrium
   * composition. For components not appearing in any reaction, Gibbs energy of formation is used as
   * a fallback.
   * </p>
   *
   * @param phaseNum a int
   * @return an array of type double
   */
  public double[] calcChemRefPot(int phaseNum) {
    // Delegate to ChemicalReactionList to calculate consistent reference potentials
    // using matrix algebra (solving sum(nu_i * mu_i) = -RT*ln(K)).
    double[] potentials =
        reactionList.updateReferencePotentials(system.getPhase(phaseNum), components);

    // Fallback if calculation failed (e.g. rank deficiency)
    if (potentials == null) {
      potentials = new double[components.length];
      for (int i = 0; i < components.length; i++) {
        potentials[i] = components[i].getGibbsEnergyOfFormation();
      }
    }

    // Set reference potentials on components
    for (int i = 0; i < components.length; i++) {
      components[i].setReferencePotential(potentials[i]);
    }

    return potentials;
  }

  /**
   * <p>
   * updateMoles.
   * </p>
   * 
   * <p>
   * Updates moles in the reactive phase based on the LP solver solution. Uses
   * Phase.addMolesChemReac with totdn=0 to only affect phase moles without corrupting the total
   * system moles (which would violate element conservation).
   * </p>
   *
   * @param phaseNum a int
   */
  public void updateMoles(int phaseNum) {
    // Guard against null newMoles - can happen if LP solver failed
    if (newMoles == null) {
      logger.debug("updateMoles called with null newMoles, skipping");
      return;
    }

    for (int i = 0; i < components.length; i++) {
      double currentMoles =
          system.getPhase(phaseNum).getComponents()[components[i].getComponentNumber()]
              .getNumberOfMolesInPhase();
      double targetMoles = Math.max(newMoles[i], 1e-45); // Ensure small positive value for
                                                         // numerical stability
      double delta = targetMoles - currentMoles;

      // Use addMolesChemReac(component, dn, 0) to ONLY change phase moles
      // NOT system total moles - this preserves element conservation
      system.getPhase(phaseNum).addMolesChemReac(components[i].getComponentNumber(), delta, 0);
    }

    // Update phase total moles to match sum of component moles
    double phaseTotalMoles = 0;
    for (int i = 0; i < system.getPhase(phaseNum).getNumberOfComponents(); i++) {
      phaseTotalMoles += system.getPhase(phaseNum).getComponent(i).getNumberOfMolesInPhase();
    }
    ((neqsim.thermo.phase.Phase) system.getPhase(phaseNum)).numberOfMolesInPhase = phaseTotalMoles;

    // Recalculate mole fractions to be consistent with new moles
    system.initBeta();
    system.init_x_y();
  }

  /**
   * <p>
   * solveChemEq.
   * </p>
   *
   * @param type a int
   * @return a boolean
   */
  public boolean solveChemEq(int type) {
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) {
      // No aqueous/liquid phase - skip chemical equilibrium
      return false;
    }
    return solveChemEq(reactivePhase, type);
  }

  /**
   * <p>
   * solveChemEq.
   * </p>
   *
   * @param phaseNum a int
   * @param type a int
   * @return a boolean
   */
  public boolean solveChemEq(int phaseNum, int type) {
    // Get the reactive phase - this finds aqueous or liquid phases
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) {
      // No aqueous/liquid phase - skip chemical equilibrium
      return false;
    }
    if (phaseNum != reactivePhase) {
      phaseNum = reactivePhase;
    }
    if (this.phase != phaseNum) {
      reinitializeForReactivePhase(phaseNum);
    } else {
      // Keep chem ref potentials updated for the current reactive phase.
      chemRefPot = calcChemRefPot(phaseNum);
    }
    if (!system.isChemicalSystem()) {
      return false;
    }

    if (Amatrix == null) {
      Amatrix = calcAmatrix();
    }
    if (Amatrix == null) {
      return false;
    }

    // Always calculate current element abundance based on current phase composition.
    // This ensures that if mass transfer (Flash) has occurred, we respect the new element balance.
    nVector = calcNVector();
    bVector = calcBVector();

    if (firsttime || type == 0) {
      try {
        calcInertMoles(phaseNum);
        if (initCalc != null) {
          newMoles = initCalc.generateInitialEstimates(system, bVector, inertMoles, phaseNum);
        }

        if (newMoles != null) {
          updateMoles(phaseNum);
          firsttime = false;
          // LP provides initial estimate - now fall through to Newton solver for refinement
        } else {
          // LP solver failed - fall through to Newton solver
          logger.debug("LP initial estimate failed, falling back to Newton solver");
        }
      } catch (Exception ex) {
        logger.error("error in chem eq", ex);
      }
    }

    // Newton solver (ChemicalEquilibrium)
    // We start from the current phase composition (possibly improved by LP initial estimate).
    try {
      solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phaseNum);
      return solver.solve();
    } catch (Exception ex) {
      logger.error("Error in chemical equilibrium solver", ex);
      return false;
    }
  }

  /**
   * <p>
   * solveKinetics.
   * </p>
   *
   * @param phaseNum a int
   * @param interPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param component a int
   * @return a double
   */
  public double solveKinetics(int phaseNum, PhaseInterface interPhase, int component) {
    return kineticsSolver.calcReacMatrix(system.getPhase(phaseNum), interPhase, component);
  }

  /**
   * <p>
   * getKinetics.
   * </p>
   *
   * @return a {@link neqsim.chemicalreactions.kinetics.Kinetics} object
   */
  public Kinetics getKinetics() {
    return kineticsSolver;
  }

  /**
   * <p>
   * Getter for the field <code>reactionList</code>.
   * </p>
   *
   * @return a {@link neqsim.chemicalreactions.chemicalreaction.ChemicalReactionList} object
   */
  public ChemicalReactionList getReactionList() {
    return reactionList;
  }

  /**
   * <p>
   * getComponents.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  public ComponentInterface[] getComponents() {
    return components;
  }

  /**
   * <p>
   * getAmatrix.
   * </p>
   * 
   * <p>
   * Returns the stoichiometry matrix (A) that relates components to elements. The matrix has
   * dimensions (elements.length + 1) x components.length, where the last row contains ionic charges
   * for the electroneutrality constraint.
   * </p>
   *
   * @return the stoichiometry matrix A
   */
  public double[][] getAmatrix() {
    return Amatrix;
  }

  /**
   * <p>
   * reacHeat.
   * </p>
   *
   * @param phaseNum a int
   * @param component a {@link java.lang.String} object
   * @return a double
   */
  public double reacHeat(int phaseNum, String component) {
    return reactionList.reacHeat(system.getPhase(phaseNum), component);
  }

  /**
   * Getter for property deltaReactionHeat.
   *
   * @return Value of property deltaReactionHeat.
   */
  public double getDeltaReactionHeat() {
    return deltaReactionHeat;
  }

  /**
   * Setter for property deltaReactionHeat.
   *
   * @param deltaReactionHeat New value of property deltaReactionHeat.
   */
  public void setDeltaReactionHeat(double deltaReactionHeat) {
    this.deltaReactionHeat = deltaReactionHeat;
  }

  // public Matrix calcReacRates(int phaseNum){
  // // System.out.println(" vol " + system.getPhase()[0].getMolarVolume());
  // return getReactionList().calcReacRates(system.getPhase(phaseNum), components);
  // }

  /**
   * Setter for property reactionList.
   *
   * @param reactionList New value of property reactionList.
   */
  public void setReactionList(ChemicalReactionList reactionList) {
    this.reactionList = reactionList;
  }
}
