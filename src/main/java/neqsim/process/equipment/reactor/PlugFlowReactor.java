package neqsim.process.equipment.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Plug flow reactor (PFR) for rigorous kinetic modeling of chemical reactions.
 *
 * <p>
 * Models chemical transformation along a tubular reactor by solving coupled ordinary differential
 * equations for species molar flows, temperature, and pressure as a function of axial position. The
 * reactor supports homogeneous gas-phase and heterogeneous catalytic reactions with multiple
 * reaction types (power-law, LHHW, reversible).
 * </p>
 *
 * <h2>Governing Equations</h2>
 * <ul>
 * <li>Species balance: dFi/dz = Ac * sum(nu_ij * r_j)</li>
 * <li>Energy balance: dT/dz = (-sum(r_j * dH_j) * Ac + U*pi*D*(Tc-T)) / sum(Fi*Cp_i)</li>
 * <li>Pressure drop: dP/dz from Ergun equation (packed bed) or friction factor (empty tube)</li>
 * </ul>
 *
 * <h2>Operating Modes</h2>
 *
 * <table>
 * <caption>Supported energy modes</caption>
 * <tr>
 * <th>Mode</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>ADIABATIC</td>
 * <td>No heat transfer; T changes from reaction enthalpy only</td>
 * </tr>
 * <tr>
 * <td>ISOTHERMAL</td>
 * <td>T held constant; heat duty calculated</td>
 * </tr>
 * <tr>
 * <td>COOLANT</td>
 * <td>Heat exchange with coolant at Tc via overall U</td>
 * </tr>
 * </table>
 *
 * <h2>Integration Methods</h2>
 * <ul>
 * <li>Forward Euler (simple, first-order)</li>
 * <li>Classical 4th-order Runge-Kutta (RK4, default, higher accuracy)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create reaction
 * KineticReaction rxn = new KineticReaction("A to B");
 * rxn.addReactant("methane", 1, 1.0);
 * rxn.addProduct("hydrogen", 2);
 * rxn.setPreExponentialFactor(1e10);
 * rxn.setActivationEnergy(150000.0);
 * rxn.setHeatOfReaction(-75000.0);
 *
 * // Configure catalyst
 * CatalystBed catalyst = new CatalystBed();
 * catalyst.setParticleDiameter(3.0, "mm");
 * catalyst.setVoidFraction(0.40);
 * catalyst.setBulkDensity(800.0);
 *
 * // Build reactor
 * PlugFlowReactor pfr = new PlugFlowReactor("PFR-1", feedStream);
 * pfr.addReaction(rxn);
 * pfr.setCatalystBed(catalyst);
 * pfr.setLength(5.0, "m");
 * pfr.setDiameter(0.10, "m");
 * pfr.setEnergyMode(PlugFlowReactor.EnergyMode.ADIABATIC);
 * pfr.run();
 *
 * double conversion = pfr.getConversion();
 * ReactorAxialProfile profile = pfr.getAxialProfile();
 * }</pre>
 *
 * @author esol
 * @version 1.0
 * @see KineticReaction
 * @see CatalystBed
 * @see ReactorAxialProfile
 */
