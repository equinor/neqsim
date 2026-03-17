package neqsim.process.equipment.adsorber;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.BETAdsorption;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.FreundlichAdsorption;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.PotentialTheoryAdsorption;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.SipsAdsorption;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Adsorption bed unit operation supporting both steady-state and transient simulation.
 *
 * <p>
 * Models a fixed-bed adsorber with:
 * </p>
 * <ul>
 * <li>Equilibrium and rate-based (LDF) adsorption calculations</li>
 * <li>Mass transfer zone (MTZ) tracking with breakthrough curves</li>
 * <li>Transient bed saturation and regeneration dynamics</li>
 * <li>Support for PSA/TSA cycle scheduling via {@link AdsorptionCycleController}</li>
 * <li>Component-specific removal from the gas phase</li>
 * </ul>
 *
 * <p>
 * The bed is discretized into axial cells. Each cell tracks its own solid-phase loading and uses an
 * isotherm model to determine the local equilibrium. A linear driving force (LDF) model couples
 * mass transfer kinetics with the axial convection of gas.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AdsorptionBed extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for this class. */
  private static final Logger logger = LogManager.getLogger(AdsorptionBed.class);

  /** Universal gas constant (J/(mol K)). */
  private static final double R = 8.314;

  // ---- Bed geometry ----
  /** Internal diameter of the bed vessel (m). */
  private double bedDiameter = 1.0;

  /** Length/height of the packed bed (m). */
  private double bedLength = 3.0;

  /** Void fraction (inter-particle porosity). */
  private double voidFraction = 0.35;

  // ---- Adsorbent properties ----
  /** Name of the solid adsorbent material (matches AdsorptionParameters.csv). */
  private String adsorbentMaterial = "AC";

  /** Bulk density of the adsorbent packing (kg/m3). */
  private double adsorbentBulkDensity = 500.0;

  /** Particle diameter of the adsorbent pellets/beads (m). */
  private double particleDiameter = 0.003;

  /** Particle porosity (intra-particle). */
  private double particlePorosity = 0.40;

  // ---- Isotherm model ----
  /** Isotherm model type to use. */
  private IsothermType isothermType = IsothermType.LANGMUIR;

  /** The isotherm model instance (created on first use or when type changes). */
  private transient AdsorptionInterface isothermModel;

  // ---- Mass transfer ----
  /** Overall LDF mass transfer coefficients (1/s), per component. */
  private double[] kLDF;

  /** Default LDF coefficient when not explicitly set (1/s). */
  private double defaultKLDF = 0.01;

  // ---- Axial discretization ----
  /** Number of axial cells for the spatial grid. */
  private int numberOfCells = 50;

  /**
   * Solid-phase loading in each cell (mol/kg adsorbent). Dimensions:
   * {@code [numberOfCells][numberOfComponents]}.
   */
  private double[][] solidLoading;

  /**
   * Gas-phase molar concentration in each cell (mol/m3). Dimensions:
   * {@code [numberOfCells][numberOfComponents]}.
   */
  private double[][] gasConcentation;

  // ---- Transient state ----
  /** Total elapsed simulation time (s). */
  private double elapsedTime = 0.0;

  /** Whether the transient grid has been initialised. */
  private boolean transientInitialised = false;

  /** Pressure drop across the bed (Pa). */
  private double pressureDrop = 0.0;

  /** Whether to model pressure drop with Ergun equation. */
  private boolean calculatePressureDrop = true;

  // ---- Operating mode ----
  /** Current operating phase of the adsorber. */
  private AdsorptionCycleController.CyclePhase currentPhase =
      AdsorptionCycleController.CyclePhase.ADSORPTION;

  /** Whether the bed is currently in desorption mode. */
  private boolean desorptionMode = false;

  /** Desorption purge-gas flow rate (mol/s) — zero means use feed flow. */
  private double purgeFlowRate = 0.0;

  /** Desorption temperature for TSA (K). Zero means same as feed (PSA only). */
  private double desorptionTemperature = 0.0;

  /** Desorption pressure for PSA (bara). Zero means same as feed. */
  private double desorptionPressure = 0.0;

  // ---- Working thermo system ----
  /** Working copy of the thermodynamic system. */
  private SystemInterface system;

  // ---- Breakthrough tracking ----
  /** Breakthrough threshold: fraction of inlet concentration (0-1). */
  private double breakthroughThreshold = 0.05;

  /** Whether any component has broken through (outlet/inlet &gt; threshold). */
  private boolean breakthroughOccurred = false;

  /** Time at which breakthrough was first detected (s). */
  private double breakthroughTime = -1.0;

  // ----------------------------------------------------------------
  // Constructors
  // ----------------------------------------------------------------

  /**
   * Constructor for AdsorptionBed.
   *
   * @param name the name of the unit operation
   */
  public AdsorptionBed(String name) {
    super(name);
  }

  /**
   * Constructor for AdsorptionBed with inlet stream.
   *
   * @param name the name of the unit operation
   * @param inletStream the inlet gas stream to the adsorber
   */
  public AdsorptionBed(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  // ----------------------------------------------------------------
  // Steady-state run
  // ----------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>
   * In steady-state mode the bed is assumed to be at cyclic-steady-state: the outlet composition
   * reflects the equilibrium loading at the feed conditions scaled by an efficiency factor derived
   * from number-of-transfer-units (NTU).
   * </p>
   */
  @Override
  public void run(UUID id) {
    system = getInletStream().getThermoSystem().clone();
    int numComp = system.getPhase(0).getNumberOfComponents();

    // Initialise the isotherm model
    AdsorptionInterface model = getOrCreateIsothermModel(system);
    model.setSolidMaterial(adsorbentMaterial);
    model.calcAdsorption(0);

    // Calculate bed NTU and efficiency
    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double bedVolume = bedCrossSection * bedLength;
    double adsorbentMass = bedVolume * (1.0 - voidFraction) * adsorbentBulkDensity;

    // Superficial velocity (m/s)
    double totalMolarFlow = system.getTotalNumberOfMoles(); // mol/s — from stream
    double gasDensity = system.getPhase(0).getDensity("mol/m3");
    double superficialVelocity = totalMolarFlow / (gasDensity * bedCrossSection);

    // Residence time in the void space
    double gasResidenceTime = (voidFraction * bedVolume) / (superficialVelocity * bedCrossSection);
    if (gasResidenceTime <= 0 || Double.isNaN(gasResidenceTime)) {
      gasResidenceTime = bedLength / Math.max(superficialVelocity, 1e-6);
    }

    // Pressure drop (Ergun equation)
    if (calculatePressureDrop) {
      pressureDrop = calcErgunPressureDrop(system, superficialVelocity);
    }

    // Remove adsorbed amounts from gas
    for (int comp = 0; comp < numComp; comp++) {
      double equilibriumLoading = model.getSurfaceExcess(comp); // mol/kg

      // Number of transfer units for this component
      double kLDFcomp = getKLDF(comp);
      double ntu = kLDFcomp * gasResidenceTime;
      double efficiency = 1.0 - Math.exp(-ntu);

      // Moles adsorbed per unit time at steady state
      double molesAdsorbed =
          efficiency * equilibriumLoading * adsorbentMass / Math.max(gasResidenceTime, 1e-10);

      // Cannot adsorb more than what is in the feed
      double inletMoles = system.getPhase(0).getComponent(comp).getNumberOfmoles();
      molesAdsorbed = Math.min(molesAdsorbed, inletMoles * 0.999);

      if (molesAdsorbed > 1e-15) {
        system.addComponent(comp, -molesAdsorbed);
      }
    }

    // Apply pressure drop
    if (pressureDrop > 0 && calculatePressureDrop) {
      double outletPressure = system.getPressure("Pa") - pressureDrop;
      if (outletPressure > 0) {
        system.setPressure(outletPressure, "Pa");
      }
    }

    // Flash the outlet
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      logger.error("TP-flash failed in AdsorptionBed: " + e.getMessage());
    }

    getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);
    setCalculationIdentifier(id);
  }

  // ----------------------------------------------------------------
  // Transient simulation
  // ----------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>
   * Transient simulation of the adsorption bed. The bed is discretized into axial cells. Each
   * time-step:
   * </p>
   * <ol>
   * <li>Convective transport of gas through the cells (upwind scheme)</li>
   * <li>LDF mass transfer between gas and solid in each cell</li>
   * <li>Update solid loadings and gas concentrations</li>
   * <li>Determine outlet composition from the last cell</li>
   * </ol>
   *
   * @param dt time step size (seconds)
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      setCalculationIdentifier(id);
      return;
    }

    if (!transientInitialised) {
      initialiseTransientGrid();
    }

    system = getInletStream().getThermoSystem().clone();
    int numComp = system.getPhase(0).getNumberOfComponents();

    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double cellLength = bedLength / numberOfCells;
    double cellVolume = bedCrossSection * cellLength;
    double cellVoidVolume = cellVolume * voidFraction;
    double cellAdsorbentMass = cellVolume * (1.0 - voidFraction) * adsorbentBulkDensity;

    // Inlet gas concentrations (mol/m3)
    double[] inletConc = new double[numComp];
    double gasDensityMol = system.getPhase(0).getDensity("mol/m3");
    for (int comp = 0; comp < numComp; comp++) {
      inletConc[comp] = system.getPhase(0).getComponent(comp).getx() * gasDensityMol;
    }

    // Superficial velocity (m/s)
    double totalMolarFlow = system.getTotalNumberOfMoles();
    double superficialVelocity = totalMolarFlow / (gasDensityMol * bedCrossSection);
    double interstitialVelocity = superficialVelocity / voidFraction;

    // CFL-limited sub-stepping
    double maxDt = 0.8 * cellLength / Math.max(interstitialVelocity, 1e-10);
    int subSteps = Math.max(1, (int) Math.ceil(dt / maxDt));
    double subDt = dt / subSteps;

    for (int step = 0; step < subSteps; step++) {
      // 1) Convective transport (upwind)
      double[][] newGasConc = new double[numberOfCells][numComp];
      for (int comp = 0; comp < numComp; comp++) {
        // Cell 0 gets inlet concentration
        double upstreamConc = desorptionMode ? 0.0 : inletConc[comp];
        for (int cell = 0; cell < numberOfCells; cell++) {
          double convFlux =
              interstitialVelocity * (upstreamConc - gasConcentation[cell][comp]) / cellLength;
          newGasConc[cell][comp] = gasConcentation[cell][comp] + subDt * convFlux;
          upstreamConc = gasConcentation[cell][comp];
        }
      }

      // 2) LDF mass-transfer in each cell
      for (int cell = 0; cell < numberOfCells; cell++) {
        // Create a local thermo system for equilibrium calculation
        SystemInterface localSystem = system.clone();
        localSystem.init(0);

        // During desorption, adjust local conditions to drive q* down
        if (desorptionMode) {
          if (desorptionPressure > 0) {
            localSystem.setPressure(desorptionPressure);
          } else {
            // Default: atmospheric pressure for PSA-style desorption
            localSystem.setPressure(1.01325);
          }
          if (desorptionTemperature > 0) {
            localSystem.setTemperature(desorptionTemperature);
          }
        }

        for (int comp = 0; comp < numComp; comp++) {
          double moleFrac = newGasConc[cell][comp] / Math.max(sumArray(newGasConc[cell]), 1e-20);
          localSystem.getPhase(0).getComponent(comp).setx(moleFrac);
        }

        AdsorptionInterface localModel = createIsothermModel(localSystem);
        localModel.setSolidMaterial(adsorbentMaterial);
        localModel.calcAdsorption(0);

        for (int comp = 0; comp < numComp; comp++) {
          double qStar = localModel.getSurfaceExcess(comp); // equilibrium loading (mol/kg)
          double qCurrent = solidLoading[cell][comp]; // current loading
          double kLDFcomp = getKLDF(comp);

          // Mass transfer: dq/dt = k_LDF * (q* - q)
          // During desorption q* < q (lower pressure/higher temp), so dqdt < 0
          double dqdt = kLDFcomp * (qStar - qCurrent);

          // Update solid loading
          solidLoading[cell][comp] += subDt * dqdt;
          solidLoading[cell][comp] = Math.max(0.0, solidLoading[cell][comp]);

          // Update gas concentration (mass balance in void space)
          double molesTransferred = dqdt * cellAdsorbentMass; // mol/s
          double concChange = molesTransferred / cellVoidVolume; // mol/m3/s
          newGasConc[cell][comp] -= subDt * concChange;
          newGasConc[cell][comp] = Math.max(0.0, newGasConc[cell][comp]);
        }
      }

      gasConcentation = newGasConc;
    }

    // 3) Set outlet composition from the last cell
    double[] outletConc = gasConcentation[numberOfCells - 1];
    double totalOutletConc = sumArray(outletConc);
    if (totalOutletConc > 1e-20) {
      for (int comp = 0; comp < numComp; comp++) {
        double moleFrac = outletConc[comp] / totalOutletConc;
        system.getPhase(0).getComponent(comp).setx(Math.max(moleFrac, 1e-30));
      }
    }

    // 4) Pressure drop
    if (calculatePressureDrop) {
      pressureDrop = calcErgunPressureDrop(system, superficialVelocity(system));
      double outPressure = system.getPressure("Pa") - pressureDrop;
      if (outPressure > 0) {
        system.setPressure(outPressure, "Pa");
      }
    }

    // 5) Check breakthrough
    checkBreakthrough(inletConc, outletConc);

    // 6) Flash and set outlet
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      logger.error("TP-flash failed during transient step: " + e.getMessage());
    }
    getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);

    elapsedTime += dt;
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  // ----------------------------------------------------------------
  // Transient grid management
  // ----------------------------------------------------------------

  /**
   * Initialise the axial discretization grid. Sets all solid loadings to zero (clean bed) and gas
   * concentrations to zero.
   */
  public void initialiseTransientGrid() {
    SystemInterface refSystem = getInletStream().getThermoSystem();
    int numComp = refSystem.getPhase(0).getNumberOfComponents();

    solidLoading = new double[numberOfCells][numComp];
    gasConcentation = new double[numberOfCells][numComp];

    // Initialise LDF array
    if (kLDF == null || kLDF.length != numComp) {
      kLDF = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        kLDF[i] = defaultKLDF;
      }
    }

    transientInitialised = true;
    breakthroughOccurred = false;
    breakthroughTime = -1.0;
    elapsedTime = 0.0;
    logger.info(
        "Transient grid initialised: " + numberOfCells + " cells, " + numComp + " components");
  }

  /**
   * Pre-load the bed to a specified fractional saturation. Useful for simulating partially
   * saturated beds or starting desorption.
   *
   * @param fractionalSaturation fraction of equilibrium loading (0 = clean, 1 = saturated)
   * @param phaseNum the phase number for equilibrium calculation
   */
  public void preloadBed(double fractionalSaturation, int phaseNum) {
    if (!transientInitialised) {
      initialiseTransientGrid();
    }
    SystemInterface refSystem = getInletStream().getThermoSystem().clone();
    AdsorptionInterface model = getOrCreateIsothermModel(refSystem);
    model.setSolidMaterial(adsorbentMaterial);
    model.calcAdsorption(phaseNum);

    int numComp = refSystem.getPhase(0).getNumberOfComponents();
    for (int cell = 0; cell < numberOfCells; cell++) {
      for (int comp = 0; comp < numComp; comp++) {
        solidLoading[cell][comp] = fractionalSaturation * model.getSurfaceExcess(comp);
      }
    }
    logger.info("Bed pre-loaded to " + (fractionalSaturation * 100) + "% saturation");
  }

  // ----------------------------------------------------------------
  // Desorption / regeneration
  // ----------------------------------------------------------------

  /**
   * Switch the bed into desorption mode. In desorption mode, the inlet concentration is treated as
   * zero (or purge gas) and the equilibrium loading is recalculated at desorption conditions.
   *
   * @param desorb {@code true} to enable desorption, {@code false} for adsorption
   */
  public void setDesorptionMode(boolean desorb) {
    this.desorptionMode = desorb;
    this.currentPhase = desorb ? AdsorptionCycleController.CyclePhase.DESORPTION
        : AdsorptionCycleController.CyclePhase.ADSORPTION;
    logger.info("Bed mode set to: " + currentPhase);
  }

  /**
   * Set the desorption temperature for TSA regeneration.
   *
   * @param temperature desorption temperature in Kelvin
   */
  public void setDesorptionTemperature(double temperature) {
    this.desorptionTemperature = temperature;
  }

  /**
   * Set the desorption pressure for PSA blowdown.
   *
   * @param pressure desorption pressure in bara
   */
  public void setDesorptionPressure(double pressure) {
    this.desorptionPressure = pressure;
  }

  /**
   * Set the purge gas flow rate during desorption.
   *
   * @param flowRate purge gas flow rate in mol/s
   */
  public void setPurgeFlowRate(double flowRate) {
    this.purgeFlowRate = flowRate;
  }

  // ----------------------------------------------------------------
  // Pressure-drop calculation
  // ----------------------------------------------------------------

  /**
   * Calculate pressure drop across the packed bed using the Ergun equation.
   *
   * <p>
   * The Ergun equation combines viscous (Blake-Kozeny) and inertial (Burke-Plummer) terms:
   * </p>
   *
   * $$\frac{\Delta P}{L} = \frac{150 \mu u_s (1-\varepsilon)^2}{\varepsilon^3 d_p^2} + \frac{1.75
   * \rho u_s^2 (1-\varepsilon)}{\varepsilon^3 d_p}$$
   *
   * @param sys the thermodynamic system for fluid properties
   * @param us superficial velocity (m/s)
   * @return total pressure drop (Pa)
   */
  private double calcErgunPressureDrop(SystemInterface sys, double us) {
    double mu = sys.getPhase(0).getViscosity("kg/msec");
    double rho = sys.getPhase(0).getDensity("kg/m3");
    double dp = particleDiameter;
    double eps = voidFraction;

    double viscousTerm = 150.0 * mu * us * Math.pow(1.0 - eps, 2) / (Math.pow(eps, 3) * dp * dp);
    double inertialTerm = 1.75 * rho * us * us * (1.0 - eps) / (Math.pow(eps, 3) * dp);

    return (viscousTerm + inertialTerm) * bedLength;
  }

  /**
   * Calculate superficial velocity from a thermodynamic system.
   *
   * @param sys the thermodynamic system
   * @return superficial velocity in m/s
   */
  private double superficialVelocity(SystemInterface sys) {
    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double gasDensityMol = sys.getPhase(0).getDensity("mol/m3");
    double totalMolarFlow = sys.getTotalNumberOfMoles();
    return totalMolarFlow / (gasDensityMol * bedCrossSection);
  }

  // ----------------------------------------------------------------
  // Breakthrough detection
  // ----------------------------------------------------------------

  /**
   * Check whether any component has broken through the bed.
   *
   * @param inletConc inlet gas concentrations (mol/m3)
   * @param outletConc outlet gas concentrations (mol/m3)
   */
  private void checkBreakthrough(double[] inletConc, double[] outletConc) {
    for (int comp = 0; comp < inletConc.length; comp++) {
      if (inletConc[comp] > 1e-15) {
        double ratio = outletConc[comp] / inletConc[comp];
        if (ratio > breakthroughThreshold && !breakthroughOccurred) {
          breakthroughOccurred = true;
          breakthroughTime = elapsedTime;
          logger.info("Breakthrough detected for component " + comp + " at time " + elapsedTime
              + " s (C/C0 = " + ratio + ")");
        }
      }
    }
  }

  // ----------------------------------------------------------------
  // Isotherm model management
  // ----------------------------------------------------------------

  /**
   * Get or create the isotherm model. Reuses the existing model if compatible, otherwise creates a
   * new one.
   *
   * @param sys the thermodynamic system
   * @return the adsorption model instance
   */
  private AdsorptionInterface getOrCreateIsothermModel(SystemInterface sys) {
    if (isothermModel == null) {
      isothermModel = createIsothermModel(sys);
    }
    return isothermModel;
  }

  /**
   * Create a new isotherm model of the configured type.
   *
   * @param sys the thermodynamic system
   * @return the adsorption model instance
   */
  private AdsorptionInterface createIsothermModel(SystemInterface sys) {
    switch (isothermType) {
      case LANGMUIR:
      case EXTENDED_LANGMUIR:
        return new LangmuirAdsorption(sys);
      case BET:
        return new BETAdsorption(sys);
      case FREUNDLICH:
        return new FreundlichAdsorption(sys);
      case SIPS:
        return new SipsAdsorption(sys);
      case DRA:
        return new PotentialTheoryAdsorption(sys);
      default:
        return new LangmuirAdsorption(sys);
    }
  }

  // ----------------------------------------------------------------
  // Profile and result queries
  // ----------------------------------------------------------------

  /**
   * Get the solid-phase loading profile along the bed.
   *
   * @param component component index
   * @return array of loadings (mol/kg) for each axial cell
   */
  public double[] getLoadingProfile(int component) {
    if (solidLoading == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfCells];
    for (int cell = 0; cell < numberOfCells; cell++) {
      profile[cell] = solidLoading[cell][component];
    }
    return profile;
  }

  /**
   * Get the gas-phase concentration profile along the bed.
   *
   * @param component component index
   * @return array of concentrations (mol/m3) for each axial cell
   */
  public double[] getConcentrationProfile(int component) {
    if (gasConcentation == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfCells];
    for (int cell = 0; cell < numberOfCells; cell++) {
      profile[cell] = gasConcentation[cell][component];
    }
    return profile;
  }

  /**
   * Get the average solid loading across the entire bed for a component.
   *
   * @param component component index
   * @return average loading in mol/kg
   */
  public double getAverageLoading(int component) {
    if (solidLoading == null) {
      return 0.0;
    }
    double sum = 0.0;
    for (int cell = 0; cell < numberOfCells; cell++) {
      sum += solidLoading[cell][component];
    }
    return sum / numberOfCells;
  }

  /**
   * Get the bed utilization factor (average loading / equilibrium loading).
   *
   * @param component component index
   * @return utilization factor (0 to 1)
   */
  public double getBedUtilization(int component) {
    if (solidLoading == null || isothermModel == null) {
      return 0.0;
    }
    double eqLoading = isothermModel.getSurfaceExcess(component);
    if (eqLoading <= 0) {
      return 0.0;
    }
    return getAverageLoading(component) / eqLoading;
  }

  /**
   * Estimate the length of the mass transfer zone for a component.
   *
   * <p>
   * The MTZ is defined as the axial distance over which the normalised gas concentration
   * transitions from 0.05 to 0.95 of the inlet concentration.
   * </p>
   *
   * @param component component index
   * @return mass transfer zone length in metres (0 if not applicable)
   */
  public double getMassTransferZoneLength(int component) {
    if (gasConcentation == null) {
      return 0.0;
    }
    double cellLength = bedLength / numberOfCells;

    // Find inlet concentration (first cell with significant loading, or use cell 0)
    double inletConc = gasConcentation[0][component];
    if (inletConc < 1e-15) {
      return 0.0;
    }

    int cellLow = -1;
    int cellHigh = -1;
    for (int cell = 0; cell < numberOfCells; cell++) {
      double cNorm = gasConcentation[cell][component] / inletConc;
      if (cNorm >= 0.05 && cellLow < 0) {
        cellLow = cell;
      }
      if (cNorm >= 0.95 && cellHigh < 0) {
        cellHigh = cell;
      }
    }
    if (cellLow < 0 || cellHigh < 0 || cellHigh <= cellLow) {
      return 0.0;
    }
    return (cellHigh - cellLow) * cellLength;
  }

  /**
   * Get the total mass of adsorbent in the bed.
   *
   * @return adsorbent mass in kg
   */
  public double getAdsorbentMass() {
    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    return bedCrossSection * bedLength * (1.0 - voidFraction) * adsorbentBulkDensity;
  }

  /**
   * Get the bed volume.
   *
   * @return bed volume in m3
   */
  public double getBedVolume() {
    return Math.PI / 4.0 * bedDiameter * bedDiameter * bedLength;
  }

  // ----------------------------------------------------------------
  // Utility
  // ----------------------------------------------------------------

  /**
   * Sum the elements of an array.
   *
   * @param arr the array
   * @return the sum
   */
  private double sumArray(double[] arr) {
    double sum = 0.0;
    for (double v : arr) {
      sum += v;
    }
    return sum;
  }

  // ----------------------------------------------------------------
  // JSON reporting
  // ----------------------------------------------------------------

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("name", getName());
    json.addProperty("equipmentType", "AdsorptionBed");

    // Geometry
    JsonObject geometry = new JsonObject();
    geometry.addProperty("bedDiameter_m", bedDiameter);
    geometry.addProperty("bedLength_m", bedLength);
    geometry.addProperty("bedVolume_m3", getBedVolume());
    geometry.addProperty("voidFraction", voidFraction);
    geometry.addProperty("particleDiameter_m", particleDiameter);
    geometry.addProperty("particlePorosity", particlePorosity);
    json.add("geometry", geometry);

    // Adsorbent
    JsonObject adsorbent = new JsonObject();
    adsorbent.addProperty("material", adsorbentMaterial);
    adsorbent.addProperty("bulkDensity_kg_m3", adsorbentBulkDensity);
    adsorbent.addProperty("totalMass_kg", getAdsorbentMass());
    json.add("adsorbent", adsorbent);

    // Model
    JsonObject modelInfo = new JsonObject();
    modelInfo.addProperty("isothermType", isothermType.name());
    modelInfo.addProperty("numberOfCells", numberOfCells);
    json.add("model", modelInfo);

    // Operating conditions
    JsonObject operating = new JsonObject();
    operating.addProperty("currentPhase", currentPhase.name());
    operating.addProperty("desorptionMode", desorptionMode);
    operating.addProperty("pressureDrop_Pa", pressureDrop);
    operating.addProperty("elapsedTime_s", elapsedTime);
    operating.addProperty("breakthroughOccurred", breakthroughOccurred);
    if (breakthroughTime >= 0) {
      operating.addProperty("breakthroughTime_s", breakthroughTime);
    }
    json.add("operating", operating);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  // ----------------------------------------------------------------
  // Validation
  // ----------------------------------------------------------------

  /** {@inheritDoc} */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = super.validateSetup();

    if (bedDiameter <= 0) {
      result.addError("geometry", "Bed diameter must be positive",
          "Set bed diameter: setBedDiameter(value)");
    }
    if (bedLength <= 0) {
      result.addError("geometry", "Bed length must be positive",
          "Set bed length: setBedLength(value)");
    }
    if (voidFraction <= 0 || voidFraction >= 1) {
      result.addError("geometry", "Void fraction must be between 0 and 1",
          "Set void fraction: setVoidFraction(value)");
    }
    if (adsorbentMaterial == null || adsorbentMaterial.trim().isEmpty()) {
      result.addError("adsorbent", "Adsorbent material not specified",
          "Set adsorbent material: setAdsorbentMaterial(name)");
    }
    if (adsorbentBulkDensity <= 0) {
      result.addError("adsorbent", "Bulk density must be positive",
          "Set adsorbent bulk density: setAdsorbentBulkDensity(value)");
    }

    return result;
  }

  // ----------------------------------------------------------------
  // Getters and setters
  // ----------------------------------------------------------------

  /**
   * Get the bed internal diameter.
   *
   * @return bed diameter in metres
   */
  public double getBedDiameter() {
    return bedDiameter;
  }

  /**
   * Set the bed internal diameter.
   *
   * @param diameter bed diameter in metres
   */
  public void setBedDiameter(double diameter) {
    this.bedDiameter = diameter;
  }

  /**
   * Get the bed length.
   *
   * @return bed length in metres
   */
  public double getBedLength() {
    return bedLength;
  }

  /**
   * Set the bed length.
   *
   * @param length bed length in metres
   */
  public void setBedLength(double length) {
    this.bedLength = length;
  }

  /**
   * Get the void fraction.
   *
   * @return void fraction (0 to 1)
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Set the void fraction (inter-particle porosity).
   *
   * @param voidFraction void fraction (0 to 1)
   */
  public void setVoidFraction(double voidFraction) {
    this.voidFraction = voidFraction;
  }

  /**
   * Get the adsorbent material name.
   *
   * @return adsorbent material name
   */
  public String getAdsorbentMaterial() {
    return adsorbentMaterial;
  }

  /**
   * Set the adsorbent material name (must match entry in AdsorptionParameters.csv).
   *
   * @param material adsorbent material name
   */
  public void setAdsorbentMaterial(String material) {
    this.adsorbentMaterial = material;
    if (isothermModel != null) {
      isothermModel.setSolidMaterial(material);
    }
  }

  /**
   * Get the adsorbent bulk density.
   *
   * @return bulk density in kg/m3
   */
  public double getAdsorbentBulkDensity() {
    return adsorbentBulkDensity;
  }

  /**
   * Set the adsorbent bulk density.
   *
   * @param density bulk density in kg/m3
   */
  public void setAdsorbentBulkDensity(double density) {
    this.adsorbentBulkDensity = density;
  }

  /**
   * Get the particle diameter.
   *
   * @return particle diameter in metres
   */
  public double getParticleDiameter() {
    return particleDiameter;
  }

  /**
   * Set the adsorbent particle diameter.
   *
   * @param diameter particle diameter in metres
   */
  public void setParticleDiameter(double diameter) {
    this.particleDiameter = diameter;
  }

  /**
   * Get the particle porosity.
   *
   * @return particle porosity (0 to 1)
   */
  public double getParticlePorosity() {
    return particlePorosity;
  }

  /**
   * Set the particle porosity (intra-particle).
   *
   * @param porosity particle porosity (0 to 1)
   */
  public void setParticlePorosity(double porosity) {
    this.particlePorosity = porosity;
  }

  /**
   * Get the isotherm type used by this bed.
   *
   * @return the isotherm type
   */
  public IsothermType getIsothermType() {
    return isothermType;
  }

  /**
   * Set the isotherm type.
   *
   * @param type the isotherm type
   */
  public void setIsothermType(IsothermType type) {
    this.isothermType = type;
    this.isothermModel = null; // force recreation
  }

  /**
   * Get the LDF mass transfer coefficient for a component.
   *
   * @param component component index
   * @return k_LDF in 1/s
   */
  public double getKLDF(int component) {
    if (kLDF == null || component >= kLDF.length) {
      return defaultKLDF;
    }
    return kLDF[component];
  }

  /**
   * Set the LDF mass transfer coefficient for a component.
   *
   * @param component component index
   * @param value k_LDF in 1/s
   */
  public void setKLDF(int component, double value) {
    if (kLDF == null) {
      int numComp = getInletStream().getThermoSystem().getPhase(0).getNumberOfComponents();
      kLDF = new double[numComp];
      for (int i = 0; i < numComp; i++) {
        kLDF[i] = defaultKLDF;
      }
    }
    if (component < kLDF.length) {
      kLDF[component] = value;
    }
  }

  /**
   * Set all LDF mass transfer coefficients to the same value.
   *
   * @param value k_LDF in 1/s
   */
  public void setKLDF(double value) {
    this.defaultKLDF = value;
    if (kLDF != null) {
      for (int i = 0; i < kLDF.length; i++) {
        kLDF[i] = value;
      }
    }
  }

  /**
   * Get the number of axial cells.
   *
   * @return number of cells
   */
  public int getNumberOfCells() {
    return numberOfCells;
  }

  /**
   * Set the number of axial cells for spatial discretization.
   *
   * @param cells number of cells (must be at least 2)
   */
  public void setNumberOfCells(int cells) {
    this.numberOfCells = Math.max(2, cells);
    this.transientInitialised = false; // force re-init
  }

  /**
   * Get the pressure drop across the bed.
   *
   * @return pressure drop in Pa
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Get the pressure drop across the bed in a specified unit.
   *
   * @param unit pressure unit ("Pa", "bar", "bara", "psi")
   * @return pressure drop in the given unit
   */
  public double getPressureDrop(String unit) {
    if ("bar".equals(unit) || "bara".equals(unit)) {
      return pressureDrop / 1e5;
    } else if ("psi".equals(unit)) {
      return pressureDrop / 6894.76;
    }
    return pressureDrop; // Pa
  }

  /**
   * Set whether to calculate pressure drop from the Ergun equation.
   *
   * @param calculate true to calculate, false to ignore
   */
  public void setCalculatePressureDrop(boolean calculate) {
    this.calculatePressureDrop = calculate;
  }

  /**
   * Get the elapsed simulation time.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTime() {
    return elapsedTime;
  }

  /**
   * Check if breakthrough has occurred.
   *
   * @return true if breakthrough detected
   */
  public boolean isBreakthroughOccurred() {
    return breakthroughOccurred;
  }

  /**
   * Get the breakthrough time.
   *
   * @return breakthrough time in seconds, or -1 if not yet occurred
   */
  public double getBreakthroughTime() {
    return breakthroughTime;
  }

  /**
   * Set the breakthrough detection threshold.
   *
   * @param threshold outlet/inlet concentration ratio (0 to 1)
   */
  public void setBreakthroughThreshold(double threshold) {
    this.breakthroughThreshold = threshold;
  }

  /**
   * Check if the bed is in desorption mode.
   *
   * @return true if desorbing
   */
  public boolean isDesorptionMode() {
    return desorptionMode;
  }

  /**
   * Get the current cycle phase.
   *
   * @return current cycle phase
   */
  public AdsorptionCycleController.CyclePhase getCurrentPhase() {
    return currentPhase;
  }

  /**
   * Reset the bed to a clean state (all loadings and concentrations to zero).
   */
  public void resetBed() {
    transientInitialised = false;
    elapsedTime = 0.0;
    breakthroughOccurred = false;
    breakthroughTime = -1.0;
    desorptionMode = false;
    currentPhase = AdsorptionCycleController.CyclePhase.ADSORPTION;
    isothermModel = null;
    solidLoading = null;
    gasConcentation = null;
    logger.info("Bed reset to clean state");
  }

  /**
   * Get the isotherm model currently used by this bed.
   *
   * @return the adsorption model, or null if not yet created
   */
  public AdsorptionInterface getIsothermModel() {
    return isothermModel;
  }
}
