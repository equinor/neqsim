package neqsim.process.safety.overpressure;

/**
 * Package-private helper providing compressible nozzle/orifice mass-flow relations used by the TR3001 overpressure
 * relief-load calculators.
 *
 * <p>
 * The relations are the standard isentropic nozzle (API 520 critical-flow) equations. When the downstream/upstream
 * pressure ratio falls at or below the critical ratio the flow is choked (sonic); otherwise a sub-critical relation is
 * used. These are screening relations: real valve trim geometry, friction and two-phase flashing are not modelled.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
final class NozzleFlow {
  /** Universal gas constant in J/(mol K). */
  static final double R_GAS = 8.314462618;

  /**
   * Private constructor to prevent instantiation of this static-only helper.
   */
  private NozzleFlow() {
  }

  /**
   * Computes the critical (choked) pressure ratio P2/P1 for an ideal gas.
   *
   * @param k specific-heat ratio Cp/Cv; must be greater than 1
   * @return the critical pressure ratio (dimensionless)
   */
  static double criticalPressureRatio(double k) {
    return Math.pow(2.0 / (k + 1.0), k / (k - 1.0));
  }

  /**
   * Computes the gas mass flow rate through a fully open restriction using the isentropic nozzle relations. Selects the
   * choked or sub-critical branch automatically from the pressure ratio.
   *
   * @param dischargeCoefficient discharge coefficient Cd in (0, 1]
   * @param areaM2 effective flow area in m^2; must be positive
   * @param upstreamPressurePa upstream (stagnation) absolute pressure in Pa; must be positive
   * @param downstreamPressurePa downstream absolute pressure in Pa; must be non-negative
   * @param upstreamTemperatureK upstream temperature in K; must be positive
   * @param molarMassKgPerMol gas molar mass in kg/mol; must be positive
   * @param specificHeatRatio specific-heat ratio Cp/Cv; must be greater than 1
   * @return the gas mass flow rate in kg/s
   */
  static double gasMassRateKgPerS(double dischargeCoefficient, double areaM2, double upstreamPressurePa,
      double downstreamPressurePa, double upstreamTemperatureK, double molarMassKgPerMol, double specificHeatRatio) {
    double k = specificHeatRatio;
    double ratio = downstreamPressurePa / upstreamPressurePa;
    double critical = criticalPressureRatio(k);
    if (ratio <= critical) {
      return dischargeCoefficient * areaM2 * upstreamPressurePa * Math.sqrt(
          k * molarMassKgPerMol / (R_GAS * upstreamTemperatureK) * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    }
    double term = Math.pow(ratio, 2.0 / k) - Math.pow(ratio, (k + 1.0) / k);
    if (term < 0.0) {
      term = 0.0;
    }
    return dischargeCoefficient * areaM2 * upstreamPressurePa
        * Math.sqrt(2.0 * molarMassKgPerMol / (R_GAS * upstreamTemperatureK) * (k / (k - 1.0)) * term);
  }

  /**
   * Returns true if the flow is choked for the supplied pressures and specific-heat ratio.
   *
   * @param upstreamPressurePa upstream absolute pressure in Pa; must be positive
   * @param downstreamPressurePa downstream absolute pressure in Pa; must be non-negative
   * @param specificHeatRatio specific-heat ratio Cp/Cv; must be greater than 1
   * @return true if the flow is choked (sonic), false if sub-critical
   */
  static boolean isChoked(double upstreamPressurePa, double downstreamPressurePa, double specificHeatRatio) {
    return downstreamPressurePa / upstreamPressurePa <= criticalPressureRatio(specificHeatRatio);
  }

  /**
   * Computes incompressible liquid mass flow through a restriction using the Bernoulli orifice relation.
   *
   * @param dischargeCoefficient discharge coefficient Cd in (0, 1]
   * @param areaM2 effective flow area in m^2; must be positive
   * @param upstreamPressurePa upstream absolute pressure in Pa; must be positive
   * @param downstreamPressurePa downstream absolute pressure in Pa; must be non-negative
   * @param liquidDensityKgPerM3 liquid density in kg/m^3; must be positive
   * @return the liquid mass flow rate in kg/s
   */
  static double liquidMassRateKgPerS(double dischargeCoefficient, double areaM2, double upstreamPressurePa,
      double downstreamPressurePa, double liquidDensityKgPerM3) {
    double dp = upstreamPressurePa - downstreamPressurePa;
    if (dp < 0.0) {
      dp = 0.0;
    }
    return dischargeCoefficient * areaM2 * Math.sqrt(2.0 * liquidDensityKgPerM3 * dp);
  }
}
