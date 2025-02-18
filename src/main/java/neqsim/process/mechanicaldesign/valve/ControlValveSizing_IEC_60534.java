/**
 * This class provides methods for sizing control valves according to the IEC 60534 standard. It
 * supports both liquid and gas fluids and includes methods for calculating flow rates and valve
 * openings based on various parameters.
 * 
 * Constants: - N1: Constant for liquids (m^3/hr, kPa) - N9: Constant for gases (m^3/hr, kPa) -
 * rho0: Water density at 288.15 K - R: Gas constant [J/(mol*K)]
 * 
 * Enum: - FluidType: Enum representing the type of fluid (LIQUID or GAS)
 * 
 * Methods: - sizeControlValve: Sizes a control valve based on the provided parameters. -
 * sizeControlValveLiquid: Sizes a control valve for liquid based on the provided parameters. -
 * calculateFlowRateFromValveOpeningLiquid: Calculates the flow rate for a control valve based on
 * the valve opening and given Cv for liquids. - calculateValveOpeningFromFlowRateLiquid: Calculates
 * the valve opening percentage based on the flow rate and Cv for liquids. -
 * findOutletPressureForFixedCvLiquid: Finds the outlet pressure for a given flow rate and a fixed
 * Cv in a liquid valve. - sizeControlValveGas: Sizes a control valve for gas based on the provided
 * parameters. - calculateFlowRateFromCvAndValveOpeningGas: Calculates the flow rate for gas based
 * on Cv and valve opening percentage. - calculateValveOpeningFromFlowRateGas: Calculates the valve
 * opening percentage for gas based on the flow rate and Cv. - findOutletPressureForFixedCvGas:
 * Finds the outlet pressure for a given flow rate and a fixed Cv in a gas valve.
 * 
 * Private Methods: - isChokedTurbulentL: Determines if the flow is choked for turbulent liquid
 * flow. - isChokedTurbulentG: Determines if the flow is choked for turbulent gas flow. -
 * ffCriticalPressureRatioL: Calculates the critical pressure ratio for liquids. - Kv_to_Cv:
 * Converts Kv to Cv. - Cv_to_Kv: Converts Cv to Kv.
 */
package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;

// THis class is based on the implementation in the fluids package from
// see: https://fluids.readthedocs.io/tutorial.html#control-valve-sizing-introduction
/**
 * <p>
 * ControlValveSizing_IEC_60534 class.
 * </p>
 *
 * @author ESOL
 */
public class ControlValveSizing_IEC_60534 {
  // Constants
  private static final double N1 = 0.1; // Constant for liquids (m^3/hr, kPa)
  private static final double N9 = 2.46E1; // Constant for gases (m^3/hr, kPa)
  private static final double rho0 = 999.10329075702327; // Water density at 288.15 K
  private static final double R = 8.314; // Gas constant [J/(mol*K)]

  // Enum for fluid type
  /**
   * Enum representing the type of fluid.
   */
  public enum FluidType {
    LIQUID, GAS
  }

  /**
   * Sizes a control valve based on the provided parameters.
   *
   * @param type the type of fluid (LIQUID or GAS)
   * @param rhoOrT density for liquid or temperature for gas
   * @param MW molecular weight of the fluid
   * @param mu dynamic viscosity of the fluid
   * @param gammaOrPsat specific heat ratio for gas or saturation pressure for liquid
   * @param ZOrPc compressibility factor for gas or critical pressure for liquid
   * @param P1 upstream pressure
   * @param P2 downstream pressure
   * @param Q flow rate
   * @param D1 upstream pipe diameter
   * @param D2 downstream pipe diameter
   * @param d valve diameter
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param xTOrNone pressure drop ratio factor for gas
   * @param allowChoked whether to allow choked flow
   * @param allowLaminar whether to allow laminar flow
   * @param fullOutput whether to return full output
   * @return a map containing the sizing results
   */
  public static Map<String, Object> sizeControlValve(FluidType type, double rhoOrT, double MW,
      double mu, double gammaOrPsat, double ZOrPc, double P1, double P2, double Q, Double D1,
      Double D2, Double d, double FL, double Fd, double xTOrNone, boolean allowChoked,
      boolean allowLaminar, boolean fullOutput) {
    Map<String, Object> result = fullOutput ? new HashMap<>() : null;

    if (type == FluidType.LIQUID) {
      return sizeControlValveLiquid(rhoOrT, gammaOrPsat, ZOrPc, mu, P1, P2, Q, D1, D2, d, FL, Fd,
          allowChoked, allowLaminar, fullOutput);
    } else if (type == FluidType.GAS) {
      return sizeControlValveGas(rhoOrT, MW, mu, gammaOrPsat, ZOrPc, P1, P2, Q, D1, D2, d, FL, Fd,
          xTOrNone, allowChoked, allowLaminar, fullOutput);
    } else {
      throw new IllegalArgumentException("Invalid fluid type");
    }
  }

