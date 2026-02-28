package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Multiphase Gibbs reactor for chemical equilibrium with multiple phases.
 *
 * <p>
 * This reactor extends the single-phase Gibbs reactor to handle multiple phases, where each phase
 * can use a different thermodynamic model for fugacity/activity calculations.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Specify number of phases expected</li>
 * <li>Each phase uses a different thermodynamic model (SRK, Pitzer, eNRTL, etc.)</li>
 * <li>Coupled phase and chemical equilibrium via Gibbs minimization</li>
 * <li>Extended Jacobian for multi-phase calculations</li>
 * </ul>
 *
 * <h2>Mathematical Formulation</h2>
 * <p>
 * For each component i in phase p, the chemical potential is:
 * </p>
 * 
 * <pre>
 * μ_i^p = μ_i^0 + RT * ln(x_i^p * φ_i^p * P)
 * </pre>
 *
 * <p>
 * At equilibrium:
 * </p>
 * <ul>
 * <li>Chemical equilibrium: ∂G/∂ξ = 0 (Gibbs minimization)</li>
 * <li>Phase equilibrium: f_i^1 = f_i^2 = ... for all phases</li>
 * </ul>
 *
 * <h2>Jacobian Structure</h2>
 * <p>
 * The Jacobian has the following structure for N components and P phases:
 * </p>
 * 
 * <pre>
 * For diagonal (same component, same phase):
 *   ∂F_i^p/∂n_i^p = RT * (1/n_i^p - 1/n_total^p) + RT * ∂ln(φ_i^p)/∂n_i^p
 *
 * For off-diagonal (different component, same phase):
 *   ∂F_i^p/∂n_j^p = -RT/n_total^p + RT * ∂ln(φ_i^p)/∂n_j^p
 *
 * Cross-phase derivatives from phase equilibrium constraints.
 * </pre>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * MultiphaseGibbsReactor reactor = new MultiphaseGibbsReactor("reactor", inletStream);
 * reactor.setNumberOfPhases(2);
 * reactor.setPhaseModel(0, "SRK"); // Gas phase
 * reactor.setPhaseModel(1, "Pitzer"); // Aqueous phase
 * reactor.run();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see GibbsReactor
 */
