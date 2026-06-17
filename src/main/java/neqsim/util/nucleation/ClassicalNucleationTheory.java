package neqsim.util.nucleation;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;

/**
 * General-purpose Classical Nucleation Theory (CNT) model for predicting particle formation from
 * supersaturated vapors.
 *
 * <p>
 * This class implements homogeneous nucleation theory to predict critical nucleus size, nucleation
 * rate, particle growth by condensation, and coagulation loss. It is applicable to any substance
 * undergoing vapor-to-solid or vapor-to-liquid phase transition including:
 * </p>
 * <ul>
 * <li>Elemental sulfur (S8) deposition in natural gas systems</li>
 * <li>Water ice nucleation in cryogenic processes</li>
 * <li>Wax crystallization from paraffinic oils</li>
 * <li>Hydrate particle formation</li>
 * <li>Metal oxide particle formation in combustion</li>
 * </ul>
 *
 * <p>
 * The model uses the following physics:
 * </p>
 * <ul>
 * <li><b>Kelvin equation</b>: critical nucleus radius from supersaturation</li>
 * <li><b>Becker-Doering CNT</b>: nucleation rate with Zeldovich factor</li>
 * <li><b>Hertz-Knudsen</b>: free-molecular condensation growth</li>
 * <li><b>Maxwell continuum</b>: diffusion-limited condensation growth</li>
 * <li><b>Fuchs interpolation</b>: transition regime bridging both growth limits</li>
 * <li><b>Smoluchowski coagulation</b>: Brownian coagulation reducing particle number</li>
 * </ul>
 *
 * <p>
 * Usage example for sulfur particle prediction:
 * </p>
 *
 * <pre>
 * {@code
 * ClassicalNucleationTheory cnt = ClassicalNucleationTheory.sulfurS8();
 * cnt.setTemperature(253.15); // -20 C
 * cnt.setSupersaturationRatio(100.0);
 * cnt.setGasViscosity(1.0e-5); // Pa.s
 * cnt.setGasDiffusivity(5.0e-6); // m2/s
 * cnt.setResidenceTime(2.0); // seconds
 * cnt.calculate();
 *
 * double diameter_um = cnt.getMeanParticleDiameter() * 1e6;
 * double rate = cnt.getNucleationRate(); // particles per m3 per second
 * }
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Becker, R. and Doering, W. (1935). Ann. Phys. 416, 719-752.</li>
 * <li>Seinfeld, J.H. and Pandis, S.N. (2016). Atmospheric Chemistry and Physics, 3rd ed.</li>
 * <li>Friedlander, S.K. (2000). Smoke, Dust, and Haze, 2nd ed.</li>
 * <li>Fuchs, N.A. and Sutugin, A.G. (1971). Topics in Current Aerosol Research.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class ClassicalNucleationTheory {

  // ============================================================================
  // Physical Constants
  // ============================================================================

  /** Boltzmann constant in J/K. */
  public static final double K_BOLTZMANN = 1.38065e-23;

  /** Avogadro's number in 1/mol. */
  public static final double N_AVOGADRO = 6.02214e23;

  /** Universal gas constant in J/(mol*K). */
  public static final double R_GAS = 8.31446;

  // ============================================================================
  // Substance Properties
  // ============================================================================

  /** Molecular weight of the condensing species in kg/mol. */
  private double molecularWeight;

  /** Density of the condensed phase (solid or liquid) in kg/m3. */
  private double condensedPhaseDensity;

  /** Surface tension (surface free energy) of the condensed phase in N/m (= J/m2). */
  private double surfaceTension;

  /** Name of the substance for reporting. */
  private String substanceName = "Unknown";

  /** Molecular volume per molecule in m3. Derived from MW and density. */
  private double molecularVolume;

  /** Mass per molecule in kg. Derived from MW. */
  private double molecularMass;

  /**
   * Sticking (accommodation) coefficient for vapor molecules hitting the nucleus surface. Default
   * 1.0 (every collision sticks). Typical range 0.01 to 1.0.
   */
  private double stickingCoefficient = 1.0;

  // ============================================================================
  // Heterogeneous Nucleation Parameters
  // ============================================================================

  /** Whether to use heterogeneous nucleation (with contact angle correction). */
  private boolean heterogeneous = false;

  /**
   * Contact angle in degrees between the condensed phase and the substrate surface. Only used when
   * heterogeneous = true. Range 0 to 180 degrees. 0 = complete wetting (no barrier), 180 =
   * non-wetting (same as homogeneous).
   */
  private double contactAngleDegrees = 90.0;

  /**
   * Fletcher shape factor f(theta) that multiplies the homogeneous barrier. f(theta) = (2 -
   * 3*cos(theta) + cos^3(theta)) / 4. Range: 0 (complete wetting) to 1 (non-wetting).
   */
  private double contactAngleFactor = 0.5;

  // ============================================================================
  // Process Conditions (Inputs)
  // ============================================================================

  /** Temperature in K. */
  private double temperature = 273.15;

  /** Actual partial pressure of the condensing species in Pa. */
  private double actualPartialPressure = 0.0;

  /** Saturation (equilibrium) vapor pressure of the condensed phase in Pa. */
  private double saturationPressure = 0.0;

  /**
   * Supersaturation ratio S = p_actual / p_sat. Can be set directly instead of setting both
   * pressures.
   */
  private double supersaturationRatio = 1.0;

  /** Diffusivity of the condensing vapor in the carrier gas in m2/s. */
  private double gasDiffusivity = 5.0e-6;

  /** Dynamic viscosity of the carrier gas in Pa*s. */
  private double gasViscosity = 1.0e-5;

  /**
   * Mean free path of gas molecules in m. If not set, estimated from gas viscosity and conditions.
   */
  private double meanFreePath = 0.0;

  /** Residence time in the supersaturated zone in seconds. */
  private double residenceTime = 1.0;

  /** Total pressure of the gas in Pa. Used for mean free path estimation. */
  private double totalPressure = 1.0e6;

  /** Molar mass of the carrier gas in kg/mol. Used for mean free path estimation. */
  private double carrierGasMolarMass = 0.016;

  // ============================================================================
  // Calculated Results
  // ============================================================================

  /** Critical nucleus radius in m. */
  private double criticalRadius = 0.0;

  /** Number of molecules in the critical nucleus. */
  private double criticalNucleusMolecules = 0.0;

  /** Free energy barrier for nucleation in J. */
  private double freeEnergyBarrier = 0.0;

  /** Dimensionless free energy barrier (DeltaG* / kT). */
  private double dimensionlessFreeEnergyBarrier = 0.0;

  /** Zeldovich non-equilibrium factor (dimensionless). */
  private double zeldovichFactor = 0.0;

  /** Homogeneous nucleation rate in particles/(m3*s). */
  private double nucleationRate = 0.0;

  /** Condensation growth rate in m/s (at critical radius). */
  private double growthRate = 0.0;

  /** Growth rate in free molecular regime in m/s. */
  private double growthRateFreeM = 0.0;

  /** Growth rate in continuum (diffusion-limited) regime in m/s. */
  private double growthRateContinuum = 0.0;

  /** Brownian coagulation kernel in m3/s (continuum regime). */
  private double coagulationKernel = 0.0;

  /** Mean particle radius after residence time (incl. condensation + coagulation) in m. */
  private double meanParticleRadius = 0.0;

  /** Mean particle diameter after residence time in m. */
  private double meanParticleDiameter = 0.0;

  /** Particle number density after residence time in particles/m3. */
  private double particleNumberDensity = 0.0;

  /** Knudsen number at the mean particle radius (dimensionless). */
  private double knudsenNumber = 0.0;

  /** Estimated geometric standard deviation of the particle size distribution. */
  private double geometricStdDev = 1.5;

  /** Mass concentration of particles in kg/m3. */
  private double particleMassConcentration = 0.0;

  /** Whether the calculation has been performed. */
  private boolean calculated = false;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Creates a ClassicalNucleationTheory model for a given substance.
   *
   * @param molecularWeight_kg_mol molecular weight of the condensing species in kg/mol
   * @param condensedDensity_kg_m3 density of the condensed (solid or liquid) phase in kg/m3
   * @param surfaceTension_N_m surface tension of the condensed phase in N/m
   */
  public ClassicalNucleationTheory(double molecularWeight_kg_mol, double condensedDensity_kg_m3,
      double surfaceTension_N_m) {
    this.molecularWeight = molecularWeight_kg_mol;
    this.condensedPhaseDensity = condensedDensity_kg_m3;
    this.surfaceTension = surfaceTension_N_m;
    this.molecularVolume = molecularWeight / (condensedDensity_kg_m3 * N_AVOGADRO);
    this.molecularMass = molecularWeight / N_AVOGADRO;
  }

  // ============================================================================
  // Factory Methods for Common Substances
  // ============================================================================

  /**
   * Creates a CNT model for elemental sulfur S8.
   *
   * <p>
   * Properties: MW = 256.52 g/mol, solid density = 2070 kg/m3, surface tension approx 0.060 N/m.
   * </p>
   *
   * @return ClassicalNucleationTheory configured for S8
   */
  public static ClassicalNucleationTheory sulfurS8() {
    ClassicalNucleationTheory cnt = new ClassicalNucleationTheory(0.25652, 2070.0, 0.060);
    cnt.substanceName = "Sulfur (S8)";
    return cnt;
  }

  /**
   * Creates a CNT model for water (ice nucleation from vapor).
   *
   * <p>
   * Properties: MW = 18.015 g/mol, ice density = 917 kg/m3, ice surface tension approx 0.106 N/m.
   * </p>
   *
   * @return ClassicalNucleationTheory configured for water/ice
   */
  public static ClassicalNucleationTheory waterIce() {
    ClassicalNucleationTheory cnt = new ClassicalNucleationTheory(0.01802, 917.0, 0.106);
    cnt.substanceName = "Water (ice)";
    return cnt;
  }

  /**
   * Creates a CNT model for a generic n-paraffin wax (e.g., C25H52 for wax nucleation).
   *
   * <p>
   * Properties: MW = 352.7 g/mol, solid density = 900 kg/m3, surface tension approx 0.025 N/m.
   * </p>
   *
   * @return ClassicalNucleationTheory configured for paraffin wax
   */
  public static ClassicalNucleationTheory paraffinWax() {
    ClassicalNucleationTheory cnt = new ClassicalNucleationTheory(0.3527, 900.0, 0.025);
    cnt.substanceName = "Paraffin Wax (C25)";
    return cnt;
  }

  /**
   * Creates a CNT model for naphthalene (C10H8) solid deposition.
   *
   * <p>
   * Properties: MW = 128.17 g/mol, solid density = 1145 kg/m3, surface tension approx 0.050 N/m.
   * </p>
   *
   * @return ClassicalNucleationTheory configured for naphthalene
   */
  public static ClassicalNucleationTheory naphthalene() {
    ClassicalNucleationTheory cnt = new ClassicalNucleationTheory(0.12817, 1145.0, 0.050);
    cnt.substanceName = "Naphthalene (C10H8)";
    return cnt;
  }

  // ============================================================================
  // EOS-Coupled Factory Method
  // ============================================================================

  /**
   * Creates a CNT model from a NeqSim thermodynamic system for a specified condensable component.
   *
   * <p>
   * This factory method extracts physical properties from the EOS-based thermo system:
   * </p>
   * <ul>
   * <li>Temperature from the system temperature</li>
   * <li>Surface tension from interphase properties (if two phases exist)</li>
   * <li>Gas-phase transport properties (viscosity, diffusivity) from the gas phase</li>
   * <li>Total pressure for mean free path estimation</li>
   * <li>Supersaturation is estimated from the fugacity ratio if two phases are present</li>
   * </ul>
   *
   * <p>
   * The system should have been flashed (TPflash + initProperties) before calling this method.
   * </p>
   *
   * @param system the NeqSim thermodynamic system (should be flashed and initialized)
   * @param componentName the name of the condensable component (e.g., "n-heptane", "water")
   * @return ClassicalNucleationTheory model populated from the EOS, or null if the component is not
   *         found
   */
  public static ClassicalNucleationTheory fromThermoSystem(SystemInterface system,
      String componentName) {
    // Find the component index
    int compIndex = -1;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getComponentName().equalsIgnoreCase(componentName)) {
        compIndex = i;
        break;
      }
    }
    if (compIndex < 0) {
      return null;
    }

    // Get component properties
    double mw = system.getPhase(0).getComponent(compIndex).getMolarMass(); // kg/mol
    double tc = system.getPhase(0).getComponent(compIndex).getTC(); // K
    double pc = system.getPhase(0).getComponent(compIndex).getPC() * 1e5; // Pa

    // Estimate condensed phase density from critical properties (Rackett equation approximation)
    double density = estimateCondensedDensity(mw, tc, pc, system.getTemperature());

    // Estimate surface tension from Macleod-Sugden if two phases exist,
    // otherwise use Eotvos approximation
    double sigma;
    if (system.getNumberOfPhases() >= 2) {
      try {
        sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      } catch (Exception e) {
        sigma = estimateSurfaceTension(tc, pc, system.getTemperature());
      }
    } else {
      sigma = estimateSurfaceTension(tc, pc, system.getTemperature());
    }

    // Create CNT model
    ClassicalNucleationTheory cnt = new ClassicalNucleationTheory(mw, density, sigma);
    cnt.substanceName = componentName + " (from EOS)";
    cnt.setTemperature(system.getTemperature());
    cnt.setTotalPressure(system.getPressure() * 1e5); // bara to Pa

    // Set gas-phase transport properties if available
    if (system.getNumberOfPhases() >= 1 && system.hasPhaseType("gas")) {
      try {
        int gasPhaseIndex = system.getPhaseNumberOfPhase("gas");
        double gasVisc = system.getPhase(gasPhaseIndex).getViscosity("kg/msec");
        if (gasVisc > 0.0) {
          cnt.setGasViscosity(gasVisc);
        }
      } catch (Exception e) {
        // Use default viscosity
      }
    }

    // Estimate supersaturation from fugacity ratio if two phases present
    if (system.getNumberOfPhases() >= 2) {
      try {
        int gasPhaseIndex = system.getPhaseNumberOfPhase("gas");
        int liquidPhaseIndex = system.getPhaseNumberOfPhase("oil");
        if (liquidPhaseIndex < 0) {
          liquidPhaseIndex = system.getPhaseNumberOfPhase("aqueous");
        }

        if (gasPhaseIndex >= 0 && liquidPhaseIndex >= 0) {
          // Fugacity = x_i * P * phi_i
          double xGas = system.getPhase(gasPhaseIndex).getComponent(compIndex).getx();
          double phiGas =
              system.getPhase(gasPhaseIndex).getComponent(compIndex).getFugacityCoefficient();
          double xLiq = system.getPhase(liquidPhaseIndex).getComponent(compIndex).getx();
          double phiLiq =
              system.getPhase(liquidPhaseIndex).getComponent(compIndex).getFugacityCoefficient();
          double pressure = system.getPressure() * 1e5; // bara to Pa
          double fugGas = xGas * pressure * phiGas;
          double fugLiq = xLiq * pressure * phiLiq;
          if (fugLiq > 0.0 && fugGas > 0.0) {
            double s = fugGas / fugLiq;
            if (s > 1.0) {
              cnt.setSupersaturationRatio(s);
            }
          }
        }
      } catch (Exception e) {
        // Supersaturation remains at default
      }
    }

    // Set carrier gas molar mass (approximate from overall gas composition)
    if (system.hasPhaseType("gas")) {
      try {
        int gasPhaseIndex = system.getPhaseNumberOfPhase("gas");
        double gasMw = system.getPhase(gasPhaseIndex).getMolarMass();
        if (gasMw > 0.0) {
          cnt.setCarrierGasMolarMass(gasMw);
        }
      } catch (Exception e) {
        // Use default
      }
    }

    return cnt;
  }

  /**
   * Estimates condensed phase density from critical properties using a simplified Rackett equation.
   *
   * @param mw molecular weight in kg/mol
   * @param tc critical temperature in K
   * @param pc critical pressure in Pa
   * @param temperature actual temperature in K
   * @return estimated liquid density in kg/m3
   */
  private static double estimateCondensedDensity(double mw, double tc, double pc,
      double temperature) {
    // Simplified: rho_L ~ pc * mw / (R * tc) * (1 + 0.85*(1 - T/Tc))
    double rhoC = pc * mw / (R_GAS * tc);
    double tr = Math.min(temperature / tc, 0.99);
    return rhoC * (1.0 + 0.85 * (1.0 - tr));
  }

  /**
   * Estimates surface tension from critical properties using the Eotvos correlation.
   *
   * @param tc critical temperature in K
   * @param pc critical pressure in Pa
   * @param temperature actual temperature in K
   * @return estimated surface tension in N/m
   */
  private static double estimateSurfaceTension(double tc, double pc, double temperature) {
    // Eotvos: sigma = k_E * (Tc - T - 6) / V_m^(2/3)
    // Simplified Brock-Bird: sigma = 0.1207 * (1 + T_br/T_c * ln(P_c/1.01325e5) / (1 -
    // T_br/T_c))^(2/3) * (1 - T/Tc)^(11/9)
    // Use simplified Macleod-Sugden approximation
    double tr = Math.min(temperature / tc, 0.99);
    double sigma = 0.072 * Math.pow(1.0 - tr, 1.222); // Simplified correlation
    return Math.max(sigma, 1.0e-4); // Minimum bound
  }

  // ============================================================================
  // Setters for Process Conditions
  // ============================================================================

  /**
   * Sets the temperature.
   *
   * @param temperatureKelvin temperature in Kelvin
   */
  public void setTemperature(double temperatureKelvin) {
    this.temperature = temperatureKelvin;
    this.calculated = false;
  }

  /**
   * Sets the actual partial pressure of the condensing species.
   *
   * @param pressurePa partial pressure in Pa
   */
  public void setPartialPressure(double pressurePa) {
    this.actualPartialPressure = pressurePa;
    this.calculated = false;
  }

  /**
   * Sets the saturation (equilibrium) vapor pressure over the condensed phase.
   *
   * @param pressurePa saturation vapor pressure in Pa
   */
  public void setSaturationPressure(double pressurePa) {
    this.saturationPressure = pressurePa;
    this.calculated = false;
  }

  /**
   * Sets the supersaturation ratio directly. If both partial pressure and saturation pressure are
   * also set, this value takes precedence.
   *
   * @param ratio supersaturation ratio S = p_actual / p_sat (must be &gt; 1 for nucleation)
   */
  public void setSupersaturationRatio(double ratio) {
    this.supersaturationRatio = ratio;
    this.calculated = false;
  }

  /**
   * Sets the diffusivity of the condensing vapor in the carrier gas.
   *
   * @param diffusivity gas-phase diffusivity in m2/s (typical 1e-6 to 1e-5)
   */
  public void setGasDiffusivity(double diffusivity) {
    this.gasDiffusivity = diffusivity;
    this.calculated = false;
  }

  /**
   * Sets the dynamic viscosity of the carrier gas.
   *
   * @param viscosity dynamic viscosity in Pa*s (typical 1e-5 for natural gas)
   */
  public void setGasViscosity(double viscosity) {
    this.gasViscosity = viscosity;
    this.calculated = false;
  }

  /**
   * Sets the mean free path of gas molecules directly. If not set, it is estimated from gas
   * viscosity and conditions.
   *
   * @param lambda mean free path in m (typical 5-100 nm at process conditions)
   */
  public void setMeanFreePath(double lambda) {
    this.meanFreePath = lambda;
    this.calculated = false;
  }

  /**
   * Sets the residence time in the supersaturated zone.
   *
   * @param timeSeconds residence time in seconds
   */
  public void setResidenceTime(double timeSeconds) {
    this.residenceTime = timeSeconds;
    this.calculated = false;
  }

  /**
   * Sets the total gas pressure. Used for mean free path estimation if not explicitly set.
   *
   * @param pressurePa total pressure in Pa
   */
  public void setTotalPressure(double pressurePa) {
    this.totalPressure = pressurePa;
    this.calculated = false;
  }

  /**
   * Sets the carrier gas molar mass. Used for mean free path estimation.
   *
   * @param molarMass carrier gas molar mass in kg/mol (e.g. 0.016 for methane)
   */
  public void setCarrierGasMolarMass(double molarMass) {
    this.carrierGasMolarMass = molarMass;
    this.calculated = false;
  }

  /**
   * Sets the sticking (accommodation) coefficient.
   *
   * @param alpha sticking coefficient (0 to 1). Default 1.0.
   */
  public void setStickingCoefficient(double alpha) {
    this.stickingCoefficient = Math.max(0.0, Math.min(1.0, alpha));
    this.calculated = false;
  }

  /**
   * Sets the surface tension of the condensed phase.
   *
   * @param sigma surface tension in N/m
   */
  public void setSurfaceTension(double sigma) {
    this.surfaceTension = sigma;
    this.calculated = false;
  }

  /**
   * Sets the substance name for reporting.
   *
   * @param name substance name
   */
  public void setSubstanceName(String name) {
    this.substanceName = name;
  }

  /**
   * Sets whether to use heterogeneous nucleation. When true, the free energy barrier is multiplied
   * by the contact angle factor f(theta), which reduces the barrier and increases the nucleation
   * rate. This models nucleation on surfaces such as pipe walls, particulate impurities, or
   * pre-existing droplets.
   *
   * @param isHeterogeneous true to enable heterogeneous nucleation
   */
  public void setHeterogeneous(boolean isHeterogeneous) {
    this.heterogeneous = isHeterogeneous;
    this.calculated = false;
  }

  /**
   * Returns whether heterogeneous nucleation is enabled.
   *
   * @return true if heterogeneous nucleation is enabled
   */
  public boolean isHeterogeneous() {
    return heterogeneous;
  }

  /**
   * Sets the contact angle for heterogeneous nucleation. The contact angle determines how much the
   * free energy barrier is reduced relative to homogeneous nucleation.
   *
   * <p>
   * Also automatically enables heterogeneous mode if not already set.
   * </p>
   *
   * @param angleDegrees contact angle in degrees (0 to 180). 0 = complete wetting (no barrier), 90
   *        = partial wetting (half barrier), 180 = non-wetting (same as homogeneous).
   */
  public void setContactAngle(double angleDegrees) {
    this.contactAngleDegrees = Math.max(0.0, Math.min(180.0, angleDegrees));
    this.contactAngleFactor = heterogeneousContactAngleFactor(this.contactAngleDegrees);
    this.calculated = false;
  }

  /**
   * Returns the contact angle in degrees.
   *
   * @return contact angle in degrees
   */
  public double getContactAngle() {
    return contactAngleDegrees;
  }

  /**
   * Returns the contact angle factor f(theta). This multiplies the homogeneous barrier to give the
   * heterogeneous barrier.
   *
   * @return contact angle factor (0 to 1)
   */
  public double getContactAngleFactor() {
    return contactAngleFactor;
  }

  /**
   * Computes the Fletcher shape factor for heterogeneous nucleation.
   *
   * <p>
   * f(theta) = (2 - 3*cos(theta) + cos^3(theta)) / 4
   * </p>
   *
   * <p>
   * This factor reduces the nucleation barrier when a substrate surface is present. Physical
   * meaning:
   * </p>
   * <ul>
   * <li>f(0) = 0: complete wetting, no barrier, barrierless nucleation</li>
   * <li>f(90) = 0.5: partial wetting, half the homogeneous barrier</li>
   * <li>f(180) = 1.0: non-wetting, same as homogeneous nucleation</li>
   * </ul>
   *
   * @param angleDegrees contact angle in degrees (0 to 180)
   * @return shape factor f(theta) in range [0, 1]
   */
  public static double heterogeneousContactAngleFactor(double angleDegrees) {
    double thetaRad = Math.toRadians(angleDegrees);
    double cosTheta = Math.cos(thetaRad);
    return (2.0 - 3.0 * cosTheta + cosTheta * cosTheta * cosTheta) / 4.0;
  }

  // ============================================================================
  // Main Calculation
  // ============================================================================

  /**
   * Performs the full nucleation, growth, and coagulation calculation.
   *
   * <p>
   * Calculates in order:
   * </p>
   * <ol>
   * <li>Supersaturation ratio (from pressures or direct setting)</li>
   * <li>Critical nucleus radius (Kelvin equation)</li>
   * <li>Free energy barrier and Zeldovich factor</li>
   * <li>Homogeneous nucleation rate (Becker-Doering)</li>
   * <li>Condensation growth rate (free-molecular and continuum with Fuchs interpolation)</li>
   * <li>Brownian coagulation kernel (Smoluchowski)</li>
   * <li>Particle size evolution over residence time</li>
   * </ol>
   */
  public void calculate() {
    // 1. Determine supersaturation
    if (actualPartialPressure > 0.0 && saturationPressure > 0.0) {
      supersaturationRatio = actualPartialPressure / saturationPressure;
    }

    if (supersaturationRatio <= 1.0) {
      // No supersaturation, no nucleation
      resetResults();
      calculated = true;
      return;
    }

    double lnS = Math.log(supersaturationRatio);
    double kT = K_BOLTZMANN * temperature;

    // 2. Critical radius (Kelvin/Thomson equation)
    // r* = 2 * gamma * v_m / (kT * ln(S))
    criticalRadius = 2.0 * surfaceTension * molecularVolume / (kT * lnS);

    // 3. Number of molecules in critical nucleus
    // n* = (4/3) * pi * r*^3 / v_m
    criticalNucleusMolecules =
        (4.0 * Math.PI / 3.0) * Math.pow(criticalRadius, 3) / molecularVolume;

    // 4. Free energy barrier
    // DeltaG* = (16*pi/3) * gamma^3 * v_m^2 / (kT * ln(S))^2
    double homogeneousBarrier = (16.0 * Math.PI / 3.0) * Math.pow(surfaceTension, 3)
        * Math.pow(molecularVolume, 2) / Math.pow(kT * lnS, 2);

    // Apply heterogeneous nucleation correction if enabled
    // DeltaG*_het = f(theta) * DeltaG*_hom
    if (heterogeneous) {
      freeEnergyBarrier = contactAngleFactor * homogeneousBarrier;
    } else {
      freeEnergyBarrier = homogeneousBarrier;
    }

    dimensionlessFreeEnergyBarrier = freeEnergyBarrier / kT;

    // 5. Zeldovich non-equilibrium factor
    // Z = sqrt(DeltaG* / (3*pi*kT*n*^2))
    if (criticalNucleusMolecules > 0.0) {
      zeldovichFactor = Math.sqrt(freeEnergyBarrier
          / (3.0 * Math.PI * kT * criticalNucleusMolecules * criticalNucleusMolecules));
    } else {
      zeldovichFactor = 0.01;
    }

    // 6. Nucleation rate (Becker-Doering / CNT)
    // J = Z * k * c_1^2 * exp(-DeltaG*/kT)
    // where k = monomer-cluster collision rate constant (m3/s)
    // k = alpha * sqrt(kT/(2*pi*m)) * 4*pi*r*^2 (Hertz-Knudsen kinetic theory)
    double vaporConcentration;
    if (actualPartialPressure > 0) {
      vaporConcentration = actualPartialPressure / kT;
    } else {
      // Estimate from supersaturation and a reference concentration
      vaporConcentration = supersaturationRatio * 1e20;
    }

    // Collision rate constant: k = alpha * sqrt(kT / (2*pi*m)) * 4*pi*r*^2
    double collisionRateConstant =
        stickingCoefficient * Math.sqrt(K_BOLTZMANN * temperature / (2.0 * Math.PI * molecularMass))
            * 4.0 * Math.PI * Math.pow(criticalRadius, 2);

    double exponent = -dimensionlessFreeEnergyBarrier;
    if (exponent < -700.0) {
      nucleationRate = 0.0;
    } else {
      nucleationRate = zeldovichFactor * collisionRateConstant * vaporConcentration
          * vaporConcentration * Math.exp(exponent);
    }

    // Cap nucleation rate at physically reasonable maximum
    if (nucleationRate > 1e35) {
      nucleationRate = 1e35;
    }

    // 7. Estimate mean free path if not set
    if (meanFreePath <= 0.0) {
      meanFreePath = estimateMeanFreePath();
    }

    // 8. Condensation growth rates
    calculateGrowthRates();

    // 9. Coagulation kernel
    calculateCoagulationKernel();

    // 10. Particle evolution over residence time
    calculateParticleEvolution();

    calculated = true;
  }

  // ============================================================================
  // Internal Calculation Methods
  // ============================================================================

  /**
   * Estimates the mean free path of gas molecules from kinetic theory.
   *
   * @return mean free path in m
   */
  private double estimateMeanFreePath() {
    // lambda = kT / (sqrt(2) * pi * d_gas^2 * P)
    // For methane-like gas, effective diameter ~ 3.8 Angstrom = 3.8e-10 m
    double dGas = 3.8e-10;
    double lambda =
        K_BOLTZMANN * temperature / (Math.sqrt(2.0) * Math.PI * dGas * dGas * totalPressure);
    return lambda;
  }

  /**
   * Calculates condensation growth rates in free-molecular and continuum regimes, with Fuchs
   * interpolation for the transition regime.
   */
  private void calculateGrowthRates() {
    double kT = K_BOLTZMANN * temperature;
    double excessPressure;
    if (actualPartialPressure > 0 && saturationPressure > 0) {
      excessPressure = actualPartialPressure - saturationPressure;
    } else {
      excessPressure = saturationPressure * (supersaturationRatio - 1.0);
      if (excessPressure <= 0) {
        excessPressure = 1e-5;
      }
    }

    // Free molecular regime: dr/dt = alpha * v_m * deltaP / sqrt(2*pi*m*kT)
    growthRateFreeM = stickingCoefficient * molecularVolume * excessPressure
        / Math.sqrt(2.0 * Math.PI * molecularMass * kT);

    // Continuum (diffusion-limited) regime: dr/dt = D * M * deltaP / (rho_p * R * T * r)
    // At r = criticalRadius
    if (criticalRadius > 0.0) {
      growthRateContinuum = gasDiffusivity * molecularWeight * excessPressure
          / (condensedPhaseDensity * R_GAS * temperature * criticalRadius);
    } else {
      growthRateContinuum = growthRateFreeM;
    }

    // Fuchs interpolation for transition regime
    // Effective growth rate using Fuchs-Sutugin correction:
    // dr/dt = dr/dt_continuum * f(Kn)
    // where f(Kn) = (1 + Kn) / (1 + (4/(3*alpha)) * Kn + Kn^2 * (4/(3*alpha)))
    if (criticalRadius > 0.0) {
      double knAtCritical = meanFreePath / criticalRadius;
      double fuchsCorrection = fuchsSutuginCorrection(knAtCritical);
      growthRate = growthRateContinuum * fuchsCorrection;
    } else {
      growthRate = growthRateFreeM;
    }

    // Ensure growth rate is positive and physically bounded
    growthRate = Math.max(0.0, growthRate);
    if (growthRate > 1.0) {
      growthRate = 1.0;
    }
  }

  /**
   * Calculates the Fuchs-Sutugin transition regime correction factor.
   *
   * <p>
   * The correction smoothly interpolates between free-molecular (Kn much greater than 1) and
   * continuum (Kn much less than 1) regimes.
   * </p>
   *
   * @param kn Knudsen number (mean free path / particle radius)
   * @return correction factor (approaches 1 in continuum, &gt; 1 in free molecular)
   */
  private double fuchsSutuginCorrection(double kn) {
    // Fuchs-Sutugin: correction = (1 + Kn) / (1 + (4/(3*alpha))*Kn + (4/(3*alpha))*Kn^2)
    double c = 4.0 / (3.0 * stickingCoefficient);
    return (1.0 + kn) / (1.0 + c * kn + c * kn * kn);
  }

  /**
   * Calculates the Brownian coagulation kernel (Smoluchowski).
   */
  private void calculateCoagulationKernel() {
    if (gasViscosity > 0.0) {
      // Continuum regime: K = 8 * kT / (3 * mu)
      coagulationKernel = 8.0 * K_BOLTZMANN * temperature / (3.0 * gasViscosity);
    }
  }

  /**
   * Calculates particle size evolution over the residence time, including condensation growth and
   * coagulation.
   */
  private void calculateParticleEvolution() {
    // Initial number of particles formed per m3
    // N0 = J * dt_nucleation (where nucleation occurs primarily in early residence time)
    // Simplified: assume nucleation occurs for first 10% of residence time,
    // then growth and coagulation dominate
    double nucleationTime = residenceTime * 0.1;
    double growthTime = residenceTime * 0.9;

    double n0 = nucleationRate * nucleationTime;
    if (n0 < 1.0) {
      n0 = 1.0;
    }

    // Cap at physically realistic maximum
    if (n0 > 1e25) {
      n0 = 1e25;
    }

    // Condensation growth: particle radius increases
    // In free-molecular regime (small particles): r(t) = r* + G_fm * t
    // In continuum regime: r(t)^2 = r*^2 + 2 * G_cont * r * t
    // Use appropriate regime based on Knudsen number
    double r0 = criticalRadius;
    if (r0 < 1e-10) {
      r0 = 1e-10;
    }

    double rGrown;
    double kn0 = meanFreePath / r0;
    if (kn0 > 10.0) {
      // Free molecular: linear growth
      rGrown = r0 + growthRate * growthTime;
    } else if (kn0 < 0.1) {
      // Continuum: parabolic growth r^2 = r0^2 + 2*G*r0*t
      rGrown = Math.sqrt(r0 * r0 + 2.0 * growthRate * r0 * growthTime);
    } else {
      // Transition regime: approximate with linear growth using Fuchs rate
      rGrown = r0 + growthRate * growthTime;
    }

    // Coagulation: N(t) = N0 / (1 + K*N0*t/2)
    // Mean volume increases: V_mean(t) = V_mean_0 * N0/N(t)
    double nt = n0;
    if (coagulationKernel > 0.0 && n0 > 1.0) {
      nt = n0 / (1.0 + coagulationKernel * n0 * growthTime / 2.0);
      if (nt < 1.0) {
        nt = 1.0;
      }
      // Volume conservation: total volume = N0*v0 = Nt*vt
      // vt/v0 = N0/Nt => r_coag = r * (N0/Nt)^(1/3)
      double volumeRatio = n0 / nt;
      rGrown = rGrown * Math.pow(volumeRatio, 1.0 / 3.0);
    }

    meanParticleRadius = rGrown;
    meanParticleDiameter = 2.0 * rGrown;
    particleNumberDensity = nt;
    knudsenNumber = meanFreePath / meanParticleRadius;

    // Geometric standard deviation estimate
    // Self-preserving distribution for coagulation: sigma_g ~ 1.32-1.5
    // For nucleation + growth dominated: sigma_g ~ 1.2-1.4
    if (n0 > 1e10 && coagulationKernel > 0) {
      geometricStdDev = 1.45;
    } else {
      geometricStdDev = 1.30;
    }

    // Mass concentration = N * (4/3)*pi*r^3 * rho_p
    double singleParticleVolume = (4.0 * Math.PI / 3.0) * Math.pow(meanParticleRadius, 3);
    particleMassConcentration = nt * singleParticleVolume * condensedPhaseDensity;
  }

  /**
   * Resets all calculated results to zero.
   */
  private void resetResults() {
    criticalRadius = 0.0;
    criticalNucleusMolecules = 0.0;
    freeEnergyBarrier = 0.0;
    dimensionlessFreeEnergyBarrier = 0.0;
    zeldovichFactor = 0.0;
    nucleationRate = 0.0;
    growthRate = 0.0;
    growthRateFreeM = 0.0;
    growthRateContinuum = 0.0;
    coagulationKernel = 0.0;
    meanParticleRadius = 0.0;
    meanParticleDiameter = 0.0;
    particleNumberDensity = 0.0;
    knudsenNumber = 0.0;
    particleMassConcentration = 0.0;
  }

  // ============================================================================
  // Convenience Methods
  // ============================================================================

  /**
   * Calculates the mean particle diameter for a given supersaturation and residence time. This is a
   * convenience method that sets both parameters and runs the calculation.
   *
   * @param supersaturation supersaturation ratio (S &gt; 1)
   * @param residenceTimeSec residence time in seconds
   * @return mean particle diameter in m
   */
  public double calculateParticleDiameter(double supersaturation, double residenceTimeSec) {
    this.supersaturationRatio = supersaturation;
    this.residenceTime = residenceTimeSec;
    this.calculated = false;
    calculate();
    return meanParticleDiameter;
  }

  /**
   * Returns the mean particle diameter in the specified unit.
   *
   * @param unit size unit: "m", "mm", "um" (micrometres), "nm" (nanometres)
   * @return mean particle diameter in the specified unit
   */
  public double getMeanParticleDiameter(String unit) {
    if ("mm".equals(unit)) {
      return meanParticleDiameter * 1e3;
    } else if ("um".equals(unit)) {
      return meanParticleDiameter * 1e6;
    } else if ("nm".equals(unit)) {
      return meanParticleDiameter * 1e9;
    }
    return meanParticleDiameter;
  }

  /**
   * Returns particle sizes at the 10th, 50th, and 90th percentiles of the estimated lognormal size
   * distribution.
   *
   * @return array [d10, d50, d90] in m
   */
  public double[] getParticleSizePercentiles() {
    double d50 = meanParticleDiameter;
    double lnSigma = Math.log(geometricStdDev);
    double d10 = d50 * Math.exp(-1.282 * lnSigma);
    double d90 = d50 * Math.exp(1.282 * lnSigma);
    return new double[] {d10, d50, d90};
  }

  /**
   * Returns the fraction of particles smaller than a given diameter, assuming lognormal
   * distribution.
   *
   * @param diameterM threshold diameter in m
   * @return fraction (0 to 1) of particles smaller than the threshold
   */
  public double getFractionSmallerThan(double diameterM) {
    if (meanParticleDiameter <= 0.0 || diameterM <= 0.0) {
      return 0.0;
    }
    double z = Math.log(diameterM / meanParticleDiameter) / Math.log(geometricStdDev);
    return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
  }

  /**
   * Returns the fraction of particles larger than the filter rating. This indicates what fraction
   * would be captured by a filter with the given rating.
   *
   * @param filterRatingM filter rating in m (e.g., 10e-6 for 10 um)
   * @return capture fraction (0 to 1)
   */
  public double getFilterCaptureEfficiency(double filterRatingM) {
    return 1.0 - getFractionSmallerThan(filterRatingM);
  }

  /**
   * Approximation of the error function using Abramowitz and Stegun formula 7.1.26.
   *
   * @param x input value
   * @return erf(x)
   */
  private static double erf(double x) {
    double sign = x < 0 ? -1.0 : 1.0;
    x = Math.abs(x);
    double t = 1.0 / (1.0 + 0.3275911 * x);
    double poly = t * (0.254829592
        + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
    return sign * (1.0 - poly * Math.exp(-x * x));
  }

  // ============================================================================
  // Getters for Results
  // ============================================================================

  /**
   * Returns the condensed phase density.
   *
   * @return condensed phase density in kg/m3
   */
  public double getCondensedPhaseDensity() {
    return condensedPhaseDensity;
  }

  /**
   * Returns the critical nucleus radius.
   *
   * @return critical radius in m
   */
  public double getCriticalRadius() {
    return criticalRadius;
  }

  /**
   * Returns the number of molecules in the critical nucleus.
   *
   * @return number of molecules in the critical cluster
   */
  public double getCriticalNucleusMolecules() {
    return criticalNucleusMolecules;
  }

  /**
   * Returns the free energy barrier for nucleation.
   *
   * @return Gibbs free energy barrier in Joules
   */
  public double getFreeEnergyBarrier() {
    return freeEnergyBarrier;
  }

  /**
   * Returns the dimensionless free energy barrier (Delta G* / kT).
   *
   * @return dimensionless barrier height
   */
  public double getDimensionlessFreeEnergyBarrier() {
    return dimensionlessFreeEnergyBarrier;
  }

  /**
   * Returns the Zeldovich non-equilibrium correction factor.
   *
   * @return Zeldovich factor (dimensionless)
   */
  public double getZeldovichFactor() {
    return zeldovichFactor;
  }

  /**
   * Returns the homogeneous nucleation rate.
   *
   * @return nucleation rate in particles/(m3*s)
   */
  public double getNucleationRate() {
    return nucleationRate;
  }

  /**
   * Returns the condensation growth rate at the critical radius.
   *
   * @return growth rate in m/s
   */
  public double getGrowthRate() {
    return growthRate;
  }

  /**
   * Returns the Brownian coagulation kernel.
   *
   * @return coagulation kernel in m3/s
   */
  public double getCoagulationKernel() {
    return coagulationKernel;
  }

  /**
   * Returns the mean particle radius after residence time.
   *
   * @return mean particle radius in m
   */
  public double getMeanParticleRadius() {
    return meanParticleRadius;
  }

  /**
   * Returns the mean particle diameter after residence time.
   *
   * @return mean particle diameter in m
   */
  public double getMeanParticleDiameter() {
    return meanParticleDiameter;
  }

  /**
   * Returns the particle number density after residence time.
   *
   * @return number density in particles/m3
   */
  public double getParticleNumberDensity() {
    return particleNumberDensity;
  }

  /**
   * Returns the Knudsen number at the mean particle radius.
   *
   * @return Knudsen number (dimensionless)
   */
  public double getKnudsenNumber() {
    return knudsenNumber;
  }

  /**
   * Returns the estimated geometric standard deviation of the size distribution.
   *
   * @return geometric standard deviation
   */
  public double getGeometricStdDev() {
    return geometricStdDev;
  }

  /**
   * Returns the supersaturation ratio used in the calculation.
   *
   * @return supersaturation ratio S
   */
  public double getSupersaturationRatio() {
    return supersaturationRatio;
  }

  /**
   * Returns the particle mass concentration.
   *
   * @return mass concentration in kg/m3
   */
  public double getParticleMassConcentration() {
    return particleMassConcentration;
  }

  /**
   * Returns whether the calculation has been performed.
   *
   * @return true if calculate() has been called successfully
   */
  public boolean isCalculated() {
    return calculated;
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * Returns all results as a Map for serialization.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("substanceName", substanceName);

    Map<String, Object> substance = new LinkedHashMap<String, Object>();
    substance.put("molecularWeight_kg_mol", molecularWeight);
    substance.put("condensedPhaseDensity_kg_m3", condensedPhaseDensity);
    substance.put("surfaceTension_N_m", surfaceTension);
    substance.put("molecularVolume_m3", molecularVolume);
    result.put("substanceProperties", substance);

    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperature_K", temperature);
    conditions.put("temperature_C", temperature - 273.15);
    conditions.put("supersaturationRatio", supersaturationRatio);
    conditions.put("residenceTime_s", residenceTime);
    conditions.put("gasDiffusivity_m2_s", gasDiffusivity);
    conditions.put("gasViscosity_Pa_s", gasViscosity);
    conditions.put("meanFreePath_m", meanFreePath);
    conditions.put("totalPressure_Pa", totalPressure);
    result.put("processConditions", conditions);

    Map<String, Object> nucleation = new LinkedHashMap<String, Object>();
    nucleation.put("heterogeneous", heterogeneous);
    if (heterogeneous) {
      nucleation.put("contactAngle_degrees", contactAngleDegrees);
      nucleation.put("contactAngleFactor_fTheta", contactAngleFactor);
    }
    nucleation.put("criticalRadius_m", criticalRadius);
    nucleation.put("criticalRadius_nm", criticalRadius * 1e9);
    nucleation.put("criticalNucleusMolecules", criticalNucleusMolecules);
    nucleation.put("freeEnergyBarrier_J", freeEnergyBarrier);
    nucleation.put("freeEnergyBarrier_kT", dimensionlessFreeEnergyBarrier);
    nucleation.put("zeldovichFactor", zeldovichFactor);
    nucleation.put("nucleationRate_per_m3_s", nucleationRate);
    result.put("nucleation", nucleation);

    Map<String, Object> growth = new LinkedHashMap<String, Object>();
    growth.put("growthRate_m_s", growthRate);
    growth.put("growthRateFreeMolecular_m_s", growthRateFreeM);
    growth.put("growthRateContinuum_m_s", growthRateContinuum);
    growth.put("coagulationKernel_m3_s", coagulationKernel);
    result.put("growth", growth);

    Map<String, Object> particles = new LinkedHashMap<String, Object>();
    particles.put("meanDiameter_m", meanParticleDiameter);
    particles.put("meanDiameter_um", meanParticleDiameter * 1e6);
    particles.put("meanDiameter_nm", meanParticleDiameter * 1e9);
    particles.put("numberDensity_per_m3", particleNumberDensity);
    particles.put("massConcentration_kg_m3", particleMassConcentration);
    particles.put("massConcentration_mg_m3", particleMassConcentration * 1e6);
    particles.put("knudsenNumber", knudsenNumber);
    particles.put("geometricStdDev", geometricStdDev);

    double[] pctiles = getParticleSizePercentiles();
    particles.put("d10_um", pctiles[0] * 1e6);
    particles.put("d50_um", pctiles[1] * 1e6);
    particles.put("d90_um", pctiles[2] * 1e6);
    result.put("particleResults", particles);

    return result;
  }

  /**
   * Returns a JSON report of all nucleation and particle results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(toMap());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!calculated) {
      return "ClassicalNucleationTheory [not calculated]";
    }
    return String.format("CNT[%s]: S=%.1f, r*=%.1f nm, d_mean=%.2f um, J=%.2e /m3s, N=%.2e /m3",
        substanceName, supersaturationRatio, criticalRadius * 1e9, meanParticleDiameter * 1e6,
        nucleationRate, particleNumberDensity);
  }
}
