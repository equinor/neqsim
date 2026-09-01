package neqsim.process.equipment.reactor;

import java.io.Serializable;

/**
 * Exact piecewise-isothermal propagation of the qualified aqueous CO2/H2CO3 pair.
 *
 * <p>
 * Each segment delegates to {@link AqueousCO2HydrationKinetics#advance(double, double, double, double)} and therefore
 * uses the Soli-Byrne first-order correlations only inside their published 288.15-305.65 K and 0.65 molal NaCl
 * boundary. The result does not add pressure correction, phase transfer, electrolyte speciation, or a pipeline source
 * term.
 * </p>
 *
 * @see <a href="https://doi.org/10.1016/S0304-4203(02)00010-5">Soli and Byrne (2002), Marine Chemistry 78, 65-73</a>
 * @author NeqSim Team
 * @version 1.0
 */
public final class AqueousCO2HydrationTrajectory implements Serializable {
  private static final long serialVersionUID = 1000L;

  private AqueousCO2HydrationTrajectory() {
  }

  /**
   * Advance a closed aqueous CO2/H2CO3 pair through piecewise-isothermal segments.
   *
   * <p>
   * The cumulative relaxation exposure is {@code sum((kH(T_i) + kD(T_i)) * duration_i)}. It is useful as a
   * dimensionless diagnostic, but a changing temperature also changes the pair equilibrium target. Consequently the
   * exposure must not be interpreted as one global remaining-deviation fraction. Final concentrations are obtained from
   * the ordered sequence of exact analytical updates.
   * </p>
   *
   * @param co2Concentration initial aqueous CO2 concentration [mol/m3]
   * @param carbonicAcidConcentration initial H2CO3 concentration [mol/m3]
   * @param segmentDurationsSeconds ordered segment durations [s]
   * @param temperaturesK ordered aqueous temperatures corresponding to the durations [K]
   * @return immutable trajectory result
   * @throws IllegalArgumentException when concentrations or arrays are invalid, arrays are empty or have different
   * lengths, any duration is negative or non-finite, any temperature is outside the published range, or an accumulated
   * quantity is non-finite
   */
  public static TrajectoryResult advance(double co2Concentration, double carbonicAcidConcentration,
      double[] segmentDurationsSeconds, double[] temperaturesK) {
    requireFiniteNonNegative(co2Concentration, "CO2 concentration");
    requireFiniteNonNegative(carbonicAcidConcentration, "H2CO3 concentration");
    if (segmentDurationsSeconds == null || temperaturesK == null) {
      throw new IllegalArgumentException("trajectory duration and temperature arrays must not be null");
    }
    if (segmentDurationsSeconds.length == 0) {
      throw new IllegalArgumentException("trajectory must contain at least one segment");
    }
    if (segmentDurationsSeconds.length != temperaturesK.length) {
      throw new IllegalArgumentException("trajectory duration and temperature arrays must have the same length");
    }

    double initialTotalConcentration = co2Concentration + carbonicAcidConcentration;
    if (!Double.isFinite(initialTotalConcentration)) {
      throw new IllegalArgumentException("total carbon concentration must be finite");
    }

    for (int index = 0; index < segmentDurationsSeconds.length; index++) {
      requireFiniteNonNegative(segmentDurationsSeconds[index], "segment duration");
      AqueousCO2HydrationKinetics.hydrationRateConstant(temperaturesK[index]);
    }

    double currentCO2 = co2Concentration;
    double currentCarbonicAcid = carbonicAcidConcentration;
    double elapsedTime = 0.0;
    double cumulativeRelaxationExposure = 0.0;
    double minimumTemperature = temperaturesK[0];
    double maximumTemperature = temperaturesK[0];

    for (int index = 0; index < segmentDurationsSeconds.length; index++) {
      double duration = segmentDurationsSeconds[index];
      double temperature = temperaturesK[index];
      double relaxationRate = AqueousCO2HydrationKinetics.hydrationRateConstant(temperature)
          + AqueousCO2HydrationKinetics.dehydrationRateConstant(temperature);

      elapsedTime += duration;
      cumulativeRelaxationExposure += relaxationRate * duration;
      if (!Double.isFinite(elapsedTime) || !Double.isFinite(cumulativeRelaxationExposure)) {
        throw new IllegalArgumentException("accumulated trajectory time and relaxation exposure must be finite");
      }

      AqueousCO2HydrationKinetics.Result segmentResult = AqueousCO2HydrationKinetics.advance(currentCO2,
          currentCarbonicAcid, duration, temperature);
      currentCO2 = segmentResult.getCO2Concentration();
      currentCarbonicAcid = segmentResult.getCarbonicAcidConcentration();
      minimumTemperature = Math.min(minimumTemperature, temperature);
      maximumTemperature = Math.max(maximumTemperature, temperature);
    }

    double finalTotalConcentration = currentCO2 + currentCarbonicAcid;
    return new TrajectoryResult(currentCO2, currentCarbonicAcid, finalTotalConcentration, elapsedTime,
        cumulativeRelaxationExposure, segmentDurationsSeconds.length, minimumTemperature, maximumTemperature,
        initialTotalConcentration - finalTotalConcentration);
  }

