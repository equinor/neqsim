package neqsim.process.mechanicaldesign.valve;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ControlValveSizing_IEC_60534 class.
 * </p>
 *
 * @author esol
 */
public class ControlValveSizing_IEC_60534 extends ControlValveSizing {

  // === IEC 60534 Constants (Units: Q[m^3/h], P[kPa], rho[kg/m^3]) ===
  /** Constant for liquids (flow in m^3/h, pressure in kPa). */
  static final double N1 = 0.1;
  /** Constant for gases (flow in m^3/h, pressure in kPa, T in K @ 0°C ref). */
  static final double N9 = 24.6;
  /** Reference density of water [kg/m^3] used in the standard. */
  static final double rho0 = 999.103;
  /** Universal gas constant [J/(mol*K)]. */
  static final double R = 8.314462;
  /** Conversion factor from Kv to Cv. */
  static final double KV_TO_CV_FACTOR = 1.156;

  // === Valve Parameters ===
  double FL = 0.9;
  double Fd = 1.0;
  double D1 = 0.0; // Upstream pipe diameter [m]
  double D2 = 0.0; // Downstream pipe diameter [m]
  double d = 0.0; // Valve diameter [m]

  double FD = 1.0;
  boolean allowLaminar = true;
  boolean fullOutput = true;


  /**
   * <p>
   * isAllowChoked.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowChoked() {
    return allowChoked;
  }

  /** {@inheritDoc} */
  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  /**
   * <p>
   * isAllowLaminar.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAllowLaminar() {
    return allowLaminar;
  }

  /**
   * <p>
   * Setter for the field <code>allowLaminar</code>.
   * </p>
   *
   * @param allowLaminar a boolean
   */
  public void setAllowLaminar(boolean allowLaminar) {
    this.allowLaminar = allowLaminar;
  }

  /**
   * <p>
   * isFullOutput.
   * </p>
   *
   * @return a boolean
   */
  public boolean isFullOutput() {
    return fullOutput;
  }

  /**
   * <p>
   * Setter for the field <code>fullOutput</code>.
   * </p>
   *
   * @param fullOutput a boolean
   */
  public void setFullOutput(boolean fullOutput) {
    this.fullOutput = fullOutput;
  }

  public enum FluidType {
    LIQUID, GAS
  }

  /**
   * <p>
   * Constructor for ControlValveSizing_IEC_60534.
   * </p>
   */
  public ControlValveSizing_IEC_60534() {
    super();
  }

  /**
   * <p>
   * Constructor for ControlValveSizing_IEC_60534.
   * </p>
   *
   * @param valveMechanicalDesign a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ControlValveSizing_IEC_60534(ValveMechanicalDesign valveMechanicalDesign) {
    super(valveMechanicalDesign);
  }

  /**
   * <p>
   * getFL.
   * </p>
   *
   * @return a double
   */
  public double getFL() {
    return FL;
  }

  /**
   * <p>
   * setFL.
   * </p>
   *
   * @param FL a double
   */
  public void setFL(double FL) {
    this.FL = FL;
  }

  /**
   * <p>
   * getFd.
   * </p>
   *
   * @return a double
   */
  public double getFd() {
    return Fd;
  }

  /**
   * <p>
   * setFd.
   * </p>
   *
   * @param Fd a double
   */
  public void setFd(double Fd) {
    this.Fd = Fd;
  }

  /**
   * <p>
   * getD1.
   * </p>
   *
   * @return a double
   */
  public double getD1() {
    return D1;
  }

  /**
   * <p>
   * setD1.
   * </p>
   *
   * @param D1 a double
   */
  public void setD1(double D1) {
    this.D1 = D1;
  }

  /**
   * <p>
   * getD2.
   * </p>
   *
   * @return a double
   */
  public double getD2() {
    return D2;
  }

  /**
   * <p>
   * setD2.
   * </p>
   *
   * @param D2 a double
   */
  public void setD2(double D2) {
    this.D2 = D2;
  }

  /**
   * <p>
   * Getter for the field <code>d</code>.
   * </p>
   *
   * @return a double
   */
  public double getD() {
    return d;
  }

  /**
   * <p>
   * Setter for the field <code>d</code>.
   * </p>
   *
   * @param d a double
   */
  public void setD(double d) {
    this.d = d;
  }

