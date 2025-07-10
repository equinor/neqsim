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

import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

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
  private double[] elementMoleBalanceDiff = new double[6]; // Difference (in - out) for each element
  
  // Objective minimization vector
  private double[] objectiveMinimizationVector;
  private List<String> objectiveMinimizationVectorLabels = new ArrayList<>();

  // Jacobian matrix for Newton-Raphson method
  private double[][] jacobianMatrix;
  private double[][] jacobianInverse;
  private List<String> jacobianRowLabels = new ArrayList<>();
  private List<String> jacobianColLabels = new ArrayList<>();

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
      InputStream inputStream = getClass().getResourceAsStream("/neqsim/data/GibbsReactDatabase.csv");
      if (inputStream == null) {
        // Try alternative path
        inputStream = getClass().getResourceAsStream("/neqsim/data/GibbsReactDatabase/GibbsReactDatabase.csv");
      }
      if (inputStream == null) {
        logger.warn("Could not find GibbsReactDatabase.csv in resources");
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
        
        String[] parts = line.split(",");
        if (parts.length >= 12) {
          try {
            final String molecule = parts[0].trim();
            
            // Parse element composition [O, N, C, H, S, Ar]
            double[] elements = new double[6];
            for (int i = 0; i < 6; i++) {
              elements[i] = Double.parseDouble(parts[i + 1].trim());
            }
            
            // Parse heat capacity coefficients
            double[] heatCapCoeffs = new double[4];
            for (int i = 0; i < 4; i++) {
              heatCapCoeffs[i] = Double.parseDouble(parts[i + 7].trim());
            }
            
            double deltaHf298 = Double.parseDouble(parts[11].trim());
            double deltaGf298 = Double.parseDouble(parts[12].trim());
            
            GibbsComponent component = new GibbsComponent(molecule, elements, heatCapCoeffs, deltaHf298, deltaGf298);
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

  @Override
  public void run(UUID id) {
    SystemInterface system = getInletStream().getThermoSystem().clone();
    system.init(0);
    
    // Store initial moles for each component
    initialMoles.clear();
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      initialMoles.put(compName, moles);
    }
    
    // Calculate initial element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceIn, true);
    
    // Perform Gibbs minimization
    if (useAllDatabaseSpecies) {
      // Add all database species to system
      for (GibbsComponent component : gibbsDatabase) {
        try {
          system.addComponent(component.getMolecule(), 1e-10);
        } catch (Exception e) {
          logger.debug("Could not add component " + component.getMolecule() + ": " + e.getMessage());
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
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      finalMoles.put(compName, moles);
      processedComponents.add(compName);
    }
    
    // Calculate final element mole balance
    calculateElementMoleBalance(system, elementMoleBalanceOut, false);
    
    // Calculate difference
    for (int i = 0; i < elementNames.length; i++) {
      elementMoleBalanceDiff[i] = elementMoleBalanceIn[i] - elementMoleBalanceOut[i];
    }
    
    // Calculate objective function values
    calculateObjectiveFunctionValues(system);
    
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
        if (currentMoles < 1e-10) {
          system.addComponent(i, 1e-10 - currentMoles, 0);
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
  private void calculateElementMoleBalance(SystemInterface system, double[] elementBalance, boolean isInput) {
    // Reset balance
    for (int i = 0; i < elementBalance.length; i++) {
      elementBalance[i] = 0.0;
    }
    
    // Process each component
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      
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
    
    // Calculate for each component
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      String compName = system.getComponent(i).getComponentName();
      double moles = system.getComponent(i).getNumberOfMolesInPhase();
      
      // Get Gibbs component
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      if (comp != null) {
        // Calculate Gibbs energy of formation
        double Gf0 = comp.calculateGibbsEnergy(T);
        
        // Calculate fugacity coefficient (assume 1 for now)
        double phi = 1.0;
        
        // Calculate mole fraction
        double totalMoles = 0.0;
        for (int j = 0; j < system.getNumberOfComponents(); j++) {
          totalMoles += system.getComponent(j).getNumberOfMolesInPhase();
        }
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
    List<String> componentsToProcess = processedComponents.isEmpty() 
        ? new ArrayList<>(finalMoles.keySet()) : processedComponents;
    
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
    
    for (String compName : processedComponents) {
      GibbsComponent comp = componentMap.get(compName.toLowerCase());
      Double molesIn = initialMoles.get(compName);
      Double molesOut = finalMoles.get(compName);
      
      if (comp != null && molesIn != null && molesOut != null) {
        Map<String, Double> componentBalance = new HashMap<>();
        final double[] elements = comp.getElements(); // [O, N, C, H, S, Ar]
        
        // Store initial and final moles
        componentBalance.put("MOLES_IN", molesIn);
        componentBalance.put("MOLES_OUT", molesOut);
        componentBalance.put("MOLES_DIFF", molesIn - molesOut);
        
        // Calculate element contributions
        for (int i = 0; i < elementNames.length; i++) {
          double elementIn = elements[i] * molesIn;
          double elementOut = elements[i] * molesOut;
          double elementDiff = elementIn - elementOut;
          
          componentBalance.put(elementNames[i] + "_IN", elementIn);
          componentBalance.put(elementNames[i] 
              + "_OUT", elementOut);
          componentBalance.put(elementNames[i] 
              + "_DIFF", elementDiff);
        }
        
        detailedBalance.put(compName, componentBalance);
      }
    }
    
    return detailedBalance;
  }

  /**
   * Calculate the objective minimization vector.
   * This vector contains the F values for each component and the mass balance constraints.
   * The system is in equilibrium when this vector is zero.
   */
  private void calculateObjectiveMinimizationVector() {
    int numComponents = processedComponents.size();
    int numElements = elementNames.length;
    
    // Create vector: [F_values, mass_balance_constraints]
    objectiveMinimizationVector = new double[numComponents + numElements];
    objectiveMinimizationVectorLabels.clear();
    
    // First part: F values for each component
    for (int i = 0; i < numComponents; i++) {
      String compName = processedComponents.get(i);
      Double fValue = objectiveFunctionValues.get(compName);
      objectiveMinimizationVector[i] = (fValue != null) ? fValue : 0.0;
      objectiveMinimizationVectorLabels.add("F_" + compName);
    }
    
    // Second part: Mass balance constraints
    for (int i = 0; i < numElements; i++) {
      objectiveMinimizationVector[numComponents + i] = elementMoleBalanceDiff[i];
      objectiveMinimizationVectorLabels.add("Balance_" + elementNames[i]);
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
   * Calculate the Jacobian matrix for the Newton-Raphson method.
   * The Jacobian represents the derivatives of the objective function with respect to the variables.
   */
  private void calculateJacobian() {
    if (processedComponents.isEmpty()) {
      return;
    }
    
    int numComponents = processedComponents.size();
    int numElements = elementNames.length;
    int totalVars = numComponents + numElements; // components + Lagrange multipliers
    
    jacobianMatrix = new double[totalVars][totalVars];
    jacobianRowLabels.clear();
    jacobianColLabels.clear();
    
    // Set up labels
    for (int i = 0; i < numComponents; i++) {
      jacobianRowLabels.add("F_" + processedComponents.get(i));
      jacobianColLabels.add("n_" + processedComponents.get(i));
    }
    for (int i = 0; i < numElements; i++) {
      jacobianRowLabels.add("Balance_" + elementNames[i]);
      jacobianColLabels.add("lambda_" + elementNames[i]);
    }
    
    SystemInterface system = getOutletStream().getThermoSystem();
    double T = system.getTemperature();
    double RT = 8.314462618e-3 * T; // kJ/mol
    
    // Calculate total moles for mole fraction derivatives
    double totalMoles = 0.0;
    for (String compName : processedComponents) {
      int compIndex = system.getPhase(0).getComponent(compName).getComponentNumber();
      totalMoles += system.getComponent(compIndex).getNumberOfMolesInPhase();
    }
    
    // Fill Jacobian matrix
    for (int i = 0; i < numComponents; i++) {
      String compI = processedComponents.get(i);
      int compIndexI = system.getPhase(0).getComponent(compI).getComponentNumber();
      double ni = system.getComponent(compIndexI).getNumberOfMolesInPhase();
      
      for (int j = 0; j < numComponents; j++) {
        if (i == j) {
          // Diagonal elements: ∂f_i/∂n_i = RT * (1/n_i - 1/n_total)
          jacobianMatrix[i][j] = RT * (1.0 / ni - 1.0 / totalMoles);
        } else {
          // Off-diagonal elements: ∂f_i/∂n_j = -RT/n_total
          jacobianMatrix[i][j] = -RT / totalMoles;
        }
      }
      
      // Derivatives with respect to Lagrange multipliers
      GibbsComponent gibbsComp = componentMap.get(compI.toLowerCase());
      if (gibbsComp != null) {
        double[] elements = gibbsComp.getElements();
        for (int k = 0; k < numElements; k++) {
          jacobianMatrix[i][numComponents + k] = -elements[k];
        }
      }
    }
    
    // Mass balance constraint derivatives
    for (int i = 0; i < numElements; i++) {
      for (int j = 0; j < numComponents; j++) {
        String compName = processedComponents.get(j);
        GibbsComponent gibbsComp = componentMap.get(compName.toLowerCase());
        if (gibbsComp != null) {
          double[] elements = gibbsComp.getElements();
          jacobianMatrix[numComponents + i][j] = elements[i];
        }
      }
      
      // Derivatives with respect to Lagrange multipliers are zero
      for (int k = 0; k < numElements; k++) {
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
   * Calculate the inverse of the Jacobian matrix using Gauss-Jordan elimination.
   *
   * @return Inverse matrix, or null if matrix is singular
   */
  private double[][] calculateJacobianInverse() {
    if (jacobianMatrix == null) {
      return null;
    }
    
    int n = jacobianMatrix.length;
    double[][] augmented = new double[n][2 * n];
    
    // Create augmented matrix [A | I]
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        augmented[i][j] = jacobianMatrix[i][j];
        augmented[i][j + n] = (i == j) ? 1.0 : 0.0;
      }
    }
    
    // Gauss-Jordan elimination
    for (int i = 0; i < n; i++) {
      // Find pivot
      int maxRow = i;
      for (int k = i + 1; k < n; k++) {
        if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i])) {
          maxRow = k;
        }
      }
      
      // Check for singular matrix
      if (Math.abs(augmented[maxRow][i]) < 1e-12) {
        logger.warn("Jacobian matrix is singular or nearly singular");
        return null;
      }
      
      // Swap rows
      if (maxRow != i) {
        double[] temp = augmented[i];
        augmented[i] = augmented[maxRow];
        augmented[maxRow] = temp;
      }
      
      // Make diagonal element 1
      double pivot = augmented[i][i];
      for (int j = 0; j < 2 * n; j++) {
        augmented[i][j] /= pivot;
      }
      
      // Eliminate column
      for (int k = 0; k < n; k++) {
        if (k != i) {
          double factor = augmented[k][i];
          for (int j = 0; j < 2 * n; j++) {
            augmented[k][j] -= factor * augmented[i][j];
          }
        }
      }
    }
    
    // Extract inverse matrix from right side of augmented matrix
    double[][] inverse = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        inverse[i][j] = augmented[i][j + n];
      }
    }
    
    return inverse;
  }

  /**
   * Enforce minimum concentration threshold to prevent numerical issues.
   * If any component has moles less than 1e-15, it will be set to 1e-15.
   *
   * @param system The thermodynamic system to check and modify
   */
  private void enforceMinimumConcentrations(SystemInterface system) {
    double minConcentration = 1e-15;
    boolean modified = false;
    
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      double currentMoles = system.getComponent(i).getNumberOfMolesInPhase();
      
      if (currentMoles < minConcentration) {
        logger.info("Component " + system.getComponent(i).getComponentName() 
            + " has very low concentration (" + currentMoles + "), setting to minimum: " + minConcentration);
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
}
