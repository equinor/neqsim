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
    Iterator<ChemicalReaction> e = chemicalReactionList.iterator();
    ChemicalReaction reaction;
    int reactionNumber = 0;
    reacMatrix = new double[chemicalReactionList.size()][reactiveComponentList.length];
    reacGMatrix = new double[chemicalReactionList.size()][reactiveComponentList.length + 1];
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
   * @return an array of type double
   */
  public double[] calcReferencePotentials() {
    Matrix reacMatr = new Matrix(reacGMatrix);
    Matrix Amatrix = reacMatr.copy().getMatrix(0, chemicalReactionList.size() - 1, 0,
        chemicalReactionList.size() - 1); // new Matrix(reacGMatrix);
    Matrix Bmatrix = reacMatr.copy().getMatrix(0, chemicalReactionList.size() - 1,
        reacGMatrix[0].length - 1, reacGMatrix[0].length - 1); // new Matrix(reacGMatrix);

    if (Amatrix.rank() < chemicalReactionList.size()) {
      System.out.println("rank of A matrix too low !!" + Amatrix.rank());
      return null;
    } else {
      Matrix solv = Amatrix.solve(Bmatrix.timesEquals(-1.0)); // Solves for A*X = -B
      // System.out.println("ref pots");
      // solv.print(10,3);
      return solv.transpose().getArrayCopy()[0];
    }
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
