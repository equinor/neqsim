package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.Objects;
import neqsim.process.equipment.heatexchanger.HeatExchanger;

/**
 * Catalogue of supported heat-exchanger configurations and their typical design data.
 */
public enum HeatExchangerType {
  /** Shell-and-tube exchanger with carbon steel tubes. */
  SHELL_AND_TUBE("Shell and tube", 580.0, 5.0, new ShellAndTubeGeometry()),

  /** Plate-and-frame exchanger for clean liquid service. */
  PLATE_AND_FRAME("Plate and frame", 1150.0, 2.0, new PlateAndFrameGeometry()),

  /** Air-cooled fin-fan exchanger. */
  AIR_COOLER("Air cooled", 90.0, 12.0, new AirCoolerGeometry()),

  /** Double-pipe exchanger suitable for smaller duties. */
  DOUBLE_PIPE("Double pipe", 350.0, 8.0, new DoublePipeGeometry());

  private final String displayName;
  private final double typicalOverallHeatTransferCoefficient;
  private final double allowableApproachTemperature;
  private final GeometryModel geometryModel;

  HeatExchangerType(String displayName, double typicalOverallHeatTransferCoefficient,
      double allowableApproachTemperature, GeometryModel geometryModel) {
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.typicalOverallHeatTransferCoefficient = typicalOverallHeatTransferCoefficient;
    this.allowableApproachTemperature = allowableApproachTemperature;
    this.geometryModel = Objects.requireNonNull(geometryModel, "geometryModel");
  }

  public String getDisplayName() {
    return displayName;
  }

  public double getTypicalOverallHeatTransferCoefficient() {
    return typicalOverallHeatTransferCoefficient;
  }

  public double getAllowableApproachTemperature() {
    return allowableApproachTemperature;
  }

  public HeatExchangerSizingResult createSizingResult(HeatExchanger exchanger, double requiredArea,
      double requiredUA, double approachTemperature) {
    return geometryModel.size(this, exchanger, requiredArea, requiredUA, approachTemperature);
  }

  interface GeometryModel {
    HeatExchangerSizingResult size(HeatExchangerType type, HeatExchanger exchanger,
        double requiredArea, double requiredUA, double approachTemperature);
  }

  private static final class ShellAndTubeGeometry implements GeometryModel {
    private static final double TUBE_OUTER_DIAMETER = 0.019; // m
    private static final double DEFAULT_TUBE_LENGTH = 6.0; // m
    private static final double TUBE_PITCH_FACTOR = 1.25; // pitch relative to OD
    private static final double SHELL_WALL_THICKNESS = 0.012; // m
    private static final double STEEL_DENSITY = 7850.0; // kg/m^3

    @Override
    public HeatExchangerSizingResult size(HeatExchangerType type, HeatExchanger exchanger,
        double requiredArea, double requiredUA, double approachTemperature) {
      double tubeLength = Math.max(DEFAULT_TUBE_LENGTH,
          Math.min(requiredArea / (Math.PI * TUBE_OUTER_DIAMETER * 40.0), 12.0));
      int tubeCount = (int) Math.ceil(requiredArea / (Math.PI * TUBE_OUTER_DIAMETER * tubeLength));
      tubeCount = Math.max(tubeCount, 2);
      int tubePasses = tubeCount > 100 ? 4 : 2;
      double tubePitch = TUBE_OUTER_DIAMETER * TUBE_PITCH_FACTOR;
      double bundleDiameter = Math.sqrt(tubeCount) * tubePitch;
      double shellInnerDiameter = Math.max(bundleDiameter, 0.3);
      double shellOuterDiameter = shellInnerDiameter + 2.0 * SHELL_WALL_THICKNESS;

      double wettedShellArea = Math.PI * shellInnerDiameter * tubeLength;
      double shellSteelVolume = wettedShellArea * SHELL_WALL_THICKNESS;
      double estimatedWeight = shellSteelVolume * STEEL_DENSITY;
      double estimatedPressureDrop = 0.05 * tubePasses * tubeLength; // qualitative proxy

      double moduleLength = tubeLength + 1.5;
      double moduleWidth = shellOuterDiameter + 0.8;
      double moduleHeight = shellOuterDiameter + 0.8;

      return HeatExchangerSizingResult.builder().type(type).requiredArea(requiredArea)
          .requiredUA(requiredUA).overallHeatTransferCoefficient(type
              .getTypicalOverallHeatTransferCoefficient())
          .approachTemperature(approachTemperature).tubeCount(tubeCount).tubePasses(tubePasses)
          .innerDiameter(shellInnerDiameter).outerDiameter(shellOuterDiameter)
          .wallThickness(SHELL_WALL_THICKNESS).estimatedLength(tubeLength)
          .estimatedPressureDrop(estimatedPressureDrop).estimatedWeight(estimatedWeight)
          .moduleLength(moduleLength).moduleWidth(moduleWidth).moduleHeight(moduleHeight).build();
    }
  }

  private static final class PlateAndFrameGeometry implements GeometryModel {
    private static final double AREA_PER_PLATE_PAIR = 2.2; // m^2
    private static final double CHANNEL_SPACING = 0.003; // m
    private static final double BASE_WEIGHT_PER_PLATE = 12.0; // kg

