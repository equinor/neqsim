package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Thermodynamic coupling for the two-fluid transient pipe model.
 *
 * <p>
 * Provides interface between the two-fluid hydrodynamic solver and NeqSim's thermodynamic
 * calculations. Handles flash calculations to update phase properties and compositions along the
 * pipeline.
 * </p>
 *
 * <h2>Key Functions</h2>
 * <ul>
 * <li>Update phase densities, viscosities, and enthalpies from P-T flash</li>
 * <li>Calculate phase compositions and mass transfer rates</li>
 * <li>Provide sound speeds for wave propagation</li>
 * <li>Support for both rigorous flash and table interpolation</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <p>
 * Flash calculations are computationally expensive. For transient simulations with many time steps
 * and grid cells, consider using {@link FlashTable} for pre-computed property interpolation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ThermodynamicCoupling implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Reference fluid system for flash calculations. */
  private SystemInterface referenceFluid;

  /** Thermodynamic operations object. */
  private transient ThermodynamicOperations thermoOps;

  /** Whether to use table interpolation instead of rigorous flash. */
  private boolean useFlashTable = false;

  /** Pre-computed flash table (optional). */
  private FlashTable flashTable;

  /** Minimum pressure for valid flash (Pa). */
  private double minPressure = 1e5;

  /** Maximum pressure for valid flash (Pa). */
  private double maxPressure = 500e5;

  /** Minimum temperature for valid flash (K). */
  private double minTemperature = 200.0;

  /** Maximum temperature for valid flash (K). */
  private double maxTemperature = 500.0;

  /** Flash tolerance for convergence. */
  private double flashTolerance = 1e-6;

  /** Maximum flash iterations. */
  private int maxFlashIterations = 100;

  /**
   * Result container for thermodynamic property update.
   */
  public static class ThermoProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    // Phase fractions
    /** Gas mole fraction. */
    public double gasVaporFraction;

    /** Liquid mole fraction. */
    public double liquidFraction;

    // Densities
    /** Gas density (kg/m³). */
    public double gasDensity;

    /** Liquid density (kg/m³). */
    public double liquidDensity;

    // Viscosities
    /** Gas dynamic viscosity (Pa·s). */
    public double gasViscosity;

    /** Liquid dynamic viscosity (Pa·s). */
    public double liquidViscosity;

    // Enthalpies
    /** Gas specific enthalpy (J/kg). */
    public double gasEnthalpy;

    /** Liquid specific enthalpy (J/kg). */
    public double liquidEnthalpy;

    // Sound speeds
    /** Gas sound speed (m/s). */
    public double gasSoundSpeed;

    /** Liquid sound speed (m/s). */
    public double liquidSoundSpeed;

    // Surface tension
    /** Gas-liquid surface tension (N/m). */
    public double surfaceTension;

    // Molecular weights
    /** Gas molecular weight (kg/kmol). */
    public double gasMolarMass;

    /** Liquid molecular weight (kg/kmol). */
    public double liquidMolarMass;

    // Compressibility
    /** Gas compressibility factor Z. */
    public double gasCompressibility;

    /** Liquid compressibility factor Z. */
    public double liquidCompressibility;

    // Heat capacities
    /** Gas specific heat at constant pressure Cp (J/(kg·K)). */
    public double gasCp;

    /** Liquid specific heat at constant pressure Cp (J/(kg·K)). */
    public double liquidCp;

    // Thermal conductivity
    /** Gas thermal conductivity (W/(m·K)). */
    public double gasThermalConductivity;

    /** Liquid thermal conductivity (W/(m·K)). */
    public double liquidThermalConductivity;

    /** Flash convergence flag. */
    public boolean converged = true;

    /** Error message if flash failed. */
    public String errorMessage;
  }

  /**
   * Default constructor.
   */
  public ThermodynamicCoupling() {}

  /**
   * Constructor with reference fluid.
   *
   * @param referenceFluid Fluid system to use as template for flash calculations
   */
  public ThermodynamicCoupling(SystemInterface referenceFluid) {
    setReferenceFluid(referenceFluid);
  }

  /**
   * Set the reference fluid for thermodynamic calculations.
   *
   * @param fluid Fluid system (will be cloned internally)
   */
  public void setReferenceFluid(SystemInterface fluid) {
    this.referenceFluid = fluid.clone();
    this.thermoOps = new ThermodynamicOperations(this.referenceFluid);
  }

  /**
   * Get the reference fluid.
   *
   * @return Reference fluid system
   */
  public SystemInterface getReferenceFluid() {
    return referenceFluid;
  }

  /**
   * Perform PT flash and extract all thermodynamic properties.
   *
   * @param pressure Pressure (Pa)
   * @param temperature Temperature (K)
   * @return ThermoProperties with all phase properties
   */
  public ThermoProperties flashPT(double pressure, double temperature) {
    ThermoProperties props = new ThermoProperties();

    // Validate inputs
    if (pressure < minPressure || pressure > maxPressure) {
      props.converged = false;
      props.errorMessage = "Pressure " + pressure + " Pa outside valid range";
      return props;
    }
    if (temperature < minTemperature || temperature > maxTemperature) {
      props.converged = false;
      props.errorMessage = "Temperature " + temperature + " K outside valid range";
      return props;
    }

    // Use table if available
    if (useFlashTable && flashTable != null) {
      return flashTable.interpolate(pressure, temperature);
    }

    // Check reference fluid
    if (referenceFluid == null) {
      props.converged = false;
      props.errorMessage = "Reference fluid not set";
      return props;
    }

    try {
      // Clone and set conditions
      SystemInterface fluid = referenceFluid.clone();
      fluid.setPressure(pressure / 1e5); // Convert Pa to bar
      fluid.setTemperature(temperature); // K
      fluid.init(0);

      // Perform flash
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.TPflash();
      fluid.initProperties();

      // Extract phase fractions
      if (fluid.getNumberOfPhases() >= 2) {
        props.gasVaporFraction = fluid.getPhase(0).getBeta();
        props.liquidFraction = fluid.getPhase(1).getBeta();
      } else if (fluid.getNumberOfPhases() == 1) {
        // Single phase - determine if gas or liquid
        double density = fluid.getPhase(0).getDensity("kg/m3");
        if (density < 100.0) {
          props.gasVaporFraction = 1.0;
          props.liquidFraction = 0.0;
        } else {
          props.gasVaporFraction = 0.0;
          props.liquidFraction = 1.0;
        }
      }

      // Extract gas properties (phase 0 = vapor)
      if (fluid.hasPhaseType("gas")) {
        int gasIndex = fluid.getPhaseIndex("gas");
        props.gasDensity = fluid.getPhase(gasIndex).getDensity("kg/m3");
        props.gasViscosity = fluid.getPhase(gasIndex).getViscosity("kg/msec");
        props.gasEnthalpy = fluid.getPhase(gasIndex).getEnthalpy("J/kg");
        props.gasSoundSpeed = fluid.getPhase(gasIndex).getSoundSpeed("m/s");
        props.gasMolarMass = fluid.getPhase(gasIndex).getMolarMass() * 1000; // kg/kmol
        props.gasCompressibility = fluid.getPhase(gasIndex).getZ();
        props.gasCp = fluid.getPhase(gasIndex).getCp("J/kgK");
        props.gasThermalConductivity = fluid.getPhase(gasIndex).getThermalConductivity("W/mK");
      } else {
        // Default gas properties for single-phase liquid
        props.gasDensity = 1.0;
        props.gasViscosity = 1e-5;
        props.gasSoundSpeed = 340.0;
      }

      // Extract liquid properties (phase 1 = oil, or phase 2 = aqueous)
      if (fluid.hasPhaseType("oil")) {
        int liqIndex = fluid.getPhaseIndex("oil");
        props.liquidDensity = fluid.getPhase(liqIndex).getDensity("kg/m3");
        props.liquidViscosity = fluid.getPhase(liqIndex).getViscosity("kg/msec");
        props.liquidEnthalpy = fluid.getPhase(liqIndex).getEnthalpy("J/kg");
        props.liquidSoundSpeed = fluid.getPhase(liqIndex).getSoundSpeed("m/s");
        props.liquidMolarMass = fluid.getPhase(liqIndex).getMolarMass() * 1000;
        props.liquidCompressibility = fluid.getPhase(liqIndex).getZ();
        props.liquidCp = fluid.getPhase(liqIndex).getCp("J/kgK");
        props.liquidThermalConductivity = fluid.getPhase(liqIndex).getThermalConductivity("W/mK");
      } else if (fluid.hasPhaseType("aqueous")) {
        int liqIndex = fluid.getPhaseIndex("aqueous");
        props.liquidDensity = fluid.getPhase(liqIndex).getDensity("kg/m3");
        props.liquidViscosity = fluid.getPhase(liqIndex).getViscosity("kg/msec");
        props.liquidEnthalpy = fluid.getPhase(liqIndex).getEnthalpy("J/kg");
        props.liquidSoundSpeed = fluid.getPhase(liqIndex).getSoundSpeed("m/s");
        props.liquidMolarMass = fluid.getPhase(liqIndex).getMolarMass() * 1000;
        props.liquidCompressibility = fluid.getPhase(liqIndex).getZ();
        props.liquidCp = fluid.getPhase(liqIndex).getCp("J/kgK");
        props.liquidThermalConductivity = fluid.getPhase(liqIndex).getThermalConductivity("W/mK");
      } else {
        // Default liquid properties for single-phase gas
        props.liquidDensity = 800.0;
        props.liquidViscosity = 1e-3;
        props.liquidSoundSpeed = 1200.0;
      }

      // Surface tension
      if (fluid.getNumberOfPhases() >= 2) {
        try {
          props.surfaceTension = fluid.getInterphaseProperties().getSurfaceTension(0, 1);
        } catch (Exception e) {
          props.surfaceTension = 0.02; // Default 20 mN/m
        }
      } else {
        props.surfaceTension = 0.02;
      }

      props.converged = true;

    } catch (Exception e) {
      props.converged = false;
      props.errorMessage = "Flash failed: " + e.getMessage();

      // Set fallback values
      props.gasDensity = 50.0;
      props.liquidDensity = 800.0;
      props.gasViscosity = 1.5e-5;
      props.liquidViscosity = 1e-3;
      props.gasSoundSpeed = 350.0;
      props.liquidSoundSpeed = 1200.0;
      props.surfaceTension = 0.02;
    }

    return props;
  }

  /**
   * Perform PH flash (constant pressure and enthalpy) for adiabatic processes.
   *
   * @param pressure Pressure (Pa)
   * @param enthalpy Specific enthalpy (J/kg)
   * @return ThermoProperties with updated temperature and phase properties
   */
  public ThermoProperties flashPH(double pressure, double enthalpy) {
    ThermoProperties props = new ThermoProperties();

    if (referenceFluid == null) {
      props.converged = false;
      props.errorMessage = "Reference fluid not set";
      return props;
    }

    try {
      SystemInterface fluid = referenceFluid.clone();
      fluid.setPressure(pressure / 1e5);
      fluid.init(0);

      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.PHflash(enthalpy * fluid.getTotalNumberOfMoles() * fluid.getMolarMass());
      fluid.initProperties();

      // Get resulting temperature and call PT flash for full properties
      double temperature = fluid.getTemperature();
      props = flashPT(pressure, temperature);

    } catch (Exception e) {
      props.converged = false;
      props.errorMessage = "PH flash failed: " + e.getMessage();
    }

    return props;
  }

  /**
   * Update a TwoFluidSection with thermodynamic properties at its P-T conditions.
   *
   * @param section Pipe section to update
   */
  public void updateSectionProperties(TwoFluidSection section) {
    ThermoProperties props = flashPT(section.getPressure(), section.getTemperature());

    if (props.converged) {
      section.setGasDensity(props.gasDensity);
      section.setLiquidDensity(props.liquidDensity);
      section.setGasViscosity(props.gasViscosity);
      section.setLiquidViscosity(props.liquidViscosity);
      section.setGasSoundSpeed(props.gasSoundSpeed);
      section.setLiquidSoundSpeed(props.liquidSoundSpeed);
      section.setSurfaceTension(props.surfaceTension);
      section.setGasEnthalpy(props.gasEnthalpy);
      section.setLiquidEnthalpy(props.liquidEnthalpy);
    }
  }

  /**
   * Update all sections in an array with thermodynamic properties.
   *
   * @param sections Array of pipe sections
   */
  public void updateAllSections(TwoFluidSection[] sections) {
    for (TwoFluidSection section : sections) {
      updateSectionProperties(section);
    }
  }

  /**
   * Calculate mass transfer rate between phases (evaporation/condensation).
   *
   * <p>
   * Based on departure from equilibrium. Positive = liquid to gas.
   * </p>
   *
   * @param section Current section state
   * @param relaxationTime Mass transfer relaxation time (s)
   * @return Mass transfer rate (kg/(m³·s))
   */
  public double calcMassTransferRate(TwoFluidSection section, double relaxationTime) {
    if (referenceFluid == null) {
      return 0.0;
    }

    try {
      // Get equilibrium holdup at current P-T
      ThermoProperties eqProps = flashPT(section.getPressure(), section.getTemperature());

      if (!eqProps.converged) {
        return 0.0;
      }

      // Calculate equilibrium liquid holdup from vapor fraction
      // Vapor fraction is mole-based, convert to volume-based
      double eqGasVolFrac = eqProps.gasVaporFraction * eqProps.gasMolarMass / eqProps.gasDensity;
      double eqLiqVolFrac =
          eqProps.liquidFraction * eqProps.liquidMolarMass / eqProps.liquidDensity;
      double totalVolFrac = eqGasVolFrac + eqLiqVolFrac;

      if (totalVolFrac < 1e-10) {
        return 0.0;
      }

      double eqLiquidHoldup = eqLiqVolFrac / totalVolFrac;

      // Departure from equilibrium
      double holdupDeparture = section.getLiquidHoldup() - eqLiquidHoldup;

      // Mass transfer rate = (ρL * departure) / relaxation time
      // Positive departure means excess liquid -> evaporation (positive rate)
      return section.getLiquidDensity() * holdupDeparture / relaxationTime;

    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Calculate mixture sound speed for wave propagation.
   *
   * <p>
   * Uses Wood's equation for homogeneous mixture.
   * </p>
   *
   * @param section Pipe section with current state
   * @return Mixture sound speed (m/s)
   */
  public double calcMixtureSoundSpeed(TwoFluidSection section) {
    double alphaG = section.getGasHoldup();
    double alphaL = section.getLiquidHoldup();
    double rhoG = section.getGasDensity();
    double rhoL = section.getLiquidDensity();
    double cG = section.getGasSoundSpeed();
    double cL = section.getLiquidSoundSpeed();

    if (cG < 1e-10 || cL < 1e-10) {
      return 300.0; // Default
    }

    // Wood's equation for mixture sound speed
    // 1/(ρm*cm²) = αG/(ρG*cG²) + αL/(ρL*cL²)
    double rhoM = alphaG * rhoG + alphaL * rhoL;

    double term1 = alphaG / (rhoG * cG * cG);
    double term2 = alphaL / (rhoL * cL * cL);

    if (term1 + term2 < 1e-20) {
      return cG; // Single phase gas
    }

    return Math.sqrt(1.0 / (rhoM * (term1 + term2)));
  }

  /**
   * Enable flash table interpolation for performance.
   *
   * @param table Pre-computed flash table
   */
  public void setFlashTable(FlashTable table) {
    this.flashTable = table;
    this.useFlashTable = (table != null);
  }

  /**
   * Get flash table.
   *
   * @return Current flash table or null
   */
  public FlashTable getFlashTable() {
    return flashTable;
  }

  /**
   * Check if flash table interpolation is enabled.
   *
   * @return True if using table interpolation
   */
  public boolean isUsingFlashTable() {
    return useFlashTable;
  }

  /**
   * Set valid pressure range for flash calculations.
   *
   * @param min Minimum pressure (Pa)
   * @param max Maximum pressure (Pa)
   */
  public void setPressureRange(double min, double max) {
    this.minPressure = min;
    this.maxPressure = max;
  }

  /**
   * Set valid temperature range for flash calculations.
   *
   * @param min Minimum temperature (K)
   * @param max Maximum temperature (K)
   */
  public void setTemperatureRange(double min, double max) {
    this.minTemperature = min;
    this.maxTemperature = max;
  }

  /**
   * Set flash convergence tolerance.
   *
   * @param tolerance Convergence tolerance
   */
  public void setFlashTolerance(double tolerance) {
    this.flashTolerance = tolerance;
  }

  /**
   * Get flash convergence tolerance.
   *
   * @return Current tolerance
   */
  public double getFlashTolerance() {
    return flashTolerance;
  }

  /**
   * Set maximum flash iterations.
   *
   * @param maxIterations Maximum iterations
   */
  public void setMaxFlashIterations(int maxIterations) {
    this.maxFlashIterations = maxIterations;
  }

  /**
   * Get maximum flash iterations.
   *
   * @return Maximum iterations
   */
  public int getMaxFlashIterations() {
    return maxFlashIterations;
  }
}