public class PlugFlowReactor extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PlugFlowReactor.class);

  /**
   * Energy balance mode.
   */
  public enum EnergyMode {
    /** No heat transfer. Temperature changes from reaction enthalpy only. */
    ADIABATIC,
    /** Temperature held constant. Heat duty calculated to maintain T. */
    ISOTHERMAL,
    /** Heat exchange with coolant stream. Q = U * perimeter * (T - Tc) per unit length. */
    COOLANT
  }

  /**
   * ODE integration method.
   */
  public enum IntegrationMethod {
    /** First-order forward Euler. */
    EULER,
    /** Classical 4th-order Runge-Kutta. */
    RK4
  }

  // ============================================================================
  // Geometry
  // ============================================================================

  /** Reactor tube length [m]. */
  private double length = 5.0;

  /** Reactor tube inner diameter [m]. */
  private double diameter = 0.10;

  /** Number of parallel tubes (multi-tube reactor). */
  private int numberOfTubes = 1;

  // ============================================================================
  // Reactions and Catalyst
  // ============================================================================

  /** List of kinetic reactions. */
  private List<KineticReaction> reactions = new ArrayList<KineticReaction>();

  /** Catalyst bed properties (null for homogeneous reactors). */
  private CatalystBed catalystBed = null;

  // ============================================================================
  // Operating Conditions
  // ============================================================================

  /** Energy balance mode. */
  private EnergyMode energyMode = EnergyMode.ADIABATIC;

  /** Coolant temperature [K] (used in COOLANT mode). */
  private double coolantTemperature = 298.15;

  /** Overall heat transfer coefficient U [W/(m2*K)] (used in COOLANT mode). */
  private double overallHeatTransferCoefficient = 50.0;

  // ============================================================================
  // Numerical Settings
  // ============================================================================

  /** Number of integration steps. */
  private int numberOfSteps = 100;

  /** Integration method. */
  private IntegrationMethod integrationMethod = IntegrationMethod.RK4;

  /** Re-flash thermodynamic properties every N steps. */
  private int propertyUpdateFrequency = 10;

  // ============================================================================
  // Key Component Tracking
  // ============================================================================

  /** Key component name for conversion tracking. */
  private String keyComponent = null;

  // ============================================================================
  // Results
  // ============================================================================

  /** Axial profile results. */
  private ReactorAxialProfile axialProfile = null;

  /** Overall conversion of key component [-]. */
  private double overallConversion = 0.0;

  /** Total heat duty [W] (positive = heat added, negative = heat removed). */
  private double heatDuty = 0.0;

  /** Total pressure drop [bar]. */
  private double pressureDrop = 0.0;

  /** Outlet temperature [K]. */
  private double outletTemperatureK = 0.0;

  /** Mean residence time [s]. */
  private double residenceTime = 0.0;

  /**
   * Constructor for PlugFlowReactor.
   *
   * @param name equipment name
   */
  public PlugFlowReactor(String name) {
    super(name);
  }

  /**
   * Constructor for PlugFlowReactor with inlet stream.
   *
   * @param name equipment name
   * @param inletStream the inlet feed stream
   */
  public PlugFlowReactor(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (reactions.isEmpty()) {
      logger.warn("PlugFlowReactor '{}': no reactions defined, passing through", getName());
      outStream.setThermoSystem(inStream.getThermoSystem().clone());
      outStream.run(id);
      setCalculationIdentifier(id);
      return;
    }

    // Clone inlet thermo system for modification
    SystemInterface system = inStream.getThermoSystem().clone();

    // Ensure product components exist
    ensureProductComponentsExist(system);

    // Initialize with a flash to get consistent properties
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      logger.error("Initial TP flash failed: {}", ex.getMessage());
    }
    system.initProperties();

    // Get component info
    int nComp = system.getNumberOfComponents();
    String[] compNames = new String[nComp];
    for (int i = 0; i < nComp; i++) {
      compNames[i] = system.getComponent(i).getComponentName();
    }

    // Get initial molar flows
    double[] molFlows = new double[nComp];
    double totalMolFlow = system.getTotalNumberOfMoles();
    for (int i = 0; i < nComp; i++) {
      molFlows[i] = system.getComponent(i).getNumberOfmoles();
    }

    // Initial conditions
    double currentT = system.getTemperature();
    double currentP = system.getPressure();

    // Store initial key component moles for conversion calculation
    double initialKeyMoles = 0.0;
    int keyCompIndex = -1;
    if (keyComponent != null) {
      for (int i = 0; i < nComp; i++) {
        if (compNames[i].equals(keyComponent)) {
          keyCompIndex = i;
          initialKeyMoles = molFlows[i];
          break;
        }
      }
    }
    if (keyCompIndex < 0 && nComp > 0) {
      // Default to first reactant of first reaction
      for (Map.Entry<String, Double> entry : reactions.get(0).getStoichiometry().entrySet()) {
        if (entry.getValue() < 0) {
          for (int i = 0; i < nComp; i++) {
            if (compNames[i].equals(entry.getKey())) {
              keyCompIndex = i;
              initialKeyMoles = molFlows[i];
              keyComponent = compNames[i];
              break;
            }
          }
          if (keyCompIndex >= 0) {
            break;
          }
        }
      }
    }

    // Geometry
    double tubeArea = Math.PI * diameter * diameter / 4.0;
    double totalArea = tubeArea * numberOfTubes;
    double perimeter = Math.PI * diameter;
    double dz = length / numberOfSteps;

    // Initialize axial profile
    axialProfile = new ReactorAxialProfile(numberOfSteps + 1, nComp, compNames);

    // Store initial point
    double currentConversion = 0.0;
    axialProfile.setData(0, 0.0, currentT, currentP, 0.0, 0.0, molFlows);

    // Accumulate heat duty
    double totalHeatDuty = 0.0;

    // Integration loop
    for (int step = 0; step < numberOfSteps; step++) {
      double z = step * dz;

      // Re-flash properties periodically to update Cp, density, etc.
      if (step % propertyUpdateFrequency == 0 && step > 0) {
        updateSystemState(system, molFlows, currentT, currentP);
        ops = new ThermodynamicOperations(system);
        try {
          ops.TPflash();
        } catch (Exception ex) {
          logger.debug("Property update flash failed at z={}: {}", z, ex.getMessage());
        }
        system.initProperties();
      }

      if (integrationMethod == IntegrationMethod.RK4) {
        // RK4 integration
        double[] state = packState(molFlows, currentT, currentP);
        double[] k1 = calculateDerivatives(system, state, nComp, totalArea, perimeter, compNames);
        double[] s2 = addScaled(state, k1, 0.5 * dz);
        double[] k2 = calculateDerivatives(system, s2, nComp, totalArea, perimeter, compNames);
        double[] s3 = addScaled(state, k2, 0.5 * dz);
        double[] k3 = calculateDerivatives(system, s3, nComp, totalArea, perimeter, compNames);
        double[] s4 = addScaled(state, k3, dz);
        double[] k4 = calculateDerivatives(system, s4, nComp, totalArea, perimeter, compNames);

        for (int i = 0; i < state.length; i++) {
          state[i] += dz / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]);
        }

        unpackState(state, molFlows, nComp);
        currentT = state[nComp];
        currentP = state[nComp + 1];

      } else {
        // Euler integration
        double[] state = packState(molFlows, currentT, currentP);
        double[] derivs =
            calculateDerivatives(system, state, nComp, totalArea, perimeter, compNames);
        for (int i = 0; i < state.length; i++) {
          state[i] += derivs[i] * dz;
        }
        unpackState(state, molFlows, nComp);
        currentT = state[nComp];
        currentP = state[nComp + 1];
      }

      // Enforce non-negative molar flows and minimum pressure
      for (int i = 0; i < nComp; i++) {
        molFlows[i] = Math.max(molFlows[i], 0.0);
      }
      currentP = Math.max(currentP, 0.1);

      // Isothermal mode: override temperature back to inlet
      if (energyMode == EnergyMode.ISOTHERMAL) {
        currentT = inStream.getThermoSystem().getTemperature();
      }

      // Calculate conversion
      if (keyCompIndex >= 0 && initialKeyMoles > 1e-30) {
        currentConversion = 1.0 - molFlows[keyCompIndex] / initialKeyMoles;
        currentConversion = Math.max(0.0, Math.min(1.0, currentConversion));
      }

      // Store axial profile
      double totalReactionRate = calculateTotalReactionRate(system, compNames);
      axialProfile.setData(step + 1, (step + 1) * dz, currentT, currentP, currentConversion,
          totalReactionRate, molFlows);
    }

    // Calculate overall results
    overallConversion = currentConversion;
    pressureDrop = inStream.getThermoSystem().getPressure() - currentP;
    outletTemperatureK = currentT;

    // Calculate heat duty for isothermal mode
    if (energyMode == EnergyMode.ISOTHERMAL) {
      heatDuty = calculateIsothermalHeatDuty(system, molFlows, compNames);
    }

    // Calculate residence time
    residenceTime = calculateResidenceTime(system, totalArea);

    // Set outlet stream
    updateSystemState(system, molFlows, currentT, currentP);
    ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      logger.error("Outlet TP flash failed: {}", ex.getMessage());
    }
    system.initProperties();

    outStream.setThermoSystem(system);
    outStream.run(id);

    setCalculationIdentifier(id);
  }

  /**
   * Calculate derivatives dF/dz, dT/dz, dP/dz for the state vector.
   *
   * @param system thermodynamic system for property evaluation
   * @param state packed state [F1..Fn, T, P]
   * @param nComp number of components
   * @param totalArea total cross-sectional area of all tubes [m2]
   * @param perimeter tube perimeter [m] (single tube)
   * @param compNames component names
   * @return derivative vector [dF1/dz..dFn/dz, dT/dz, dP/dz]
   */
  private double[] calculateDerivatives(SystemInterface system, double[] state, int nComp,
      double totalArea, double perimeter, String[] compNames) {
    double[] derivs = new double[nComp + 2];

    double temperature = state[nComp];
    double pressure = state[nComp + 1];

    // Get total molar flow for this state
    double totalMolFlow = 0.0;
    for (int i = 0; i < nComp; i++) {
      totalMolFlow += Math.max(state[i], 0.0);
    }
    if (totalMolFlow < 1e-30) {
      return derivs;
    }

    // Calculate reaction rates and species derivatives
    double totalHeatGeneration = 0.0;

    for (KineticReaction rxn : reactions) {
      double rate = rxn.calculateRate(system, 0);

      // Apply catalyst effectiveness and activity if heterogeneous
      if (catalystBed != null) {
        rate *= catalystBed.getActivityFactor();
      }

      // Convert rate to volumetric basis if needed
      double volumetricRate = convertRateToVolumetric(rate, rxn);

      // Species balances: dFi/dz = Ac * sum(nu_ij * r_j)
      for (Map.Entry<String, Double> entry : rxn.getStoichiometry().entrySet()) {
        String compName = entry.getKey();
        double stoichCoeff = entry.getValue();
        for (int i = 0; i < nComp; i++) {
          if (compNames[i].equals(compName)) {
            derivs[i] += totalArea * stoichCoeff * volumetricRate;
            break;
          }
        }
      }

      // Heat generation from reaction
      totalHeatGeneration += volumetricRate * rxn.getHeatOfReaction();
    }

    // Energy balance: dT/dz
    if (energyMode == EnergyMode.ADIABATIC) {
      double sumFiCpi = estimateTotalHeatCapacityFlow(system, state, nComp);
      if (Math.abs(sumFiCpi) > 1e-20) {
        derivs[nComp] = -totalHeatGeneration * totalArea / sumFiCpi;
      }
    } else if (energyMode == EnergyMode.COOLANT) {
      double sumFiCpi = estimateTotalHeatCapacityFlow(system, state, nComp);
      double heatTransfer = overallHeatTransferCoefficient * perimeter * numberOfTubes
          * (coolantTemperature - temperature);
      if (Math.abs(sumFiCpi) > 1e-20) {
        derivs[nComp] = (-totalHeatGeneration * totalArea + heatTransfer) / sumFiCpi;
      }
    }
    // ISOTHERMAL: dT/dz = 0 (handled by overriding T after integration)

    // Pressure drop: dP/dz
    if (catalystBed != null) {
      double gasDensity = getGasDensity(system);
      double gasViscosity = getGasViscosity(system);
      double superficialVelocity = getSuperVelocity(system, totalArea);
      double dPdz =
          catalystBed.calculatePressureDrop(superficialVelocity, gasDensity, gasViscosity);
      derivs[nComp + 1] = -dPdz / 1.0e5; // Pa/m to bar/m
    } else {
      // Empty tube friction loss (Darcy-Weisbach simplified)
      double gasDensity = getGasDensity(system);
      double superficialVelocity = getSuperVelocity(system, totalArea);
      double frictionFactor = 0.01; // simplified
      double dPdz = frictionFactor * gasDensity * superficialVelocity * superficialVelocity
          / (2.0 * diameter);
      derivs[nComp + 1] = -dPdz / 1.0e5;
    }

    return derivs;
  }

  /**
   * Convert reaction rate from its native basis to volumetric basis [mol/(m3_reactor * s)].
   *
   * @param rate rate in native units
   * @param rxn the kinetic reaction
   * @return volumetric rate [mol/(m3_reactor * s)]
   */
  private double convertRateToVolumetric(double rate, KineticReaction rxn) {
    if (rxn.getRateBasis() == KineticReaction.RateBasis.CATALYST_MASS && catalystBed != null) {
      // mol/(kg_cat * s) -> mol/(m3_reactor * s) by multiplying with bulk density
      return rate * catalystBed.getBulkDensity();
    } else if (rxn.getRateBasis() == KineticReaction.RateBasis.CATALYST_AREA
        && catalystBed != null) {
      // mol/(m2_cat * s) -> mol/(m3_reactor * s)
      return rate * catalystBed.getSpecificSurfaceArea() * catalystBed.getBulkDensity();
    }
    // Already volumetric
    return rate;
  }

  /**
   * Estimate total heat capacity flow sum(Fi * Cp_i) [J/(s*K)].
   *
   * @param system thermodynamic system
   * @param state current state vector
   * @param nComp number of components
   * @return total heat capacity flow [J/(s*K)]
   */
  private double estimateTotalHeatCapacityFlow(SystemInterface system, double[] state, int nComp) {
    // Use system Cp if available
    try {
      double cpMolar = system.getCp("J/mol/K");
      double totalMolFlow = 0.0;
      for (int i = 0; i < nComp; i++) {
        totalMolFlow += Math.max(state[i], 0.0);
      }
      return cpMolar * totalMolFlow;
    } catch (Exception ex) {
      // Fallback: estimate Cp ~ 30 J/(mol*K) for gas
      double totalMolFlow = 0.0;
      for (int i = 0; i < nComp; i++) {
        totalMolFlow += Math.max(state[i], 0.0);
      }
      return 30.0 * totalMolFlow;
    }
  }

  /**
   * Get gas density from the thermodynamic system.
   *
   * @param system thermodynamic system
   * @return gas density [kg/m3]
   */
  private double getGasDensity(SystemInterface system) {
    try {
      return system.getDensity("kg/m3");
    } catch (Exception ex) {
      return 10.0; // default fallback
    }
  }

  /**
   * Get gas viscosity from the thermodynamic system.
   *
   * @param system thermodynamic system
   * @return gas dynamic viscosity [Pa*s]
   */
  private double getGasViscosity(SystemInterface system) {
    try {
      if (system.hasPhaseType("gas")) {
        return system.getPhase("gas").getViscosity("kg/msec");
      }
      return system.getPhase(0).getViscosity("kg/msec");
    } catch (Exception ex) {
      return 1.5e-5; // typical gas viscosity fallback
    }
  }

  /**
   * Calculate superficial velocity through the reactor tubes.
   *
   * @param system thermodynamic system
   * @param totalArea total cross-sectional area [m2]
   * @return superficial velocity [m/s]
   */
  private double getSuperVelocity(SystemInterface system, double totalArea) {
    try {
      double volumeFlow =
          system.getVolume("m3") / system.getNumberOfMoles() * system.getTotalNumberOfMoles();
      return volumeFlow / totalArea;
    } catch (Exception ex) {
      return 1.0; // fallback
    }
  }

  /**
   * Calculate total reaction rate for profile storage.
   *
   * @param system thermodynamic system
   * @param compNames component names
   * @return total absolute reaction rate [mol/(m3*s)]
   */
  private double calculateTotalReactionRate(SystemInterface system, String[] compNames) {
    double totalRate = 0.0;
    for (KineticReaction rxn : reactions) {
      totalRate += Math.abs(rxn.calculateRate(system, 0));
    }
    return totalRate;
  }

  /**
   * Calculate isothermal heat duty by summing reaction enthalpies over the reactor.
   *
   * @param system thermodynamic system
   * @param molFlows current molar flows
   * @param compNames component names
   * @return heat duty [W]
   */
  private double calculateIsothermalHeatDuty(SystemInterface system, double[] molFlows,
      String[] compNames) {
    double duty = 0.0;
    for (KineticReaction rxn : reactions) {
      // Estimate total moles reacted from change in key component
      String firstReactant = null;
      double firstCoeff = 1.0;
      for (Map.Entry<String, Double> entry : rxn.getStoichiometry().entrySet()) {
        if (entry.getValue() < 0) {
          firstReactant = entry.getKey();
          firstCoeff = entry.getValue();
          break;
        }
      }
      if (firstReactant != null) {
        double initialMoles = 0.0;
        double currentMoles = 0.0;
        for (int i = 0; i < compNames.length; i++) {
          if (compNames[i].equals(firstReactant)) {
            initialMoles =
                inStream.getThermoSystem().getComponent(firstReactant).getNumberOfmoles();
            currentMoles = molFlows[i];
            break;
          }
        }
        double molesReacted = initialMoles - currentMoles;
        duty += -rxn.getHeatOfReaction() * molesReacted / Math.abs(firstCoeff);
      }
    }
    return duty;
  }

  /**
   * Calculate mean residence time.
   *
   * @param system thermodynamic system
   * @param totalArea total cross-sectional area [m2]
   * @return residence time [s]
   */
  private double calculateResidenceTime(SystemInterface system, double totalArea) {
    double reactorVolume = totalArea * length;
    try {
      double volumetricFlowRate =
          system.getVolume("m3") / system.getNumberOfMoles() * system.getTotalNumberOfMoles();
      if (volumetricFlowRate > 0) {
        return reactorVolume / volumetricFlowRate;
      }
    } catch (Exception ex) {
      // ignore
    }
    return 0.0;
  }

  /**
   * Pack molar flows, temperature, and pressure into a state vector.
   *
   * @param molFlows molar flows [mol/s]
   * @param temperature temperature [K]
   * @param pressure pressure [bara]
   * @return state vector [F1..Fn, T, P]
   */
  private double[] packState(double[] molFlows, double temperature, double pressure) {
    int n = molFlows.length;
    double[] state = new double[n + 2];
    System.arraycopy(molFlows, 0, state, 0, n);
    state[n] = temperature;
    state[n + 1] = pressure;
    return state;
  }

  /**
   * Unpack molar flows from state vector.
   *
   * @param state state vector
   * @param molFlows output molar flows array
   * @param nComp number of components
   */
  private void unpackState(double[] state, double[] molFlows, int nComp) {
    System.arraycopy(state, 0, molFlows, 0, nComp);
  }

  /**
   * Add scaled vector: result[i] = base[i] + scale * delta[i].
   *
   * @param base base vector
   * @param delta delta vector
   * @param scale scaling factor
   * @return result vector
   */
  private double[] addScaled(double[] base, double[] delta, double scale) {
    double[] result = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      result[i] = base[i] + scale * delta[i];
    }
    return result;
  }

  /**
   * Update the thermodynamic system to reflect current molar flows, T, and P.
   *
   * @param system thermodynamic system
   * @param molFlows current molar flows [mol/s]
   * @param temperature current temperature [K]
   * @param pressure current pressure [bara]
   */
  private void updateSystemState(SystemInterface system, double[] molFlows, double temperature,
      double pressure) {
    int nComp = system.getNumberOfComponents();
    for (int i = 0; i < nComp; i++) {
      double currentMoles = system.getComponent(i).getNumberOfmoles();
      double targetMoles = Math.max(molFlows[i], 1e-30);
      double delta = targetMoles - currentMoles;
      system.addComponent(i, delta);
    }
    system.setTemperature(temperature);
    system.setPressure(pressure);
    system.init(0);
  }

  /**
   * Ensure that all product components from reactions exist in the system.
   *
   * @param system thermodynamic system to check/update
   */
  private void ensureProductComponentsExist(SystemInterface system) {
    for (KineticReaction rxn : reactions) {
      for (Map.Entry<String, Double> entry : rxn.getStoichiometry().entrySet()) {
        String compName = entry.getKey();
        if (!system.hasComponent(compName)) {
          try {
            system.addComponent(compName, 1.0e-20);
          } catch (Exception ex) {
            logger.warn("Could not add product component '{}': {}", compName, ex.getMessage());
          }
        }
      }
    }
    system.createDatabase(true);
    system.init(0);
  }

  // ============================================================================
  // Configuration Methods
  // ============================================================================

  /**
   * Add a kinetic reaction to the reactor.
   *
   * @param reaction the kinetic reaction to add
   */
  public void addReaction(KineticReaction reaction) {
    reactions.add(reaction);
  }

  /**
   * Get the list of reactions.
   *
   * @return list of kinetic reactions
   */
  public List<KineticReaction> getReactions() {
    return reactions;
  }

  /**
   * Set catalyst bed properties.
   *
   * @param catalystBed catalyst bed (null for homogeneous reactor)
   */
  public void setCatalystBed(CatalystBed catalystBed) {
    this.catalystBed = catalystBed;
  }

  /**
   * Get catalyst bed properties.
   *
   * @return catalyst bed (null if homogeneous)
   */
  public CatalystBed getCatalystBed() {
    return catalystBed;
  }

  /**
   * Set reactor tube length.
   *
   * @param length length value
   * @param unit "m", "cm", "ft"
   */
  public void setLength(double length, String unit) {
    if ("cm".equals(unit)) {
      this.length = length / 100.0;
    } else if ("ft".equals(unit)) {
      this.length = length * 0.3048;
    } else {
      this.length = length;
    }
  }

  /**
   * Get reactor tube length [m].
   *
   * @return length in meters
   */
  public double getLength() {
    return length;
  }

  /**
   * Set reactor tube inner diameter.
   *
   * @param diameter diameter value
   * @param unit "m", "mm", "cm", "in"
   */
  public void setDiameter(double diameter, String unit) {
    if ("mm".equals(unit)) {
      this.diameter = diameter / 1000.0;
    } else if ("cm".equals(unit)) {
      this.diameter = diameter / 100.0;
    } else if ("in".equals(unit)) {
      this.diameter = diameter * 0.0254;
    } else {
      this.diameter = diameter;
    }
  }

  /**
   * Get reactor tube inner diameter [m].
   *
   * @return diameter in meters
   */
  public double getDiameter() {
    return diameter;
  }

  /**
   * Set number of parallel tubes.
   *
   * @param numberOfTubes number of tubes (1 or more)
   */
  public void setNumberOfTubes(int numberOfTubes) {
    this.numberOfTubes = Math.max(1, numberOfTubes);
  }

  /**
   * Get number of parallel tubes.
   *
   * @return number of tubes
   */
  public int getNumberOfTubes() {
    return numberOfTubes;
  }

  /**
   * Set energy balance mode.
   *
   * @param energyMode ADIABATIC, ISOTHERMAL, or COOLANT
   */
  public void setEnergyMode(EnergyMode energyMode) {
    this.energyMode = energyMode;
  }

  /**
   * Get energy balance mode.
   *
   * @return energy mode
   */
  public EnergyMode getEnergyMode() {
    return energyMode;
  }

  /**
   * Set coolant temperature for COOLANT energy mode.
   *
   * @param temperature temperature value
   * @param unit "K", "C", "F"
   */
  public void setCoolantTemperature(double temperature, String unit) {
    if ("C".equals(unit)) {
      this.coolantTemperature = temperature + 273.15;
    } else if ("F".equals(unit)) {
      this.coolantTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.coolantTemperature = temperature;
    }
  }

  /**
   * Get coolant temperature [K].
   *
   * @return coolant temperature in Kelvin
   */
  public double getCoolantTemperature() {
    return coolantTemperature;
  }

  /**
   * Set overall heat transfer coefficient for COOLANT mode.
   *
   * @param coefficient U [W/(m2*K)]
   */
  public void setOverallHeatTransferCoefficient(double coefficient) {
    this.overallHeatTransferCoefficient = coefficient;
  }

  /**
   * Get overall heat transfer coefficient [W/(m2*K)].
   *
   * @return U
   */
  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoefficient;
  }

  /**
   * Set number of integration steps.
   *
   * @param steps number of steps (minimum 10)
   */
  public void setNumberOfSteps(int steps) {
    this.numberOfSteps = Math.max(10, steps);
  }

  /**
   * Get number of integration steps.
   *
   * @return number of steps
   */
  public int getNumberOfSteps() {
    return numberOfSteps;
  }

  /**
   * Set integration method.
   *
   * @param method "EULER" or "RK4"
   */
  public void setIntegrationMethod(String method) {
    if ("EULER".equalsIgnoreCase(method)) {
      this.integrationMethod = IntegrationMethod.EULER;
    } else {
      this.integrationMethod = IntegrationMethod.RK4;
    }
  }

  /**
   * Get integration method.
   *
   * @return integration method
   */
  public IntegrationMethod getIntegrationMethod() {
    return integrationMethod;
  }

  /**
   * Set frequency of thermodynamic property updates.
   *
   * @param frequency re-flash every N steps
   */
  public void setPropertyUpdateFrequency(int frequency) {
    this.propertyUpdateFrequency = Math.max(1, frequency);
  }

  /**
   * Get property update frequency.
   *
   * @return frequency in steps
   */
  public int getPropertyUpdateFrequency() {
    return propertyUpdateFrequency;
  }

  /**
   * Set key component for conversion tracking.
   *
   * @param componentName name of key component (typically limiting reactant)
   */
  public void setKeyComponent(String componentName) {
    this.keyComponent = componentName;
  }

  /**
   * Get key component name.
   *
   * @return key component name
   */
  public String getKeyComponent() {
    return keyComponent;
  }

  // ============================================================================
  // Result Getters
  // ============================================================================

  /**
   * Get overall conversion of the key component.
   *
   * @return conversion [0-1]
   */
  public double getConversion() {
    return overallConversion;
  }

  /**
   * Get the axial profile results.
   *
   * @return reactor axial profile (null if not yet run)
   */
  public ReactorAxialProfile getAxialProfile() {
    return axialProfile;
  }

  /**
   * Get total heat duty.
   *
   * @return heat duty in Watts (positive = heat added)
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get total heat duty in specified unit.
   *
   * @param unit "W", "kW", or "MW"
   * @return heat duty in specified unit
   */
  public double getHeatDuty(String unit) {
    if ("kW".equals(unit)) {
      return heatDuty / 1.0e3;
    } else if ("MW".equals(unit)) {
      return heatDuty / 1.0e6;
    }
    return heatDuty;
  }

  /**
   * Get total pressure drop across the reactor.
   *
   * @return pressure drop [bar]
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Get outlet temperature.
   *
   * @return outlet temperature [K]
   */
  @Override
  public double getOutletTemperature() {
    return outletTemperatureK;
  }

  /**
   * Get mean residence time.
   *
   * @return residence time [s]
   */
  public double getResidenceTime() {
    return residenceTime;
  }

  /**
   * Get gas hourly space velocity.
   *
   * @return GHSV [1/hr]
   */
  public double getSpaceVelocity() {
    double tubeArea = Math.PI * diameter * diameter / 4.0;
    double reactorVolume = tubeArea * numberOfTubes * length;
    if (reactorVolume <= 0) {
      return 0.0;
    }
    try {
      double volumetricFlowRate =
          inStream.getThermoSystem().getVolume("m3") / inStream.getThermoSystem().getNumberOfMoles()
              * inStream.getThermoSystem().getTotalNumberOfMoles() * 3600.0;
      return volumetricFlowRate / reactorVolume;
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (inStream != null) {
      streams.add(inStream);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (outStream != null) {
      streams.add(outStream);
    }
    return streams;
  }
}
