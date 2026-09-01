package neqsim.process.equipment.reactor.digestion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result from an anaerobic-digestion model.
 *
 * <p>
 * The biochemical ledger is dry-gas based. Water subsequently added to the gas by a thermodynamic saturation
 * calculation is reported by the equipment layer and is not part of this result.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class AnaerobicDigestionResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard molar volume used by the existing digestion API in Nm3/kmol. */
  private static final double STANDARD_MOLAR_VOLUME = 22.414;
  /** Methane molecular weight in kg/kmol. */
  private static final double MW_CH4 = 16.043;
  /** Carbon-dioxide molecular weight in kg/kmol. */
  private static final double MW_CO2 = 44.010;
  /** Hydrogen-sulfide molecular weight in kg/kmol. */
  private static final double MW_H2S = 34.081;
  /** Carbon atomic weight in kg/kmol. */
  private static final double MW_C = 12.011;

  /** Model identifier. */
  private final String modelIdentifier;
  /** Model fidelity. */
  private final ModelFidelity fidelity;
  /** Traceable model calibration or evidence reference. */
  private final String modelEvidenceReference;
  /** Wet feed in kg/day. */
  private final double wetFeedKgPerDay;
  /** Total solids in kg/day. */
  private final double totalSolidsKgPerDay;
  /** Volatile solids in kg/day. */
  private final double volatileSolidsKgPerDay;
  /** Destroyed volatile solids in kg/day. */
  private final double destroyedVsKgPerDay;
  /** Actual volatile-solids destruction fraction. */
  private final double vsDestruction;
  /** Methane production in Nm3/day. */
  private final double methaneNm3PerDay;
  /** Carbon-dioxide production in Nm3/day. */
  private final double carbonDioxideNm3PerDay;
  /** Hydrogen-sulfide production in Nm3/day. */
  private final double hydrogenSulfideNm3PerDay;
  /** Dry-gas methane fraction. */
  private final double methaneFraction;
  /** Residual digestate mass in kg/day. */
  private final double digestateKgPerDay;
  /** Residual digestate solids in kg/day. */
  private final double digestateSolidsKgPerDay;
  /** Carbon closure fraction. */
  private final double carbonClosureFraction;
  /** Overall dry biochemical mass closure fraction. */
  private final double massClosureFraction;
  /** Warnings generated during calculation. */
  private final List<String> warnings;

  /**
   * Creates a digestion result and calculates conservation diagnostics.
   *
   * @param modelIdentifier model identifier
   * @param fidelity model fidelity
   * @param wetFeedKgPerDay wet feed in kg/day
   * @param totalSolidsKgPerDay total solids in kg/day
   * @param volatileSolidsKgPerDay volatile solids in kg/day
   * @param destroyedVsKgPerDay destroyed volatile solids in kg/day
   * @param methaneNm3PerDay methane in Nm3/day
   * @param carbonDioxideNm3PerDay carbon dioxide in Nm3/day
   * @param hydrogenSulfideNm3PerDay hydrogen sulfide in Nm3/day
   * @param methaneFraction dry-gas methane fraction
   * @param feedCarbonKgPerDay carbon entering in kg/day
   * @param warnings calculation warnings
   */
  public AnaerobicDigestionResult(String modelIdentifier, ModelFidelity fidelity, double wetFeedKgPerDay,
      double totalSolidsKgPerDay, double volatileSolidsKgPerDay, double destroyedVsKgPerDay, double methaneNm3PerDay,
      double carbonDioxideNm3PerDay, double hydrogenSulfideNm3PerDay, double methaneFraction, double feedCarbonKgPerDay,
      List<String> warnings) {
    this(modelIdentifier, fidelity, "", wetFeedKgPerDay, totalSolidsKgPerDay, volatileSolidsKgPerDay,
        destroyedVsKgPerDay, methaneNm3PerDay, carbonDioxideNm3PerDay, hydrogenSulfideNm3PerDay, methaneFraction,
        feedCarbonKgPerDay, warnings);
  }

  /**
   * Creates an evidence-bearing digestion result and calculates conservation diagnostics.
   *
   * @param modelIdentifier model identifier
   * @param fidelity model fidelity
   * @param modelEvidenceReference calibration or model-evidence reference
   * @param wetFeedKgPerDay wet feed in kg/day
   * @param totalSolidsKgPerDay total solids in kg/day
   * @param volatileSolidsKgPerDay volatile solids in kg/day
   * @param destroyedVsKgPerDay destroyed volatile solids in kg/day
   * @param methaneNm3PerDay methane in Nm3/day
   * @param carbonDioxideNm3PerDay carbon dioxide in Nm3/day
   * @param hydrogenSulfideNm3PerDay hydrogen sulfide in Nm3/day
   * @param methaneFraction dry-gas methane fraction
   * @param feedCarbonKgPerDay carbon entering in kg/day
   * @param warnings calculation warnings
   */
  public AnaerobicDigestionResult(String modelIdentifier, ModelFidelity fidelity, String modelEvidenceReference,
      double wetFeedKgPerDay, double totalSolidsKgPerDay, double volatileSolidsKgPerDay, double destroyedVsKgPerDay,
      double methaneNm3PerDay, double carbonDioxideNm3PerDay, double hydrogenSulfideNm3PerDay, double methaneFraction,
      double feedCarbonKgPerDay, List<String> warnings) {
    this.modelIdentifier = modelIdentifier;
    this.fidelity = fidelity;
    this.modelEvidenceReference = modelEvidenceReference == null ? "" : modelEvidenceReference;
    this.wetFeedKgPerDay = wetFeedKgPerDay;
    this.totalSolidsKgPerDay = totalSolidsKgPerDay;
    this.volatileSolidsKgPerDay = volatileSolidsKgPerDay;
    this.destroyedVsKgPerDay = destroyedVsKgPerDay;
    this.vsDestruction = volatileSolidsKgPerDay > 0.0 ? destroyedVsKgPerDay / volatileSolidsKgPerDay : 0.0;
    this.methaneNm3PerDay = methaneNm3PerDay;
    this.carbonDioxideNm3PerDay = carbonDioxideNm3PerDay;
    this.hydrogenSulfideNm3PerDay = hydrogenSulfideNm3PerDay;
    this.methaneFraction = methaneFraction;

    double gasMass = methaneNm3PerDay / STANDARD_MOLAR_VOLUME * MW_CH4
        + carbonDioxideNm3PerDay / STANDARD_MOLAR_VOLUME * MW_CO2
        + hydrogenSulfideNm3PerDay / STANDARD_MOLAR_VOLUME * MW_H2S;
    digestateKgPerDay = Math.max(0.0, wetFeedKgPerDay - gasMass);
    digestateSolidsKgPerDay = Math.max(0.0, totalSolidsKgPerDay - destroyedVsKgPerDay);
    massClosureFraction = wetFeedKgPerDay > 0.0 ? (digestateKgPerDay + gasMass) / wetFeedKgPerDay : 1.0;

    double carbonInGas = (methaneNm3PerDay + carbonDioxideNm3PerDay) / STANDARD_MOLAR_VOLUME * MW_C;
    double carbonInDigestate = Math.max(0.0, feedCarbonKgPerDay - carbonInGas);
    carbonClosureFraction = feedCarbonKgPerDay > 0.0 ? (carbonInGas + carbonInDigestate) / feedCarbonKgPerDay : 1.0;

    List<String> resultWarnings = new ArrayList<String>();
    if (warnings != null) {
      resultWarnings.addAll(warnings);
    }
    if (carbonInGas > feedCarbonKgPerDay * 1.0001) {
      resultWarnings.add("Specified gas yield requires more carbon than is available in the feedstock analysis");
    }
    if (gasMass > wetFeedKgPerDay * 1.0001) {
      resultWarnings.add("Specified gas yield requires more mass than is available in the wet feed");
    }
    this.warnings = Collections.unmodifiableList(resultWarnings);
  }

  /**
   * Returns a report map.
   *
   * @return result map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("modelIdentifier", modelIdentifier);
    values.put("modelFidelity", fidelity.name());
    values.put("modelEvidenceReference", modelEvidenceReference);
    values.put("wetFeed_kgPerDay", wetFeedKgPerDay);
    values.put("totalSolids_kgPerDay", totalSolidsKgPerDay);
    values.put("volatileSolids_kgPerDay", volatileSolidsKgPerDay);
    values.put("destroyedVS_kgPerDay", destroyedVsKgPerDay);
    values.put("vsDestruction", vsDestruction);
    values.put("methane_Nm3PerDay", methaneNm3PerDay);
    values.put("carbonDioxide_Nm3PerDay", carbonDioxideNm3PerDay);
    values.put("hydrogenSulfide_Nm3PerDay", hydrogenSulfideNm3PerDay);
    values.put("methaneFraction", methaneFraction);
    values.put("digestate_kgPerDay", digestateKgPerDay);
    values.put("digestateSolids_kgPerDay", digestateSolidsKgPerDay);
    values.put("massClosureFraction", massClosureFraction);
    values.put("carbonClosureFraction", carbonClosureFraction);
    values.put("warnings", warnings);
    return values;
  }

  /** @return model identifier */
  public String getModelIdentifier() {
    return modelIdentifier;
  }

  /** @return model fidelity */
  public ModelFidelity getFidelity() {
    return fidelity;
  }

  /** @return calibration or model-evidence reference, or an empty string when unavailable */
  public String getModelEvidenceReference() {
    return modelEvidenceReference;
  }

  /** @return wet feed in kg/day */
  public double getWetFeedKgPerDay() {
    return wetFeedKgPerDay;
  }

  /** @return total solids in kg/day */
  public double getTotalSolidsKgPerDay() {
    return totalSolidsKgPerDay;
  }

  /** @return volatile solids in kg/day */
  public double getVolatileSolidsKgPerDay() {
    return volatileSolidsKgPerDay;
  }

  /** @return destroyed volatile solids in kg/day */
  public double getDestroyedVsKgPerDay() {
    return destroyedVsKgPerDay;
  }

  /** @return actual VS-destruction fraction */
  public double getVsDestruction() {
    return vsDestruction;
  }

  /** @return methane production in Nm3/day */
  public double getMethaneNm3PerDay() {
    return methaneNm3PerDay;
  }

  /** @return carbon-dioxide production in Nm3/day */
  public double getCarbonDioxideNm3PerDay() {
    return carbonDioxideNm3PerDay;
  }

  /** @return hydrogen-sulfide production in Nm3/day */
  public double getHydrogenSulfideNm3PerDay() {
    return hydrogenSulfideNm3PerDay;
  }

  /** @return dry-gas methane fraction */
  public double getMethaneFraction() {
    return methaneFraction;
  }

  /** @return dry biogas in Nm3/day */
  public double getDryBiogasNm3PerDay() {
    return methaneNm3PerDay + carbonDioxideNm3PerDay + hydrogenSulfideNm3PerDay;
  }

  /** @return digestate in kg/day */
  public double getDigestateKgPerDay() {
    return digestateKgPerDay;
  }

  /** @return digestate solids in kg/day */
  public double getDigestateSolidsKgPerDay() {
    return digestateSolidsKgPerDay;
  }

  /** @return biochemical mass closure fraction */
  public double getMassClosureFraction() {
    return massClosureFraction;
  }

  /** @return carbon closure fraction */
  public double getCarbonClosureFraction() {
    return carbonClosureFraction;
  }

  /** @return immutable calculation warnings */
  public List<String> getWarnings() {
    return warnings;
  }
}
