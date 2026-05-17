package neqsim.process.equipment.adsorber;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.adsorber.MercuryRemovalMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Fixed-bed mercury removal unit operation using chemisorption (e.g. PuraSpec,
 * MRU).
 *
 * <p>
 * Models irreversible chemisorption of elemental mercury (Hg0) onto
 * metal-sulphide sorbents such as
 * CuS, FeS, or ZnS pellets commonly used in LNG pre-treatment. Unlike physical
 * adsorption the
 * reaction is non-regenerable: the bed is replaced when spent.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Irreversible Langmuir–Hinshelwood chemisorption kinetics</li>
 * <li>Time-dependent bed loading and mercury breakthrough tracking</li>
 * <li>Mass-transfer-zone (MTZ) length estimation</li>
 * <li>Degradation modelling for fouled/degraded column internals</li>
 * <li>Ergun-equation pressure drop</li>
 * <li>Integrated mechanical design and cost estimation</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MercuryRemovalBed extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Logger object for this class. */
  private static final Logger logger = LogManager.getLogger(MercuryRemovalBed.class);

  // ======================================================================
  // Bed geometry
  // ======================================================================

  /** Internal diameter of the bed vessel (m). */
  private double bedDiameter = 1.5;

  /** Length/height of the packed section (m). */
  private double bedLength = 4.0;

  /** Void fraction (inter-particle porosity). */
  private double voidFraction = 0.40;

  // ======================================================================
  // Sorbent properties
  // ======================================================================

  /** Name or trade-mark of the sorbent material (e.g. "PuraSpec", "MRU-CuS"). */
  private String sorbentType = "PuraSpec";

  /** Bulk density of the sorbent (kg/m3). */
  private double sorbentBulkDensity = 1100.0;

  /** Sorbent particle diameter (m). */
  private double particleDiameter = 0.004;

  /**
   * Maximum mercury loading capacity of the sorbent (mg Hg / kg sorbent). Typical
   * PuraSpec values
   * are 10–25 wt% Hg; default 10 wt% = 100 000 mg/kg.
   */
  private double maxMercuryCapacity = 100000.0;

  // ======================================================================
  // Chemisorption kinetics
  // ======================================================================

  /**
   * First-order rate constant for chemisorption (1/s). Relates to the
   * linear-driving-force
   * approximation: r = k * C_Hg * (1 - theta).
   * Typical value for PuraSpec-type CuS sorbents: 0.3-0.8 s⁻¹.
   */
  private double reactionRateConstant = 0.5;

  /** Activation energy for the chemisorption reaction (J/mol). */
  private double activationEnergy = 25000.0;

  /** Reference temperature for the rate constant (K). */
  private double referenceTemperature = 298.15;

  // ======================================================================
  // Degradation
  // ======================================================================

  /**
   * Overall degradation factor (0–1). At 1.0 the bed is brand-new; at lower
   * values the effective
   * capacity and/or rate constant are reduced because of fouling, liquid
   * carry-over, or other
   * damage to column internals.
   */
  private double degradationFactor = 1.0;

  /**
   * Fraction of bed that is bypassed due to channelling caused by degraded
   * internals (0–1). At 0
   * there is no bypass. Gas that bypasses does not contact sorbent.
   */
  private double bypassFraction = 0.0;

  // ======================================================================
  // Axial discretisation and transient state
  // ======================================================================

  /** Number of axial cells. */
  private int numberOfCells = 50;

  /** Mercury loading in each cell (mg Hg / kg sorbent). */
  private double[] cellLoading;

  /** Gas-phase Hg concentration in each cell (microgram/Nm3). */
  private double[] cellHgConcentration;

  /** Whether the transient grid has been initialised. */
  private boolean transientInitialised = false;

  /** Total elapsed on-stream time (hours). */
  private double elapsedTimeHours = 0.0;

  // ======================================================================
  // Operating / breakthrough
  // ======================================================================

  /**
   * Practical sorbent utilisation at replacement (0–1). In practice operators
   * change out sorbent when only a fraction of the theoretical capacity has been
   * used, because the mass-transfer zone leaves the tail of the bed under-utilised
   * and a safety margin is maintained before breakthrough. Typical values:
   * 0.4–0.6 for single beds, 0.7–0.9 for lead–lag configurations.
   */
  private double replacementUtilisation = 0.50;

  /** Breakthrough threshold as fraction of inlet Hg concentration (0–1). */
  private double breakthroughThreshold = 0.01;

  /** Whether breakthrough has been detected. */
  private boolean breakthroughOccurred = false;

  /** Time at which breakthrough was first detected (hours). */
  private double breakthroughTimeHours = -1.0;

  /** Pressure drop across the bed (Pa). */
  private double pressureDrop = 0.0;

  /** Whether to calculate pressure drop via Ergun equation. */
  private boolean calculatePressureDrop = true;

  /**
   * Index of the mercury component in the thermodynamic system (-1 if not
   * present).
   */
  private int mercuryIndex = -1;

  /** Working copy of the thermodynamic system. */
  private SystemInterface system;

  // ======================================================================
  // Constructors
  // ======================================================================

  /**
   * Constructor for MercuryRemovalBed.
   *
   * @param name the name of the unit operation
   */
  public MercuryRemovalBed(String name) {
    super(name);
  }

  /**
   * Constructor for MercuryRemovalBed with inlet stream.
   *
   * @param name        the name of the unit operation
   * @param inletStream the inlet gas stream
   */
  public MercuryRemovalBed(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  // ======================================================================
  // Steady-state run
  // ======================================================================

  /**
   * {@inheritDoc}
   *
   * <p>
   * Steady-state mode assumes the bed is not yet saturated and calculates mercury
   * removal based on
   * the overall NTU/efficiency for the current bed geometry and kinetics.
   * </p>
   */
  @Override
  public void run(UUID id) {
    system = getInletStream().getThermoSystem().clone();
    findMercuryIndex(system);

    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double bedVolume = bedCrossSection * bedLength;
    double sorbentMass = bedVolume * (1.0 - voidFraction) * sorbentBulkDensity;

    // Superficial velocity
    double gasDensityMol = system.getPhase(0).getDensity("mol/m3");
    double totalMolarFlow = system.getTotalNumberOfMoles();
    double superficialVelocity = totalMolarFlow / (gasDensityMol * bedCrossSection);
    double gasResidenceTime = (voidFraction * bedVolume) / Math.max(superficialVelocity * bedCrossSection, 1e-12);

    // Temperature-corrected rate constant
    double kEff = effectiveRateConstant(system.getTemperature());

    // NTU-based removal efficiency
    double ntu = kEff * gasResidenceTime * degradationFactor;
    double efficiency = (1.0 - bypassFraction) * (1.0 - Math.exp(-ntu));

    // Remove mercury from the gas phase
    if (mercuryIndex >= 0) {
      double inletMoles = system.getPhase(0).getComponent(mercuryIndex).getNumberOfmoles();
      double molesRemoved = efficiency * inletMoles;
      molesRemoved = Math.min(molesRemoved, inletMoles * 0.9999);
      if (molesRemoved > 1e-20) {
        system.addComponent(mercuryIndex, -molesRemoved);
      }
    }

    // Pressure drop
    if (calculatePressureDrop) {
      pressureDrop = calcErgunPressureDrop(system, superficialVelocity);
      double outPressure = system.getPressure("Pa") - pressureDrop;
      if (outPressure > 0) {
        system.setPressure(outPressure, "Pa");
      }
    }

    // Flash and set outlet
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      logger.error("TP-flash failed in MercuryRemovalBed: " + e.getMessage());
    }
    getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);
    setCalculationIdentifier(id);
  }

  // ======================================================================
  // Transient simulation
  // ======================================================================

  /**
   * {@inheritDoc}
   *
   * <p>
   * Advances the bed state by {@code dt} seconds. The bed is discretised into
   * axial cells; each
   * cell tracks its local mercury loading and gas-phase Hg concentration. The
   * irreversible
   * chemisorption kinetics consume mercury from the gas phase and accumulate it
   * on the sorbent until
   * the local capacity is exhausted.
   * </p>
   *
   * @param dt time-step size in seconds
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
    findMercuryIndex(system);
    if (mercuryIndex < 0) {
      // No mercury in feed — pass through
      getOutletStream().setThermoSystem(system);
      getOutletStream().run(id);
      elapsedTimeHours += dt / 3600.0;
      increaseTime(dt);
      setCalculationIdentifier(id);
      return;
    }

    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double cellLength = bedLength / numberOfCells;
    double cellVolume = bedCrossSection * cellLength;
    double cellVoidVolume = cellVolume * voidFraction;
    double cellSorbentMass = cellVolume * (1.0 - voidFraction) * sorbentBulkDensity;

    // Inlet Hg concentration in microgram/Nm3
    double inletHgConc = calcInletHgConcentration(system);

    // Interstitial velocity
    double gasDensityMol = system.getPhase(0).getDensity("mol/m3");
    double totalMolarFlow = system.getTotalNumberOfMoles();
    double superficialVelocity = totalMolarFlow / (gasDensityMol * bedCrossSection);
    double interstitialVelocity = superficialVelocity / voidFraction;

    // CFL-limited sub-stepping
    double maxDt = 0.8 * cellLength / Math.max(interstitialVelocity, 1e-10);
    int subSteps = Math.max(1, (int) Math.ceil(dt / maxDt));
    double subDt = dt / subSteps;

    double kEff = effectiveRateConstant(system.getTemperature());
    double effectiveCapacity = maxMercuryCapacity * degradationFactor;

    for (int step = 0; step < subSteps; step++) {
      // 1) Convective transport (upwind)
      double[] newConc = new double[numberOfCells];
      double upstreamConc = inletHgConc * (1.0 - bypassFraction);
      for (int cell = 0; cell < numberOfCells; cell++) {
        double convFlux = interstitialVelocity * (upstreamConc - cellHgConcentration[cell]) / cellLength;
        newConc[cell] = cellHgConcentration[cell] + subDt * convFlux;
        upstreamConc = cellHgConcentration[cell];
      }

      // 2) Chemisorption reaction in each cell
      for (int cell = 0; cell < numberOfCells; cell++) {
        double theta = cellLoading[cell] / effectiveCapacity; // fractional saturation
        theta = Math.min(theta, 1.0);

        // Irreversible first-order: r = k * C * (1 - theta)
        double reactionRate = kEff * newConc[cell] * (1.0 - theta);

        // Convert reaction rate from concentration to sorbent loading
        // dq/dt in mg/kg/s = reactionRate (ug/Nm3/s) * cellVoidVolume / cellSorbentMass
        // * 1e-3
        double dqdt = reactionRate * cellVoidVolume / cellSorbentMass * 1e-3;

        cellLoading[cell] += subDt * dqdt;
        cellLoading[cell] = Math.min(cellLoading[cell], effectiveCapacity);

        // Corresponding concentration decrease
        newConc[cell] -= subDt * reactionRate;
        newConc[cell] = Math.max(0.0, newConc[cell]);
      }

      cellHgConcentration = newConc;
    }

    // Outlet Hg concentration: last cell + bypassed fraction
    double outletHgConc = cellHgConcentration[numberOfCells - 1] + inletHgConc * bypassFraction;

    // Check breakthrough
    if (inletHgConc > 1e-15) {
      double ratio = outletHgConc / inletHgConc;
      if (ratio > breakthroughThreshold && !breakthroughOccurred) {
        breakthroughOccurred = true;
        breakthroughTimeHours = elapsedTimeHours;
        logger.info("Mercury breakthrough at " + elapsedTimeHours + " hours (C/C0 = " + ratio
            + ")");
      }
    }

    // Update outlet system: set mercury moles to reflect outlet concentration
    setOutletMercuryFromConcentration(system, outletHgConc, inletHgConc);

    // Pressure drop
    if (calculatePressureDrop) {
      pressureDrop = calcErgunPressureDrop(system, superficialVelocity);
      double outPressure = system.getPressure("Pa") - pressureDrop;
      if (outPressure > 0) {
        system.setPressure(outPressure, "Pa");
      }
    }

    // Flash and set outlet
    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      logger.error("TP-flash failed during transient: " + e.getMessage());
    }
    getOutletStream().setThermoSystem(system);
    getOutletStream().run(id);

    elapsedTimeHours += dt / 3600.0;
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  // ======================================================================
  // Transient grid management
  // ======================================================================

  /**
   * Initialise the axial discretisation grid with empty sorbent.
   */
  public void initialiseTransientGrid() {
    cellLoading = new double[numberOfCells];
    cellHgConcentration = new double[numberOfCells];
    transientInitialised = true;
    breakthroughOccurred = false;
    breakthroughTimeHours = -1.0;
    elapsedTimeHours = 0.0;
    logger.info("Mercury removal bed transient grid initialised: " + numberOfCells + " cells");
  }

  /**
   * Pre-load the bed to simulate a partially spent sorbent.
   *
   * @param fractionalSaturation fraction of maximum capacity already consumed (0
   *                             = fresh, 1 =
   *                             spent)
   */
  public void preloadBed(double fractionalSaturation) {
    if (!transientInitialised) {
      initialiseTransientGrid();
    }
    double effectiveCapacity = maxMercuryCapacity * degradationFactor;
    for (int cell = 0; cell < numberOfCells; cell++) {
      cellLoading[cell] = fractionalSaturation * effectiveCapacity;
    }
    logger.info("Bed pre-loaded to " + (fractionalSaturation * 100.0) + "% of capacity");
  }

  /**
   * Reset the bed to a fresh (unloaded) state.
   */
  public void resetBed() {
    transientInitialised = false;
    elapsedTimeHours = 0.0;
    breakthroughOccurred = false;
    breakthroughTimeHours = -1.0;
    cellLoading = null;
    cellHgConcentration = null;
    mercuryIndex = -1;
    logger.info("Mercury removal bed reset to fresh state");
  }

  // ======================================================================
  // Profile and results queries
  // ======================================================================

  /**
   * Get the mercury loading profile along the bed.
   *
   * @return array of loading values (mg Hg / kg sorbent) per axial cell
   */
  public double[] getLoadingProfile() {
    if (cellLoading == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfCells];
    System.arraycopy(cellLoading, 0, profile, 0, numberOfCells);
    return profile;
  }

  /**
   * Get the gas-phase mercury concentration profile along the bed.
   *
   * @return array of concentrations (microgram/Nm3) per axial cell
   */
  public double[] getConcentrationProfile() {
    if (cellHgConcentration == null) {
      return new double[0];
    }
    double[] profile = new double[numberOfCells];
    System.arraycopy(cellHgConcentration, 0, profile, 0, numberOfCells);
    return profile;
  }

  /**
   * Get the average mercury loading across the entire bed.
   *
   * @return average loading in mg Hg / kg sorbent
   */
  public double getAverageLoading() {
    if (cellLoading == null) {
      return 0.0;
    }
    double sum = 0.0;
    for (int cell = 0; cell < numberOfCells; cell++) {
      sum += cellLoading[cell];
    }
    return sum / numberOfCells;
  }

  /**
   * Get the bed utilisation factor (average loading / effective capacity).
   *
   * @return utilisation factor (0 to 1)
   */
  public double getBedUtilisation() {
    double effectiveCapacity = maxMercuryCapacity * degradationFactor;
    if (effectiveCapacity <= 0) {
      return 0.0;
    }
    return getAverageLoading() / effectiveCapacity;
  }

  /**
   * Estimate the length of the mass transfer zone (MTZ).
   *
   * <p>
   * The MTZ is defined as the axial distance over which the normalised gas-phase
   * Hg concentration
   * transitions from 0.05 to 0.95 of the inlet concentration.
   * </p>
   *
   * @return MTZ length in metres, 0 if not applicable
   */
  public double getMassTransferZoneLength() {
    if (cellHgConcentration == null || numberOfCells < 2) {
      return 0.0;
    }
    double cellLen = bedLength / numberOfCells;
    double inletConc = cellHgConcentration[0];
    if (inletConc < 1e-15) {
      // Use a representative inlet concentration
      SystemInterface refSys = getInletStream().getThermoSystem();
      findMercuryIndex(refSys);
      inletConc = calcInletHgConcentration(refSys);
    }
    if (inletConc < 1e-15) {
      return 0.0;
    }

    int cellLow = -1;
    int cellHigh = -1;
    for (int cell = 0; cell < numberOfCells; cell++) {
      double cNorm = cellHgConcentration[cell] / inletConc;
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
    return (cellHigh - cellLow) * cellLen;
  }

  /**
   * Estimate bed lifetime (hours) based on current inlet conditions and bed
   * capacity.
   *
   * @return estimated lifetime in hours, or -1 if mercury is not present
   */
  public double estimateBedLifetime() {
    if (mercuryIndex < 0 && getInletStream() != null) {
      findMercuryIndex(getInletStream().getThermoSystem());
    }
    if (mercuryIndex < 0) {
      return -1.0;
    }

    SystemInterface refSys = getInletStream().getThermoSystem();
    double xHgCheck = refSys.getPhase(0).getComponent(mercuryIndex).getx();
    if (xHgCheck <= 0) {
      return -1.0;
    }

    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    double bedVolume = bedCrossSection * bedLength;
    double sorbentMass = bedVolume * (1.0 - voidFraction) * sorbentBulkDensity;
    double effectiveCapacity = maxMercuryCapacity * degradationFactor;

    // Total Hg the bed can hold (mg), scaled by practical replacement utilisation
    double totalCapacity = effectiveCapacity * sorbentMass * replacementUtilisation;

    // Hg feed rate computed directly from molar flow to avoid mixing
    // concentration bases (process vs NTP). This is the most robust approach:
    //   rate (mg/s) = x_Hg * molarFlow (mol/s) * MW_Hg (g/mol) * 1e3 (mg/g)
    double xHg = refSys.getPhase(0).getComponent(mercuryIndex).getx();
    double totalMolarFlow = refSys.getTotalNumberOfMoles();
    double hgFeedRate = xHg * totalMolarFlow * 200.59 * 1e3; // mg/s

    if (hgFeedRate <= 0) {
      return -1.0;
    }
    return totalCapacity / hgFeedRate / 3600.0; // hours
  }

  /**
   * Get the total mass of sorbent in the bed.
   *
   * @return sorbent mass in kg
   */
  public double getSorbentMass() {
    double bedCrossSection = Math.PI / 4.0 * bedDiameter * bedDiameter;
    return bedCrossSection * bedLength * (1.0 - voidFraction) * sorbentBulkDensity;
  }

  /**
   * Get the bed volume.
   *
   * @return bed volume in m3
   */
  public double getBedVolume() {
    return Math.PI / 4.0 * bedDiameter * bedDiameter * bedLength;
  }

  /**
   * Get the outlet mercury removal efficiency based on the most recent
   * calculation.
   *
   * @return removal efficiency (0 to 1), or -1 if not yet calculated
   */
  public double getRemovalEfficiency() {
    if (system == null || mercuryIndex < 0) {
      return -1.0;
    }
    SystemInterface inSys = getInletStream().getThermoSystem();
    double inletMoles = inSys.getPhase(0).getComponent(mercuryIndex).getNumberOfmoles();
    double outletMoles = system.getPhase(0).getComponent(mercuryIndex).getNumberOfmoles();
    if (inletMoles <= 0) {
      return -1.0;
    }
    return 1.0 - outletMoles / inletMoles;
  }

  // ======================================================================
  // Mechanical design
  // ======================================================================

  /** {@inheritDoc} */
  @Override
  public MercuryRemovalMechanicalDesign getMechanicalDesign() {
    return new MercuryRemovalMechanicalDesign(this);
  }

  // ======================================================================
  // Internal helpers
  // ======================================================================

  /**
   * Find the index of elemental mercury in the thermodynamic system.
   *
   * @param sys the thermodynamic system
   */
  private void findMercuryIndex(SystemInterface sys) {
    mercuryIndex = -1;
    for (int i = 0; i < sys.getPhase(0).getNumberOfComponents(); i++) {
      String name = sys.getPhase(0).getComponent(i).getComponentName();
      if ("mercury".equalsIgnoreCase(name) || "Hg".equalsIgnoreCase(name)) {
        mercuryIndex = i;
        return;
      }
    }
  }

  /**
   * Calculate the temperature-corrected effective rate constant using an
   * Arrhenius relation.
   *
   * @param temperatureK temperature in Kelvin
   * @return effective rate constant (1/s)
   */
  private double effectiveRateConstant(double temperatureK) {
    double r = 8.314;
    return reactionRateConstant
        * Math.exp(-activationEnergy / r * (1.0 / temperatureK - 1.0 / referenceTemperature));
  }

  /**
   * Calculate the inlet mercury concentration in microgram/Nm3.
   *
   * @param sys the thermodynamic system with mercury present
   * @return inlet Hg concentration in microgram/Nm3
   */
  private double calcInletHgConcentration(SystemInterface sys) {
    if (mercuryIndex < 0) {
      return 0.0;
    }
    double xHg = sys.getPhase(0).getComponent(mercuryIndex).getx();
    double gasDensityMolPerM3 = sys.getPhase(0).getDensity("mol/m3");
    // Hg moles per m3 at process conditions
    double cHgMolPerM3 = xHg * gasDensityMolPerM3;
    // Convert to microgram/Nm3: mol/m3 * MW_Hg (200.59 g/mol) * 1e6 ug/g * (process
    // density /
    // NTP density)
    // Simplified: just use mol-fraction * total molar density * MW * 1e6
    double cHgUgPerM3 = cHgMolPerM3 * 200.59 * 1e6;
    return cHgUgPerM3;
  }

  /**
   * Set the outlet mercury content by scaling the inlet mole fraction.
   *
   * @param sys        the working thermodynamic system to modify
   * @param outletConc outlet Hg concentration (microgram/Nm3)
   * @param inletConc  inlet Hg concentration (microgram/Nm3)
   */
  private void setOutletMercuryFromConcentration(SystemInterface sys, double outletConc,
      double inletConc) {
    if (mercuryIndex < 0 || inletConc <= 0) {
      return;
    }
    double removalFraction = 1.0 - Math.min(outletConc / inletConc, 1.0);
    double inletMoles = sys.getPhase(0).getComponent(mercuryIndex).getNumberOfmoles();
    double molesRemoved = removalFraction * inletMoles;
    molesRemoved = Math.min(molesRemoved, inletMoles * 0.9999);
    if (molesRemoved > 1e-20) {
      sys.addComponent(mercuryIndex, -molesRemoved);
    }
  }

  /**
   * Calculate pressure drop across the packed bed using the Ergun equation.
   *
   * @param sys the thermodynamic system for fluid properties
   * @param us  superficial velocity (m/s)
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

  // ======================================================================
  // JSON reporting
  // ======================================================================

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("name", getName());
    json.addProperty("equipmentType", "MercuryRemovalBed");

    // Geometry
    JsonObject geometry = new JsonObject();
    geometry.addProperty("bedDiameter_m", bedDiameter);
    geometry.addProperty("bedLength_m", bedLength);
    geometry.addProperty("bedVolume_m3", getBedVolume());
    geometry.addProperty("voidFraction", voidFraction);
    geometry.addProperty("particleDiameter_m", particleDiameter);
    json.add("geometry", geometry);

    // Sorbent
    JsonObject sorbent = new JsonObject();
    sorbent.addProperty("type", sorbentType);
    sorbent.addProperty("bulkDensity_kg_m3", sorbentBulkDensity);
    sorbent.addProperty("totalMass_kg", getSorbentMass());
    sorbent.addProperty("maxMercuryCapacity_mg_per_kg", maxMercuryCapacity);
    json.add("sorbent", sorbent);

    // Kinetics
    JsonObject kinetics = new JsonObject();
    kinetics.addProperty("reactionRateConstant_1_per_s", reactionRateConstant);
    kinetics.addProperty("activationEnergy_J_per_mol", activationEnergy);
    kinetics.addProperty("referenceTemperature_K", referenceTemperature);
    json.add("kinetics", kinetics);

    // Degradation
    JsonObject degradation = new JsonObject();
    degradation.addProperty("degradationFactor", degradationFactor);
    degradation.addProperty("bypassFraction", bypassFraction);
    degradation.addProperty("replacementUtilisation", replacementUtilisation);
    json.add("degradation", degradation);

    // Operating
    JsonObject operating = new JsonObject();
    operating.addProperty("elapsedTime_hours", elapsedTimeHours);
    operating.addProperty("pressureDrop_Pa", pressureDrop);
    operating.addProperty("averageLoading_mg_per_kg", getAverageLoading());
    operating.addProperty("bedUtilisation", getBedUtilisation());
    operating.addProperty("breakthroughOccurred", breakthroughOccurred);
    if (breakthroughTimeHours >= 0) {
      operating.addProperty("breakthroughTime_hours", breakthroughTimeHours);
    }
    double lifetime = estimateBedLifetime();
    if (lifetime > 0) {
      operating.addProperty("estimatedLifetime_hours", lifetime);
    }
    json.add("operating", operating);

    // Loading profile
    if (cellLoading != null) {
      JsonArray loadingArr = new JsonArray();
      for (int i = 0; i < numberOfCells; i++) {
        loadingArr.add(cellLoading[i]);
      }
      json.add("loadingProfile_mg_per_kg", loadingArr);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  // ======================================================================
  // Validation
  // ======================================================================

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
    if (maxMercuryCapacity <= 0) {
      result.addError("sorbent", "Mercury capacity must be positive",
          "Set max mercury capacity: setMaxMercuryCapacity(value)");
    }
    if (degradationFactor < 0 || degradationFactor > 1) {
      result.addError("degradation", "Degradation factor must be between 0 and 1",
          "Set degradation factor: setDegradationFactor(value)");
    }
    if (bypassFraction < 0 || bypassFraction >= 1) {
      result.addError("degradation", "Bypass fraction must be between 0 and <1",
          "Set bypass fraction: setBypassFraction(value)");
    }

    return result;
  }

  // ======================================================================
  // Getters and setters
  // ======================================================================

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
   * Get the sorbent type name.
   *
   * @return sorbent type
   */
  public String getSorbentType() {
    return sorbentType;
  }

  /**
   * Set the sorbent type name (e.g. "PuraSpec", "MRU-CuS").
   *
   * @param sorbentType sorbent type
   */
  public void setSorbentType(String sorbentType) {
    this.sorbentType = sorbentType;
  }

  /**
   * Get the sorbent bulk density.
   *
   * @return bulk density in kg/m3
   */
  public double getSorbentBulkDensity() {
    return sorbentBulkDensity;
  }

  /**
   * Set the sorbent bulk density.
   *
   * @param density bulk density in kg/m3
   */
  public void setSorbentBulkDensity(double density) {
    this.sorbentBulkDensity = density;
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
   * Set the sorbent particle diameter.
   *
   * @param diameter particle diameter in metres
   */
  public void setParticleDiameter(double diameter) {
    this.particleDiameter = diameter;
  }

  /**
   * Get the maximum mercury loading capacity.
   *
   * @return maximum capacity in mg Hg / kg sorbent
   */
  public double getMaxMercuryCapacity() {
    return maxMercuryCapacity;
  }

  /**
   * Set the maximum mercury loading capacity of the sorbent.
   *
   * @param capacity maximum capacity in mg Hg / kg sorbent (typical PuraSpec: 100
   *                 000)
   */
  public void setMaxMercuryCapacity(double capacity) {
    this.maxMercuryCapacity = capacity;
  }

  /**
   * Get the chemisorption reaction rate constant.
   *
   * @return rate constant in 1/s
   */
  public double getReactionRateConstant() {
    return reactionRateConstant;
  }

  /**
   * Set the chemisorption reaction rate constant.
   *
   * @param k rate constant in 1/s
   */
  public void setReactionRateConstant(double k) {
    this.reactionRateConstant = k;
  }

  /**
   * Get the practical replacement utilisation factor.
   *
   * @return replacement utilisation (0 to 1)
   */
  public double getReplacementUtilisation() {
    return replacementUtilisation;
  }

  /**
   * Set the practical replacement utilisation factor. This is the fraction of
   * theoretical sorbent capacity that can be used before the bed must be replaced.
   * Typical 0.4–0.6 for single beds, 0.7–0.9 for lead–lag.
   *
   * @param utilisation replacement utilisation factor (0 to 1)
   */
  public void setReplacementUtilisation(double utilisation) {
    this.replacementUtilisation = Math.max(0.01, Math.min(1.0, utilisation));
  }

  /**
   * Get the activation energy.
   *
   * @return activation energy in J/mol
   */
  public double getActivationEnergy() {
    return activationEnergy;
  }

  /**
   * Set the activation energy for the chemisorption reaction.
   *
   * @param energy activation energy in J/mol
   */
  public void setActivationEnergy(double energy) {
    this.activationEnergy = energy;
  }

  /**
   * Get the degradation factor.
   *
   * @return degradation factor (0 to 1)
   */
  public double getDegradationFactor() {
    return degradationFactor;
  }

  /**
   * Set the degradation factor for column internals or sorbent fouling.
   *
   * <p>
   * A value of 1.0 means brand-new / undegraded bed. Lower values reduce both the
   * effective
   * capacity and the reaction rate to simulate fouling, liquid carry-over, or
   * damaged internals.
   * </p>
   *
   * @param factor degradation factor (0 to 1)
   */
  public void setDegradationFactor(double factor) {
    this.degradationFactor = Math.max(0.0, Math.min(1.0, factor));
  }

  /**
   * Get the bypass fraction.
   *
   * @return bypass fraction (0 to 1)
   */
  public double getBypassFraction() {
    return bypassFraction;
  }

  /**
   * Set the bypass fraction to model channelling due to degraded internals.
   *
   * @param fraction bypass fraction (0 to &lt;1)
   */
  public void setBypassFraction(double fraction) {
    this.bypassFraction = Math.max(0.0, Math.min(0.99, fraction));
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
   * Set the number of axial cells for spatial discretisation.
   *
   * @param cells number of cells (at least 2)
   */
  public void setNumberOfCells(int cells) {
    this.numberOfCells = Math.max(2, cells);
    this.transientInitialised = false;
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
    return pressureDrop;
  }

  /**
   * Set whether to calculate pressure drop from the Ergun equation.
   *
   * @param calculate true to calculate, false to skip
   */
  public void setCalculatePressureDrop(boolean calculate) {
    this.calculatePressureDrop = calculate;
  }

  /**
   * Get the elapsed on-stream time in hours.
   *
   * @return elapsed time in hours
   */
  public double getElapsedTimeHours() {
    return elapsedTimeHours;
  }

  /**
   * Check if mercury breakthrough has occurred.
   *
   * @return true if breakthrough detected
   */
  public boolean isBreakthroughOccurred() {
    return breakthroughOccurred;
  }

  /**
   * Get the breakthrough time.
   *
   * @return breakthrough time in hours, or -1 if not yet occurred
   */
  public double getBreakthroughTimeHours() {
    return breakthroughTimeHours;
  }

  /**
   * Set the breakthrough detection threshold.
   *
   * @param threshold outlet/inlet Hg concentration ratio (0 to 1)
   */
  public void setBreakthroughThreshold(double threshold) {
    this.breakthroughThreshold = threshold;
  }

  /**
   * Set the reference temperature for the Arrhenius rate constant.
   *
   * @param temperature reference temperature in Kelvin
   */
  public void setReferenceTemperature(double temperature) {
    this.referenceTemperature = temperature;
  }

  /**
   * Get the reference temperature for the Arrhenius rate constant.
   *
   * @return reference temperature in Kelvin
   */
  public double getReferenceTemperature() {
    return referenceTemperature;
  }
}
