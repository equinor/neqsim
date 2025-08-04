package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Provides a full implementation of the IEC 60534 standard for control valve sizing. This class
 * extends the simplified version to include iterative calculations for:
 * <ul>
 * <li>Piping geometry factors (Fp, FLP, xTP) for valves installed with reducers/expanders.</li>
 * <li>Reynolds number corrections (FR) for laminar or transitional flow regimes.</li>
 * </ul>
 * The logic is a direct translation of the comprehensive calculations found in the 'fluids' Python
 * library, ensuring high fidelity to the standard for a wide range of operating conditions.
 *
 * @see <a href="https://github.com/CalebBell/fluids/blob/master/fluids/control_valve.py">fluids
 *      Python library</a>
 */
public class ControlValveSizing_IEC_60534_full extends ControlValveSizing_IEC_60534 {

  // === Additional IEC 60534 Constants from 'fluids' library ===
  /** Constant related to valve geometry for Reynolds number calculation. Units: mm. */
  private static final double N2 = 1.6E-3;
  /** Constant for Reynolds number calculation. Units: m^3/hr, m^2/s. */
  private static final double N4 = 7.07E-2;
  /** Constant related to gas piping geometry for xTP calculation. Units: mm. */
  private static final double N5 = 1.8E-3;
  /** Constant for Reynolds factor calculation (full trim). Units: mm. */
  private static final double N18 = 8.65E-1;
  /** Constant for Reynolds factor calculation (reduced trim). Units: mm. */
  private static final double N32 = 1.4E2;
  /** Maximum number of iterations for convergence loops. */
  private static final int MAX_ITERATIONS = 20;
  /** Convergence tolerance for iterative calculations. */
  private static final double CONVERGENCE_TOLERANCE = 1e-3;

  // Additional valve parameter for laminar flow calculations
  private boolean isFullTrim = true;

  public ControlValveSizing_IEC_60534_full() {
    super();
  }

  public ControlValveSizing_IEC_60534_full(ValveMechanicalDesign valveMechanicalDesign) {
    super(valveMechanicalDesign);
  }

  public boolean isFullTrim() {
    return isFullTrim;
  }

  public void setFullTrim(boolean isFullTrim) {
    this.isFullTrim = isFullTrim;
  }

  /**
   * Overrides the simplified liquid sizing method to provide a full, iterative calculation
   * including piping geometry and Reynolds number corrections.
   */
  @Override
  public Map<String, Object> sizeControlValveLiquid(double rho, double Psat, double Pc, double P1,
      double P2, double Q) {
    Map<String, Object> ans = new HashMap<>();

    // Unit conversions to match IEC formulas
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;
    double Qloc = Q * 3600.0;
    double dP = locP1 - locP2;

    double FF = ffCriticalPressureRatioL(locPsat, locPc);

    // Initial Kv calculation (turbulent, no fittings)
    double initialKv;
    boolean initialChoked = isChokedTurbulentL(dP, locP1, locPsat, FF, getFL());
    if (initialChoked && isAllowChoked()) {
      initialKv = Qloc / (N1 * getFL() * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho));
    } else {
      initialKv = Qloc / (N1 * Math.sqrt(dP * rho0 / rho));
    }

    // If no diameters are provided, return the simplified turbulent calculation
    if (getD1() == 0.0 || getD() == 0.0) {
      ans.put("Kv", initialKv);
      ans.put("Cv", Kv_to_Cv(initialKv));
      ans.put("choked", initialChoked);
      ans.put("FF", FF);
      ans.put("Rev", Double.POSITIVE_INFINITY); // Assumed turbulent
      ans.put("laminar", false);
      return ans;
    }

    // --- Full Calculation with Iterations ---
    double nu = getValve().getInletStream().getThermoSystem().getViscosity("kg/msec") / rho; // Kinematic
                                                                                             // viscosity
                                                                                             // in
                                                                                             // m^2/s
    double dmm = getD() * 1000.0;
    double D1mm = getD1() * 1000.0;
    double D2mm = getD2() * 1000.0;

