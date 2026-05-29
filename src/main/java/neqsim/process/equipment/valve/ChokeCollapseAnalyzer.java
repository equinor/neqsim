package neqsim.process.equipment.valve;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Diagnoses <em>choke collapse</em> on a {@link ThrottlingValve} — i.e. transitions out of the
 * critical (choked / sonic) flow regime, plus flashing and cavitation flags for liquid service.
 *
 * <p>
 * Theory:
 * </p>
 * <ul>
 * <li>Gas: critical pressure ratio \( r_c = \left(\tfrac{2}{\gamma+1}\right)^{\gamma/(\gamma-1)}
 * \). When the actual \( r = p_2/p_1 \) drops below \( r_c \) the throat is sonic. If \( r \) rises
 * above \( r_c \) the choke has <em>collapsed</em> back to subcritical and downstream pressure now
 * propagates upstream.</li>
 * <li>Liquid: collapse manifests as <em>flashing</em> (\( p_2 &lt; p_v \)) or <em>cavitation</em>
 * (sigma \( = (p_2-p_v)/(p_1-p_2) \) below threshold).</li>
 * </ul>
 *
 * <p>
 * Heat capacity ratio \(\gamma\) is taken from the inlet gas phase
 * ({@link PhaseInterface#getGamma()}). Inlet/outlet pressures are read in bara.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ChokeCollapseAnalyzer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Default fractional margin for NEAR_COLLAPSE flag (5%). */
  public static final double DEFAULT_MARGIN_THRESHOLD = 0.05;
  /** Default cavitation index threshold (sigma below this ⇒ CAVITATION). */
  public static final double DEFAULT_CAVITATION_THRESHOLD = 1.5;

  private final ThrottlingValve valve;
  private double marginThreshold = DEFAULT_MARGIN_THRESHOLD;
  private double cavitationThreshold = DEFAULT_CAVITATION_THRESHOLD;
  private double downstreamPressureBara = Double.NaN;

  /**
   * Build an analyzer for the given valve. The valve must have been run at least once so that inlet
   * and outlet streams carry valid thermodynamic state.
   *
   * @param valve throttling valve / choke to analyse
   */
  public ChokeCollapseAnalyzer(ThrottlingValve valve) {
    if (valve == null) {
      throw new IllegalArgumentException("valve must not be null");
    }
    this.valve = valve;
  }

  /**
   * Set the fractional margin used to flag {@code NEAR_COLLAPSE}.
   *
   * @param frac e.g. 0.05 for 5%
   */
  public void setCriticalMarginThreshold(double frac) {
    if (frac < 0.0 || frac > 1.0) {
      throw new IllegalArgumentException("margin must be in [0,1]");
    }
    this.marginThreshold = frac;
  }

  /**
   * Set the cavitation-index threshold below which liquid service is flagged as cavitating.
   *
   * @param sigma cavitation index threshold (typical 1.5–2.0)
   */
  public void setCavitationThreshold(double sigma) {
    this.cavitationThreshold = sigma;
  }

  /**
   * Override the downstream pressure used for the analysis (otherwise the valve's current outlet
   * pressure is used).
   *
   * @param pressure pressure value
   * @param unit "bara" (only bara supported here; convert externally for other units)
   */
  public void setDownstreamPressure(double pressure, String unit) {
    if (!"bara".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("only 'bara' is supported, got: " + unit);
    }
    this.downstreamPressureBara = pressure;
  }

  /**
   * Run the analysis using the valve's current state.
   *
   * @return analysis result; never null
   */
  public ChokeCollapseResult analyze() {
    ChokeCollapseResult result = new ChokeCollapseResult();

    StreamInterface inlet = valve.getInletStream();
    StreamInterface outlet = valve.getOutletStream();
    if (inlet == null || inlet.getThermoSystem() == null || outlet == null
        || outlet.getThermoSystem() == null) {
      result.getRecommendations()
          .add("Valve has no thermo state — call valve.run() before analysing.");
      return result;
    }

    SystemInterface inSys = inlet.getThermoSystem();
    double p1 = inSys.getPressure("bara");
    double p2 = Double.isNaN(downstreamPressureBara) ? outlet.getThermoSystem().getPressure("bara")
        : downstreamPressureBara;
    double t1 = inSys.getTemperature();
    result.setInletPressureBara(p1);
    result.setOutletPressureBara(p2);
    result.setInletTemperatureK(t1);
    result.setMassFlowKgPerSec(inSys.getFlowRate("kg/sec"));

    // Reverse-flow guard
    if (p2 >= p1) {
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.REVERSE);
      result.setCollapseMode(ChokeCollapseResult.CollapseMode.COLLAPSED);
      result.setPressureRatio(p1 > 0.0 ? p2 / p1 : Double.NaN);
      result.getRecommendations()
          .add("Downstream pressure >= upstream pressure: no driving force across the choke.");
      return result;
    }

    double r = p2 / p1;
    result.setPressureRatio(r);

    boolean hasGas = inSys.hasPhaseType(PhaseType.GAS);
    boolean hasLiquid = inSys.hasPhaseType(PhaseType.OIL) || inSys.hasPhaseType(PhaseType.AQUEOUS)
        || inSys.hasPhaseType(PhaseType.LIQUID);

    if (hasGas && hasLiquid) {
      result.setFluidPhase("two-phase");
    } else if (hasGas) {
      result.setFluidPhase("gas");
    } else if (hasLiquid) {
      result.setFluidPhase("liquid");
    }

    if (hasGas) {
      analyseGas(inSys, p1, p2, r, result);
    } else if (hasLiquid) {
      analyseLiquid(inSys, p1, p2, result);
    } else {
      result.getRecommendations().add("Could not identify gas or liquid phase on inlet.");
    }

    return result;
  }

  /**
   * Find the downstream/upstream pressure ratio at which the choke transitions between critical and
   * subcritical flow. For gas service this equals \( r_c \) computed from \(\gamma\); for liquid
   * service NaN is returned.
   *
   * @return collapse pressure ratio, or NaN if not applicable
   */
  public double findCollapsePressureRatio() {
    StreamInterface inlet = valve.getInletStream();
    if (inlet == null || inlet.getThermoSystem() == null) {
      return Double.NaN;
    }
    SystemInterface sys = inlet.getThermoSystem();
    if (!sys.hasPhaseType(PhaseType.GAS)) {
      return Double.NaN;
    }
    double gamma = sys.getPhase("gas").getGamma();
    return criticalPressureRatio(gamma);
  }

  /**
   * Gas-service analysis using the critical-pressure-ratio criterion.
   */
  private void analyseGas(SystemInterface sys, double p1, double p2, double r,
      ChokeCollapseResult result) {
    PhaseInterface gas = sys.getPhase("gas");
    double gamma = gas.getGamma();
    double rc = criticalPressureRatio(gamma);
    result.setGamma(gamma);
    result.setCriticalPressureRatio(rc);
    double margin = rc - r;
    result.setMarginToCollapse(margin);

    // Mach number rough proxy: 1.0 at critical, scales with sqrt of expansion (informational).
    if (r <= rc) {
      result.setMachNumber(1.0);
    } else {
      // Subsonic; estimate from isentropic relation M = sqrt( 2/(g-1) * ( (1/r)^((g-1)/g) - 1 ) )
      double exponent = (gamma - 1.0) / gamma;
      double bracket = Math.pow(1.0 / r, exponent) - 1.0;
      result.setMachNumber(Math.sqrt(Math.max(0.0, 2.0 / (gamma - 1.0) * bracket)));
    }

    if (r <= rc) {
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.CRITICAL);
      double relativeMargin = (rc - r) / rc;
      if (relativeMargin < marginThreshold) {
        result.setCollapseMode(ChokeCollapseResult.CollapseMode.NEAR_COLLAPSE);
        result.getRecommendations()
            .add(String.format(
                "Operating within %.1f%% of critical pressure ratio (r=%.4f, rc=%.4f). "
                    + "Small downstream pressure rise will collapse the choke.",
                marginThreshold * 100.0, r, rc));
      } else {
        result.setCollapseMode(ChokeCollapseResult.CollapseMode.NONE);
      }
    } else {
      // r > rc: choke is subcritical
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.SUBCRITICAL);
      double relativeOvershoot = (r - rc) / rc;
      if (relativeOvershoot < marginThreshold) {
        // Just above critical — flag as TRANSITION
        result.setFlowRegime(ChokeCollapseResult.FlowRegime.TRANSITION);
        result.setCollapseMode(ChokeCollapseResult.CollapseMode.NEAR_COLLAPSE);
        result.getRecommendations()
            .add(String.format("Pressure ratio r=%.4f just above rc=%.4f — transition regime; "
                + "mass flow becomes sensitive to downstream pressure.", r, rc));
      } else {
        result.setCollapseMode(ChokeCollapseResult.CollapseMode.COLLAPSED);
        result.getRecommendations()
            .add(String.format("Choke has collapsed to subcritical flow (r=%.4f > rc=%.4f). "
                + "Downstream pressure now propagates upstream; expect rate transient and "
                + "possible severe-slugging coupling.", r, rc));
      }
    }
  }

  /**
   * Liquid-service analysis: flashing and cavitation flags via a bubble-point flash at inlet
   * temperature on a cloned inlet system.
   */
  private void analyseLiquid(SystemInterface sys, double p1, double p2,
      ChokeCollapseResult result) {
    double pv = estimateVapourPressureBara(sys);
    if (Double.isNaN(pv) || pv <= 0.0) {
      result.getRecommendations()
          .add("Could not estimate vapour pressure; flashing/cavitation check skipped.");
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.SUBCRITICAL);
      return;
    }

    if (p2 < pv) {
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.SUBCRITICAL);
      result.setCollapseMode(ChokeCollapseResult.CollapseMode.FLASHING);
      result.setFlashing(true);
      result.getRecommendations()
          .add(String.format(
              "Outlet pressure %.3f bara below vapour pressure %.3f bara — liquid flashing. "
                  + "Use anti-cavitation trim and verify downstream piping for two-phase flow.",
              p2, pv));
      return;
    }

    double denom = p1 - p2;
    double sigma = denom > 0.0 ? (p2 - pv) / denom : Double.POSITIVE_INFINITY;
    result.setCavitationIndex(sigma);

    if (sigma < cavitationThreshold) {
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.SUBCRITICAL);
      result.setCollapseMode(ChokeCollapseResult.CollapseMode.CAVITATION);
      result.getRecommendations().add(String.format(
          "Cavitation index sigma=%.2f below threshold %.2f — incipient cavitation likely. "
              + "Increase backpressure, stage the let-down, or specify hardened/anti-cavitation trim.",
          sigma, cavitationThreshold));
    } else {
      result.setFlowRegime(ChokeCollapseResult.FlowRegime.SUBCRITICAL);
      result.setCollapseMode(ChokeCollapseResult.CollapseMode.NONE);
    }
  }

  /**
   * Compute the analytical critical pressure ratio for an ideal gas with the given heat capacity
   * ratio.
   *
   * @param gamma heat capacity ratio (Cp/Cv); must be &gt; 1
   * @return critical pressure ratio r_c
   */
  public static double criticalPressureRatio(double gamma) {
    if (gamma <= 1.0) {
      throw new IllegalArgumentException("gamma must be > 1, got: " + gamma);
    }
    return Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
  }

  /**
   * Estimate vapour pressure of the liquid at inlet temperature using a bubble-point flash on a
   * clone of the system. Returns NaN on failure.
   *
   * @param sys inlet system
   * @return vapour pressure in bara, or NaN if it could not be computed
   */
  private double estimateVapourPressureBara(SystemInterface sys) {
    try {
      SystemInterface clone = sys.clone();
      clone.setTemperature(sys.getTemperature());
      ThermodynamicOperations ops = new ThermodynamicOperations(clone);
      ops.bubblePointPressureFlash();
      return clone.getPressure("bara");
    } catch (Exception e) {
      return Double.NaN;
    }
  }
}
