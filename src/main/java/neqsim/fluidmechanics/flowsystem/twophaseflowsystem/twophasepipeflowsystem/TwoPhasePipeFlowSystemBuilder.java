package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import neqsim.fluidmechanics.flownode.FlowPattern;
import neqsim.fluidmechanics.flownode.FlowPatternModel;
import neqsim.fluidmechanics.flownode.WallHeatTransferModel;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;

/**
 * Builder class for creating TwoPhasePipeFlowSystem instances with a fluent API.
 *
 * <p>
 * This builder provides a convenient way to configure and create two-phase pipe flow simulations.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * TwoPhasePipeFlowSystem pipe =
 *     TwoPhasePipeFlowSystemBuilder.create().withFluid(thermoSystem).withDiameter(0.1, "m")
 *         .withLength(1000, "m").withNodes(100).withFlowPattern(FlowPattern.STRATIFIED)
 *         .withWallTemperature(278, "K").enableNonEquilibriumMassTransfer().build();
 * </pre>
 *
 * @author ASMF
 * @version 1.0
 */
public class TwoPhasePipeFlowSystemBuilder {

  private SystemInterface fluid;
  private double diameter = 0.1; // m
  private double length = 100.0; // m
  private int numberOfLegs = 1;
  private int nodesPerLeg = 25;
  private FlowPattern flowPattern = FlowPattern.STRATIFIED;
  private FlowPatternModel flowPatternModel = FlowPatternModel.MANUAL;
  private boolean autoFlowPatternDetection = false;
  private WallHeatTransferModel wallHeatTransferModel = WallHeatTransferModel.CONVECTIVE_BOUNDARY;
  private double wallTemperature = 288.15; // K
  private double ambientTemperature = 288.15; // K
  private double overallHeatTransferCoefficient = 10.0; // W/(m²·K)
  private double heatFlux = 0.0; // W/m²
  private boolean nonEquilibriumMassTransfer = false;
  private boolean nonEquilibriumHeatTransfer = false;
  private double roughness = 1e-5; // m
  private double inclination = 0.0; // radians (positive = upward flow)
  private double[] legHeights = null;
  private double[] legPositions = null;
  private double[] outerTemperatures = null;

  /**
   * Private constructor - use create() method.
   */
  private TwoPhasePipeFlowSystemBuilder() {}

  /**
   * Creates a new builder instance.
   *
   * @return a new TwoPhasePipeFlowSystemBuilder
   */
  public static TwoPhasePipeFlowSystemBuilder create() {
    return new TwoPhasePipeFlowSystemBuilder();
  }

