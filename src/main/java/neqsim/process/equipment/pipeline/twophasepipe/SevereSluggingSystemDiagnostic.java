package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;

/**
 * Stability diagnostic for a stratified flowline feeding a liquid-filled riser.
 *
 * <p>
 * The diagnostic implements the quasi-steady stability condition derived by Taitel for severe slugging at a
 * flowline-riser junction:
 * </p>
 *
 * <pre>
 * P_top,critical = phi rho_L g (V_G / (A_r alpha_prime) - H)
 * </pre>
 *
 * <p>
 * Here {@code P_top} is absolute pressure at the riser outlet, {@code phi} is average liquid holdup in the riser,
 * {@code rho_L} is average liquid density, {@code V_G} is the compressible upstream gas volume, {@code A_r} and
 * {@code H} are riser area and vertical height, and {@code alpha_prime} is the void fraction in the gas cap penetrating
 * the riser. The system is stable when the effective top pressure is at least the critical pressure. A non-positive
 * critical pressure is geometrically stable in this reduced model.
 * </p>
 *
 * <p>
 * Validity is limited to a two-phase, low-rate, stratified flowline connected to a liquid-filled, constant-area rising
 * section. The derivation assumes isothermal ideal-gas compression and neglects wall and interfacial shear during the
 * incipient displacement. It is a stability screen, not a dynamic slug-frequency or slug-size model.
 * </p>
 *
 * @see <a href="https://doi.org/10.1016/0301-9322(86)90026-1">Taitel (1986)</a>
 */
public final class SevereSluggingSystemDiagnostic {
  /** Standard gravitational acceleration in m/s2. */
  public static final double STANDARD_GRAVITY = 9.80665;

  private SevereSluggingSystemDiagnostic() {
  }

  /** Diagnostic classification. */
  public enum Status {
    /** The Taitel condition is applicable and predicts stable operation. */
    STABLE,
    /** The Taitel condition is applicable and predicts possible severe slugging. */
    UNSTABLE,
    /** The supplied geometry is not a downflow/level flowline followed by a rising section. */
    NOT_APPLICABLE_INVALID_TOPOLOGY,
    /** The feeding flowline is not stratified. */
    NOT_APPLICABLE_NON_STRATIFIED_FLOWLINE,
    /** Both gas and liquid inventories required by the derivation are not present. */
    NOT_APPLICABLE_SINGLE_PHASE,
    /** Oil-water-gas application is intentionally rejected because it is not validated. */
    NOT_VALIDATED_THREE_PHASE
  }

  /** Immutable input descriptor for one flowline-riser system. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double upstreamGasVolumeM3;
    private final double riserAreaM2;
    private final double riserHeightM;
    private final double separatorPressurePa;
    private final double staticChokePressureDropPa;
    private final double liquidDensityKgPerM3;
    private final double riserLiquidHoldup;
    private final double gasCapVoidFraction;
    private final boolean validFlowlineRiserTopology;
    private final boolean flowlineStratified;
    private final boolean flowlineContainsGasAndLiquid;
    private final boolean threePhase;

    private Input(Builder builder) {
      upstreamGasVolumeM3 = nonNegative(builder.upstreamGasVolumeM3, "upstreamGasVolumeM3");
      riserAreaM2 = positive(builder.riserAreaM2, "riserAreaM2");
      riserHeightM = positive(builder.riserHeightM, "riserHeightM");
      separatorPressurePa = positive(builder.separatorPressurePa, "separatorPressurePa");
      staticChokePressureDropPa = nonNegative(builder.staticChokePressureDropPa, "staticChokePressureDropPa");
      liquidDensityKgPerM3 = positive(builder.liquidDensityKgPerM3, "liquidDensityKgPerM3");
      riserLiquidHoldup = fraction(builder.riserLiquidHoldup, "riserLiquidHoldup", true);
      gasCapVoidFraction = fraction(builder.gasCapVoidFraction, "gasCapVoidFraction", false);
      validFlowlineRiserTopology = builder.validFlowlineRiserTopology;
      flowlineStratified = builder.flowlineStratified;
      flowlineContainsGasAndLiquid = builder.flowlineContainsGasAndLiquid;
      threePhase = builder.threePhase;
    }

    /**
     * Creates a builder. Numeric quantities without documented defaults must be supplied; applicability flags default
     * to false and must be confirmed explicitly.
     */
    public static Builder builder() {
      return new Builder();
    }

