package neqsim.process.equipment.solidhandling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.thermo.characterization.BioFeedstock;

/**
 * Screening unit for dewatering, size reduction, handling, and densification of biological feedstocks.
 *
 * <p>
 * The unit provides a transparent mass and energy ledger before a conversion process. Energy intensities are explicit
 * scenario inputs rather than embedded technology claims. The model is suitable for concept comparison; project data
 * should replace the default screening values before design use.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class BioFeedstockPreparation extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Feedstock characterization. */
  private BioFeedstock feedstock;
  /** As-received feed rate in kg/hr. */
  private double wetFeedRateKgPerHr;
  /** Target total-solids mass fraction. */
  private double targetTotalSolidsFraction = Double.NaN;
  /** Target prepared density in kg/m3. */
  private double targetDensityKgPerM3 = Double.NaN;
  /** Electricity for handling and size reduction in kWh/t as received. */
  private double handlingEnergyKWhPerTonne = 15.0;
  /** Densification electricity in kWh/t dry solids. */
  private double densificationEnergyKWhPerTonneDrySolids = 70.0;
  /** Water-removal energy in kWh/kg water removed. */
  private double waterRemovalEnergyKWhPerKg = 0.75;
  /** Dry-solids loss fraction. */
  private double drySolidsLossFraction = 0.0;

  /** Prepared feedstock characterization. */
  private BioFeedstock preparedFeedstock;
  /** Prepared wet feed in kg/hr. */
  private double preparedFeedRateKgPerHr;
  /** Water removed in kg/hr. */
  private double waterRemovedKgPerHr;
  /** Dry solids lost in kg/hr. */
  private double drySolidsLostKgPerHr;
  /** Preparation power in kW. */
  private double powerKW;
  /** Inlet bulk volume in m3/hr. */
  private double inletBulkVolumeM3PerHr;
  /** Prepared bulk volume in m3/hr. */
  private double preparedBulkVolumeM3PerHr;
  /** Overall mass closure fraction. */
  private double massClosureFraction = Double.NaN;

  /**
   * Creates a feedstock preparation unit.
   *
   * @param name equipment name
   */
  public BioFeedstockPreparation(String name) {
    super(name);
  }

  /**
   * Sets the feedstock and as-received mass rate.
   *
   * @param characterizedFeedstock feedstock characterization
   * @param feedRateKgPerHr as-received feed rate in kg/hr
   */
  public void setFeedstock(BioFeedstock characterizedFeedstock, double feedRateKgPerHr) {
    if (characterizedFeedstock == null) {
      throw new IllegalArgumentException("Feedstock must be provided");
    }
    if (feedRateKgPerHr <= 0.0) {
      throw new IllegalArgumentException("Feed rate must be positive");
    }
    characterizedFeedstock.validate();
    feedstock = characterizedFeedstock;
    wetFeedRateKgPerHr = feedRateKgPerHr;
  }

  /**
   * Sets the target total-solids fraction after water removal.
   *
   * @param fraction target total-solids mass fraction
   */
  public void setTargetTotalSolidsFraction(double fraction) {
    if (fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Target total-solids fraction must be greater than zero and at most one");
    }
    targetTotalSolidsFraction = fraction;
  }

  /**
   * Sets the target prepared bulk density.
   *
   * @param densityKgPerM3 target density in kg/m3
   */
  public void setTargetDensityKgPerM3(double densityKgPerM3) {
    if (densityKgPerM3 <= 0.0) {
      throw new IllegalArgumentException("Target density must be positive");
    }
    targetDensityKgPerM3 = densityKgPerM3;
  }

  /**
   * Sets the preparation energy intensities.
   *
   * @param handlingKWhPerTonne energy for handling and size reduction per tonne as received
   * @param densificationKWhPerTonneDry energy for densification per tonne retained dry solids
   * @param waterRemovalKWhPerKg energy per kg water removed
   */
  public void setEnergyIntensities(double handlingKWhPerTonne, double densificationKWhPerTonneDry,
      double waterRemovalKWhPerKg) {
    if (handlingKWhPerTonne < 0.0 || densificationKWhPerTonneDry < 0.0 || waterRemovalKWhPerKg < 0.0) {
      throw new IllegalArgumentException("Preparation energy intensities cannot be negative");
    }
    handlingEnergyKWhPerTonne = handlingKWhPerTonne;
    densificationEnergyKWhPerTonneDrySolids = densificationKWhPerTonneDry;
    waterRemovalEnergyKWhPerKg = waterRemovalKWhPerKg;
  }

  /**
   * Sets the dry-solids loss fraction during preparation.
   *
   * @param fraction loss fraction between zero and one
   */
  public void setDrySolidsLossFraction(double fraction) {
    if (fraction < 0.0 || fraction >= 1.0) {
      throw new IllegalArgumentException("Dry-solids loss fraction must be at least zero and less than one");
    }
    drySolidsLossFraction = fraction;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (feedstock == null || wetFeedRateKgPerHr <= 0.0) {
      throw new IllegalStateException("Feedstock and positive feed rate must be configured before running");
    }

    double inletDrySolids = wetFeedRateKgPerHr * feedstock.getTotalSolidsFraction();
    double inletWater = wetFeedRateKgPerHr - inletDrySolids;
    drySolidsLostKgPerHr = inletDrySolids * drySolidsLossFraction;
    double retainedDrySolids = inletDrySolids - drySolidsLostKgPerHr;
    double targetSolids = Double.isNaN(targetTotalSolidsFraction) ? feedstock.getTotalSolidsFraction()
        : Math.max(feedstock.getTotalSolidsFraction(), targetTotalSolidsFraction);
    double targetWater = retainedDrySolids * (1.0 - targetSolids) / targetSolids;
    waterRemovedKgPerHr = Math.max(0.0, inletWater - targetWater);
    double retainedWater = inletWater - waterRemovedKgPerHr;
    preparedFeedRateKgPerHr = retainedDrySolids + retainedWater;

    double preparedSolidsFraction = retainedDrySolids / preparedFeedRateKgPerHr;
    double preparedDensity = Double.isNaN(targetDensityKgPerM3) ? feedstock.getPreparedDensityKgPerM3()
        : targetDensityKgPerM3;
    preparedFeedstock = feedstock.copy();
    preparedFeedstock.setSolidsAnalysis(preparedSolidsFraction, feedstock.getVolatileSolidsFraction(),
        feedstock.getMaximumVsDestruction(), feedstock.getAshFraction());
    preparedFeedstock.setHandlingProperties(feedstock.getBulkDensityKgPerM3(), preparedDensity);
    preparedFeedstock.setEvidenceReference(feedstock.getEvidenceReference() + "; preparation scenario");

    powerKW = wetFeedRateKgPerHr / 1000.0 * handlingEnergyKWhPerTonne
        + retainedDrySolids / 1000.0 * densificationEnergyKWhPerTonneDrySolids
        + waterRemovedKgPerHr * waterRemovalEnergyKWhPerKg;
    inletBulkVolumeM3PerHr = wetFeedRateKgPerHr / feedstock.getBulkDensityKgPerM3();
    preparedBulkVolumeM3PerHr = preparedFeedRateKgPerHr / preparedDensity;
    massClosureFraction = (preparedFeedRateKgPerHr + waterRemovedKgPerHr + drySolidsLostKgPerHr) / wetFeedRateKgPerHr;

    getEnergyStream().setDuty(powerKW * 1000.0);
    setCalculationIdentifier(id);
  }

  /**
   * Returns the prepared feedstock characterization.
   *
   * @return prepared feedstock, or null before the first run
   */
  public BioFeedstock getPreparedFeedstock() {
    return preparedFeedstock;
  }

  /**
   * Returns the prepared wet feed rate.
   *
   * @return prepared feed rate in kg/hr
   */
  public double getPreparedFeedRateKgPerHr() {
    return preparedFeedRateKgPerHr;
  }

  /**
   * Returns water removed during preparation.
   *
   * @return water removed in kg/hr
   */
  public double getWaterRemovedKgPerHr() {
    return waterRemovedKgPerHr;
  }

  /**
   * Returns total preparation power.
   *
   * @return power in kW
   */
  public double getPowerKW() {
    return powerKW;
  }

  /**
   * Returns the mass-closure fraction.
   *
   * @return mass closure fraction
   */
  public double getMassClosureFraction() {
    return massClosureFraction;
  }

  /**
   * Returns a report-ready result map.
   *
   * @return result map
   */
  public Map<String, Object> getResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("feedstock", feedstock == null ? null : feedstock.getName());
    results.put("wetFeed_kgPerHr", wetFeedRateKgPerHr);
    results.put("preparedFeed_kgPerHr", preparedFeedRateKgPerHr);
    results.put("waterRemoved_kgPerHr", waterRemovedKgPerHr);
    results.put("drySolidsLost_kgPerHr", drySolidsLostKgPerHr);
    results.put("power_kW", powerKW);
    results.put("inletBulkVolume_m3PerHr", inletBulkVolumeM3PerHr);
    results.put("preparedBulkVolume_m3PerHr", preparedBulkVolumeM3PerHr);
    results.put("massClosureFraction", massClosureFraction);
    return results;
  }
}
