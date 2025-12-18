package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.fluidmechanics.flownode.FlowPattern;

/**
 * Container class for two-phase pipe flow simulation results.
 *
 * <p>
 * This class provides a convenient way to access all simulation results from a
 * {@link TwoPhasePipeFlowSystem} calculation. It encapsulates profile data, summary statistics, and
 * provides methods for data export.
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.1, 1000, 100);
 * pipe.enableNonEquilibriumMassTransfer();
 * PipeFlowResult result = pipe.solve();
 *
 * // Access summary values
 * System.out.println("Pressure drop: " + result.getTotalPressureDrop() + " bar");
 * System.out.println("Outlet temperature: " + result.getOutletTemperature() + " K");
 *
 * // Access profiles
 * double[] pressures = result.getPressureProfile();
 * double[] temperatures = result.getTemperatureProfile();
 *
 * // Export to map (useful for Jupyter/Python integration)
 * Map<String, double[]> data = result.toMap();
 * }</pre>
 *
 * @author ASMF
 * @version 1.0
 * @see TwoPhasePipeFlowSystem
 */
public class PipeFlowResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  // ==================== PROFILE DATA ====================
  private final double[] positions;
  private final double[] pressures;
  private final double[] temperatures;
  private final double[] liquidHoldups;
  private final double[] voidFractions;
  private final double[] gasVelocities;
  private final double[] liquidVelocities;
  private final double[] gasDensities;
  private final double[] liquidDensities;
  private final double[] interfacialAreas;
  private final FlowPattern[] flowPatterns;

  // ==================== SUMMARY DATA ====================
  private final double inletPressure;
  private final double outletPressure;
  private final double inletTemperature;
  private final double outletTemperature;
  private final double totalPressureDrop;
  private final double totalHeatLoss;
  private final double pipeLength;
  private final double pipeDiameter;
  private final int numberOfNodes;

  // ==================== MASS TRANSFER DATA ====================
  private final double[] totalMassTransferRates;
  private final String[] componentNames;

  /**
   * Private constructor - use {@link Builder} to create instances.
   */
  private PipeFlowResult(Builder builder) {
    this.positions = builder.positions;
    this.pressures = builder.pressures;
    this.temperatures = builder.temperatures;
    this.liquidHoldups = builder.liquidHoldups;
    this.voidFractions = builder.voidFractions;
    this.gasVelocities = builder.gasVelocities;
    this.liquidVelocities = builder.liquidVelocities;
    this.gasDensities = builder.gasDensities;
    this.liquidDensities = builder.liquidDensities;
    this.interfacialAreas = builder.interfacialAreas;
    this.flowPatterns = builder.flowPatterns;
    this.inletPressure = builder.inletPressure;
    this.outletPressure = builder.outletPressure;
    this.inletTemperature = builder.inletTemperature;
    this.outletTemperature = builder.outletTemperature;
    this.totalPressureDrop = builder.totalPressureDrop;
    this.totalHeatLoss = builder.totalHeatLoss;
    this.pipeLength = builder.pipeLength;
    this.pipeDiameter = builder.pipeDiameter;
    this.numberOfNodes = builder.numberOfNodes;
    this.totalMassTransferRates = builder.totalMassTransferRates;
    this.componentNames = builder.componentNames;
  }

  // ==================== PROFILE ACCESSORS ====================

  /**
   * Gets the position profile along the pipe.
   *
   * @return array of positions in meters from inlet
   */
  public double[] getPositionProfile() {
    return positions != null ? positions.clone() : new double[0];
  }

  /**
   * Gets the pressure profile along the pipe.
   *
   * @return array of pressures in bar at each node
   */
  public double[] getPressureProfile() {
    return pressures != null ? pressures.clone() : new double[0];
  }

  /**
   * Gets the temperature profile along the pipe.
   *
   * @return array of temperatures in Kelvin at each node
   */
  public double[] getTemperatureProfile() {
    return temperatures != null ? temperatures.clone() : new double[0];
  }

  /**
   * Gets the liquid holdup profile along the pipe.
   *
   * @return array of liquid holdups (volume fraction) at each node
   */
  public double[] getLiquidHoldupProfile() {
    return liquidHoldups != null ? liquidHoldups.clone() : new double[0];
  }

  /**
   * Gets the void fraction (gas volume fraction) profile along the pipe.
   *
   * @return array of void fractions at each node
   */
  public double[] getVoidFractionProfile() {
    return voidFractions != null ? voidFractions.clone() : new double[0];
  }

  /**
   * Gets the gas velocity profile along the pipe.
   *
   * @return array of gas velocities in m/s at each node
   */
  public double[] getGasVelocityProfile() {
    return gasVelocities != null ? gasVelocities.clone() : new double[0];
  }

  /**
   * Gets the liquid velocity profile along the pipe.
   *
   * @return array of liquid velocities in m/s at each node
   */
  public double[] getLiquidVelocityProfile() {
    return liquidVelocities != null ? liquidVelocities.clone() : new double[0];
  }

  /**
   * Gets the gas density profile along the pipe.
   *
   * @return array of gas densities in kg/m³ at each node
   */
  public double[] getGasDensityProfile() {
    return gasDensities != null ? gasDensities.clone() : new double[0];
  }

  /**
   * Gets the liquid density profile along the pipe.
   *
   * @return array of liquid densities in kg/m³ at each node
   */
  public double[] getLiquidDensityProfile() {
    return liquidDensities != null ? liquidDensities.clone() : new double[0];
  }

  /**
   * Gets the interfacial area profile along the pipe.
   *
   * @return array of interfacial areas in m² at each node
   */
  public double[] getInterfacialAreaProfile() {
    return interfacialAreas != null ? interfacialAreas.clone() : new double[0];
  }

  /**
   * Gets the flow pattern profile along the pipe.
   *
   * @return array of FlowPattern enums at each node
   */
  public FlowPattern[] getFlowPatternProfile() {
    return flowPatterns != null ? flowPatterns.clone() : new FlowPattern[0];
  }

  // ==================== SUMMARY ACCESSORS ====================

  /**
   * Gets the inlet pressure.
   *
   * @return inlet pressure in bar
   */
  public double getInletPressure() {
    return inletPressure;
  }

  /**
   * Gets the outlet pressure.
   *
   * @return outlet pressure in bar
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Gets the inlet temperature.
   *
   * @return inlet temperature in Kelvin
   */
  public double getInletTemperature() {
    return inletTemperature;
  }

  /**
   * Gets the outlet temperature.
   *
   * @return outlet temperature in Kelvin
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Gets the total pressure drop across the pipe.
   *
   * @return pressure drop in bar (positive = pressure decreases along flow)
   */
  public double getTotalPressureDrop() {
    return totalPressureDrop;
  }

  /**
   * Gets the total heat loss from the pipe to surroundings.
   *
   * @return heat loss in Watts (positive = heat lost from fluid)
   */
  public double getTotalHeatLoss() {
    return totalHeatLoss;
  }

  /**
   * Gets the pipe length.
   *
   * @return pipe length in meters
   */
  public double getPipeLength() {
    return pipeLength;
  }

  /**
   * Gets the pipe diameter.
   *
   * @return pipe diameter in meters
   */
  public double getPipeDiameter() {
    return pipeDiameter;
  }

  /**
   * Gets the number of calculation nodes.
   *
   * @return number of nodes
   */
  public int getNumberOfNodes() {
    return numberOfNodes;
  }

  /**
   * Gets the temperature change across the pipe.
   *
   * @return temperature change in Kelvin (positive = temperature increased)
   */
  public double getTemperatureChange() {
    return outletTemperature - inletTemperature;
  }

  /**
   * Gets the pressure drop per unit length.
   *
   * @return pressure gradient in bar/m
   */
  public double getPressureGradient() {
    return pipeLength > 0 ? totalPressureDrop / pipeLength : 0.0;
  }

  // ==================== MASS TRANSFER ACCESSORS ====================

  /**
   * Gets the total mass transfer rate for a component.
   *
   * @param componentIndex the component index
   * @return mass transfer rate in mol/s
   */
  public double getTotalMassTransferRate(int componentIndex) {
    if (totalMassTransferRates != null && componentIndex < totalMassTransferRates.length) {
      return totalMassTransferRates[componentIndex];
    }
    return 0.0;
  }

  /**
   * Gets the total mass transfer rate for a component by name.
   *
   * @param componentName the component name
   * @return mass transfer rate in mol/s, or 0 if not found
   */
  public double getTotalMassTransferRate(String componentName) {
    if (componentNames != null && totalMassTransferRates != null) {
      for (int i = 0; i < componentNames.length; i++) {
        if (componentNames[i].equalsIgnoreCase(componentName)) {
          return totalMassTransferRates[i];
        }
      }
    }
    return 0.0;
  }

  /**
   * Gets all component names.
   *
   * @return array of component names
   */
  public String[] getComponentNames() {
    return componentNames != null ? componentNames.clone() : new String[0];
  }

  // ==================== EXPORT METHODS ====================

  /**
   * Converts all profile data to a Map for easy export to data frames or JSON.
   *
   * <p>
   * This method is particularly useful for integration with Jupyter notebooks and Python via
   * neqsim-python.
   * </p>
   *
   * @return a Map with column names as keys and double arrays as values
   */
  public Map<String, double[]> toMap() {
    Map<String, double[]> data = new LinkedHashMap<>();
    data.put("position_m", getPositionProfile());
    data.put("pressure_bar", getPressureProfile());
    data.put("temperature_K", getTemperatureProfile());
    data.put("liquid_holdup", getLiquidHoldupProfile());
    data.put("void_fraction", getVoidFractionProfile());
    data.put("gas_velocity_m_s", getGasVelocityProfile());
    data.put("liquid_velocity_m_s", getLiquidVelocityProfile());
    data.put("gas_density_kg_m3", getGasDensityProfile());
    data.put("liquid_density_kg_m3", getLiquidDensityProfile());
    data.put("interfacial_area_m2", getInterfacialAreaProfile());
    return data;
  }

  /**
   * Gets a summary of the simulation results as a Map.
   *
   * @return a Map with summary statistics
   */
  public Map<String, Object> getSummary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("pipe_length_m", pipeLength);
    summary.put("pipe_diameter_m", pipeDiameter);
    summary.put("number_of_nodes", numberOfNodes);
    summary.put("inlet_pressure_bar", inletPressure);
    summary.put("outlet_pressure_bar", outletPressure);
    summary.put("total_pressure_drop_bar", totalPressureDrop);
    summary.put("pressure_gradient_bar_per_m", getPressureGradient());
    summary.put("inlet_temperature_K", inletTemperature);
    summary.put("outlet_temperature_K", outletTemperature);
    summary.put("temperature_change_K", getTemperatureChange());
    summary.put("total_heat_loss_W", totalHeatLoss);
    return summary;
  }

  /**
   * Returns a string representation of the result summary.
   *
   * @return formatted summary string
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Two-Phase Pipe Flow Results ===\n");
    sb.append(String.format("Pipe: %.1f m length, %.1f mm diameter, %d nodes%n", pipeLength,
        pipeDiameter * 1000, numberOfNodes));
    sb.append(String.format("Pressure: %.3f bar inlet → %.3f bar outlet (ΔP = %.4f bar)%n",
        inletPressure, outletPressure, totalPressureDrop));
    sb.append(String.format("Temperature: %.2f K inlet → %.2f K outlet (ΔT = %.2f K)%n",
        inletTemperature, outletTemperature, getTemperatureChange()));
    sb.append(String.format("Heat loss: %.2f W%n", totalHeatLoss));
    sb.append(String.format("Pressure gradient: %.6f bar/m%n", getPressureGradient()));
    return sb.toString();
  }

  // ==================== BUILDER ====================

  /**
   * Builder class for creating PipeFlowResult instances.
   */
  public static class Builder {
    private double[] positions;
    private double[] pressures;
    private double[] temperatures;
    private double[] liquidHoldups;
    private double[] voidFractions;
    private double[] gasVelocities;
    private double[] liquidVelocities;
    private double[] gasDensities;
    private double[] liquidDensities;
    private double[] interfacialAreas;
    private FlowPattern[] flowPatterns;
    private double inletPressure;
    private double outletPressure;
    private double inletTemperature;
    private double outletTemperature;
    private double totalPressureDrop;
    private double totalHeatLoss;
    private double pipeLength;
    private double pipeDiameter;
    private int numberOfNodes;
    private double[] totalMassTransferRates;
    private String[] componentNames;

    public Builder positions(double[] positions) {
      this.positions = positions;
      return this;
    }

    public Builder pressures(double[] pressures) {
      this.pressures = pressures;
      return this;
    }

    public Builder temperatures(double[] temperatures) {
      this.temperatures = temperatures;
      return this;
    }

    public Builder liquidHoldups(double[] liquidHoldups) {
      this.liquidHoldups = liquidHoldups;
      return this;
    }

    public Builder voidFractions(double[] voidFractions) {
      this.voidFractions = voidFractions;
      return this;
    }

    public Builder gasVelocities(double[] gasVelocities) {
      this.gasVelocities = gasVelocities;
      return this;
    }

    public Builder liquidVelocities(double[] liquidVelocities) {
      this.liquidVelocities = liquidVelocities;
      return this;
    }

    public Builder gasDensities(double[] gasDensities) {
      this.gasDensities = gasDensities;
      return this;
    }

    public Builder liquidDensities(double[] liquidDensities) {
      this.liquidDensities = liquidDensities;
      return this;
    }

    public Builder interfacialAreas(double[] interfacialAreas) {
      this.interfacialAreas = interfacialAreas;
      return this;
    }

    public Builder flowPatterns(FlowPattern[] flowPatterns) {
      this.flowPatterns = flowPatterns;
      return this;
    }

    public Builder inletPressure(double inletPressure) {
      this.inletPressure = inletPressure;
      return this;
    }

    public Builder outletPressure(double outletPressure) {
      this.outletPressure = outletPressure;
      return this;
    }

    public Builder inletTemperature(double inletTemperature) {
      this.inletTemperature = inletTemperature;
      return this;
    }

    public Builder outletTemperature(double outletTemperature) {
      this.outletTemperature = outletTemperature;
      return this;
    }

    public Builder totalPressureDrop(double totalPressureDrop) {
      this.totalPressureDrop = totalPressureDrop;
      return this;
    }

    public Builder totalHeatLoss(double totalHeatLoss) {
      this.totalHeatLoss = totalHeatLoss;
      return this;
    }

    public Builder pipeLength(double pipeLength) {
      this.pipeLength = pipeLength;
      return this;
    }

    public Builder pipeDiameter(double pipeDiameter) {
      this.pipeDiameter = pipeDiameter;
      return this;
    }

    public Builder numberOfNodes(int numberOfNodes) {
      this.numberOfNodes = numberOfNodes;
      return this;
    }

    public Builder massTransferRates(double[] rates) {
      this.totalMassTransferRates = rates;
      return this;
    }

    public Builder componentNames(String[] names) {
      this.componentNames = names;
      return this;
    }

    public PipeFlowResult build() {
      return new PipeFlowResult(this);
    }
  }

  /**
   * Creates a new Builder instance.
   *
   * @return a new Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a PipeFlowResult from a TwoPhasePipeFlowSystem after solving.
   *
   * @param pipe the solved pipe flow system
   * @return a PipeFlowResult containing all simulation results
   */
  public static PipeFlowResult fromPipeSystem(TwoPhasePipeFlowSystem pipe) {
    int numNodes = pipe.getTotalNumberOfNodes();
    double[] pressures = pipe.getPressureProfile();
    double[] temperatures = pipe.getTemperatureProfile();

    // Get component names and mass transfer rates
    int numComponents = pipe.getNode(0).getBulkSystem().getNumberOfComponents();
    String[] componentNames = new String[numComponents];
    double[] massTransferRates = new double[numComponents];
    for (int i = 0; i < numComponents; i++) {
      componentNames[i] = pipe.getNode(0).getBulkSystem().getComponent(i).getComponentName();
      massTransferRates[i] = pipe.getTotalMassTransferRate(i);
    }

    // Calculate pipe length from positions
    double[] positions = pipe.getPositionProfile();
    double pipeLength = positions.length > 0 ? positions[positions.length - 1] : 0.0;

    // Get diameter from first node
    double pipeDiameter = pipe.getNode(0).getGeometry().getDiameter();

    return builder().positions(positions).pressures(pressures).temperatures(temperatures)
        .liquidHoldups(pipe.getLiquidHoldupProfile()).voidFractions(pipe.getVoidFractionProfile())
        .gasVelocities(pipe.getVelocityProfile(0)).liquidVelocities(pipe.getVelocityProfile(1))
        .gasDensities(pipe.getDensityProfile(0)).liquidDensities(pipe.getDensityProfile(1))
        .interfacialAreas(pipe.getInterfacialAreaProfile())
        .flowPatterns(pipe.getFlowPatternProfile()).inletPressure(pressures[0])
        .outletPressure(pressures[numNodes - 1]).inletTemperature(temperatures[0])
        .outletTemperature(temperatures[numNodes - 1])
        .totalPressureDrop(pressures[0] - pressures[numNodes - 1])
        .totalHeatLoss(pipe.getTotalHeatLoss()).pipeLength(pipeLength).pipeDiameter(pipeDiameter)
        .numberOfNodes(numNodes).massTransferRates(massTransferRates).componentNames(componentNames)
        .build();
  }
}
