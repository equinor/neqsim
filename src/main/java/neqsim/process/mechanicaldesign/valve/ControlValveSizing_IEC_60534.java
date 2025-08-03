package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

public class ControlValveSizing_IEC_60534 extends ControlValveSizing {

  // === IEC 60534 Constants (Units: Q[m^3/h], P[kPa], rho[kg/m^3]) ===
  /** Constant for liquids (flow in m^3/h, pressure in kPa). */
  private static final double N1 = 0.1;
  /** Constant for gases (flow in m^3/h, pressure in kPa, T in K @ 0°C ref). */
  private static final double N9 = 24.6;
  /** Reference density of water [kg/m^3] used in the standard. */
  private static final double rho0 = 999.103;
  /** Universal gas constant [J/(mol*K)]. */
  private static final double R = 8.314462;
  /** Conversion factor from Kv to Cv. */
  private static final double KV_TO_CV_FACTOR = 1.156;

  // === Valve Parameters ===
  private double FL = 0.9;
  private double Fd = 1.0;
  private double D1 = 0.1; // Upstream pipe diameter [m]
  private double D2 = 0.1; // Downstream pipe diameter [m]
  private double d = 0.1; // Valve diameter [m]

  double diameter = 8 * 0.0254;
  double diameterInlet = 8 * 0.0254;
  double diameterOutlet = 8 * 0.0254;

  double FD = 1.0;
  boolean allowLaminar = true;
  boolean fullOutput = true;


  public boolean isAllowChoked() {
    return allowChoked;
  }

  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  public boolean isAllowLaminar() {
    return allowLaminar;
  }

  public void setAllowLaminar(boolean allowLaminar) {
    this.allowLaminar = allowLaminar;
  }

  public boolean isFullOutput() {
    return fullOutput;
  }

  public void setFullOutput(boolean fullOutput) {
    this.fullOutput = fullOutput;
  }

  public enum FluidType {
    LIQUID, GAS
  }

  public ControlValveSizing_IEC_60534() {
    super();
  }

  public ControlValveSizing_IEC_60534(ValveMechanicalDesign valveMechanicalDesign) {
    super(valveMechanicalDesign);
  }

  public double getFL() {
    return FL;
  }

  public void setFL(double FL) {
    this.FL = FL;
  }

  public double getFd() {
    return Fd;
  }

  public void setFd(double Fd) {
    this.Fd = Fd;
  }

  public double getD1() {
    return D1;
  }

  public void setD1(double D1) {
    this.D1 = D1;
  }

  public double getD2() {
    return D2;
  }

  public void setD2(double D2) {
    this.D2 = D2;
  }

  public double getD() {
    return d;
  }

  public void setD(double d) {
    this.d = d;
  }

  // === Main API ===
  public Map<String, Object> calcValveSize() {
    SystemInterface fluid = valveMechanicalDesign.getProcessEquipment().getFluid();
    Map<String, Object> result;
    if (fluid.hasPhaseType(PhaseType.GAS)) {
      return sizeControlValveGas(fluid.getTemperature(), fluid.getMolarMass("gr/mol"),
          fluid.getGamma2(), fluid.getZ(), getValve().getInletPressure() * 1e5,
          getValve().getOutletPressure() * 1e5, fluid.getFlowRate("m3/sec"));

    } else {
      return sizeControlValveLiquid(fluid.getDensity("kg/m3"), fluid.getZ(), fluid.getPC() * 1e5,
          getValve().getInletPressure() * 1e5, getValve().getOutletPressure() * 1e5,
          fluid.getFlowRate("m3/sec"));
    }
  }

  private ValveInterface getValve() {
    return (ValveInterface) valveMechanicalDesign.getProcessEquipment();
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
  public Map<String, Object> sizeControlValve(FluidType type, double rhoOrT, double MW, double mu,
      double gammaOrPsat, double ZOrPc, double P1, double P2, double Q, Double D1, Double D2,
      Double d, double FL, double Fd, double xTOrNone, boolean allowChoked, boolean allowLaminar,
      boolean fullOutput) {
    Map<String, Object> result = fullOutput ? new HashMap<>() : null;

    if (type == FluidType.LIQUID) {
      return sizeControlValveLiquid(rhoOrT, gammaOrPsat, ZOrPc, P1, P2, Q);

    } else if (type == FluidType.GAS) {
      return sizeControlValveGas(rhoOrT, MW, gammaOrPsat, ZOrPc, P1, P2, Q);

    } else {
      throw new IllegalArgumentException("Invalid fluid type");
    }
  }


  /**
   * Sizes a control valve for a liquid based on the provided parameters. Aligned with IEC 60534 and
   * 'fluids' library.
   *
   * @param rho Density of the liquid [kg/m^3]
   * @param Psat Saturation pressure of the liquid [Pa]
   * @param Pc Critical pressure of the liquid [Pa]
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @param Q Volumetric flow rate [m^3/s]
   * @return A map containing the sizing results (Kv, Cv, choked, etc.).
   */
  public Map<String, Object> sizeControlValveLiquid(double rho, double Psat, double Pc, double P1,
      double P2, double Q) {
    Map<String, Object> ans = new HashMap<>();

    // Convert units to match IEC formulas (Pressure: Pa -> kPa, Flow: m^3/s -> m^3/h)
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;
    double Qloc = Q * 3600.0;
    double dP = locP1 - locP2;

    double FF = ffCriticalPressureRatioL(locPsat, locPc);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, this.FL);

    double Kv; // The required flow coefficient
    if (choked && isAllowChoked()) {
      // Formula for choked flow
      Kv = Qloc / (N1 * this.FL * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho));
    } else {
      // Formula for non-choked flow
      Kv = Qloc / (N1 * Math.sqrt(dP * rho0 / rho));
    }