  // === Main API ===
  /** {@inheritDoc} */
  public Map<String, Object> calcValveSize(double percentOpening) {
    SystemInterface fluid =
        ((ThrottlingValve) valveMechanicalDesign.getProcessEquipment()).getInletStream().getFluid();
    Map<String, Object> result;
    if (fluid.hasPhaseType(PhaseType.GAS)) {
      return sizeControlValveGas(fluid.getTemperature(), fluid.getMolarMass("gr/mol"),
          fluid.getGamma2(), fluid.getZ(), getValve().getInletPressure() * 1e5,
          getValve().getOutletPressure() * 1e5, fluid.getFlowRate("m3/sec"), percentOpening);

    } else {
      return sizeControlValveLiquid(fluid.getDensity("kg/m3"), fluid.getZ(), fluid.getPC() * 1e5,
          getValve().getInletPressure() * 1e5, getValve().getOutletPressure() * 1e5,
          fluid.getFlowRate("m3/sec"), percentOpening);
    }
  }

  /**
   * <p>
   * getValve.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.valve.ValveInterface} object
   */
  public ValveInterface getValve() {
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
   * @param percentOpening Valve opening percentage (0-100)
   * @return a map containing the sizing results
   */
  public Map<String, Object> sizeControlValve(FluidType type, double rhoOrT, double MW, double mu,
      double gammaOrPsat, double ZOrPc, double P1, double P2, double Q, Double D1, Double D2,
      Double d, double FL, double Fd, double xTOrNone, boolean allowChoked, boolean allowLaminar,
      boolean fullOutput, double percentOpening) {
    Map<String, Object> result = fullOutput ? new HashMap<>() : null;

    if (type == FluidType.LIQUID) {
      return sizeControlValveLiquid(rhoOrT, gammaOrPsat, ZOrPc, P1, P2, Q, percentOpening);

    } else if (type == FluidType.GAS) {
      return sizeControlValveGas(rhoOrT, MW, gammaOrPsat, ZOrPc, P1, P2, Q, percentOpening);

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
   * @param percentOpening Valve opening percentage (0-100)
   * @return A map containing the sizing results (Kv, Kv, choked, etc.).
   */
  public Map<String, Object> sizeControlValveLiquid(double rho, double Psat, double Pc, double P1,
      double P2, double Q, double percentOpening) {
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
    if (valveMechanicalDesign != null) {
      Kv = Kv
          / valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);
    }

    ans.put("FF", FF);
    ans.put("choked", choked);
    ans.put("Kv", Kv);
    ans.put("Cv", Kv_to_Cv(Kv));

    return ans;
  }

  /**
   * {@inheritDoc}
   *
   * Calculates the flow rate through a control valve based on the valve opening, Kv, and
   * inlet/outlet streams.
   */
  public double calculateFlowRateFromValveOpening(double adjustedKv, StreamInterface inletStream,
      StreamInterface outletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return calculateFlowRateFromValveOpeningGas(adjustedKv, inletStream, outletStream);
    } else {
      return calculateFlowRateFromValveOpeningLiquid(adjustedKv, inletStream, outletStream);
    }
  }

  /**
   * <p>
   * calculateFlowRateFromValveOpeningGas.
   * </p>
   *
   * @param adjustedKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateFlowRateFromValveOpeningGas(double adjustedKv, StreamInterface inletStream,
      StreamInterface outletStream) {
    return calculateFlowRateFromKvAndValveOpeningGas(adjustedKv,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("gr/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, xT, allowChoked);
  }

  /**
   * Calculates liquid flow rate from valve opening.
   *
   * @param adjustedKv Max flow coefficient of the valve
   * @param rho Liquid density [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @return Flow rate [m^3/s]
   */
  public double calculateFlowRateFromValveOpeningLiquid(double adjustedKv, double rho, double Psat,
      double Pc, double P1, double P2) {

    // Use consistent kPa unit system
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double locPsat = Psat / 1000.0;
    double dP = locP1 - locP2;

    double effectiveKv = adjustedKv;

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

  /**
   * <p>
   * calculateFlowRateFromValveOpeningLiquid.
   * </p>
   *
   * @param adjustedKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateFlowRateFromValveOpeningLiquid(double adjustedKv,
      StreamInterface inletStream, StreamInterface outletStream) {

    return calculateFlowRateFromValveOpeningLiquid(adjustedKv,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"));
  }

  /**
   * Calculates required valve opening for a given liquid flow rate.
   *
   * @param Q Desired flow rate [m^3/s]
   * @param Kv Max flow coefficient of the valve
   * @param rho Liquid density [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param P1 Upstream pressure [Pa]
   * @param P2 Downstream pressure [Pa]
   * @return Required valve opening percentage (0-100).
   */
  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Kv, double rho,
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

    double valveOpening = (requiredKv / Kv) * 100.0;

    return Math.max(0.0, Math.min(100.0, valveOpening)); // Clamp between 0 and 100
  }



  /**
   * <p>
   * calculateValveOpeningFromFlowRateLiquid.
   * </p>
   *
   * @param Q a double
   * @param Kv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Kv,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateLiquid(Q, Kv,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"));
  }

  /**
   * Finds the outlet pressure for a given flow rate Q and a fixed (actual) Kv in a liquid valve.
   * This is solved iteratively using a bisection search algorithm.
   *
   * @param rho Density of the liquid [kg/m^3]
   * @param Psat Saturation pressure [Pa]
   * @param Pc Critical pressure [Pa]
   * @param mu Dynamic viscosity [Pa.s] - Note: Not used in this turbulent model
   * @param P1 Upstream pressure [Pa]
   * @param Q Flow rate [m^3/s]
   * @param actualKv The actual installed valve's Kv
   * @param FL Liquid pressure recovery factor
   * @param Fd Valve style modifier
   * @param allowChoked Whether to allow choked flowfindOutletPressureForFixedKvLiquid
   * @param allowLaminar Whether to allow laminar flow
   * @return Outlet pressure P2 [Pa]
   */
  public double findOutletPressureForFixedKvLiquid(double rho, double Psat, double Pc, double mu,
      double P1, double Q, double actualKv, double FL, double Fd, boolean allowChoked,
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

      Map<String, Object> result = sizeControlValveLiquid(rho, Psat, Pc, P1, guessP2_pa, Q,
          ((ValveInterface) getValveMechanicalDesign().getProcessEquipment())
              .getPercentValveOpening());
      double requiredKv = (double) result.get("Kv");

      // Bisection logic
      if (requiredKv < actualKv) {
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
    return P2_mid_kPa * 1000.0;
  }

  // add a general method to find outlet pressure for fixed Kv that work for both gas and liquid


  /**
   * <p>
   * findOutletPressureForFixedKvLiquid.
   * </p>
   *
   * @param actualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double findOutletPressureForFixedKvLiquid(double actualKv, StreamInterface inletStream) {
    return findOutletPressureForFixedKvLiquid(inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPseudoCriticalPressure() * 1e5,
        inletStream.getThermoSystem().getViscosity("kg/msec"), inletStream.getPressure("Pa"),
        inletStream.getFlowRate("m3/sec"), actualKv, FL, FD, allowChoked, allowLaminar);
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
   * @param percentOpening Valve opening percentage (0-100)
   * @return A map containing the sizing results (Kv, Kv, Y, choked, etc.).
   */
  public Map<String, Object> sizeControlValveGas(double T, double MW, double gamma, double Z,
      double P1, double P2, double Q, double percentOpening) {
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

    if (valveMechanicalDesign != null) {
      Kv = Kv
          / valveMechanicalDesign.getValveCharacterizationMethod().getOpeningFactor(percentOpening);
    }

    ans.put("choked", choked);
    ans.put("Y", Y);
    ans.put("Fgamma", Fgamma);
    ans.put("Kv", Kv);
    ans.put("Cv", Kv_to_Cv(Kv));

    return ans;
  }



  /**
   * Calculates the flow rate for gas based on Kv and valve opening percentage.
   *
   * @param adjustedKv full flow coefficient (at 100% opening)
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
  public double calculateFlowRateFromKvAndValveOpeningGas(double adjustedKv, double T, double MW,
      double mu, double gamma, double Z, double P1, double P2, double FL, double xT,
      boolean allowChoked) {

    // Convert pressures to bar
    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;

    // Calculate effective Kv based on valve opening percentage
    double effectiveKv = adjustedKv;
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
      Qloc = effectiveKv * N9 * locP1 * Y / Math.sqrt(MW * T * Z / xT / Fgamma);
    } else {
      Qloc = effectiveKv * N9 * locP1 * Y / Math.sqrt(MW * T * Z / x);
    }

    // Convert flow rate from m^3/h to m^3/s
    return Qloc / 3600.0;
  }

  /**
   * <p>
   * calculateFlowRateFromKvAndValveOpeningGas.
   * </p>
   *
   * @param adjustedKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateFlowRateFromKvAndValveOpeningGas(double adjustedKv,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateFlowRateFromKvAndValveOpeningGas(adjustedKv,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, FD, allowChoked);
  }

  /**
   * Calculates the valve opening percentage for gas based on the flow rate and Kv.
   *
   * @param Q desired flow rate in m^3/s
   * @param Kv full flow coefficient (at 100% opening)
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
  public double calculateValveOpeningFromFlowRateGas(double Q, double Kv, double T, double MW,
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

    // Calculate the effective Kv required for the given flow rate
    double effectiveKv;
    if (choked && allowChoked) {
      effectiveKv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / xT / Fgamma);
    } else {
      effectiveKv = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);
    }

    // Calculate valve opening percentage
    double valveOpening = (effectiveKv / Kv) * 100.0;

    // Ensure the valve opening percentage is within valid bounds
    if (valveOpening < 0.0)
      valveOpening = 0.0;
    if (valveOpening > 100.0)
      valveOpening = 100.0;

    return valveOpening;
  }

  /**
   * <p>
   * calculateValveOpeningFromFlowRateGas.
   * </p>
   *
   * @param Q a double
   * @param Kv a double
   * @param valveOpening a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double calculateValveOpeningFromFlowRateGas(double Q, double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateGas(Q, Kv,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getViscosity("kg/msec"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), outletStream.getPressure("Pa"), FL, FD, allowChoked);
  }

  /**
   * Finds the outlet pressure for a given flow rate and a fixed Kv in a gas valve. This is solved
   * iteratively using a bisection search algorithm.
   *
   * @param T Temperature [K]
   * @param MW Molecular Weight [g/mol]
   * @param gamma Specific heat ratio
   * @param Z Compressibility Factor
   * @param P1 Upstream pressure [Pa]
   * @param Q Volumetric flow rate [m^3/s]
   * @param actualKv The actual installed valve's Kv
   * @return The calculated outlet pressure P2 [Pa] that satisfies the conditions.
   */
  public double findOutletPressureForFixedKvGas(double T, double MW, double gamma, double Z,
      double P1, double Q, double actualKv) {

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
      Map<String, Object> result = sizeControlValveGas(T, MW, gamma, Z, P1, P2_mid, Q, 100);
      double requiredKv = (double) result.get("Kv");

      // Bisection logic (this part was already correct)
      if (requiredKv < actualKv) {
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
        // logger.warn("findOutletPressureForFixedKvGas did not converge within " + maxIter + "
        // iterations.");
      }
    }

    // CORRECTED: Return the final guess of P2, which is already in Pa.
    return P2_mid;
  }

  /**
   * <p>
   * findOutletPressureForFixedKvGas.
   * </p>
   *
   * @param actualKv a double
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @return a double
   */
  public double findOutletPressureForFixedKvGas(double actualKv, StreamInterface inletStream) {

    return findOutletPressureForFixedKvGas(inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("gr/mol"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getPressure("Pa"), inletStream.getFlowRate("m3/sec"), actualKv);
  }

  boolean isChokedTurbulentL(double dP, double P1, double Psat, double FF, double FL) {
    // All pressures must be in consistent units (kPa)
    return dP >= FL * FL * (P1 - FF * Psat);
  }

  boolean isChokedTurbulentG(double x, double Fgamma, double xT) {
    return x >= Fgamma * xT;
  }

  double ffCriticalPressureRatioL(double Psat, double Pc) {
    // All pressures must be in consistent units (kPa)
    return 0.96 - 0.28 * Math.sqrt(Psat / Pc);
  }

  double Kv_to_Cv(double Kv) {
    return Kv * KV_TO_CV_FACTOR;
  }

  double Cv_to_Kv(double Cv) {
    return Cv / KV_TO_CV_FACTOR;
  }

  /**
   * {@inheritDoc}
   *
   * Finds the outlet pressure for a given flow rate and fixed Kv, for both gas and liquid.
   */
  public double findOutletPressureForFixedKv(double actualKv, StreamInterface inletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return findOutletPressureForFixedKvGas(actualKv, inletStream);
    } else {
      return findOutletPressureForFixedKvLiquid(actualKv, inletStream);
    }
  }
}