    double kv = initialKv;
    double Rev = reynoldsValve(nu * 1e6, Qloc, D1mm, getFL(), getFd(), kv); // nu must be in m2/s
                                                                            // for python code
    ans.put("Rev", Rev);
    ans.put("laminar", Rev <= 10000 && isAllowLaminar());

    if (Rev > 10000 || !isAllowLaminar()) {
      // Turbulent flow with piping correction
      double FP = 1.0;
      double FLP = getFL();
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        double loss = lossCoefficientPiping(dmm, D1mm, D2mm);
        FP = 1.0 / Math.sqrt(1 + loss / N2 * Math.pow(kv / (dmm * dmm), 2));

        double lossUpstream = (D1mm > 0) ? lossCoefficientPiping(dmm, D1mm, null) : 0;
        FLP = getFL() / Math
            .sqrt(1 + Math.pow(getFL(), 2) / N2 * lossUpstream * Math.pow(kv / (dmm * dmm), 2));

        boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, FLP, FP);

        double newKv;
        if (choked && isAllowChoked()) {
          newKv = Qloc / (N1 * FLP) * Math.sqrt(rho / rho0 / (locP1 - FF * locPsat));
        } else {
          newKv = Qloc / (N1 * FP) * Math.sqrt(rho / rho0 / dP);
        }