  /**
   * Sets the fluid/thermodynamic system.
   *
   * @param system the thermodynamic system
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withFluid(SystemInterface system) {
    this.fluid = system;
    return this;
  }

  /**
   * Sets the pipe diameter.
   *
   * @param diameter the diameter value
   * @param unit the unit ("m", "mm", "in", "ft")
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withDiameter(double diameter, String unit) {
    this.diameter = convertLength(diameter, unit);
    return this;
  }

  /**
   * Sets the pipe length.
   *
   * @param length the length value
   * @param unit the unit ("m", "km", "ft", "mi")
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withLength(double length, String unit) {
    this.length = convertLength(length, unit);
    return this;
  }

  /**
   * Sets the number of calculation nodes.
   *
   * @param nodes the total number of nodes
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withNodes(int nodes) {
    this.nodesPerLeg = nodes;
    this.numberOfLegs = 1;
    return this;
  }

  /**
   * Sets the number of legs and nodes per leg.
   *
   * @param legs the number of pipe legs
   * @param nodesPerLeg the number of nodes per leg
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withLegs(int legs, int nodesPerLeg) {
    this.numberOfLegs = legs;
    this.nodesPerLeg = nodesPerLeg;
    return this;
  }

  /**
   * Sets the flow pattern.
   *
   * @param pattern the flow pattern
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withFlowPattern(FlowPattern pattern) {
    this.flowPattern = pattern;
    this.flowPatternModel = FlowPatternModel.MANUAL;
    return this;
  }

  /**
   * Sets the flow pattern by name.
   *
   * @param patternName the flow pattern name
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withFlowPattern(String patternName) {
    this.flowPattern = FlowPattern.fromString(patternName);
    this.flowPatternModel = FlowPatternModel.MANUAL;
    return this;
  }

  /**
   * Enables automatic flow pattern detection.
   *
   * @param model the flow pattern prediction model
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withAutomaticFlowPatternDetection(FlowPatternModel model) {
    this.flowPatternModel = model;
    this.autoFlowPatternDetection = true;
    return this;
  }

  /**
   * Sets the wall heat transfer model to constant wall temperature.
   *
   * @param temperature the wall temperature
   * @param unit the temperature unit ("K", "C", "F")
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withWallTemperature(double temperature, String unit) {
    this.wallTemperature = convertTemperature(temperature, unit);
    this.wallHeatTransferModel = WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE;
    return this;
  }

  /**
   * Sets the wall heat transfer model to constant heat flux.
   *
   * @param heatFlux the heat flux in W/m²
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withHeatFlux(double heatFlux) {
    this.heatFlux = heatFlux;
    this.wallHeatTransferModel = WallHeatTransferModel.CONSTANT_HEAT_FLUX;
    return this;
  }

  /**
   * Sets up convective boundary condition.
   *
   * @param ambientTemperature the ambient temperature
   * @param tempUnit the temperature unit ("K", "C", "F")
   * @param uValue the overall heat transfer coefficient in W/(m²·K)
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withConvectiveBoundary(double ambientTemperature,
      String tempUnit, double uValue) {
    this.ambientTemperature = convertTemperature(ambientTemperature, tempUnit);
    this.overallHeatTransferCoefficient = uValue;
    this.wallHeatTransferModel = WallHeatTransferModel.CONVECTIVE_BOUNDARY;
    return this;
  }

  /**
   * Sets the wall to be adiabatic (no heat transfer).
   *
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withAdiabaticWall() {
    this.wallHeatTransferModel = WallHeatTransferModel.ADIABATIC;
    return this;
  }

  /**
   * Enables non-equilibrium mass transfer.
   *
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder enableNonEquilibriumMassTransfer() {
    this.nonEquilibriumMassTransfer = true;
    return this;
  }

  /**
   * Enables non-equilibrium heat transfer.
   *
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder enableNonEquilibriumHeatTransfer() {
    this.nonEquilibriumHeatTransfer = true;
    return this;
  }

  /**
   * Sets the pipe roughness.
   *
   * @param roughness the roughness value
   * @param unit the unit ("m", "mm", "um")
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withRoughness(double roughness, String unit) {
    switch (unit.toLowerCase()) {
      case "mm":
        this.roughness = roughness * 1e-3;
        break;
      case "um":
      case "µm":
        this.roughness = roughness * 1e-6;
        break;
      case "m":
      default:
        this.roughness = roughness;
        break;
    }
    return this;
  }

  /**
   * Sets the pipe inclination angle.
   *
   * <p>
   * Positive angles indicate upward flow (against gravity), negative angles indicate downward flow.
   * This automatically calculates leg heights based on the length and inclination.
   * </p>
   *
   * @param angle the inclination angle
   * @param unit the angle unit ("deg", "degrees", "rad", "radians")
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withInclination(double angle, String unit) {
    switch (unit.toLowerCase()) {
      case "deg":
      case "degrees":
        this.inclination = Math.toRadians(angle);
        break;
      case "rad":
      case "radians":
      default:
        this.inclination = angle;
        break;
    }
    return this;
  }

  /**
   * Sets the pipe as vertical (90 degrees upward).
   *
   * @param upward true for upward flow, false for downward flow
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder vertical(boolean upward) {
    this.inclination = upward ? Math.PI / 2.0 : -Math.PI / 2.0;
    return this;
  }

  /**
   * Sets the pipe as horizontal (0 degrees).
   *
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder horizontal() {
    this.inclination = 0.0;
    return this;
  }

  /**
   * Sets leg heights for elevation changes.
   *
   * @param heights array of heights at each leg end in meters
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withLegHeights(double[] heights) {
    this.legHeights = heights.clone();
    return this;
  }

  /**
   * Sets leg positions along the pipe.
   *
   * @param positions array of positions at each leg end in meters
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withLegPositions(double[] positions) {
    this.legPositions = positions.clone();
    return this;
  }

  /**
   * Sets outer temperatures for each leg.
   *
   * @param temperatures array of temperatures in Kelvin
   * @return this builder
   */
  public TwoPhasePipeFlowSystemBuilder withOuterTemperatures(double[] temperatures) {
    this.outerTemperatures = temperatures.clone();
    return this;
  }

