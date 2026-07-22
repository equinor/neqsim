package neqsim.process.mechanicaldesign.thermowell;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * Thermowell wake-frequency and strength calculator following the methodology of ASME PTC 19.3 TW-2016 (with a fallback
 * to the older TW-1974 frequency-limit basis).
 *
 * <p>
 * The calculation screens an intrusive thermowell against the four standard acceptance checks:
 * </p>
 * <ol>
 * <li><b>Frequency limit</b> &ndash; the vortex shedding frequency must stay sufficiently below the installed natural
 * frequency of the thermowell so that resonance is avoided.</li>
 * <li><b>Dynamic (oscillating) stress</b> &ndash; the peak alternating bending stress from vortex-induced lift,
 * including resonant magnification, must stay below the allowable fatigue stress amplitude of the material.</li>
 * <li><b>Static stress</b> &ndash; the steady-state bending stress from the drag force plus the axial pressure stress
 * must stay below the allowable static stress.</li>
 * <li><b>Hydrostatic (external pressure) limit</b> &ndash; the process pressure must stay below the pressure rating of
 * the thermowell tip treated as a flat circular plate.</li>
 * </ol>
 *
 * <p>
 * All process inputs (fluid density, velocity, viscosity, pressure) can be taken directly from a NeqSim
 * {@code Stream}/{@code SystemInterface} so that the manual property paste step of the spreadsheet workflow is removed.
 * The geometry uses a representative (mean) shank diameter for the mass term and the root diameter for the section
 * modulus, which is a conservative engineering approximation of the stepped/tapered shank treatment in PTC 19.3.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ThermowellDesignCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ThermowellDesignCalculator.class);

  /** First cantilever-beam eigenvalue (mode 1). */
  private static final double BETA1 = 1.875104068711961;

  // ====================== Geometry inputs ======================
  /** Unsupported (immersion) length of the thermowell in meters. */
  private double length = 0.15;

  /** Root (support) outside diameter in meters. */
  private double rootDiameter = 0.0226;

  /** Tip outside diameter in meters. */
  private double tipDiameter = 0.0226;

  /** Bore (internal) diameter in meters. */
  private double boreDiameter = 0.00665;

  /** Tip (bottom) wall thickness in meters. */
  private double tipThickness = 0.005;

  // ====================== Material inputs ======================
  /** Material density in kg/m3 (e.g. 316SS ~ 7960). */
  private double materialDensity = 7960.0;

  /** Material modulus of elasticity at temperature in Pa (e.g. 316SS ~ 1.93e11). */
  private double elasticModulus = 1.93e11;

  /** Allowable static (primary membrane + bending) stress in Pa. */
  private double allowableStaticStress = 1.38e8;

  /** Allowable fatigue stress amplitude in Pa (PTC 19.3 Class A/B endurance limit). */
  private double allowableFatigueStress = 4.83e7;

  // ====================== Process inputs ======================
  /** Fluid density at the thermowell in kg/m3. */
  private double fluidDensity = 50.0;

  /** Free-stream fluid velocity in m/s. */
  private double fluidVelocity = 20.0;

  /** Fluid dynamic viscosity in Pa-s. */
  private double fluidViscosity = 1.5e-5;

  /** Process pressure in Pa absolute. */
  private double processPressure = 7.0e6;

  // ====================== Correction factors ======================
  /** Foundation/mounting compliance correction (PTC 19.3 Hf), 0-1. */
  private double mountingComplianceFactor = 0.99;

  /** Added fluid mass correction (PTC 19.3 Ha), 0-1. */
  private double fluidMassFactor = 0.97;

  /** Geometry (tapered/stepped shank) correction (PTC 19.3 Hc). */
  private double geometryFactor = 1.0;

  /** Drag coefficient for steady drag force. */
  private double dragCoefficient = 1.4;

  /** Lift coefficient for oscillating (vortex) force. */
  private double liftCoefficient = 1.0;

  // ====================== Results ======================
  private double reynoldsNumber = 0.0;
  private double strouhalNumber = 0.0;
  private double vortexSheddingFrequency = 0.0;
  private double naturalFrequency = 0.0;
  private double installedNaturalFrequency = 0.0;
  private double frequencyRatio = 0.0;
  private double scrutonNumber = 0.0;
  private double magnificationFactor = 0.0;
  private double staticStress = 0.0;
  private double dynamicStress = 0.0;
  private double maxAllowablePressure = 0.0;

  private boolean frequencyCheckPassed = false;
  private boolean dynamicStressCheckPassed = false;
  private boolean staticStressCheckPassed = false;
  private boolean hydrostaticCheckPassed = false;

  /** Frequency ratio acceptance limit per PTC 19.3 TW-2016 (with in-line resonance considered). */
  private double frequencyRatioLimit = 0.8;

  /**
   * Default constructor for ThermowellDesignCalculator.
   */
  public ThermowellDesignCalculator() {
  }

  /**
   * Sets the thermowell geometry.
   *
   * @param lengthM unsupported length in meters (must be &gt; 0)
   * @param rootDiameterM root outside diameter in meters (must be &gt; bore)
   * @param tipDiameterM tip outside diameter in meters (must be &gt; bore)
   * @param boreDiameterM bore diameter in meters (must be &ge; 0)
   * @param tipThicknessM tip wall thickness in meters (must be &gt; 0)
   */
  public void setGeometry(double lengthM, double rootDiameterM, double tipDiameterM, double boreDiameterM,
      double tipThicknessM) {
    this.length = lengthM;
    this.rootDiameter = rootDiameterM;
    this.tipDiameter = tipDiameterM;
    this.boreDiameter = boreDiameterM;
    this.tipThickness = tipThicknessM;
  }

  /**
   * Sets the thermowell material properties.
   *
   * @param density material density in kg/m3 (must be &gt; 0)
   * @param modulusPa modulus of elasticity in Pa (must be &gt; 0)
   * @param allowableStaticPa allowable static stress in Pa (must be &gt; 0)
   * @param allowableFatiguePa allowable fatigue stress amplitude in Pa (must be &gt; 0)
   */
  public void setMaterial(double density, double modulusPa, double allowableStaticPa, double allowableFatiguePa) {
    this.materialDensity = density;
    this.elasticModulus = modulusPa;
    this.allowableStaticStress = allowableStaticPa;
    this.allowableFatigueStress = allowableFatiguePa;
  }

  /**
   * Sets the process (fluid) conditions at the thermowell.
   *
   * @param density fluid density in kg/m3 (must be &gt; 0)
   * @param velocity free-stream velocity in m/s (must be &ge; 0)
   * @param viscosity dynamic viscosity in Pa-s (must be &gt; 0)
   * @param pressurePa process pressure in Pa absolute (must be &ge; 0)
   */
  public void setProcessConditions(double density, double velocity, double viscosity, double pressurePa) {
    this.fluidDensity = density;
    this.fluidVelocity = velocity;
    this.fluidViscosity = viscosity;
    this.processPressure = pressurePa;
  }

  /**
   * Sets the PTC 19.3 frequency-correction factors.
   *
   * @param mountingCompliance foundation/mounting compliance factor Hf (0-1)
   * @param fluidMass added fluid mass factor Ha (0-1)
   * @param geometry tapered/stepped geometry factor Hc
   */
  public void setCorrectionFactors(double mountingCompliance, double fluidMass, double geometry) {
    this.mountingComplianceFactor = mountingCompliance;
    this.fluidMassFactor = fluidMass;
    this.geometryFactor = geometry;
  }

  /**
   * Computes the Strouhal number as a function of Reynolds number using the standard subcritical plateau value with the
   * PTC 19.3 low-Reynolds correction.
   *
   * @param re Reynolds number (must be &gt; 0)
   * @return Strouhal number (dimensionless)
   */
  private double computeStrouhal(double re) {
    if (re < 1.0) {
      return 0.2;
    }
    // Subcritical bluff-body plateau ~0.22 with a mild Reynolds correction.
    double s = 0.22 * (1.0 - 19.7 / re);
    if (s < 0.18) {
      s = 0.18;
    }
    if (s > 0.22) {
      s = 0.22;
    }
    return s;
  }

  /**
   * Runs the full thermowell calculation and the four PTC 19.3 acceptance checks.
   */
  public void calcAll() {
    // --- Section properties ---
    double meanDiameter = 0.5 * (rootDiameter + tipDiameter);
    double area = Math.PI / 4.0 * (meanDiameter * meanDiameter - boreDiameter * boreDiameter);
    double inertiaRoot = Math.PI / 64.0 * (Math.pow(rootDiameter, 4) - Math.pow(boreDiameter, 4));
    double massPerLength = materialDensity * area;

    // --- Vortex shedding (use tip diameter as the characteristic dimension) ---
    reynoldsNumber = fluidDensity * fluidVelocity * tipDiameter / fluidViscosity;
    strouhalNumber = computeStrouhal(reynoldsNumber);
    vortexSheddingFrequency = strouhalNumber * fluidVelocity / tipDiameter;

    // --- Natural frequency (cantilever, mode 1) with PTC 19.3 corrections ---
    naturalFrequency = (BETA1 * BETA1 / (2.0 * Math.PI))
        * Math.sqrt(elasticModulus * inertiaRoot / (massPerLength * Math.pow(length, 4)));
    installedNaturalFrequency = mountingComplianceFactor * fluidMassFactor * geometryFactor * naturalFrequency;

    frequencyRatio = installedNaturalFrequency > 0.0 ? vortexSheddingFrequency / installedNaturalFrequency : 0.0;
    frequencyCheckPassed = frequencyRatio < frequencyRatioLimit;

    // --- Scruton number (mass-damping parameter), assume structural damping ratio 0.0005 ---
    double dampingRatio = 0.0005;
    scrutonNumber = 4.0 * Math.PI * massPerLength * dampingRatio / (fluidDensity * tipDiameter * tipDiameter);

    // --- Resonant magnification factor (limited at resonance by damping) ---
    double fr2 = frequencyRatio * frequencyRatio;
    if (frequencyRatio < 0.999) {
      magnificationFactor = 1.0 / (1.0 - fr2);
    } else {
      magnificationFactor = 1.0 / (2.0 * dampingRatio);
    }
    if (magnificationFactor > 1.0 / (2.0 * dampingRatio)) {
      magnificationFactor = 1.0 / (2.0 * dampingRatio);
    }

    // --- Static (steady drag) bending stress at the root ---
    double dynamicPressure = 0.5 * fluidDensity * fluidVelocity * fluidVelocity;
    double dragForcePerLength = dragCoefficient * dynamicPressure * tipDiameter;
    double dragMoment = dragForcePerLength * length * length / 2.0;
    double sectionModulus = inertiaRoot / (0.5 * rootDiameter);
    double dragBendingStress = sectionModulus > 0.0 ? dragMoment / sectionModulus : 0.0;

    // Axial pressure stress on the closed tip.
    double axialPressureStress = processPressure * (tipDiameter * tipDiameter)
        / (rootDiameter * rootDiameter - boreDiameter * boreDiameter);
    staticStress = dragBendingStress + axialPressureStress;
    staticStressCheckPassed = staticStress < allowableStaticStress;

    // --- Dynamic (oscillating lift) bending stress, amplified at resonance ---
    double liftForcePerLength = liftCoefficient * dynamicPressure * tipDiameter;
    double liftMoment = liftForcePerLength * length * length / 2.0;
    double liftBendingStress = sectionModulus > 0.0 ? liftMoment / sectionModulus : 0.0;
    dynamicStress = liftBendingStress * magnificationFactor;
    dynamicStressCheckPassed = dynamicStress < allowableFatigueStress;

    // --- Hydrostatic (external pressure) limit: tip as a flat circular plate ---
    double tipRadius = 0.5 * tipDiameter;
    if (tipRadius > 0.0) {
      maxAllowablePressure = 1.1 * allowableStaticStress * (tipThickness * tipThickness) / (tipRadius * tipRadius);
    } else {
      maxAllowablePressure = 0.0;
    }
    hydrostaticCheckPassed = processPressure < maxAllowablePressure;

    logger.debug("Thermowell calc: Re={}, fs={} Hz, fnc={} Hz, fr={}", reynoldsNumber, vortexSheddingFrequency,
        installedNaturalFrequency, frequencyRatio);
  }

  /**
   * Returns whether all four PTC 19.3 acceptance checks pass.
   *
   * @return true if the thermowell design is acceptable
   */
  public boolean isDesignAcceptable() {
    return frequencyCheckPassed && dynamicStressCheckPassed && staticStressCheckPassed && hydrostaticCheckPassed;
  }

  /**
   * Returns the calculated Reynolds number.
   *
   * @return Reynolds number (dimensionless)
   */
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /**
   * Returns the calculated Strouhal number.
   *
   * @return Strouhal number (dimensionless)
   */
  public double getStrouhalNumber() {
    return strouhalNumber;
  }

  /**
   * Returns the vortex shedding frequency.
   *
   * @return vortex shedding frequency in Hz
   */
  public double getVortexSheddingFrequency() {
    return vortexSheddingFrequency;
  }

  /**
   * Returns the uncorrected natural frequency.
   *
   * @return natural frequency in Hz
   */
  public double getNaturalFrequency() {
    return naturalFrequency;
  }

  /**
   * Returns the installed (corrected) natural frequency.
   *
   * @return installed natural frequency in Hz
   */
  public double getInstalledNaturalFrequency() {
    return installedNaturalFrequency;
  }

  /**
   * Returns the frequency ratio (shedding frequency / installed natural frequency).
   *
   * @return frequency ratio (dimensionless)
   */
  public double getFrequencyRatio() {
    return frequencyRatio;
  }

  /**
   * Returns the Scruton number (mass-damping parameter).
   *
   * @return Scruton number (dimensionless)
   */
  public double getScrutonNumber() {
    return scrutonNumber;
  }

  /**
   * Returns the calculated static stress at the root.
   *
   * @return static stress in Pa
   */
  public double getStaticStress() {
    return staticStress;
  }

  /**
   * Returns the calculated peak dynamic (oscillating) stress.
   *
   * @return dynamic stress in Pa
   */
  public double getDynamicStress() {
    return dynamicStress;
  }

  /**
   * Returns the maximum allowable external (hydrostatic) pressure of the tip.
   *
   * @return maximum allowable pressure in Pa
   */
  public double getMaxAllowablePressure() {
    return maxAllowablePressure;
  }

  /**
   * Returns whether the frequency-limit check passed.
   *
   * @return true if shedding frequency is sufficiently below the natural frequency
   */
  public boolean isFrequencyCheckPassed() {
    return frequencyCheckPassed;
  }

  /**
   * Returns whether the dynamic-stress check passed.
   *
   * @return true if the dynamic stress is below the allowable fatigue stress
   */
  public boolean isDynamicStressCheckPassed() {
    return dynamicStressCheckPassed;
  }

  /**
   * Returns whether the static-stress check passed.
   *
   * @return true if the static stress is below the allowable static stress
   */
  public boolean isStaticStressCheckPassed() {
    return staticStressCheckPassed;
  }

  /**
   * Returns whether the hydrostatic (external pressure) check passed.
   *
   * @return true if the process pressure is below the tip pressure rating
   */
  public boolean isHydrostaticCheckPassed() {
    return hydrostaticCheckPassed;
  }

  /**
   * Sets the frequency-ratio acceptance limit (PTC 19.3 TW-2016 default 0.8; use 0.4 for the conservative TW-1974
   * basis).
   *
   * @param limit frequency-ratio limit (must be &gt; 0)
   */
  public void setFrequencyRatioLimit(double limit) {
    this.frequencyRatioLimit = limit;
  }

  /**
   * Serializes the calculated results to a pretty-printed JSON string.
   *
   * @return JSON representation of the calculation results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
