/*
 * ChemicalReactionList.java
 *
 * Created on 4. februar 2001, 15:32
 */

package neqsim.chemicalreactions.chemicalreaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ChemicalReactionList class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ChemicalReactionList implements ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemicalReactionList.class);

  ArrayList<ChemicalReaction> chemicalReactionList = new ArrayList<ChemicalReaction>();
  String[] reactiveComponentList;
  double[][] reacMatrix;
  double[][] reacGMatrix;
  double[][] tempReacMatrix;
  double[][] tempStocMatrix;
  /** Components array for reference potential calculations. */
  private ComponentInterface[] refPotComponents;

  /**
   * <p>
   * readReactions.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void readReactions(SystemInterface system) {
    // TODO: refact and combine with chemicalreactionfactory?
    chemicalReactionList.clear();
    ArrayList<String> names = new ArrayList<String>();
    ArrayList<String> stocCoef = new ArrayList<String>();
    double r = 0;
    double refT = 0;
    double actH;
    double[] K = new double[4];
    boolean useReaction = false;
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      if (system.getModelName().equals("Kent Eisenberg-model")) {
        // System.out.println("selecting Kent-Eisenberg reaction set");
        dataSet = database.getResultSet("SELECT * FROM reactiondatakenteisenberg");
      } else {
        // System.out.println("selecting standard reaction set");
        dataSet = database.getResultSet("SELECT * FROM reactiondata");
      }

      double[] coefArray;
      String[] nameArray;
      dataSet.next();
      do {
        useReaction = Integer.parseInt(dataSet.getString("usereaction")) == 1;
        if (useReaction) {
          names.clear();
          stocCoef.clear();
          String reacname = dataSet.getString("NAME");
          // System.out.println("name " +reacname );
          K[0] = Double.parseDouble(dataSet.getString("K1"));
          K[1] = Double.parseDouble(dataSet.getString("K2"));
          K[2] = Double.parseDouble(dataSet.getString("K3"));
          K[3] = Double.parseDouble(dataSet.getString("K4"));
          refT = Double.parseDouble(dataSet.getString("Tref"));
          r = Double.parseDouble(dataSet.getString("r"));
          actH = Double.parseDouble(dataSet.getString("ACTENERGY"));

          try (
              neqsim.util.database.NeqSimDataBase database2 =
                  new neqsim.util.database.NeqSimDataBase();
              java.sql.ResultSet dataSet2 = database2
                  .getResultSet("SELECT * FROM stoccoefdata where REACNAME='" + reacname + "'")) {
            dataSet2.next();
            do {
              // System.out.println("name of cop "
              // +dataSet2.getString("compname").trim());
              names.add(dataSet2.getString("compname").trim());
              stocCoef.add((dataSet2.getString("stoccoef")).trim());
            } while (dataSet2.next());
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }

          nameArray = new String[names.size()];
          coefArray = new double[nameArray.length];
          for (int i = 0; i < nameArray.length; i++) {
            coefArray[i] = Double.parseDouble(stocCoef.get(i));
            nameArray[i] = names.get(i);
          }

          ChemicalReaction reaction =
              new ChemicalReaction(reacname, nameArray, coefArray, K, r, actH, refT);
          chemicalReactionList.add(reaction);
          // System.out.println("reaction added ok...");
        }
      } while (dataSet.next());
    } catch (Exception ex) {
      logger.error("could not add reaction: ", ex);
    }
  }

  /**
   * <p>
   * getReaction.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.chemicalreactions.chemicalreaction.ChemicalReaction} object
   */
  public ChemicalReaction getReaction(int i) {
    return chemicalReactionList.get(i);
  }

  /**
   * <p>
   * getReaction.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.chemicalreactions.chemicalreaction.ChemicalReaction} object
   */
  public ChemicalReaction getReaction(String name) {
    for (int i = 0; i < chemicalReactionList.size(); i++) {
      if ((chemicalReactionList.get(i)).getName().equals(name)) {
        return chemicalReactionList.get(i);
      }
    }
    logger.warn("did not find reaction: " + name);
    return null;
  }

  /**
   * <p>
   * removeJunkReactions.
   * </p>
   *
   * @param names an array of {@link java.lang.String} objects
   */
  public void removeJunkReactions(String[] names) {
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    while (e.hasNext()) {
      // System.out.println("reaction name " +((ChemicalReaction)
      // e.next()).getName());
      if (!(e.next()).reactantsContains(names)) {
        e.remove();
      }
    }
  }

  /**
   * <p>
   * removeDependentReactions.
   * </p>
   */
  public void removeDependentReactions() {
    if (chemicalReactionList.size() == 0) {
      return;
    }

    // Ensure reactiveComponentList is populated
    getAllComponents();

    ArrayList<ChemicalReaction> independentReactions = new ArrayList<>();

    // We need to map component names to indices
    ArrayList<String> compList = new ArrayList<>(Arrays.asList(reactiveComponentList));

    for (ChemicalReaction reaction : chemicalReactionList) {
      // Try adding this reaction to the independent set
      independentReactions.add(reaction);

      // Build matrix for current set
      double[][] matrixData = new double[independentReactions.size()][compList.size()];
      for (int i = 0; i < independentReactions.size(); i++) {
        ChemicalReaction r = independentReactions.get(i);
        String[] rNames = r.getNames();
        double[] rCoefs = r.getStocCoefs();
        for (int j = 0; j < rNames.length; j++) {
          int colIndex = compList.indexOf(rNames[j]);
          if (colIndex >= 0) {
            matrixData[i][colIndex] = rCoefs[j];
          }
        }
      }

      Matrix mat = new Matrix(matrixData);
      int rank = mat.rank();

      if (rank < independentReactions.size()) {
        // Rank didn't increase (or is less than rows), so this reaction is dependent
        independentReactions.remove(independentReactions.size() - 1);
        logger.info("Removed dependent reaction: " + reaction.getName());
      }
    }

    chemicalReactionList = independentReactions;
  }

  /**
   * <p>
   * checkReactions.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void checkReactions(PhaseInterface phase) {
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    while (e.hasNext()) {
      (e.next()).init(phase);
    }
  }

  /**
   * <p>
   * initMoleNumbers.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param Amatrix an array of type double
   * @param chemRefPot an array of type double
   */
  public void initMoleNumbers(PhaseInterface phase, ComponentInterface[] components,
      double[][] Amatrix, double[] chemRefPot) {
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    while (e.hasNext()) {
      (e.next()).initMoleNumbers(phase, components, Amatrix, chemRefPot);
      // ((ChemicalReaction)e).checkK(system);
    }
  }

  /**
   * <p>
   * getAllComponents.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getAllComponents() {
    HashSet<String> components = new HashSet<String>();
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    ChemicalReaction reaction;
    while (e.hasNext()) {
      reaction = e.next();
      components.addAll(Arrays.asList(reaction.getNames()));
    }
    String[] componentList = new String[components.size()];
    int k = 0;
    Iterator<String> newe = components.iterator();
    while (newe.hasNext()) {
      componentList[k++] = newe.next();
    }
    reactiveComponentList = componentList;
    return componentList;
  }

  /**
   * <p>
   * createReactionMatrix.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @return an array of type double
   */
  public double[][] createReactionMatrix(PhaseInterface phase, ComponentInterface[] components) {
    this.refPotComponents = components; // Store for use in calcReferencePotentials
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    ChemicalReaction reaction;
    int reactionNumber = 0;
    reacMatrix = new double[chemicalReactionList.size()][components.length];
    reacGMatrix = new double[chemicalReactionList.size()][components.length + 1];
    try {
      while (e.hasNext()) {
        reaction = e.next();
        for (int i = 0; i < components.length; i++) {
          reacMatrix[reactionNumber][i] = 0;
          // System.out.println("Component List loop "+components[i].getComponentName());
          for (int j = 0; j < reaction.getNames().length; j++) {
            if (components[i].getName().equals(reaction.getNames()[j])) {
              reacMatrix[reactionNumber][i] = reaction.getStocCoefs()[j];
              reacGMatrix[reactionNumber][i] = reaction.getStocCoefs()[j];
            }
          }
        }
        reacGMatrix[reactionNumber][components.length] =
            R * phase.getTemperature() * Math.log(reaction.getK(phase));
        reactionNumber++;
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    /*
     * Matrix reacMatr; if (reacGMatrix.length > 0) { reacMatr = new Matrix(reacGMatrix); }
     * System.out.println("reac matrix: "); reacMatr.print(10,3);
     */
    return reacMatrix;
  }

  /**
   * <p>
   * updateReferencePotentials.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @return an array of type double
   */
  public double[] updateReferencePotentials(PhaseInterface phase, ComponentInterface[] components) {
    this.refPotComponents = components; // Store for use in calcReferencePotentials
    for (int i = 0; i < chemicalReactionList.size(); i++) {
      reacGMatrix[i][components.length] =
          R * phase.getTemperature() * Math.log((chemicalReactionList.get(i)).getK(phase));
    }
    return calcReferencePotentials();
  }

  /**
   * <p>
   * getReactionGMatrix.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getReactionGMatrix() {
    return reacGMatrix;
  }

  /**
   * <p>
   * getReactionMatrix.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getReactionMatrix() {
    return reacMatrix;
  }

  /**
   * <p>
   * calcReferencePotentials.
   * </p>
   *
   * <p>
   * Calculates reference potentials (chemical potentials at standard state) for all reactive
   * components using the reaction equilibrium relationships. For each reaction: sum(nu_i *
   * mu_i^ref) = -RT * ln(K)
   * </p>
   *
   * <p>
   * The method first identifies linearly independent columns (components) and solves for their
   * reference potentials directly. Then it iteratively propagates these values to dependent
   * components using the reaction stoichiometry, processing them in dependency order.
   * </p>
   *
   * @return an array of reference potentials for all reactive components
   */
  public double[] calcReferencePotentials() {
    int nRows = chemicalReactionList.size();
    if (nRows == 0) {
      return new double[0];
    }
    int nCols = reacGMatrix[0].length - 1;

    // Get B matrix (last column contains -RT*ln(K) for each reaction)
    double[][] bData = new double[nRows][1];
    for (int i = 0; i < nRows; i++) {
      bData[i][0] = reacGMatrix[i][nCols];
    }
    Matrix Bmatrix = new Matrix(bData);

    // Find independent columns (components with linearly independent stoichiometry)
    ArrayList<Integer> independentColumns = new ArrayList<>();
    ArrayList<Integer> dependentColumns = new ArrayList<>();
    Matrix currentMat = null;

    for (int j = 0; j < nCols; j++) {
      // Create a candidate matrix with the new column added
      Matrix nextMat;
      if (currentMat == null) {
        nextMat = new Matrix(nRows, 1);
        for (int i = 0; i < nRows; i++) {
          nextMat.set(i, 0, reacGMatrix[i][j]);
        }
      } else {
        nextMat = new Matrix(nRows, currentMat.getColumnDimension() + 1);
        nextMat.setMatrix(0, nRows - 1, 0, currentMat.getColumnDimension() - 1, currentMat);
        for (int i = 0; i < nRows; i++) {
          nextMat.set(i, currentMat.getColumnDimension(), reacGMatrix[i][j]);
        }
      }

      int currentRank = (currentMat == null) ? 0 : currentMat.rank();
      if (nextMat.rank() > currentRank) {
        currentMat = nextMat;
        independentColumns.add(j);
        if (independentColumns.size() == nRows) {
          // Remaining columns are dependent
          for (int k = j + 1; k < nCols; k++) {
            dependentColumns.add(k);
          }
          break;
        }
      } else {
        dependentColumns.add(j);
      }
    }

    if (independentColumns.size() < nRows) {
      logger.error("Rank of reaction matrix too low: " + independentColumns.size() + " < " + nRows);
      return null;
    }

    // Solve A_indep * x_indep = -B for independent component reference potentials
    Matrix solv = currentMat.solve(Bmatrix.times(-1.0));

    double[] result = new double[nCols];
    boolean[] computed = new boolean[nCols];

    // Mark independent components as computed
    for (int i = 0; i < nRows; i++) {
      int col = independentColumns.get(i);
      result[col] = solv.get(i, 0);
      computed[col] = true;
    }

    // Iteratively compute reference potentials for dependent components
    // A component can be computed when a reaction exists where all OTHER components are known
    // If stuck (multiple unknowns in same reaction), use Gibbs energy fallback for one component
    int maxIterations = dependentColumns.size() * 2 + 1; // Extra iterations for fallback handling
    for (int iter = 0; iter < maxIterations; iter++) {
      boolean progress = false;

      for (int depCol : dependentColumns) {
        if (computed[depCol]) {
          continue; // Already computed
        }

        // Find a reaction where this component appears and all other components are known
        for (int r = 0; r < nRows; r++) {
          double nuDep = reacGMatrix[r][depCol];
          if (Math.abs(nuDep) < 1e-10) {
            continue; // Component not in this reaction
          }

          // Check if all other components in this reaction are already computed
          boolean allOthersKnown = true;
          int unknownCol = -1;
          for (int j = 0; j < nCols; j++) {
            if (j != depCol && Math.abs(reacGMatrix[r][j]) > 1e-10 && !computed[j]) {
              allOthersKnown = false;
              unknownCol = j;
              break;
            }
          }

          if (allOthersKnown) {
            // Calculate: mu_dep = (-RT*ln(K) - sum(nu_i * mu_i for i != dep)) / nu_dep
            double rtLnK = reacGMatrix[r][nCols];
            double sumOthers = 0.0;
            StringBuilder calcDebug = new StringBuilder();
            for (int j = 0; j < nCols; j++) {
              if (j != depCol) {
                double term = reacGMatrix[r][j] * result[j];
                sumOthers += term;
              }
            }
            result[depCol] = (-rtLnK - sumOthers) / nuDep;
            computed[depCol] = true;
            progress = true;
            break; // Found reference potential for this component
          }
        }
      }

      if (!progress) {
        // No progress using reactions alone - use Gibbs energy fallback for ONE component
        // to break the deadlock, then continue iterating to propagate to others
        boolean usedFallback = false;
        for (int depCol : dependentColumns) {
          if (!computed[depCol] && refPotComponents != null && depCol < refPotComponents.length) {
            // Use Gibbs energy of formation as reference potential for this component
            double gf = refPotComponents[depCol].getGibbsEnergyOfFormation();
            result[depCol] = gf; // Use Gibbs energy directly (not negated)
            computed[depCol] = true;
            usedFallback = true;
            break; // Only use fallback for one component, then try propagating
          }
        }
        if (!usedFallback) {
          break; // Truly stuck
        }
        // Continue loop to propagate from the fallback value
      }
    }

    // Final check: any remaining components get Gibbs energy fallback
    for (int depCol : dependentColumns) {
      if (!computed[depCol]) {
        if (refPotComponents != null && depCol < refPotComponents.length) {
          double gf = refPotComponents[depCol].getGibbsEnergyOfFormation();
          result[depCol] = gf;
          computed[depCol] = true;
        } else {
          logger.warn("Could not compute reference potential for component index " + depCol);
        }
      }
    }

    return result;
  }

  /**
   * <p>
   * calcReacMatrix.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcReacMatrix(PhaseInterface phase) {
    tempReacMatrix = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    tempStocMatrix = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    ChemicalReaction reaction;

    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
      while (e.hasNext()) {
        reaction = e.next();
        for (int j = 0; j < reaction.getNames().length; j++) {
          if (phase.getComponent(i).getName().equals(reaction.getNames()[j])) {
            for (int k = 0; k < phase.getNumberOfComponents(); k++) {
              for (int o = 0; o < reaction.getNames().length; o++) {
                if (phase.getComponent(k).getName().equals(reaction.getNames()[o])) {
                  // System.out.println("comp1 " +
                  // system.getPhases()[1].getComponent(i).getComponentName() +
                  // " comp2 "
                  // +system.getPhases()[1].getComponent(k).getComponentName()
                  // );
                  tempReacMatrix[i][k] = reaction.getRateFactor(phase);
                  tempStocMatrix[i][k] = -reaction.getStocCoefs()[o];
                }
              }
            }
          }
        }
      }
    }

    // Matrix temp = new Matrix(tempReacMatrix);
    // Matrix temp2 = new Matrix(tempStocMatrix);
    // temp.print(10,10);
    // temp2.print(10,10);
  }

  /**
   * <p>
   * Getter for the field <code>reacMatrix</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getReacMatrix() {
    return tempReacMatrix;
  }

  /**
   * <p>
   * getStocMatrix.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getStocMatrix() {
    return tempStocMatrix;
  }

  /**
   * <p>
   * calcReacRates.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @return a {@link Jama.Matrix} object
   */
  public Matrix calcReacRates(PhaseInterface phase, ComponentInterface[] components) {
    Matrix modReacMatrix = new Matrix(reacMatrix).copy();
    // System.out.println(" vol " + system.getPhases()[1].getMolarVolume());

    for (int i = 0; i < chemicalReactionList.size(); i++) {
      for (int j = 0; j < components.length; j++) {
        // System.out.println("mol cons " +
        // components[j].getx()/system.getPhases()[1].getMolarMass());
        modReacMatrix.set(i, j,
            Math.pow(components[j].getx() * phase.getDensity() / phase.getMolarMass(),
                Math.abs(reacMatrix[i][j])));
      }
    }
    // modReacMatrix.print(10,10);
    double[] tempForward = new double[chemicalReactionList.size()];
    double[] tempBackward = new double[chemicalReactionList.size()];
    double[] reacVec = new double[chemicalReactionList.size()];

    for (int i = 0; i < chemicalReactionList.size(); i++) {
      tempForward[i] = (chemicalReactionList.get(i)).getRateFactor();
      tempBackward[i] =
          (chemicalReactionList.get(i)).getK(phase) / (chemicalReactionList.get(i)).getRateFactor();
      for (int j = 0; j < components.length; j++) {
        if (reacMatrix[i][j] > 0) {
          tempForward[i] *= modReacMatrix.get(i, j);
        }
        if (reacMatrix[i][j] < 0) {
          tempBackward[i] *= modReacMatrix.get(i, j);
        }
      }
      reacVec[i] = tempForward[i] - tempBackward[i];
    }

    Matrix reacMatVec = new Matrix(reacVec, 1);
    Matrix reacMat = new Matrix(reacMatrix).transpose().times(reacMatVec);

    double[] reactRates = new double[phase.getComponents().length];
    for (int j = 0; j < components.length; j++) {
      reactRates[components[j].getComponentNumber()] = reacMat.get(j, 0);
    }

    for (int j = 0; j < phase.getNumberOfComponents(); j++) {
      // System.out.println("reac " +j + " " + reactRates[j] );
    }

    // System.out.println("reac matrix ");
    // reacMat.print(10,10);
    return reacMat;
  }

  /**
   * Getter for property chemicalReactionList.
   *
   * @return Value of property chemicalReactionList.
   */
  public ArrayList<ChemicalReaction> getChemicalReactionList() {
    return chemicalReactionList;
  }

  /**
   * Setter for property chemicalReactionList.
   *
   * @param chemicalReactionList New value of property chemicalReactionList.
   */
  public void setChemicalReactionList(ArrayList<ChemicalReaction> chemicalReactionList) {
    this.chemicalReactionList = chemicalReactionList;
  }

  /**
   * <p>
   * reacHeat.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param comp a {@link java.lang.String} object
   * @return a double
   */
  public double reacHeat(PhaseInterface phase, String comp) {
    ChemicalReaction reaction;
    double heat = 0.0;
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    while (e.hasNext()) {
      reaction = e.next();
      heat += phase.getComponent(comp).getNumberOfmoles() * reaction.getReactionHeat(phase);
      // System.out.println("moles " + phase.getComponent(comp).getNumberOfmoles());
      // System.out.println("reac heat 2 " + heat);
    }
    return heat;
  }
}
