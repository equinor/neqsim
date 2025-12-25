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

    // Only return aqueous phase index - no fallback to non-aqueous phases.
    try {
      int aqueousPhase = system.getPhaseNumberOfPhase("aqueous");
      if (aqueousPhase >= 0 && aqueousPhase < nPhases) {
        return aqueousPhase;
      }
    } catch (Exception ex) {
      // No aqueous phase found
    }

    // No aqueous phase - return -1 to signal chemical equilibrium should be skipped.
    return -1;
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
    chemRefPot = calcChemRefPot(phaseNum);
    elements = getAllElements();
    try {
      initCalc =
          new LinearProgrammingChemicalEquilibrium(chemRefPot, components, elements, this, phase);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    setComponents(phaseNum);
    if (initCalc != null) {
      Amatrix = initCalc.getA();
    }
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
      chemRefPot = calcChemRefPot(phase);
      elements = getAllElements();

      try {
        initCalc =
            new LinearProgrammingChemicalEquilibrium(chemRefPot, components, elements, this, phase);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      setComponents();
      Amatrix = initCalc.getA();
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
    if (reactivePhase < 0) {
      return; // No aqueous phase - nothing to set
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
        system.addComponent(name, 1.0e-40);
        // System.out.println("new component added: " + name);
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
   * @return an array of type double
   */
  public double[] calcNVector() {
    double[] nvec = new double[components.length];
    for (int i = 0; i < components.length; i++) {
      nvec[i] = components[i].getNumberOfMolesInPhase();
      // System.out.println("nvec: " + nvec[i]);
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
   * calcChemRefPot.
   * </p>
   *
   * @param phaseNum a int
   * @return an array of type double
   */
  public double[] calcChemRefPot(int phaseNum) {
    double[] referencePotentials = new double[components.length];
    reactionList.createReactionMatrix(system.getPhase(phaseNum), components);
    double[] newreferencePotentials =
        reactionList.updateReferencePotentials(system.getPhase(phaseNum), components);
    if (newreferencePotentials != null) {
      for (int i = 0; i < newreferencePotentials.length; i++) {
        referencePotentials[i] = newreferencePotentials[i];
        components[i].setReferencePotential(referencePotentials[i]);
      }
      return referencePotentials;
    } else {
      return null;
    }
  }

  /**
   * <p>
   * updateMoles.
   * </p>
   *
   * @param phaseNum a int
   */
  public void updateMoles(int phaseNum) {
    double changeMoles = 0.0;
    for (int i = 0; i < components.length; i++) {
      if (Math.abs(newMoles[i]) > 1e-45) {
        changeMoles += (newMoles[i]
            - system.getPhase(phaseNum).getComponents()[components[i].getComponentNumber()]
                .getNumberOfMolesInPhase());
        // System.out.println("update moles first " + (components[i].getComponentName()
        // + " moles " + components[i].getNumberOfMolesInPhase()));
        system.getPhase(phaseNum).addMolesChemReac(components[i].getComponentNumber(),
            (newMoles[i]
                - system.getPhase(phaseNum).getComponents()[components[i].getComponentNumber()]
                    .getNumberOfMolesInPhase()));
        // System.out.println("update moles after " + (components[i].getComponentName()
        // + " moles " + components[i].getNumberOfMolesInPhase()));
      }
    }
    // System.out.println("change " + changeMoles);
    system.initTotalNumberOfMoles(changeMoles); // x_solve.get(NELE,0)*n_t);
    system.initBeta(); // this was added for mass trans calc
    system.init_x_y();
    system.init(1);
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
      // No aqueous phase - skip chemical equilibrium
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
    // Enforce aqueous-only chemistry: only solve in aqueous phase.
    int reactivePhase = getReactivePhaseIndex();
    if (reactivePhase < 0) {
      // No aqueous phase - skip chemical equilibrium
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
      System.out.println("no chemical reactions will occur...continue");
      return false;
    }

    // System.out.println("pressure1");
    calcChemRefPot(phaseNum);
    // System.out.println("pressure2");
    if (firsttime || type == 0) {
      try {
        // System.out.println("Calculating initial estimates");
        nVector = calcNVector();
        bVector = calcBVector();
        calcInertMoles(phaseNum);
        newMoles = initCalc.generateInitialEstimates(system, bVector, inertMoles, phaseNum);
        // Print statement added by Neeraj
        // for (i=0;i<5;i++)
        // System.out.println("new moles "+newMoles[i]);
        updateMoles(phaseNum);
        // System.out.println("finished iniT estimtes ");
        // system.display();
        firsttime = false;
        return true;
      } catch (Exception ex) {
        logger.error("error in chem eq", ex);
        solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phaseNum);
        return solver.solve();
      }
    } else {
      nVector = calcNVector();
      bVector = calcBVector();
      try {
        solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phaseNum);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        // todo: Will this crash below?
      }
      return solver.solve();
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

  // /** Setter for property reactionList.
  // * @param reactionList New value of property reactionList.
  // */
  // public void
  // setReactionList(chemicalReactions.chemicalReaction.ChemicalReactionList
  // reactionList) {
  // this.reactionList = reactionList;
  // }
}
