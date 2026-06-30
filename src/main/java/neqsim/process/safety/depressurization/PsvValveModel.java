package neqsim.process.safety.depressurization;

import java.io.Serializable;

/**
 * Pressure-safety-valve (PSV) model with API 520 open/close hysteresis.
 *
 * <p>
 * The valve is a latched discharge device: it pops open when the upstream pressure reaches the set pressure and reseats
 * only after the pressure has fallen to the blowdown (reseat) pressure {@code Pset * (1 - blowdown)}. This hysteresis
 * is what produces the characteristic PSV cycling (chattering) seen when the relief capacity exceeds the load, and the
 * model exposes a cycle counter so that behaviour can be detected. While open, the relieving mass flow is computed from
 * the API 520 compressible-flow orifice relation with automatic switching between critical (choked) and subcritical
 * flow against the back pressure.
 * </p>
 *
 * <p>
 * The model is intended to be used as the discharge device in both blowdown and fire-pressurisation transient runs, so
 * that the relieving device dynamics (rather than a fixed orifice) govern the vessel pressure history.
 * </p>
 *
 * <p>
 * <b>References:</b> API 520 Part I (sizing); API 521 (pressure-relieving and depressuring systems).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PsvValveModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double GAS_CONSTANT = 8.314;

  private final double setPressurePa;
  private final double reseatPressurePa;
  private final double dischargeCoefficient;
  private final double orificeAreaM2;
  private final double backPressurePa;

  private boolean open;
  private int cycleCount;

  /**
   * Creates a PSV model.
   *
   * @param setPressurePa set (pop) pressure in Pa absolute; must be greater than the back pressure
   * @param blowdownFraction blowdown fraction from 0 (exclusive) to below 1; the reseat pressure is
   * {@code setPressurePa * (1 - blowdownFraction)}
   * @param dischargeCoefficient orifice discharge coefficient (typically 0.85 to 0.975); must be positive
   * @param orificeAreaM2 effective orifice flow area in m²; must be positive
   * @param backPressurePa downstream back pressure in Pa absolute; must be non-negative
   * @throws IllegalArgumentException if any argument is invalid
   */
  public PsvValveModel(double setPressurePa, double blowdownFraction, double dischargeCoefficient, double orificeAreaM2,
      double backPressurePa) {
    if (backPressurePa < 0.0) {
      throw new IllegalArgumentException("backPressurePa must be non-negative");
    }
    if (setPressurePa <= backPressurePa) {
      throw new IllegalArgumentException("setPressurePa must be greater than backPressurePa");
    }
    if (blowdownFraction <= 0.0 || blowdownFraction >= 1.0) {
      throw new IllegalArgumentException("blowdownFraction must be between 0 and 1 (exclusive)");
    }
    if (dischargeCoefficient <= 0.0) {
      throw new IllegalArgumentException("dischargeCoefficient must be positive");
    }
    if (orificeAreaM2 <= 0.0) {
      throw new IllegalArgumentException("orificeAreaM2 must be positive");
    }
    this.setPressurePa = setPressurePa;
    this.reseatPressurePa = setPressurePa * (1.0 - blowdownFraction);
    this.dischargeCoefficient = dischargeCoefficient;
    this.orificeAreaM2 = orificeAreaM2;
    this.backPressurePa = backPressurePa;
  }

  /**
   * Updates the latched open/closed state from the current upstream pressure.
   *
   * <p>
   * The valve opens when the pressure reaches the set pressure and closes only when it falls to the reseat pressure.
   * Each open transition increments the cycle counter.
   * </p>
   *
   * @param upstreamPressurePa upstream pressure in Pa absolute
   * @return {@code true} if the valve is open after the update
   */
  public boolean update(double upstreamPressurePa) {
    if (!open && upstreamPressurePa >= setPressurePa) {
      open = true;
      cycleCount++;
    } else if (open && upstreamPressurePa <= reseatPressurePa) {
      open = false;
    }
    return open;
  }

  /**
   * Calculates the relieving mass flow for the current open/closed state.
   *
   * <p>
   * Returns zero when the valve is closed. When open, the API 520 compressible orifice relation is used with automatic
   * critical/subcritical switching against the back pressure.
   * </p>
   *
   * @param upstreamPressurePa upstream pressure in Pa absolute; must be positive
   * @param upstreamTempK upstream temperature in K; must be positive
   * @param molarMassKgPerMol fluid molar mass in kg/mol; must be positive
   * @param heatCapacityRatio isentropic exponent (gamma = Cp/Cv); must be greater than one
   * @param compressibility compressibility factor Z; must be positive
   * @return relieving mass flow in kg/s (zero when closed)
   * @throws IllegalArgumentException if any argument is invalid
   */
  public double massFlowKgPerS(double upstreamPressurePa, double upstreamTempK, double molarMassKgPerMol,
      double heatCapacityRatio, double compressibility) {
    if (upstreamPressurePa <= 0.0 || upstreamTempK <= 0.0) {
      throw new IllegalArgumentException("upstreamPressurePa and upstreamTempK must be positive");
    }
    if (molarMassKgPerMol <= 0.0) {
      throw new IllegalArgumentException("molarMassKgPerMol must be positive");
    }
    if (heatCapacityRatio <= 1.0) {
      throw new IllegalArgumentException("heatCapacityRatio must be greater than one");
    }
    if (compressibility <= 0.0) {
      throw new IllegalArgumentException("compressibility must be positive");
    }
    if (!open) {
      return 0.0;
    }
    double gamma = heatCapacityRatio;
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    double pressureRatio = backPressurePa / upstreamPressurePa;
    double mDot;
    if (pressureRatio <= criticalRatio) {
      mDot = dischargeCoefficient * orificeAreaM2 * upstreamPressurePa
          * Math.sqrt(gamma * molarMassKgPerMol / (compressibility * GAS_CONSTANT * upstreamTempK))
          * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
    } else {
      double term = (2.0 * gamma / (gamma - 1.0))
          * (Math.pow(pressureRatio, 2.0 / gamma) - Math.pow(pressureRatio, (gamma + 1.0) / gamma));
      if (term < 0.0) {
        term = 0.0;
      }
      mDot = dischargeCoefficient * orificeAreaM2 * upstreamPressurePa
          * Math.sqrt(molarMassKgPerMol / (compressibility * GAS_CONSTANT * upstreamTempK)) * Math.sqrt(term);
    }
    if (mDot < 0.0 || Double.isNaN(mDot)) {
      return 0.0;
    }
    return mDot;
  }

  /**
   * Indicates whether the valve is currently open.
   *
   * @return {@code true} if open
   */
  public boolean isOpen() {
    return open;
  }

  /**
   * Gets the number of open transitions (pop events) since construction or the last {@link #reset()}.
   *
   * @return cumulative open-cycle count
   */
  public int getCycleCount() {
    return cycleCount;
  }

  /**
   * Gets the set (pop) pressure.
   *
   * @return set pressure in Pa absolute
   */
  public double getSetPressurePa() {
    return setPressurePa;
  }

  /**
   * Gets the reseat (blowdown) pressure.
   *
   * @return reseat pressure in Pa absolute
   */
  public double getReseatPressurePa() {
    return reseatPressurePa;
  }

  /**
   * Gets the effective orifice flow area.
   *
   * @return orifice area in m²
   */
  public double getOrificeAreaM2() {
    return orificeAreaM2;
  }

  /**
   * Resets the valve to the closed state and clears the cycle counter.
   */
  public void reset() {
    open = false;
    cycleCount = 0;
  }
}