    ans.put("FF", FF);
    ans.put("choked", choked);
    ans.put("Kv", Kv);
    ans.put("Cv", Kv_to_Cv(Kv));

    return ans;
  }


  /**
   * Calculates the flow rate through a control valve based on the valve opening, Cv, and
   * inlet/outlet streams.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Calculated flow rate (units depend on phase type)
   */
  public double calculateFlowRateFromValveOpening(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return calculateFlowRateFromValveOpeningGas(Cv, valveOpening, inletStream, outletStream);
    } else {
      return calculateFlowRateFromValveOpeningLiquid(Cv, valveOpening, inletStream, outletStream);
    }
  }

  public double calculateFlowRateFromValveOpeningGas(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateFlowRateFromCvAndValveOpeningGas(Cv, valveOpening,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("gr/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, xT, allowChoked);
  }

  /**
   * Calculates liquid flow rate from valve opening.
   *
   * @param Cv Max flow coefficient of the valve
   * @param valveOpening Valve opening percentage (0-100)
   * @param rho Liquid density [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @return Flow rate [m^3/s]
   */
  public double calculateFlowRateFromValveOpeningLiquid(double Cv, double valveOpening, double rho,
      double Psat, double Pc, double P1, double P2) {
    if (valveOpening < 0 || valveOpening > 100) {
      throw new IllegalArgumentException("Valve opening must be between 0 and 100%");
    }

    // Use consistent kPa unit system
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double dP = locP1 - locP2;

    double effectiveKv = Cv * (valveOpening / 100.0);
    // double effectiveKv = Cv_to_Kv(effectiveCv);

    double FF = ffCriticalPressureRatioL(Psat / 1000.0, Pc / 1000.0);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, this.FL);

    double Qloc; // Flow rate in m^3/h
    if (choked && isAllowChoked()) {
      Qloc = N1 * effectiveKv * this.FL * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho);
    } else {
      Qloc = N1 * effectiveKv * Math.sqrt(dP * rho0 / rho);
    }

    return Qloc / 3600.0; // Convert from m^3/h to m^3/s
  }

  public double calculateFlowRateFromValveOpeningLiquid(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {

    return calculateFlowRateFromValveOpeningLiquid(Cv, valveOpening,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"));
  }

  /**
   * Calculates required valve opening for a given liquid flow rate.
   *
   * @param Q Desired flow rate [m^3/s]
   * @param Cv Max flow coefficient of the valve
   * @param rho Liquid density [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @return Required valve opening percentage (0-100).
   */
  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double rho,
      double Psat, double Pc, double P1, double P2) {
    // Use consistent kPa unit system
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double locPc = Pc / 1000.0;
    double dP = locP1 - locP2;
    double Qloc = Q * 3600.0;

    double FF = ffCriticalPressureRatioL(locPsat, locPc);
    boolean choked = isChokedTurbulentL(dP, locP1, locPsat, FF, this.FL);

    double requiredKv;
    if (choked && isAllowChoked()) {
      requiredKv = Qloc / (N1 * this.FL * Math.sqrt((locP1 - FF * locPsat) * rho0 / rho));
    } else {
      requiredKv = Qloc / (N1 * Math.sqrt(dP * rho0 / rho));
    }

    double requiredCv = Kv_to_Cv(requiredKv);
    double valveOpening = (requiredCv / Cv) * 100.0;

    return Math.max(0.0, Math.min(100.0, valveOpening)); // Clamp between 0 and 100
  }



  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateLiquid(Q, Cv,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"));
  }

  /**
   * Finds the outlet pressure for a given flow rate Q and a fixed (actual) Cv in a liquid valve.
   * This is solved iteratively using a bisection search algorithm.
   *
   * @param rho Density of the liquid [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param mu Dynamic viscosity [Pa.s] - Note: Not used in this turbulent model
   * @param P1 Upstream pressure [Pa]
   * @param Q Flow rate [m^3/s]
   * @param actualCv The actual installed valve's Cv
   * @param FL Liquid pressure recovery factor
   * @param Fd Valve style modifier
   * @param allowChoked Whether to allow choked flowfindOutletPressureForFixedCvLiquid
   * @param allowLaminar Whether to allow laminar flow
   * @return Outlet pressure P2 [Pa]
   */
  public double findOutletPressureForFixedCvLiquid(double rho, double Psat, double Pc, double mu,
      double P1, double Q, double actualCv, double FL, double Fd, boolean allowChoked,
      boolean allowLaminar) {

    // --- CORRECTED: The entire bisection search is performed in kilopascals (kPa) ---

    // Set bisection bounds for P2 in kPa.
    double P2_low_kPa = 0.1; // Lower bound, near vacuum [kPa]
    double P2_high_kPa = P1 / 1000.0 - 1e-4; // Upper bound, just below P1 [kPa]
    double P2_mid_kPa = 0.5 * (P2_low_kPa + P2_high_kPa);

    // Convergence settings
    double tolerance = 1e-6; // Relative tolerance
    int maxIter = 50;

    for (int i = 0; i < maxIter; i++) {
      // Convert the midpoint guess from kPa back to Pa for the sizing function call.
      double guessP2_pa = P2_mid_kPa * 1000.0;

      Map<String, Object> result = sizeControlValveLiquid(rho, Psat, Pc, P1, guessP2_pa, Q);
      double requiredCv = (double) result.get("Kv");

      // Bisection logic
      if (requiredCv < actualCv) {
        // Pressure drop is too high. Increase P2 to reduce the drop.
        P2_low_kPa = P2_mid_kPa;
      } else {
        // Pressure drop is too low. Decrease P2 to increase the drop.
        P2_high_kPa = P2_mid_kPa;
      }

      double oldMid_kPa = P2_mid_kPa;
      P2_mid_kPa = 0.5 * (P2_low_kPa + P2_high_kPa);

      // Check for convergence
      if (Math.abs(P2_mid_kPa - oldMid_kPa) < tolerance) {
        break;
      }
    }

    // Return the final converged guess, converted from kPa back to Pa.
    return P2_mid_kPa * 1000.0 / 1e5;
  }

  // add a general method to find outlet pressure for fixed Cv that work for both gas and liquid


  public double findOutletPressureForFixedCvLiquid(double Cv, double valveOpening,
      StreamInterface inletStream) {
    return findOutletPressureForFixedCvLiquid(inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getThermoSystem().getViscosity("kg/msec"), inletStream.getPressure("Pa"),
        inletStream.getFlowRate("m3/sec"), Cv * valveOpening / 100, FL, FD, allowChoked,
        allowLaminar);
  }

  /**
   * Sizes a control valve for a gas based on the provided parameters. CORRECTED to include Fgamma,
   * aligning with IEC 60534 and 'fluids' library.
   *
   * @param T Temperature of the gas [K]
   * @param MW Molecular weight of the gas [g/mol]
   * @param gamma Specific heat ratio
   * @param Z Compressibility factor
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @param Q Volumetric flow rate at inlet conditions [m^3/s]
   * @return A map containing the sizing results (Kv, Cv, Y, choked, etc.).
   */
  public Map<String, Object> sizeControlValveGas(double T, double MW, double gamma, double Z,
      double P1, double P2, double Q) {
    Map<String, Object> ans = new HashMap<>();

    // Convert units (Pressure: Pa -> kPa, Flow: m^3/s -> m^3/h)
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double Qloc = Q * 3600.0;
    double dP = locP1 - locP2;
    double x = dP / locP1;

    // CORRECTED to include Fgamma
    double Fgamma = gamma / 1.40;
    double Y = Math.max(1.0 - x / (3.0 * Fgamma * this.xT), 2.0 / 3.0);
    boolean choked = isChokedTurbulentG(x, Fgamma, this.xT);

    double Kv;
    if (choked && isAllowChoked()) {
      // Choked flow formula, CORRECTED with Fgamma
      Kv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / (this.xT * Fgamma));
    } else {
      // Non-choked flow formula
      Kv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);
    }

    ans.put("choked", choked);
    ans.put("Y", Y);
    ans.put("Fgamma", Fgamma);
    ans.put("Kv", Kv);
    ans.put("Cv", Kv_to_Cv(Kv));

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
  public double calculateFlowRateFromCvAndValveOpeningGas(double Cv, double valveOpening, double T,
      double MW, double mu, double gamma, double Z, double P1, double P2, double FL, double xT,
      boolean allowChoked) {
    // Validate input for valve opening
    if (valveOpening < 0 || valveOpening > 100) {
      throw new IllegalArgumentException("Valve opening must be between 0 and 100%");
    }

    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;

    // Calculate effective Cv based on valve opening percentage
    double effectiveCv = Cv * (valveOpening / 100.0);
    // effectiveCv = Cv_to_Kv(effectiveCv);
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

  public double calculateFlowRateFromCvAndValveOpeningGas(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateFlowRateFromCvAndValveOpeningGas(Cv, valveOpening,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, FD, allowChoked);
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
  public double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double T, double MW,
      double mu, double gamma, double Z, double P1, double P2, double FL, double xT,
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

  public double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateGas(Q, Cv,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, FD, allowChoked);
  }

  /**
   * Finds the outlet pressure for a given flow rate and a fixed Cv in a gas valve. This is solved
   * iteratively using a bisection search algorithm.
   *
   * @param T Temperature [K]
   * @param MW Molecular Weight [g/mol]
   * @param gamma Specific heat ratio
   * @param Z Compressibility Factor
   * @param P1 Upstream pressure [Pa]
   * @param Q Volumetric flow rate [m^3/s]
   * @param actualCv The actual installed valve's Cv
   * @return The calculated outlet pressure P2 [Pa] that satisfies the conditions.
   */
  public double findOutletPressureForFixedCvGas(double T, double MW, double gamma, double Z,
      double P1, double Q, double actualCv) {

    // --- CORRECTED: The entire bisection search is performed in Pascals (Pa) ---

    // Set bisection bounds for P2 in Pascals.
    // Lower bound is a small positive pressure, e.g., 1 Pa.
    double P2_low = 1.0;
    // Upper bound is just slightly below the inlet pressure.
    double P2_high = P1 - 1e-4;
    double P2_mid = 0.5 * (P2_low + P2_high);

    // Convergence settings
    double tolerance = 1e-6; // Relative tolerance
    int maxIter = 50;

    for (int i = 0; i < maxIter; i++) {
      // Call sizeControlValveGas with the P2 guess in Pascals. This is correct.
      Map<String, Object> result = sizeControlValveGas(T, MW, gamma, Z, P1, P2_mid, Q);
      double requiredCv = (double) result.get("Kv");

      // Bisection logic (this part was already correct)
      if (requiredCv < actualCv) {
        // Pressure drop is too high. Increase P2 to reduce the drop.
        P2_low = P2_mid;
      } else {
        // Pressure drop is too low. Decrease P2 to increase the drop.
        P2_high = P2_mid;
      }

      double oldMid = P2_mid;
      P2_mid = 0.5 * (P2_low + P2_high);

      // Check for convergence with a check to avoid division by zero
      if (P2_mid > 1e-9 && Math.abs(P2_mid - oldMid) / P2_mid < tolerance) {
        break;
      }
      if (i == maxIter - 1) {
        // Optional: Add a warning if the loop maxes out
        // logger.warn("findOutletPressureForFixedCvGas did not converge within " + maxIter + "
        // iterations.");
      }
    }

    // CORRECTED: Return the final guess of P2, which is already in Pascals.
    return P2_mid / 1e5;
  }

  public double findOutletPressureForFixedCvGas(double Cv, double valveOpening,
      StreamInterface inletStream) {

    return findOutletPressureForFixedCvGas(inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("gr/mol"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), inletStream.getFlowRate("m3/sec"),
        Cv * valveOpening / 100.0);
  }

  private boolean isChokedTurbulentL(double dP, double P1, double Psat, double FF, double FL) {
    // All pressures must be in consistent units (kPa)
    return dP >= FL * FL * (P1 - FF * Psat);
  }

  private boolean isChokedTurbulentG(double x, double Fgamma, double xT) {
    return x >= Fgamma * xT;
  }

  private double ffCriticalPressureRatioL(double Psat, double Pc) {
    // All pressures must be in consistent units (kPa)
    return 0.96 - 0.28 * Math.sqrt(Psat / Pc);
  }

  private double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  private double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }

  /**
   * Finds the outlet pressure for a given flow rate and fixed Cv, for both gas and liquid.
   *
   * @param Cv the valve flow coefficient
   * @param valveOpening the valve opening percentage (0-100)
   * @param inletStream the inlet stream to the valve
   * @return outlet pressure (Pa for liquid, Pa for gas)
   */
  public double findOutletPressureForFixedCv(double Cv, double valveOpening,
      StreamInterface inletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return findOutletPressureForFixedCvGas(Cv, valveOpening, inletStream);
    } else {
      return findOutletPressureForFixedCvLiquid(Cv, valveOpening, inletStream);
    }
  }
}