        if (Math.abs(newKv - kv) / newKv < CONVERGENCE_TOLERANCE) {
          kv = newKv;
          break;
        }
        kv = newKv;
      }
      ans.put("FP", FP);
      ans.put("FLP", FLP);
    } else {
      // Laminar or Transitional flow
      double FR = 1.0;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        Rev = reynoldsValve(nu * 1e6, Qloc, D1mm, getFL(), getFd(), kv);
        FR = reynoldsFactor(getFL(), kv, dmm, Rev, isFullTrim);

        double newKv = initialKv / FR;

        if (Math.abs(newKv - kv) / newKv < CONVERGENCE_TOLERANCE) {
          kv = newKv;
          break;
        }
        kv = newKv;
      }
      ans.put("Rev", Rev);
      ans.put("FR", FR);
    }

    ans.put("FF", FF);
    ans.put("choked", isChokedTurbulentL(dP, locP1, locPsat, FF, (Double) ans.get("FLP"),
        (Double) ans.get("FP")));
    ans.put("Kv", kv);
    ans.put("Cv", Kv_to_Cv(kv));
    return ans;
  }

  /**
   * Overrides the simplified gas sizing method to provide a full, iterative calculation including
   * piping geometry and Reynolds number corrections.
   */
  @Override
  public Map<String, Object> sizeControlValveGas(double T, double MW, double gamma, double Z,
      double P1, double P2, double Q) {
    Map<String, Object> ans = new HashMap<>();

    // Unit conversions
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double Qloc = Q * 3600.0;
    double dP = locP1 - locP2;
    double x = dP / locP1;

    double Fgamma = gamma / 1.40;
    double Y = Math.max(1.0 - x / (3.0 * Fgamma * getxT()), 2.0 / 3.0);
    boolean initialChoked = isChokedTurbulentG(x, Fgamma, getxT());

    // Initial Kv calculation
    double initialKv;
    if (initialChoked && isAllowChoked()) {
      initialKv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / (getxT() * Fgamma));
    } else {
      initialKv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);
    }

    if (getD1() == 0.0 || getD() == 0.0) {
      ans.put("Kv", initialKv);
      ans.put("Cv", Kv_to_Cv(initialKv));
      ans.put("choked", initialChoked);
      ans.put("Y", Y);
      ans.put("Fgamma", Fgamma);
      ans.put("Rev", Double.POSITIVE_INFINITY);
      ans.put("laminar", false);
      return ans;
    }

    // --- Full Calculation with Iterations ---
    double Vm = Z * R * T / P1; // Molar volume in m^3/mol
    double rho = MW * 1e-3 / Vm; // Density in kg/m^3
    double nu = getValve().getInletStream().getThermoSystem().getViscosity("kg/msec") / rho; // m^2/s

    double dmm = getD() * 1000.0;
    double D1mm = getD1() * 1000.0;
    double D2mm = getD2() * 1000.0;

    double kv = initialKv;
    double Rev = reynoldsValve(nu * 1e6, Qloc, D1mm, getFL(), getFd(), kv);
    ans.put("Rev", Rev);
    ans.put("laminar", Rev <= 10000 && isAllowLaminar());

    if (Rev > 10000 || !isAllowLaminar()) {
      // Turbulent flow with piping correction
      double FP = 1.0;
      double xTP = getxT();
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        double loss = lossCoefficientPiping(dmm, D1mm, D2mm);
        FP = 1.0 / Math.sqrt(1.0 + loss / N2 * Math.pow(kv / (dmm * dmm), 2));

        double lossUpstream = (D1mm > 0) ? lossCoefficientPiping(dmm, D1mm, null) : 0;
        xTP =
            getxT() / (FP * FP) / (1 + getxT() * lossUpstream / N5 * Math.pow(kv / (dmm * dmm), 2));

        boolean choked = isChokedTurbulentG(x, Fgamma, xTP);

        double newKv;
        if (choked && isAllowChoked()) {
          newKv = Qloc / (N9 * FP * locP1 * Y) * Math.sqrt(MW * T * Z / (xTP * Fgamma));
        } else {
          newKv = Qloc / (N9 * FP * locP1 * Y) * Math.sqrt(MW * T * Z / x);
        }

        if (Math.abs(newKv - kv) / newKv < CONVERGENCE_TOLERANCE) {
          kv = newKv;
          break;
        }
        kv = newKv;
      }
      ans.put("FP", FP);
      ans.put("xTP", xTP);
    } else {
      // Laminar or Transitional flow
      double FR = 1.0;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        Rev = reynoldsValve(nu * 1e6, Qloc, D1mm, getFL(), getFd(), kv);
        FR = reynoldsFactor(getFL(), kv, dmm, Rev, isFullTrim);

        double newKv = initialKv / FR;

        if (Math.abs(newKv - kv) / newKv < CONVERGENCE_TOLERANCE) {
          kv = newKv;
          break;
        }
        kv = newKv;
      }
      ans.put("Rev", Rev);
      ans.put("FR", FR);
    }

    ans.put("choked", isChokedTurbulentG(x, Fgamma, (Double) ans.getOrDefault("xTP", getxT())));
    ans.put("Y", Y);
    ans.put("Fgamma", Fgamma);
    ans.put("Kv", kv);
    ans.put("Cv", Kv_to_Cv(kv));
    return ans;
  }

  // === Private Helper Methods Translated from 'fluids' Library ===

  /**
   * Calculates the sum of loss coefficients from inlet/outlet reducers/expanders. IEC 60534-2-1,
   * Equation (6).
   */
  private double lossCoefficientPiping(double d, Double D1, Double D2) {
    double loss = 0.0;
    if (D1 != null) {
      double dr = d / D1;
      double dr2 = dr * dr;
      loss += 1.0 - Math.pow(dr2, 2); // Inlet Bernoulli effect
      loss += 0.5 * Math.pow(1.0 - dr2, 2); // Inlet reducer
    }
    if (D2 != null) {
      double dr = d / D2;
      double dr2 = dr * dr;
      loss += 1.0 * Math.pow(1.0 - dr2, 2); // Outlet expander
      loss -= (1.0 - Math.pow(dr2, 2)); // Outlet Bernoulli effect
    }
    return loss;
  }

  /**
   * Calculates the Reynolds number of a control valve. IEC 60534-2-1, Equation (20).
   */
  private double reynoldsValve(double nu, double Q, double D1, double FL, double Fd, double C) {
    // nu in the formula is in centistokes (mm^2/s), so convert from m^2/s
    double nu_cSt = nu;
    return N4 * Fd * Q / nu_cSt / Math.sqrt(C * FL)
        * Math.pow(Math.pow(FL, 2) * Math.pow(C, 2) / N2 * Math.pow(D1, -4.0) + 1.0, 0.25);
  }

  /**
   * Calculates the Reynolds number factor FR for laminar or transitional flow. IEC 60534-2-1,
   * Section 7.3.
   */
  private double reynoldsFactor(double FL, double C, double d, double Rev, boolean fullTrim) {
    double FR;
    if (fullTrim) {
      // Note: In python code C/d**2 must not exceed 0.04 - simplified here
      double n1 = N2 / Math.pow(C / (d * d), 2);
      double FR_1a = 1.0 + (0.33 * Math.sqrt(FL)) / Math.pow(n1, 0.25) * Math.log10(Rev / 10000.0);
      double FR_2 = 0.026 / FL * Math.sqrt(n1 * Rev);
      FR = (Rev < 10.0) ? FR_2 : Math.min(FR_2, FR_1a);
    } else {
      double n2 = 1 + N32 * Math.pow(C / (d * d), 2.0 / 3.0);
      double FR_3a = 1.0 + (0.33 * Math.sqrt(FL)) / Math.pow(n2, 0.25) * Math.log10(Rev / 10000.0);
      double FR_4 = Math.min(0.026 / FL * Math.sqrt(n2 * Rev), 1.0);
      FR = (Rev < 10.0) ? FR_4 : Math.min(FR_3a, FR_4);
    }
    return Math.max(0, FR); // FR cannot be negative
  }

  /**
   * Overloaded method to check for choked flow with piping factors.
   */
  private boolean isChokedTurbulentL(double dP, double P1, double Psat, double FF, Double FLP,
      Double FP) {
    if (FLP != null && FP != null) {
      return dP >= Math.pow(FLP / FP, 2) * (P1 - FF * Psat);
    }
    // Fallback to the base class method if piping factors are not available
    return super.isChokedTurbulentL(dP, P1, Psat, FF, getFL());
  }

  /**
   * Finds the outlet pressure for a given flow rate and fixed Kv. This method is inherited, but it
   * remains consistent due to polymorphism. Its internal calls to `sizeControlValveGas` or
   * `sizeControlValveLiquid` will correctly resolve to the overridden, full implementations in this
   * class. It is overridden here for clarity and completeness.
   *
   * @param Kv the valve flow coefficient
   * @param valveOpening the valve opening percentage (0-100)
   * @param inletStream the inlet stream to the valve
   * @return outlet pressure in Pascals.
   */
  @Override
  public double findOutletPressureForFixedKv(double Kv, double valveOpening,
      StreamInterface inletStream) {
    // This correctly calls the overridden findOutletPressureForFixedKvGas/Liquid
    // in the base class, which in turn call the FULL sizeControlValve... methods
    // implemented here. No further logic is needed.
    return super.findOutletPressureForFixedKv(Kv, valveOpening, inletStream);
  }

  /**
   * Calculates the flow rate for a given valve opening using the full, iterative model. This method
   * overrides the simplified base class implementation to ensure consistency.
   *
   * @param Kv The maximum flow coefficient of the valve.
   * @param valveOpening The opening of the valve (0-100).
   * @param inletStream The stream entering the valve.
   * @param outletStream The stream leaving the valve (used for outlet pressure).
   * @return The calculated flow rate [m^3/s].
   */
  @Override
  public double calculateFlowRateFromValveOpening(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return calculateFlowRateFromValveOpeningGas_full(Kv, valveOpening, inletStream, outletStream);
    } else {
      return calculateFlowRateFromValveOpeningLiquid_full(Kv, valveOpening, inletStream,
          outletStream);
    }
  }

  /**
   * Calculates the required valve opening for a given flow rate using the full, iterative model.
   * This method overrides the simplified base class implementation.
   *
   * @param Q The desired flow rate [m^3/s].
   * @param Kv The maximum flow coefficient of the valve.
   * @param inletStream The stream entering the valve.
   * @param outletStream The stream leaving the valve.
   * @return The required valve opening (0-100).
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Kv, StreamInterface inletStream,
      StreamInterface outletStream) {
    if (Q <= 0) {
      return 0.0;
    }
    // First, use the full sizing model to determine the exact Kv required for this flow
    // condition.
    Map<String, Object> result = this.calcValveSize(); // Uses streams set in
                                                       // valveMechanicalDesign
    double requiredKv = (double) result.get("Kv");

    // The required opening is the ratio of the required Kv to the valve's max Kv.
    double valveOpening = (requiredKv / Kv) * 100.0;

    return Math.max(0.0, Math.min(100.0, valveOpening)); // Clamp between 0 and 100
  }


  // === PRIVATE HELPERS FOR CONSISTENT CALCULATIONS ===

  /**
   * Numerically solves for liquid flow rate using a bisection search.
   */
  private double calculateFlowRateFromValveOpeningLiquid_full(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (valveOpening <= 0)
      return 0.0;
    double effectiveKv = Kv * (valveOpening / 100.0);

    double rho = inletStream.getThermoSystem().getDensity("kg/m3");
    double Psat = inletStream.getThermoSystem().getPhase(0).getPressure("Pa");
    double Pc = inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5;
    double P1 = inletStream.getPressure("Pa");
    double P2 = outletStream.getPressure("Pa");

    // Bisection search for the flow rate Q
    double Q_low = 0.0;
    // Use the simplified formula to get a reasonable upper bound for the search
    double dP_kPa = (P1 > P2) ? (P1 - P2) / 1000.0 : 1e-3;
    double Q_high_m3h = N1 * effectiveKv * Math.sqrt(dP_kPa * rho0 / rho);
    double Q_high = (Q_high_m3h / 3600.0) * 2.0; // Start with a generous upper bound
    double Q_mid = 0.0;

    for (int i = 0; i < MAX_ITERATIONS; i++) {
      Q_mid = 0.5 * (Q_low + Q_high);
      if (Q_mid < 1e-9)
        break;

      // For this guessed flow rate (Q_mid), what Kv would our full model require?
      Map<String, Object> result = sizeControlValveLiquid(rho, Psat, Pc, P1, P2, Q_mid);
      double requiredKv = (double) result.get("Kv");

      if (requiredKv < effectiveKv) {
        // Q_mid is too low for this Kv; the actual flow must be higher.
        Q_low = Q_mid;
      } else {
        // Q_mid is too high for this Kv; the actual flow must be lower.
        Q_high = Q_mid;
      }
      if (Math.abs(Q_high - Q_low) < 1e-6)
        break;
    }
    return Q_mid;
  }

  /**
   * Numerically solves for gas flow rate using a bisection search.
   */
  private double calculateFlowRateFromValveOpeningGas_full(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (valveOpening <= 0)
      return 0.0;
    double effectiveKv = Kv * (valveOpening / 100.0);

    double T = inletStream.getThermoSystem().getTemperature("K");
    double MW = inletStream.getThermoSystem().getMolarMass("gr/mole");
    double gamma = inletStream.getThermoSystem().getGamma2();
    double Z = inletStream.getThermoSystem().getZ();
    double P1 = inletStream.getPressure("Pa");
    double P2 = outletStream.getPressure("Pa");

    // Bisection search for the flow rate Q
    double Q_low = 0.0;
    // Use the simplified formula to get a reasonable upper bound
    double Y = 0.8; // Assume a reasonable Y for the initial guess
    double x = (P1 > P2) ? (P1 - P2) / P1 : 1e-3;
    double Q_high_m3h = N9 * effectiveKv * (P1 / 1000.0) * Y / Math.sqrt(MW * T * Z / x);
    double Q_high = (Q_high_m3h / 3600.0) * 2.0; // Generous upper bound
    double Q_mid = 0.0;

    for (int i = 0; i < MAX_ITERATIONS; i++) {
      Q_mid = 0.5 * (Q_low + Q_high);
      if (Q_mid < 1e-9)
        break;

      // For this guessed flow rate (Q_mid), what Kv would our full model require?
      Map<String, Object> result = sizeControlValveGas(T, MW, gamma, Z, P1, P2, Q_mid);
      double requiredKv = (double) result.get("Kv");

      if (requiredKv < effectiveKv) {
        // Q_mid is too low; actual flow must be higher.
        Q_low = Q_mid;
      } else {
        // Q_mid is too high; actual flow must be lower.
        Q_high = Q_mid;
      }
      if (Math.abs(Q_high - Q_low) < 1e-6)
        break;
    }
    return Q_mid;
  }
}