    public double getUpstreamGasVolumeM3() {
      return upstreamGasVolumeM3;
    }

    public double getRiserAreaM2() {
      return riserAreaM2;
    }

    public double getRiserHeightM() {
      return riserHeightM;
    }

    /** Returns absolute separator pressure in Pa. */
    public double getSeparatorPressurePa() {
      return separatorPressurePa;
    }

    /** Returns the optional static choke pressure drop in Pa. */
    public double getStaticChokePressureDropPa() {
      return staticChokePressureDropPa;
    }

    public double getLiquidDensityKgPerM3() {
      return liquidDensityKgPerM3;
    }

    public double getRiserLiquidHoldup() {
      return riserLiquidHoldup;
    }

    /** Returns gas-cap void fraction alpha-prime. */
    public double getGasCapVoidFraction() {
      return gasCapVoidFraction;
    }

    public boolean hasValidFlowlineRiserTopology() {
      return validFlowlineRiserTopology;
    }

    public boolean isFlowlineStratified() {
      return flowlineStratified;
    }

    /** Return whether gas and liquid are both present in the feeding flowline. */
    public boolean flowlineContainsGasAndLiquid() {
      return flowlineContainsGasAndLiquid;
    }

    public boolean isThreePhase() {
      return threePhase;
    }

    /** Builder for {@link Input}. */
    public static final class Builder {
      private double upstreamGasVolumeM3 = Double.NaN;
      private double riserAreaM2 = Double.NaN;
      private double riserHeightM = Double.NaN;
      private double separatorPressurePa = Double.NaN;
      private double staticChokePressureDropPa;
      private double liquidDensityKgPerM3 = Double.NaN;
      private double riserLiquidHoldup = Double.NaN;
      private double gasCapVoidFraction = 0.89;
      private boolean validFlowlineRiserTopology;
      private boolean flowlineStratified;
      private boolean flowlineContainsGasAndLiquid;
      private boolean threePhase;

      private Builder() {
      }

      public Builder upstreamGasVolumeM3(double value) {
        upstreamGasVolumeM3 = value;
        return this;
      }

      public Builder riserAreaM2(double value) {
        riserAreaM2 = value;
        return this;
      }

      public Builder riserHeightM(double value) {
        riserHeightM = value;
        return this;
      }

      /** Sets absolute pressure at the riser outlet in Pa. */
      public Builder separatorPressurePa(double value) {
        separatorPressurePa = value;
        return this;
      }

      /** Sets a fixed choke pressure drop in Pa; dynamic choke response is outside the model. */
      public Builder staticChokePressureDropPa(double value) {
        staticChokePressureDropPa = value;
        return this;
      }

      public Builder liquidDensityKgPerM3(double value) {
        liquidDensityKgPerM3 = value;
        return this;
      }

      public Builder riserLiquidHoldup(double value) {
        riserLiquidHoldup = value;
        return this;
      }

      /**
       * Sets alpha-prime. The default 0.89 is the air-water value used in Taitel's comparison; applications outside
       * that basis should provide a justified value.
       */
      public Builder gasCapVoidFraction(double value) {
        gasCapVoidFraction = value;
        return this;
      }

      public Builder validFlowlineRiserTopology(boolean value) {
        validFlowlineRiserTopology = value;
        return this;
      }

      public Builder flowlineStratified(boolean value) {
        flowlineStratified = value;
        return this;
      }

      /** Set whether both phases are present in the feeding flowline. */
      public Builder flowlineContainsGasAndLiquid(boolean value) {
        flowlineContainsGasAndLiquid = value;
        return this;
      }

