package neqsim.chemicalreactions.chemicalequilibrium;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReactionConcentrationBasis;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * ChemicalEquilibrium class.
 *
 * <p>
 * Internal dense solver state uses EJML. Serialized instances written with the former Jama matrix fields are not
 * compatible with this representation; the public constructor and solver methods do not expose Jama types.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class ChemicalEquilibrium implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ChemicalEquilibrium.class);

  // ============ STABILITY CONSTANTS ============
  /** Minimum moles to prevent log(0) and division by zero. */
  private static final double MIN_MOLES = 1e-60;

  /** Maximum iterations allowed for chemical equilibrium solver. */
  private static final int MAX_ITERATIONS = 100;

  /** Counter for consecutive non-improving iterations to detect stagnation. */
  private static final int STAGNATION_LIMIT = 10;

  /** Relative conservation residual above which a single-phase Newton step restores the constraint manifold. */
  private static final double CONSERVATION_CORRECTION_TOLERANCE = 1e-8;

  /**
   * Iteration threshold to switch from simple to derivative-based iterations. First iterations use simple M_matrix
   * (cheap), then switch to derivatives for faster quadratic convergence near the solution.
   */
  private static final int DERIVATIVE_SWITCH_ITERATION = 5;

  /**
   * Error threshold to switch to derivative-based iterations. When relative error falls below this, use derivatives for
   * faster convergence.
   */
  private static final double DERIVATIVE_SWITCH_ERROR = 1e-3;

  /**
   * Flag to enable fugacity coefficient derivatives in M_matrix. When true, uses init(3) for derivative calculations
   * and includes dln(fugacity)/dN terms for more accurate Newton steps. Default is false for backward compatibility and
   * performance.
   */
  private boolean useFugacityDerivatives = false;

  /**
   * Flag to enable automatic switching to derivatives after initial iterations. When true, starts with simple
   * iterations then switches to derivatives for faster convergence.
   */
  private boolean useAdaptiveDerivatives = false;

  /**
   * Flag to enable the full Smith-Missen M-matrix with the -1/n_t coupling term. When true, uses M_ij = δ_ij/n_i -
   * 1/n_t instead of the simplified M_ij = δ_ij/n_i. The full form provides better quadratic convergence near the
   * solution but may be less stable for some systems. Default is false for backward compatibility and stability.
   */
  private boolean useFullMMatrix = false;

  // ============ CONFIGURABLE SOLVER PARAMETERS ============
  /** Maximum iterations allowed (configurable). Default is MAX_ITERATIONS. */
  private int maxIterations = MAX_ITERATIONS;

  /** Convergence tolerance for relative mole change. Default is 1e-8. */
  private double convergenceTolerance = 1e-8;

  // ============ SOLVER METRICS (from last solve) ============
  /** Number of iterations from the last solve() call. */
  private int lastIterationCount = 0;

  /** Final relative error from the last solve() call. */
  private double lastError = Double.NaN;

  /** Whether the last solve() call converged successfully. */
  private boolean lastConverged = false;

  SystemInterface system;
  double[] nVector;
  double[] n_mol;
  double d_n_t = 0;
  int NSPEC = 2;
  int NELE = 2;
  double R = ThermodynamicConstantsInterface.R;
  DMatrixRMaj x_solve;
  double y_solve;
  double n_t = 0.0;
  double solventWeight = MIN_MOLES;
  double agemo = 0;
  double kronDelt = 0;

  ComponentInterface[] components;
  double[][] M_matrix = new double[NSPEC][NSPEC];
  DMatrixRMaj M_matrixDense;
  DMatrixRMaj A_matrixDense;
  DMatrixRMaj nmu;
  DMatrixRMaj AMA_matrix;
  DMatrixRMaj dn_matrix;
  DMatrixRMaj AMU_matrix;
  DMatrixRMaj Alambda_matrix;
  double[] d_n = new double[NSPEC];
  double[] logactivityVec = new double[NSPEC];
  double[] n0;
  double[][] A_matrix;
  double[] chem_ref;
  int waterNumb = 0;
  int upMoles = 0;
  // double chem_pot_dilute[];
  // double chem_pot_pure[];
  double[] b_element;
  DMatrixRMaj b_matrix;

  DMatrixRMaj A_solve;
  DMatrixRMaj b_solve;
  double[] chem_pot;
  DMatrixRMaj chem_pot_matrix;
  int phasenumb = 1;

  /**
   * Constructor for ChemicalEquilibrium.
   *
   * @param A_matrix an array of type double
   * @param b_element an array of type double
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param phaseNum a int
   */
  public ChemicalEquilibrium(double[][] A_matrix, double[] b_element, SystemInterface system,
      ComponentInterface[] components, int phaseNum) {
    this.system = system;
    phasenumb = phaseNum;
    this.A_matrix = A_matrix;
    this.b_element = b_element;
    this.components = components;
    NSPEC = components.length; // Number of Species
    NELE = b_element.length; // Number of elements
    n_mol = new double[components.length];
    logactivityVec = new double[NSPEC];
    this.chem_ref = new double[components.length];
    A_solve = new DMatrixRMaj(NELE + 1, NELE + 1);
    b_solve = new DMatrixRMaj(NELE + 1, 1);
    chem_ref = new double[NSPEC];
    chem_pot = new double[NSPEC];
    // chem_pot_dilute = new double[NSPEC];
    // chem_pot_pure = new double[NSPEC];
    M_matrix = new double[NSPEC][NSPEC];
    d_n = new double[NSPEC];
    if (A_matrix != null) {
      A_matrixDense = new DMatrixRMaj(A_matrix);
    }
    if (b_element != null) {
      b_matrix = rowVector(b_element);
    }

    for (int i = 0; i < components.length; i++) {
      if (components[i].getComponentName().equals("water")) {
        waterNumb = i;
        break;
      }
    }
    system.init(1, phasenumb);
    calcRefPot();
    for (int j = 0; j < NSPEC; j++) {
      d_n[j] = 0;
    }
  }

  /**
   * calcRefPot.
   */
  public void calcRefPot() {
    for (int i = 0; i < components.length; i++) {
      // calculates the reduced chemical potential mu/RT
      this.chem_ref[i] = components[i].getReferencePotential() / (R * system.getPhase(phasenumb).getTemperature());
      logactivityVec[i] = 0.0;
      if (components[i].calcActivity()) {
        logactivityVec[i] = system.getPhase(phasenumb).getLogActivityCoefficient(components[i].getComponentNumber(),
            components[waterNumb].getComponentNumber());
        // System.out.println("activity " + Math.exp(logactivityVec[i]) + " " +
        // components[i].getComponentName());
      }
    }
  }

  /**
   * chemSolve.
   */
  public void chemSolve() {
    // Protect against n_t = 0 which would cause division by zero in chem_pot calculation
    n_t = Math.max(MIN_MOLES, system.getPhase(phasenumb).getNumberOfMolesInPhase());
    solventWeight = calculateSolventWeight(system.getPhase(phasenumb));

    // If using fugacity derivatives, need init(3) for derivative calculations
    if (useFugacityDerivatives) {
      try {
        system.init(3, phasenumb);
      } catch (Exception ex) {
        logger.debug("Failed to init(3) for derivatives, falling back to simple M_matrix");
      }
    }

    for (int i = 0; i < NSPEC; i++) {
      n_mol[i] = system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
          .getNumberOfMolesInPhase();

      for (int k = 0; k < NSPEC; k++) {
        if (k == i) {
          kronDelt = 1.0;
        } else {
          kronDelt = 0.0;
        }
        // M_matrix definition: M_ij = δ_ij/n_i for the simplified ideal case
        // The full Smith-Missen formulation uses M_ij = δ_ij/n_i - 1/n_t
        // The -1/n_t term couples all species through total moles and improves convergence
        // near the solution, but may be less stable for some systems.
        // Protect against division by zero using MIN_MOLES
        double molesForDiv = Math.max(MIN_MOLES,
            system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
        M_matrix[i][k] = kronDelt / molesForDiv;

        // Add the -1/n_t coupling term if full M-matrix is enabled
        if (useFullMMatrix) {
          M_matrix[i][k] -= 1.0 / Math.max(MIN_MOLES, n_t);
        }

        // Add fugacity coefficient derivative if enabled
        // dfugdn contains: δ_ij/n_i - 1/n_t + ∂ln(φ_i)/∂n_j
        // The original M_matrix is just δ_ij/n_i (ideal, no mixing term)
        // To add non-ideal effects, we need ∂ln(φ_i)/∂n_j = dfugdn - δ_ij/n_i + 1/n_t
        if (useFugacityDerivatives) {
          try {
            int compNumI = components[i].getComponentNumber();
            int compNumK = components[k].getComponentNumber();
            double dfugdN = system.getPhase(phasenumb).getComponent(compNumI).getdfugdn(compNumK);
            if (!Double.isNaN(dfugdN) && !Double.isInfinite(dfugdN)) {
              // Extract just the non-ideal part: ∂ln(φ)/∂n = dfugdn - δ/n +
              // 1/n_t
              double idealPart = (i == k ? 1.0 / molesForDiv : 0.0) - 1.0 / n_t;
              double nonIdealPart = dfugdN - idealPart;
              M_matrix[i][k] += nonIdealPart;
            }
          } catch (Exception ex) {
            // Derivative not available, use ideal term only
          }
        }

        // System.out.println("dfugdn "
        // +system.getPhase(phasenumb).getComponent(i).logfugcoefdNi(this.system.getPhase(phasenumb),
        // i));
        // if (i == k) System.out.println("n "
        // +system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase()
        // );
      }
    }
    // printComp();

    M_matrixDense = new DMatrixRMaj(M_matrix);

    for (int i = 0; i < NSPEC; i++) {
      // system.getPhase(phasenumb).getActivityCoefficient(components[i].getComponentNumber(),
      // components[waterNumb].getComponentNumber());

      // calculates the reduced chemical potential mu/RT
      // Protect against log(0) by ensuring minimum moles
      double molesInPhase = Math.max(MIN_MOLES,
          system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
      chem_pot[i] = chem_ref[i] + getLogReactionActivity(i, molesInPhase);
      // System.out.println("chem ref pot " + chem_pot[i]);
    }

    chem_pot_matrix = columnVector(chem_pot);

    // Use solve() instead of inverse() for numerical stability and efficiency
    // M * X = A^T -> X = M.solve(A^T), then AMA = A * X
    DMatrixRMaj M_inv_AT;
    try {
      M_inv_AT = solveDense(M_matrixDense, transpose(A_matrixDense));
    } catch (Exception e) {
      M_inv_AT = solveLeastSquares(M_matrixDense, transpose(A_matrixDense));
    }
    AMA_matrix = multiply(A_matrixDense, M_inv_AT);
    // Similarly for AMU: M * Y = mu^T -> Y = M.solve(mu^T), then AMU = A * Y
    DMatrixRMaj M_inv_mu;
    try {
      M_inv_mu = solveDense(M_matrixDense, chem_pot_matrix);
    } catch (Exception e) {
      M_inv_mu = solveLeastSquares(M_matrixDense, chem_pot_matrix);
    }
    AMU_matrix = multiply(A_matrixDense, M_inv_mu);
    DMatrixRMaj nmol = columnVector(n_mol);
    double nmuValue = 0.0;
    for (int componentIndex = 0; componentIndex < NSPEC; componentIndex++) {
      nmuValue += n_mol[componentIndex] * chem_pot[componentIndex];
    }
    nmu = new DMatrixRMaj(1, 1);
    nmu.set(0, 0, nmuValue);
    DMatrixRMaj couplingQuantities = b_matrix;
    DMatrixRMaj conservationCorrection = new DMatrixRMaj(NELE, 1);
    if (system.getNumberOfPhases() == 1) {
      DMatrixRMaj currentConservedQuantities = transpose(multiply(A_matrixDense, nmol));
      for (int elementIndex = 0; elementIndex < NELE; elementIndex++) {
        double target = b_matrix.get(0, elementIndex);
        double current = currentConservedQuantities.get(0, elementIndex);
        if (Math.abs(target - current) > CONSERVATION_CORRECTION_TOLERANCE * Math.max(1.0, Math.abs(target))) {
          couplingQuantities = currentConservedQuantities;
          conservationCorrection = transpose(subtract(b_matrix, currentConservedQuantities));
          break;
        }
      }
    }
    insert(AMA_matrix, A_solve, 0, 0);
    insert(transpose(couplingQuantities), A_solve, 0, NELE);
    insert(couplingQuantities, A_solve, NELE, 0);
    A_solve.set(NELE, NELE, 0.0);

    // A_solve.print(10,20);
    // System.out.println("Rank of A_solve "+A_solve.rank());
    insert(add(AMU_matrix, conservationCorrection), b_solve, 0, 0);
    b_solve.set(NELE, 0, nmu.get(0, 0));
    // b_solve.print(10,5);
    // System.out.println("det "+A_solve.det());

    // y_solve added by Neeraj
    y_solve = CommonOps_DDRM.det(A_solve);
    // System.out.println("Determinant "+y_solve);
    if ((y_solve < 1e-38 && y_solve > -1e-38) || y_solve < -1e70) {
      // A_solve.print(5,5);
      y_solve = CommonOps_DDRM.det(AMA_matrix);
      // System.out.println("AMA det "+y_solve);
      y_solve = numericalRank(A_solve);
      // System.out.println("Rank " + y_solve);
      // b_solve.print(5,5);
      // System.out.println("det A " + A_solve.rank());
    }

    // try catch block added by Neeraj
    // Enhanced with SVD pseudo-inverse fallback for rank-deficient matrices
    try {
      x_solve = solveDense(A_solve, b_solve);

      // Check if solution is valid (no NaN or Inf)
      boolean validSolution = true;
      for (int i = 0; i <= NELE && validSolution; i++) {
        double val = x_solve.get(i, 0);
        if (Double.isNaN(val) || Double.isInfinite(val)) {
          validSolution = false;
        }
      }

      if (!validSolution) {
        // Try pseudo-inverse for numerically unstable cases
        x_solve = solveLeastSquares(A_solve, b_solve);
      }
    } catch (Exception ex) {
      // Matrix is singular or near-singular, use pseudo-inverse
      try {
        x_solve = solveLeastSquares(A_solve, b_solve);
      } catch (Exception ex2) {
        logger.error("Both regular and least-squares solve failed: " + ex2.getMessage());
        x_solve = new DMatrixRMaj(NELE + 1, 1); // Zero solution as fallback
      }
    }
    // d_n_t = x_solve.get(NELE,0)*n_t;

    // Equation 3.115 (Smith-Missen): dn = M^-1 * (A^T * lambda - mu) + n * tau
    // Use solve() instead of inverse() for numerical stability
    DMatrixRMaj lambdaTerms = multiply(transpose(A_matrixDense), rowRange(x_solve, 0, NELE));
    DMatrixRMaj rhs = subtract(lambdaTerms, chem_pot_matrix);
    DMatrixRMaj M_inv_rhs;
    try {
      M_inv_rhs = solveDense(M_matrixDense, rhs);
    } catch (Exception e) {
      M_inv_rhs = solveLeastSquares(M_matrixDense, rhs);
    }
    DMatrixRMaj scaledMoles = columnVector(n_mol);
    CommonOps_DDRM.scale(x_solve.get(NELE, 0), scaledMoles);
    dn_matrix = add(M_inv_rhs, scaledMoles);
    d_n = dn_matrix.getData().clone();
  }

  /**
   * Calculates the logarithm of the activity term used by the chemical-equilibrium solver.
   *
   * <p>
   * The generic historical solver uses a mole-fraction standard state. Models selecting
   * {@link ChemicalReactionConcentrationBasis#SOLUTE_MOLALITY} use solute molality and retain solvent mole-fraction
   * activity. Deshmukh-Mather amine reaction constants use their existing apparent aqueous activity conversion.
   * </p>
   *
   * @param componentIndex index in the reactive component array
   * @param molesInPhase component moles in the reactive phase, lower bounded by {@link #MIN_MOLES}
   * @return logarithm of the activity contribution
   */
  private double getLogReactionActivity(int componentIndex, double molesInPhase) {
    PhaseInterface reactivePhase = system.getPhase(phasenumb);
    ComponentInterface component = components[componentIndex];
    if (system.getChemicalReactionConcentrationBasis() == ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY) {
      if ("solvent".equalsIgnoreCase(component.getReferenceStateType())) {
        return Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[componentIndex];
      }
      return Math.log(molesInPhase) - Math.log(solventWeight) + logactivityVec[componentIndex];
    }
    if (reactivePhase instanceof PhaseDesmukhMather) {
      return getLogDesmukhMatherReactionActivity((PhaseDesmukhMather) reactivePhase, component);
    }
    return Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[componentIndex];
  }

  /**
   * Calculate solvent mass for a solute-molality reaction standard state.
   *
   * @param phase reactive phase
   * @return solvent mass in kilograms, lower bounded for logarithmic evaluation
   */
  private double calculateSolventWeight(PhaseInterface phase) {
    double weight = 0.0;
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = phase.getComponent(componentIndex);
      if ("solvent".equalsIgnoreCase(component.getReferenceStateType())) {
        weight += component.getNumberOfMolesInPhase() * component.getMolarMass();
      }
    }
    return Math.max(MIN_MOLES, weight);
  }

  /**
   * Calculates the Deshmukh-Mather aqueous reaction activity on a molality basis.
   *
   * @param phase Deshmukh-Mather aqueous phase
   * @param component reactive component
   * @return logarithm of the molality-basis activity term
   */
  private double getLogDesmukhMatherReactionActivity(PhaseDesmukhMather phase, ComponentInterface component) {
    int componentNumber = component.getComponentNumber();
    if ("water".equals(component.getComponentName())) {
      double waterActivity = Math.max(MIN_MOLES, component.getx() * phase.getActivityCoefficient(componentNumber));
      return Math.log(waterActivity);
    }

    double molality = Math.max(MIN_MOLES, component.getMolality(phase));
    double gammaMolality = getDesmukhMatherMolalityActivityCoefficient(phase, component, molality);
    return Math.log(molality) + Math.log(gammaMolality);
  }

  /**
   * Converts the Deshmukh-Mather stored mole-fraction activity coefficient back to the molality scale used by the
   * original correlation.
   *
   * @param phase Deshmukh-Mather aqueous phase
   * @param component reactive component
   * @param molality component molality in mol/kg solvent
   * @return molality-scale activity coefficient, lower bounded by {@link #MIN_MOLES}
   */
  private double getDesmukhMatherMolalityActivityCoefficient(PhaseDesmukhMather phase, ComponentInterface component,
      double molality) {
    double gammaMoleFraction = Math.max(MIN_MOLES, phase.getActivityCoefficient(component.getComponentNumber()));
    double moleFraction = Math.max(MIN_MOLES, component.getx());
    double solventMolarMass = Math.max(MIN_MOLES, phase.getSolventMolarMass());
    double gammaMolality = gammaMoleFraction * moleFraction / (molality * solventMolarMass);
    return Math.max(MIN_MOLES, gammaMolality);
  }

  /**
   * updateMoles.
   *
   * <p>
   * Updates the moles in the reactive phase based on the calculated n_mol values from the Newton solver. Uses
   * Phase.addMolesChemReac with totdn=0 to only affect phase moles without corrupting the total system moles (which
   * would violate element conservation).
   * </p>
   */
  public void updateMoles() {
    upMoles++;
    for (int i = 0; i < components.length; i++) {
      int compNum = components[i].getComponentNumber();
      double currentMoles = system.getPhase(phasenumb).getComponents()[compNum].getNumberOfMolesInPhase();
      double targetMoles;
      if (n_mol[i] > MIN_MOLES) {
        targetMoles = n_mol[i];
      } else {
        // Use MIN_MOLES to maintain element balance while avoiding zero/negative moles
        // This is more consistent than arbitrary scaling which violates conservation
        targetMoles = MIN_MOLES;
      }
      double dn = targetMoles - currentMoles;

      // Use Phase.addMolesChemReac with totdn=0 to only change phase moles
      // This preserves total system moles and element conservation
      system.getPhase(phasenumb).addMolesChemReac(compNum, dn, 0);
    }

    // Update phase total moles to match sum of component moles
    double phaseTotalMoles = 0;
    for (int i = 0; i < components.length; i++) {
      phaseTotalMoles += system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
          .getNumberOfMolesInPhase();
    }
    // Also include any non-reactive components
    for (int i = 0; i < system.getPhase(phasenumb).getNumberOfComponents(); i++) {
      boolean isReactive = false;
      for (int j = 0; j < components.length; j++) {
        if (components[j].getComponentNumber() == i) {
          isReactive = true;
          break;
        }
      }
      if (!isReactive) {
        phaseTotalMoles += system.getPhase(phasenumb).getComponent(i).getNumberOfMolesInPhase();
      }
    }
    ((neqsim.thermo.phase.Phase) system.getPhase(phasenumb)).numberOfMolesInPhase = phaseTotalMoles;

    system.initBeta();
    system.init_x_y();
  }

  /**
   * solve.
   *
   * @return a boolean
   */
  public boolean solve() {
    double error = 1e10;
    double errOld = 1e10;
    double thisError = 0;
    int p = 0;
    double maxError = convergenceTolerance; // Use configurable tolerance
    upMoles = 0;
    int stagnationCount = 0;
    double bestError = Double.MAX_VALUE;

    // Save original derivative setting for adaptive mode
    boolean originalDerivativeSetting = useFugacityDerivatives;
    if (useAdaptiveDerivatives) {
      // Start without derivatives (cheaper, good for initial convergence)
      useFugacityDerivatives = false;
    }

    try {
      do {
        p++;
        errOld = error;
        error = 0.0;

        // Adaptive derivative switching: enable derivatives after initial iterations
        // or when error is small enough for quadratic convergence to help
        if (useAdaptiveDerivatives && !useFugacityDerivatives) {
          if (p >= DERIVATIVE_SWITCH_ITERATION || errOld < DERIVATIVE_SWITCH_ERROR) {
            useFugacityDerivatives = true;
            logger.debug("Chemical equilibrium: switching to derivatives at iteration " + p + ", error=" + errOld);
          }
        }

        this.chemSolve();

        // Early exit if chemSolve produced invalid results
        if (dn_matrix == null) {
          logger.warn("Chemical equilibrium: dn_matrix is null at iteration " + p);
          break;
        }

        double step1 = step();

        for (int i = 0; i < NSPEC; i++) {
          double molesInPhase = system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
              .getNumberOfMolesInPhase();

          // Skip if moles are too small to avoid division issues
          if (molesInPhase < MIN_MOLES) {
            continue;
          }

          double dnValue = dn_matrix.get(i, 0);

          // Check for NaN or Infinite values
          if (Double.isNaN(dnValue) || Double.isInfinite(dnValue)) {
            logger.debug("Chemical equilibrium: NaN/Inf detected in dn_matrix at iteration " + p);
            error = Double.NaN;
            break;
          }

          if (Math.abs(dnValue) / molesInPhase > 1e-15) {
            thisError = Math.abs(dnValue) / molesInPhase;
            error += Math.abs(thisError);
            n_mol[i] = dnValue * step1 + molesInPhase;
          }
        }

        // Exit immediately if NaN detected
        if (Double.isNaN(error) || Double.isInfinite(error)) {
          logger.debug("Chemical equilibrium: NaN/Inf error at iteration " + p + ", P=" + system.getPressure()
              + " bara, T=" + system.getTemperature() + " K");
          break;
        }

        // Track best error and detect stagnation
        if (error < bestError) {
          bestError = error;
          stagnationCount = 0;
        } else {
          stagnationCount++;
        }

        // Exit if solver is stagnating (not making progress)
        if (stagnationCount >= STAGNATION_LIMIT) {
          logger.debug("Chemical equilibrium: stagnation detected at iteration " + p + ", error=" + error);
          break;
        }

        if (error <= errOld) {
          updateMoles();

          // Save the correct moles before init(1) might corrupt them
          double[] savedMolesInPhase = new double[components.length];
          for (int i = 0; i < components.length; i++) {
            savedMolesInPhase[i] = system.getPhase(phasenumb).getComponent(components[i].getComponentNumber())
                .getNumberOfMolesInPhase();
          }

          system.init(1, phasenumb);

          // Restore the correct moles after init(1)
          for (int i = 0; i < components.length; i++) {
            double currentMoles = system.getPhase(phasenumb).getComponent(components[i].getComponentNumber())
                .getNumberOfMolesInPhase();
            if (Math.abs(currentMoles - savedMolesInPhase[i]) > 1e-15) {
              // Moles were corrupted by init(1), restore them
              double diff = savedMolesInPhase[i] - currentMoles;
              system.getPhase(phasenumb).addMolesChemReac(components[i].getComponentNumber(), diff);
            }
          }
          system.init_x_y(); // Recalculate x values to match restored moles

          calcRefPot();
        }

        // Gradually relax tolerance for difficult convergence cases
        if (p > 15) {
          maxError *= 1.5;
        }
      } while (((errOld > maxError && Math.abs(error) > maxError) && p < maxIterations) || p < 2);
    } catch (Exception ex) {
      logger.error("Chemical equilibrium solver exception: " + ex.getMessage(), ex);
      // Restore original derivative setting
      if (useAdaptiveDerivatives) {
        useFugacityDerivatives = originalDerivativeSetting;
      }
      return false;
    }

    // Restore original derivative setting after solve
    if (useAdaptiveDerivatives) {
      useFugacityDerivatives = originalDerivativeSetting;
    }

    if (p >= maxIterations) {
      logger.debug("Chemical equilibrium: max iterations (" + maxIterations + ") reached" + ", error=" + error + ", P="
          + system.getPressure() + " bara" + ", T=" + system.getTemperature() + " K");
    }

    // Always try to reinitialize system even if convergence failed
    try {
      system.init(1, phasenumb);
    } catch (Exception ex) {
      logger.debug("Chemical equilibrium: failed to reinitialize system: " + ex.getMessage());
    }

    // Ensure ionic species have physically reasonable values
    // Do this regardless of convergence status, as the solver may converge
    // with unrealistic ion concentrations
    enforceMinimumIonConcentrations();

    boolean converged = !Double.isNaN(error) && !Double.isInfinite(error) && error < maxError;

    // Store convergence metrics for external access
    lastIterationCount = p;
    lastError = error;
    lastConverged = converged;

    return converged;
  }

  /**
   * Enforce minimum physically reasonable concentrations for ionic species.
   *
   * <p>
   * When the chemical equilibrium solver fails to converge, ionic species like H3O+ and OH- can be left at
   * unrealistically low values. This method checks the water auto-ionization equilibrium (Kw = [H3O+][OH-]) and
   * corrects unrealistic values while respecting legitimate acidic or alkaline conditions.
   * </p>
   *
   * <p>
   * The method only intervenes when both H3O+ and OH- are unrealistically low (violating Kw), not when the solution is
   * legitimately acidic (high H3O+, low OH-) or alkaline (low H3O+, high OH-).
   * </p>
   */
  private void enforceMinimumIonConcentrations() {
    // Find H3O+, OH-, and water indices
    int h3oIndex = -1;
    int ohIndex = -1;
    int waterIndex = -1;

    for (int i = 0; i < components.length; i++) {
      String name = components[i].getComponentName();
      if (name.equals("H3O+")) {
        h3oIndex = i;
      } else if (name.equals("OH-")) {
        ohIndex = i;
      } else if (name.equals("water")) {
        waterIndex = i;
      }
    }

    if (h3oIndex < 0 || waterIndex < 0) {
      return; // No H3O+ or water in system
    }

    double totalMoles = system.getPhase(phasenumb).getNumberOfMolesInPhase();
    if (totalMoles <= 0) {
      return;
    }

    double currentH3OMoles = system.getPhase(phasenumb).getComponents()[components[h3oIndex].getComponentNumber()]
        .getNumberOfMolesInPhase();
    double currentH3OMoleFraction = currentH3OMoles / totalMoles;

    double currentOHMoles = 0;
    double currentOHMoleFraction = 0;
    if (ohIndex >= 0) {
      currentOHMoles = system.getPhase(phasenumb).getComponents()[components[ohIndex].getComponentNumber()]
          .getNumberOfMolesInPhase();
      currentOHMoleFraction = currentOHMoles / totalMoles;
    }

    // Water auto-ionization: Kw = [H3O+][OH-] ≈ 10^-14 at 25°C
    // In mole fraction terms for aqueous phase (~55.5 mol/L water):
    // x(H3O+) * x(OH-) ≈ (10^-14) / (55.5)^2 ≈ 3.2e-18
    // Minimum physically reasonable: either x(H3O+) or x(OH-) should be > ~10^-15
    // for extreme pH cases (pH 0-1 or pH 13-14)

    double minIonMoleFraction = 1e-15; // Absolute minimum for any ion
    double neutralH3OMoleFraction = 1e-9; // ~pH 7 in mole fraction terms

    // Check if the water equilibrium is violated (both ions unrealistically low)
    boolean h3oTooLow = currentH3OMoleFraction < minIonMoleFraction;
    boolean ohTooLow = ohIndex >= 0 && currentOHMoleFraction < minIonMoleFraction;

    // Only intervene if BOTH are too low - this indicates solver failure
    // If only one is low, it could be a legitimate acidic/alkaline solution
    if (h3oTooLow && ohTooLow) {
      // Both are unrealistically low - solver failed to establish water equilibrium
      // Set to neutral pH as a reasonable default
      double targetH3OMoles = neutralH3OMoleFraction * totalMoles;
      double molesToAdd = targetH3OMoles - currentH3OMoles;

      if (molesToAdd > 0) {
        system.addComponent(components[h3oIndex].getComponentNumber(), molesToAdd, phasenumb);

        // Set OH- to maintain Kw ≈ 10^-14 (x_H3O * x_OH ≈ 3.2e-18)
        if (ohIndex >= 0) {
          double targetOHMoleFraction = 3.2e-18 / neutralH3OMoleFraction;
          double targetOHMoles = targetOHMoleFraction * totalMoles;
          if (targetOHMoles > currentOHMoles) {
            system.addComponent(components[ohIndex].getComponentNumber(), targetOHMoles - currentOHMoles, phasenumb);
          }
        }

        reinitializeAfterIonAdjustment();
        logger.debug("Chemical equilibrium: enforced water equilibrium (both ions were too low)" + ", old x(H3O+)="
            + currentH3OMoleFraction + ", new x(H3O+)=" + neutralH3OMoleFraction);
      }
    } else if (h3oTooLow && !ohTooLow) {
      // Only H3O+ is too low but OH- is reasonable - could be alkaline solution
      // Calculate what H3O+ should be based on Kw and current OH-
      // x_H3O = Kw_molfrac / x_OH ≈ 3.2e-18 / x_OH
      double targetH3OMoleFraction = 3.2e-18 / currentOHMoleFraction;

      // Only enforce if H3O+ is MUCH lower than what Kw predicts (factor of 1000)
      if (currentH3OMoleFraction < targetH3OMoleFraction * 1e-3) {
        double targetH3OMoles = targetH3OMoleFraction * totalMoles;
        double molesToAdd = targetH3OMoles - currentH3OMoles;
        if (molesToAdd > 0) {
          system.addComponent(components[h3oIndex].getComponentNumber(), molesToAdd, phasenumb);
          reinitializeAfterIonAdjustment();
          logger.debug("Chemical equilibrium: adjusted H3O+ for alkaline solution" + ", old x(H3O+)="
              + currentH3OMoleFraction + ", new x(H3O+)=" + targetH3OMoleFraction);
        }
      }
    }
    // If OH- is too low but H3O+ is reasonable (acidic solution), don't intervene
    // The solution is legitimately acidic
  }

  /**
   * Reinitialize the system after ion concentration adjustment.
   */
  private void reinitializeAfterIonAdjustment() {
    try {
      system.initBeta();
      system.init_x_y();
      system.init(1, phasenumb);
    } catch (Exception ex) {
      logger.debug("Chemical equilibrium: failed to reinitialize after ion adjustment: " + ex.getMessage());
    }
  }

  /**
   * Check if fugacity coefficient derivatives are used in the M_matrix calculation.
   *
   * @return true if fugacity derivatives are enabled
   */
  public boolean isUseFugacityDerivatives() {
    return useFugacityDerivatives;
  }

  /**
   * Enable or disable fugacity coefficient derivatives in M_matrix calculation.
   *
   * <p>
   * When enabled, the solver uses init(3, phase) to calculate fugacity derivatives and includes dln(fugacity)/dN terms
   * in the M_matrix for more accurate Newton steps. This can improve convergence for non-ideal mixtures but is
   * computationally more expensive.
   * </p>
   *
   * @param useFugacityDerivatives true to enable, false to disable
   */
  public void setUseFugacityDerivatives(boolean useFugacityDerivatives) {
    this.useFugacityDerivatives = useFugacityDerivatives;
  }

  /**
   * Check if adaptive derivative switching is enabled.
   *
   * @return true if adaptive derivatives are enabled
   */
  public boolean isUseAdaptiveDerivatives() {
    return useAdaptiveDerivatives;
  }

  /**
   * Enable or disable adaptive derivative switching.
   *
   * <p>
   * When enabled, the solver starts with simple iterations (without fugacity derivatives) for the first few iterations,
   * then automatically switches to derivative-based iterations for faster quadratic convergence near the solution. This
   * combines the robustness of simple iterations with the speed of derivative-based methods.
   * </p>
   *
   * <p>
   * The switch occurs after {@code DERIVATIVE_SWITCH_ITERATION} iterations or when the error falls below
   * {@code DERIVATIVE_SWITCH_ERROR}.
   * </p>
   *
   * @param useAdaptiveDerivatives true to enable adaptive switching, false to disable
   */
  public void setUseAdaptiveDerivatives(boolean useAdaptiveDerivatives) {
    this.useAdaptiveDerivatives = useAdaptiveDerivatives;
  }

  /**
   * Check if full Smith-Missen M-matrix is enabled.
   *
   * @return true if full M-matrix with -1/n_t term is enabled
   */
  public boolean isUseFullMMatrix() {
    return useFullMMatrix;
  }

  /**
   * Enable or disable the full Smith-Missen M-matrix.
   *
   * <p>
   * When enabled, the M-matrix uses the full form M_ij = δ_ij/n_i - 1/n_t instead of the simplified form M_ij =
   * δ_ij/n_i. The -1/n_t term couples all species through total moles and can improve quadratic convergence near the
   * solution, but may be less stable for some systems.
   * </p>
   *
   * @param useFullMMatrix true to enable full M-matrix, false to use simplified form
   */
  public void setUseFullMMatrix(boolean useFullMMatrix) {
    this.useFullMMatrix = useFullMMatrix;
  }

  // ============ CONFIGURABLE SOLVER PARAMETERS GETTERS/SETTERS ============

  /**
   * Get the maximum number of iterations allowed for the solver.
   *
   * @return maximum iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set the maximum number of iterations allowed for the solver.
   *
   * @param maxIterations maximum iterations (must be positive)
   */
  public void setMaxIterations(int maxIterations) {
    if (maxIterations > 0) {
      this.maxIterations = maxIterations;
    }
  }

  /**
   * Get the convergence tolerance for relative mole change.
   *
   * @return convergence tolerance
   */
  public double getConvergenceTolerance() {
    return convergenceTolerance;
  }

  /**
   * Set the convergence tolerance for relative mole change.
   *
   * @param convergenceTolerance tolerance value (must be positive)
   */
  public void setConvergenceTolerance(double convergenceTolerance) {
    if (convergenceTolerance > 0) {
      this.convergenceTolerance = convergenceTolerance;
    }
  }

  // ============ SOLVER METRICS GETTERS ============

  /**
   * Get the number of iterations from the last solve() call.
   *
   * @return iteration count from last solve
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Get the final relative error from the last solve() call.
   *
   * @return final error from last solve (NaN if solve() not yet called)
   */
  public double getLastError() {
    return lastError;
  }

  /**
   * Check if the last solve() call converged successfully.
   *
   * @return true if last solve converged, false otherwise
   */
  public boolean isLastConverged() {
    return lastConverged;
  }

  /**
   * printComp.
   */
  public void printComp() {
    for (int j = 0; j < NSPEC; j++) {
      System.out.println(" SVAR : " + n_mol[j]);
      double activity = system.getPhase(phasenumb).getActivityCoefficient(components[j].getComponentNumber(),
          components[waterNumb].getComponentNumber());
      System.out.println("act " + activity + " comp " + components[j].getComponentName());
    }
  }

  /**
   * getMoles.
   *
   * @return an array of type double
   */
  public double[] getMoles() {
    return n_mol;
  }

  /**
   * step.
   *
   * @return a double
   */
  public double step() {
    double step = 1.0;
    int i;
    int check = 0;
    double[] n_omega = new double[NSPEC];
    double[] chem_pot_omega = new double[NSPEC];
    double[] chem_pot = new double[NSPEC];
    double G_1 = 0.0;
    double G_0 = 0.0;

    for (i = 0; i < NSPEC; i++) {
      n_omega[i] = n_mol[i] + d_n[i];
      // System.out.println("nomega " + n_omega[i] );
      if (n_omega[i] < 0) {
        check = i;

        step = innerStep(i, n_omega, check, step, true);
        // System.out.println("step2 ... " + step);
        return step;
      } else {
        if (system.getChemicalReactionConcentrationBasis() == ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY) {
          double molesInPhase = Math.max(MIN_MOLES,
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + getLogReactionActivity(i, molesInPhase));
        } else if (system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()]
            .getReferenceStateType().equals("solvent")) {
          // Protect against log(0) with MIN_MOLES
          double molesInPhase = Math.max(MIN_MOLES,
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[i]);
        } else {
          // Protect against log(0) with MIN_MOLES
          double molesInPhase = Math.max(MIN_MOLES,
              system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
          chem_pot[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(molesInPhase) - Math.log(n_t) + logactivityVec[i]);
        }
        // Protect n_omega against log(0) with MIN_MOLES
        double n_omega_safe = Math.max(MIN_MOLES, n_omega[i]);
        if (system.getChemicalReactionConcentrationBasis() == ChemicalReactionConcentrationBasis.SOLUTE_MOLALITY) {
          chem_pot_omega[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + getLogReactionActivity(i, n_omega_safe));
        } else {
          chem_pot_omega[i] = R * system.getPhase(phasenumb).getTemperature()
              * (chem_ref[i] + Math.log(n_omega_safe) - Math.log(n_t) + logactivityVec[i]);
        }
      }
    }
    // Added by Neeraj
    Alambda_matrix = multiply(transpose(A_matrixDense), rowRange(x_solve, 0, NELE));

    G_1 = 0.0;
    for (i = 0; i < NSPEC; i++) {
      // G_1 += chem_pot_omega[i] * d_n[i];
      // Added by Neeraj
      // Protect against division by zero
      double n_omega_safe = Math.max(MIN_MOLES, n_omega[i]);
      G_1 += (chem_pot_omega[i] - Alambda_matrix.get(i, 0)) * d_n[i] * (1 / n_omega_safe - 1 / n_t);
    }
    // System.out.println("G1 " +G_1);

    if (G_1 > 0) {
      G_0 = 0.0;
      for (i = 0; i < NSPEC; i++) {
        // G_0 += chem_pot[i]*d_n[i];
        // Added by Neeraj
        // Protect against division by zero
        double molesInPhase = Math.max(MIN_MOLES,
            system.getPhase(phasenumb).getComponents()[components[i].getComponentNumber()].getNumberOfMolesInPhase());
        G_0 += (chem_pot[i] - Alambda_matrix.get(i, 0)) * d_n[i] * (1 / molesInPhase - 1 / n_t);
      }
      // Protect against division by zero when G_0 ≈ G_1
      double denominator = G_0 - G_1;
      if (Math.abs(denominator) > 1e-30) {
        step = G_0 / denominator;
      }
      // System.out.println("step G " + step);
    }

    step = innerStep(i, n_omega, check, step, false);
    // System.out.println("step ... " + step);

    // Clamp step to valid range [0, 1] for Newton iterations
    // Negative steps or steps > 1 can cause divergence and element conservation violations
    if (step < 0 || step > 1 || Double.isNaN(step) || Double.isInfinite(step)) {
      step = 1.0;
    }

    // BUG FIX: Return the calculated step instead of always 1.0
    // The calculated step provides damping to prevent overshooting during Newton iterations
    return step;
  }

  /**
   * innerStep.
   *
   * @param i a int
   * @param n_omega an array of type double
   * @param check a int
   * @param step a double
   * @param test a boolean
   * @return a double
   */
  public double innerStep(int i, double[] n_omega, int check, double step, boolean test) {
    if (test) {
      agemo = (-n_mol[i] / d_n[i]) * (1.0 - 0.03);

      for (i = check; i < NSPEC; i++) {
        n_omega[i] = n_mol[i] + d_n[i];

        if (n_omega[i] < 0) {
          step = (-n_mol[i] / d_n[i]) * (1.0 - 0.03);
          if (step < agemo) {
            agemo = step;
          }
        }
      }

      step = agemo;

      if (step > 1) {
        step = 1.0;
      }
    }
    return step;
  }

  /**
   * Solve least-squares problem using SVD pseudo-inverse.
   *
   * <p>
   * For rank-deficient or ill-conditioned matrices, this provides a more robust solution than direct inversion. Uses
   * SVD decomposition: A = U * S * V^T, then x = V * S^(-1) * U^T * b.
   * </p>
   *
   * @param matrixA the coefficient matrix
   * @param matrixB the right-hand side matrix
   * @return the least-squares solution x
   */
  static DMatrixRMaj solveLeastSquares(DMatrixRMaj matrixA, DMatrixRMaj matrixB) {
    int rows = matrixA.getNumRows();
    int columns = matrixA.getNumCols();
    SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(rows, columns, true, true, false);
    if (!svd.decompose(matrixA.copy())) {
      throw new IllegalStateException("Chemical-equilibrium singular value decomposition did not converge");
    }
    double[] singularValues = svd.getSingularValues();
    int singularValueCount = svd.numberOfSingularValues();
    double largestSingularValue = 0.0;
    for (int index = 0; index < singularValueCount; index++) {
      largestSingularValue = Math.max(largestSingularValue, singularValues[index]);
    }
    double tolerance = 1e-12 * (singularValueCount > 0 ? largestSingularValue : 1.0);

    DMatrixRMaj inverseSingularValues = new DMatrixRMaj(columns, rows);
    for (int index = 0; index < Math.min(rows, columns); index++) {
      if (Math.abs(singularValues[index]) > tolerance) {
        inverseSingularValues.set(index, index, 1.0 / singularValues[index]);
      }
    }

    DMatrixRMaj leftVectors = svd.getU(null, false);
    DMatrixRMaj rightVectors = svd.getV(null, false);
    DMatrixRMaj projectedRightHandSide = multiply(transpose(leftVectors), matrixB);
    DMatrixRMaj solution = multiply(multiply(rightVectors, inverseSingularValues), projectedRightHandSide);
    requireFiniteSolution(solution, "SVD least-squares solve");
    return solution;
  }

  /**
   * Solves a square dense system with EJML LU and supports multiple right-hand sides.
   *
   * @param matrixA square coefficient matrix
   * @param matrixB right-hand side matrix
   * @return the solution matrix
   * @throws IllegalStateException if factorization fails or the solution contains a non-finite value
   */
  private static DMatrixRMaj solveDense(DMatrixRMaj matrixA, DMatrixRMaj matrixB) {
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(matrixA.getNumRows());
    if (!solver.setA(matrixA.copy())) {
      throw new IllegalStateException("Chemical-equilibrium LU factorization failed");
    }
    DMatrixRMaj solution = new DMatrixRMaj(matrixA.getNumCols(), matrixB.getNumCols());
    solver.solve(matrixB, solution);
    // EJML LU can accept a singular matrix and return infinities. Throw so the existing SVD fallback is used.
    requireFiniteSolution(solution, "LU solve");
    return solution;
  }

  /**
   * Rejects an invalid linear solve before it can contaminate the chemical-potential and mole updates.
   *
   * @param solution computed solution matrix
   * @param operation description of the linear solve
   * @throws IllegalStateException if a solution entry is not finite
   */
  private static void requireFiniteSolution(DMatrixRMaj solution, String operation) {
    for (int index = 0; index < solution.getNumElements(); index++) {
      if (!Double.isFinite(solution.get(index))) {
        throw new IllegalStateException("Chemical-equilibrium " + operation + " produced a non-finite solution");
      }
    }
  }

  /**
   * Computes the numerical rank using Jama's dimension-scaled machine-precision threshold.
   *
   * @param matrix matrix to inspect
   * @return numerical rank
   */
  private static int numericalRank(DMatrixRMaj matrix) {
    SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(matrix.getNumRows(),
        matrix.getNumCols(), false, false, true);
    if (!svd.decompose(matrix.copy())) {
      throw new IllegalStateException("Chemical-equilibrium rank decomposition did not converge");
    }
    double[] singularValues = svd.getSingularValues();
    int count = svd.numberOfSingularValues();
    double largest = 0.0;
    for (int index = 0; index < count; index++) {
      largest = Math.max(largest, singularValues[index]);
    }
    double tolerance = Math.max(matrix.getNumRows(), matrix.getNumCols()) * Math.ulp(1.0) * largest;
    int rank = 0;
    for (int index = 0; index < count; index++) {
      if (singularValues[index] > tolerance) {
        rank++;
      }
    }
    return rank;
  }

  /**
   * Creates a column vector from an array.
   *
   * @param values vector values
   * @return column vector
   */
  private static DMatrixRMaj columnVector(double[] values) {
    return new DMatrixRMaj(values.length, 1, true, values);
  }

  /**
   * Creates a row vector from an array.
   *
   * @param values vector values
   * @return row vector
   */
  private static DMatrixRMaj rowVector(double[] values) {
    return new DMatrixRMaj(1, values.length, true, values);
  }

  /**
   * Multiplies two dense matrices.
   *
   * @param matrixA left operand
   * @param matrixB right operand
   * @return matrix product
   */
  private static DMatrixRMaj multiply(DMatrixRMaj matrixA, DMatrixRMaj matrixB) {
    DMatrixRMaj product = new DMatrixRMaj(matrixA.getNumRows(), matrixB.getNumCols());
    CommonOps_DDRM.mult(matrixA, matrixB, product);
    return product;
  }

  /**
   * Adds two dense matrices.
   *
   * @param matrixA left operand
   * @param matrixB right operand
   * @return matrix sum
   */
  private static DMatrixRMaj add(DMatrixRMaj matrixA, DMatrixRMaj matrixB) {
    DMatrixRMaj sum = new DMatrixRMaj(matrixA.getNumRows(), matrixA.getNumCols());
    CommonOps_DDRM.add(matrixA, matrixB, sum);
    return sum;
  }

  /**
   * Subtracts one dense matrix from another.
   *
   * @param matrixA minuend
   * @param matrixB subtrahend
   * @return matrix difference
   */
  private static DMatrixRMaj subtract(DMatrixRMaj matrixA, DMatrixRMaj matrixB) {
    DMatrixRMaj difference = new DMatrixRMaj(matrixA.getNumRows(), matrixA.getNumCols());
    CommonOps_DDRM.subtract(matrixA, matrixB, difference);
    return difference;
  }

  /**
   * Transposes a dense matrix.
   *
   * @param matrix matrix to transpose
   * @return transposed matrix
   */
  private static DMatrixRMaj transpose(DMatrixRMaj matrix) {
    DMatrixRMaj transposed = new DMatrixRMaj(matrix.getNumCols(), matrix.getNumRows());
    CommonOps_DDRM.transpose(matrix, transposed);
    return transposed;
  }

  /**
   * Inserts a dense block at the specified row and column offsets.
   *
   * @param block matrix block to insert
   * @param destination matrix receiving the block
   * @param row row offset
   * @param column column offset
   */
  private static void insert(DMatrixRMaj block, DMatrixRMaj destination, int row, int column) {
    CommonOps_DDRM.insert(block, destination, row, column);
  }

  /**
   * Copies a contiguous range of entries from a column vector.
   *
   * @param matrix source column vector
   * @param firstRow inclusive first row
   * @param endRow exclusive end row
   * @return the selected column vector
   */
  private static DMatrixRMaj rowRange(DMatrixRMaj matrix, int firstRow, int endRow) {
    DMatrixRMaj range = new DMatrixRMaj(endRow - firstRow, matrix.getNumCols());
    CommonOps_DDRM.extract(matrix, firstRow, endRow, 0, matrix.getNumCols(), range, 0, 0);
    return range;
  }
}
