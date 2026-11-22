package neqsim.process.mechanicaldesign.valve.choke;

import neqsim.process.mechanicaldesign.valve.ValveCharacteristic;

/**
 * Conversions between a choke flow coefficient (Cv), an equivalent orifice and a discharge coefficient.
 *
 * <p>
 * The US flow coefficient is defined by water at 1000 kg/m3: a valve with Cv = 1 passes one US gallon per minute with a
 * pressure drop of one psi. If all of that pressure drop is spent accelerating the fluid through the flow area (no
 * pressure recovery, Bernoulli), the area that passes the Cv = 1 flow is
 * </p>
 *
 * <pre>
 * A1 = Q1 / sqrt(2 dP1 / rho_w) = 1.699e-5 m2   (an orifice of about 4.65 mm diameter)
 * </pre>
 *
 * <p>
 * For a real orifice with discharge coefficient {@code Cd} the geometric area is {@code A = Cv A1 / Cd}, which is
 * equivalent to {@code Cv = 46 224 Cd D^2} for the diameter {@code D} in metres. The discharge coefficient is the
 * product of the contraction (vena contracta) and the energy-loss effects, depends on trim geometry and opening, and
 * must be chosen for the valve in question. Converting Cv to a diameter manually, with the discharge coefficient that
 * the downstream flow model will use, keeps the capacity of the flow model equal to the Cv of the data sheet.
 * </p>
 *
 * <p>
 * The helper also converts measured flow and pressure drop into an observed Cv, which is the basis of choke condition
 * monitoring ({@link ChokeConditionMonitor}).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ChokeFlowCoefficient {
  /** Cubic metres in one US gallon. */
  public static final double US_GALLON_M3 = 0.003785411784;
  /** Pascal in one psi. */
  public static final double PSI_PA = 6894.757293168;
  /** Reference water density used in the Cv definition [kg/m3]. */
  public static final double WATER_DENSITY = 1000.0;
  /** Volume flow of water through a Cv = 1 valve at 1 psi [m3/s]. */
  public static final double CV1_FLOW_M3_PER_S = US_GALLON_M3 / 60.0;
  /** Flow area equivalent to Cv = 1 for a unit discharge coefficient [m2]. */
  public static final double AREA_PER_CV_M2 = CV1_FLOW_M3_PER_S / Math.sqrt(2.0 * PSI_PA / WATER_DENSITY);

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private ChokeFlowCoefficient() {
  }

  /**
   * Geometric orifice area that passes the flow of a given Cv.
   *
   * @param cv flow coefficient [US Cv], positive
   * @param dischargeCoefficient discharge coefficient Cd [-], greater than zero and at most one
   * @return orifice area [m2]
   * @throws IllegalArgumentException if cv is not positive and finite or Cd is outside (0, 1]
   */
  public static double orificeAreaFromCv(double cv, double dischargeCoefficient) {
    requirePositive(cv, "cv");
    requireDischargeCoefficient(dischargeCoefficient);
    return cv * AREA_PER_CV_M2 / dischargeCoefficient;
  }

  /**
   * Diameter of the circular orifice that passes the flow of a given Cv.
   *
   * @param cv flow coefficient [US Cv], positive
   * @param dischargeCoefficient discharge coefficient Cd [-], greater than zero and at most one
   * @return orifice diameter [m]
   * @throws IllegalArgumentException if cv is not positive and finite or Cd is outside (0, 1]
   */
  public static double orificeDiameterFromCv(double cv, double dischargeCoefficient) {
    return Math.sqrt(4.0 * orificeAreaFromCv(cv, dischargeCoefficient) / Math.PI);
  }

  /**
   * Flow coefficient of a circular orifice.
   *
   * @param diameterM orifice diameter [m], positive
   * @param dischargeCoefficient discharge coefficient Cd [-], greater than zero and at most one
   * @return flow coefficient [US Cv]
   * @throws IllegalArgumentException if the diameter is not positive and finite or Cd is outside (0, 1]
   */
  public static double cvFromOrificeDiameter(double diameterM, double dischargeCoefficient) {
    requirePositive(diameterM, "diameterM");
    requireDischargeCoefficient(dischargeCoefficient);
    return dischargeCoefficient * Math.PI * diameterM * diameterM / 4.0 / AREA_PER_CV_M2;
  }

  /**
   * Discharge coefficient implied by a Cv and a geometric orifice diameter.
   *
   * @param cv flow coefficient [US Cv], positive
   * @param diameterM orifice diameter [m], positive
   * @return discharge coefficient [-]; values above one mean the diameter is too small for the Cv
   * @throws IllegalArgumentException if cv or the diameter is not positive and finite
   */
  public static double dischargeCoefficientFromCv(double cv, double diameterM) {
    requirePositive(cv, "cv");
    requirePositive(diameterM, "diameterM");
    return cv * AREA_PER_CV_M2 / (Math.PI * diameterM * diameterM / 4.0);
  }

  /**
   * Flow coefficient at a valve opening, following the trim characteristic.
   *
   * @param maximumCv Cv at full opening [US Cv], positive
   * @param percentOpening opening [percent], 0 to 100
   * @param characteristic trim characteristic that maps opening to the fraction of maximum Cv
   * @return Cv at the opening, never negative
   * @throws IllegalArgumentException if maximumCv is not positive and finite or the characteristic is null
   */
  public static double cvAtOpening(double maximumCv, double percentOpening, ValveCharacteristic characteristic) {
    requirePositive(maximumCv, "maximumCv");
    if (characteristic == null) {
      throw new IllegalArgumentException("characteristic must not be null");
    }
    return maximumCv * Math.max(0.0, characteristic.getOpeningFactor(percentOpening));
  }

  /**
   * Observed flow coefficient from a measured volume flow and pressure drop, treating the fluid as incompressible at
   * the given density: {@code Cv = Q_gpm sqrt(SG / dP_psi)}.
   *
   * <p>
   * For a gas or a flashing multiphase stream the value is an equivalent coefficient on the upstream mixture density,
   * adequate for comparing an observation with a theoretical curve of the same definition but not as a sizing result.
   * </p>
   *
   * @param volumeFlowM3PerS volume flow at upstream conditions [m3/s], positive
   * @param densityKgPerM3 upstream fluid density [kg/m3], positive
   * @param pressureDropPa pressure drop across the choke [Pa], positive
   * @return observed Cv [US Cv]
   * @throws IllegalArgumentException if an input is not positive and finite
   */
  public static double observedCv(double volumeFlowM3PerS, double densityKgPerM3, double pressureDropPa) {
    requirePositive(volumeFlowM3PerS, "volumeFlowM3PerS");
    requirePositive(densityKgPerM3, "densityKgPerM3");
    requirePositive(pressureDropPa, "pressureDropPa");
    double gpm = volumeFlowM3PerS / US_GALLON_M3 * 60.0;
    double specificGravity = densityKgPerM3 / WATER_DENSITY;
    return gpm * Math.sqrt(specificGravity / (pressureDropPa / PSI_PA));
  }

  /**
   * Observed flow coefficient from a measured mass flow, see {@link #observedCv(double, double, double)}.
   *
   * @param massFlowKgPerS mass flow [kg/s], positive
   * @param densityKgPerM3 upstream fluid density [kg/m3], positive
   * @param pressureDropPa pressure drop across the choke [Pa], positive
   * @return observed Cv [US Cv]
   * @throws IllegalArgumentException if an input is not positive and finite
   */
  public static double observedCvFromMassFlow(double massFlowKgPerS, double densityKgPerM3, double pressureDropPa) {
    requirePositive(massFlowKgPerS, "massFlowKgPerS");
    requirePositive(densityKgPerM3, "densityKgPerM3");
    return observedCv(massFlowKgPerS / densityKgPerM3, densityKgPerM3, pressureDropPa);
  }

  /**
   * Nominal throttling velocity: volume flow divided by the orifice area equivalent to a Cv. It is the velocity
   * variable of the sand-erosion operating-range screening.
   *
   * @param volumeFlowM3PerS volume flow at upstream conditions [m3/s], not negative
   * @param cv flow coefficient at the current opening [US Cv], positive
   * @param dischargeCoefficient discharge coefficient Cd [-], greater than zero and at most one
   * @return velocity through the orifice [m/s]
   * @throws IllegalArgumentException if the flow is negative or non-finite, cv is not positive or Cd is outside (0, 1]
   */
  public static double throttlingVelocity(double volumeFlowM3PerS, double cv, double dischargeCoefficient) {
    if (!Double.isFinite(volumeFlowM3PerS) || volumeFlowM3PerS < 0.0) {
      throw new IllegalArgumentException("volumeFlowM3PerS must be finite and not negative");
    }
    return volumeFlowM3PerS / orificeAreaFromCv(cv, dischargeCoefficient);
  }

  /**
   * Checks that a value is positive and finite.
   *
   * @param value value to check
   * @param name argument name for the message
   * @throws IllegalArgumentException if the value is not positive and finite
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Checks the discharge coefficient range.
   *
   * @param dischargeCoefficient value to check
   * @throws IllegalArgumentException if the value is outside (0, 1]
   */
  private static void requireDischargeCoefficient(double dischargeCoefficient) {
    if (!Double.isFinite(dischargeCoefficient) || dischargeCoefficient <= 0.0 || dischargeCoefficient > 1.0) {
      throw new IllegalArgumentException("dischargeCoefficient must be in (0, 1]");
    }
  }
}
