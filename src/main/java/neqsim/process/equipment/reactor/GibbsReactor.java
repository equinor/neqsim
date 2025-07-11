package neqsim.process.equipment.reactor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * GibbsReactor class.
 * </p>
 *
 * @author Sviatoslav Eroshkin
 * @version $Id: $Id
 */
public class GibbsReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GibbsReactor.class);

  private String method = "DirectGibbsMinimization";
  private boolean useAllDatabaseSpecies = false;
  private List<GibbsComponent> gibbsDatabase = new ArrayList<>();
  private Map<String, GibbsComponent> componentMap = new HashMap<>();

  // Results from the last calculation
  private double[] lambda = new double[6]; // O, N, C, H, S, Ar
  private Map<String, Double> lagrangeContributions = new HashMap<>();
  private String[] elementNames = {"O", "N", "C", "H", "S", "Ar"};
  private List<String> processedComponents = new ArrayList<>();
  private Map<String, Double> objectiveFunctionValues = new HashMap<>();

  // Mole balance calculations
  private Map<String, Double> initialMoles = new HashMap<>();
  private Map<String, Double> finalMoles = new HashMap<>();
  private double[] elementMoleBalanceIn = new double[6]; // Total moles of each element in
  private double[] elementMoleBalanceOut = new double[6]; // Total moles of each element out
  private double[] elementMoleBalanceDiff = new double[6]; // Difference (out - in) for each element

  // Mole lists for calculations
  private List<Double> inlet_mole = new ArrayList<>();
  private List<Double> outlet_mole = new ArrayList<>();

  // Objective minimization vector
  private double[] objectiveMinimizationVector;
  private List<String> objectiveMinimizationVectorLabels = new ArrayList<>();

  // Jacobian matrix for Newton-Raphson method
  private double[][] jacobianMatrix;
  private double[][] jacobianInverse;
  private List<String> jacobianRowLabels = new ArrayList<>();
  private List<String> jacobianColLabels = new ArrayList<>();

  // Newton-Raphson iteration control
  private int maxIterations = 5000;
  private double convergenceTolerance = 1e-6;
  private int actualIterations = 0;
  private boolean converged = false;
  private double finalConvergenceError = 0.0;

  /**
   * Constructor for GibbsReactor.
   *
   * @param name Name of GibbsReactor
   */
  public GibbsReactor(String name) {
    super(name);
    loadGibbsDatabase();
  }

  /**
   * Constructor for GibbsReactor.
   *
   * @param name Name of GibbsReactor
   * @param stream Stream to set as inlet Stream. A clone of stream is set as outlet stream.
   */
  public GibbsReactor(String name, StreamInterface stream) {
    super(name, stream);
    loadGibbsDatabase();
  }

  /**
   * Inner class to represent a component in the Gibbs reaction database.
   */
  public static class GibbsComponent {
    private String molecule;
    private double[] elements = new double[6]; // O, N, C, H, S, Ar
    private double[] heatCapacityCoeffs = new double[4]; // A, B, C, D
    private double deltaHf298; // Enthalpy of formation at 298K
    private double deltaGf298; // Gibbs energy of formation at 298K

    /**
     * Constructor for GibbsComponent.
     */
    public GibbsComponent(String molecule, double[] elements, double[] heatCapacityCoeffs,
        double deltaHf298, double deltaGf298) {
      this.molecule = molecule;
      this.elements = elements.clone();
      this.heatCapacityCoeffs = heatCapacityCoeffs.clone();
      this.deltaHf298 = deltaHf298;
      this.deltaGf298 = deltaGf298;
    }

    public String getMolecule() {
      return molecule;
    }

    public double[] getElements() {
      return elements.clone();
    }

    public double[] getHeatCapacityCoeffs() {
      return heatCapacityCoeffs.clone();
    }

    public double getDeltaHf298() {
      return deltaHf298;
    }

    public double getDeltaGf298() {
      return deltaGf298;
    }

    public double calculateGibbsEnergy(double temperature) {
      // Simplified calculation using constant heat capacity
      double T = temperature;
      double T0 = 298.15; // Reference temperature
      double R = 8.314462618e-3; // kJ/(mol·K)

      // For now, return deltaGf298 - simplified approach
      return deltaGf298;
    }
  }

  /**
   * Load the Gibbs reaction database from resources.
   */
  private void loadGibbsDatabase() {
    try {
      // Load from resources
      InputStream inputStream =
          getClass().getResourceAsStream("/data/GibbsReactDatabase/GibbsReactDatabase.csv");
      if (inputStream == null) {
        // Try alternative path
        inputStream = getClass()
            .getResourceAsStream("/neqsim/data/GibbsReactDatabase/GibbsReactDatabase.csv");
      }
      if (inputStream == null) {
        // Try another alternative path
        inputStream = getClass().getResourceAsStream("/neqsim/data/GibbsReactDatabase.csv");
      }
      if (inputStream == null) {
        logger.warn("Could not find GibbsReactDatabase.csv in resources");
        System.out
            .println("DEBUG: Could not find GibbsReactDatabase.csv in any of the expected paths");
        return;
      }

      Scanner scanner = new Scanner(inputStream);

      // Skip header line
      if (scanner.hasNextLine()) {
        scanner.nextLine();
      }

      while (scanner.hasNextLine()) {
        String line = scanner.nextLine().trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }

        String[] parts = line.split(";");
        if (parts.length >= 13) {
          try {
            final String molecule = parts[0].trim();

            // Parse element composition [O, N, C, H, S, Ar]
            double[] elements = new double[6];
            for (int i = 0; i < 6; i++) {
              String value = parts[i + 1].trim().replace(",", ".");
              elements[i] = Double.parseDouble(value);
            }

            // Parse heat capacity coefficients
            double[] heatCapCoeffs = new double[4];
            for (int i = 0; i < 4; i++) {
              String value = parts[i + 7].trim().replace(",", ".");
              heatCapCoeffs[i] = Double.parseDouble(value);
            }

            String deltaHf298Str = parts[11].trim().replace(",", ".");
            String deltaGf298Str = parts[12].trim().replace(",", ".");
            double deltaHf298 = Double.parseDouble(deltaHf298Str);
            double deltaGf298 = Double.parseDouble(deltaGf298Str);

            GibbsComponent component =
                new GibbsComponent(molecule, elements, heatCapCoeffs, deltaHf298, deltaGf298);
            gibbsDatabase.add(component);
            componentMap.put(molecule.toLowerCase(), component);

            logger.debug("Loaded component: " + molecule);
          } catch (NumberFormatException e) {
            logger.warn("Error parsing line: " + line + " - " + e.getMessage());
          }
        }
      }

      scanner.close();
      logger.info("Loaded " + gibbsDatabase.size() + " components from Gibbs database");

    } catch (Exception e) {
      logger.error("Error loading Gibbs database: " + e.getMessage());
    }
  }

  /**
   * Set whether to use all database species or only species in the system.
   *
   * @param useAllDatabaseSpecies true to use all database species, false to use only system species
   */
  public void setUseAllDatabaseSpecies(boolean useAllDatabaseSpecies) {
    this.useAllDatabaseSpecies = useAllDatabaseSpecies;
  }

  /**
   * Get whether using all database species or only species in the system.
   *
   * @return true if using all database species, false if using only system species
   */
  public boolean getUseAllDatabaseSpecies() {
    return useAllDatabaseSpecies;
  }

  /**
   * Get the method used for Gibbs minimization.
   *
   * @return The method name
   */
  public String getMethod() {
    return method;
  }

  /**
   * Set the method used for Gibbs minimization.
   *
   * @param method The method name
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /**
   * Get the Lagrange contributions (legacy method).
   *
   * @return Map of Lagrange contributions
   */
  public Map<String, Double> getLagrangeContributions() {
    return new HashMap<>(lagrangeContributions);
  }

  @Override
  public void run(UUID id) {
    SystemInterface system = getInletStream().getThermoSystem().clone();
    system.init(0);

    // Store initial moles for each component
    initialMoles.clear();
    inlet_mole.clear();
    outlet_mole.clear();

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      initialMoles.put(compName, moles);
      inlet_mole.add(moles);
    }

    // Calculate initial element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceIn, true);

    // Perform Gibbs minimization
    if (useAllDatabaseSpecies) {
      // Add all database species to system
      for (GibbsComponent component : gibbsDatabase) {
        try {
          system.addComponent(component.getMolecule(), 1E-6);
        } catch (Exception e) {
          logger
              .debug("Could not add component " + component.getMolecule() + ": " + e.getMessage());
        }
      }
    }

    // Minimize Gibbs energy
    performGibbsMinimization(system);

    // Enforce minimum concentrations
    enforceMinimumConcentrations(system);

    // Store final moles for each component
    finalMoles.clear();
    processedComponents.clear();
    outlet_mole.clear(); // Clear and repopulate with actual final values

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      finalMoles.put(compName, moles);
      processedComponents.add(compName);

      // Update outlet_mole with actual final values, enforcing minimum
      double outletMoles = Math.max(moles, 1E-6);
      outlet_mole.add(outletMoles);
    }

    // Calculate final element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceOut, false);

    // Calculate difference (outlet - inlet)
    for (int i = 0; i < elementNames.length; i++) {
      elementMoleBalanceDiff[i] = elementMoleBalanceOut[i] - elementMoleBalanceIn[i];
    }

    // Calculate objective function values
    calculateObjectiveFunctionValues(system);

    solveGibbsEquilibrium();

    // Set outlet stream
    getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);
  }

  /**
   * Perform Gibbs free energy minimization.
   *
   * @param system The thermodynamic system
   */
  private void performGibbsMinimization(SystemInterface system) {
    // Set iteration to 1
    final int iteration = 1;

    // Create initial guess for moles
    final Map<String, Double> initialGuess = new HashMap<>();
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      initialGuess.put(compName, system.getComponent(i).getNumberOfMolesInPhase());
    }

    // Check if component is in system
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      final boolean inSystem = false;

      if (initialGuess.containsKey(compName)) {
        double currentMoles = system.getComponent(i).getNumberOfMolesInPhase();
        if (currentMoles < 1E-6) {
          system.addComponent(i, 1E-6 - currentMoles, 0);
        }
      }
    }

    // Run thermodynamic equilibrium calculation
    system.init(0);
    system.init(1);

    logger.info("Gibbs minimization completed for iteration " + iteration);
  }

  /**
   * Calculate element mole balance for a system.
   *
   * @param system The thermodynamic system
   * @param elementBalance Array to store the element balance
   * @param isInput true if this is input balance, false if output balance
   */
  private void calculateElementMoleBalance(SystemInterface system, double[] elementBalance,
      boolean isInput) {
    // Reset balance
    for (int i = 0; i < elementBalance.length; i++) {
      elementBalance[i] = 0.0;
    }

    // Process each component using appropriate mole list
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();

      // Use inlet_mole for input, outlet_mole for output
      double moles;
      if (isInput) {
        moles = (i < inlet_mole.size()) ? inlet_mole.get(i) : 0.0;
      } else {
        moles = (i < outlet_mole.size()) ? outlet_mole.get(i) : 0.0;
      }

      // Get element composition from database
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp != null) {
        double[] elements = comp.getElements();
        for (int j = 0; j < elementNames.length; j++) {
          elementBalance[j] += elements[j] * moles;
        }
      }
    }
  }

  /**
   * Calculate objective function values for each component.
   *
   * @param system The thermodynamic system
   */
  private void calculateObjectiveFunctionValues(SystemInterface system) {
    objectiveFunctionValues.clear();

    double T = system.getTemperature();
    double RT = 8.314462618e-3 * T; // kJ/mol

    // Calculate total moles from outlet_mole list
    double totalMoles = 0.0;
    for (Double moles : outlet_mole) {
      totalMoles += moles;
    }

    // Calculate for each component
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();

      // Use outlet_mole for formulas
      double moles = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;

      // Get Gibbs component
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp != null) {
        // Calculate Gibbs energy of formation
        double Gf0 = comp.calculateGibbsEnergy(T);

        // Calculate fugacity coefficient (assume 1 for now)
        double phi = 1.0;

        // Calculate mole fraction
        double yi = moles / totalMoles;

        // Calculate Lagrange multiplier contribution
        double lagrangeSum = 0.0;
        double[] elements = comp.getElements();
        for (int j = 0; j < lambda.length; j++) {
          lagrangeSum += lambda[j] * elements[j];
        }

        // Calculate objective function: F = Gf0 + RT*ln(phi) + RT*ln(yi) - lagrangeSum
        double F = Gf0 + RT * Math.log(phi) + RT * Math.log(yi) - lagrangeSum;
        objectiveFunctionValues.put(compName, F);
      }
    }
  }

  /**
   * Set a Lagrange multiplier value.
   *
   * @param index The element index (0=O, 1=N, 2=C, 3=H, 4=S, 5=Ar)
   * @param value The Lagrange multiplier value
   */
  public void setLagrangeMultiplier(int index, double value) {
    if (index >= 0 && index < lambda.length) {
      lambda[index] = value;
    }
  }

  /**
   * Get the Lagrange multiplier values.
   *
   * @return Array of Lagrange multiplier values [O, N, C, H, S, Ar]
   */
  public double[] getLagrangianMultipliers() {
    return lambda.clone();
  }

  /**
   * Get the element names.
   *
   * @return Array of element names
   */
  public String[] getElementNames() {
    return elementNames.clone();
  }

  /**
   * Get the Lagrange multiplier contributions for each component.
   *
   * @return Map with component names and their Lagrange multiplier contributions
   */
  public Map<String, Map<String, Double>> getLagrangeMultiplierContributions() {
    Map<String, Map<String, Double>> contributions = new HashMap<>();

    // Use the components that were actually processed in the last run
    List<String> componentsToProcess =
        processedComponents.isEmpty() ? new ArrayList<>(finalMoles.keySet()) : processedComponents;

    for (String compName : componentsToProcess) {
      Map<String, Double> compContributions = new HashMap<>();

      // Get element composition from database
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp != null) {
        double[] elements = comp.getElements();
        double totalContribution = 0.0;

        for (int i = 0; i < elementNames.length; i++) {
          double contribution = lambda[i] * elements[i];
          compContributions.put(elementNames[i], contribution);
          totalContribution += contribution;
        }

        compContributions.put("TOTAL", totalContribution);
        contributions.put(compName, compContributions);
      }
    }

    return contributions;
  }

  /**
   * Get the objective function values for each component.
   *
   * @return Map with component names and their objective function values
   */
  public Map<String, Double> getObjectiveFunctionValues() {
    return new HashMap<>(objectiveFunctionValues);
  }

  /**
   * Get the element mole balance for input streams.
   *
   * @return Array of total moles for each element in input [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceIn() {
    return elementMoleBalanceIn.clone();
  }

  /**
   * Get the element mole balance for output streams.
   *
   * @return Array of total moles for each element in output [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceOut() {
    return elementMoleBalanceOut.clone();
  }

  /**
   * Get the element mole balance difference (in - out).
   *
   * @return Array of mole differences for each element [O, N, C, H, S, Ar]
   */
  public double[] getElementMoleBalanceDiff() {
    return elementMoleBalanceDiff.clone();
  }

  /**
   * Get detailed mole balance information for each component.
   *
   * @return Map with component names and their element contributions to mole balance
   */
  public Map<String, Map<String, Double>> getDetailedMoleBalance() {
    Map<String, Map<String, Double>> detailedBalance = new HashMap<>();

    for (int i = 0; i < processedComponents.size(); i++) {
      String compName = processedComponents.get(i);
      GibbsComponent comp = componentMap.get(compName.toLowerCase());

      // Use inlet_mole and outlet_mole lists
      Double molesIn = (i < inlet_mole.size()) ? inlet_mole.get(i) : 0.0;
      Double molesOut = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;

      if (comp != null) {
        Map<String, Double> componentBalance = new HashMap<>();
        final double[] elements = comp.getElements(); // [O, N, C, H, S, Ar]

        // Store initial and final moles
        componentBalance.put("MOLES_IN", molesIn);
        componentBalance.put("MOLES_OUT", molesOut);
        componentBalance.put("MOLES_DIFF", molesOut - molesIn); // outlet - inlet for mass balance

        // Calculate element contributions using outlet - inlet difference
        for (int j = 0; j < elementNames.length; j++) {
          double elementIn = elements[j] * molesIn;
          double elementOut = elements[j] * molesOut;
          double elementDiff = elementOut - elementIn; // outlet - inlet

          componentBalance.put(elementNames[j] + "_IN", elementIn);
          componentBalance.put(elementNames[j] + "_OUT", elementOut);
          componentBalance.put(elementNames[j] + "_DIFF", elementDiff);
        }

        detailedBalance.put(compName, componentBalance);
      }
    }

    return detailedBalance;
  }

  /**
   * Calculate the objective minimization vector. This vector contains the F values for each
   * component and the mass balance constraints. The system is in equilibrium when this vector is
   * zero. Only includes elements that are actually present in the system.
   */
  private void calculateObjectiveMinimizationVector() {
    // Find which elements are actually present in the system
    List<Integer> activeElements = findActiveElements();

    int numComponents = processedComponents.size();
    int numActiveElements = activeElements.size();

    // Create vector: [F_values, mass_balance_constraints]
    objectiveMinimizationVector = new double[numComponents + numActiveElements];
    objectiveMinimizationVectorLabels.clear();

    // First part: F values for each component
    for (int i = 0; i < numComponents; i++) {
      String compName = processedComponents.get(i);
      Double fValue = objectiveFunctionValues.get(compName);
      objectiveMinimizationVector[i] = (fValue != null) ? fValue : 0.0;
      objectiveMinimizationVectorLabels.add("F_" + compName);
    }

    // Second part: Mass balance constraints (only for active elements)
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      objectiveMinimizationVector[numComponents + i] = elementMoleBalanceDiff[elementIndex];
      objectiveMinimizationVectorLabels.add("Balance_" + elementNames[elementIndex]);
    }
  }

  /**
   * Get the objective minimization vector.
   *
   * @return The objective minimization vector
   */
  public double[] getObjectiveMinimizationVector() {
    calculateObjectiveMinimizationVector();
    return objectiveMinimizationVector.clone();
  }

  /**
   * Get the labels for the objective minimization vector.
   *
   * @return List of labels for each element in the objective minimization vector
   */
  public List<String> getObjectiveMinimizationVectorLabels() {
    return new ArrayList<>(objectiveMinimizationVectorLabels);
  }

  /**
   * Calculate the Jacobian matrix for the Newton-Raphson method. The Jacobian represents the
   * derivatives of the objective function with respect to the variables. Only includes elements
   * that are actually present in the system to avoid singular matrices.
   */
  private void calculateJacobian() {
    if (processedComponents.isEmpty()) {
      return;
    }

    // Find which elements are actually present in the system
    List<Integer> activeElements = findActiveElements();

    int numComponents = processedComponents.size();
    int numActiveElements = activeElements.size();
    int totalVars = numComponents + numActiveElements; // components + active Lagrange multipliers

    jacobianMatrix = new double[totalVars][totalVars];
    jacobianRowLabels.clear();
    jacobianColLabels.clear();

    // Set up labels
    for (int i = 0; i < numComponents; i++) {
      jacobianRowLabels.add("F_" + processedComponents.get(i));
      jacobianColLabels.add("n_" + processedComponents.get(i));
    }
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      jacobianRowLabels.add("Balance_" + elementNames[elementIndex]);
      jacobianColLabels.add("lambda_" + elementNames[elementIndex]);
    }

    SystemInterface system = getOutletStream().getThermoSystem();
    double T = system.getTemperature();
    double RT = 8.314462618e-3 * T; // kJ/mol

    // Calculate total moles for mole fraction derivatives using outlet_mole
    double totalMoles = 0.0;
    for (Double moles : outlet_mole) {
      totalMoles += moles;
    }

    // Fill Jacobian matrix
    for (int i = 0; i < numComponents; i++) {
      String compI = processedComponents.get(i);

      // Use outlet_mole for calculations, but with a minimum value to avoid numerical issues
      double ni = (i < outlet_mole.size()) ? outlet_mole.get(i) : 1E-6;
      double niForJacobian = Math.max(ni, 1e-6); // Use minimum of 1e-6 for Jacobian calculation

      for (int j = 0; j < numComponents; j++) {
        if (i == j) {
          // Diagonal elements: ∂f_i/∂n_i = RT * (1/n_i - 1/n_total)
          jacobianMatrix[i][j] = RT * (1.0 / niForJacobian - 1.0 / totalMoles);
        } else {
          // Off-diagonal elements: ∂f_i/∂n_j = -RT/n_total
          jacobianMatrix[i][j] = -RT / totalMoles;
        }
      }

      // Derivatives with respect to Lagrange multipliers (only active elements)
      GibbsComponent gibbsComp = componentMap.get(compI.toLowerCase());
      if (gibbsComp != null) {
        double[] elements = gibbsComp.getElements();
        for (int k = 0; k < numActiveElements; k++) {
          int elementIndex = activeElements.get(k);
          jacobianMatrix[i][numComponents + k] = -elements[elementIndex];
        }
      }
    }

    // Mass balance constraint derivatives (only for active elements)
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElements.get(i);
      for (int j = 0; j < numComponents; j++) {
        String compName = processedComponents.get(j);
        GibbsComponent gibbsComp = componentMap.get(compName.toLowerCase());
        if (gibbsComp != null) {
          double[] elements = gibbsComp.getElements();
          jacobianMatrix[numComponents + i][j] = elements[elementIndex];
        }
      }

      // Derivatives with respect to Lagrange multipliers are zero
      for (int k = 0; k < numActiveElements; k++) {
        jacobianMatrix[numComponents + i][numComponents + k] = 0.0;
      }
    }

    // Calculate inverse
    jacobianInverse = calculateJacobianInverse();
  }

  /**
   * Get the Jacobian matrix.
   *
   * @return The Jacobian matrix
   */
  public double[][] getJacobianMatrix() {
    calculateJacobian();

    if (jacobianMatrix == null) {
      return null;
    }

    // Return a deep copy to prevent external modification
    double[][] copy = new double[jacobianMatrix.length][];
    for (int i = 0; i < jacobianMatrix.length; i++) {
      copy[i] = jacobianMatrix[i].clone();
    }
    return copy;
  }

  /**
   * Get the Jacobian row labels.
   *
   * @return List of row labels for the Jacobian matrix
   */
  public List<String> getJacobianRowLabels() {
    return new ArrayList<>(jacobianRowLabels);
  }

  /**
   * Get the Jacobian column labels.
   *
   * @return List of column labels for the Jacobian matrix
   */
  public List<String> getJacobianColLabels() {
    return new ArrayList<>(jacobianColLabels);
  }

  /**
   * Get the Jacobian inverse matrix.
   *
   * @return The Jacobian inverse matrix, or null if it couldn't be calculated
   */
  public double[][] getJacobianInverse() {
    if (jacobianInverse == null) {
      return null;
    }

    // Return a deep copy to prevent external modification
    double[][] copy = new double[jacobianInverse.length][];
    for (int i = 0; i < jacobianInverse.length; i++) {
      copy[i] = jacobianInverse[i].clone();
    }
    return copy;
  }

  /**
   * Calculate the inverse of the Jacobian matrix using JAMA Matrix library.
   *
   * @return Inverse matrix, or null if matrix is singular
   */
  private double[][] calculateJacobianInverse() {
    if (jacobianMatrix == null) {
      return null;
    }

    try {
      // Create JAMA Matrix object
      Matrix jamaMatrix = new Matrix(jacobianMatrix);

      // Calculate inverse using JAMA
      Matrix inverseMatrix = jamaMatrix.inverse();

      // Convert back to double[][]
      return inverseMatrix.getArray();

    } catch (RuntimeException e) {
      logger.warn("Jacobian matrix is singular or nearly singular: " + e.getMessage());
      return null;
    }
  }

  /**
   * Enforce minimum concentration threshold to prevent numerical issues. If any component has moles
   * less than 1E-6, it will be set to 1E-6.
   *
   * @param system The thermodynamic system to check and modify
   */
  private void enforceMinimumConcentrations(SystemInterface system) {
    double minConcentration = 1e-6;
    boolean modified = false;

    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      double currentMoles = system.getComponent(i).getNumberOfMolesInPhase();

      if (currentMoles < minConcentration) {
        logger.info("Component " + system.getComponent(i).getComponentName()
            + " has very low concentration (" + currentMoles + "), setting to minimum: "
            + minConcentration);
        system.addComponent(i, minConcentration - currentMoles, 0);
        modified = true;
      }
    }

    if (modified) {
      // Reinitialize the system after modifying concentrations
      system.init(0);
      logger.info("System reinitialized after enforcing minimum concentrations");
    }
  }

  /**
   * Get the inlet mole list.
   *
   * @return List of inlet moles for each component
   */
  public List<Double> getInletMole() {
    return new ArrayList<>(inlet_mole);
  }

  /**
   * Get the outlet mole list.
   *
   * @return List of outlet moles for each component (with 0 values replaced by 1E-6)
   */
  public List<Double> getOutletMole() {
    return new ArrayList<>(outlet_mole);
  }

  /**
   * Get the inlet mole list for debugging.
   *
   * @return List of inlet moles
   */
  public List<Double> getInletMoles() {
    return new ArrayList<>(inlet_mole);
  }

  /**
   * Get the outlet mole list for debugging.
   *
   * @return List of outlet moles
   */
  public List<Double> getOutletMoles() {
    return new ArrayList<>(outlet_mole);
  }

  /**
   * Print loaded database components for debugging.
   */
  public void printDatabaseComponents() {
    System.out.println("\n=== Loaded Database Components ===");
    System.out.println("Total components in database: " + gibbsDatabase.size());

    for (GibbsComponent comp : gibbsDatabase) {
      String molecule = comp.getMolecule();
      double[] elements = comp.getElements();
      System.out.printf("  %s: O=%.1f, N=%.1f, C=%.1f, H=%.1f, S=%.1f, Ar=%.1f%n", molecule,
          elements[0], elements[1], elements[2], elements[3], elements[4], elements[5]);
    }

    System.out.println("\nComponent map keys:");
    for (String key : componentMap.keySet()) {
      System.out.println("  '" + key + "'");
    }
  }

  /**
   * Find which elements are actually present in the system (have non-zero coefficients).
   *
   * @return List of indices of active elements
   */
  private List<Integer> findActiveElements() {
    List<Integer> activeElements = new ArrayList<>();

    // Check each element to see if any component has a non-zero coefficient
    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      boolean elementPresent = false;

      for (String compName : processedComponents) {
        GibbsComponent comp = componentMap.get(compName.toLowerCase());
        if (comp != null) {
          double[] elements = comp.getElements();
          if (Math.abs(elements[elementIndex]) > 1E-6) {
            elementPresent = true;
            break;
          }
        }
      }

      if (elementPresent) {
        activeElements.add(elementIndex);
      }
    }

    return activeElements;
  }

  /**
   * Get the indices of active elements (elements that have non-zero coefficients in any component).
   *
   * @return List of active element indices
   */
  private List<Integer> getActiveElementIndices() {
    List<Integer> activeIndices = new ArrayList<>();

    for (int elementIndex = 0; elementIndex < elementNames.length; elementIndex++) {
      boolean hasNonZero = false;

      for (String compName : processedComponents) {
        GibbsComponent comp = componentMap.get(compName.toLowerCase());
        if (comp != null) {
          double[] elements = comp.getElements();
          if (Math.abs(elements[elementIndex]) > 1e-10) {
            hasNonZero = true;
            break;
          }
        }
      }

      if (hasNonZero) {
        activeIndices.add(elementIndex);
      }
    }

    return activeIndices;
  }

  /**
   * Verify that the Jacobian inverse is correct by multiplying J * J^-1. Should return the identity
   * matrix if the inverse is correct.
   *
   * @return True if the inverse is correct (within tolerance)
   */
  public boolean verifyJacobianInverse() {
    if (jacobianMatrix == null || jacobianInverse == null) {
      return false;
    }

    try {
      // Create JAMA Matrix objects
      Matrix jacobianMatrix_JAMA = new Matrix(jacobianMatrix);
      Matrix jacobianInverseMatrix = new Matrix(jacobianInverse);

      // Calculate J * J^(-1) using JAMA
      Matrix resultMatrix = jacobianMatrix_JAMA.times(jacobianInverseMatrix);

      // Check if result is identity matrix
      double tolerance = 1e-10;
      int n = jacobianMatrix.length;

      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          double value = resultMatrix.get(i, j);
          double expected = (i == j) ? 1.0 : 0.0;

          if (Math.abs(value - expected) > tolerance) {
            logger.warn("Jacobian inverse verification failed at [" + i + "," + j + "]: "
                + "expected " + expected + ", got " + value);
            return false;
          }
        }
      }

      return true;

    } catch (RuntimeException e) {
      logger.warn("Error during Jacobian inverse verification: " + e.getMessage());
      return false;
    }
  }

  /**
   * Perform one Newton-Raphson iteration step to calculate the delta vector (dX). Uses the formula:
   * dX = -J^(-1) * F where J is the Jacobian matrix and F is the objective function vector.
   *
   * @return The delta vector (dX) for updating variables, or null if calculation fails
   */
  public double[] performNewtonRaphsonIteration() {
    // Calculate the Jacobian matrix and its inverse
    calculateJacobian();

    if (jacobianMatrix == null || jacobianInverse == null) {
      logger.warn("Cannot perform Newton-Raphson iteration: Jacobian or its inverse is null");
      return null;
    }

    // Get the objective function vector F
    double[] objectiveVector = getObjectiveMinimizationVector();

    if (objectiveVector == null || objectiveVector.length != jacobianInverse.length) {
      logger.warn("Objective vector size mismatch with Jacobian matrix");
      return null;
    }

    try {
      // Create JAMA Matrix objects
      Matrix jacobianInverseMatrix = new Matrix(jacobianInverse);
      Matrix objectiveVectorMatrix = new Matrix(objectiveVector, objectiveVector.length);

      // Calculate dX = -J^(-1) * F using JAMA matrix multiplication
      Matrix deltaXMatrix = jacobianInverseMatrix.times(objectiveVectorMatrix).times(-1.0);

      // Convert back to double array
      return deltaXMatrix.getColumnPackedCopy();

    } catch (RuntimeException e) {
      logger.warn("Error during Newton-Raphson iteration calculation: " + e.getMessage());
      return null;
    }
  }

  /**
   * Perform a Newton-Raphson iteration update. Updates outlet compositions with damping factor and
   * Lagrange multipliers directly.
   *
   * @param deltaX The delta vector from Newton-Raphson iteration
   * @param alphaComposition Damping factor for composition updates (e.g., 0.0001)
   * @return True if update was successful, false otherwise
   */
  public boolean performIterationUpdate(double[] deltaX, double alphaComposition) {
    if (deltaX == null || outlet_mole.isEmpty()) {
      logger.warn("Cannot perform iteration update: deltaX or outlet_mole is null/empty");
      return false;
    }

    int numComponents = processedComponents.size();
    List<Integer> activeElementIndices = getActiveElementIndices();
    int numActiveElements = activeElementIndices.size();

    if (deltaX.length != numComponents + numActiveElements) {
      logger.warn("Delta vector size mismatch: expected " + (numComponents + numActiveElements)
          + ", got " + deltaX.length);
      return false;
    }

    // Update outlet compositions with damping factor
    System.out.println("\n=== Updating Outlet Compositions ===");
    for (int i = 0; i < numComponents; i++) {
      double oldValue = outlet_mole.get(i);
      double deltaComposition = deltaX[i];
      double newValue = oldValue + deltaComposition * alphaComposition;

      // Ensure non-negative values and minimum concentration
      newValue = Math.max(newValue, 1e-15);

      outlet_mole.set(i, newValue);

      System.out.printf("  %s: %12.6e → %12.6e (Δ = %12.6e, α*Δ = %12.6e)%n",
          processedComponents.get(i), oldValue, newValue, deltaComposition,
          deltaComposition * alphaComposition);
    }

    // Update Lagrange multipliers directly (no damping)
    System.out.println("\n=== Updating Lagrange Multipliers ===");
    for (int i = 0; i < numActiveElements; i++) {
      int elementIndex = activeElementIndices.get(i);
      double oldValue = lambda[elementIndex];
      double deltaLambda = deltaX[numComponents + i];
      double newValue = oldValue + deltaLambda;

      lambda[elementIndex] = newValue;

      System.out.printf("  λ[%s]: %12.6e → %12.6e (Δ = %12.6e)%n", elementNames[elementIndex],
          oldValue, newValue, deltaLambda);
    }

    // Update the system with new compositions
    return updateSystemWithNewCompositions();
  }

  /**
   * Update the thermodynamic system with the new outlet compositions.
   *
   * @return True if update was successful, false otherwise
   */
  private boolean updateSystemWithNewCompositions() {
    try {
      SystemInterface system = getOutletStream().getThermoSystem();

      // Update component moles in the system
      for (int i = 0; i < processedComponents.size(); i++) {
        String compName = processedComponents.get(i);
        double newMoles = outlet_mole.get(i);

        // Find component index in system
        int compIndex = -1;
        for (int j = 0; j < system.getNumberOfComponents(); j++) {
          if (compName.equals(system.getComponent(j).getComponentName())) {
            compIndex = j;
            break;
          }
        }

        if (compIndex >= 0) {
          // Set new moles
          double currentMoles = system.getComponent(compIndex).getNumberOfMolesInPhase();
          double molesToAdd = newMoles - currentMoles;

          if (Math.abs(molesToAdd) > 1e-15) {
            system.addComponent(compIndex, molesToAdd, 0);
          }
        }
      }

      // Reinitialize the system
      system.init(0);
      system.init(1);

      // Recalculate objective function values with new compositions and Lagrange multipliers
      calculateObjectiveFunctionValues(system);

      // Recalculate element mole balances
      calculateElementMoleBalance(system, elementMoleBalanceOut, false);
      for (int i = 0; i < elementNames.length; i++) {
        elementMoleBalanceDiff[i] = elementMoleBalanceOut[i] - elementMoleBalanceIn[i];
      }

      return true;

    } catch (Exception e) {
      logger.error("Error updating system with new compositions: " + e.getMessage());
      return false;
    }
  }

  /**
   * Update the system with the final outlet composition after convergence. This method assigns the
   * converged outlet composition to the system state.
   */
  public void updateSystemWithFinalComposition() {
    SystemInterface system = getOutletStream().getThermoSystem();

    logger.info("Updating system with final converged composition");

    // Update mole numbers from outlet_mole list
    for (int i = 0; i < Math.min(outlet_mole.size(), system.getNumberOfComponents()); i++) {
      double finalMoles = outlet_mole.get(i);
      String compName = system.getComponent(i).getComponentName();

      // Set the final mole number for this component
      system.addComponent(i, finalMoles - system.getComponent(i).getNumberOfMolesInPhase(), 0);

      logger.debug("Updated " + compName + " moles to " + finalMoles);
    }

    // Re-initialize system with new composition
    system.init(0);
    system.init(1);

    logger.info("System updated with final composition");
  }

  /**
   * Set maximum number of Newton-Raphson iterations.
   *
   * @param maxIterations Maximum number of iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get maximum number of Newton-Raphson iterations.
   *
   * @return Maximum number of iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set convergence tolerance for Newton-Raphson iterations.
   *
   * @param convergenceTolerance Convergence tolerance
   */
  public void setConvergenceTolerance(double convergenceTolerance) {
    this.convergenceTolerance = convergenceTolerance;
  }

  /**
   * Get convergence tolerance for Newton-Raphson iterations.
   *
   * @return Convergence tolerance
   */
  public double getConvergenceTolerance() {
    return convergenceTolerance;
  }

  /**
   * Get actual number of iterations performed.
   *
   * @return Actual iterations performed
   */
  public int getActualIterations() {
    return actualIterations;
  }

  /**
   * Check if iterations converged.
   *
   * @return true if converged, false otherwise
   */
  public boolean hasConverged() {
    return converged;
  }

  /**
   * Get final convergence error.
   *
   * @return Final convergence error
   */
  public double getFinalConvergenceError() {
    return finalConvergenceError;
  }

  /**
   * Solve Gibbs equilibrium using Newton-Raphson iterations with convergence control.
   *
   * @param alphaComposition Step size for composition updates
   * @return true if converged, false otherwise
   */
  public boolean solveGibbsEquilibrium(double alphaComposition) {
    converged = false;
    actualIterations = 0;
    finalConvergenceError = Double.MAX_VALUE;

    logger.info("Starting Gibbs equilibrium solution with Newton-Raphson iterations");
    logger.info("Maximum iterations: " + maxIterations);
    logger.info("Convergence tolerance: " + convergenceTolerance);
    logger.info("Composition step size: " + alphaComposition);

    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      actualIterations = iteration;

      // Calculate current objective function values
      SystemInterface outletSystem = getOutletStream().getThermoSystem();
      calculateObjectiveFunctionValues(outletSystem);

      // Calculate F vector norm for convergence check
      Map<String, Double> fValues = getObjectiveFunctionValues();
      double fNorm = 0.0;
      for (Double value : fValues.values()) {
        fNorm += value * value;
      }
      fNorm = Math.sqrt(fNorm);

      logger.debug("Iteration " + iteration + ": F vector norm = " + fNorm);

      // Perform Newton-Raphson iteration step
      double[] deltaX = performNewtonRaphsonIteration();

      if (deltaX == null) {
        logger.warn("Newton-Raphson iteration failed at iteration " + iteration);
        finalConvergenceError = fNorm;
        return false;
      }

      // Calculate delta vector norm
      double deltaXNorm = 0.0;
      for (double value : deltaX) {
        deltaXNorm += value * value;
      }
      deltaXNorm = Math.sqrt(deltaXNorm);

      logger.debug("Iteration " + iteration + ": Delta vector norm = " + deltaXNorm);

      // Check convergence
      if (deltaXNorm < convergenceTolerance) {
        logger.info("Converged at iteration " + iteration + " with delta norm = " + deltaXNorm);
        converged = true;
        finalConvergenceError = deltaXNorm;
        return true;
      }

      // Perform iteration update
      boolean updateSuccess = performIterationUpdate(deltaX, alphaComposition);
      if (!updateSuccess) {
        logger.warn("Iteration update failed at iteration " + iteration);
        finalConvergenceError = deltaXNorm;
        return false;
      }

      finalConvergenceError = deltaXNorm;
    }

    // Maximum iterations reached without convergence
    logger.warn("Maximum iterations (" + maxIterations + ") reached without convergence");
    logger.warn("Final convergence error: " + finalConvergenceError);
    return false;
  }

  /**
   * Solve Gibbs equilibrium using Newton-Raphson iterations with default step size.
   *
   * @return true if converged, false otherwise
   */
  public boolean solveGibbsEquilibrium() {
    return solveGibbsEquilibrium(0.0001); // Default step size
  }

  /**
   * Get labels for delta vector components.
   *
   * @return List of delta vector labels
   */
  public List<String> getDeltaVectorLabels() {
    List<String> labels = new ArrayList<>();
    
    // Add component labels (for composition changes)
    SystemInterface outletSystem = getOutletStream().getThermoSystem();
    for (int i = 0; i < outletSystem.getNumberOfComponents(); i++) {
      labels.add(outletSystem.getComponent(i).getComponentName());
    }
    
    // Add Lagrange multiplier labels
    for (String element : elementNames) {
      labels.add("λ[" + element + "]");
    }
    
    return labels;
  }

  /**
   * Get method to verify Jacobian inverse.
   *
   * @return true if verification passes
   */
  public boolean verifyJacobianInverse() {
    if (jacobianMatrix == null || jacobianInverse == null) {
      return false;
    }
    
    try {
      Matrix J = new Matrix(jacobianMatrix);
      Matrix JInv = new Matrix(jacobianInverse);
      Matrix identity = J.times(JInv);
      
      double tolerance = 1e-10;
      int size = identity.getRowDimension();
      
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
          double value = identity.get(i, j);
          double expected = (i == j) ? 1.0 : 0.0;
          
          if (Math.abs(value - expected) > tolerance) {
            return false;
          }
        }
      }
      
      return true;
    } catch (RuntimeException e) {
      logger.error("Error verifying Jacobian inverse", e);
      return false;
    }
  }

  /**
   * Perform iteration update with delta vector.
   *
   * @param deltaX Delta vector from Newton-Raphson iteration
   * @param alphaComposition Step size for composition updates
   * @return true if update was successful
   */
  public boolean performIterationUpdate(double[] deltaX, double alphaComposition) {
    if (deltaX == null || deltaX.length == 0) {
      return false;
    }
    
    try {
      SystemInterface outletSystem = getOutletStream().getThermoSystem();
      int numComponents = outletSystem.getNumberOfComponents();
      
      // Update compositions with step size
      for (int i = 0; i < numComponents && i < deltaX.length; i++) {
        double currentMoles = outlet_mole.get(i);
        double newMoles = currentMoles + alphaComposition * deltaX[i];
        
        // Enforce minimum mole constraint
        newMoles = Math.max(newMoles, 1e-15);
        
        outlet_mole.set(i, newMoles);
      }
      
      // Update Lagrange multipliers (full step)
      for (int i = 0; i < elementNames.length && (numComponents + i) < deltaX.length; i++) {
        lambda[i] = lambda[i] + deltaX[numComponents + i];
      }
      
      // Recalculate objective function with new values
      calculateObjectiveFunctionValues(outletSystem);
      
      return true;
    } catch (Exception e) {
      logger.error("Error performing iteration update", e);
      return false;
    }
  }
}
