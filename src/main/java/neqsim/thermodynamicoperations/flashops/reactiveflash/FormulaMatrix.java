package neqsim.thermodynamicoperations.flashops.reactiveflash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Formula matrix (A) for the modified RAND method.
 *
 * <p>
 * Maps chemical components to their elemental composition. For NC components and NE elements, the
 * matrix A has dimensions (NE x NC), where A[k][i] is the number of atoms of element k in component
 * i.
 * </p>
 *
 * <p>
 * The element balance constraint for simultaneous chemical and phase equilibrium is: A * n_total =
 * b where n_total[i] = sum over all phases j of n[i][j], and b[k] = sum_i A[k][i] * z[i] * F (total
 * element amounts from the feed).
 * </p>
 *
 * <p>
 * References:
 * <ul>
 * <li>White, Johnson, Dantzig (1958) J. Chem. Phys. 28, 751-755</li>
 * <li>Smith, Missen (1982) Chemical Reaction Equilibrium Analysis, Wiley</li>
 * <li>Tsanas, Stenby, Yan (2017) Ind. Eng. Chem. Res. 56, 11983-11995</li>
 * </ul>
 * </p>
 *
 * @author copilot
 * @version 1.0
 */
public class FormulaMatrix implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Element names in order (rows of A). */
  private String[] elementNames;

  /** Component names in order (columns of A). */
  private String[] componentNames;

  /** The formula matrix A[NE][NC]. A[k][i] = count of element k in component i. */
  private double[][] A;

  /** Number of elements (rows). */
  private int numElements;

  /** Number of components (columns). */
  private int numComponents;

  /**
   * Construct formula matrix from a NeqSim system.
   *
   * <p>
   * Extracts elemental composition from each component's Element data (stored in the NeqSim
   * database). Components without element data (e.g. pseudo-components) are assigned a unique
   * pseudo-element so that their conservation is still enforced.
   * </p>
   *
   * @param system the thermodynamic system
   */
  public FormulaMatrix(SystemInterface system) {
    PhaseInterface phase = system.getPhase(0);
    numComponents = phase.getNumberOfComponents();
    componentNames = new String[numComponents];

    // Collect all unique element names from all components
    Set<String> elementSet = new LinkedHashSet<String>();
    List<Map<String, Double>> componentElements = new ArrayList<Map<String, Double>>();

    for (int i = 0; i < numComponents; i++) {
      ComponentInterface comp = phase.getComponent(i);
      componentNames[i] = comp.getComponentName();

      Map<String, Double> elemMap = new LinkedHashMap<String, Double>();
      neqsim.thermo.atomelement.Element elems = comp.getElements();

      if (elems != null && elems.getElementNames() != null && elems.getElementNames().length > 0) {
        String[] names = elems.getElementNames();
        double[] coefs = elems.getElementCoefs();
        for (int k = 0; k < names.length; k++) {
          elementSet.add(names[k]);
          elemMap.put(names[k], coefs[k]);
        }
      } else {
        // No element data: assign a unique pseudo-element for mass conservation
        String pseudoElem = "Pseudo_" + componentNames[i];
        elementSet.add(pseudoElem);
        elemMap.put(pseudoElem, 1.0);
      }
      componentElements.add(elemMap);
    }

    // Build element name array
    elementNames = elementSet.toArray(new String[0]);
    numElements = elementNames.length;

    // Build A matrix
    A = new double[numElements][numComponents];
    for (int i = 0; i < numComponents; i++) {
      Map<String, Double> elemMap = componentElements.get(i);
      for (int k = 0; k < numElements; k++) {
        Double coef = elemMap.get(elementNames[k]);
        if (coef != null) {
          A[k][i] = coef;
        }
      }
    }
  }

  /**
   * Construct formula matrix from explicit data.
   *
   * @param elementNames array of element names
   * @param componentNames array of component names
   * @param A the formula matrix A[NE][NC]
   */
  public FormulaMatrix(String[] elementNames, String[] componentNames, double[][] A) {
    this.elementNames = elementNames.clone();
    this.componentNames = componentNames.clone();
    this.numElements = elementNames.length;
    this.numComponents = componentNames.length;
    this.A = new double[numElements][numComponents];
    for (int k = 0; k < numElements; k++) {
      for (int i = 0; i < numComponents; i++) {
        this.A[k][i] = A[k][i];
      }
    }
  }

  /**
   * Compute the total element vector b from mole fractions z.
   *
   * <p>
   * b[k] = sum_i A[k][i] * z[i]
   * </p>
   *
   * @param z mole fractions (length NC)
   * @return element vector b (length NE)
   */
  public double[] computeElementVector(double[] z) {
    double[] b = new double[numElements];
    for (int k = 0; k < numElements; k++) {
      for (int i = 0; i < numComponents; i++) {
        b[k] += A[k][i] * z[i];
      }
    }
    return b;
  }

  /**
   * Return the formula matrix.
   *
   * @return A[NE][NC]
   */
  public double[][] getMatrix() {
    return A;
  }

  /**
   * Return a single entry.
   *
   * @param element row index
   * @param component column index
   * @return A[element][component]
   */
  public double get(int element, int component) {
    return A[element][component];
  }

  /**
   * Return element names.
   *
   * @return array of element names
   */
  public String[] getElementNames() {
    return elementNames;
  }

  /**
   * Return component names.
   *
   * @return array of component names
   */
  public String[] getComponentNames() {
    return componentNames;
  }

  /**
   * Return number of elements.
   *
   * @return NE
   */
  public int getNumberOfElements() {
    return numElements;
  }

  /**
   * Return number of components.
   *
   * @return NC
   */
  public int getNumberOfComponents() {
    return numComponents;
  }

  /**
   * Compute the rank of the formula matrix using Gaussian elimination.
   *
   * <p>
   * The rank equals the number of independent element constraints. The number of independent
   * reactions is NR = NC - rank(A).
   * </p>
   *
   * @return the rank of A
   */
  public int getRank() {
    // Work on a copy
    double[][] M = new double[numElements][numComponents];
    for (int k = 0; k < numElements; k++) {
      for (int i = 0; i < numComponents; i++) {
        M[k][i] = A[k][i];
      }
    }

    int rank = 0;
    int rows = numElements;
    int cols = numComponents;
    boolean[] rowUsed = new boolean[rows];

    for (int col = 0; col < cols && rank < rows; col++) {
      // Find pivot
      int pivotRow = -1;
      double maxVal = 1e-12;
      for (int row = 0; row < rows; row++) {
        if (!rowUsed[row] && Math.abs(M[row][col]) > maxVal) {
          maxVal = Math.abs(M[row][col]);
          pivotRow = row;
        }
      }
      if (pivotRow == -1) {
        continue;
      }
      rowUsed[pivotRow] = true;
      rank++;

      // Eliminate
      double pivot = M[pivotRow][col];
      for (int row = 0; row < rows; row++) {
        if (row != pivotRow && Math.abs(M[row][col]) > 1e-15) {
          double factor = M[row][col] / pivot;
          for (int c = col; c < cols; c++) {
            M[row][c] -= factor * M[pivotRow][c];
          }
        }
      }
    }
    return rank;
  }

  /**
   * Get the number of independent reactions.
   *
   * <p>
   * NR = NC - rank(A)
   * </p>
   *
   * @return number of independent reactions
   */
  public int getNumberOfIndependentReactions() {
    return numComponents - getRank();
  }
}