  /**
   * Builds the TwoPhasePipeFlowSystem.
   *
   * @return the configured TwoPhasePipeFlowSystem
   * @throws IllegalStateException if required parameters are not set or invalid
   */
  public TwoPhasePipeFlowSystem build() {
    // Validate required parameters
    validateParameters();

    TwoPhasePipeFlowSystem pipe = new TwoPhasePipeFlowSystem();
    pipe.setInletThermoSystem(fluid);

    // Set up geometry
    pipe.setNumberOfLegs(numberOfLegs);
    pipe.setNumberOfNodesInLeg(nodesPerLeg);

    // Create pipe geometry
    GeometryDefinitionInterface[] geometry = new PipeData[numberOfLegs + 1];
    for (int i = 0; i <= numberOfLegs; i++) {
      PipeData pipeData = new PipeData(diameter, roughness);
      geometry[i] = pipeData;
    }
    pipe.setEquipmentGeometry(geometry);

    // Set leg positions (evenly distributed if not specified)
    if (legPositions == null) {
      legPositions = new double[numberOfLegs + 1];
      for (int i = 0; i <= numberOfLegs; i++) {
        legPositions[i] = i * length / numberOfLegs;
      }
    }
    pipe.setLegPositions(legPositions);

    // Set leg heights (flat if not specified, or calculated from inclination)
    if (legHeights == null) {
      legHeights = new double[numberOfLegs + 1];
      if (Math.abs(inclination) > 1e-10) {
        // Calculate heights based on inclination angle
        for (int i = 0; i <= numberOfLegs; i++) {
          legHeights[i] = legPositions[i] * Math.sin(inclination);
        }
      }
    }
    pipe.setLegHeights(legHeights);
    pipe.setInclination(inclination);

    // Set outer temperatures
    if (outerTemperatures == null) {
      outerTemperatures = new double[numberOfLegs + 1];
      for (int i = 0; i <= numberOfLegs; i++) {
        outerTemperatures[i] = ambientTemperature;
      }
    }
    pipe.setLegOuterTemperatures(outerTemperatures);

    // Set heat transfer coefficients (required for heat transfer calculations)
    double[] outerHeatCoeffs = new double[numberOfLegs + 1];
    double[] wallHeatCoeffs = new double[numberOfLegs + 1];
    for (int i = 0; i <= numberOfLegs; i++) {
      outerHeatCoeffs[i] =
          overallHeatTransferCoefficient > 0 ? overallHeatTransferCoefficient : 5.0;
      wallHeatCoeffs[i] = 50.0; // Default wall heat transfer coefficient W/(m2·K)
    }
    pipe.setLegOuterHeatTransferCoefficients(outerHeatCoeffs);
    pipe.setLegWallHeatTransferCoefficients(wallHeatCoeffs);

    // Set flow pattern
    pipe.setInitialFlowPattern(flowPattern.getName());
    pipe.setFlowPatternModel(flowPatternModel);
    pipe.enableAutomaticFlowPatternDetection(autoFlowPatternDetection);

    // Set heat transfer model
    pipe.setWallHeatTransferModel(wallHeatTransferModel);
    pipe.setConstantWallTemperature(wallTemperature);
    pipe.setConstantHeatFlux(heatFlux);
    pipe.setAmbientTemperature(ambientTemperature);
    pipe.setOverallHeatTransferCoefficient(overallHeatTransferCoefficient);

    // Create and initialize the system
    pipe.createSystem();
    pipe.init();

    // Set mass/heat transfer modes (must be done AFTER init since flow nodes are required)
    if (nonEquilibriumMassTransfer) {
      pipe.enableNonEquilibriumMassTransfer();
    }
    if (nonEquilibriumHeatTransfer) {
      pipe.enableNonEquilibriumHeatTransfer();
    }

    return pipe;
  }

