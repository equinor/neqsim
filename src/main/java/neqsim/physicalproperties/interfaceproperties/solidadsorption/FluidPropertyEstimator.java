package neqsim.physicalproperties.interfaceproperties.solidadsorption;

import neqsim.thermo.system.SystemInterface;

/**
 * Utility class for estimating fluid properties needed by adsorption models.
 *
 * <p>
 * Provides methods for estimating saturation pressure, liquid molar volume, and surface tension using generalized
 * correlations based on critical properties and acentric factor.
 * </p>
 *
 * <p>
 * Correlations used:
 * </p>
 * <ul>
 * <li>Lee-Kesler for vapor pressure</li>
 * <li>Rackett for saturated liquid molar volume</li>
 * <li>Macleod-Sugden for surface tension</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FluidPropertyEstimator {

  /**
   * Private constructor to prevent instantiation.
   */
  private FluidPropertyEstimator() {
    // Utility class
  }

  /**
   * Estimate saturation pressure using the Lee-Kesler correlation.
   *
   * <p>
   * Uses the three-parameter corresponding states correlation:
   * </p>
   * $$\ln(P_r^{sat}) = f^{(0)}(T_r) + \omega \cdot f^{(1)}(T_r)$$
   *
   * @param temperature the temperature in K
   * @param tc critical temperature in K
   * @param pc critical pressure in bara
   * @param omega acentric factor (dimensionless)
   * @return saturation pressure in bara
   */
  public static double estimateSaturationPressure(double temperature, double tc, double pc, double omega) {
    if (temperature >= tc) {
      return pc;
    }
    double tr = temperature / tc;
    double f0 = 5.92714 - 6.09648 / tr - 1.28862 * Math.log(tr) + 0.169347 * Math.pow(tr, 6);
    double f1 = 15.2518 - 15.6875 / tr - 13.4721 * Math.log(tr) + 0.43577 * Math.pow(tr, 6);
    return pc * Math.exp(f0 + omega * f1);
  }

  /**
   * Estimate saturation pressure for a component in a system.
   *
   * <p>
   * The component-specific Antoine correlation is preferred when the component database supplies usable coefficients,
   * because the Lee-Kesler corresponding-states correlation is built for normal fluids and is very inaccurate for
   * polar, hydrogen-bonding components. For methanol at 5-35 &deg;C it errs by 9 to 21 percent even with a literature
   * acentric factor.
   * </p>
   *
   * <p>
   * The error is far larger in practice because the stored acentric factor of an associating component is a fitting
   * placeholder rather than a physical value. NeqSim stores &omega; = -0.031 for methanol against a literature value of
   * 0.556, which inflates the Lee-Kesler vapour pressure by roughly a factor of ten to nineteen over 5-35 &deg;C. Any
   * corresponding-states correlation that reads the acentric factor of an associating component will fail the same way,
   * and it fails silently. Lee-Kesler is retained here only as a fallback when no Antoine data exists.
   * </p>
   *
   * @param system the thermodynamic system
   * @param phaseNum the phase number
   * @param compNum the component index
   * @return saturation pressure in bara
   */
  public static double estimateSaturationPressure(SystemInterface system, int phaseNum, int compNum) {
    double tc = system.getPhase(phaseNum).getComponent(compNum).getTC();
    double pc = system.getPhase(phaseNum).getComponent(compNum).getPC();
    double omega = system.getPhase(phaseNum).getComponent(compNum).getAcentricFactor();
    double temperature = system.getPhase(phaseNum).getTemperature();

    double antoine = Double.NaN;
    try {
      antoine = system.getPhase(phaseNum).getComponent(compNum).getAntoineVaporPressure(temperature);
    } catch (Exception ex) {
      antoine = Double.NaN;
    }
    if (!Double.isNaN(antoine) && !Double.isInfinite(antoine) && antoine > 0.0 && antoine < pc) {
      return antoine;
    }
    return estimateSaturationPressure(temperature, tc, pc, omega);
  }

  /**
   * Estimate liquid molar volume using the Rackett equation.
   *
   * <p>
   * Modified Rackett equation:
   * </p>
   * $$V_m^{sat} = V_c \cdot Z_{RA}^{(1 - T_r)^{2/7}}$$
   *
   * <p>
   * where $Z_{RA} = 0.29056 - 0.08775\omega$.
   * </p>
   *
   * @param temperature the temperature in K
   * @param tc critical temperature in K
   * @param vc critical volume in cm3/mol
   * @param omega acentric factor (dimensionless)
   * @return liquid molar volume in m3/mol
   */
  public static double estimateLiquidMolarVolume(double temperature, double tc, double vc, double omega) {
    double zra = 0.29056 - 0.08775 * omega;
    double tr = temperature / tc;
    // cm3/mol -> m3/mol is 1e-6; the previous 1e-3 made every molar volume 1000x too large, which
    // in turn made the Macleod-Sugden surface tension underflow by 1e-12 (density is raised to 4).
    if (tr < 1.0) {
      return vc * Math.pow(zra, Math.pow(1.0 - tr, 2.0 / 7.0)) * 1e-6;
    } else {
      return vc * 1e-6;
    }
  }

  /**
   * Estimate liquid molar volume for a component in a system.
   *
   * @param system the thermodynamic system
   * @param phaseNum the phase number
   * @param compNum the component index
   * @return liquid molar volume in m3/mol
   */
  public static double estimateLiquidMolarVolume(SystemInterface system, int phaseNum, int compNum) {
    double tc = system.getPhase(phaseNum).getComponent(compNum).getTC();
    double vc = system.getPhase(phaseNum).getComponent(compNum).getCriticalVolume();
    double omega = system.getPhase(phaseNum).getComponent(compNum).getAcentricFactor();
    double temperature = system.getPhase(phaseNum).getTemperature();
    return estimateLiquidMolarVolume(temperature, tc, vc, omega);
  }

  /**
   * Estimate surface tension using the Macleod-Sugden correlation.
   *
   * <p>
   * Uses the parachor method:
   * </p>
   * $$\sigma = \left(\frac{[P] \cdot \rho_L}{M}\right)^4$$
   *
   * <p>
   * Falls back to an empirical correlation when parachor is unavailable:
   * </p>
   * $$\sigma = 0.02 \cdot (1 - T_r)^{1.26}$$
   *
   * @param temperature the temperature in K
   * @param tc critical temperature in K
   * @param parachor the parachor parameter
   * @param liquidMolarVolume liquid molar volume in m3/mol
   * @return surface tension in N/m
   */
  public static double estimateSurfaceTension(double temperature, double tc, double parachor,
      double liquidMolarVolume) {
    double tr = temperature / tc;
    if (parachor > 0 && tr < 1.0 && liquidMolarVolume > 1e-20) {
      double rhoL = 1.0 / liquidMolarVolume;
      // The parachor form yields dyn/cm (= mN/m); the trailing 1e-3 converts to N/m.
      return Math.pow(parachor * rhoL / 1e6, 4.0) * 1e-3;
    } else {
      return 0.02 * Math.pow(Math.max(0.0, 1.0 - tr), 1.26);
    }
  }

  /**
   * Estimate surface tension for a component in a system.
   *
   * @param system the thermodynamic system
   * @param phaseNum the phase number
   * @param compNum the component index
   * @param liquidMolarVolume the liquid molar volume in m3/mol
   * @return surface tension in N/m
   */
  public static double estimateSurfaceTension(SystemInterface system, int phaseNum, int compNum,
      double liquidMolarVolume) {
    double tc = system.getPhase(phaseNum).getComponent(compNum).getTC();
    double parachor = system.getPhase(phaseNum).getComponent(compNum).getParachorParameter();
    double temperature = system.getPhase(phaseNum).getTemperature();
    return estimateSurfaceTension(temperature, tc, parachor, liquidMolarVolume);
  }

  /**
   * Estimate all fluid properties for a component at once.
   *
   * <p>
   * Returns an array of [saturationPressure (bar), liquidMolarVolume (m3/mol), surfaceTension (N/m)].
   * </p>
   *
   * @param system the thermodynamic system
   * @param phaseNum the phase number
   * @param compNum the component index
   * @return double array with [pSat, Vm_liquid, sigma]
   */
  public static double[] estimateAllProperties(SystemInterface system, int phaseNum, int compNum) {
    double pSat = estimateSaturationPressure(system, phaseNum, compNum);
    double vm = estimateLiquidMolarVolume(system, phaseNum, compNum);
    double sigma = estimateSurfaceTension(system, phaseNum, compNum, vm);
    return new double[] { pSat, vm, sigma };
  }

  /**
   * Estimate saturation pressures for all components in a phase.
   *
   * @param system the thermodynamic system
   * @param phaseNum the phase number
   * @return array of saturation pressures in bara
   */
  public static double[] estimateAllSaturationPressures(SystemInterface system, int phaseNum) {
    int numComp = system.getPhase(phaseNum).getNumberOfComponents();
    double[] pSat = new double[numComp];
    for (int comp = 0; comp < numComp; comp++) {
      pSat[comp] = estimateSaturationPressure(system, phaseNum, comp);
    }
    return pSat;
  }
}
