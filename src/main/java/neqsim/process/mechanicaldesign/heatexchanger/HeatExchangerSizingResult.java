package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.Objects;

/**
 * Result structure capturing calculated geometry and performance indicators for a candidate heat
 * exchanger type.
 */
public final class HeatExchangerSizingResult {
  private final HeatExchangerType type;
  private final double requiredArea;
  private final double requiredUA;
  private final double overallHeatTransferCoefficient;
  private final double approachTemperature;
  private final double estimatedLength;
  private final double innerDiameter;
  private final double outerDiameter;
  private final double wallThickness;
  private final int tubeCount;
  private final int tubePasses;
  private final double finSurfaceArea;
  private final double estimatedPressureDrop;
  private final double estimatedWeight;
  private final double moduleLength;
  private final double moduleWidth;
  private final double moduleHeight;

  private HeatExchangerSizingResult(Builder builder) {
    this.type = Objects.requireNonNull(builder.type, "type");
    this.requiredArea = builder.requiredArea;
    this.requiredUA = builder.requiredUA;
    this.overallHeatTransferCoefficient = builder.overallHeatTransferCoefficient;
    this.approachTemperature = builder.approachTemperature;
    this.estimatedLength = builder.estimatedLength;
    this.innerDiameter = builder.innerDiameter;
    this.outerDiameter = builder.outerDiameter;
    this.wallThickness = builder.wallThickness;
    this.tubeCount = builder.tubeCount;
    this.tubePasses = builder.tubePasses;
    this.finSurfaceArea = builder.finSurfaceArea;
    this.estimatedPressureDrop = builder.estimatedPressureDrop;
    this.estimatedWeight = builder.estimatedWeight;
    this.moduleLength = builder.moduleLength;
    this.moduleWidth = builder.moduleWidth;
    this.moduleHeight = builder.moduleHeight;
  }

  public static Builder builder() {
    return new Builder();
  }

  public HeatExchangerType getType() {
    return type;
  }

  public double getRequiredArea() {
    return requiredArea;
  }

  public double getRequiredUA() {
    return requiredUA;
  }

  public double getOverallHeatTransferCoefficient() {
    return overallHeatTransferCoefficient;
  }

  public double getApproachTemperature() {
    return approachTemperature;
  }

  public double getEstimatedLength() {
    return estimatedLength;
  }

  public double getInnerDiameter() {
    return innerDiameter;
  }

  public double getOuterDiameter() {
    return outerDiameter;
  }

  public double getWallThickness() {
    return wallThickness;
  }

  public int getTubeCount() {
    return tubeCount;
  }

  public int getTubePasses() {
    return tubePasses;
  }

  public double getFinSurfaceArea() {
    return finSurfaceArea;
  }

  public double getEstimatedPressureDrop() {
    return estimatedPressureDrop;
  }

  public double getEstimatedWeight() {
    return estimatedWeight;
  }

  public double getModuleLength() {
    return moduleLength;
  }

  public double getModuleWidth() {
    return moduleWidth;
  }

  public double getModuleHeight() {
    return moduleHeight;
  }

  public double getMetric(HeatExchangerMechanicalDesign.SelectionCriterion criterion) {
    switch (Objects.requireNonNull(criterion, "criterion")) {
      case MIN_AREA:
        return requiredArea;
      case MIN_WEIGHT:
        return estimatedWeight;
      case MIN_PRESSURE_DROP:
        return estimatedPressureDrop;
      default:
        throw new IllegalStateException("Unknown selection criterion: " + criterion);
    }
  }

  @Override
  public String toString() {
    return "HeatExchangerSizingResult{" + "type=" + type + ", requiredArea=" + requiredArea
        + ", estimatedWeight=" + estimatedWeight + ", estimatedPressureDrop="
        + estimatedPressureDrop + '}';
  }

  /**
   * Builder for {@link HeatExchangerSizingResult}.
   */
  public static final class Builder {
    private HeatExchangerType type;
    private double requiredArea;
    private double requiredUA;
    private double overallHeatTransferCoefficient;
    private double approachTemperature;
    private double estimatedLength;
    private double innerDiameter;
    private double outerDiameter;
    private double wallThickness;
    private int tubeCount;
    private int tubePasses;
    private double finSurfaceArea;
    private double estimatedPressureDrop;
    private double estimatedWeight;
    private double moduleLength;
    private double moduleWidth;
    private double moduleHeight;

    private Builder() {}

    public Builder type(HeatExchangerType type) {
      this.type = type;
      return this;
    }

    public Builder requiredArea(double requiredArea) {
      this.requiredArea = requiredArea;
      return this;
    }

    public Builder requiredUA(double requiredUA) {
      this.requiredUA = requiredUA;
      return this;
    }

    public Builder overallHeatTransferCoefficient(double overallHeatTransferCoefficient) {
      this.overallHeatTransferCoefficient = overallHeatTransferCoefficient;
      return this;
    }

    public Builder approachTemperature(double approachTemperature) {
      this.approachTemperature = approachTemperature;
      return this;
    }

    public Builder estimatedLength(double estimatedLength) {
      this.estimatedLength = estimatedLength;
      return this;
    }

    public Builder innerDiameter(double innerDiameter) {
      this.innerDiameter = innerDiameter;
      return this;
    }

    public Builder outerDiameter(double outerDiameter) {
      this.outerDiameter = outerDiameter;
      return this;
    }

    public Builder wallThickness(double wallThickness) {
      this.wallThickness = wallThickness;
      return this;
    }

    public Builder tubeCount(int tubeCount) {
      this.tubeCount = tubeCount;
      return this;
    }

    public Builder tubePasses(int tubePasses) {
      this.tubePasses = tubePasses;
      return this;
    }

    public Builder finSurfaceArea(double finSurfaceArea) {
      this.finSurfaceArea = finSurfaceArea;
      return this;
    }

    public Builder estimatedPressureDrop(double estimatedPressureDrop) {
      this.estimatedPressureDrop = estimatedPressureDrop;
      return this;
    }

    public Builder estimatedWeight(double estimatedWeight) {
      this.estimatedWeight = estimatedWeight;
      return this;
    }

    public Builder moduleLength(double moduleLength) {
      this.moduleLength = moduleLength;
      return this;
    }

    public Builder moduleWidth(double moduleWidth) {
      this.moduleWidth = moduleWidth;
      return this;
    }

    public Builder moduleHeight(double moduleHeight) {
      this.moduleHeight = moduleHeight;
      return this;
    }

    public HeatExchangerSizingResult build() {
      return new HeatExchangerSizingResult(this);
    }
  }
}
