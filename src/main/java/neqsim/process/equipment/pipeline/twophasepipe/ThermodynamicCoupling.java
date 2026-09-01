package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Thermodynamic coupling for the two-fluid transient pipe model.
 *
 * <p>
 * Provides interface between the two-fluid hydrodynamic solver and NeqSim's thermodynamic calculations. Handles flash
 * calculations to update phase properties and compositions along the pipeline.
 * </p>
 *
 * <h2>Key Functions</h2>
 * <ul>
 * <li>Update phase densities, viscosities, and enthalpies from P-T flash</li>
 * <li>Aggregate gas, hydrocarbon-liquid, and aqueous-liquid contributions by {@link PhaseType}</li>
 * <li>Calculate conservative phase-resolved mass transfer rates with donor-inventory limits</li>
 * <li>Provide sound speeds for wave propagation</li>
 * <li>Support for both rigorous flash and table interpolation</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <p>
 * Flash calculations are computationally expensive. For transient simulations with many time steps and grid cells,
 * consider using {@link FlashTable} for pre-computed property interpolation.
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

    /** Hydrocarbon-liquid mass fraction of total equilibrium liquid. */
    public double oilMassFractionOfLiquid;

    /** Aqueous-liquid mass fraction of total equilibrium liquid. */
    public double aqueousMassFractionOfLiquid;

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
  public ThermodynamicCoupling() {
  }

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

      props = extractProperties(fluid);

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
   * Extract phase-aggregated properties by phase identity rather than phase-array position.
   *
   * <p>
   * Hydrocarbon phases of type {@link PhaseType#OIL}, {@link PhaseType#LIQUID}, and {@link PhaseType#LIQUID_ASPHALTENE}
   * are accumulated as oil. All aqueous contributions are accumulated separately. Aggregate liquid transport properties
   * are mass weighted, while the aggregate liquid density is calculated from total liquid mass divided by total liquid
   * volume.
   * </p>
   *
   * @param fluid flashed and property-initialized fluid
   * @return phase-aggregated properties
   */
  ThermoProperties extractProperties(SystemInterface fluid) {
    ThermoProperties props = new ThermoProperties();
    double gasMoles = 0.0;
    double gasMass = 0.0;
    double gasVolume = 0.0;
    double oilMoles = 0.0;
    double oilMass = 0.0;
    double oilVolume = 0.0;
    double aqueousMoles = 0.0;
    double aqueousMass = 0.0;
    double aqueousVolume = 0.0;
    double gasViscosityMassSum = 0.0;
    double gasEnthalpyMassSum = 0.0;
    double gasSoundSpeedMassSum = 0.0;
    double gasCpMassSum = 0.0;
    double gasConductivityMassSum = 0.0;
    double gasCompressibilityMoleSum = 0.0;
    double liquidViscosityMassSum = 0.0;
    double liquidEnthalpyMassSum = 0.0;
    double liquidSoundSpeedMassSum = 0.0;
    double liquidCpMassSum = 0.0;
    double liquidConductivityMassSum = 0.0;
    double liquidCompressibilityMoleSum = 0.0;
    int gasPhaseIndex = -1;
    double surfaceTensionMassSum = 0.0;

    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      PhaseType phaseType = phase.getType();
      double beta = Math.max(0.0, phase.getBeta());
      double molarMass = Math.max(0.0, phase.getMolarMass() * 1000.0);
      double massContribution = beta * molarMass;
      double density = Math.max(phase.getDensity("kg/m3"), 1.0e-12);
      double volumeContribution = massContribution / density;

      if (phaseType == PhaseType.GAS) {
        gasPhaseIndex = phaseIndex;
        gasMoles += beta;
        gasMass += massContribution;
        gasVolume += volumeContribution;
        gasViscosityMassSum += massContribution * phase.getViscosity("kg/msec");
        gasEnthalpyMassSum += massContribution * phase.getEnthalpy("J/kg");
        gasSoundSpeedMassSum += massContribution * phase.getSoundSpeed("m/s");
        gasCpMassSum += massContribution * phase.getCp("J/kgK");
        gasConductivityMassSum += massContribution * phase.getThermalConductivity("W/mK");
        gasCompressibilityMoleSum += beta * phase.getZ();
      } else if (isHydrocarbonLiquid(phaseType) || phaseType == PhaseType.AQUEOUS) {
        if (phaseType == PhaseType.AQUEOUS) {
          aqueousMoles += beta;
          aqueousMass += massContribution;
          aqueousVolume += volumeContribution;
        } else {
          oilMoles += beta;
          oilMass += massContribution;
          oilVolume += volumeContribution;
        }
        liquidViscosityMassSum += massContribution * phase.getViscosity("kg/msec");
        liquidEnthalpyMassSum += massContribution * phase.getEnthalpy("J/kg");
        liquidSoundSpeedMassSum += massContribution * phase.getSoundSpeed("m/s");
        liquidCpMassSum += massContribution * phase.getCp("J/kgK");
        liquidConductivityMassSum += massContribution * phase.getThermalConductivity("W/mK");
        liquidCompressibilityMoleSum += beta * phase.getZ();
      }
    }

    double liquidMoles = oilMoles + aqueousMoles;
    double liquidMass = oilMass + aqueousMass;
    double liquidVolume = oilVolume + aqueousVolume;
    double classifiedMoles = gasMoles + liquidMoles;
    if (classifiedMoles > 0.0) {
      props.gasVaporFraction = gasMoles / classifiedMoles;
      props.liquidFraction = liquidMoles / classifiedMoles;
    }
    if (liquidMass > 0.0) {
      props.oilMassFractionOfLiquid = oilMass / liquidMass;
      props.aqueousMassFractionOfLiquid = 1.0 - props.oilMassFractionOfLiquid;
    }

    if (gasMass > 0.0 && gasMoles > 0.0 && gasVolume > 0.0) {
      props.gasDensity = gasMass / gasVolume;
      props.gasMolarMass = gasMass / gasMoles;
      props.gasViscosity = gasViscosityMassSum / gasMass;
      props.gasEnthalpy = gasEnthalpyMassSum / gasMass;
      props.gasSoundSpeed = gasSoundSpeedMassSum / gasMass;
      props.gasCp = gasCpMassSum / gasMass;
      props.gasThermalConductivity = gasConductivityMassSum / gasMass;
      props.gasCompressibility = gasCompressibilityMoleSum / gasMoles;
    } else {
      setDefaultGasProperties(props);
    }

    if (liquidMass > 0.0 && liquidMoles > 0.0 && liquidVolume > 0.0) {
      props.liquidDensity = liquidMass / liquidVolume;
      props.liquidMolarMass = liquidMass / liquidMoles;
      props.liquidViscosity = liquidViscosityMassSum / liquidMass;
      props.liquidEnthalpy = liquidEnthalpyMassSum / liquidMass;
      props.liquidSoundSpeed = liquidSoundSpeedMassSum / liquidMass;
      props.liquidCp = liquidCpMassSum / liquidMass;
      props.liquidThermalConductivity = liquidConductivityMassSum / liquidMass;
      props.liquidCompressibility = liquidCompressibilityMoleSum / liquidMoles;
    } else {
      setDefaultLiquidProperties(props);
    }

    if (gasPhaseIndex >= 0 && liquidMass > 0.0) {
      for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
        PhaseInterface phase = fluid.getPhase(phaseIndex);
        if (isHydrocarbonLiquid(phase.getType()) || phase.getType() == PhaseType.AQUEOUS) {
          double phaseMass = Math.max(0.0, phase.getBeta() * phase.getMolarMass() * 1000.0);
          try {
            surfaceTensionMassSum += phaseMass
                * fluid.getInterphaseProperties().getSurfaceTension(gasPhaseIndex, phaseIndex);
          } catch (Exception ignored) {
            surfaceTensionMassSum += phaseMass * 0.02;
          }
        }
      }
      props.surfaceTension = surfaceTensionMassSum / liquidMass;
    } else {
      props.surfaceTension = 0.02;
    }
    props.converged = true;
    return props;
  }

  /**
   * Check whether a NeqSim phase type belongs to the hydrocarbon-liquid inventory.
   *
   * @param phaseType phase identity
   * @return {@code true} for oil-like liquid phase types
   */
  private boolean isHydrocarbonLiquid(PhaseType phaseType) {
    return phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID || phaseType == PhaseType.LIQUID_ASPHALTENE;
  }

  /**
   * Set finite gas placeholders when no equilibrium gas phase is present.
   *
   * @param props property result to update
   */
  private void setDefaultGasProperties(ThermoProperties props) {
    props.gasDensity = 1.0;
    props.gasViscosity = 1.0e-5;
    props.gasSoundSpeed = 340.0;
    props.gasMolarMass = 20.0;
    props.gasCompressibility = 1.0;
  }

  /**
   * Set finite liquid placeholders when no equilibrium liquid phase is present.
   *
   * @param props property result to update
   */
  private void setDefaultLiquidProperties(ThermoProperties props) {
    props.liquidDensity = 800.0;
    props.liquidViscosity = 1.0e-3;
    props.liquidSoundSpeed = 1200.0;
    props.liquidMolarMass = 100.0;
    props.liquidCompressibility = 0.01;
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
      double eqLiqVolFrac = eqProps.liquidFraction * eqProps.liquidMolarMass / eqProps.liquidDensity;
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
   * Calculate a conservative flash-driven gas/liquid mass transfer source per pipe length.
   *
   * <p>
   * Positive values mean evaporation from liquid to gas. The target is the equilibrium liquid inventory from the local
   * PT flash, expressed on the section area. The method returns kg/(m*s), which can be split conservatively into gas,
   * oil, and water equations by the hydrodynamic solver.
   * </p>
   *
   * @param section current pipe section
   * @param relaxationTime relaxation time toward flash equilibrium (s)
   * @return gas mass source per length, kg/(m*s)
   */
  public double calcMassTransferRatePerLength(TwoFluidSection section, double relaxationTime) {
    return calcPhaseMassTransferRatePerLength(section, relaxationTime).getGasSourceKgPerMetreSecond();
  }

  /**
   * Calculate conservative gas, hydrocarbon-liquid, and aqueous-liquid transfer sources.
   *
   * <p>
   * Condensation is split using equilibrium liquid mass contributions from the PT flash. Evaporation is split using the
   * actual donor inventories and each withdrawal is limited by that phase inventory divided by the relaxation time.
   * Consequently an absent oil or water phase cannot evaporate. Positive gas source denotes evaporation and negative
   * gas source denotes condensation.
   * </p>
   *
   * @param section current pipe section
   * @param relaxationTime relaxation time toward flash equilibrium in seconds
   * @return immutable phase-resolved sources in kg/(m s)
   */
  public PhaseMassTransfer calcPhaseMassTransferRatePerLength(TwoFluidSection section, double relaxationTime) {
    if (referenceFluid == null || relaxationTime <= 0.0 || !Double.isFinite(relaxationTime)) {
      return PhaseMassTransfer.zero(false, false, "Reference fluid and a positive relaxation time are required");
    }

    ThermoProperties eqProps = flashPT(section.getPressure(), section.getTemperature());
    if (!eqProps.converged) {
      return PhaseMassTransfer.zero(false, false, eqProps.errorMessage);
    }

    double gasSource = calculateScalarGasSourcePerLength(section, relaxationTime, eqProps);
    if (gasSource < 0.0) {
      double oilFraction = Math.max(0.0, eqProps.oilMassFractionOfLiquid);
      double waterFraction = Math.max(0.0, eqProps.aqueousMassFractionOfLiquid);
      double fractionSum = oilFraction + waterFraction;
      if (fractionSum <= 1.0e-15) {
        return PhaseMassTransfer.zero(true, false,
            "Equilibrium liquid exists but its oil/aqueous identity is unavailable");
      }
      oilFraction /= fractionSum;
      double liquidSource = -gasSource;
      double oilSource = liquidSource * oilFraction;
      double waterSource = -gasSource - oilSource;
      return new PhaseMassTransfer(gasSource, oilSource, waterSource, true, true, null);
    }

    if (gasSource > 0.0) {
      double oilInventory = Math.max(0.0, section.getOilMassPerLength());
      double waterInventory = Math.max(0.0, section.getWaterMassPerLength());
      double liquidInventory = oilInventory + waterInventory;
      if (liquidInventory <= 0.0) {
        return PhaseMassTransfer.zero(true, true, null);
      }
      double oilWithdrawal = Math.min(gasSource * oilInventory / liquidInventory, oilInventory / relaxationTime);
      double waterWithdrawal = Math.min(gasSource - oilWithdrawal, waterInventory / relaxationTime);
      double boundedGasSource = oilWithdrawal + waterWithdrawal;
      double oilSource = -oilWithdrawal;
      double waterSource = -boundedGasSource - oilSource;
      return new PhaseMassTransfer(boundedGasSource, oilSource, waterSource, true, true, null);
    }

    return PhaseMassTransfer.zero(true, true, null);
  }

  /**
   * Calculate the legacy aggregate gas source from departure from equilibrium liquid inventory.
   *
   * @param section current pipe section
   * @param relaxationTime relaxation time in seconds
   * @param eqProps equilibrium properties at section pressure and temperature
   * @return aggregate gas source in kg/(m s)
   */
  private double calculateScalarGasSourcePerLength(TwoFluidSection section, double relaxationTime,
      ThermoProperties eqProps) {

    double gasDensity = Math.max(eqProps.gasDensity, 0.1);
    double liquidDensity = Math.max(eqProps.liquidDensity, 100.0);
    double gasMolarMass = Math.max(eqProps.gasMolarMass, 1e-6);
    double liquidMolarMass = Math.max(eqProps.liquidMolarMass, 1e-6);

    double eqGasVolume = Math.max(eqProps.gasVaporFraction, 0.0) * gasMolarMass / gasDensity;
    double eqLiquidVolume = Math.max(eqProps.liquidFraction, 0.0) * liquidMolarMass / liquidDensity;
    double totalEquilibriumVolume = eqGasVolume + eqLiquidVolume;
    if (totalEquilibriumVolume <= 1e-20) {
      return 0.0;
    }

    double equilibriumLiquidHoldup = eqLiquidVolume / totalEquilibriumVolume;
    equilibriumLiquidHoldup = Math.max(0.0, Math.min(1.0, equilibriumLiquidHoldup));

    double currentLiquidMassPerLength = Math.max(0.0, section.getLiquidMassPerLength());
    if (currentLiquidMassPerLength <= 0.0) {
      currentLiquidMassPerLength = Math.max(0.0, section.getOilMassPerLength() + section.getWaterMassPerLength());
    }
    double equilibriumLiquidMassPerLength = equilibriumLiquidHoldup * liquidDensity * section.getArea();

    double source = (currentLiquidMassPerLength - equilibriumLiquidMassPerLength) / relaxationTime;
    source = Math.min(source, currentLiquidMassPerLength / relaxationTime);
    source = Math.max(source, -Math.max(0.0, section.getGasMassPerLength()) / relaxationTime);

    return Double.isFinite(source) ? source : 0.0;
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
