package neqsim.process.equipment.reactor.digestion;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.characterization.BioFeedstock;

/**
 * Screening digestion model based on prescribed volatile-solids destruction and methane yield.
 *
 * @author NeqSim team
 * @version 1.0
 */
public class EmpiricalYieldDigestionModel implements AnaerobicDigestionModel {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** {@inheritDoc} */
  @Override
  public AnaerobicDigestionResult calculate(AnaerobicDigestionInput input) {
    BioFeedstock feedstock = input.getFeedstock();
    double feedKgPerDay = input.getWetFeedRateKgPerHr() * 24.0;
    double totalSolids = feedKgPerDay * feedstock.getTotalSolidsFraction();
    double volatileSolids = totalSolids * feedstock.getVolatileSolidsFraction();
    double destruction = Double.isNaN(input.getVsDestructionOverride()) ? feedstock.getMaximumVsDestruction()
        : input.getVsDestructionOverride();
    destruction = Math.max(0.0, Math.min(1.0, destruction));
    double destroyedVs = volatileSolids * destruction;
    double methaneYield = Double.isNaN(input.getMethaneYieldOverride()) ? feedstock.getMethaneYieldNm3PerKgDestroyedVs()
        : input.getMethaneYieldOverride();
    double methaneFraction = Double.isNaN(input.getMethaneFractionOverride()) ? feedstock.getMethaneFraction()
        : input.getMethaneFractionOverride();
    methaneFraction = Math.max(1.0e-6, Math.min(0.999999, methaneFraction));

    double methane = destroyedVs * methaneYield;
    double carbonDioxide = methane * (1.0 - methaneFraction) / methaneFraction;
    double h2s = calculateHydrogenSulfide(input, totalSolids);
    double feedCarbon = totalSolids * feedstock.getCarbonFraction();

    List<String> warnings = new ArrayList<String>();
    warnings.add("Screening model uses prescribed conversion and yield; calibrate before design use");
    return new AnaerobicDigestionResult(getModelIdentifier(), getFidelity(), getEvidenceReference(), feedKgPerDay,
        totalSolids, volatileSolids, destroyedVs, methane, carbonDioxide, h2s, methaneFraction, feedCarbon, warnings);
  }

  /**
   * Converts the configured sulfur release into dry H2S standard volume.
   *
   * @param input digestion input
   * @param totalSolidsKgPerDay total solids in kg/day
   * @return hydrogen sulfide in Nm3/day
   */
  protected double calculateHydrogenSulfide(AnaerobicDigestionInput input, double totalSolidsKgPerDay) {
    double sulfurKgPerDay = totalSolidsKgPerDay * input.getFeedstock().getSulfurFraction()
        * input.getSulfurToGasFraction();
    double h2sKmolPerDay = sulfurKgPerDay / 32.065;
    return h2sKmolPerDay * 22.414;
  }

  /** {@inheritDoc} */
  @Override
  public ModelFidelity getFidelity() {
    return ModelFidelity.SCREENING;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelIdentifier() {
    return "anaerobic-digestion-empirical-yield-v1";
  }
}