  /**
   * Sizes a control valve for liquid based on the provided parameters.
   *
   * @param rho density of the liquid
   * @param Psat saturation pressure of the liquid
   * @param Pc critical pressure of the liquid
   * @param mu dynamic viscosity of the liquid
   * @param P1 upstream pressure
   * @param P2 downstream pressure
   * @param Q flow rate
   * @param D1 upstream pipe diameter
   * @param D2 downstream pipe diameter
   * @param d valve diameter
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param allowChoked whether to allow choked flow
   * @param allowLaminar whether to allow laminar flow
   * @param fullOutput whether to return full output
   * @return a map containing the sizing results
   */
  public static Map<String, Object> sizeControlValveLiquid(double rho, double Psat, double Pc,
      double mu, double P1, double P2, double Q, Double D1, Double D2, Double d, double FL,
      double Fd, boolean allowChoked, boolean allowLaminar, boolean fullOutput) {
    Map<String, Object> ans = fullOutput ? new HashMap<>() : null;

    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;
    double Qloc = Q * 3600.0;

    double nu = mu / rho; // Kinematic viscosity
    double dP = locP1 - locP2;

    // Calculate choked flow
    double FF = ffCriticalPressureRatioL(locPsat, locPc);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, FL);
    double C =
        choked && allowChoked ? Qloc / N1 / FL * Math.sqrt(rho / rho0 / (locP1 - FF * locPsat))
            : Qloc / N1 * Math.sqrt(rho / rho0 / dP);

