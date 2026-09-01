package neqsim.process.engineering.piping;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned project rules used by network-level piping candidate selection. */
public final class PipingRulePack implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final String standard;
  private final String edition;
  private final double maximumGasVelocityMPerS;
  private final double maximumLiquidVelocityMPerS;
  private final double minimumLiquidVelocityMPerS;
  private final double maximumPressureGradientBarPerKm;
  private final double maximumReliefInletLossFraction;

  private PipingRulePack(Builder builder) {
    id = text(builder.id, "id");
    standard = text(builder.standard, "standard");
    edition = text(builder.edition, "edition");
    maximumGasVelocityMPerS = positive(builder.maximumGasVelocityMPerS, "maximumGasVelocityMPerS");
    maximumLiquidVelocityMPerS = positive(builder.maximumLiquidVelocityMPerS, "maximumLiquidVelocityMPerS");
    minimumLiquidVelocityMPerS = nonNegative(builder.minimumLiquidVelocityMPerS, "minimumLiquidVelocityMPerS");
    maximumPressureGradientBarPerKm = positive(builder.maximumPressureGradientBarPerKm,
        "maximumPressureGradientBarPerKm");
    maximumReliefInletLossFraction = positive(builder.maximumReliefInletLossFraction, "maximumReliefInletLossFraction");
  }

  /** Default offshore rule pack; numerical limits remain project-overridable inputs. */
  public static PipingRulePack norsokP0022023Ac2024() {
    return builder("norsok-p002-2023-ac2024").standard("NORSOK P-002").edition("2023+AC:2024").build();
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public double maximumVelocity(boolean gasService) {
    return gasService ? maximumGasVelocityMPerS : maximumLiquidVelocityMPerS;
  }

  public double getMinimumLiquidVelocityMPerS() {
    return minimumLiquidVelocityMPerS;
  }

  public double getMaximumPressureGradientBarPerKm() {
    return maximumPressureGradientBarPerKm;
  }

  public double getMaximumReliefInletLossFraction() {
    return maximumReliefInletLossFraction;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("standard", standard);
    result.put("edition", edition);
    result.put("maximumGasVelocityMPerS", Double.valueOf(maximumGasVelocityMPerS));
    result.put("maximumLiquidVelocityMPerS", Double.valueOf(maximumLiquidVelocityMPerS));
    result.put("minimumLiquidVelocityMPerS", Double.valueOf(minimumLiquidVelocityMPerS));
    result.put("maximumPressureGradientBarPerKm", Double.valueOf(maximumPressureGradientBarPerKm));
    result.put("maximumReliefInletLossFraction", Double.valueOf(maximumReliefInletLossFraction));
    result.put("projectRuleConfirmationRequired", Boolean.TRUE);
    return result;
  }

  /** Builder for controlled project rule packs. */
  public static final class Builder {
    private final String id;
    private String standard = "PROJECT PIPING RULES";
    private String edition = "working";
    private double maximumGasVelocityMPerS = 20.0;
    private double maximumLiquidVelocityMPerS = 5.0;
    private double minimumLiquidVelocityMPerS;
    private double maximumPressureGradientBarPerKm = 0.5;
    private double maximumReliefInletLossFraction = 0.03;

    private Builder(String id) {
      this.id = id;
    }

    public Builder standard(String value) {
      standard = value;
      return this;
    }

    public Builder edition(String value) {
      edition = value;
      return this;
    }

    public Builder velocityLimits(double gas, double liquid, double minimumLiquid) {
      maximumGasVelocityMPerS = gas;
      maximumLiquidVelocityMPerS = liquid;
      minimumLiquidVelocityMPerS = minimumLiquid;
      return this;
    }

    public Builder maximumPressureGradientBarPerKm(double value) {
      maximumPressureGradientBarPerKm = value;
      return this;
    }

    public Builder maximumReliefInletLossFraction(double value) {
      maximumReliefInletLossFraction = value;
      return this;
    }

    public PipingRulePack build() {
      return new PipingRulePack(this);
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