  private static void requireFiniteNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  /** Result of an exact piecewise-isothermal aqueous CO2/H2CO3 trajectory. */
  public static final class TrajectoryResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double co2Concentration;
    private final double carbonicAcidConcentration;
    private final double totalMolecularCO2Concentration;
    private final double elapsedTimeSeconds;
    private final double cumulativeRelaxationExposure;
    private final int segmentCount;
    private final double minimumTemperatureK;
    private final double maximumTemperatureK;
    private final double carbonBalanceResidual;

    private TrajectoryResult(double co2Concentration, double carbonicAcidConcentration,
        double totalMolecularCO2Concentration, double elapsedTimeSeconds, double cumulativeRelaxationExposure,
        int segmentCount, double minimumTemperatureK, double maximumTemperatureK, double carbonBalanceResidual) {
      this.co2Concentration = co2Concentration;
      this.carbonicAcidConcentration = carbonicAcidConcentration;
      this.totalMolecularCO2Concentration = totalMolecularCO2Concentration;
      this.elapsedTimeSeconds = elapsedTimeSeconds;
      this.cumulativeRelaxationExposure = cumulativeRelaxationExposure;
      this.segmentCount = segmentCount;
      this.minimumTemperatureK = minimumTemperatureK;
      this.maximumTemperatureK = maximumTemperatureK;
      this.carbonBalanceResidual = carbonBalanceResidual;
    }

    /** @return final aqueous CO2 concentration [mol/m3]. */
    public double getCO2Concentration() {
      return co2Concentration;
    }

    /** @return final carbonic-acid concentration [mol/m3]. */
    public double getCarbonicAcidConcentration() {
      return carbonicAcidConcentration;
    }

    /** @return final lumped {@code CO2(aq) + H2CO3} concentration [mol/m3]. */
    public double getTotalMolecularCO2Concentration() {
      return totalMolecularCO2Concentration;
    }

    /** @return total trajectory time [s]. */
    public double getElapsedTimeSeconds() {
      return elapsedTimeSeconds;
    }

    /** @return dimensionless sum of local reversible-pair relaxation rates multiplied by segment durations. */
    public double getCumulativeRelaxationExposure() {
      return cumulativeRelaxationExposure;
    }

    /** @return number of propagated trajectory segments. */
    public int getSegmentCount() {
      return segmentCount;
    }

    /** @return minimum evaluated trajectory temperature [K]. */
    public double getMinimumTemperatureK() {
      return minimumTemperatureK;
    }

    /** @return maximum evaluated trajectory temperature [K]. */
    public double getMaximumTemperatureK() {
      return maximumTemperatureK;
    }

    /** @return initial minus final molecular-carbon concentration [mol/m3]. */
    public double getCarbonBalanceResidual() {
      return carbonBalanceResidual;
    }
  }
}
