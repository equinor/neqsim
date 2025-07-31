package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Provides methods for sizing control valves for liquids and gases according to standard equations.
 */
public class ControlValveSizing implements ControlValveSizingInterface, Serializable {

  double diameter = 8 * 0.0254;
  double diameterInlet = 8 * 0.0254;
  double diameterOutlet = 8 * 0.0254;
  double xT = 0.137;
  double FL = 1.0;
  double FD = 1.0;
  boolean allowChoked = false;
  boolean allowLaminar = true;
  boolean fullOutput = true;
  /** Constant for gases (m^3/hr, kPa). */
  private static final double N9 = 2.46E1;
  /** Constant for liquids (m^3/hr, kPa). */
  private static final double N1 = 0.1;
  private static final double rho0 = 999.10329075702327;
  ValveMechanicalDesign valveMechanicalDesign = null;

  public ControlValveSizing() {

  }

  public ControlValveSizing(ValveMechanicalDesign valveMechanicalDesign) {
    this.valveMechanicalDesign = valveMechanicalDesign;
  }



  public double getxT() {
    return xT;
  }

  public void setxT(double xT) {
    this.xT = xT;
  }


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

  /**
   * Calculates the valve size based on the fluid properties and operating conditions.
   *
   * @return a map containing the calculated valve size and related parameters. If fullOutput is
   *         false, the map will be null.
   */
  public Map<String, Object> calcValveSize() {
    // valveSizing.
    SystemInterface fluid = valveMechanicalDesign.getProcessEquipment().getFluid();

    Map<String, Object> result = valveMechanicalDesign.fullOutput ? new HashMap<>() : null;

    if (fluid.hasPhaseType(PhaseType.GAS)) {
      result = sizeControlValveGas(fluid.getTemperature("K"), fluid.getMolarMass("gr/mol"),
          fluid.getGamma2(), fluid.getZ(),
          ((ValveInterface) valveMechanicalDesign.getProcessEquipment()).getInletPressure() * 1e5,
          ((ValveInterface) valveMechanicalDesign.getProcessEquipment()).getOutletPressure() * 1e5,
          fluid.getFlowRate("Sm3/sec"), xT);
    } else if (fluid.hasPhaseType(PhaseType.LIQUID) || fluid.hasPhaseType(PhaseType.AQUEOUS)
        || fluid.hasPhaseType(PhaseType.OIL)) {
      return sizeControlValveLiquid(fluid.getDensity("kg/m3"),
          ((ValveInterface) valveMechanicalDesign.getProcessEquipment()).getInletPressure() * 1e5,
          ((ValveInterface) valveMechanicalDesign.getProcessEquipment()).getOutletPressure() * 1e5,
          fluid.getFlowRate("m3/hr"));
    } else {
      throw new IllegalArgumentException("Invalid fluid type");
    }
    return result;
  }

