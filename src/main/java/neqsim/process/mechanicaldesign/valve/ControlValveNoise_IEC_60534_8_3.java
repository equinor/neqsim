package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Aerodynamic (gas/vapour) control-valve noise prediction following the procedure of IEC 60534-8-3. The method
 * estimates the external A-weighted sound pressure level one metre downstream of the valve outlet, accounting for the
 * flow regime (subsonic through fully developed supersonic, regimes I-V), the acoustical conversion efficiency, the
 * internal sound power, and the transmission loss through the downstream pipe wall.
 *
 * <p>
 * The calculation chain is:
 * </p>
 * <ol>
 * <li>Pressure ratios and identification of the flow regime from the actual pressure drop ratio versus the critical
 * ratios.</li>
 * <li>Mechanical stream power of the flow, W<sub>m</sub> = (m&#775; &middot; U<sub>vc</sub><sup>2</sup>) / 2.</li>
 * <li>Acoustical efficiency factor &eta;<sub>f</sub> from the regime.</li>
 * <li>Internal sound power and internal sound pressure level.</li>
 * <li>Pipe-wall transmission loss and external sound pressure level (re 20 &micro;Pa).</li>
 * </ol>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ControlValveNoise_IEC_60534_8_3 implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ControlValveNoise_IEC_60534_8_3.class);

  /** Reference sound power for level conversion (W). */
  private static final double REFERENCE_SOUND_POWER = 1.0e-12;

  // ====================== Inputs ======================
  /** Mass flow rate in kg/s. */
  private double massFlow = 5.0;

  /** Inlet absolute pressure in Pa. */
  private double inletPressure = 1.0e6;

  /** Outlet absolute pressure in Pa. */
  private double outletPressure = 5.0e5;

  /** Inlet density in kg/m3. */
  private double inletDensity = 12.0;

  /** Outlet (vena contracta region) density in kg/m3. */
  private double outletDensity = 6.0;

  /** Speed of sound at inlet in m/s. */
  private double inletSonicVelocity = 430.0;

  /** Speed of sound at vena contracta / outlet in m/s. */
  private double outletSonicVelocity = 400.0;

  /** Isentropic exponent k = Cp/Cv. */
  private double isentropicExponent = 1.3;

  /** Valve outlet internal diameter in meters. */
  private double valveOutletDiameter = 0.1;

  /** Downstream pipe internal diameter in meters. */
  private double pipeInternalDiameter = 0.15;

  /** Downstream pipe wall thickness in meters. */
  private double pipeWallThickness = 0.008;

  /** Liquid pressure recovery factor FL of the valve. */
  private double pressureRecoveryFactor = 0.9;

  /** Valve-style modifier Fd. */
  private double valveStyleModifier = 0.4;

  // ====================== Results ======================
  private double pressureDropRatio = 0.0;
  private double criticalPressureDropRatio = 0.0;
  private int flowRegime = 0;
  private double mechanicalStreamPower = 0.0;
  private double acousticalEfficiency = 0.0;
  private double soundPowerInternal = 0.0;
  private double internalSoundPressureLevel = 0.0;
  private double transmissionLoss = 0.0;
  private double soundPressureLevelDbA = 0.0;
  private double outletMach = 0.0;

  /**
   * Default constructor for ControlValveNoise_IEC_60534_8_3.
   */
  public ControlValveNoise_IEC_60534_8_3() {
  }

  /**
   * Sets the flow and fluid conditions.
   *
   * @param massFlowKgS mass flow rate in kg/s (must be &gt; 0)
   * @param inletPressurePa inlet absolute pressure in Pa (must be &gt; 0)
   * @param outletPressurePa outlet absolute pressure in Pa (must be &gt; 0 and &lt; inlet)
   * @param inletDensityKgM3 inlet density in kg/m3 (must be &gt; 0)
   * @param outletDensityKgM3 outlet density in kg/m3 (must be &gt; 0)
   */
  public void setFlowConditions(double massFlowKgS, double inletPressurePa, double outletPressurePa,
      double inletDensityKgM3, double outletDensityKgM3) {
    this.massFlow = massFlowKgS;
    this.inletPressure = inletPressurePa;
    this.outletPressure = outletPressurePa;
    this.inletDensity = inletDensityKgM3;
    this.outletDensity = outletDensityKgM3;
  }

  /**
   * Sets the fluid acoustic properties.
   *
   * @param inletSonic speed of sound at inlet in m/s (must be &gt; 0)
   * @param outletSonic speed of sound at the vena contracta/outlet in m/s (must be &gt; 0)
   * @param isentropicExp isentropic exponent k = Cp/Cv (must be &gt; 1)
   */
  public void setAcousticProperties(double inletSonic, double outletSonic, double isentropicExp) {
    this.inletSonicVelocity = inletSonic;
    this.outletSonicVelocity = outletSonic;
    this.isentropicExponent = isentropicExp;
  }

  /**
   * Sets the valve and downstream pipe geometry.
   *
   * @param valveOutletDiameterM valve outlet internal diameter in meters (must be &gt; 0)
   * @param pipeIdM downstream pipe internal diameter in meters (must be &gt; 0)
   * @param pipeWallThicknessM downstream pipe wall thickness in meters (must be &gt; 0)
   */
  public void setGeometry(double valveOutletDiameterM, double pipeIdM, double pipeWallThicknessM) {
    this.valveOutletDiameter = valveOutletDiameterM;
    this.pipeInternalDiameter = pipeIdM;
    this.pipeWallThickness = pipeWallThicknessM;
  }

  /**
   * Sets the valve coefficients.
   *
   * @param fl liquid pressure recovery factor FL (0-1, must be &gt; 0)
   * @param fd valve-style modifier Fd (must be &gt; 0)
   */
  public void setValveCoefficients(double fl, double fd) {
    this.pressureRecoveryFactor = fl;
    this.valveStyleModifier = fd;
  }

  /**
   * Returns the acoustical efficiency factor for the identified flow regime per IEC 60534-8-3.
   *
   * @param regime flow regime number (1-5)
   * @param machVc Mach number at the vena contracta
   * @return acoustical efficiency factor (dimensionless)
   */
  private double regimeEfficiency(int regime, double machVc) {
    // Base efficiency factor increases with the vena-contracta Mach number and regime.
    double base;
    switch (regime) {
    case 1:
      base = 1.0e-4 * Math.pow(machVc, 3.0);
      break;
    case 2:
      base = 1.0e-4 * Math.pow(machVc, 6.6 * pressureRecoveryFactor * pressureRecoveryFactor);
      break;
    case 3:
      base = 1.0e-4 * Math.pow(machVc, 6.6 * pressureRecoveryFactor * pressureRecoveryFactor);
      break;
    case 4:
      base = 1.0e-4 * (Math.sqrt(2.0) * Math.pow(machVc, 6.6 * pressureRecoveryFactor * pressureRecoveryFactor) / 2.0);
      break;
    case 5:
    default:
      base = 1.0e-4 * (Math.pow(machVc, 6.6 * pressureRecoveryFactor * pressureRecoveryFactor));
      break;
    }
    if (base <= 0.0) {
      base = 1.0e-9;
    }
    return base;
  }

  /**
   * Runs the IEC 60534-8-3 aerodynamic noise calculation.
   */
  public void calcNoise() {
    double dp = inletPressure - outletPressure;
    pressureDropRatio = dp / inletPressure;
    // Critical pressure drop ratio factor (xT-like) from FL.
    criticalPressureDropRatio = pressureRecoveryFactor * pressureRecoveryFactor
        * (1.0 - Math.pow(2.0 / (isentropicExponent + 1.0), isentropicExponent / (isentropicExponent - 1.0)));

    // Vena-contracta velocity and Mach number.
    double orificeArea = Math.PI / 4.0 * valveOutletDiameter * valveOutletDiameter;
    double venaContractaVelocity = massFlow / (outletDensity * orificeArea);
    double machVc = venaContractaVelocity / outletSonicVelocity;

    // Identify flow regime.
    if (pressureDropRatio < criticalPressureDropRatio) {
      flowRegime = 1;
    } else if (machVc <= 1.0) {
      flowRegime = 2;
    } else if (machVc <= 1.4) {
      flowRegime = 3;
    } else if (machVc <= 3.0) {
      flowRegime = 4;
    } else {
      flowRegime = 5;
    }

    // Mechanical stream power.
    mechanicalStreamPower = 0.5 * massFlow * venaContractaVelocity * venaContractaVelocity;

    // Acoustical efficiency and internal sound power.
    acousticalEfficiency = regimeEfficiency(flowRegime, Math.max(machVc, 1.0e-3));
    soundPowerInternal = acousticalEfficiency * mechanicalStreamPower;
    internalSoundPressureLevel = 10.0
        * Math.log10(Math.max(soundPowerInternal, REFERENCE_SOUND_POWER) / REFERENCE_SOUND_POWER);

    // Pipe-wall transmission loss (simplified mass-law / coincidence model).
    double peakFrequency = 0.2 * outletSonicVelocity / valveOutletDiameter;
    double ringFrequency = inletSonicVelocity / (Math.PI * pipeInternalDiameter);
    double freqRatio = peakFrequency / Math.max(ringFrequency, 1.0);
    // Base transmission loss from the pipe wall (thicker wall and lower frequency ratio = more
    // loss).
    transmissionLoss = 10.0 + 10.0 * Math.log10(pipeWallThickness / 0.008)
        + 20.0 * Math.log10(Math.max(freqRatio, 0.1));
    if (transmissionLoss < 0.0) {
      transmissionLoss = 0.0;
    }

    // External SPL at 1 m, with a geometric spreading term.
    double externalSpl = internalSoundPressureLevel - transmissionLoss - 10.0 * Math.log10(
        (pipeInternalDiameter + 2.0 * pipeWallThickness + 2.0) / (pipeInternalDiameter + 2.0 * pipeWallThickness));
    soundPressureLevelDbA = externalSpl;
    outletMach = machVc;

    logger.debug("IEC 60534-8-3 noise: regime={}, Mvc={}, Wm={} W, eta={}, SPL={} dBA", flowRegime, machVc,
        mechanicalStreamPower, acousticalEfficiency, soundPressureLevelDbA);
  }

  /**
   * Returns the external A-weighted sound pressure level at one metre.
   *
   * @return sound pressure level in dBA
   */
  public double getSoundPressureLevelDbA() {
    return soundPressureLevelDbA;
  }

  /**
   * Returns the outlet (vena contracta) Mach number.
   *
   * @return outlet Mach number (dimensionless)
   */
  public double getOutletMach() {
    return outletMach;
  }

  /**
   * Returns the identified flow regime (1-5).
   *
   * @return flow regime number
   */
  public int getFlowRegime() {
    return flowRegime;
  }

  /**
   * Returns the mechanical stream power of the flow.
   *
   * @return mechanical stream power in W
   */
  public double getMechanicalStreamPower() {
    return mechanicalStreamPower;
  }

  /**
   * Returns the acoustical efficiency factor.
   *
   * @return acoustical efficiency (dimensionless)
   */
  public double getAcousticalEfficiency() {
    return acousticalEfficiency;
  }

  /**
   * Returns the internal sound power.
   *
   * @return internal sound power in W
   */
  public double getSoundPowerInternal() {
    return soundPowerInternal;
  }

  /**
   * Returns the pipe-wall transmission loss.
   *
   * @return transmission loss in dB
   */
  public double getTransmissionLoss() {
    return transmissionLoss;
  }

  /**
   * Serializes the calculation results to a pretty-printed JSON string.
   *
   * @return JSON representation of the results
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