      public Builder threePhase(boolean value) {
        threePhase = value;
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable diagnostic result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Status status;
    private final double criticalTopPressurePa;
    private final double effectiveTopPressurePa;
    private final double pressureMarginPa;
    private final double stabilityRatio;
    private final double gasExpansionHeadM;

    private Result(Status status, double criticalTopPressurePa, double effectiveTopPressurePa, double pressureMarginPa,
        double stabilityRatio, double gasExpansionHeadM) {
      this.status = status;
      this.criticalTopPressurePa = criticalTopPressurePa;
      this.effectiveTopPressurePa = effectiveTopPressurePa;
      this.pressureMarginPa = pressureMarginPa;
      this.stabilityRatio = stabilityRatio;
      this.gasExpansionHeadM = gasExpansionHeadM;
    }

    public Status getStatus() {
      return status;
    }

    public boolean isApplicable() {
      return status == Status.STABLE || status == Status.UNSTABLE;
    }

    public boolean isStable() {
      return status == Status.STABLE;
    }

    public boolean isSevereSluggingPossible() {
      return status == Status.UNSTABLE;
    }

    public double getCriticalTopPressurePa() {
      return criticalTopPressurePa;
    }

    public double getEffectiveTopPressurePa() {
      return effectiveTopPressurePa;
    }

    public double getPressureMarginPa() {
      return pressureMarginPa;
    }

    public double getStabilityRatio() {
      return stabilityRatio;
    }

    public double getGasExpansionHeadM() {
      return gasExpansionHeadM;
    }
  }

  /**
   * Build a system descriptor from solved pipe sections.
   *
   * <p>
   * Sections before {@code riserBaseSection} form the level or downward-inclined feeder. Their gas-filled cell volumes
   * are summed using each section's own area, so a flowline-to-riser diameter change is permitted. Sections from
   * {@code riserBaseSection} onward must form a continuously rising, constant-area riser. The last section pressure is
   * interpreted as the absolute pressure at the riser outlet.
   * </p>
   *
   * @param sections solved sections in flow direction
   * @param riserBaseSection index of the first continuously rising section
   * @param gasCapVoidFraction void fraction alpha-prime in the penetrating gas cap
   * @param staticChokePressureDropPa fixed pressure drop between the riser outlet and separator, in Pa
   * @return immutable system input with geometry, inventory, phase, and flow-regime evidence
   */
  public static Input fromSections(TwoFluidSection[] sections, int riserBaseSection, double gasCapVoidFraction,
      double staticChokePressureDropPa) {
    if (sections == null || sections.length < 2) {
      throw new IllegalArgumentException("sections must contain a flowline and riser");
    }
    if (riserBaseSection <= 0 || riserBaseSection >= sections.length) {
      throw new IllegalArgumentException("riserBaseSection must be between 1 and sections.length - 1");
    }

    double referenceArea = sections[riserBaseSection].getArea();
    double upstreamGasVolume = 0.0;
    double riserVolume = 0.0;
    double riserLiquidVolume = 0.0;
    double riserLiquidMass = 0.0;
    double riserHeight = 0.0;
    boolean topologyValid = true;
    boolean flowlineStratified = true;
    boolean flowlineContainsGasAndLiquid = false;
    boolean threePhase = false;

    for (int i = 0; i < sections.length; i++) {
      TwoFluidSection section = sections[i];
      if (section == null) {
        throw new IllegalArgumentException("sections must not contain null entries");
      }
      double cellVolume = section.getArea() * section.getLength();
      if (section.getOilHoldup() > 1.0e-10 && section.getWaterHoldup() > 1.0e-10) {
        threePhase = true;
      }

      if (i < riserBaseSection) {
        upstreamGasVolume += section.getGasHoldup() * cellVolume;
        topologyValid &= section.getInclination() <= Math.toRadians(1.0);
        if (section.getGasHoldup() > 1.0e-10 && section.getLiquidHoldup() > 1.0e-10) {
          flowlineContainsGasAndLiquid = true;
          PipeSection.FlowRegime regime = section.getFlowRegime();
          flowlineStratified &= regime == PipeSection.FlowRegime.STRATIFIED_SMOOTH
              || regime == PipeSection.FlowRegime.STRATIFIED_WAVY;
        }
      } else {
        topologyValid &= Math.abs(section.getArea() - referenceArea) <= 1.0e-6 * referenceArea;
        topologyValid &= section.getInclination() > Math.toRadians(1.0);
        riserHeight += Math.sin(section.getInclination()) * section.getLength();
        riserVolume += cellVolume;
        double liquidVolume = section.getLiquidHoldup() * cellVolume;
        riserLiquidVolume += liquidVolume;
        riserLiquidMass += liquidVolume * section.getLiquidDensity();
      }
    }

    flowlineStratified &= flowlineContainsGasAndLiquid;
    double riserLiquidHoldup = riserVolume > 0.0 ? riserLiquidVolume / riserVolume : 0.0;
    double liquidDensity = riserLiquidVolume > 0.0 ? riserLiquidMass / riserLiquidVolume : 1.0;
    topologyValid &= riserHeight > 0.0;

    return Input.builder().upstreamGasVolumeM3(upstreamGasVolume).riserAreaM2(referenceArea)
        .riserHeightM(Math.max(riserHeight, 1.0e-12)).separatorPressurePa(sections[sections.length - 1].getPressure())
        .staticChokePressureDropPa(staticChokePressureDropPa).liquidDensityKgPerM3(liquidDensity)
        .riserLiquidHoldup(riserLiquidHoldup).gasCapVoidFraction(gasCapVoidFraction)
        .validFlowlineRiserTopology(topologyValid).flowlineStratified(flowlineStratified)
        .flowlineContainsGasAndLiquid(flowlineContainsGasAndLiquid).threePhase(threePhase).build();
  }