    @Override
    public HeatExchangerSizingResult size(HeatExchangerType type, HeatExchanger exchanger,
        double requiredArea, double requiredUA, double approachTemperature) {
      int platePairs = (int) Math.ceil(requiredArea / AREA_PER_PLATE_PAIR);
      platePairs = Math.max(platePairs, 5);
      int plateCount = platePairs * 2 + 1;
      double stackLength = 0.6 + plateCount * CHANNEL_SPACING;
      double moduleHeight = 1.8;
      double moduleWidth = 1.0;
      double estimatedWeight = plateCount * BASE_WEIGHT_PER_PLATE;
      double estimatedPressureDrop = 0.02 * platePairs;
      double equivalentDiameter = Math.sqrt(requiredArea / Math.PI);
      double wallThickness = 0.008;
      double outerDiameter = equivalentDiameter + 2.0 * wallThickness;

      return HeatExchangerSizingResult.builder().type(type).requiredArea(requiredArea)
          .requiredUA(requiredUA).overallHeatTransferCoefficient(type
              .getTypicalOverallHeatTransferCoefficient())
          .approachTemperature(approachTemperature).tubeCount(plateCount).tubePasses(platePairs)
          .innerDiameter(equivalentDiameter).outerDiameter(outerDiameter)
          .wallThickness(wallThickness).estimatedLength(stackLength)
          .estimatedPressureDrop(estimatedPressureDrop).estimatedWeight(estimatedWeight)
          .moduleLength(stackLength).moduleWidth(moduleWidth).moduleHeight(moduleHeight).build();
    }
  }

  private static final class AirCoolerGeometry implements GeometryModel {
    private static final double FIN_SURFACE_MULTIPLIER = 2.5;
    private static final double STRUCTURE_WEIGHT_FACTOR = 45.0; // kg per m^2

    @Override
    public HeatExchangerSizingResult size(HeatExchangerType type, HeatExchanger exchanger,
        double requiredArea, double requiredUA, double approachTemperature) {
      double fanPlanArea = Math.max(requiredArea / FIN_SURFACE_MULTIPLIER, 5.0);
      double moduleWidth = Math.sqrt(fanPlanArea);
      double moduleLength = fanPlanArea / moduleWidth;
      double moduleHeight = 3.2;
      double finSurfaceArea = requiredArea * FIN_SURFACE_MULTIPLIER;
      double estimatedWeight = fanPlanArea * STRUCTURE_WEIGHT_FACTOR;
      double estimatedPressureDrop = 0.01 * requiredArea;
      double equivalentDiameter = Math.sqrt(4.0 * fanPlanArea / Math.PI);
      double wallThickness = 0.006;
      double outerDiameter = equivalentDiameter + 2.0 * wallThickness;

      return HeatExchangerSizingResult.builder().type(type).requiredArea(requiredArea)
          .requiredUA(requiredUA).overallHeatTransferCoefficient(type
              .getTypicalOverallHeatTransferCoefficient())
          .approachTemperature(approachTemperature).tubeCount(0).tubePasses(0)
          .innerDiameter(equivalentDiameter).outerDiameter(outerDiameter)
          .wallThickness(wallThickness).estimatedLength(moduleLength)
          .estimatedPressureDrop(estimatedPressureDrop).estimatedWeight(estimatedWeight)
          .finSurfaceArea(finSurfaceArea).moduleLength(moduleLength).moduleWidth(moduleWidth)
          .moduleHeight(moduleHeight).build();
    }
  }

  private static final class DoublePipeGeometry implements GeometryModel {
    private static final double INNER_PIPE_OUTER_DIAMETER = 0.05; // m
    private static final double ANNULUS_OUTER_DIAMETER = 0.11; // m
    private static final double PIPE_WALL_THICKNESS = 0.005; // m
    private static final double STEEL_DENSITY = 7850.0; // kg/m^3

    @Override
    public HeatExchangerSizingResult size(HeatExchangerType type, HeatExchanger exchanger,
        double requiredArea, double requiredUA, double approachTemperature) {
      double effectivePerimeter = Math.PI * INNER_PIPE_OUTER_DIAMETER;
      double pipeLength = Math.max(requiredArea / effectivePerimeter, 2.5);
      int tubeCount = 2; // inner and annulus passes
      int tubePasses = 2;
      double innerDiameter = INNER_PIPE_OUTER_DIAMETER;
      double outerDiameter = ANNULUS_OUTER_DIAMETER;
      double metalSurfaceArea = Math.PI * outerDiameter * pipeLength;
      double metalVolume = metalSurfaceArea * PIPE_WALL_THICKNESS;
      double estimatedWeight = metalVolume * STEEL_DENSITY;
      double estimatedPressureDrop = 0.08 * pipeLength;
      double moduleLength = pipeLength + 0.5;
      double moduleWidth = outerDiameter + 0.6;
      double moduleHeight = outerDiameter + 0.6;

      return HeatExchangerSizingResult.builder().type(type).requiredArea(requiredArea)
          .requiredUA(requiredUA).overallHeatTransferCoefficient(type
              .getTypicalOverallHeatTransferCoefficient())
          .approachTemperature(approachTemperature).tubeCount(tubeCount).tubePasses(tubePasses)
          .innerDiameter(innerDiameter).outerDiameter(outerDiameter)
          .wallThickness(PIPE_WALL_THICKNESS).estimatedLength(pipeLength)
          .estimatedPressureDrop(estimatedPressureDrop).estimatedWeight(estimatedWeight)
          .moduleLength(moduleLength).moduleWidth(moduleWidth).moduleHeight(moduleHeight).build();
    }
  }
}