  public Map<String, Object> sizeControlValveGas(double T, double MW, double gamma, double Z,
      double P1, double P2, double Q, double xT) {

    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double Qloc = Q * 3600.0;
    double R = 8.31446261815324; // J/(mol*K)
    // Gas properties
    double dP = locP1 - locP2;
    double Fgamma = gamma / 1.40;
    double x = dP / locP1;
    double Y = Math.max(1 - x / (3 * Fgamma * xT), 2.0 / 3.0);

    double C = Qloc / (N9 * locP1 * Y) * Math.sqrt(MW * T * Z / x);

    Map<String, Object> ans = new HashMap<>();
    if (fullOutput) {
      ans.put("choked", false);
      ans.put("Y", Y);
      ans.put("Kv", C);
      ans.put("Cv", Kv_to_Cv(C));
      ans.put("Cg", Kv_to_Cv(C) * 30.0);
    }
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
    } else if (inletStream.getThermoSystem().hasPhaseType(PhaseType.LIQUID)) {
      return calculateFlowRateFromValveOpeningLiquid(Cv, valveOpening, inletStream, outletStream);
    } else {
      throw new IllegalArgumentException("Unsupported phase type for flow rate calculation");
    }
  }

  /**
   * Calculates the required valve opening fraction for a given flow rate, Cv, and inlet/outlet
   * streams.
   *
   * @param Q Flow rate (units depend on phase type)
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @param outletStream Outlet stream from the valve
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRate(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return calculateValveOpeningFromFlowRateGas(Q, Cv, valveOpening, inletStream, outletStream);
    } else if (inletStream.getThermoSystem().hasPhaseType(PhaseType.LIQUID)) {
      return calculateValveOpeningFromFlowRateLiquid(Q, Cv, valveOpening, inletStream,
          outletStream);
    } else {
      throw new IllegalArgumentException("Unsupported phase type for valve opening calculation");
    }
  }

  /**
   * Finds the outlet pressure for a given Cv, valve opening, and inlet stream.
   * 
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param inletStream Inlet stream to the valve
   * @return Outlet pressure (units depend on phase type)
   */
  public double findOutletPressureForFixedCv(double Cv, double valveOpening,
      StreamInterface inletStream) {
    if (inletStream.getThermoSystem().hasPhaseType(PhaseType.GAS)) {
      return findOutletPressureForFixedCvGas(Cv, valveOpening, inletStream);
    } else if (inletStream.getThermoSystem().hasPhaseType(PhaseType.LIQUID)
        || inletStream.getThermoSystem().hasPhaseType(PhaseType.AQUEOUS)) {
      return findOutletPressureForFixedCvLiquid(Cv, valveOpening, inletStream);
    } else {
      throw new IllegalArgumentException("Unsupported phase type for outlet pressure calculation");
    }
  }


  // Implementation for ControlValveSizingInterface.calculateCvForGas
  /**
   * Calculates the required Cv for a gas valve based on molar flow, valve opening, pressures,
   * temperature, molecular weight, specific heat ratio, compressibility factor, and valve pressure
   * drop ratio factor.
   *
   * @param molarFlow_mol_s Molar flow rate [mol/s]
   * @param valveOpening Valve opening fraction (0.0 - 1.0)
   * @param P1_bar Upstream pressure [bar]
   * @param P2_bar Downstream pressure [bar]
   * @param T_K Temperature [K]
   * @param MW_gMol Molecular weight [g/mol]
   * @param gamma Specific heat ratio (Cp/Cv)
   * @param Z Compressibility factor
   * @param xT Valve pressure drop ratio factor
   * @return Required Cv value at 100% opening
   */
  public double calculateCvForGas(double molarFlow_mol_s, double valveOpening, double P1_bar,
      double P2_bar, double T_K, double MW_gMol, double gamma, double Z, double xT) {
    final double R = 8.3145; // J/(mol·K)
    final double N1 = 0.865; // Unit adjustment factor

    // Input validation
    if (P2_bar >= P1_bar || molarFlow_mol_s <= 0 || valveOpening <= 0.0) {
      return -1;
    }

    // Calculate pressure ratio
    double x = (P1_bar - P2_bar) / P1_bar;
    double gamma_r = gamma / 1.4;

    // Calculate expansion factor Y
    double Y = 1.0 - x / (3.0 * xT * gamma_r);
    Y = Math.max(Y, 2.0 / 3.0); // minimum value to avoid choked flow issues

    // Calculate Cv × valveOpening
    double Cv_eff = molarFlow_mol_s * Math.sqrt(Z * MW_gMol * T_K) / (Y * P1_bar * N1);

    // Return Cv at full open
    return Cv_eff / valveOpening;
  }

  // Implementation for ControlValveSizingInterface.calculateCvForLiquid
  public double calculateCvForLiquid(double molarFlow_mol_s, double valveOpening, double P1_bar,
      double P2_bar, double MW_gMol, double density_kg_m3) {
    final double BAR_TO_PSI = 14.5038;
    final double G_TO_KG = 0.001;
    final double KG_M3_TO_LB_FT3 = 62.428;
    final double GPM_TO_M3_S = 0.06309 / 1000.0;

    // Input validation
    if (valveOpening <= 0 || P2_bar >= P1_bar || molarFlow_mol_s <= 0)
      return -1;

    // Pressure drop in psi
    double dP_psi = (P1_bar - P2_bar) * BAR_TO_PSI;

    // Convert density to lb/ft^3 (for IEC/ISA standard)
    double density_lb_ft3 = density_kg_m3 / 16.0185;

    // Convert molar flow to mass flow [kg/s]
    double massFlow_kg_s = molarFlow_mol_s * (MW_gMol * G_TO_KG);

    // Volumetric flow rate [m³/s]
    double volumetricFlow_m3_s = massFlow_kg_s / density_kg_m3;

    // Convert to USGPM
    double Q_gpm = volumetricFlow_m3_s / GPM_TO_M3_S;

    // Calculate effective Cv
    double Cv_eff = Q_gpm / Math.sqrt(dP_psi / density_lb_ft3);

    // Return Cv at 100% opening
    return Cv_eff / valveOpening;
  }


  // Kv to Cv Conversion
  private double Kv_to_Cv(double Kv) {
    return 1.156 * Kv;
  }

  // Cv to Kv Conversion
  private double Cv_to_Kv(double Cv) {
    return 0.864 * Cv;
  }

  public Map<String, Object> sizeControlValveLiquid(double rho, double P1, double P2, double Q) {
    Map<String, Object> ans = new HashMap<>();

    double locP1 = P1 / 1000.0;
    double locP2 = P2 / 1000.0;
    double Qloc = Q * 3600.0;
    double dP = locP1 - locP2;

    double C = Qloc / N1 * Math.sqrt(rho / rho0 / dP);

    ans.put("Kv", C);
    ans.put("Cv", Kv_to_Cv(C));

    return ans;
  }

  /**
   * Calculates the downstream pressure (P2) through a control valve for a liquid based on the given
   * parameters.
   *
   * @param P1 Upstream pressure in bar.
   * @param m Mass flow rate in kilograms per hour (kg/h).
   * @param rho Density of the fluid in kilograms per cubic meter (kg/m³).
   * @param Cv Flow coefficient in US gallons per minute (USG/min).
   * @param Fp Piping geometry factor (dimensionless).
   * @param percentValveOpening Percentage valve opening (0 to 100).
   * @return Downstream pressure in bar.
   */
  public double liquidValvePout(double P1, double m, double rho, double Cv, double Fp,
      double percentValveOpening) {
    // Equation unit conversion constant
    final double N1 = 0.0865;

    // Convert upstream pressure from bar to Pascals directly in the code
    double P1Pa = P1 * 100000;

    // Adjust Cv based on the percentage valve opening
    double adjustedCv = adjustCv(Cv, percentValveOpening);

    // Clip Cv value to be non-negative
    double clippedCv = Math.max(adjustedCv, 0);
    // Calculate deltaP from mass flow rate
    double deltaP = Math.pow(m / (clippedCv * N1 * Fp), 2) / rho;
    // Calculate downstream pressure
    double P2Pa = P1Pa - deltaP;

    // Ensure downstream pressure is non-negative
    P2Pa = Math.max(P2Pa, 0);

    // Convert downstream pressure from Pascals to bar directly in the code
    return P2Pa / 100000;
  }

  /**
   * Adjusts the Cv value based on the percentage valve opening.
   *
   * @param Cv The flow coefficient at 100% opening.
   * @param percentValveOpening The valve opening as a percentage (0 to 100).
   * @return The adjusted Cv value.
   */
  private double adjustCv(double Cv, double percentValveOpening) {
    return Cv * percentValveOpening / 100.0;
  }

  /**
   * Find outlet pressure [bar] for a given target molar flow rate [mol/s] through a gas valve.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Valve opening (0.0–1.0)
   * @param P1_bar Upstream pressure [bar]
   * @param T_K Temperature [K]
   * @param MW_gMol Molecular weight [g/mol]
   * @param Z Compressibility factor
   * @param gamma Specific heat ratio (Cp/Cv)
   * @param xT Valve pressure drop ratio factor
   * @param targetMolarFlow Desired molar flow rate [mol/s]
   * @return Estimated outlet pressure [bar] or -1 if not solvable
   */
  public double findOutletPressureForFixedCvGas(double Cv, double valveOpening, double P1_bar,
      double T_K, double MW_gMol, double Z, double gamma, double xT, double targetMolarFlow) {

    // Constants
    final double R = 8.3145;
    final double N1 = 0.865;

    // Bisection bounds
    double lower = 0.01; // Minimum physically allowed P2 [bar]
    double upper = P1_bar - 1e-6; // Just below P1

    double tolerance = 1e-4;
    int maxIter = 100;

    for (int iter = 0; iter < maxIter; iter++) {
      double mid = (lower + upper) / 2.0;
      double calcFlow =
          calculateMolarFlowRateGas(Cv, valveOpening, P1_bar, mid, T_K, MW_gMol, Z, gamma, xT);

      if (Math.abs(calcFlow - targetMolarFlow) < tolerance) {
        return mid;
      }

      if (calcFlow < targetMolarFlow) {
        lower = mid;
      } else {
        upper = mid;
      }
      System.out.println("Iteration " + iter + ": P2 = " + mid + ", Flow = " + calcFlow
          + " targetflow " + targetMolarFlow);
    }

    // If not converged
    return -1;
  }



  /**
   * Calculates molar flow rate [mol/s] for an incompressible liquid through a control valve.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction (0.0 - 1.0)
   * @param P1_bar Upstream pressure [bar]
   * @param P2_bar Downstream pressure [bar]
   * @param density_kg_m3 Liquid density [kg/m^3]
   * @param MW_gMol Molecular weight [g/mol]
   * @return Molar flow rate [mol/s]
   */
  public double calculateLiquidMolarFlowRate(double Cv, double valveOpening, double P1_bar,
      double P2_bar, double density_kg_m3, double MW_gMol) {

    // Convert bar to psi
    final double BAR_TO_PSI = 14.5038;
    double dP_psi = (P1_bar - P2_bar) * BAR_TO_PSI;

    // Convert density to lb/ft^3
    final double KG_M3_TO_LB_FT3 = 62.428;
    double density_lb_ft3 = density_kg_m3 / 16.0185;

    // Constants
    final double GPM_TO_M3_PER_HR = 0.2271; // For reference, not used here
    final double LB_TO_KG = 0.453592;
    final double G_TO_KG = 0.001;

    // Check for valid input
    if (Cv <= 0 || valveOpening <= 0 || dP_psi <= 0) {
      return 0.0;
    }

    // Effective Cv
    double Cv_eff = Cv * valveOpening;

    // Flow rate in USGPM using IEC/ISA incompressible flow equation:
    double Q_gpm = Cv_eff * Math.sqrt(dP_psi / density_lb_ft3);

    // Convert USGPM to kg/s: 1 USGPM ≈ 0.06309 L/s
    double Q_m3_s = Q_gpm * 0.06309 / 1000.0; // [m³/s]
    double massFlow_kg_s = Q_m3_s * density_kg_m3;

    // Convert to mol/s
    double molarFlow_mol_s = massFlow_kg_s / (MW_gMol * G_TO_KG); // MW in g/mol → kg/mol

    return molarFlow_mol_s;
  }


  /**
   * Finds the outlet pressure [bar] for a given molar flow [mol/s] through a control valve
   * (liquid).
   *
   * @param Cv Flow coefficient [USGPM/(psi)^0.5]
   * @param valveOpening Valve opening (0.0–1.0)
   * @param P1_bar Inlet pressure [bar]
   * @param density_kg_m3 Liquid density [kg/m³]
   * @param MW_gMol Molecular weight [g/mol]
   * @param targetMolarFlow Desired molar flow [mol/s]
   * @return Outlet pressure [bar] or -1 if not found
   */
  public double findOutletPressureForFixedCvLiquid(double Cv, double valveOpening, double P1_bar,
      double density_kg_m3, double MW_gMol, double targetMolarFlow) {

    final double BAR_TO_PSI = 14.5038;
    final double KG_M3_TO_LB_FT3 = 62.428;
    final double G_TO_KG = 0.001;

    if (Cv <= 0 || valveOpening <= 0 || P1_bar <= 0)
      return -1;

    double Cv_eff = Cv * valveOpening;
    double density_lb_ft3 = density_kg_m3 / 16.0185;

    double lower = 0.01; // Lower bound for outlet pressure [bar]
    double upper = P1_bar - 1e-6; // Upper bound for outlet pressure [bar]
    double tolerance = 1e-4;
    int maxIter = 100;

    for (int i = 0; i < maxIter; i++) {
      double midP2 = (lower + upper) / 2.0;
      double dP_psi = (P1_bar - midP2) * BAR_TO_PSI;

      // GPM from IEC incompressible flow equation
      double Q_gpm = Cv_eff * Math.sqrt(dP_psi / density_lb_ft3);

      // Convert GPM → m³/s
      double Q_m3_s = Q_gpm * 0.06309 / 1000.0;

      // Mass flow [kg/s]
      double massFlow_kg_s = Q_m3_s * density_kg_m3;

      // Molar flow
      double molarFlow = massFlow_kg_s / (MW_gMol * G_TO_KG);

      if (Math.abs(molarFlow - targetMolarFlow) < tolerance) {
        return midP2;
      }

      if (molarFlow > targetMolarFlow) {
        lower = midP2;
      } else {
        upper = midP2;
      }
    }

    return -1; // Not converged
  }


  /**
   * Calculates molar flow rate (mol/s) of a gas through a control valve.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction of the valve (0.0 - 1.0)
   * @param P1_bar Upstream pressure [bar]
   * @param P2_bar Downstream pressure [bar]
   * @param T_K Temperature [K]
   * @param MW_gMol Molecular weight [g/mol]
   * @param Z Compressibility factor
   * @param gamma Specific heat ratio (Cp/Cv)
   * @param xT Valve pressure drop ratio factor (typically ~0.7)
   * @return Molar flow rate [mol/s]
   */
  public double calculateMolarFlowRateGas(double Cv, double valveOpening, double P1_bar,
      double P2_bar, double T_K, double MW_gMol, double Z, double gamma, double xT) {

    // Constants
    final double R = 8.3145; // J/(mol·K)
    final double N1 = 0.865; // Cv conversion factor for molar flow [mol/s]

    // Ensure inputs are physically valid
    if (P2_bar >= P1_bar || valveOpening <= 0 || Cv <= 0) {
      return 0.0;
    }

    // Effective Cv based on valve opening
    double Cv_eff = Cv * valveOpening;

    // Pressure terms
    double x = (P1_bar - P2_bar) / P1_bar;
    double gamma_r = gamma / 1.4;

    // Expansion factor (Y)
    double Y = 1.0 - x / (3.0 * xT * gamma_r);
    Y = Math.max(Y, 2.0 / 3.0); // limit to prevent Y < 0

    // Final molar flow calculation
    double numerator = Cv_eff * Y * P1_bar * N1;
    double denominator = Math.sqrt(Z * MW_gMol * T_K);
    double molarFlow = numerator / denominator;

    return molarFlow; // [mol/s]
  }

  public double calculateFlowRateFromValveOpeningLiquid(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateFlowRateFromCvAndValveOpeningLiquid(Cv, valveOpening,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        outletStream.getThermoSystem().getPhase(0).getPressure("Pa"));
  }

  /**
   * Calculates the volumetric flow rate [m3/s] for a liquid through a control valve given Cv, valve
   * opening, density, temperature, inlet and outlet pressures.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction (0.0 - 1.0)
   * @param density_kg_m3 Liquid density [kg/m3]
   * @param temperature_K Temperature [K] (not used in calculation, but kept for interface
   *        consistency)
   * @param inletPressure_Pa Inlet pressure [Pa]
   * @param outletPressure_Pa Outlet pressure [Pa]
   * @return Volumetric flow rate [m3/s]
   */
  public double calculateFlowRateFromCvAndValveOpeningLiquid(double Cv, double valveOpening,
      double density_kg_m3, double temperature_K, double inletPressure_Pa,
      double outletPressure_Pa) {
    final double PA_TO_BAR = 1.0e-5;
    final double BAR_TO_PSI = 14.5038;
    final double GPM_TO_M3_S = 0.06309 / 1000.0;

    double P1_bar = inletPressure_Pa * PA_TO_BAR;
    double P2_bar = outletPressure_Pa * PA_TO_BAR;
    double dP_psi = (P1_bar - P2_bar) * BAR_TO_PSI;

    if (Cv <= 0 || valveOpening <= 0 || dP_psi <= 0) {
      return 0.0;
    }

    double density_lb_ft3 = density_kg_m3 / 16.0185;
    double Cv_eff = Cv * valveOpening;
    double Q_gpm = Cv_eff * Math.sqrt(dP_psi / density_lb_ft3);
    double Q_m3_s = Q_gpm * GPM_TO_M3_S;

    return Q_m3_s;
  }


  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateLiquid(Q, Cv,
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        outletStream.getThermoSystem().getPhase(0).getPressure("Pa"));
  }

  /**
   * Calculates the required valve opening fraction for a given volumetric flow rate [m3/s] for a
   * liquid through a control valve.
   *
   * @param Q Volumetric flow rate [m3/s]
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param density_kg_m3 Liquid density [kg/m3]
   * @param temperature_K Temperature [K] (not used in calculation, but kept for interface
   *        consistency)
   * @param inletPressure_Pa Inlet pressure [Pa]
   * @param outletPressure_Pa Outlet pressure [Pa]
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double density_kg_m3,
      double temperature_K, double inletPressure_Pa, double outletPressure_Pa) {
    final double PA_TO_BAR = 1.0e-5;
    final double BAR_TO_PSI = 14.5038;
    final double GPM_TO_M3_S = 0.06309 / 1000.0;

    double P1_bar = inletPressure_Pa * PA_TO_BAR;
    double P2_bar = outletPressure_Pa * PA_TO_BAR;
    double dP_psi = (P1_bar - P2_bar) * BAR_TO_PSI;

    if (Cv <= 0 || dP_psi <= 0) {
      return 0.0;
    }

    double density_lb_ft3 = density_kg_m3 / 16.0185;
    double Q_gpm = Q / GPM_TO_M3_S;
    double valveOpening = Q_gpm / (Cv * Math.sqrt(dP_psi / density_lb_ft3));
    return valveOpening;
  }


  public double findOutletPressureForFixedCvLiquid(double Cv, double valveOpening,
      StreamInterface inletStream) {
    return findOutletPressureForFixedCvLiquid(Cv, valveOpening,
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getDensity("kg/m3"),
        inletStream.getThermoSystem().getMolarMass("g/mol"), inletStream.getFlowRate("mol/sec"));
  }

  public double calculateFlowRateFromValveOpeningGas(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateFlowRateFromCvAndValveOpeningGas(Cv, valveOpening,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("gr/mol"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        outletStream.getThermoSystem().getPhase(0).getPressure("Pa"));
  }

  /**
   * Calculates the molar flow rate [mol/s] for a gas through a control valve given Cv, valve
   * opening, temperature, molar mass, gamma, Z, inlet and outlet pressures.
   *
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param valveOpening Opening fraction (0.0 - 1.0)
   * @param temperature_K Temperature [K]
   * @param molarMass_g_mol Molar mass [g/mol]
   * @param gamma Specific heat ratio (Cp/Cv)
   * @param Z Compressibility factor
   * @param inletPressure_Pa Inlet pressure [Pa]
   * @param outletPressure_Pa Outlet pressure [Pa]
   * @return Molar flow rate [mol/s]
   */
  public double calculateFlowRateFromCvAndValveOpeningGas(double Cv, double valveOpening,
      double temperature_K, double molarMass_g_mol, double gamma, double Z, double inletPressure_Pa,
      double outletPressure_Pa) {

    final double PA_TO_BAR = 1.0e-5;

    double P1_bar = inletPressure_Pa * PA_TO_BAR;
    double P2_bar = outletPressure_Pa * PA_TO_BAR;

    // Use the existing method for gas molar flow calculation
    return calculateMolarFlowRateGas(Cv, valveOpening, P1_bar, P2_bar, temperature_K,
        molarMass_g_mol, Z, gamma, xT);
  }

  public double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream) {
    return calculateValveOpeningFromFlowRateGas(Q, Cv,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        outletStream.getThermoSystem().getPhase(0).getPressure("Pa"));
  }

  /**
   * Calculates the required valve opening fraction for a given molar flow rate [mol/s] for a gas
   * through a control valve.
   *
   * @param Q Molar flow rate [mol/s]
   * @param Cv Flow coefficient (for 100% opening) [USGPM/(psi)^0.5]
   * @param temperature_K Temperature [K]
   * @param molarMass_g_mol Molar mass [g/mol]
   * @param gamma Specific heat ratio (Cp/Cv)
   * @param Z Compressibility factor
   * @param inletPressure_Pa Inlet pressure [Pa]
   * @param outletPressure_Pa Outlet pressure [Pa]
   * @return Required valve opening fraction (0.0 - 1.0)
   */
  public double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double temperature_K,
      double molarMass_g_mol, double gamma, double Z, double inletPressure_Pa,
      double outletPressure_Pa) {
    final double PA_TO_BAR = 1.0e-5;

    double P1_bar = inletPressure_Pa * PA_TO_BAR;
    double P2_bar = outletPressure_Pa * PA_TO_BAR;

    if (Cv <= 0 || Q <= 0 || P1_bar <= P2_bar) {
      return 0.0;
    }

    // Use a simple bisection method to solve for valve opening
    double lower = 0.0;
    double upper = 1.0;
    double tolerance = 1e-6;
    int maxIter = 100;

    for (int i = 0; i < maxIter; i++) {
      double mid = (lower + upper) / 2.0;
      double calcFlow = calculateMolarFlowRateGas(Cv, mid, P1_bar, P2_bar, temperature_K,
          molarMass_g_mol, Z, gamma, xT);

      if (Math.abs(calcFlow - Q) < tolerance) {
        return mid;
      }

      if (calcFlow < Q) {
        lower = mid;
      } else {
        upper = mid;
      }
    }

    return (lower + upper) / 2.0;
  }

  public double findOutletPressureForFixedCvGas(double Cv, double valveOpening,
      StreamInterface inletStream) {
    return findOutletPressureForFixedCvGas(inletStream.getFlowRate("Sm3/hr"), Cv, valveOpening,
        inletStream.getThermoSystem().getTemperature("K"),
        inletStream.getThermoSystem().getMolarMass("g/mol"),
        inletStream.getThermoSystem().getGamma2(), inletStream.getThermoSystem().getZ(),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"),
        inletStream.getThermoSystem().getPhase(0).getPressure("Pa"));
  }

}