  /** Evaluates the input using the Taitel quasi-steady stability condition. */
  public static Result evaluate(Input input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (!input.hasValidFlowlineRiserTopology()) {
      return notApplicable(Status.NOT_APPLICABLE_INVALID_TOPOLOGY);
    }
    if (input.isThreePhase()) {
      return notApplicable(Status.NOT_VALIDATED_THREE_PHASE);
    }
    if (input.getUpstreamGasVolumeM3() == 0.0 || input.getRiserLiquidHoldup() == 0.0
        || !input.flowlineContainsGasAndLiquid()) {
      return notApplicable(Status.NOT_APPLICABLE_SINGLE_PHASE);
    }
    if (!input.isFlowlineStratified()) {
      return notApplicable(Status.NOT_APPLICABLE_NON_STRATIFIED_FLOWLINE);
    }

    double effectiveTopPressurePa = input.getSeparatorPressurePa() + input.getStaticChokePressureDropPa();
    double gasExpansionHeadM = input.getUpstreamGasVolumeM3()
        / (input.getRiserAreaM2() * input.getGasCapVoidFraction());
    double destabilizingHeadM = gasExpansionHeadM - input.getRiserHeightM();
    double criticalTopPressurePa = Math.max(0.0,
        input.getRiserLiquidHoldup() * input.getLiquidDensityKgPerM3() * STANDARD_GRAVITY * destabilizingHeadM);
    double pressureMarginPa = effectiveTopPressurePa - criticalTopPressurePa;
    double stabilityRatio = criticalTopPressurePa == 0.0 ? Double.POSITIVE_INFINITY
        : effectiveTopPressurePa / criticalTopPressurePa;
    Status status = pressureMarginPa >= 0.0 ? Status.STABLE : Status.UNSTABLE;
    return new Result(status, criticalTopPressurePa, effectiveTopPressurePa, pressureMarginPa, stabilityRatio,
        gasExpansionHeadM);
  }

  private static Result notApplicable(Status status) {
    return new Result(status, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
  }

  private static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and > 0");
    }
    return value;
  }

  private static double nonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and >= 0");
    }
    return value;
  }

  private static double fraction(double value, String name, boolean allowZero) {
    if (!Double.isFinite(value) || value > 1.0 || (allowZero ? value < 0.0 : value <= 0.0)) {
      throw new IllegalArgumentException(name + (allowZero ? " must be in [0, 1]" : " must be in (0, 1]"));
    }
    return value;
  }
}
