package neqsim.process.safety.overpressure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable description of a single credible overpressure relief scenario for a protected item.
 *
 * <p>
 * A relief scenario is one row of the API STD 521 section 4.4 / TR3001 section 4.7-4.8 cause table: it records the
 * overpressure cause, the calculated required relief rate, the relieving-fluid properties needed to size a pressure
 * relief device, the relieving phase, whether the rate was determined dynamically (TR3001 SR-26565), the governing
 * standard reference and any assumptions.
 * </p>
 *
 * <p>
 * Scenarios are created with the {@link Builder}. The {@code credible} flag allows a double-jeopardy filter (TR3001
 * section 4.2) to exclude a scenario from the governing-case selection without discarding it from the documented set.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReliefScenario implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String name;
  private final ReliefCause cause;
  private final ReliefPhase phase;
  private final double reliefRateKgPerS;
  private final double reliefTemperatureK;
  private final double molarMassKgPerMol;
  private final double compressibility;
  private final double specificHeatRatio;
  private final double densityKgPerM3;
  private final double viscosityPaS;
  private final double gasMassFraction;
  private final double gasDensityKgPerM3;
  private final double liquidDensityKgPerM3;
  private final double latentHeatJPerKg;
  private final double liquidHeatCapacityJPerKgK;
  private final boolean dynamicallyDetermined;
  private final boolean credible;
  private final String standardReference;
  private final List<String> assumptions;

  /**
   * Creates an immutable relief scenario from a builder.
   *
   * @param builder the populated builder; not null
   */
  private ReliefScenario(Builder builder) {
    this.name = builder.name;
    this.cause = builder.cause;
    this.phase = builder.phase;
    this.reliefRateKgPerS = builder.reliefRateKgPerS;
    this.reliefTemperatureK = builder.reliefTemperatureK;
    this.molarMassKgPerMol = builder.molarMassKgPerMol;
    this.compressibility = builder.compressibility;
    this.specificHeatRatio = builder.specificHeatRatio;
    this.densityKgPerM3 = builder.densityKgPerM3;
    this.viscosityPaS = builder.viscosityPaS;
    this.gasMassFraction = builder.gasMassFraction;
    this.gasDensityKgPerM3 = builder.gasDensityKgPerM3;
    this.liquidDensityKgPerM3 = builder.liquidDensityKgPerM3;
    this.latentHeatJPerKg = builder.latentHeatJPerKg;
    this.liquidHeatCapacityJPerKgK = builder.liquidHeatCapacityJPerKgK;
    this.dynamicallyDetermined = builder.dynamicallyDetermined;
    this.credible = builder.credible;
    this.standardReference = builder.standardReference != null ? builder.standardReference
        : (builder.cause != null ? builder.cause.getStandardReference() : "");
    this.assumptions = Collections.unmodifiableList(new ArrayList<String>(builder.assumptions));
  }

  /**
   * Gets the scenario name.
   *
   * @return the scenario name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the overpressure cause.
   *
   * @return the relief cause
   */
  public ReliefCause getCause() {
    return cause;
  }

  /**
   * Gets the relieving phase.
   *
   * @return the relief phase
   */
  public ReliefPhase getPhase() {
    return phase;
  }

  /**
   * Gets the required relief rate.
   *
   * @return the relief rate in kg/s
   */
  public double getReliefRateKgPerS() {
    return reliefRateKgPerS;
  }

  /**
   * Gets the required relief rate in kg/hr.
   *
   * @return the relief rate in kg/hr
   */
  public double getReliefRateKgPerHr() {
    return reliefRateKgPerS * 3600.0;
  }

  /**
   * Gets the relieving temperature.
   *
   * @return the relieving temperature in K
   */
  public double getReliefTemperatureK() {
    return reliefTemperatureK;
  }

  /**
   * Gets the relieving-fluid molar mass.
   *
   * @return the molar mass in kg/mol
   */
  public double getMolarMassKgPerMol() {
    return molarMassKgPerMol;
  }

  /**
   * Gets the relieving-fluid compressibility factor Z.
   *
   * @return the compressibility factor (dimensionless)
   */
  public double getCompressibility() {
    return compressibility;
  }

  /**
   * Gets the relieving-fluid specific-heat ratio Cp/Cv.
   *
   * @return the specific-heat ratio (dimensionless)
   */
  public double getSpecificHeatRatio() {
    return specificHeatRatio;
  }

  /**
   * Gets the relieving-fluid density (used for liquid relief volume-flow conversion).
   *
   * @return the density in kg/m3, or NaN if not set
   */
  public double getDensityKgPerM3() {
    return densityKgPerM3;
  }

  /**
   * Gets the relieving-liquid dynamic viscosity (used for the API 520 liquid viscosity correction).
   *
   * @return the viscosity in Pa*s, or NaN if not set
   */
  public double getViscosityPaS() {
    return viscosityPaS;
  }

  /**
   * Gets the inlet gas (vapour) mass fraction for two-phase relief sizing.
   *
   * @return the gas mass fraction (0-1), or NaN if not set
   */
  public double getGasMassFraction() {
    return gasMassFraction;
  }

  /**
   * Gets the inlet gas density for two-phase relief sizing.
   *
   * @return the gas density in kg/m3, or NaN if not set
   */
  public double getGasDensityKgPerM3() {
    return gasDensityKgPerM3;
  }

  /**
   * Gets the inlet liquid density for two-phase relief sizing.
   *
   * @return the liquid density in kg/m3, or NaN if not set
   */
  public double getLiquidDensityKgPerM3() {
    return liquidDensityKgPerM3;
  }

  /**
   * Gets the latent heat of vaporization used for fire and flashing two-phase relief.
   *
   * @return the latent heat in J/kg, or NaN if not set
   */
  public double getLatentHeatJPerKg() {
    return latentHeatJPerKg;
  }

  /**
   * Gets the liquid heat capacity used for the two-phase omega method.
   *
   * @return the liquid heat capacity in J/(kg*K), or NaN if not set
   */
  public double getLiquidHeatCapacityJPerKgK() {
    return liquidHeatCapacityJPerKgK;
  }

  /**
   * Indicates whether the relief rate was determined by dynamic simulation (TR3001 SR-26565).
   *
   * @return true if dynamically determined, false if from a steady-state estimate
   */
  public boolean isDynamicallyDetermined() {
    return dynamicallyDetermined;
  }

  /**
   * Indicates whether the scenario is credible and eligible for governing-case selection.
   *
   * @return true if credible, false if excluded (for example by a double-jeopardy filter)
   */
  public boolean isCredible() {
    return credible;
  }

  /**
   * Gets the governing standard reference (TR3001 SR-clause and/or API STD 521 section).
   *
   * @return the standard reference string
   */
  public String getStandardReference() {
    return standardReference;
  }

  /**
   * Gets the list of assumptions recorded for this scenario.
   *
   * @return an unmodifiable list of assumption strings
   */
  public List<String> getAssumptions() {
    return assumptions;
  }

  /**
   * Returns a copy of this scenario with the credible flag set to the supplied value. Used by a double-jeopardy filter
   * to mark a scenario non-credible without mutating the original.
   *
   * @param newCredible the credibility flag for the returned scenario
   * @return a copy of this scenario with the updated credibility flag
   */
  public ReliefScenario withCredible(boolean newCredible) {
    return new Builder(name, cause).phase(phase).reliefRateKgPerS(reliefRateKgPerS)
        .reliefTemperatureK(reliefTemperatureK).molarMassKgPerMol(molarMassKgPerMol).compressibility(compressibility)
        .specificHeatRatio(specificHeatRatio).densityKgPerM3(densityKgPerM3).viscosityPaS(viscosityPaS)
        .gasMassFraction(gasMassFraction).gasDensityKgPerM3(gasDensityKgPerM3)
        .liquidDensityKgPerM3(liquidDensityKgPerM3).latentHeatJPerKg(latentHeatJPerKg)
        .liquidHeatCapacityJPerKgK(liquidHeatCapacityJPerKgK).dynamicallyDetermined(dynamicallyDetermined)
        .credible(newCredible).standardReference(standardReference).assumptions(assumptions).build();
  }

  /**
   * Builder for {@link ReliefScenario}.
   */
  public static final class Builder {
    private final String name;
    private final ReliefCause cause;
    private ReliefPhase phase = ReliefPhase.VAPOUR;
    private double reliefRateKgPerS = 0.0;
    private double reliefTemperatureK = Double.NaN;
    private double molarMassKgPerMol = Double.NaN;
    private double compressibility = 1.0;
    private double specificHeatRatio = 1.3;
    private double densityKgPerM3 = Double.NaN;
    private double viscosityPaS = Double.NaN;
    private double gasMassFraction = Double.NaN;
    private double gasDensityKgPerM3 = Double.NaN;
    private double liquidDensityKgPerM3 = Double.NaN;
    private double latentHeatJPerKg = Double.NaN;
    private double liquidHeatCapacityJPerKgK = Double.NaN;
    private boolean dynamicallyDetermined = false;
    private boolean credible = true;
    private String standardReference = null;
    private final List<String> assumptions = new ArrayList<String>();

    /**
     * Creates a builder for a relief scenario.
     *
     * @param name the scenario name; not null
     * @param cause the overpressure cause; not null
     */
    public Builder(String name, ReliefCause cause) {
      this.name = name;
      this.cause = cause;
    }

    /**
     * Sets the relieving phase.
     *
     * @param phase the relief phase
     * @return this builder for chaining
     */
    public Builder phase(ReliefPhase phase) {
      this.phase = phase;
      return this;
    }

    /**
     * Sets the required relief rate.
     *
     * @param reliefRateKgPerS the relief rate in kg/s
     * @return this builder for chaining
     */
    public Builder reliefRateKgPerS(double reliefRateKgPerS) {
      this.reliefRateKgPerS = reliefRateKgPerS;
      return this;
    }

    /**
     * Sets the relieving temperature.
     *
     * @param reliefTemperatureK the relieving temperature in K
     * @return this builder for chaining
     */
    public Builder reliefTemperatureK(double reliefTemperatureK) {
      this.reliefTemperatureK = reliefTemperatureK;
      return this;
    }

    /**
     * Sets the relieving-fluid molar mass.
     *
     * @param molarMassKgPerMol the molar mass in kg/mol
     * @return this builder for chaining
     */
    public Builder molarMassKgPerMol(double molarMassKgPerMol) {
      this.molarMassKgPerMol = molarMassKgPerMol;
      return this;
    }

    /**
     * Sets the relieving-fluid compressibility factor Z.
     *
     * @param compressibility the compressibility factor (dimensionless)
     * @return this builder for chaining
     */
    public Builder compressibility(double compressibility) {
      this.compressibility = compressibility;
      return this;
    }

    /**
     * Sets the relieving-fluid specific-heat ratio Cp/Cv.
     *
     * @param specificHeatRatio the specific-heat ratio (dimensionless)
     * @return this builder for chaining
     */
    public Builder specificHeatRatio(double specificHeatRatio) {
      this.specificHeatRatio = specificHeatRatio;
      return this;
    }

    /**
     * Sets the relieving-fluid density (used for liquid relief volume-flow conversion).
     *
     * @param densityKgPerM3 the density in kg/m3
     * @return this builder for chaining
     */
    public Builder densityKgPerM3(double densityKgPerM3) {
      this.densityKgPerM3 = densityKgPerM3;
      return this;
    }

    /**
     * Sets the relieving-liquid dynamic viscosity (used for the API 520 liquid viscosity correction).
     *
     * @param viscosityPaS the viscosity in Pa*s
     * @return this builder for chaining
     */
    public Builder viscosityPaS(double viscosityPaS) {
      this.viscosityPaS = viscosityPaS;
      return this;
    }

    /**
     * Sets the inlet gas (vapour) mass fraction for two-phase relief sizing.
     *
     * @param gasMassFraction the gas mass fraction (0-1)
     * @return this builder for chaining
     */
    public Builder gasMassFraction(double gasMassFraction) {
      this.gasMassFraction = gasMassFraction;
      return this;
    }

    /**
     * Sets the inlet gas density for two-phase relief sizing.
     *
     * @param gasDensityKgPerM3 the gas density in kg/m3
     * @return this builder for chaining
     */
    public Builder gasDensityKgPerM3(double gasDensityKgPerM3) {
      this.gasDensityKgPerM3 = gasDensityKgPerM3;
      return this;
    }

    /**
     * Sets the inlet liquid density for two-phase relief sizing.
     *
     * @param liquidDensityKgPerM3 the liquid density in kg/m3
     * @return this builder for chaining
     */
    public Builder liquidDensityKgPerM3(double liquidDensityKgPerM3) {
      this.liquidDensityKgPerM3 = liquidDensityKgPerM3;
      return this;
    }

    /**
     * Sets the latent heat of vaporization used for fire and flashing two-phase relief.
     *
     * @param latentHeatJPerKg the latent heat in J/kg
     * @return this builder for chaining
     */
    public Builder latentHeatJPerKg(double latentHeatJPerKg) {
      this.latentHeatJPerKg = latentHeatJPerKg;
      return this;
    }

    /**
     * Sets the liquid heat capacity used for the two-phase omega method.
     *
     * @param liquidHeatCapacityJPerKgK the liquid heat capacity in J/(kg*K)
     * @return this builder for chaining
     */
    public Builder liquidHeatCapacityJPerKgK(double liquidHeatCapacityJPerKgK) {
      this.liquidHeatCapacityJPerKgK = liquidHeatCapacityJPerKgK;
      return this;
    }

    /**
     * Sets whether the relief rate was determined by dynamic simulation.
     *
     * @param dynamicallyDetermined true if dynamically determined
     * @return this builder for chaining
     */
    public Builder dynamicallyDetermined(boolean dynamicallyDetermined) {
      this.dynamicallyDetermined = dynamicallyDetermined;
      return this;
    }

    /**
     * Sets whether the scenario is credible and eligible for governing-case selection.
     *
     * @param credible true if credible
     * @return this builder for chaining
     */
    public Builder credible(boolean credible) {
      this.credible = credible;
      return this;
    }

    /**
     * Overrides the governing standard reference. When not set, the reference from the cause is used.
     *
     * @param standardReference the standard reference string
     * @return this builder for chaining
     */
    public Builder standardReference(String standardReference) {
      this.standardReference = standardReference;
      return this;
    }

    /**
     * Adds a single assumption string.
     *
     * @param assumption the assumption to record
     * @return this builder for chaining
     */
    public Builder addAssumption(String assumption) {
      if (assumption != null) {
        this.assumptions.add(assumption);
      }
      return this;
    }

    /**
     * Adds all assumption strings from a collection.
     *
     * @param assumptionList the assumptions to record; may be null
     * @return this builder for chaining
     */
    public Builder assumptions(List<String> assumptionList) {
      if (assumptionList != null) {
        this.assumptions.addAll(assumptionList);
      }
      return this;
    }

    /**
     * Builds the immutable relief scenario.
     *
     * @return a new {@link ReliefScenario}
     */
    public ReliefScenario build() {
      return new ReliefScenario(this);
    }
  }
}