    if (fullOutput) {
      ans.put("FF", FF);
      ans.put("choked", choked);
      ans.put("Kv", C);
      ans.put("Cv", Kv_to_Cv(C));
    }
    return ans;
  }

  /**
   * Calculates the flow rate for a control valve based on the valve opening and given Cv.
   *
   * @param valveOpening percentage of valve opening (0 to 100)
   * @param Cv flow coefficient (at 100% opening)
   * @param rho density of the liquid (kg/m^3)
   * @param Psat saturation pressure of the liquid (Pa)
   * @param Pc critical pressure of the liquid (Pa)
   * @param mu dynamic viscosity of the liquid (Pa·s)
   * @param P1 upstream pressure (Pa)
   * @param P2 downstream pressure (Pa)
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param allowChoked whether to allow choked flow
   * @return the calculated flow rate in m^3/s
   */
  public static double calculateFlowRateFromValveOpeningLiquid(double valveOpening, double Cv,
      double rho, double Psat, double Pc, double mu, double P1, double P2, double FL, double Fd,
      boolean allowChoked) { // Validate input for valve opening
    if (valveOpening < 0 || valveOpening > 100) {
      throw new IllegalArgumentException("Valve opening must be between 0 and 100%");
    }

    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;

    // Differential pressure
    double dP = locP1 - locP2;

    // Calculate the effective Cv based on valve opening
    double effectiveCv = Cv * (valveOpening / 100.0);

    double effectiveKv = Cv_to_Kv(effectiveCv);
    // Calculate choked flow condition
    double FF = ffCriticalPressureRatioL(locPsat, locPc);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, FL);

    // Calculate flow rate
    double Qloc; // Flow rate in m^3/h
    if (choked && allowChoked) {
      Qloc = effectiveKv * N1 * FL * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho);
    } else {
      Qloc = effectiveKv * N1 / Math.sqrt(rho / rho0 / dP);
    }

    // Convert flow rate from m^3/h to m^3/s
    return Qloc / 3600.0;
  }

  /**
   * Calculates the valve opening percentage based on the flow rate and Cv.
   *
   * @param Q desired flow rate in m^3/s
   * @param Cv full flow coefficient (at 100% opening)
   * @param rho density of the liquid (kg/m^3)
   * @param Psat saturation pressure of the liquid (Pa)
   * @param Pc critical pressure of the liquid (Pa)
   * @param mu dynamic viscosity of the liquid (Pa·s)
   * @param P1 upstream pressure (Pa)
   * @param P2 downstream pressure (Pa)
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param allowChoked whether to allow choked flow
   * @return the valve opening percentage (0 to 100%)
   */
  public static double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double rho,
      double Psat, double Pc, double mu, double P1, double P2, double FL, double Fd,
      boolean allowChoked) { // Constants
    double N1 = 0.865; // Flow coefficient constant for liquids
    double rho0 = 1000.0; // Reference density (kg/m^3)

    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;

    // Differential pressure
    double dP = locP1 - locP2;

    // Convert flow rate from m^3/s to m^3/h
    double Qloc = Q * 3600.0;

    // Calculate choked flow condition
    double FF = ffCriticalPressureRatioL(locPsat, locPc);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, FL);

    // Calculate effective Cv required for the given flow rate
    double effectiveCv;
    if (choked && allowChoked) {
      effectiveCv = Qloc / (N1 * FL * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho));
    } else {
      effectiveCv = Qloc / (N1 * Math.sqrt(rho0 / rho / dP));
    }
    // Calculate valve opening percentage
    double valveOpening = (effectiveCv / Cv) * 100.0;

    // Ensure the valve opening percentage is within valid bounds
    if (valveOpening < 0.0) {
      valveOpening = 0.0;
    }
    if (valveOpening > 100.0) {
      valveOpening = 100.0;
    }

    return valveOpening;
  }

  /**
   * Finds the outlet pressure for a given flow rate Q and a fixed (actual) Cv in a liquid valve, by
   * iterating around your existing sizeControlValveLiquid(...) method.
   *
   * @param rho density of the liquid [kg/m^3]
   * @param Psat saturation pressure [Pa]
   * @param Pc critical pressure [Pa]
   * @param mu dynamic viscosity [Pa.s]
   * @param P1 upstream pressure [Pa]
   * @param Q flow rate [m^3/s]
   * @param actualCv the actual installed valve's Cv
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param allowChoked whether to allow choked flow
   * @param allowLaminar whether to allow laminar flow
   * @return outlet pressure P2 [Pa]
   */
  public static double findOutletPressureForFixedCvLiquid(double rho, double Psat, double Pc,
      double mu, double P1, double Q, double actualCv, double FL, double Fd, boolean allowChoked,
      boolean allowLaminar) {
    // Convert upstream pressure to bar
    double locP1 = P1 / 1e3; // [bar]

    // Set bisection bounds for P2 in bar
    // (e.g. from near vacuum 0.001 bar up to just below P1)
    double locP2_low = 0.001;
    double locP2_high = locP1 - 1e-4;
    double locP2mid = 0.5 * (locP2_low + locP2_high);

    // Convergence settings
    double tolerance = 1e-6;
    int maxIter = 50;

    for (int i = 0; i < maxIter; i++) {
      // Call sizeControlValveLiquid(...) for the guess of locP2mid
      double guessP2_pa = locP2mid * 1e3; // convert bar -> Pa

      // fullOutput = true => we get "Cv" in result map
      Map<String, Object> result =
          sizeControlValveLiquid(rho, Psat, Pc, mu, P1, guessP2_pa, Q, 1.0, 1.0, 1.0, // D1, D2,
              FL, Fd, allowChoked, allowLaminar, true // fullOutput
          );

      // The required Cv for that guessed P2
      double requiredCv = (double) result.get("Cv");

      // Compare required Cv vs. actualCv:
      if (requiredCv < actualCv) {
        // The valve "demands" more Cv than we actually have
        // => means the guess dP is too large for that flow (or the flow is too big),
        // => to reduce requiredCv, we must raise P2 => reduce deltaP
        locP2_low = locP2mid;
      } else {
        // requiredCv <= actualCv => we can pass that flow
        // => we might be able to drop P2 further
        locP2_high = locP2mid;
      }

      // Next iteration's midpoint
      double oldMid = locP2mid;
      locP2mid = 0.5 * (locP2_low + locP2_high);

      // Optional: print iteration info
      // System.out.printf("iter=%d: P2mid=%.6f bar, requiredCv=%.4f, actualCv=%.4f %n", i,
      // locP2mid,
      // requiredCv, actualCv);

      // Check convergence
      if (Math.abs(locP2mid - oldMid) < tolerance) {
        break;
      }
    }

    // Return final guess in Pa
    return locP2mid * 1e3;
  }

  /**
   * Sizes a control valve for gas based on the provided parameters.
   *
   * @param T temperature of the gas
   * @param MW molecular weight of the gas
   * @param mu dynamic viscosity of the gas
   * @param gamma specific heat ratio of the gas
   * @param Z compressibility factor of the gas
   * @param P1 upstream pressure
   * @param P2 downstream pressure
   * @param Q flow rate
   * @param D1 upstream pipe diameter
   * @param D2 downstream pipe diameter
   * @param d valve diameter
   * @param FL liquid pressure recovery factor
   * @param Fd valve style modifier
   * @param xT pressure drop ratio factor for gas
   * @param allowChoked whether to allow choked flow
   * @param allowLaminar whether to allow laminar flow
   * @param fullOutput whether to return full output
   * @return a map containing the sizing results
   */
  public static Map<String, Object> sizeControlValveGas(double T, double MW, double mu,
      double gamma, double Z, double P1, double P2, double Q, Double D1, Double D2, Double d,
      double FL, double Fd, double xT, boolean allowChoked, boolean allowLaminar,
      boolean fullOutput) {
    Map<String, Object> ans = fullOutput ? new HashMap<>() : null;

    // Convert units
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double Qloc = Q * 3600.0;

    // Gas properties
    double Vm = Z * R * T / (locP1 * 1000);
    double rho = MW * 1e-3 / Vm;
    double nu = mu / rho;
    double dP = locP1 - locP2;

    // Choked flow check
    double Fgamma = gamma / 1.40;
    double x = dP / locP1;
    double Y = Math.max(1 - x / (3 * Fgamma * xT), 2.0 / 3.0);
    boolean choked = isChokedTurbulentG(x, Fgamma, xT);

    double C = choked && allowChoked ? Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / xT / Fgamma)
        : Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);
    if (fullOutput) {
      ans.put("choked", choked);
      ans.put("Y", Y);
      ans.put("Kv", C);
      ans.put("Cv", Kv_to_Cv(C));
      ans.put("Cg", Kv_to_Cv(C) * 30.0);
    }
    return ans;
  }

  /**
   * Calculates the flow rate for gas based on Cv and valve opening percentage.
   *
   * @param Cv full flow coefficient (at 100% opening)
   * @param valveOpening valve opening percentage (0 to 100%)
   * @param T temperature of the gas (K)
   * @param MW molecular weight of the gas (g/mol)
   * @param mu dynamic viscosity of the gas (Pa·s)
   * @param gamma specific heat ratio of the gas
   * @param Z compressibility factor of the gas
   * @param P1 upstream pressure (Pa)
   * @param P2 downstream pressure (Pa)
   * @param FL liquid pressure recovery factor
   * @param xT pressure drop ratio factor for gas
   * @param allowChoked whether to allow choked flow
   * @return the calculated flow rate in m^3/s
   */
  public static double calculateFlowRateFromCvAndValveOpeningGas(double Cv, double valveOpening,
      double T, double MW, double mu, double gamma, double Z, double P1, double P2, double FL,
      double xT, boolean allowChoked) { // Validate input for valve opening
    if (valveOpening < 0 || valveOpening > 100) {
      throw new IllegalArgumentException("Valve opening must be between 0 and 100%");
    }

    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;

    // Calculate effective Cv based on valve opening percentage
    double effectiveCv = Cv * (valveOpening / 100.0);
    effectiveCv = Cv_to_Kv(effectiveCv);
    // Gas properties
    double Vm = Z * R * T / (locP1 * 1000.0); // Molar volume (m^3/kmol)
    double rho = MW * 1e-3 / Vm; // Gas density (kg/m^3)
    double dP = locP1 - locP2; // Pressure difference (bar)

    // Choked flow check
    double Fgamma = gamma / 1.40;
    double x = dP / locP1;
    double Y = Math.max(1 - x / (3 * Fgamma * xT), 2.0 / 3.0); // Expansion factor
    boolean choked = isChokedTurbulentG(x, Fgamma, xT);

    // Calculate flow rate in m^3/h
    double Qloc;
    if (choked && allowChoked) {
      Qloc = effectiveCv * N9 * locP1 * Y / Math.sqrt(MW * T * Z / xT / Fgamma);
    } else {
      Qloc = effectiveCv * N9 * locP1 * Y / Math.sqrt(MW * T * Z / x);
    }

    // Convert flow rate from m^3/h to m^3/s
    return Qloc / 3600.0;
  }

  /**
   * Calculates the valve opening percentage for gas based on the flow rate and Cv.
   *
   * @param Q desired flow rate in m^3/s
   * @param Cv full flow coefficient (at 100% opening)
   * @param T temperature of the gas (K)
   * @param MW molecular weight of the gas (g/mol)
   * @param mu dynamic viscosity of the gas (Pa·s)
   * @param gamma specific heat ratio of the gas
   * @param Z compressibility factor of the gas
   * @param P1 upstream pressure (Pa)
   * @param P2 downstream pressure (Pa)
   * @param FL liquid pressure recovery factor
   * @param xT pressure drop ratio factor for gas
   * @param allowChoked whether to allow choked flow
   * @return the valve opening percentage (0 to 100%)
   */
  public static double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double T,
      double MW, double mu, double gamma, double Z, double P1, double P2, double FL, double xT,
      boolean allowChoked) {
    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;

    // Convert flow rate from m^3/s to m^3/h
    double Qloc = Q * 3600.0;

    // Gas properties
    double Vm = Z * R * T / (locP1 * 1000.0); // Molar volume (m^3/kmol)
    double rho = MW * 1e-3 / Vm; // Gas density (kg/m^3)
    double dP = locP1 - locP2; // Pressure difference (bar)

    // Choked flow check
    double Fgamma = gamma / 1.40;
    double x = dP / locP1;
    double Y = Math.max(1 - x / (3 * Fgamma * xT), 2.0 / 3.0); // Expansion factor
    boolean choked = isChokedTurbulentG(x, Fgamma, xT);

    // Calculate the effective Cv required for the given flow rate
    double effectiveCv;
    if (choked && allowChoked) {
      effectiveCv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / xT / Fgamma);
    } else {
      effectiveCv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);
    }

    effectiveCv = Kv_to_Cv(effectiveCv);

    // Calculate valve opening percentage
    double valveOpening = (effectiveCv / Cv) * 100.0;

    // Ensure the valve opening percentage is within valid bounds
    if (valveOpening < 0.0)
      valveOpening = 0.0;
    if (valveOpening > 100.0)
      valveOpening = 100.0;

    return valveOpening;
  }

  /**
   * <p>
   * findOutletPressureForFixedCvGas.
   * </p>
   *
   * @param T a double
   * @param MW a double
   * @param mu a double
   * @param gamma a double
   * @param Z a double
   * @param P1 a double
   * @param Q a double
   * @param actualCv a double
   * @param xT a double
   * @param allowChoked a boolean
   * @return a double
   */
  public static double findOutletPressureForFixedCvGas(double T, double MW, double mu, double gamma,
      double Z, double P1, double Q, // known upstream pressure & desired flow
      double actualCv, // the actual installed valve's Cv
      double xT, boolean allowChoked) {
    double locP1 = P1 / 1e3; // bar
    double locP2_low = 0.001; // lower bound ~ near vacuum [bar]
    double locP2_high = locP1 - 1e-4; // just below P1
    double tolerance = 1e-6;
    double locP2mid = 0.5 * (locP2_low + locP2_high);

    for (int i = 0; i < 50; i++) {
      // call sizeControlValveGas for guess of P2mid
      Map<String, Object> result = sizeControlValveGas(T, MW, mu, gamma, Z,
          // P1 in Pa, P2 guess in Pa
          P1, locP2mid * 1e3, Q, 0.1, 0.1, 0.1, 1.0, 1.0, xT, allowChoked, false, true);
      double requiredCv = (double) result.get("Cv");

      // see how it compares to our actual Cv
      if (requiredCv < actualCv) {
        // we need more Cv than we have => flow is too big =>
        // or we must reduce deltaP => raise P2
        locP2_low = locP2mid;
      } else {
        // requiredCv <= actualCv => we can pass that flow with our valve =>
        // maybe we can drop P2 further
        locP2_high = locP2mid;
      }
      double oldMid = locP2mid;
      locP2mid = 0.5 * (locP2_low + locP2_high);
      // System.out.println("P2mid: " + locP2mid + " Cv " + requiredCv + " actual Cv " + actualCv);
      if (Math.abs(locP2mid - oldMid) < tolerance)
        break;
    }

    // return final guess of P2 in Pa
    return locP2mid * 1e3;
  }

  private static boolean isChokedTurbulentL(double dP, double P1, double Psat, double FF,
      double FL) {
    return dP >= FL * FL * (P1 - FF * Psat);
  }

  private static boolean isChokedTurbulentG(double x, double Fgamma, double xT) {
    return x >= Fgamma * xT;
  }

  private static double ffCriticalPressureRatioL(double Psat, double Pc) {
    return 0.96 - 0.28 * Math.sqrt(Psat / Pc);
  }

  // Kv to Cv Conversion
  private static double Kv_to_Cv(double Kv) {
    return 1.156 * Kv;
  }

  // Cv to Kv Conversion
  private static double Cv_to_Kv(double Cv) {
    return 0.864 * Cv;
  }
}