  /**
   * Validates all builder parameters before creating the pipe system.
   *
   * @throws IllegalStateException if any required parameter is invalid
   */
  private void validateParameters() {
    // Check fluid is set
    if (fluid == null) {
      throw new IllegalStateException(
          "Fluid system must be set using withFluid(). " + "Example: builder().withFluid(myFluid)");
    }

    // Provide helpful message if fluid might not be properly initialized
    if (fluid.getNumberOfComponents() == 0) {
      throw new IllegalStateException(
          "Fluid system has no components. Add components before building the pipe. "
              + "Example: fluid.addComponent(\"methane\", 0.1, 0);");
    }

    // Check geometry parameters
    if (diameter <= 0) {
      throw new IllegalStateException("Pipe diameter must be positive. Got: " + diameter + " m. "
          + "Set diameter using withDiameter(value, unit).");
    }

    if (length <= 0) {
      throw new IllegalStateException("Pipe length must be positive. Got: " + length + " m. "
          + "Set length using withLength(value, unit).");
    }

    if (numberOfLegs < 1) {
      throw new IllegalStateException(
          "Number of legs must be at least 1. Got: " + numberOfLegs + ".");
    }

    if (nodesPerLeg < 1) {
      throw new IllegalStateException("Nodes per leg must be at least 1. Got: " + nodesPerLeg + ". "
          + "Set nodes using withNodes(n) or withLegs(legs, nodesPerLeg).");
    }

    // Warn about very coarse discretization
    int totalNodes = numberOfLegs * nodesPerLeg;
    if (totalNodes < 5 && length > 100) {
      // This is just a warning - we don't throw
      System.err.println("Warning: Only " + totalNodes + " nodes for a " + length
          + " m pipe may give inaccurate results. " + "Consider using at least 10-20 nodes.");
    }

    // Validate temperature settings
    if (wallHeatTransferModel == WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE) {
      if (wallTemperature <= 0) {
        throw new IllegalStateException(
            "Wall temperature must be positive (in Kelvin). Got: " + wallTemperature + " K.");
      }
    }

    if (ambientTemperature <= 0) {
      throw new IllegalStateException(
          "Ambient temperature must be positive (in Kelvin). Got: " + ambientTemperature + " K.");
    }
  }

  /**
   * Converts length to meters.
   */
  private double convertLength(double value, String unit) {
    switch (unit.toLowerCase()) {
      case "mm":
        return value * 1e-3;
      case "cm":
        return value * 1e-2;
      case "km":
        return value * 1e3;
      case "in":
        return value * 0.0254;
      case "ft":
        return value * 0.3048;
      case "mi":
        return value * 1609.34;
      case "m":
      default:
        return value;
    }
  }

  /**
   * Converts temperature to Kelvin.
   */
  private double convertTemperature(double value, String unit) {
    switch (unit.toUpperCase()) {
      case "C":
        return value + 273.15;
      case "F":
        return (value - 32) * 5.0 / 9.0 + 273.15;
      case "K":
      default:
        return value;
    }
  }
}
