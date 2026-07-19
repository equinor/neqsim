package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Measured export/discharge quality and compliance for one process operating point. */
public final class ProductSpecificationResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final boolean evaluated;
  private final double gasCo2MolePercent;
  private final double gasH2sPpm;
  private final double gasOxygenMolePercent;
  private final double gasWaterDewPointC;
  private final double gasHydrocarbonDewPointC;
  private final double gasGrossCalorificValueMjPerSm3;
  private final double gasWobbeIndexMjPerSm3;
  private final double gasRelativeDensity;
  private final double oilRvpBara;
  private final double oilBswVolumePercent;
  private final double oilInWaterMgPerL;
  private final List<String> violations;

  ProductSpecificationResult(boolean evaluated, double gasCo2MolePercent, double gasH2sPpm, double gasOxygenMolePercent,
      double gasWaterDewPointC, double gasHydrocarbonDewPointC, double gasGrossCalorificValueMjPerSm3,
      double gasWobbeIndexMjPerSm3, double gasRelativeDensity, double oilRvpBara, double oilBswVolumePercent,
      double oilInWaterMgPerL, List<String> violations) {
    this.evaluated = evaluated;
    this.gasCo2MolePercent = gasCo2MolePercent;
    this.gasH2sPpm = gasH2sPpm;
    this.gasOxygenMolePercent = gasOxygenMolePercent;
    this.gasWaterDewPointC = gasWaterDewPointC;
    this.gasHydrocarbonDewPointC = gasHydrocarbonDewPointC;
    this.gasGrossCalorificValueMjPerSm3 = gasGrossCalorificValueMjPerSm3;
    this.gasWobbeIndexMjPerSm3 = gasWobbeIndexMjPerSm3;
    this.gasRelativeDensity = gasRelativeDensity;
    this.oilRvpBara = oilRvpBara;
    this.oilBswVolumePercent = oilBswVolumePercent;
    this.oilInWaterMgPerL = oilInWaterMgPerL;
    this.violations = Collections.unmodifiableList(new ArrayList<String>(violations));
  }

  /** Returns a result used when no product specifications are configured. */
  public static ProductSpecificationResult notEvaluated() {
    return new ProductSpecificationResult(false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Collections.<String>emptyList());
  }

  /** Returns whether quality was evaluated. */
  public boolean isEvaluated() {
    return evaluated;
  }

  /** Returns true when every evaluated limit is satisfied. */
  public boolean isCompliant() {
    return violations.isEmpty();
  }

  /** Returns export-gas CO2 in mole percent. */
  public double getGasCo2MolePercent() {
    return gasCo2MolePercent;
  }

  /** Returns export-gas H2S in ppm molar. */
  public double getGasH2sPpm() {
    return gasH2sPpm;
  }

  /** Returns export-gas oxygen in mole percent. */
  public double getGasOxygenMolePercent() {
    return gasOxygenMolePercent;
  }

  /** Returns gas water dew point in Celsius. */
  public double getGasWaterDewPointC() {
    return gasWaterDewPointC;
  }

  /** Returns gas hydrocarbon dew point in Celsius. */
  public double getGasHydrocarbonDewPointC() {
    return gasHydrocarbonDewPointC;
  }

  /** Returns gas gross calorific value in MJ/Sm3 at 15/25 C reference conditions. */
  public double getGasGrossCalorificValueMjPerSm3() {
    return gasGrossCalorificValueMjPerSm3;
  }

  /** Returns gas superior Wobbe index in MJ/Sm3. */
  public double getGasWobbeIndexMjPerSm3() {
    return gasWobbeIndexMjPerSm3;
  }

  /** Returns gas relative density from ISO 6976. */
  public double getGasRelativeDensity() {
    return gasRelativeDensity;
  }

  /** Returns stabilized-oil RVP in bara. */
  public double getOilRvpBara() {
    return oilRvpBara;
  }

  /** Returns stabilized-oil BS&amp;W in volume percent. */
  public double getOilBswVolumePercent() {
    return oilBswVolumePercent;
  }

  /** Returns treated-water oil-in-water concentration in mg/L. */
  public double getOilInWaterMgPerL() {
    return oilInWaterMgPerL;
  }

  /** Returns immutable specification violation descriptions. */
  public List<String> getViolations() {
    return violations;
  }

  /** Returns a compact compliance summary. */
  public String getSummary() {
    return violations.isEmpty() ? (evaluated ? "on specification" : "not evaluated") : String.join("; ", violations);
  }

  /** Returns a result retaining worst measurements and all violations from both inputs. */
  public ProductSpecificationResult combine(ProductSpecificationResult other) {
    if (other == null || !other.evaluated) {
      return this;
    }
    if (!evaluated) {
      return other;
    }
    Set<String> combinedViolations = new LinkedHashSet<String>(violations);
    combinedViolations.addAll(other.violations);
    return new ProductSpecificationResult(true, maximum(gasCo2MolePercent, other.gasCo2MolePercent),
        maximum(gasH2sPpm, other.gasH2sPpm), maximum(gasOxygenMolePercent, other.gasOxygenMolePercent),
        maximum(gasWaterDewPointC, other.gasWaterDewPointC),
        maximum(gasHydrocarbonDewPointC, other.gasHydrocarbonDewPointC),
        maximum(gasGrossCalorificValueMjPerSm3, other.gasGrossCalorificValueMjPerSm3),
        maximum(gasWobbeIndexMjPerSm3, other.gasWobbeIndexMjPerSm3),
        maximum(gasRelativeDensity, other.gasRelativeDensity), maximum(oilRvpBara, other.oilRvpBara),
        maximum(oilBswVolumePercent, other.oilBswVolumePercent), maximum(oilInWaterMgPerL, other.oilInWaterMgPerL),
        new ArrayList<String>(combinedViolations));
  }

  private static double maximum(double first, double second) {
    if (Double.isNaN(first)) {
      return second;
    }
    if (Double.isNaN(second)) {
      return first;
    }
    return Math.max(first, second);
  }
}

