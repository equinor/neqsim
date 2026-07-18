package neqsim.process.engineering.numerics;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controlled tolerances and evidence requirements for numerical engineering closure. */
public final class EngineeringNumericalHealthCriteria implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String massBalanceUnit;
  private final double maximumMassBalanceErrorPercent;
  private final double maximumEnergyBalanceErrorPercent;
  private final double maximumNormalizedResidual;
  private final double maximumJacobianScaleRatio;
  private final boolean requireEnergyClosure;
  private final boolean requireEquationResiduals;
  private final boolean requireSensitivityEvidence;

  private EngineeringNumericalHealthCriteria(Builder builder) {
    massBalanceUnit = builder.massBalanceUnit;
    maximumMassBalanceErrorPercent = builder.maximumMassBalanceErrorPercent;
    maximumEnergyBalanceErrorPercent = builder.maximumEnergyBalanceErrorPercent;
    maximumNormalizedResidual = builder.maximumNormalizedResidual;
    maximumJacobianScaleRatio = builder.maximumJacobianScaleRatio;
    requireEnergyClosure = builder.requireEnergyClosure;
    requireEquationResiduals = builder.requireEquationResiduals;
    requireSensitivityEvidence = builder.requireSensitivityEvidence;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static EngineeringNumericalHealthCriteria defaults() {
    return builder().build();
  }

  public String getMassBalanceUnit() {
    return massBalanceUnit;
  }

  public double getMaximumMassBalanceErrorPercent() {
    return maximumMassBalanceErrorPercent;
  }

  public double getMaximumEnergyBalanceErrorPercent() {
    return maximumEnergyBalanceErrorPercent;
  }

  public double getMaximumNormalizedResidual() {
    return maximumNormalizedResidual;
  }

  public double getMaximumJacobianScaleRatio() {
    return maximumJacobianScaleRatio;
  }

  public boolean isEnergyClosureRequired() {
    return requireEnergyClosure;
  }

  public boolean areEquationResidualsRequired() {
    return requireEquationResiduals;
  }

  public boolean isSensitivityEvidenceRequired() {
    return requireSensitivityEvidence;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("massBalanceUnit", massBalanceUnit);
    result.put("maximumMassBalanceErrorPercent", Double.valueOf(maximumMassBalanceErrorPercent));
    result.put("maximumEnergyBalanceErrorPercent", Double.valueOf(maximumEnergyBalanceErrorPercent));
    result.put("maximumNormalizedResidual", Double.valueOf(maximumNormalizedResidual));
    result.put("maximumJacobianScaleRatio", Double.valueOf(maximumJacobianScaleRatio));
    result.put("requireEnergyClosure", Boolean.valueOf(requireEnergyClosure));
    result.put("requireEquationResiduals", Boolean.valueOf(requireEquationResiduals));
    result.put("requireSensitivityEvidence", Boolean.valueOf(requireSensitivityEvidence));
    return result;
  }

  /** Builder with conservative, explicit defaults. */
  public static final class Builder {
    private String massBalanceUnit = "kg/sec";
    private double maximumMassBalanceErrorPercent = 0.1;
    private double maximumEnergyBalanceErrorPercent = 0.1;
    private double maximumNormalizedResidual = 1.0;
    private double maximumJacobianScaleRatio = 1.0e8;
    private boolean requireEnergyClosure;
    private boolean requireEquationResiduals;
    private boolean requireSensitivityEvidence;

    public Builder massBalanceUnit(String value) {
      if (value == null || value.trim().isEmpty()) {
        throw new IllegalArgumentException("massBalanceUnit must not be blank");
      }
      massBalanceUnit = value.trim();
      return this;
    }

    public Builder maximumMassBalanceErrorPercent(double value) {
      maximumMassBalanceErrorPercent = positive(value, "maximumMassBalanceErrorPercent");
      return this;
    }

    public Builder maximumEnergyBalanceErrorPercent(double value) {
      maximumEnergyBalanceErrorPercent = positive(value, "maximumEnergyBalanceErrorPercent");
      return this;
    }

    public Builder maximumNormalizedResidual(double value) {
      maximumNormalizedResidual = positive(value, "maximumNormalizedResidual");
      return this;
    }

    public Builder maximumJacobianScaleRatio(double value) {
      maximumJacobianScaleRatio = positive(value, "maximumJacobianScaleRatio");
      return this;
    }

    public Builder requireEnergyClosure(boolean value) {
      requireEnergyClosure = value;
      return this;
    }

    public Builder requireEquationResiduals(boolean value) {
      requireEquationResiduals = value;
      return this;
    }

    public Builder requireSensitivityEvidence(boolean value) {
      requireSensitivityEvidence = value;
      return this;
    }

    public EngineeringNumericalHealthCriteria build() {
      return new EngineeringNumericalHealthCriteria(this);
    }

    private static double positive(double value, String name) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException(name + " must be finite and positive");
      }
      return value;
    }
  }
}