public class MultiphaseGibbsReactor extends GibbsReactor {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MultiphaseGibbsReactor.class);

  /** Universal gas constant in kJ/(mol·K). */
  private static final double R_KJ = 8.314462618e-3;

  /** Minimum mole amount to prevent numerical issues. */
  private static final double MIN_MOLES = 1e-6;

  /** Minimum moles for Jacobian calculation to avoid extreme values. */
  private static final double MIN_JACOBIAN_MOLES = 1e-6;

  /** Number of phases in the system. */
  private int numberOfPhases = 2;

  /** Thermodynamic model name for each phase. */
  private List<String> phaseModels = new ArrayList<>();

  /** Thermodynamic system for each phase. */
  private List<SystemInterface> phaseSystems = new ArrayList<>();

  /** Moles of each component in each phase: moles[phase][component]. */
  private double[][] phaseMoles;

  /** Total moles in each phase. */
  private double[] phaseTotalMoles;

  /** Component names in the system. */
  private List<String> componentNames = new ArrayList<>();

  /** Extended Jacobian matrix for multiphase system. */
  private double[][] multiphaseJacobian;

  /** Convergence tolerance. */
  private double tolerance = 1e-8;

  /** Maximum iterations. */
  private int maxIterations = 500;

  /** Damping factor for Newton-Raphson updates. */
  private double dampingFactor = 0.0005;

  /** Enable dynamic damping adjustment. */
  private boolean dynamicDamping = true;

  /** Minimum damping factor. */
  private double minDampingFactor = 1e-8;

  /** Maximum damping factor. */
  private double maxDampingFactor = 1.0;

  /** Maximum allowable element balance error. */
  private double maxElementBalanceError = 1e-4;

  /** Whether the reactor has converged. */
  private boolean converged = false;

  /** Enable debug printing. */
  private boolean debugMode = true;

  /** Include d(ln phi)/dn term in Jacobian. If false, this term is set to 0. */
  private boolean includeFugacityDerivatives = true;

  /** Element names (O, N, C, H, S, Ar, Z). */
  private static final String[] ELEMENT_NAMES = {"O", "N", "C", "H", "S", "Ar", "Z"};

  /** Flag indicating if an element constraint was removed due to singular Jacobian. */
  private boolean elementConstraintRemoved = false;

  /** Index of removed element (if any) for debugging. */
  private int removedElementIndex = -1;

  /** Indices of active elements (elements present in the system). */
  private List<Integer> activeElements = new ArrayList<>();

  /** Element coefficients for each component: elementCoeffs[component][element] = aᵢₖ. */
  private double[][] elementCoeffs;

  /** Total moles of each element in the inlet. */
  private double[] elementTotals;

  /**
   * Set debug mode for verbose output.
   *
   * @param debug true to enable debug printing
   */
  public void setDebugMode(boolean debug) {
    this.debugMode = debug;
  }

  /**
   * Set whether to include the d(ln φ_i)/dn_j term in the Jacobian.
   *
   * <p>
   * When false, this fugacity coefficient derivative term is set to 0, which simplifies the
   * Jacobian but may affect convergence behavior.
   * </p>
   *
   * @param include true to include the term (default), false to set it to 0
   */
  public void setIncludeFugacityDerivatives(boolean include) {
    this.includeFugacityDerivatives = include;
  }

  /**
   * Check if fugacity derivatives are included in the Jacobian.
   *
   * @return true if d(ln φ_i)/dn_j term is included
   */
  public boolean isIncludeFugacityDerivatives() {
    return includeFugacityDerivatives;
  }

  /**
   * Set maximum number of iterations for convergence.
   *
   * @param maxIterations Maximum number of iterations
   */
  @Override
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get maximum number of iterations for convergence.
   *
   * @return Maximum number of iterations
   */
  @Override
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set damping factor for Newton-Raphson updates.
   *
   * @param dampingFactor Damping factor (0 to 1, typically 0.0005 to 0.1)
   */
  public void setDampingFactor(double dampingFactor) {
    this.dampingFactor = dampingFactor;
  }

  /**
   * Get damping factor for Newton-Raphson updates.
   *
   * @return Damping factor
   */
  public double getDampingFactor() {
    return dampingFactor;
  }

  /**
   * Enable or disable dynamic damping adjustment.
   *
   * @param dynamicDamping True to enable dynamic damping
   */
  public void setDynamicDamping(boolean dynamicDamping) {
    this.dynamicDamping = dynamicDamping;
  }

  /**
   * Check if dynamic damping is enabled.
   *
   * @return True if dynamic damping is enabled
   */
  public boolean isDynamicDamping() {
    return dynamicDamping;
  }

  /**
   * Set maximum allowable element balance error.
   *
   * @param maxError Maximum element balance error
   */
  public void setMaxElementBalanceError(double maxError) {
    this.maxElementBalanceError = maxError;
  }

  /**
   * Constructor for MultiphaseGibbsReactor.
   *
   * @param name Name of the reactor
   */
  public MultiphaseGibbsReactor(String name) {
    super(name);
    initializeDefaults();
  }

  /**
   * Constructor for MultiphaseGibbsReactor with inlet stream.
   *
   * @param name Name of the reactor
   * @param inletStream Inlet stream to the reactor
   */
  public MultiphaseGibbsReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
    initializeDefaults();
  }

  /**
   * Constructor for MultiphaseGibbsReactor with thermodynamic system directly.
   *
   * <p>
   * This constructor creates an internal inlet stream from the provided system.
   * </p>
   *
   * @param name Name of the reactor
   * @param thermoSystem Thermodynamic system to use as inlet
   */
  public MultiphaseGibbsReactor(String name, SystemInterface thermoSystem) {
    super(name);
    Stream inletStream = new Stream(name + "_inlet", thermoSystem);
    inletStream.run();
    setInletStream(inletStream);
    initializeDefaults();
  }

  /**
   * Initialize default settings.
   */
  private void initializeDefaults() {
    // Default: 2 phases with SRK for gas and Pitzer for liquid
    phaseModels.add("SRK");
    phaseModels.add("Pitzer");
  }

  /**
   * Set the number of phases expected in the system.
   *
   * @param numPhases Number of phases (must be at least 1)
   */
  public void setNumberOfPhases(int numPhases) {
    if (numPhases < 1) {
      throw new IllegalArgumentException("Number of phases must be at least 1");
    }
    this.numberOfPhases = numPhases;
  }

  /**
   * Get the number of phases.
   *
   * @return Number of phases
   */
  public int getNumberOfPhases() {
    return numberOfPhases;
  }

  /**
   * Set the thermodynamic model for a specific phase.
   *
   * <p>
   * Supported models:
   * </p>
   * <ul>
   * <li>"SRK" - Soave-Redlich-Kwong EoS</li>
   * <li>"PR" - Peng-Robinson EoS</li>
   * <li>"Pitzer" - Pitzer activity coefficient model</li>
   * <li>"eNRTL" - Electrolyte NRTL model</li>
   * <li>"NRTL" - NRTL activity coefficient model</li>
   * <li>"CPA" - Cubic Plus Association</li>
   * <li>"ElectrolyteCPA" - Electrolyte CPA</li>
   * </ul>
   *
   * @param phaseIndex Phase index (0-based)
   * @param modelName Name of the thermodynamic model
   */
  public void setPhaseModel(int phaseIndex, String modelName) {
    if (phaseIndex < 0 || phaseIndex >= numberOfPhases) {
      throw new IndexOutOfBoundsException(
          "Phase index " + phaseIndex + " out of range [0, " + (numberOfPhases - 1) + "]");
    }

    // Extend list if necessary
    while (phaseModels.size() <= phaseIndex) {
      phaseModels.add("SRK");
    }

    phaseModels.set(phaseIndex, modelName);
  }

  /**
   * Get the thermodynamic model for a specific phase.
   *
   * @param phaseIndex Phase index (0-based)
   * @return Model name for the phase
   */
  public String getPhaseModel(int phaseIndex) {
    return phaseModels.get(phaseIndex);
  }

  /**
   * Set component moles for a specific phase directly.
   *
   * @param phaseIndex Phase index
   * @param componentIndex Component index
   * @param moles Moles of the component in this phase
   */
  public void setPhaseMoles(int phaseIndex, int componentIndex, double moles) {
    if (phaseMoles == null) {
      initializePhaseMoles();
    }
    if (phaseIndex >= 0 && phaseIndex < numberOfPhases && componentIndex >= 0
        && componentIndex < componentNames.size()) {
      phaseMoles[phaseIndex][componentIndex] = Math.max(moles, 0.0);
    }
  }

  /**
   * Get component moles for a specific phase.
   *
   * @param phaseIndex Phase index
   * @param componentIndex Component index
   * @return Moles of the component in this phase
   */
  public double getPhaseMoles(int phaseIndex, int componentIndex) {
    if (phaseMoles == null || phaseIndex < 0 || phaseIndex >= numberOfPhases || componentIndex < 0
        || componentIndex >= componentNames.size()) {
      return 0.0;
    }
    return phaseMoles[phaseIndex][componentIndex];
  }

  /**
   * Get total moles in a specific phase.
   *
   * @param phaseIndex Phase index
   * @return Total moles in the phase
   */
  public double getPhaseTotalMoles(int phaseIndex) {
    if (phaseTotalMoles == null || phaseIndex < 0 || phaseIndex >= numberOfPhases) {
      return 0.0;
    }
    return phaseTotalMoles[phaseIndex];
  }

  /**
   * Initialize mole arrays based on inlet stream.
   */
  private void initializePhaseMoles() {
    SystemInterface inlet = getInletStream().getThermoSystem();
    int numComponents = inlet.getNumberOfComponents();

    componentNames.clear();
    for (int i = 0; i < numComponents; i++) {
      componentNames.add(inlet.getComponent(i).getName());
    }

    phaseMoles = new double[numberOfPhases][numComponents];
    phaseTotalMoles = new double[numberOfPhases];

    // Initialize phases: main phase gets most moles, secondary phases get 1 mole each
    for (int i = 0; i < numComponents; i++) {
      double totalMoles = inlet.getComponent(i).getNumberOfmoles();
      if (totalMoles > MIN_MOLES) {
        // Secondary phases get 1 mole each
        double molesForSecondary = 1.0;
        // Put main amount in phase 0, minus what goes to other phases
        phaseMoles[0][i] = totalMoles - molesForSecondary * (numberOfPhases - 1);
        // Each secondary phase gets 1 mole
        for (int p = 1; p < numberOfPhases; p++) {
          phaseMoles[p][i] = molesForSecondary;
        }
      } else {
        // Component has negligible amount - distribute MIN_MOLES
        phaseMoles[0][i] = MIN_MOLES;
        for (int p = 1; p < numberOfPhases; p++) {
          phaseMoles[p][i] = MIN_MOLES;
        }
      }
    }

    // Calculate total moles per phase
    for (int p = 0; p < numberOfPhases; p++) {
      phaseTotalMoles[p] = 0.0;
      for (int i = 0; i < numComponents; i++) {
        phaseTotalMoles[p] += phaseMoles[p][i];
      }
    }

    // Debug output
    if (debugMode) {
      System.out.println("\n=== INITIALIZING PHASE MOLES ===");
      for (int p = 0; p < numberOfPhases; p++) {
        System.out.println("Phase " + p + " (" + getPhaseModel(p) + "):");
        for (int i = 0; i < numComponents; i++) {
          System.out.println("  " + componentNames.get(i) + ": " + phaseMoles[p][i] + " mol");
        }
        System.out.println("  Total: " + phaseTotalMoles[p] + " mol");
      }
    }
  }

  /**
   * Initialize element data: find active elements, build coefficient matrix, compute totals.
   *
   * <p>
   * For each component, extracts element composition from GibbsReactor's component database. Active
   * elements are those with non-zero coefficients in at least one component.
   * </p>
   */
  private void initializeElementData() {
    int N = componentNames.size();
    Map<String, GibbsComponent> compMap = getComponentMap();

    // Build element coefficient matrix: elementCoeffs[component][element]
    elementCoeffs = new double[N][7];
    for (int i = 0; i < N; i++) {
      String compName = componentNames.get(i);
      GibbsComponent gibbs = compMap.get(compName.toLowerCase());
      if (gibbs != null) {
        double[] elems = gibbs.getElements();
        System.arraycopy(elems, 0, elementCoeffs[i], 0, 7);
      } else {
        // Fallback: try to infer from component name
        inferElementsFromName(compName, elementCoeffs[i]);
      }
    }

    // Find which elements are active (have non-zero coefficients in any component)
    activeElements.clear();
    for (int k = 0; k < 7; k++) {
      boolean present = false;
      for (int i = 0; i < N; i++) {
        if (Math.abs(elementCoeffs[i][k]) > 1e-10) {
          present = true;
          break;
        }
      }
      if (present) {
        activeElements.add(k);
      }
    }

    // Check for linearly dependent element constraints
    removeRedundantElementConstraints();

    // Calculate total moles of each active element from actual phaseMoles
    // (includes MIN_MOLES added to other phases during initialization)
    elementTotals = new double[activeElements.size()];
    for (int e = 0; e < activeElements.size(); e++) {
      int elemIdx = activeElements.get(e);
      elementTotals[e] = 0.0;
      for (int p = 0; p < numberOfPhases; p++) {
        for (int i = 0; i < N; i++) {
          elementTotals[e] += phaseMoles[p][i] * elementCoeffs[i][elemIdx];
        }
      }
    }

    // Debug output
    if (debugMode) {
      System.out.println("\nActive elements after rank check: ");
      for (int e = 0; e < activeElements.size(); e++) {
        int idx = activeElements.get(e);
        System.out
            .println("  " + ELEMENT_NAMES[idx] + ", total = " + elementTotals[e] + " mol-atoms");
      }
    }
  }

  /**
   * Infer element composition from component name (fallback).
   *
   * @param compName Component name
   * @param elems Array to fill with element counts [O, N, C, H, S, Ar, Z]
   */
  private void inferElementsFromName(String compName, double[] elems) {
    String name = compName.toLowerCase();
    // Simple patterns for common components
    if (name.equals("co2") || name.equals("carbon dioxide")) {
      elems[0] = 2; // O
      elems[2] = 1; // C
    } else if (name.equals("water") || name.equals("h2o")) {
      elems[0] = 1; // O
      elems[3] = 2; // H
    } else if (name.equals("methane") || name.equals("ch4")) {
      elems[2] = 1; // C
      elems[3] = 4; // H
    } else if (name.equals("nitrogen") || name.equals("n2")) {
      elems[1] = 2; // N
    } else if (name.equals("oxygen") || name.equals("o2")) {
      elems[0] = 2; // O
    }
    // Add more as needed
  }

  /**
   * Remove linearly dependent element constraints.
   *
   * <p>
   * For a non-reactive system with N components, the element constraint matrix can have at most
   * rank N. If there are E &gt; N active elements, some constraints are redundant and will cause a
   * singular Jacobian. This method uses Gaussian elimination with partial pivoting to find and
   * remove redundant element rows.
   * </p>
   */
  private void removeRedundantElementConstraints() {
    int N = componentNames.size();
    int E = activeElements.size();

    if (E <= 1) {
      return; // Nothing to check
    }

    // Build element matrix A[e][i] = atoms of element e in component i
    double[][] workMatrix = new double[E][N];
    int[] rowMapping = new int[E];
    for (int e = 0; e < E; e++) {
      int elemIdx = activeElements.get(e);
      for (int i = 0; i < N; i++) {
        workMatrix[e][i] = elementCoeffs[i][elemIdx];
      }
      rowMapping[e] = e;
    }

    // Gaussian elimination with partial pivoting to find rank
    int rank = 0;
    java.util.List<Integer> pivotOriginalRows = new java.util.ArrayList<>();

    for (int col = 0; col < N && rank < E; col++) {
      int maxRow = -1;
      double maxVal = 1e-10;
      for (int row = rank; row < E; row++) {
        double absVal = Math.abs(workMatrix[row][col]);
        if (absVal > maxVal) {
          maxVal = absVal;
          maxRow = row;
        }
      }

      if (maxRow == -1) {
        continue;
      }

      if (maxRow != rank) {
        double[] tempRow = workMatrix[rank];
        workMatrix[rank] = workMatrix[maxRow];
        workMatrix[maxRow] = tempRow;
        int tempIdx = rowMapping[rank];
        rowMapping[rank] = rowMapping[maxRow];
        rowMapping[maxRow] = tempIdx;
      }

      pivotOriginalRows.add(rowMapping[rank]);

      double pivot = workMatrix[rank][col];
      for (int row = rank + 1; row < E; row++) {
        double factor = workMatrix[row][col] / pivot;
        for (int c = col; c < N; c++) {
          workMatrix[row][c] -= factor * workMatrix[rank][c];
        }
      }
      rank++;
    }

    if (rank < E) {
      // Rebuild activeElements keeping only independent ones
      java.util.Set<Integer> pivotSet = new java.util.HashSet<>(pivotOriginalRows);
      java.util.List<Integer> newActiveElements = new java.util.ArrayList<>();

      for (int e = 0; e < E; e++) {
        if (pivotSet.contains(e)) {
          newActiveElements.add(activeElements.get(e));
        }
      }

      activeElements.clear();
      activeElements.addAll(newActiveElements);
    }
  }

  /**
   * Estimate K-value for initial phase distribution.
   *
   * @param system Thermodynamic system
   * @param compIndex Component index
   * @return Estimated K-value
   */
  private double estimateKValue(SystemInterface system, int compIndex) {
    double Tc = system.getComponent(compIndex).getTC();
    double Pc = system.getComponent(compIndex).getPC();
    double omega = system.getComponent(compIndex).getAcentricFactor();
    double T = system.getTemperature();
    double P = system.getPressure();

    // Wilson's correlation for initial K estimate
    double Tr = T / Tc;
    double Pr = P / Pc;
    double K = (Pc / P) * Math.exp(5.37 * (1.0 + omega) * (1.0 - Tc / T));

    return Math.max(K, 1e-6);
  }

  /**
   * Estimate vapor fraction for initial guess.
   *
   * @param system Thermodynamic system
   * @return Estimated vapor fraction
   */
  private double estimateVaporFraction(SystemInterface system) {
    // Use Rachford-Rice for initial estimate
    double beta = 0.5;
    for (int iter = 0; iter < 20; iter++) {
      double f = 0.0;
      double df = 0.0;
      for (int i = 0; i < system.getNumberOfComponents(); i++) {
        double z = system.getComponent(i).getz();
        double K = estimateKValue(system, i);
        double denom = 1.0 + (K - 1.0) * beta;
        f += z * (K - 1.0) / denom;
        df -= z * (K - 1.0) * (K - 1.0) / (denom * denom);
      }
      if (Math.abs(df) > 1e-10) {
        double dbeta = -f / df;
        beta = Math.max(0.0, Math.min(1.0, beta + dbeta));
      }
      if (Math.abs(f) < 1e-8) {
        break;
      }
    }
    return beta;
  }

  /**
   * Create a thermodynamic system for a specific phase.
   *
   * @param phaseIndex Phase index
   * @param T Temperature in K
   * @param P Pressure in bar
   * @return Thermodynamic system for the phase
   */
  private SystemInterface createPhaseSystem(int phaseIndex, double T, double P) {
    String modelName = getPhaseModel(phaseIndex);
    SystemInterface system;

    switch (modelName.toUpperCase()) {
      case "SRK":
        system = new neqsim.thermo.system.SystemSrkEos(T, P);
        break;
      case "PR":
        system = new neqsim.thermo.system.SystemPrEos(T, P);
        break;
      case "PITZER":
        system = new neqsim.thermo.system.SystemPitzer(T, P);
        break;
      case "ENRTL":
      case "SRK-ENRTL":
        // eNRTL not yet available, use NRTL as placeholder
        system = new neqsim.thermo.system.SystemNRTL(T, P);
        break;
      case "NRTL":
        system = new neqsim.thermo.system.SystemNRTL(T, P);
        break;
      case "CPA":
        system = new neqsim.thermo.system.SystemSrkCPA(T, P);
        break;
      case "ELECTROLYTECPA":
      case "ELECTROLYTE-CPA":
        system = new neqsim.thermo.system.SystemElectrolyteCPAstatoil(T, P);
        break;
      default:
        logger.warn("Unknown model '{}', defaulting to SRK", modelName);
        system = new neqsim.thermo.system.SystemSrkEos(T, P);
    }

    return system;
  }

  /**
   * Initialize thermodynamic systems for all phases.
   */
  private void initializePhaseSystems() {
    phaseSystems.clear();
    SystemInterface inlet = getInletStream().getThermoSystem();
    double T = inlet.getTemperature();
    double P = inlet.getPressure();

    for (int p = 0; p < numberOfPhases; p++) {
      SystemInterface phaseSystem = createPhaseSystem(p, T, P);

      // Add all components to each phase system
      for (int i = 0; i < componentNames.size(); i++) {
        String compName = componentNames.get(i);
        double moles = phaseMoles[p][i];
        try {
          phaseSystem.addComponent(compName, moles);
        } catch (Exception e) {
          logger.warn("Could not add component {} to phase {}: {}", compName, p, e.getMessage());
        }
      }

      // Set appropriate mixing rule
      try {
        String model = getPhaseModel(p);
        if (model.equalsIgnoreCase("SRK") || model.equalsIgnoreCase("PR")) {
          phaseSystem.setMixingRule("classic");
        } else if (model.equalsIgnoreCase("CPA") || model.equalsIgnoreCase("ELECTROLYTECPA")) {
          phaseSystem.setMixingRule(10);
        } else {
          phaseSystem.setMixingRule(2);
        }
      } catch (Exception e) {
        logger.warn("Could not set mixing rule for phase {}: {}", p, e.getMessage());
      }

      phaseSystems.add(phaseSystem);

      // Force single-phase behavior and set correct EOS root (gas vs liquid)
      // The order is critical: setNumberOfPhases -> init(0) -> setPhaseType -> init(3)
      phaseSystem.setNumberOfPhases(1);
      phaseSystem.setMaxNumberOfPhases(1);
      phaseSystem.setForcePhaseTypes(true);
      phaseSystem.init(0);
      if (p == 0) {
        phaseSystem.setPhaseType(0, PhaseType.GAS);
      } else {
        phaseSystem.setPhaseType(0, PhaseType.LIQUID);
      }
      phaseSystem.init(3);
    }
  }

  /**
   * Calculate fugacity coefficient for a component in a specific phase.
   *
   * @param phaseIndex Phase index
   * @param compIndex Component index
   * @return Fugacity coefficient
   */
  private double calculateFugacityCoefficient(int phaseIndex, int compIndex) {
    if (phaseIndex < 0 || phaseIndex >= phaseSystems.size()) {
      return 1.0;
    }

    SystemInterface phaseSystem = phaseSystems.get(phaseIndex);
    try {
      // Ensure correct phase type and EOS root
      // Order: init(0) -> setPhaseType -> init(3)
      phaseSystem.setNumberOfPhases(1);
      phaseSystem.setMaxNumberOfPhases(1);
      phaseSystem.setForcePhaseTypes(true);
      phaseSystem.init(0);
      if (phaseIndex == 0) {
        phaseSystem.setPhaseType(0, PhaseType.GAS);
      } else {
        phaseSystem.setPhaseType(0, PhaseType.LIQUID);
      }
      phaseSystem.init(3);
      return phaseSystem.getPhase(0).getComponent(compIndex).getFugacityCoefficient();
    } catch (Exception e) {
      logger.debug("Fugacity calculation failed for phase {} comp {}: {}", phaseIndex, compIndex,
          e.getMessage());
      return 1.0;
    }
  }

  /**
   * Calculate the extended Jacobian matrix for multiphase Gibbs minimization.
   *
   * <p>
   * Uses Lagrangian formulation with block structure like GibbsReactor:
   * </p>
   * <ul>
   * <li>Variables: [n^phase1, n^phase2, ..., n^phaseP, lambda] = N*P + N variables</li>
   * <li>Equations: N equations per phase (Gibbs conditions) + N material balances</li>
   * </ul>
   *
   * <p>
   * Block structure for 2 phases:
   * </p>
   * 
   * <pre>
   *          | Phase 1 (n^gas) | Phase 2 (n^liq) | Mass Bal (lambda) |
   * ---------|-----------------|-----------------|-------------------|
   * Phase 1  |    [Gibbs_1]    |       [0]       |       [I]         |
   * ---------|-----------------|-----------------|-------------------|
   * Phase 2  |       [0]       |    [Gibbs_2]    |       [I]         |
   * ---------|-----------------|-----------------|-------------------|
   * Mass Bal |       [I]       |       [I]       |       [0]         |
   * </pre>
   * 
   * <p>
   * Where Gibbs_p = d(mu_i^p)/d(n_j^p) is the standard Gibbs reactor Jacobian for phase p
   * </p>
   */
  private void calculateMultiphaseJacobian() {
    int N = componentNames.size();
    int P = numberOfPhases;
    int E = activeElements.size();

    // Lagrangian formulation: N*P phase variables + E element Lagrange multipliers
    int size = N * P + E;
    multiphaseJacobian = new double[size][size];

    // Initialize lambda array if needed (one per active element)
    if (lambda == null || lambda.length != E) {
      lambda = new double[E];
    }

    double T = getInletStream().getThermoSystem().getTemperature();
    double Pres = getInletStream().getThermoSystem().getPressure();
    double RT = R_KJ * T;

    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("CALCULATING BLOCK-STRUCTURED JACOBIAN MATRIX");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println("N = " + N + " components, P = " + P + " phases, E = " + E + " elements");
      System.out.println("Variables: N*P + E = " + (N * P) + " + " + E + " = " + size);
      System.out.println("T = " + T + " K, P = " + Pres + " bar, RT = " + RT + " kJ/mol");

      // Print current state
      System.out.println("\n--- Current Mole Distribution ---");
      for (int p = 0; p < P; p++) {
        String phaseName = (p == 0) ? "gas" : "liq";
        System.out.println("Phase " + p + " (" + phaseName + ", " + getPhaseModel(p) + "):");
        for (int i = 0; i < N; i++) {
          System.out.println("  n[" + componentNames.get(i) + "] = " + phaseMoles[p][i] + " mol");
        }
        System.out.println("  Total: " + phaseTotalMoles[p] + " mol");
      }
      System.out.println("Lambda (Lagrange multipliers per element):");
      for (int e = 0; e < E; e++) {
        int elemIdx = activeElements.get(e);
        System.out.println("  lambda[" + ELEMENT_NAMES[elemIdx] + "] = " + lambda[e]);
      }

      System.out.println("\n--- Initializing Phase Systems ---");
    }
    for (int p = 0; p < P; p++) {
      SystemInterface phaseSystem = phaseSystems.get(p);
      try {
        // Force correct phase type with proper sequence:
        // setNumberOfPhases -> setForcePhaseTypes -> init(0) -> setPhaseType -> init(3)
        phaseSystem.setNumberOfPhases(1);
        phaseSystem.setMaxNumberOfPhases(1);
        phaseSystem.setForcePhaseTypes(true);
        phaseSystem.init(0);
        if (p == 0) {
          phaseSystem.setPhaseType(0, PhaseType.GAS);
        } else {
          phaseSystem.setPhaseType(0, PhaseType.LIQUID);
        }
        phaseSystem.init(3);
        if (debugMode) {
          System.out.println(
              "Phase " + p + " initialized as " + phaseSystem.getPhase(0).getPhaseTypeName());
        }
      } catch (Exception e) {
        if (debugMode) {
          System.out.println("Phase " + p + " init FAILED: " + e.getMessage());
        }
      }
    }

    // BLOCK 1: Phase diagonal blocks (Gibbs-like for each phase)
    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("BLOCK 1: Phase Diagonal Blocks - dμ_i^p / dn_j^p");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println(
          "Formula for DIAGONAL (i==j): J[row,col] = RT * (1/n_i - 1/n_phase + d(ln φ_i)/dn_j)");
      System.out
          .println("Formula for OFF-DIAG (i!=j): J[row,col] = RT * (-1/n_phase + d(ln φ_i)/dn_j)");
      System.out.println();
    }

    for (int p = 0; p < P; p++) {
      double nPhase = Math.max(phaseTotalMoles[p], MIN_MOLES);
      SystemInterface phaseSystem = phaseSystems.get(p);
      String phaseName = (p == 0) ? "gas" : "liq";

      // Update phase system with current mole amounts BEFORE computing dfugdn
      // This is critical - dfugdn depends on the mole fractions in the phase
      try {
        double totalMoles = 0.0;
        for (int i = 0; i < N; i++) {
          totalMoles += phaseMoles[p][i];
        }
        phaseSystem.setTotalNumberOfMoles(totalMoles);
        for (int i = 0; i < N; i++) {
          phaseSystem.getComponent(i).setNumberOfmoles(phaseMoles[p][i]);
          if (phaseSystem.getPhase(0) != null) {
            phaseSystem.getPhase(0).getComponent(i).setNumberOfmoles(phaseMoles[p][i]);
            phaseSystem.getPhase(0).getComponent(i).setNumberOfMolesInPhase(phaseMoles[p][i]);
          }
        }
      } catch (Exception e) {
        // Skip if update fails
      }

      // Initialize phase system for fugacity derivatives
      try {
        phaseSystem.setNumberOfPhases(1);
        phaseSystem.setMaxNumberOfPhases(1);
        phaseSystem.setForcePhaseTypes(true);
        phaseSystem.init(0);
        if (p == 0) {
          phaseSystem.setPhaseType(0, PhaseType.GAS);
        } else {
          phaseSystem.setPhaseType(0, PhaseType.LIQUID);
        }
        phaseSystem.init(3);
      } catch (Exception e) {
        // Skip phase init error
      }

      if (debugMode) {
        System.out.println("--- Phase " + p + " (" + phaseName + ") Block ---");
        System.out.println("n_phase = " + nPhase + " mol");
        System.out.println();
      }

      int rowOffset = p * N;
      int colOffset = p * N;

      // Fill the NxN diagonal block
      for (int i = 0; i < N; i++) {
        double ni = Math.max(phaseMoles[p][i], MIN_MOLES);
        double niForJacobian = Math.max(ni, MIN_JACOBIAN_MOLES);
        int row = rowOffset + i;

        for (int j = 0; j < N; j++) {
          int col = colOffset + j;
          double dfugdn = 0.0;
          if (includeFugacityDerivatives) {
            try {
              dfugdn = phaseSystem.getPhase(0).getComponent(i).getdfugdn(j);
            } catch (Exception e) {
              dfugdn = 0.0;
            }
          }

          double dMuDn;
          if (i == j) {
            dMuDn = RT * (1.0 / niForJacobian - 1.0 / nPhase + dfugdn);
            if (debugMode) {
              System.out.println("J[" + row + "," + col + "] = dμ_" + componentNames.get(i) + "^"
                  + phaseName + " / dn_" + componentNames.get(j) + "^" + phaseName + " (DIAGONAL)");
              System.out.println("  = RT * (1/n_i - 1/n_phase + d(ln φ_i)/dn_j)");
              System.out.println(
                  "  = " + RT + " * (1/" + niForJacobian + " - 1/" + nPhase + " + " + dfugdn + ")");
              System.out.println("  = " + RT + " * (" + (1.0 / niForJacobian) + " - "
                  + (1.0 / nPhase) + " + " + dfugdn + ")");
              System.out
                  .println("  = " + RT + " * " + (1.0 / niForJacobian - 1.0 / nPhase + dfugdn));
              System.out.println("  = " + dMuDn);
            }
          } else {
            dMuDn = RT * (-1.0 / nPhase + dfugdn);
            if (debugMode) {
              System.out.println("J[" + row + "," + col + "] = dμ_" + componentNames.get(i) + "^"
                  + phaseName + " / dn_" + componentNames.get(j) + "^" + phaseName + " (OFF-DIAG)");
              System.out.println("  = RT * (-1/n_phase + d(ln φ_i)/dn_j)");
              System.out.println("  = " + RT + " * (-1/" + nPhase + " + " + dfugdn + ")");
              System.out.println("  = " + RT + " * (" + (-1.0 / nPhase) + " + " + dfugdn + ")");
              System.out.println("  = " + RT + " * " + (-1.0 / nPhase + dfugdn));
              System.out.println("  = " + dMuDn);
            }
          }
          if (debugMode) {
            System.out.println();
          }
          multiphaseJacobian[row][col] = dMuDn;
        }
      }
    }

    // BLOCK 2: Phase-to-Lambda coupling (Element coefficient matrix -A^T)
    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("BLOCK 2: Phase-to-Lambda Coupling - Coupling with Lagrange Multipliers");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println("Formula: J[row,col] = -a_ik (negative element stoichiometry)");
      System.out.println("  where a_ik = number of atoms of element k in component i");
      System.out.println();
    }

    int lambdaColOffset = N * P;
    for (int p = 0; p < P; p++) {
      int rowOffset = p * N;
      String phaseName = (p == 0) ? "gas" : "liq";
      for (int i = 0; i < N; i++) {
        int row = rowOffset + i;
        for (int e = 0; e < E; e++) {
          int elemIdx = activeElements.get(e);
          int col = lambdaColOffset + e;
          double aik = elementCoeffs[i][elemIdx];
          multiphaseJacobian[row][col] = -aik;
          if (debugMode) {
            System.out.println("J[" + row + "," + col + "] = coupling μ_" + componentNames.get(i)
                + "^" + phaseName + " to λ_" + ELEMENT_NAMES[elemIdx]);
            System.out.println("  = -a_" + componentNames.get(i) + "," + ELEMENT_NAMES[elemIdx]);
            System.out.println("  = -" + aik);
            System.out.println("  = " + (-aik));
            System.out.println();
          }
        }
      }
    }

    // BLOCK 3: Element balance rows
    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("BLOCK 3: Element Balance Rows - d(Element Balance)/dn_j^p");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println("Formula: J[row,col] = a_jk (element stoichiometry)");
      System.out.println("  where a_jk = number of atoms of element k in component j");
      System.out
          .println("Element balance: sum_p sum_j (a_jk * n_j^p) = b_k (total moles of element k)");
      System.out.println();
    }

    int elemBalRowOffset = N * P;
    for (int e = 0; e < E; e++) {
      int elemIdx = activeElements.get(e);
      int row = elemBalRowOffset + e;
      if (debugMode) {
        System.out.println(
            "--- Element " + ELEMENT_NAMES[elemIdx] + " Balance Row (row " + row + ") ---");
      }
      for (int p = 0; p < P; p++) {
        int colOffset = p * N;
        String phaseName = (p == 0) ? "gas" : "liq";
        for (int j = 0; j < N; j++) {
          int col = colOffset + j;
          double aje = elementCoeffs[j][elemIdx];
          multiphaseJacobian[row][col] = aje;
          if (debugMode) {
            System.out.println("J[" + row + "," + col + "] = d(EB_" + ELEMENT_NAMES[elemIdx]
                + ")/dn_" + componentNames.get(j) + "^" + phaseName);
            System.out.println("  = a_" + componentNames.get(j) + "," + ELEMENT_NAMES[elemIdx]);
            System.out.println("  = " + aje + " atoms of " + ELEMENT_NAMES[elemIdx] + " in "
                + componentNames.get(j));
            System.out.println();
          }
        }
      }
    }

    // ============================================================
    // Print full Jacobian matrix with block separators
    // ============================================================
    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("FULL JACOBIAN MATRIX WITH BLOCK STRUCTURE:");
      System.out.println(StringUtils.repeat("=", 80));

      // Column headers
      System.out.print("            ");
      for (int p = 0; p < P; p++) {
        String phaseName = (p == 0) ? "gas" : "liq";
        for (int j = 0; j < N; j++) {
          System.out.printf("%10s", componentNames.get(j) + "^" + phaseName);
        }
        System.out.print(" |");
      }
      for (int e = 0; e < E; e++) {
        int elemIdx = activeElements.get(e);
        System.out.printf("%10s", "lam_" + ELEMENT_NAMES[elemIdx]);
      }
      System.out.println();

      // Separator line
      System.out.print("            ");
      for (int c = 0; c < size; c++) {
        System.out.print("----------");
        if ((c + 1) % N == 0 && c < N * P) {
          System.out.print("-+");
        }
      }
      System.out.println();

      // Matrix rows
      for (int row = 0; row < size; row++) {
        // Row label
        String rowLabel;
        if (row < N) {
          rowLabel = "mu_" + componentNames.get(row) + "^gas";
        } else if (row < 2 * N) {
          rowLabel = "mu_" + componentNames.get(row - N) + "^liq";
        } else {
          int elemE = row - 2 * N;
          int elemIdx = activeElements.get(elemE);
          rowLabel = "EB_" + ELEMENT_NAMES[elemIdx];
        }
        System.out.printf("%11s ", rowLabel);

        // Matrix values
        for (int col = 0; col < size; col++) {
          System.out.printf("%10.2e", multiphaseJacobian[row][col]);
          if ((col + 1) % N == 0 && col < N * P) {
            System.out.print(" |");
          }
        }
        System.out.println();

        // Block separator lines
        if ((row + 1) % N == 0 && row < N * P) {
          System.out.print("            ");
          for (int c = 0; c < size; c++) {
            System.out.print("----------");
            if ((c + 1) % N == 0 && c < N * P) {
              System.out.print("-+");
            }
          }
          System.out.println();
        }
      }

      // Check determinant
      try {
        SimpleMatrix J = new SimpleMatrix(multiphaseJacobian);
        double det = J.determinant();
        System.out.println("\nJacobian determinant: " + det);
        if (Math.abs(det) < 1e-20) {
          System.out.println("WARNING: Jacobian is nearly SINGULAR!");
        } else {
          System.out.println("Jacobian is non-singular - good!");
        }
      } catch (Exception e) {
        System.out.println("Could not compute determinant: " + e.getMessage());
      }
    } // End of debugMode block for Jacobian printing
  }

  /** Lagrange multipliers for mass balance constraints. */
  private double[] lambda;

  /** Previous iteration total Gibbs free energy for tracking convergence. */
  private double previousGibbsEnergy = Double.NaN;

  /**
   * Calculate total Gibbs free energy of the system.
   *
   * <p>
   * G_total = sum over all phases p and components i of: n_i^p * (DG°f,i/RT + ln(phi_i * y_i * P))
   * </p>
   *
   * @return Total Gibbs free energy in kJ
   */
  private double calculateTotalGibbsEnergy() {
    return calculateTotalGibbsEnergy(false);
  }

  /**
   * Calculate total Gibbs free energy with optional detailed printing.
   *
   * @param printDetails If true, print breakdown for each component
   * @return Total Gibbs free energy in kJ
   */
  private double calculateTotalGibbsEnergy(boolean printDetails) {
    boolean shouldPrint = printDetails && debugMode;
    double T = getInletStream().getThermoSystem().getTemperature();
    double P = getInletStream().getThermoSystem().getPressure();
    double RT = R_KJ * T; // kJ/mol

    double Gtotal = 0.0;

    if (shouldPrint) {
      System.out.println("\n--- GIBBS FREE ENERGY CALCULATION ---");
      System.out.println("T = " + T + " K, P = " + P + " bar, RT = " + RT + " kJ/mol");
      System.out.println("Formula: G_i = n_i * (DG°f,i + RT*ln(phi*y*P))");
    }

    for (int p = 0; p < numberOfPhases; p++) {
      SystemInterface phaseSystem = phaseSystems.get(p);
      double nPhase = phaseTotalMoles[p];
      String phaseName = (p == 0) ? "gas" : "liq";
      double Gphase = 0.0;

      if (shouldPrint) {
        System.out.println("\nPhase " + p + " (" + phaseName + "), n_phase = " + nPhase + " mol:");
      }

      try {
        // Ensure correct phase type
        phaseSystem.setNumberOfPhases(1);
        phaseSystem.setMaxNumberOfPhases(1);
        phaseSystem.setForcePhaseTypes(true);
        phaseSystem.init(0);
        if (p == 0) {
          phaseSystem.setPhaseType(0, PhaseType.GAS);
        } else {
          phaseSystem.setPhaseType(0, PhaseType.LIQUID);
        }
        phaseSystem.init(3);

        for (int i = 0; i < componentNames.size(); i++) {
          double ni = phaseMoles[p][i];
          if (ni < MIN_MOLES) {
            continue;
          }

          double yi = ni / Math.max(nPhase, MIN_MOLES);
          double phi = phaseSystem.getPhase(0).getComponent(i).getFugacityCoefficient();
          double Gf = phaseSystem.getPhase(0).getComponent(i).getGibbsEnergyOfFormation() / 1000.0; // Convert
                                                                                                    // J/mol
                                                                                                    // to
                                                                                                    // kJ/mol

          // G_i = n_i * (DG°f,i + RT*ln(phi*y*P))
          double lnTerm = Math.log(Math.max(phi * yi * P, 1e-30));
          double Gi = ni * (Gf + RT * lnTerm);
          Gphase += Gi;

          if (shouldPrint) {
            System.out.println("  " + componentNames.get(i) + ":");
            System.out.println("    n_i = " + ni + " mol, y_i = " + yi + ", phi = " + phi);
            System.out.println("    DG°f = " + Gf + " kJ/mol");
            System.out
                .println("    ln(phi*y*P) = ln(" + phi + "*" + yi + "*" + P + ") = " + lnTerm);
            System.out.println("    G_i = " + ni + " * (" + Gf + " + " + RT + "*" + lnTerm + ")");
            System.out
                .println("         = " + ni + " * " + (Gf + RT * lnTerm) + " = " + Gi + " kJ");
          }
        }
        Gtotal += Gphase;

        if (shouldPrint) {
          System.out.println("  Phase " + p + " total G = " + Gphase + " kJ");
        }
      } catch (Exception e) {
        // Skip this phase on error
      }
    }

    if (shouldPrint) {
      System.out.println("\nG_total = " + Gtotal + " kJ");
    }

    return Gtotal;
  }

  /**
   * Calculate derivative of log fugacity coefficient with respect to moles.
   *
   * @param phaseIndex Phase index
   * @param compI Component i
   * @param compJ Component j
   * @return d(ln phi_i)/d(n_j)
   */
  private double calculateDfugDn(int phaseIndex, int compI, int compJ) {
    if (phaseIndex >= phaseSystems.size()) {
      return 0.0;
    }

    SystemInterface phaseSystem = phaseSystems.get(phaseIndex);
    try {
      // Ensure correct phase type and EOS root
      // Order: init(0) -> setPhaseType -> init(3)
      phaseSystem.setNumberOfPhases(1);
      phaseSystem.setMaxNumberOfPhases(1);
      phaseSystem.setForcePhaseTypes(true);
      phaseSystem.init(0);
      if (phaseIndex == 0) {
        phaseSystem.setPhaseType(0, PhaseType.GAS);
      } else {
        phaseSystem.setPhaseType(0, PhaseType.LIQUID);
      }
      phaseSystem.init(3);
      return phaseSystem.getPhase(0).getComponent(compI).getdfugdn(compJ);
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Calculate the objective function vector for Newton-Raphson.
   *
   * <p>
   * Uses Lagrangian formulation matching the block-structured Jacobian:
   * </p>
   * <ul>
   * <li>F_i^p = mu_i^p - sum_k(lambda_k * a_ik) for each phase p (Gibbs conditions)</li>
   * <li>G_e = sum_i sum_p (a_ie * n_i^p) - b_e (element balance)</li>
   * </ul>
   *
   * @return Objective function values (size = N*P + E)
   */
  private double[] calculateObjectiveVector() {
    int N = componentNames.size();
    int P = numberOfPhases;
    int E = activeElements.size();
    int size = N * P + E; // Phase equations + element balances
    double[] F = new double[size];

    double T = getInletStream().getThermoSystem().getTemperature();
    double Pres = getInletStream().getThermoSystem().getPressure();
    double RT = R_KJ * T;

    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("CALCULATING OBJECTIVE VECTOR F (Element-based Lagrangian)");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println("Size = N*P + E = " + (N * P) + " + " + E + " = " + size);
      System.out.println("T = " + T + " K, RT = " + RT + " kJ/mol");
    }

    // Calculate chemical potentials for all phases
    // mu_i = mu0_i + RT * ln(a_i) = mu0_i + RT * ln(x_i * gamma_i)
    // For ideal gas: mu_i = mu0_i(T) + RT * ln(P/P0) + RT * ln(y_i * phi_i)

    double[][] mu = new double[P][N]; // Chemical potentials

    for (int p = 0; p < P; p++) {
      String phaseName = (p == 0) ? "gas" : "liq";
      double nTotal = Math.max(phaseTotalMoles[p], MIN_MOLES);
      SystemInterface phaseSystem = phaseSystems.get(p);

      if (debugMode) {
        System.out.println("\n--- Phase " + p + " (" + phaseName + ") Chemical Potentials ---");
      }

      try {
        // Force correct phase type with proper sequence
        phaseSystem.setNumberOfPhases(1);
        phaseSystem.setMaxNumberOfPhases(1);
        phaseSystem.setForcePhaseTypes(true);
        phaseSystem.init(0);
        if (p == 0) {
          phaseSystem.setPhaseType(0, PhaseType.GAS);
        } else {
          phaseSystem.setPhaseType(0, PhaseType.LIQUID);
        }
        phaseSystem.init(3);

        for (int i = 0; i < N; i++) {
          double ni = Math.max(phaseMoles[p][i], MIN_MOLES);
          double xi = ni / nTotal;
          double phi = phaseSystem.getPhase(0).getComponent(i).getFugacityCoefficient();

          // Get standard Gibbs energy of formation
          double Gf = phaseSystem.getPhase(0).getComponent(i).getGibbsEnergyOfFormation() / 1000.0; // Convert
                                                                                                    // J/mol
                                                                                                    // to
                                                                                                    // kJ/mol

          // Individual terms for mu = Gf0 + RT*ln(phi) + RT*ln(P) + RT*ln(x)
          double termLnPhi = RT * Math.log(Math.max(phi, 1e-30));
          double termLnP = RT * Math.log(Math.max(Pres, 1e-30));
          double termLnX = RT * Math.log(Math.max(xi, 1e-30));

          // mu_i = Gf0 + RT * ln(phi * x * P)
          // Full chemical potential including Gibbs energy of formation
          double lnActivity = Math.log(Math.max(xi * phi * Pres, 1e-30));
          mu[p][i] = Gf + RT * lnActivity;

          if (debugMode) {
            System.out.println("  " + componentNames.get(i) + ":");
            System.out.println("    n = " + ni + " mol, x = " + xi);
            System.out.println("    phi = " + phi);
            System.out.println("    Gf0 = " + Gf + " kJ/mol");
            // Calculate element contribution: sum_k(lambda_k * a_ik)
            double lambdaSum = 0.0;
            StringBuilder lambdaTerms = new StringBuilder();
            for (int e = 0; e < E; e++) {
              int elemIdx = activeElements.get(e);
              double aik = elementCoeffs[i][elemIdx];
              if (Math.abs(aik) > 1e-10) {
                lambdaSum += lambda[e] * aik;
                if (lambdaTerms.length() > 0)
                  lambdaTerms.append(" + ");
                lambdaTerms.append(lambda[e]).append("*").append(aik).append("(")
                    .append(ELEMENT_NAMES[elemIdx]).append(")");
              }
            }
            double muValue = Gf + termLnPhi + termLnP + termLnX;
            double Fvalue = muValue - lambdaSum;
            System.out
                .println("    mu = Gf0 (" + Gf + ") + RT*ln(phi) (" + termLnPhi + ") + RT*ln(x) ("
                    + termLnX + ") + RT*ln(P) (" + termLnP + ") = " + muValue + " kJ/mol");
            System.out.println("    F = mu - sum(lambda*a) = " + muValue + " - " + lambdaSum + " = "
                + Fvalue + " kJ/mol");
          }
        }
      } catch (Exception e) {
        if (debugMode) {
          System.out.println("  Phase " + p + " calculation failed: " + e.getMessage());
        }
        for (int i = 0; i < N; i++) {
          double ni = Math.max(phaseMoles[p][i], MIN_MOLES);
          double xi = ni / nTotal;
          // Fallback: use simplified mu without Gf (less accurate)
          mu[p][i] = RT * Math.log(Math.max(xi * Pres, 1e-30));
        }
      }
    }

    // Block 1 & 2: Phase Gibbs conditions (rows 0 to N*P-1)
    // F_i^p = mu_i^p - sum_k(lambda_k * a_ik) = 0
    for (int p = 0; p < P; p++) {
      int rowOffset = p * N;
      for (int i = 0; i < N; i++) {
        int row = rowOffset + i;
        // Calculate sum_k(lambda_k * a_ik)
        double lambdaSum = 0.0;
        for (int e = 0; e < E; e++) {
          int elemIdx = activeElements.get(e);
          double aik = elementCoeffs[i][elemIdx];
          lambdaSum += lambda[e] * aik;
        }
        F[row] = mu[p][i] - lambdaSum;
      }
    }

    // Block 3: Element balance equations (rows N*P to N*P+E-1)
    // G_e = sum_i sum_p (a_ie * n_i^p) - b_e = 0
    if (debugMode) {
      System.out.println("\n--- Element Balance (per phase and total) ---");
    }
    int elemBalOffset = N * P;
    double totalElemError = 0.0;

    for (int e = 0; e < E; e++) {
      int elemIdx = activeElements.get(e);
      if (debugMode) {
        System.out.print("  " + ELEMENT_NAMES[elemIdx] + ": ");
      }

      // Calculate element atoms per phase
      double elemSum = 0.0;
      for (int p = 0; p < P; p++) {
        double phaseElem = 0.0;
        for (int i = 0; i < N; i++) {
          double aie = elementCoeffs[i][elemIdx];
          phaseElem += aie * phaseMoles[p][i];
        }
        if (debugMode) {
          System.out.print("phase" + p + "=" + phaseElem + " ");
        }
        elemSum += phaseElem;
      }

      int row = elemBalOffset + e;
      F[row] = elemSum - elementTotals[e];
      totalElemError += Math.abs(F[row]);
      if (debugMode) {
        System.out
            .println("| total=" + elemSum + ", target=" + elementTotals[e] + ", error=" + F[row]);
      }
    }
    if (debugMode) {
      System.out.println("Total element balance error: " + totalElemError);
    }

    return F;
  }

  /**
   * Calculate total component moles in the inlet.
   *
   * @return Component totals
   */
  private double[] calculateComponentTotalsIn() {
    SystemInterface inlet = getInletStream().getThermoSystem();
    int N = inlet.getNumberOfComponents();
    double[] totals = new double[N];
    for (int i = 0; i < N; i++) {
      totals[i] = inlet.getComponent(i).getNumberOfmoles();
    }
    return totals;
  }

  /**
   * Calculate total element amounts in the inlet.
   *
   * @return Element totals [O, N, C, H, S, Ar, Z]
   */
  private double[] calculateElementTotalsIn() {
    double[] totals = new double[7];
    SystemInterface inlet = getInletStream().getThermoSystem();

    for (int i = 0; i < inlet.getNumberOfComponents(); i++) {
      String compName = inlet.getComponent(i).getName();
      double moles = inlet.getComponent(i).getNumberOfmoles();
      GibbsComponent gibbsComp = getComponentMap().get(compName.toLowerCase());

      if (gibbsComp != null) {
        double[] elements = gibbsComp.getElements();
        for (int e = 0; e < 7; e++) {
          totals[e] += moles * elements[e];
        }
      }
    }

    return totals;
  }

  /**
   * Perform Newton-Raphson iteration for multiphase equilibrium.
   *
   * @return True if converged
   */
  private boolean performMultiphaseIteration() {
    int N = componentNames.size();
    int P = numberOfPhases;
    int E = activeElements.size(); // Number of active elements
    int size = N * P + E; // Lagrangian: N*P phase vars + E element lambda vars

    if (debugMode) {
      System.out.println("\n" + StringUtils.repeat("=", 80));
      System.out.println("NEWTON-RAPHSON ITERATION (Lagrangian Formulation)");
      System.out.println(StringUtils.repeat("=", 80));
      System.out.println("N = " + N + " components, P = " + P + " phases, E = " + E + " elements");
      System.out.println("Variables: N*P + E = " + (N * P) + " + " + E + " = " + size);
    }

    // Calculate and print total Gibbs free energy
    boolean isFirstIteration = Double.isNaN(previousGibbsEnergy);
    double currentGibbsEnergy = calculateTotalGibbsEnergy(isFirstIteration);
    if (debugMode) {
      System.out.println("\n--- GIBBS FREE ENERGY TRACKING ---");
      System.out.println("  Current G_total = " + currentGibbsEnergy + " kJ");
      if (!isFirstIteration) {
        double deltaG = currentGibbsEnergy - previousGibbsEnergy;
        System.out.println("  Previous G_total = " + previousGibbsEnergy + " kJ");
        System.out.println("  Delta G = " + deltaG + " kJ");
        if (deltaG > 0) {
          System.out.println("  WARNING: Gibbs energy INCREASED! (should decrease)");
        } else {
          System.out.println("  OK: Gibbs energy decreased");
        }
      } else {
        System.out.println("  (First iteration - see breakdown above)");
      }
    }
    previousGibbsEnergy = currentGibbsEnergy;

    // Initialize lambda if needed (per element)
    if (lambda == null || lambda.length != E) {
      lambda = new double[E];
      if (debugMode) {
        System.out.println("Initialized lambda array to zeros (size " + E + " for elements)");
      }
    }

    // Calculate Jacobian
    calculateMultiphaseJacobian();

    // Calculate objective vector
    double[] F = calculateObjectiveVector();

    // Check convergence
    double maxError = 0.0;
    int maxErrorIdx = 0;
    for (int i = 0; i < F.length; i++) {
      if (Math.abs(F[i]) > maxError) {
        maxError = Math.abs(F[i]);
        maxErrorIdx = i;
      }
    }

    if (debugMode) {
      System.out.println("\n--- Convergence Check ---");
      String errLabel;
      if (maxErrorIdx < N) {
        errLabel = "mu_" + componentNames.get(maxErrorIdx) + "^gas - sum(lambda*a)";
      } else if (maxErrorIdx < N * P) {
        errLabel = "mu_" + componentNames.get(maxErrorIdx - N) + "^liq - sum(lambda*a)";
      } else {
        int elemIdx = activeElements.get(maxErrorIdx - N * P);
        errLabel = "EB_" + ELEMENT_NAMES[elemIdx];
      }
      System.out
          .println("Max error: " + maxError + " at index " + maxErrorIdx + " (" + errLabel + ")");
      System.out.println("Tolerance: " + tolerance);
    }

    if (maxError < tolerance) {
      if (debugMode) {
        System.out.println("*** CONVERGED ***");
      }
      return true;
    }

    // Solve J * dx = -F using EJML with row/column scaling for numerical stability
    if (debugMode) {
      System.out.println("\n--- Solving Linear System ---");
    }
    try {
      // ============================================================
      // ROW/COLUMN EQUILIBRATION SCALING
      // ============================================================
      // This is critical for multiphase problems where phase sizes differ by orders
      // of magnitude.
      // Gas phase ~10^6 mol creates Jacobian diagonals ~10^-6, while liquid ~1 mol
      // creates diagonals ~1. Scaling equilibrates these differences.

      // Compute row scaling factors (1 / max |J[i][j]| for each row)
      double[] rowScale = new double[size];
      for (int i = 0; i < size; i++) {
        double maxVal = 0.0;
        for (int j = 0; j < size; j++) {
          double absVal = Math.abs(multiphaseJacobian[i][j]);
          if (absVal > maxVal) {
            maxVal = absVal;
          }
        }
        rowScale[i] = (maxVal > 1e-30) ? 1.0 / maxVal : 1.0;
      }

      // Apply row scaling to Jacobian
      double[][] scaledJacobian = new double[size][size];
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
          scaledJacobian[i][j] = multiphaseJacobian[i][j] * rowScale[i];
        }
      }

      // Compute column scaling factors (1 / max |J_scaled[i][j]| for each column)
      double[] colScale = new double[size];
      for (int j = 0; j < size; j++) {
        double maxVal = 0.0;
        for (int i = 0; i < size; i++) {
          double absVal = Math.abs(scaledJacobian[i][j]);
          if (absVal > maxVal) {
            maxVal = absVal;
          }
        }
        colScale[j] = (maxVal > 1e-30) ? 1.0 / maxVal : 1.0;
      }

      // Apply column scaling
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
          scaledJacobian[i][j] = scaledJacobian[i][j] * colScale[j];
        }
      }

      // Scale the RHS: F_scaled = rowScale * (-F)
      double[] scaledF = new double[size];
      for (int i = 0; i < size; i++) {
        scaledF[i] = -F[i] * rowScale[i];
      }

      SimpleMatrix Jscaled = new SimpleMatrix(scaledJacobian);

      // Check condition number of SCALED matrix
      double conditionNumber = Jscaled.conditionP2();
      if (debugMode) {
        System.out.println("Scaled Jacobian condition number: " + conditionNumber);
      }

      SimpleMatrix Fmat = new SimpleMatrix(size, 1, true, scaledF);

      // Print F vector (unscaled for readability)
      if (debugMode) {
        System.out.println("\n--- F Vector (Objective Function) ---");
        for (int i = 0; i < size; i++) {
          String label;
          if (i < N) {
            label = "F_" + componentNames.get(i) + "^gas";
          } else if (i < 2 * N) {
            label = "F_" + componentNames.get(i - N) + "^liq";
          } else {
            int elemIdx = activeElements.get(i - 2 * N);
            label = "EB_" + ELEMENT_NAMES[elemIdx];
          }
          System.out.println("  F[" + i + "] (" + label + ") = " + F[i]);
        }

        System.out.println("\nRight-hand side (-F) summary:");
        System.out.println("  Phase 1 (gas) equations: -F[0.." + (N - 1) + "]");
        System.out.println("  Phase 2 (liq) equations: -F[" + N + ".." + (2 * N - 1) + "]");
        System.out.println("  Element balance: -F[" + (2 * N) + ".." + (2 * N + E - 1) + "]");
      }

      SimpleMatrix dxScaled;
      if (conditionNumber > 1e14 || Double.isNaN(conditionNumber)
          || Double.isInfinite(conditionNumber)) {
        if (debugMode) {
          System.out
              .println("WARNING: Scaled matrix ill-conditioned (cond=" + conditionNumber + ")");
        }

        // If we haven't removed an element yet and have more than 1 element, try removing one
        if (!elementConstraintRemoved && activeElements.size() > 1) {
          // Remove the last element in the list (arbitrary choice)
          removedElementIndex = activeElements.remove(activeElements.size() - 1);
          elementConstraintRemoved = true;
          if (debugMode) {
            System.out.println("REGULARIZATION: Removed element constraint for "
                + ELEMENT_NAMES[removedElementIndex] + " to fix singular Jacobian");
            System.out.println("Remaining active elements: " + activeElements.size());
          }

          // Need to resize lambda array and recalculate
          double[] newLambda = new double[activeElements.size()];
          System.arraycopy(lambda, 0, newLambda, 0, activeElements.size());
          lambda = newLambda;

          // Signal that we need to rebuild and retry this iteration
          return false; // Will cause re-iteration with reduced system
        }

        // Already removed an element or only 1 element - use pseudo-inverse
        if (debugMode) {
          System.out.println("Using pseudo-inverse for ill-conditioned system");
        }
        dxScaled = Jscaled.pseudoInverse().mult(Fmat);
      } else {
        if (debugMode) {
          System.out.println("Scaled matrix well-conditioned, using direct solve");
        }
        dxScaled = Jscaled.solve(Fmat);
      }

      // Unscale the solution: dx = colScale * dxScaled
      SimpleMatrix dx = new SimpleMatrix(size, 1);
      for (int i = 0; i < size; i++) {
        dx.set(i, 0, dxScaled.get(i, 0) * colScale[i]);
      }

      // For debugging, compute the effective inverse (optional, expensive)
      SimpleMatrix Jinv = null;
      if (debugMode && size <= 10) { // Only compute for small systems
        Jinv = new SimpleMatrix(multiphaseJacobian).pseudoInverse();
      }

      // Print Jacobian inverse matrix (only if computed)
      if (debugMode) {
        if (Jinv != null) {
          System.out.println("\n--- Jacobian Inverse Matrix ---");
          System.out.print("            ");
          for (int c = 0; c < size; c++) {
            if (c < N) {
              System.out.printf("%12s", componentNames.get(c) + "^g");
            } else if (c < 2 * N) {
              System.out.printf("%12s", componentNames.get(c - N) + "^l");
            } else {
              int elemIdx = activeElements.get(c - 2 * N);
              System.out.printf("%12s", "lam_" + ELEMENT_NAMES[elemIdx]);
            }
          }
          System.out.println();
          for (int row = 0; row < size; row++) {
            String rowLabel;
            if (row < N) {
              rowLabel = componentNames.get(row) + "^g";
            } else if (row < 2 * N) {
              rowLabel = componentNames.get(row - N) + "^l";
            } else {
              int elemIdx = activeElements.get(row - 2 * N);
              rowLabel = "lam_" + ELEMENT_NAMES[elemIdx];
            }
            System.out.printf("%12s", rowLabel);
            for (int col = 0; col < size; col++) {
              System.out.printf("%12.4e", Jinv.get(row, col));
            }
            System.out.println();
          }
        } else {
          System.out.println("\n--- Jacobian Inverse Matrix (skipped - large system) ---");
        }

        System.out.println("\nSolution vector dx (block structure):");
        System.out.println("Phase 1 (gas) mole updates:");
        for (int i = 0; i < N; i++) {
          System.out.println("  d(n_" + componentNames.get(i) + "^gas) = " + dx.get(i, 0));
        }
        System.out.println("Phase 2 (liq) mole updates:");
        for (int i = 0; i < N; i++) {
          System.out.println("  d(n_" + componentNames.get(i) + "^liq) = " + dx.get(N + i, 0));
        }
        System.out.println("Lambda updates (per element):");
        for (int e = 0; e < E; e++) {
          int elemIdx = activeElements.get(e);
          System.out
              .println("  d(lambda_" + ELEMENT_NAMES[elemIdx] + ") = " + dx.get(2 * N + e, 0));
        }
      }

      // Use constant damping
      double effectiveDamping = dampingFactor;
      if (debugMode) {
        System.out.println("\nApplying damping = " + effectiveDamping);
      }

      // Update mole amounts
      System.out.println("\n--- Updating Mole Amounts ---");

      // Calculate proposed new moles
      double[][] newMoles = new double[P][N];
      boolean hasNegative = false;
      for (int p = 0; p < P; p++) {
        for (int i = 0; i < N; i++) {
          int idx = p * N + i;
          double delta = effectiveDamping * dx.get(idx, 0);
          newMoles[p][i] = phaseMoles[p][i] + delta;
          if (newMoles[p][i] < 0.0) {
            if (debugMode) {
              System.out.println("WARNING: Negative moles for " + componentNames.get(i)
                  + " in phase " + p + ": " + newMoles[p][i]);
            }
            hasNegative = true;
          }
        }
      }

      if (hasNegative) {
        throw new RuntimeException("Negative moles calculated. Try reducing dampingFactor.");
      }

      // Apply updates with constant damping
      double effectiveDampingUsed = effectiveDamping;
      if (debugMode) {
        System.out
            .println("\n--- Applying updates with damping = " + effectiveDampingUsed + " ---");

        for (int p = 0; p < P; p++) {
          String phaseName = (p == 0) ? "gas" : "liq";
          System.out.println("Phase " + p + " (" + phaseName + "):");
          for (int i = 0; i < N; i++) {
            int idx = p * N + i;
            double oldVal = phaseMoles[p][i];
            double dxDamped = effectiveDampingUsed * dx.get(idx, 0);
            System.out.println("  " + componentNames.get(i) + ": " + oldVal + " + " + dxDamped
                + " = " + newMoles[p][i]);
          }
        }
      }

      // Apply updates to phaseMoles
      for (int p = 0; p < P; p++) {
        for (int i = 0; i < N; i++) {
          phaseMoles[p][i] = newMoles[p][i];
        }
      }

      // Update lambda (per element)
      if (debugMode) {
        System.out.println("\n--- Updating Lambda (per element) ---");
      }
      int lambdaOffset = N * P;
      for (int e = 0; e < E; e++) {
        int elemIdx = activeElements.get(e);
        double oldLambda = lambda[e];
        double deltaLambda = effectiveDampingUsed * dx.get(lambdaOffset + e, 0);
        lambda[e] = oldLambda + deltaLambda;
        if (debugMode) {
          System.out.println("  lambda_" + ELEMENT_NAMES[elemIdx] + ": " + oldLambda + " + "
              + deltaLambda + " = " + lambda[e]);
        }
      }

      // Calculate and track element balance error
      if (debugMode) {
        System.out.println("\n--- Element Balance Error Check ---");
      }
      double maxElementError = 0.0;
      for (int e = 0; e < E; e++) {
        int elemIdx = activeElements.get(e);
        double totalElementMoles = 0.0;
        for (int p = 0; p < P; p++) {
          for (int i = 0; i < N; i++) {
            totalElementMoles += phaseMoles[p][i] * elementCoeffs[i][elemIdx];
          }
        }
        double error = Math.abs(totalElementMoles - elementTotals[e]);
        double relError = elementTotals[e] > 0 ? error / elementTotals[e] : error;
        if (debugMode) {
          System.out.println("  " + ELEMENT_NAMES[elemIdx] + ": current=" + totalElementMoles
              + ", target=" + elementTotals[e] + ", rel error=" + relError);
        }
        maxElementError = Math.max(maxElementError, relError);
      }

      if (maxElementError > maxElementBalanceError) {
        if (debugMode) {
          System.out.println("WARNING: Element balance error " + maxElementError
              + " exceeds threshold " + maxElementBalanceError);
        }
      }

      // Recalculate phase totals
      if (debugMode) {
        System.out.println("\n--- Recalculating Phase Totals ---");
      }
      for (int p = 0; p < P; p++) {
        phaseTotalMoles[p] = 0.0;
        for (int i = 0; i < N; i++) {
          phaseTotalMoles[p] += phaseMoles[p][i];
        }
        if (debugMode) {
          System.out.println("Phase " + p + " total: " + phaseTotalMoles[p] + " mol");
        }
      }

      // Update phase systems with new compositions
      updatePhaseSystems();

    } catch (Exception e) {
      if (debugMode) {
        System.out.println("ERROR: Newton-Raphson solve failed: " + e.getMessage());
        e.printStackTrace();
      }
      return false;
    }

    return false;
  }

  /**
   * Update thermodynamic systems with current mole amounts.
   */
  private void updatePhaseSystems() {
    for (int p = 0; p < numberOfPhases; p++) {
      SystemInterface phaseSystem = phaseSystems.get(p);
      double totalMoles = 0.0;

      // First pass: calculate total moles
      for (int i = 0; i < componentNames.size(); i++) {
        totalMoles += phaseMoles[p][i];
      }

      // Set total moles on the system
      try {
        phaseSystem.setTotalNumberOfMoles(totalMoles);
      } catch (Exception e) {
        logger.debug("Failed to set total moles: {}", e.getMessage());
      }

      // Update component moles on both system and phase level
      for (int i = 0; i < componentNames.size(); i++) {
        try {
          // Set on system-level component
          phaseSystem.getComponent(i).setNumberOfmoles(phaseMoles[p][i]);
          // Set on phase-level component (phase 0 is the single phase in this system)
          if (phaseSystem.getPhase(0) != null) {
            phaseSystem.getPhase(0).getComponent(i).setNumberOfmoles(phaseMoles[p][i]);
            phaseSystem.getPhase(0).getComponent(i).setNumberOfMolesInPhase(phaseMoles[p][i]);
          }
        } catch (Exception e) {
          // Component may not exist
        }
      }

      // Initialize to recalculate all derived properties including mole fractions
      try {
        // Force correct phase type with proper sequence
        phaseSystem.setNumberOfPhases(1);
        phaseSystem.setMaxNumberOfPhases(1);
        phaseSystem.setForcePhaseTypes(true);
        phaseSystem.init(0);
        if (p == 0) {
          phaseSystem.setPhaseType(0, PhaseType.GAS);
        } else {
          phaseSystem.setPhaseType(0, PhaseType.LIQUID);
        }
        phaseSystem.init(3);
      } catch (Exception e) {
        logger.debug("Phase system init failed: {}", e.getMessage());
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("RUNNING MULTIPHASE GIBBS REACTOR: " + getName());
    System.out.println(StringUtils.repeat("=", 60));

    // Reset regularization flags at start of each run
    elementConstraintRemoved = false;
    removedElementIndex = -1;
    previousGibbsEnergy = Double.NaN;

    // Initialize
    System.out.println("\nStep 1: Initializing phase moles...");
    initializePhaseMoles();

    System.out.println("\nStep 1b: Initializing element data...");
    initializeElementData();

    System.out.println("\nStep 2: Initializing phase systems...");
    initializePhaseSystems();

    System.out.println("\nPhase models:");
    for (int p = 0; p < numberOfPhases; p++) {
      System.out.println("  Phase " + p + ": " + getPhaseModel(p));
    }

    // Check component database
    System.out.println("\nChecking Gibbs database for components:");
    for (String compName : componentNames) {
      GibbsComponent gc = getComponentMap().get(compName.toLowerCase());
      if (gc != null) {
        System.out.println("  " + compName + ": FOUND (elements: "
            + java.util.Arrays.toString(gc.getElements()) + ")");
      } else {
        System.out.println("  " + compName + ": NOT FOUND - will not participate in reactions");
      }
    }

    // Newton-Raphson iterations
    System.out
        .println("\nStep 3: Starting Newton-Raphson iterations (max=" + maxIterations + ")...");
    converged = false;
    for (int iter = 0; iter < maxIterations; iter++) {
      System.out.println("\n" + StringUtils.repeat("-", 40));
      System.out.println("ITERATION " + (iter + 1));
      System.out.println(StringUtils.repeat("-", 40));

      converged = performMultiphaseIteration();
      if (converged) {
        if (debugMode) {
          System.out.println("\n*** CONVERGED in " + (iter + 1) + " iterations ***");
        }
        break;
      }
    }

    if (!converged) {
      System.out.println("\nWARNING: Did not converge after " + maxIterations + " iterations");
    }

    // Skip outlet stream creation - use getPhaseOut(phaseNumber) to get individual phases

    System.out.println("\n" + StringUtils.repeat("=", 60));
    System.out.println("MULTIPHASE GIBBS REACTOR COMPLETE");
    System.out.println(StringUtils.repeat("=", 60));
  }

  /**
   * Create the outlet stream from the equilibrium result.
   */
  private void createOutletStream() {
    SystemInterface inlet = getInletStream().getThermoSystem();
    SystemInterface outlet = inlet.clone();

    // Set component moles to sum across all phases
    for (int i = 0; i < componentNames.size(); i++) {
      double totalMoles = 0.0;
      for (int p = 0; p < numberOfPhases; p++) {
        totalMoles += phaseMoles[p][i];
      }
      outlet.getComponent(i).setNumberOfmoles(totalMoles);
    }

    outlet.init(0);
    outlet.init(1);

    // Set outlet stream
    if (getOutletStream() == null) {
      setOutletStream(new Stream(getName() + "_outlet", outlet));
    } else {
      getOutletStream().setThermoSystem(outlet);
    }
  }

  /**
   * Check if the reactor has converged.
   *
   * @return True if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get the thermodynamic system for a specific phase.
   *
   * <p>
   * Returns the phase system at equilibrium after the reactor has been run.
   * </p>
   *
   * @param phaseNumber Phase number (0-based index)
   * @return Thermodynamic system for the specified phase, or null if phase doesn't exist
   */
  public SystemInterface getPhaseOut(int phaseNumber) {
    if (phaseSystems == null || phaseNumber < 0 || phaseNumber >= phaseSystems.size()) {
      return null;
    }
    return phaseSystems.get(phaseNumber);
  }

  /**
   * Get the phase fractions at equilibrium.
   *
   * @return Array of phase fractions (mole basis)
   */
  public double[] getPhaseFractions() {
    double[] fractions = new double[numberOfPhases];
    double totalMoles = 0.0;

    for (int p = 0; p < numberOfPhases; p++) {
      totalMoles += phaseTotalMoles[p];
    }

    if (totalMoles > MIN_MOLES) {
      for (int p = 0; p < numberOfPhases; p++) {
        fractions[p] = phaseTotalMoles[p] / totalMoles;
      }
    }

    return fractions;
  }

  /**
   * Print a summary of the multiphase equilibrium result.
   */
  public void printPhaseSummary() {
    System.out.println("\n=== Multiphase Gibbs Reactor Summary ===");
    System.out.println("Converged: " + converged);
    System.out.println("Number of phases: " + numberOfPhases);

    double[] fractions = getPhaseFractions();
    for (int p = 0; p < numberOfPhases; p++) {
      System.out.println("\nPhase " + p + " (" + getPhaseModel(p) + "):");
      System.out.println("  Mole fraction: " + String.format("%.4f", fractions[p]));
      System.out.println("  Total moles: " + String.format("%.6f", phaseTotalMoles[p]));
      System.out.println("  Components:");

      for (int i = 0; i < componentNames.size(); i++) {
        double moles = phaseMoles[p][i];
        double x = moles / Math.max(phaseTotalMoles[p], MIN_MOLES);
        System.out.println("    " + componentNames.get(i) + ": " + String.format("%.6f mol", moles)
            + " (x=" + String.format("%.4f", x) + ")");
      }
    }
  }
}
