package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Compressible (gas/vapour) control-valve sizing per IEC 60534-2-1.
 *
 * <p>
 * Computes the required flow coefficient (Kv and Cv) for a control valve in compressible service, including:
 * </p>
 *
 * <ul>
 * <li>pressure-drop ratio x = &Delta;p / p1;</li>
 * <li>specific-heat-ratio factor F&gamma; = &gamma; / 1.40;</li>
 * <li>choked-flow detection from the terminal pressure-drop ratio factor xT;</li>
 * <li>expansion factor Y = 1 - x / (3 &middot; F&gamma; &middot; xT), bounded to 2/3;</li>
 * <li>piping geometry factor Fp.</li>
 * </ul>
 *
 * <p>
 * The mass-flow sizing equation is W = N6 &middot; Fp &middot; C &middot; Y &middot; sqrt(x &middot; p1 &middot;
 * &rho;1), solved for the required flow coefficient C (= Kv). With W in kg/h, p1 in bar (absolute) and &rho;1 in kg/m3,
 * the numerical constant N6 = 27.3.
 * </p>
 *
 * <p>
 * This is a standalone sizing calculator and is a companion to {@link ControlValveNoise_IEC_60534_8_3}. It does not
 * modify a process valve; the resulting Kv/Cv can be applied separately via {@code ThrottlingValve.setCv()}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ControlValveGasSizing_IEC_60534_2_1 implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(ControlValveGasSizing_IEC_60534_2_1.class);

  /** Numerical constant N6 for W in kg/h, p in bar(a), density in kg/m3. */
  private static final double N6 = 27.3;

  /** Ratio of flow coefficients Kv/Cv (Kv = 0.865 * Cv). */
  private static final double KV_PER_CV = 0.865;

  // ===== Inputs =====
  /** Mass flow rate in kg/h. */
  private double massFlow = 1000.0;
  /** Inlet absolute pressure in bar. */
  private double inletPressure = 10.0;
  /** Outlet absolute pressure in bar. */
  private double outletPressure = 5.0;
  /** Inlet density in kg/m3. */
  private double inletDensity = 8.0;
  /** Isentropic exponent (ratio of specific heats) gamma = Cp/Cv. */
  private double isentropicExponent = 1.30;
  /** Terminal pressure-drop ratio factor xT (dimensionless). */
  private double pressureDropRatioFactor = 0.70;
  /** Piping geometry factor Fp (dimensionless, 1.0 for line-size valve). */
  private double pipingGeometryFactor = 1.0;

  // ===== Results =====
  /** Pressure-drop ratio x = dp / p1. */
  private double pressureDropRatio;
  /** Specific-heat-ratio factor Fgamma = gamma / 1.40. */
  private double specificHeatRatioFactor;
  /** Choked pressure-drop ratio = Fgamma * xT. */
  private double chokedPressureDropRatio;
  /** True when the flow is choked (x >= Fgamma * xT). */
  private boolean choked;
  /** Expansion factor Y (dimensionless). */
  private double expansionFactor;
  /** Required flow coefficient Kv (m3/h at unit pressure drop). */
  private double requiredKv;
  /** Required flow coefficient Cv (US gallons/min at unit pressure drop). */
  private double requiredCv;

  /**
   * Default constructor for ControlValveGasSizing_IEC_60534_2_1.
   */
  public ControlValveGasSizing_IEC_60534_2_1() {
  }

  /**
   * Sets the flow and pressure conditions.
   *
   * @param massFlowKgH mass flow rate in kg/h (must be &gt; 0)
   * @param inletPressureBara inlet absolute pressure in bar (must be &gt; 0)
   * @param outletPressureBara outlet absolute pressure in bar (must be &gt; 0 and &lt; inlet)
   * @param inletDensityKgM3 inlet density in kg/m3 (must be &gt; 0)
   */
  public void setFlowConditions(double massFlowKgH, double inletPressureBara, double outletPressureBara,
      double inletDensityKgM3) {
    this.massFlow = massFlowKgH;
    this.inletPressure = inletPressureBara;
    this.outletPressure = outletPressureBara;
    this.inletDensity = inletDensityKgM3;
  }

  /**
   * Sets the gas property and valve coefficients.
   *
   * @param isentropicExp isentropic exponent gamma = Cp/Cv (must be &gt; 1)
   * @param xt terminal pressure-drop ratio factor xT (0-1, must be &gt; 0)
   * @param fp piping geometry factor Fp (must be &gt; 0)
   */
  public void setValveCoefficients(double isentropicExp, double xt, double fp) {
    this.isentropicExponent = isentropicExp;
    this.pressureDropRatioFactor = xt;
    this.pipingGeometryFactor = fp;
  }

  /**
   * Populates the flow conditions directly from a NeqSim process {@link ThrottlingValve}.
   *
   * <p>
   * Reads the mass flow, density and isentropic exponent from the valve inlet fluid and the inlet/outlet pressures from
   * the valve streams (the valve must already have been run/flashed). The valve coefficients xT and Fp are left
   * unchanged so they can be configured separately via {@link #setValveCoefficients}.
   * </p>
   *
   * @param valve the process throttling valve supplying the flow conditions (must not be null and must have inlet and
   * outlet streams)
   */
  public void fromValve(ThrottlingValve valve) {
    if (valve == null) {
      throw new IllegalArgumentException("valve cannot be null");
    }
    StreamInterface inlet = valve.getInletStream();
    StreamInterface outlet = valve.getOutletStream();
    if (inlet == null || outlet == null) {
      throw new IllegalArgumentException("valve must have inlet and outlet streams");
    }
    SystemInterface fluid = inlet.getFluid();
    this.massFlow = inlet.getFlowRate("kg/hr");
    this.inletPressure = inlet.getPressure("bara");
    this.outletPressure = outlet.getPressure("bara");
    this.inletDensity = fluid.getDensity("kg/m3");
    double gamma = fluid.getGamma();
    if (gamma > 1.0) {
      this.isentropicExponent = gamma;
    }
    logger.debug("Populated control-valve gas sizing from valve: m={} kg/h, p1={} bara, p2={} bara, rho={} kg/m3",
        this.massFlow, this.inletPressure, this.outletPressure, this.inletDensity);
  }

  /**
   * Runs the IEC 60534-2-1 compressible sizing calculation.
   */
  public void calcSizing() {
    double dp = inletPressure - outletPressure;
    pressureDropRatio = dp / inletPressure;
    specificHeatRatioFactor = isentropicExponent / 1.40;
    chokedPressureDropRatio = specificHeatRatioFactor * pressureDropRatioFactor;

    choked = pressureDropRatio >= chokedPressureDropRatio;
    double xSizing = Math.min(pressureDropRatio, chokedPressureDropRatio);

    expansionFactor = 1.0 - xSizing / (3.0 * specificHeatRatioFactor * pressureDropRatioFactor);
    if (expansionFactor < 2.0 / 3.0) {
      expansionFactor = 2.0 / 3.0;
    }

    double denominator = N6 * pipingGeometryFactor * expansionFactor
        * Math.sqrt(xSizing * inletPressure * inletDensity);
    if (denominator <= 0.0) {
      requiredKv = 0.0;
    } else {
      requiredKv = massFlow / denominator;
    }
    requiredCv = requiredKv / KV_PER_CV;

    logger.debug("IEC 60534-2-1 sizing: x={}, Fgamma={}, xChoked={}, choked={}, Y={}, Kv={}, Cv={}", pressureDropRatio,
        specificHeatRatioFactor, chokedPressureDropRatio, choked, expansionFactor, requiredKv, requiredCv);
  }

  /**
   * Returns the pressure-drop ratio x = dp / p1.
   *
   * @return pressure-drop ratio (dimensionless)
   */
  public double getPressureDropRatio() {
    return pressureDropRatio;
  }

  /**
   * Returns the specific-heat-ratio factor Fgamma = gamma / 1.40.
   *
   * @return specific-heat-ratio factor (dimensionless)
   */
  public double getSpecificHeatRatioFactor() {
    return specificHeatRatioFactor;
  }

  /**
   * Returns the choked pressure-drop ratio Fgamma * xT.
   *
   * @return choked pressure-drop ratio (dimensionless)
   */
  public double getChokedPressureDropRatio() {
    return chokedPressureDropRatio;
  }

  /**
   * Returns whether the flow is choked.
   *
   * @return true when the flow is choked
   */
  public boolean isChoked() {
    return choked;
  }

  /**
   * Returns the expansion factor Y.
   *
   * @return expansion factor (dimensionless)
   */
  public double getExpansionFactor() {
    return expansionFactor;
  }

  /**
   * Returns the required flow coefficient Kv.
   *
   * @return required Kv (m3/h at unit pressure drop)
   */
  public double getRequiredKv() {
    return requiredKv;
  }

  /**
   * Returns the required flow coefficient Cv.
   *
   * @return required Cv (US gpm at unit pressure drop)
   */
  public double getRequiredCv() {
    return requiredCv;
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
