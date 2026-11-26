package neqsim.process.equipment.pipeline;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.validation.ValidationResult;

/**
 * Compositional, conservative line-pack model for long gas and gas-condensate pipelines.
 *
 * <p>
 * Intended for screening studies of shutdown, settle-out, depressurisation, hold, packing and restart of long rich-gas
 * lines, where retrograde condensation, revaporisation and the composition of the delivered gas matter more than
 * flow-regime detail. Each of the {@code numberOfSections} finite volumes carries its component masses and its total
 * energy (fluid internal energy, potential energy and an optional lumped steel-wall heat capacity). Face mass fluxes
 * follow a staggered one-dimensional momentum equation with pressure gradient, gravity and Haaland wall friction
 * (semi-implicit). The pressure, temperature and phase split of every section are recovered from its volume, energy and
 * composition with a Newton iteration on NeqSim TP flashes (a volume/internal-energy flash), so condensation and
 * revaporisation follow the equation of state at every time step.
 * </p>
 *
 * <p>
 * Liquid transport is bounded by two documented limits rather than resolved: {@link #setLiquidSlipFactor(double)} 0
 * lets only the gas phase advect (condensate stays where it forms) and 1 advects the mixture without slip (worst case
 * for liquid carry-over). {@link #setLiquidGravityDriftVelocity(double)} adds a downhill drift of condensate so liquid
 * can collect in seabed sags. Use {@link TwoFluidPipe} or a qualified multiphase simulator when holdup, slugging or
 * flow regime themselves are the question.
 * </p>
 *
 * <p>
 * Boundaries: the inlet stream supplies composition, mass flow and enthalpy. The enthalpy is taken at the inlet stream
 * pressure and temperature, so throttling into a lower pipeline pressure is isenthalpic (Joule-Thomson cooling at the
 * inlet choke is captured). The outlet mass flow is prescribed with {@link #setOutletMassFlowRate(double)};
 * {@link DewPointGuardedOutletController} provides a delivery policy that only opens when the line is liquid-free and
 * the outlet is above the local dew point. All pressures are in bara and temperatures in kelvin unless a method says
 * otherwise.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public class CompositionalLinePackPipe extends Pipeline {
  private static final long serialVersionUID = 1000;
  /** Logger. */
  private static final Logger logger = LogManager.getLogger(CompositionalLinePackPipe.class);
  /** Gravitational acceleration in m/s2. */
  private static final double GRAVITY = 9.81;
  /** Reference temperature of the wall energy in K. */
  private static final double WALL_REFERENCE_TEMPERATURE = 273.15;

  // ------------------------------------------------------------------ configuration
  /** Number of finite volumes. */
  private int numberOfSections = 50;
  /** Section-centre elevations in m (null means flat). */
  private double[] sectionElevations = null;
  /** Whether the steel wall heat capacity is included. */
  private boolean wallHeatCapacityEnabled = true;
  /** Steel density in kg/m3. */
  private double steelDensity = 7850.0;
  /** Steel specific heat in J/(kg K). */
  private double steelHeatCapacity = 500.0;
  /** Liquid slip factor (0 gas-only transport, 1 no-slip mixture). */
  private double liquidSlipFactor = 0.0;
  /** Gravity drift velocity of condensate at a slope of 1e-3, m/s (0 disables drift). */
  private double liquidGravityDriftVelocity = 0.0;
  /** Acoustic CFL number. */
  private double cflNumber = 0.8;
  /** Maximum internal time step in s. */
  private double maxTimeStep = 120.0;
  /** Flash refresh tolerance on pressure far from the phase boundary, bar. */
  private double refreshPressureTolerance = 0.3;
  /** Flash refresh tolerance on temperature far from the phase boundary, K. */
  private double refreshTemperatureTolerance = 0.15;
  /** Flash refresh tolerance on pressure inside or near the two-phase region, bar. */
  private double refreshPressureToleranceNear = 0.15;
  /** Flash refresh tolerance on temperature inside or near the two-phase region, K. */
  private double refreshTemperatureToleranceNear = 0.08;
  /** Flash refresh tolerance on the summed absolute mole-fraction change. */
  private double refreshCompositionTolerance = 2.0e-5;
  /** Prescribed outlet mass flow in kg/s (positive out of the pipe). */
  private double outletMassFlowRate = 0.0;

  // ------------------------------------------------------------------ state
  /** Working fluid used for all flashes. */
  private SystemInterface work;
  /** Operations bound to the working fluid. */
  private transient ThermodynamicOperations workOps;
  /** Number of components. */
  private int nc;
  /** Component molar masses in kg/mol. */
  private double[] molarMass;
  /** Flags for C3+ components (propane and heavier hydrocarbons). */
  private boolean[] c3plus;
  /** Section length in m. */
  private double dx;
  /** Flow area in m2. */
  private double area;
  /** Section volume in m3. */
  private double sectionVolume;
  /** Wall heat capacity per section in J/K. */
  private double wallHeatCapacity;
  /** Whether the state has been initialised. */
  private boolean initialized = false;
  /** Simulated time in s. */
  private double time = 0.0;
  /** Component masses per section, kg. */
  private double[][] mass;
  /** Total energy per section, J. */
  private double[] energy;
  /** Face mass flux, kg/(m2 s); numberOfSections + 1 faces. */
  private double[] massFlux;
  /** Section pressure, bara. */
  private double[] pressure;
  /** Section temperature, K. */
  private double[] temperature;
  /** Pressure at last full flash, bara. */
  private double[] flashPressure;
  /** Temperature at last full flash, K. */
  private double[] flashTemperature;
  /** Molar volume at last full flash, m3/mol. */
  private double[] flashMolarVolume;
  /** Molar internal energy at last full flash, J/mol. */
  private double[] flashMolarEnergy;
  /** Derivative of molar volume with pressure. */
  private double[] dvdP;
  /** Derivative of molar volume with temperature. */
  private double[] dvdT;
  /** Derivative of molar internal energy with pressure. */
  private double[] dudP;
  /** Derivative of molar internal energy with temperature. */
  private double[] dudT;
  /** Derivative of specific enthalpy with pressure. */
  private double[] dhdP;
  /** Derivative of specific enthalpy with temperature. */
  private double[] dhdT;
  /** Composition at last full flash. */
  private double[][] flashComposition;
  /** Number of phases. */
  private int[] phaseCount;
  /** Refreshes since the last Jacobian evaluation. */
  private int[] jacobianAge;
  /** Time of the last transport-property evaluation, s. */
  private double[] propertyTime;
  /** Liquid mole fraction. */
  private double[] liquidMoleFraction;
  /** Liquid mass per section, kg. */
  private double[] liquidMass;
  /** Liquid volume per section, m3. */
  private double[] liquidVolume;
  /** Mixture density, kg/m3. */
  private double[] mixtureDensity;
  /** Liquid density, kg/m3. */
  private double[] liquidDensity;
  /** Gas-phase sound speed, m/s. */
  private double[] soundSpeed;
  /** Gas-phase viscosity, Pa s. */
  private double[] viscosity;
  /** Specific enthalpy of the transported stream at last flash, J/kg. */
  private double[] transportedEnthalpy;
  /** Specific enthalpy of the liquid phase, J/kg. */
  private double[] liquidEnthalpy;
  /** Liquid drained since the last flash, kg. */
  private double[] drained;
  /** Transported mass fractions. */
  private double[][] transportedMassFraction;
  /** Liquid mass fractions. */
  private double[][] liquidMassFraction;
  /** Gas mole fractions. */
  private double[][] gasMoleFraction;
  /** Liquid mole fractions. */
  private double[][] liquidMoleFractions;
  /** Inlet mass fractions (from the inlet stream). */
  private double[] inletMassFraction;
  /** Inlet specific enthalpy, J/kg. */
  private double inletEnthalpy;
  /** Inlet mass flow used in the last step, kg/s. */
  private double inletMassFlowRate;

  // ------------------------------------------------------------------ bookkeeping
  /** Initial total mass, kg. */
  private double initialMass;
  /** Initial total energy, J. */
  private double initialEnergy;
  /** Initial component masses, kg. */
  private double[] initialComponentMass;
  /** Cumulative inflow, kg. */
  private double cumulativeIn;
  /** Cumulative outflow, kg. */
  private double cumulativeOut;
  /** Cumulative heat added from the surroundings, J. */
  private double cumulativeHeat;
  /** Cumulative energy in, J. */
  private double cumulativeEnergyIn;
  /** Cumulative energy out, J. */
  private double cumulativeEnergyOut;
  /** Cumulative liquid delivered (non-zero only with slip), kg. */
  private double cumulativeLiquidOut;
  /** Cumulative component inflow, kg. */
  private double[] cumulativeComponentIn;
  /** Cumulative component outflow, kg. */
  private double[] cumulativeComponentOut;
  /** Number of TP flashes performed. */
  private long flashCount;
  /** Remaining steps with a reduced time step after a phase change. */
  private int reducedStepCount;
  /** Last internal time step, s. */
  private double lastTimeStep;

  /**
   * Creates the pipe.
   *
   * @param name equipment name
   * @param inletStream inlet stream (composition, flow and inlet enthalpy)
   */
  public CompositionalLinePackPipe(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  // ================================================================== configuration API

  /**
   * Sets the number of finite-volume sections (default 50).
   *
   * @param sections number of sections, at least 1
   */
  public void setNumberOfSections(int sections) {
    if (sections < 1) {
      throw new IllegalArgumentException("numberOfSections must be at least 1");
    }
    this.numberOfSections = sections;
    this.initialized = false;
  }

  /**
   * Returns the number of sections.
   *
   * @return number of sections
   */
  public int getNumberOfSections() {
    return numberOfSections;
  }

  /**
   * Sets the elevation of every section centre in metres (for a subsea line, negative below sea level).
   *
   * @param elevations section-centre elevations in m; length must equal the number of sections
   */
  public void setSectionElevations(double[] elevations) {
    this.sectionElevations = elevations == null ? null : elevations.clone();
    this.initialized = false;
  }

  /**
   * Builds section-centre elevations by averaging a chainage/elevation profile over each section.
   *
   * @param chainageM chainage of the profile points in m (increasing, from the inlet)
   * @param elevationM elevation of the profile points in m
   */
  public void setElevationProfileFromPoints(double[] chainageM, double[] elevationM) {
    if (chainageM == null || elevationM == null || chainageM.length != elevationM.length || chainageM.length < 2) {
      throw new IllegalArgumentException("chainage and elevation arrays must have equal length of at least 2");
    }
    double[] sum = new double[numberOfSections];
    int[] count = new int[numberOfSections];
    double scale = getLength() / chainageM[chainageM.length - 1];
    for (int k = 0; k < chainageM.length; k++) {
      if (Double.isNaN(elevationM[k])) {
        continue;
      }
      int i = (int) Math.min(numberOfSections - 1, Math.floor(chainageM[k] * scale / getLength() * numberOfSections));
      sum[i] += elevationM[k];
      count[i]++;
    }
    double[] z = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      if (count[i] > 0) {
        z[i] = sum[i] / count[i];
      } else {
        z[i] = i > 0 ? z[i - 1] : 0.0;
      }
    }
    setSectionElevations(z);
  }

  /**
   * Includes or excludes the lumped steel-wall heat capacity (wall at fluid temperature).
   *
   * @param enabled true to include the wall heat capacity (default)
   */
  public void setWallHeatCapacityEnabled(boolean enabled) {
    this.wallHeatCapacityEnabled = enabled;
    this.initialized = false;
  }

  /**
   * Sets the liquid slip factor of the transported stream.
   *
   * @param slip 0 transports gas only (condensate immobile), 1 transports the mixture without slip
   */
  public void setLiquidSlipFactor(double slip) {
    if (slip < 0.0 || slip > 1.0) {
      throw new IllegalArgumentException("liquid slip factor must be between 0 and 1");
    }
    this.liquidSlipFactor = slip;
  }

  /**
   * Returns the liquid slip factor.
   *
   * @return slip factor between 0 and 1
   */
  public double getLiquidSlipFactor() {
    return liquidSlipFactor;
  }

  /**
   * Sets the downhill gravity drift velocity of condensate at a slope of 0.1 %; the velocity scales with the square
   * root of the local slope and is capped at 1 m/s. Zero disables drift.
   *
   * @param velocity drift velocity in m/s
   */
  public void setLiquidGravityDriftVelocity(double velocity) {
    this.liquidGravityDriftVelocity = Math.max(0.0, velocity);
  }

  /**
   * Sets the acoustic CFL number of the explicit scheme (default 0.8, must be at most 1).
   *
   * @param cfl CFL number
   */
  public void setCflNumber(double cfl) {
    if (cfl <= 0.0 || cfl > 1.0) {
      throw new IllegalArgumentException("CFL number must be in (0, 1]");
    }
    this.cflNumber = cfl;
  }

  /**
   * Sets the maximum internal time step (default 120 s).
   *
   * @param seconds maximum time step in s
   */
  public void setMaxTimeStep(double seconds) {
    this.maxTimeStep = seconds;
  }

  /**
   * Sets the flash refresh tolerances used far from and near the phase boundary.
   *
   * @param pressureBar pressure tolerance far from the boundary, bar
   * @param temperatureK temperature tolerance far from the boundary, K
   * @param pressureNearBar pressure tolerance near or inside the two-phase region, bar
   * @param temperatureNearK temperature tolerance near or inside the two-phase region, K
   */
  public void setFlashRefreshTolerances(double pressureBar, double temperatureK, double pressureNearBar,
      double temperatureNearK) {
    this.refreshPressureTolerance = pressureBar;
    this.refreshTemperatureTolerance = temperatureK;
    this.refreshPressureToleranceNear = pressureNearBar;
    this.refreshTemperatureToleranceNear = temperatureNearK;
  }

  /**
   * Prescribes the outlet mass flow rate.
   *
   * @param kgPerSecond outlet mass flow in kg/s (0 closes the outlet)
   */
  public void setOutletMassFlowRate(double kgPerSecond) {
    this.outletMassFlowRate = Math.max(0.0, kgPerSecond);
  }

  /**
   * Returns the prescribed outlet mass flow rate.
   *
   * @return outlet mass flow in kg/s
   */
  public double getOutletMassFlowRate() {
    return outletMassFlowRate;
  }

  // ================================================================== initialisation

  /**
   * Allocates arrays and geometry from the current configuration and inlet stream.
   */
  private void allocate() {
    if (inStream == null || inStream.getThermoSystem() == null) {
      throw new IllegalStateException("CompositionalLinePackPipe '" + getName() + "' needs an inlet stream");
    }
    if (sectionElevations != null && sectionElevations.length != numberOfSections) {
      throw new IllegalStateException(
          "section elevations have " + sectionElevations.length + " entries; expected " + numberOfSections);
    }
    work = inStream.getThermoSystem().clone();
    work.setMultiPhaseCheck(false);
    // the working fluid is used on a per-mole basis; give it moles even if the inlet stream has zero flow
    work.setTotalFlowRate(1.0, "mole/sec");
    work.init(0);
    workOps = new ThermodynamicOperations(work);
    nc = work.getNumberOfComponents();
    molarMass = new double[nc];
    c3plus = new boolean[nc];
    double[] z = new double[nc];
    for (int k = 0; k < nc; k++) {
      molarMass[k] = work.getComponent(k).getMolarMass();
      z[k] = work.getComponent(k).getz();
      String n = work.getComponent(k).getComponentName();
      c3plus[k] = !("nitrogen".equals(n) || "CO2".equals(n) || "methane".equals(n) || "ethane".equals(n)
          || "water".equals(n) || "H2S".equals(n) || "oxygen".equals(n) || "helium".equals(n) || "hydrogen".equals(n)
          || "argon".equals(n));
    }
    inletMassFraction = moleToMass(z);
    int n = numberOfSections;
    dx = getLength() / n;
    area = Math.PI * getDiameter() * getDiameter() / 4.0;
    sectionVolume = area * dx;
    wallHeatCapacity = wallHeatCapacityEnabled
        ? steelDensity * steelHeatCapacity * Math.PI * (getDiameter() + getWallThickness()) * getWallThickness() * dx
        : 0.0;
    mass = new double[n][nc];
    energy = new double[n];
    massFlux = new double[n + 1];
    pressure = new double[n];
    temperature = new double[n];
    flashPressure = new double[n];
    flashTemperature = new double[n];
    flashMolarVolume = new double[n];
    flashMolarEnergy = new double[n];
    dvdP = new double[n];
    dvdT = new double[n];
    dudP = new double[n];
    dudT = new double[n];
    dhdP = new double[n];
    dhdT = new double[n];
    flashComposition = new double[n][nc];
    phaseCount = new int[n];
    jacobianAge = new int[n];
    propertyTime = new double[n];
    liquidMoleFraction = new double[n];
    liquidMass = new double[n];
    liquidVolume = new double[n];
    mixtureDensity = new double[n];
    liquidDensity = new double[n];
    soundSpeed = new double[n];
    viscosity = new double[n];
    transportedEnthalpy = new double[n];
    liquidEnthalpy = new double[n];
    drained = new double[n];
    transportedMassFraction = new double[n][nc];
    liquidMassFraction = new double[n][nc];
    gasMoleFraction = new double[n][nc];
    liquidMoleFractions = new double[n][nc];
    cumulativeComponentIn = new double[nc];
    cumulativeComponentOut = new double[nc];
    initialComponentMass = new double[nc];
    time = 0.0;
    flashCount = 0;
    if (sectionElevations == null) {
      sectionElevations = new double[n];
    }
  }

  /**
   * Returns the section-centre elevation, m.
   *
   * @param i section index
   * @return elevation in m
   */
  private double elevation(int i) {
    return sectionElevations[i];
  }

  /**
   * Elevation at a distance from the inlet, piecewise linear between section centres.
   *
   * @param x distance from the inlet in m
   * @return elevation in m
   */
  private double elevationAt(double x) {
    double fi = x / dx - 0.5;
    if (fi <= 0) {
      return elevation(0);
    }
    if (fi >= numberOfSections - 1) {
      return elevation(numberOfSections - 1);
    }
    int i = (int) fi;
    return elevation(i) + (fi - i) * (elevation(i + 1) - elevation(i));
  }

  /**
   * Initialises a uniform, stagnant (packed) line at the inlet-stream composition.
   *
   * @param pressureBara pressure in bara
   * @param temperatureK temperature in kelvin
   */
  public void initializeUniform(double pressureBara, double temperatureK) {
    allocate();
    double[] z = inletMoleFractions();
    for (int i = 0; i < numberOfSections; i++) {
      setSectionFromPressureTemperature(i, z, pressureBara, temperatureK);
    }
    Arrays.fill(massFlux, 0.0);
    finishInitialisation();
  }

  /**
   * Steady isothermal initialisation: the inlet-stream mass flow is marched from the inlet-stream pressure at the
   * inlet-stream temperature, including friction and gravity. Heat transfer starts at the first transient step.
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    allocate();
    double mdot = Math.max(0.0, inStream.getFlowRate("kg/sec"));
    double gs = mdot / area;
    double t = inStream.getTemperature();
    double p = inStream.getPressure();
    double[] z = inletMoleFractions();
    int sub = 8;
    double h = dx / sub;
    double x = 0.0;
    for (int i = 0; i < numberOfSections; i++) {
      for (int s = 0; s < sub; s++) {
        double slope = (elevationAt(x + h) - elevationAt(x)) / h;
        double k1 = steadyGradient(z, p, t, gs, slope);
        double pm = p + 0.5 * h * k1;
        double k2 = steadyGradient(z, Math.max(pm, 1.0), t, gs, slope);
        p = Math.max(p + h * k2, 1.0);
        x += h;
        if (s == sub / 2 - 1) {
          setSectionFromPressureTemperature(i, z, p, t);
        }
      }
    }
    Arrays.fill(massFlux, gs);
    outletMassFlowRate = mdot;
    finishInitialisation();
    setCalculationIdentifier(id);
  }

  /**
   * Steady pressure gradient (friction plus gravity) at a state, bar/m.
   *
   * @param z mole fractions
   * @param p pressure in bara
   * @param t temperature in K
   * @param gs mass flux in kg/(m2 s)
   * @param slope elevation gradient dz/dx
   * @return dp/dx in bar/m
   */
  private double steadyGradient(double[] z, double p, double t, double gs, double slope) {
    flash(z, p, t, true);
    double rho = work.getDensity("kg/m3");
    double mu = work.getViscosity("kg/msec");
    return (-frictionFactor(gs * getDiameter() / mu) * gs * gs / (2 * getDiameter() * rho) - rho * GRAVITY * slope)
        / 1e5;
  }

  /**
   * Fills a section from pressure, temperature and composition.
   *
   * @param i section index
   * @param z mole fractions
   * @param p pressure in bara
   * @param t temperature in K
   */
  private void setSectionFromPressureTemperature(int i, double[] z, double p, double t) {
    double[] r = flash(z, p, t, false);
    double n = sectionVolume / r[0];
    for (int k = 0; k < nc; k++) {
      mass[i][k] = n * z[k] * molarMass[k];
    }
    energy[i] = n * r[1] + wallHeatCapacity * (t - WALL_REFERENCE_TEMPERATURE)
        + sectionMass(i) * GRAVITY * elevation(i);
    jacobian(i, z, p, t, r);
    jacobianAge[i] = 0;
    flash(z, p, t, true);
    store(i, z, p, t, true);
  }

  /** Records the initial inventories and resets cumulative counters. */
  private void finishInitialisation() {
    initialMass = totalMass();
    initialEnergy = totalEnergy();
    for (int k = 0; k < nc; k++) {
      initialComponentMass[k] = componentMass(k);
    }
    cumulativeIn = 0;
    cumulativeOut = 0;
    cumulativeHeat = 0;
    cumulativeEnergyIn = 0;
    cumulativeEnergyOut = 0;
    cumulativeLiquidOut = 0;
    Arrays.fill(cumulativeComponentIn, 0.0);
    Arrays.fill(cumulativeComponentOut, 0.0);
    initialized = true;
    updateOutletStream();
  }

  /**
   * Returns the inlet-stream mole fractions.
   *
   * @return mole fractions
   */
  private double[] inletMoleFractions() {
    SystemInterface s = inStream.getThermoSystem();
    double[] z = new double[nc];
    for (int k = 0; k < nc; k++) {
      z[k] = s.getComponent(k).getz();
    }
    return z;
  }

  // ================================================================== thermodynamics

  /**
   * TP flash of the working fluid on a per-mole basis.
   *
   * @param z mole fractions
   * @param p pressure in bara
   * @param t temperature in K
   * @param full true to also evaluate derivatives and transport properties
   * @return {molar volume m3/mol, molar internal energy J/mol, specific enthalpy J/kg, number of phases}
   */
  private double[] flash(double[] z, double p, double t, boolean full) {
    if (workOps == null) {
      workOps = new ThermodynamicOperations(work);
    }
    flashCount++;
    RuntimeException last = null;
    for (int attempt = 0; attempt < 6; attempt++) {
      try {
        work.setMolarComposition(z);
        work.setTemperature(t * (1 + 1e-5 * attempt));
        work.setPressure(p * (1 + 1e-4 * attempt));
        workOps.TPflash();
        if (full) {
          work.init(3);
          work.initPhysicalProperties();
        } else {
          work.init(2);
        }
        double nt = work.getTotalNumberOfMoles();
        double v = work.getVolume("m3") / nt;
        if (Double.isNaN(v) || v <= 0) {
          throw new IllegalStateException("non-physical molar volume");
        }
        return new double[] {v, work.getInternalEnergy("J") / nt, work.getEnthalpy("J") / (nt * work.getMolarMass()),
            work.getNumberOfPhases()};
      } catch (RuntimeException e) {
        last = e;
      }
    }
    throw new IllegalStateException("TP flash failed at " + p + " bara, " + t + " K", last);
  }

  /**
   * Numerical derivatives of molar volume, molar energy and specific enthalpy with P and T.
   *
   * @param i section index
   * @param z mole fractions
   * @param p pressure in bara
   * @param t temperature in K
   * @param r flash result at (p, t)
   */
  private void jacobian(int i, double[] z, double p, double t, double[] r) {
    double dp = 0.02;
    double dt = 0.02;
    double[] rp = flash(z, p + dp, t, false);
    double[] rt = flash(z, p, t + dt, false);
    dvdP[i] = (rp[0] - r[0]) / dp;
    dudP[i] = (rp[1] - r[1]) / dp;
    dhdP[i] = (rp[2] - r[2]) / dp;
    dvdT[i] = (rt[0] - r[0]) / dt;
    dudT[i] = (rt[1] - r[1]) / dt;
    dhdT[i] = (rt[2] - r[2]) / dt;
  }

  /**
   * Whether a section is inside or close to the two-phase region of its last flash composition.
   *
   * @param i section index
   * @return true if two-phase or within the near-boundary band
   */
  private boolean nearPhaseBoundary(int i) {
    return phaseCount[i] > 1 || liquidMoleFraction[i] > 0;
  }

  /**
   * Recovers pressure and temperature of a section from its conserved variables.
   *
   * @param i section index
   * @param force true to force a full flash
   */
  private void solveSection(int i, boolean force) {
    double n = 0;
    double[] z = new double[nc];
    for (int k = 0; k < nc; k++) {
      z[k] = mass[i][k] / molarMass[k];
      n += z[k];
    }
    for (int k = 0; k < nc; k++) {
      z[k] /= n;
    }
    double vt = sectionVolume / n;
    double internal = energy[i] - sectionMass(i) * GRAVITY * elevation(i);
    double r1 = vt - flashMolarVolume[i];
    double r2 = internal - n * flashMolarEnergy[i]
        - wallHeatCapacity * (flashTemperature[i] - WALL_REFERENCE_TEMPERATURE);
    double a11 = dvdP[i];
    double a12 = dvdT[i];
    double a21 = n * dudP[i];
    double a22 = n * dudT[i] + wallHeatCapacity;
    double det = a11 * a22 - a12 * a21;
    double dP = (r1 * a22 - a12 * r2) / det;
    double dT = (a11 * r2 - a21 * r1) / det;
    double dz = 0;
    for (int k = 0; k < nc; k++) {
      dz += Math.abs(z[k] - flashComposition[i][k]);
    }
    boolean near = nearPhaseBoundary(i);
    double tp = near ? refreshPressureToleranceNear : refreshPressureTolerance;
    double tt = near ? refreshTemperatureToleranceNear : refreshTemperatureTolerance;
    if (!force && Math.abs(dP) < tp && Math.abs(dT) < tt && dz < refreshCompositionTolerance) {
      pressure[i] = flashPressure[i] + dP;
      temperature[i] = flashTemperature[i] + dT;
      return;
    }
    refresh(i, z, n, vt, internal, flashPressure[i] + dP, flashTemperature[i] + dT, force);
  }

  /**
   * Newton iteration on (P, T) so that the flashed volume and internal energy match the section.
   *
   * @param i section index
   * @param z mole fractions
   * @param n total moles
   * @param vt target molar volume
   * @param internal target internal plus wall energy, J
   * @param p0 starting pressure in bara
   * @param t0 starting temperature in K
   * @param force true to force a property update
   */
  private void refresh(int i, double[] z, double n, double vt, double internal, double p0, double t0, boolean force) {
    int oldPhases = phaseCount[i];
    boolean jacobianFresh = false;
    boolean converged = false;
    boolean needJacobian = jacobianAge[i] >= 4 || dvdP[i] == 0;
    double p = p0;
    double t = t0;
    double[] r = null;
    for (int it = 0; it < 12; it++) {
      p = Math.max(p, 1.0);
      t = Math.max(t, 150.0);
      r = flash(z, p, t, false);
      double e1 = vt - r[0];
      double e2 = internal - n * r[1] - wallHeatCapacity * (t - WALL_REFERENCE_TEMPERATURE);
      if (Math.abs(e1) / vt < 1e-6 && Math.abs(e2) < n * 0.05 + wallHeatCapacity * 5e-5) {
        converged = true;
        break;
      }
      if (!jacobianFresh && (needJacobian || it >= 1 || ((int) r[3]) != oldPhases)) {
        jacobian(i, z, p, t, r);
        jacobianFresh = true;
        jacobianAge[i] = 0;
      }
      double a11 = dvdP[i];
      double a12 = dvdT[i];
      double a21 = n * dudP[i];
      double a22 = n * dudT[i] + wallHeatCapacity;
      double det = a11 * a22 - a12 * a21;
      double sP = (e1 * a22 - a12 * e2) / det;
      double sT = (a11 * e2 - a21 * e1) / det;
      double scale = Math.max(Math.abs(sP), Math.abs(sT)) / 5.0;
      if (scale > 1) {
        sP /= scale;
        sT /= scale;
      }
      p += sP;
      t += sT;
    }
    if (!converged) {
      logger.warn("{}: section {} state recovery did not converge at {} bara, {} K", getName(), i, p, t);
      r = flash(z, p, t, false);
    }
    jacobianAge[i]++;
    boolean full = force || time - propertyTime[i] > 1800 || ((int) r[3]) != oldPhases;
    if (full) {
      work.init(3);
      work.initPhysicalProperties();
      propertyTime[i] = time;
    }
    store(i, z, p, t, full);
  }

  /**
   * Stores the flash result currently held by the working fluid for a section.
   *
   * @param i section index
   * @param z mole fractions
   * @param p pressure in bara
   * @param t temperature in K
   * @param full whether transport properties were evaluated
   */
  private void store(int i, double[] z, double p, double t, boolean full) {
    double nt = work.getTotalNumberOfMoles();
    pressure[i] = p;
    temperature[i] = t;
    flashPressure[i] = p;
    flashTemperature[i] = t;
    flashComposition[i] = z.clone();
    drained[i] = 0;
    flashMolarVolume[i] = work.getVolume("m3") / nt;
    flashMolarEnergy[i] = work.getInternalEnergy("J") / nt;
    double massTotal = nt * work.getMolarMass();
    double hMix = work.getEnthalpy("J") / massTotal;
    int np = work.getNumberOfPhases();
    phaseCount[i] = np;
    mixtureDensity[i] = sectionMass(i) / sectionVolume;
    int ig = 0;
    int il = -1;
    if (np > 1) {
      if (work.getPhase(0).getDensity("kg/m3") > work.getPhase(1).getDensity("kg/m3")) {
        ig = 1;
        il = 0;
      } else {
        il = 1;
      }
      double lm = work.getPhase(il).getNumberOfMolesInPhase() * work.getPhase(il).getMolarMass();
      double rl = work.getPhase(il).getDensity("kg/m3");
      if (!(lm >= 0 && lm <= massTotal * 1.000001) || !(rl > 1.0)) {
        il = -1;
        ig = 0;
        phaseCount[i] = 1;
      }
    }
    if (il >= 0) {
      for (int k = 0; k < nc; k++) {
        gasMoleFraction[i][k] = work.getPhase(ig).getComponent(k).getx();
      }
    } else {
      System.arraycopy(z, 0, gasMoleFraction[i], 0, nc);
    }
    if (full) {
      soundSpeed[i] = work.getPhase(ig).getSoundSpeed();
      viscosity[i] = work.getPhase(ig).getViscosity("kg/msec");
    }
    if (il >= 0) {
      double gm = work.getPhase(ig).getNumberOfMolesInPhase() * work.getPhase(ig).getMolarMass();
      double lm = work.getPhase(il).getNumberOfMolesInPhase() * work.getPhase(il).getMolarMass();
      double hG = work.getPhase(ig).getEnthalpy("J") / gm;
      double hL = work.getPhase(il).getEnthalpy("J") / lm;
      double wl = lm / massTotal;
      double wg = 1 - wl;
      liquidMoleFraction[i] = work.getBeta(il);
      liquidMass[i] = wl * sectionMass(i);
      liquidDensity[i] = work.getPhase(il).getDensity("kg/m3");
      liquidVolume[i] = liquidMass[i] / liquidDensity[i];
      for (int k = 0; k < nc; k++) {
        liquidMoleFractions[i][k] = work.getPhase(il).getComponent(k).getx();
      }
      double[] wgk = moleToMass(gasMoleFraction[i]);
      double[] wlk = moleToMass(liquidMoleFractions[i]);
      double den = wg + liquidSlipFactor * wl;
      for (int k = 0; k < nc; k++) {
        transportedMassFraction[i][k] = (wg * wgk[k] + liquidSlipFactor * wl * wlk[k]) / den;
      }
      transportedEnthalpy[i] = (wg * hG + liquidSlipFactor * wl * hL) / den;
      liquidEnthalpy[i] = hL;
      System.arraycopy(wlk, 0, liquidMassFraction[i], 0, nc);
    } else {
      liquidMoleFraction[i] = 0;
      liquidMass[i] = 0;
      liquidVolume[i] = 0;
      liquidDensity[i] = 0;
      liquidEnthalpy[i] = 0;
      Arrays.fill(liquidMoleFractions[i], 0.0);
      System.arraycopy(moleToMass(z), 0, transportedMassFraction[i], 0, nc);
      transportedEnthalpy[i] = hMix;
    }
  }

  /**
   * Transported specific enthalpy of a section, corrected linearly to its current P and T.
   *
   * @param i section index
   * @return specific enthalpy in J/kg
   */
  private double transportedEnthalpyNow(int i) {
    return transportedEnthalpy[i] + dhdP[i] * (pressure[i] - flashPressure[i])
        + dhdT[i] * (temperature[i] - flashTemperature[i]);
  }

  /**
   * Converts mole fractions to mass fractions.
   *
   * @param z mole fractions
   * @return mass fractions
   */
  private double[] moleToMass(double[] z) {
    double s = 0;
    double[] w = new double[nc];
    for (int k = 0; k < nc; k++) {
      w[k] = z[k] * molarMass[k];
      s += w[k];
    }
    for (int k = 0; k < nc; k++) {
      w[k] /= s;
    }
    return w;
  }

  /**
   * Converts mass fractions to mole fractions.
   *
   * @param w mass fractions
   * @return mole fractions
   */
  private double[] massToMole(double[] w) {
    double s = 0;
    double[] z = new double[nc];
    for (int k = 0; k < nc; k++) {
      z[k] = w[k] / molarMass[k];
      s += z[k];
    }
    for (int k = 0; k < nc; k++) {
      z[k] /= s;
    }
    return z;
  }

  /**
   * Haaland/laminar Darcy friction factor.
   *
   * @param reynolds Reynolds number
   * @return Darcy friction factor
   */
  private double frictionFactor(double reynolds) {
    double re = Math.max(reynolds, 1.0);
    if (re < 2300) {
      return 64.0 / re;
    }
    double a = Math.pow(getPipeWallRoughness() / getDiameter() / 3.7, 1.11) + 6.9 / re;
    double x = -1.8 * Math.log10(a);
    return 1.0 / (x * x);
  }

  // ================================================================== transient

  /**
   * Advances the pipe by {@code dt} seconds using internal explicit sub-steps. The inlet mass flow, composition and
   * enthalpy are read from the inlet stream; the outlet mass flow is the value set with
   * {@link #setOutletMassFlowRate(double)}. Initialises with {@link #run(UUID)} if needed.
   *
   * @param dt time interval in s
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!initialized) {
      run(id);
    }
    readInletBoundary();
    double end = time + dt;
    while (time < end - 1e-9) {
      step(Math.min(end - time, stableTimeStep()));
    }
    updateOutletStream();
    setCalculationIdentifier(id);
  }

  /** Reads inlet composition, mass flow and enthalpy from the inlet stream. */
  private void readInletBoundary() {
    inletMassFlowRate = Math.max(0.0, inStream.getFlowRate("kg/sec"));
    double[] z = inletMoleFractions();
    inletMassFraction = moleToMass(z);
    double[] r = flash(z, inStream.getPressure(), inStream.getTemperature(), false);
    inletEnthalpy = r[2];
  }

  /**
   * Stable explicit time step (acoustic CFL, reduced after a phase change).
   *
   * @return time step in s
   */
  private double stableTimeStep() {
    double dt = maxTimeStep;
    for (int i = 0; i < numberOfSections; i++) {
      double v = Math.abs(massFlux[i]) / mixtureDensity[i];
      double c = soundSpeed[i] > 50 ? soundSpeed[i] : 300;
      dt = Math.min(dt, cflNumber * dx / (c + v));
    }
    if (reducedStepCount > 0) {
      dt *= 0.25;
      reducedStepCount--;
    }
    return dt;
  }

  /**
   * One explicit step: momentum on faces, conservative mass/energy update, state recovery.
   *
   * @param dt time step in s
   */
  private void step(double dt) {
    int n = numberOfSections;
    lastTimeStep = dt;
    for (int j = 1; j < n; j++) {
      double rhoF = 0.5 * (mixtureDensity[j - 1] + mixtureDensity[j]);
      double mu = 0.5 * (viscosity[j - 1] + viscosity[j]);
      double re = Math.abs(massFlux[j]) * getDiameter() / Math.max(mu, 1e-6);
      double f = frictionFactor(re);
      double dpdx = (pressure[j - 1] - pressure[j]) * 1e5 / dx
          - rhoF * GRAVITY * (elevation(j) - elevation(j - 1)) / dx;
      massFlux[j] = (massFlux[j] + dt * dpdx) / (1 + dt * f * Math.abs(massFlux[j]) / (2 * getDiameter() * rhoF));
    }
    massFlux[0] = inletMassFlowRate / area;
    massFlux[n] = outletMassFlowRate / area;
    double[][] fk = new double[n + 1][nc];
    double[] fe = new double[n + 1];
    for (int j = 0; j <= n; j++) {
      double mf = massFlux[j] * area;
      if (mf == 0) {
        continue;
      }
      double[] w;
      double h;
      int up;
      if (j == 0) {
        w = inletMassFraction;
        h = inletEnthalpy;
        up = 0;
      } else {
        if (j == n) {
          up = n - 1;
        } else {
          up = mf > 0 ? j - 1 : j;
        }
        w = transportedMassFraction[up];
        h = transportedEnthalpyNow(up);
      }
      for (int k = 0; k < nc; k++) {
        fk[j][k] = mf * w[k];
      }
      fe[j] = mf * (h + GRAVITY * elevation(up));
    }
    if (liquidGravityDriftVelocity > 0) {
      addGravityDrift(dt, fk, fe);
    }
    double ambient = surfaceTemperature;
    for (int i = 0; i < n; i++) {
      double q = getHeatTransferCoefficient() * Math.PI * getDiameter() * dx * (ambient - temperature[i]);
      for (int k = 0; k < nc; k++) {
        mass[i][k] = Math.max(0.0, mass[i][k] + dt * (fk[i][k] - fk[i + 1][k]));
      }
      energy[i] += dt * (fe[i] - fe[i + 1] + q);
      cumulativeHeat += dt * q;
    }
    for (int k = 0; k < nc; k++) {
      cumulativeComponentIn[k] += dt * fk[0][k];
      cumulativeComponentOut[k] += dt * fk[n][k];
    }
    cumulativeIn += dt * massFlux[0] * area;
    cumulativeOut += dt * massFlux[n] * area;
    cumulativeEnergyIn += dt * fe[0];
    cumulativeEnergyOut += dt * fe[n];
    if (massFlux[n] > 0 && phaseCount[n - 1] > 1 && liquidSlipFactor > 0) {
      double wl = liquidMass[n - 1] / sectionMass(n - 1);
      cumulativeLiquidOut += dt * massFlux[n] * area * liquidSlipFactor * wl / (1 - wl + liquidSlipFactor * wl);
    }
    time += dt;
    for (int i = 0; i < n; i++) {
      int old = phaseCount[i];
      solveSection(i, false);
      if (phaseCount[i] != old) {
        reducedStepCount = 20;
      }
      mixtureDensity[i] = sectionMass(i) / sectionVolume;
    }
  }

  /**
   * Adds the downhill gravity drift of condensate to the face fluxes.
   *
   * @param dt time step in s
   * @param fk component mass fluxes (modified)
   * @param fe energy fluxes (modified)
   */
  private void addGravityDrift(double dt, double[][] fk, double[] fe) {
    for (int j = 1; j < numberOfSections; j++) {
      double slope = (elevation(j) - elevation(j - 1)) / dx;
      if (slope == 0) {
        continue;
      }
      int up = slope < 0 ? j - 1 : j;
      double available = liquidMass[up] - drained[up];
      if (phaseCount[up] < 2 || available <= 0) {
        continue;
      }
      double vd = Math.min(1.0, liquidGravityDriftVelocity * Math.sqrt(Math.abs(slope) / 1e-3));
      double ml = Math.min(available / sectionVolume * area * vd, 0.5 * available / dt);
      double sign = up == j - 1 ? 1.0 : -1.0;
      for (int k = 0; k < nc; k++) {
        fk[j][k] += sign * ml * liquidMassFraction[up][k];
      }
      fe[j] += sign * ml * (liquidEnthalpy[up] + GRAVITY * elevation(up));
      drained[up] += ml * dt;
    }
  }

  /** Updates the outlet stream with the transported composition at outlet-section conditions. */
  private void updateOutletStream() {
    if (outStream == null) {
      return;
    }
    int last = numberOfSections - 1;
    SystemInterface s = work.clone();
    s.setMolarComposition(massToMole(transportedMassFraction[last]));
    s.setTemperature(temperature[last]);
    s.setPressure(pressure[last]);
    new ThermodynamicOperations(s).TPflash();
    s.initProperties();
    outStream.setThermoSystem(s);
    outStream.setFlowRate(Math.max(outletMassFlowRate, 1e-10), "kg/sec");
  }

  // ================================================================== results

  /**
   * Mass of a section, kg.
   *
   * @param i section index
   * @return mass in kg
   */
  private double sectionMass(int i) {
    double s = 0;
    for (int k = 0; k < nc; k++) {
      s += mass[i][k];
    }
    return s;
  }

  /**
   * Total mass of one component in the pipe, kg.
   *
   * @param k component index
   * @return mass in kg
   */
  private double componentMass(int k) {
    double s = 0;
    for (int i = 0; i < numberOfSections; i++) {
      s += mass[i][k];
    }
    return s;
  }

  /**
   * Total energy (fluid internal, potential and wall), J.
   *
   * @return energy in J
   */
  private double totalEnergy() {
    double s = 0;
    for (int i = 0; i < numberOfSections; i++) {
      s += energy[i];
    }
    return s;
  }

  /**
   * Returns the total fluid mass in the pipe (line pack).
   *
   * @return mass in kg
   */
  public double totalMass() {
    double s = 0;
    for (int i = 0; i < numberOfSections; i++) {
      s += sectionMass(i);
    }
    return s;
  }

  /**
   * Returns the hydrocarbon liquid inventory.
   *
   * @param unit "kg", "tonnes" or "m3"
   * @return liquid inventory in the requested unit
   */
  public double getLiquidInventory(String unit) {
    double m = 0;
    double v = 0;
    for (int i = 0; i < numberOfSections; i++) {
      m += liquidMass[i];
      v += liquidVolume[i];
    }
    if ("kg".equalsIgnoreCase(unit)) {
      return m;
    }
    if ("tonnes".equalsIgnoreCase(unit) || "t".equalsIgnoreCase(unit)) {
      return m / 1000.0;
    }
    if ("m3".equalsIgnoreCase(unit)) {
      return v;
    }
    throw new IllegalArgumentException("unsupported unit '" + unit + "'; use kg, tonnes or m3");
  }

  /**
   * Returns the number of sections that are two-phase.
   *
   * @return count of two-phase sections
   */
  public int getNumberOfTwoPhaseSections() {
    int s = 0;
    for (int i = 0; i < numberOfSections; i++) {
      if (phaseCount[i] > 1) {
        s++;
      }
    }
    return s;
  }

  /**
   * Whether the last (outlet) section is two-phase.
   *
   * @return true if the outlet section holds liquid
   */
  public boolean isOutletSectionTwoPhase() {
    return phaseCount[numberOfSections - 1] > 1;
  }

  /**
   * Returns the section pressures in bara.
   *
   * @return pressure profile in bara
   */
  @Override
  public double[] getPressureProfile() {
    return pressure == null ? new double[0] : pressure.clone();
  }

  /**
   * Returns the section temperatures in kelvin.
   *
   * @return temperature profile in K
   */
  @Override
  public double[] getTemperatureProfile() {
    return temperature == null ? new double[0] : temperature.clone();
  }

  /**
   * Returns the equilibrium liquid volume fraction of every section.
   *
   * @return liquid volume fraction per section
   */
  @Override
  public double[] getLiquidHoldupProfile() {
    double[] h = new double[numberOfSections];
    for (int i = 0; liquidVolume != null && i < numberOfSections; i++) {
      h[i] = liquidVolume[i] / sectionVolume;
    }
    return h;
  }

  /**
   * Returns the liquid mass of every section in kg.
   *
   * @return liquid mass per section in kg
   */
  public double[] getLiquidMassProfile() {
    return liquidMass == null ? new double[0] : liquidMass.clone();
  }

  /**
   * Returns the section-centre positions from the inlet in m.
   *
   * @return positions in m
   */
  public double[] getPositionProfile() {
    double[] x = new double[numberOfSections];
    for (int i = 0; i < numberOfSections; i++) {
      x[i] = (i + 0.5) * getLength() / numberOfSections;
    }
    return x;
  }

  /**
   * Returns the overall mole fractions of a section.
   *
   * @param section section index
   * @return mole fractions
   */
  public double[] getSectionComposition(int section) {
    double[] z = new double[nc];
    double n = 0;
    for (int k = 0; k < nc; k++) {
      z[k] = mass[section][k] / molarMass[k];
      n += z[k];
    }
    for (int k = 0; k < nc; k++) {
      z[k] /= n;
    }
    return z;
  }

  /**
   * Returns the mole percent of C3+ components in the stream leaving the outlet section.
   *
   * @return C3+ in mol%
   */
  public double getOutletC3PlusMolePercent() {
    double[] z = massToMole(transportedMassFraction[numberOfSections - 1]);
    double s = 0;
    for (int k = 0; k < nc; k++) {
      if (c3plus[k]) {
        s += z[k];
      }
    }
    return 100.0 * s;
  }

  /**
   * Upper (retrograde) dew-point pressure of a section's overall composition at a temperature.
   *
   * @param section section index
   * @param temperatureK temperature in kelvin
   * @param maxPressureBara upper end of the scan in bara
   * @return dew-point pressure in bara or NaN when the mixture is single phase over the range
   */
  public double getSectionUpperDewPointPressure(int section, double temperatureK, double maxPressureBara) {
    SystemInterface s = work.clone();
    s.setMolarComposition(getSectionComposition(section));
    return RetrogradeDewPointScanner.upperDewPointPressure(s, temperatureK, maxPressureBara);
  }

  /**
   * Highest upper dew-point pressure of the last {@code sections} sections, each evaluated at the colder of its own and
   * the outlet temperature. This is the pressure the outlet must stay above for the gas reaching it to remain single
   * phase.
   *
   * @param sections number of sections counted back from the outlet
   * @param maxPressureBara upper end of the scan in bara
   * @return dew-point pressure in bara (0 if every mixture is single phase over the range)
   */
  public double getDownstreamDewPointPressure(int sections, double maxPressureBara) {
    double m = 0;
    int lastIndex = numberOfSections - 1;
    for (int i = Math.max(0, numberOfSections - sections); i < numberOfSections; i++) {
      double p = getSectionUpperDewPointPressure(i, Math.min(temperature[i], temperature[lastIndex]), maxPressureBara);
      if (!Double.isNaN(p)) {
        m = Math.max(m, p);
      }
    }
    return m;
  }

  /**
   * Returns the simulated time since initialisation.
   *
   * @return time in s
   */
  public double getSimulationTime() {
    return time;
  }

  /**
   * Returns the last internal time step.
   *
   * @return time step in s
   */
  public double getLastTimeStep() {
    return lastTimeStep;
  }

  /**
   * Returns the cumulative mass delivered through the outlet.
   *
   * @return mass in kg
   */
  public double getCumulativeOutletMass() {
    return cumulativeOut;
  }

  /**
   * Returns the cumulative mass supplied through the inlet.
   *
   * @return mass in kg
   */
  public double getCumulativeInletMass() {
    return cumulativeIn;
  }

  /**
   * Returns the cumulative hydrocarbon liquid delivered through the outlet (non-zero only with slip &gt; 0).
   *
   * @return liquid mass in kg
   */
  public double getCumulativeOutletLiquidMass() {
    return cumulativeLiquidOut;
  }

  /**
   * Returns the number of TP flashes performed.
   *
   * @return flash count
   */
  public long getFlashCount() {
    return flashCount;
  }

  /**
   * Relative closure errors of the conservation balances: {total mass, worst component, total energy}. Each is the
   * difference between the current inventory and initial inventory plus cumulative boundary fluxes (and heat).
   *
   * @return array of three relative errors
   */
  public double[] getBalanceErrors() {
    double massError = (totalMass() - (initialMass + cumulativeIn - cumulativeOut)) / initialMass;
    double componentError = 0;
    for (int k = 0; k < nc; k++) {
      double ref = initialComponentMass[k] + cumulativeComponentIn[k] - cumulativeComponentOut[k];
      componentError = Math.max(componentError,
          Math.abs(componentMass(k) - ref) / Math.max(Math.max(initialComponentMass[k], ref), 1e-9));
    }
    double energyRef = initialEnergy + cumulativeEnergyIn - cumulativeEnergyOut + cumulativeHeat;
    double energyError = (totalEnergy() - energyRef) / Math.max(Math.abs(initialEnergy), 1e-9);
    return new double[] {massError, componentError, energyError};
  }

  /**
   * Relative residual between each section's volume and energy and a fresh NeqSim flash at the reported pressure and
   * temperature (thermodynamic closure of the linearised predictor).
   *
   * @return maximum relative volume residual
   */
  public double getThermodynamicClosureResidual() {
    double worst = 0;
    for (int i = 0; i < numberOfSections; i++) {
      double[] z = getSectionComposition(i);
      double n = 0;
      for (int k = 0; k < nc; k++) {
        n += mass[i][k] / molarMass[k];
      }
      double[] r = flash(z, pressure[i], temperature[i], false);
      worst = Math.max(worst, Math.abs(sectionVolume - n * r[0]) / sectionVolume);
    }
    return worst;
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = super.validateSetup();
    if (getLength() <= 0) {
      result.addError("geometry", "Pipe length must be positive", "Call setLength(metres)");
    }
    if (getDiameter() <= 0) {
      result.addError("geometry", "Pipe diameter must be positive", "Call setDiameter(metres)");
    }
    if (sectionElevations != null && sectionElevations.length != numberOfSections) {
      result.addError("geometry", "Section elevation array length differs from the number of sections",
          "Call setNumberOfSections before setSectionElevations, or use setElevationProfileFromPoints");
    }
    if (liquidSlipFactor > 0 && liquidGravityDriftVelocity > 0) {
      result.addWarning("model", "Slip and gravity drift are both active",
          "No-slip transport already moves liquid with the gas; drift adds downhill motion on top");
    }
    return result;
  }
}
