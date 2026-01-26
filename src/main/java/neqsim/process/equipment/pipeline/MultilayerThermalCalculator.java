package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-layer radial heat transfer calculator for pipeline thermal analysis.
 *
 * <p>
 * Implements OLGA-style thermal model with these features:
 * </p>
 * <ul>
 * <li>Multiple concentric layers with different materials</li>
 * <li>Steady-state overall heat transfer coefficient (U-value) calculation</li>
 * <li>Transient heat transfer with thermal mass effects</li>
 * <li>Cooldown time estimation for shutdown scenarios</li>
 * <li>Inner and outer convective boundary conditions</li>
 * </ul>
 *
 * <p>
 * The thermal resistance network approach is used:
 * </p>
 * 
 * <pre>
 * T_fluid --- R_inner --- [ Layer 1 ] --- [ Layer 2 ] --- ... --- R_outer --- T_ambient
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Incropera &amp; DeWitt "Fundamentals of Heat and Mass Transfer" - Chapter 3</li>
 * <li>Bai &amp; Bai "Subsea Pipelines and Risers" - Thermal Design</li>
 * <li>DNV-RP-F110 "Global Buckling of Submarine Pipelines"</li>
 * <li>OLGA 7 User Manual - Heat Transfer Model</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class MultilayerThermalCalculator implements Serializable {
  private static final long serialVersionUID = 1L;

  /** List of thermal layers from inside to outside. */
  private List<RadialThermalLayer> layers = new ArrayList<RadialThermalLayer>();

  /** Inner (fluid-side) heat transfer coefficient [W/(m²·K)]. */
  private double innerHTC = 300.0;

  /** Outer (ambient-side) heat transfer coefficient [W/(m²·K)]. */
  private double outerHTC = 50.0;

  /** Inner radius (pipe bore) [m]. */
  private double innerRadius = 0.1;

  /** Fluid temperature [K]. */
  private double fluidTemperature = 333.15; // 60°C

  /** Ambient temperature [K]. */
  private double ambientTemperature = 277.15; // 4°C (seabed)

  /** Enable transient thermal mass effects. */
  private boolean enableThermalMass = true;

  /** Cached overall U-value based on inner surface [W/(m²·K)]. */
  private double overallUValue = 0.0;

  /** Flag indicating if U-value needs recalculation. */
  private boolean uValueDirty = true;

  /**
   * Default constructor.
   */
  public MultilayerThermalCalculator() {
    // Empty constructor
  }

  /**
   * Construct with pipe inner radius.
   *
   * @param innerRadius Pipe inner radius in meters
   */
  public MultilayerThermalCalculator(double innerRadius) {
    this.innerRadius = innerRadius;
  }

  /**
   * Add a thermal layer to the outside of existing layers.
   *
   * <p>
   * Layers must be added from inside to outside. The inner radius of the new layer will be
   * automatically set to match the outer radius of the previous layer.
   * </p>
   *
   * @param thickness Layer thickness [m]
   * @param material Material type preset
   * @return The created layer for further customization
   */
  public RadialThermalLayer addLayer(double thickness, RadialThermalLayer.MaterialType material) {
    double ri = getOuterRadius();
    RadialThermalLayer layer = new RadialThermalLayer(material.name(), ri, thickness, material);
    layers.add(layer);
    uValueDirty = true;
    return layer;
  }

  /**
   * Add a custom thermal layer.
   *
   * @param name Layer identifier
   * @param thickness Layer thickness [m]
   * @param thermalConductivity Thermal conductivity [W/(m·K)]
   * @param density Density [kg/m³]
   * @param specificHeat Specific heat [J/(kg·K)]
   * @return The created layer
   */
  public RadialThermalLayer addCustomLayer(String name, double thickness,
      double thermalConductivity, double density, double specificHeat) {
    double ri = getOuterRadius();
    RadialThermalLayer layer =
        new RadialThermalLayer(name, ri, thickness, thermalConductivity, density, specificHeat);
    layers.add(layer);
    uValueDirty = true;
    return layer;
  }

  /**
   * Create a standard subsea pipeline thermal configuration.
   *
   * <p>
   * Typical layers:
   * </p>
   * <ol>
   * <li>Steel wall</li>
   * <li>FBE corrosion coating</li>
   * <li>Insulation (if specified)</li>
   * <li>Concrete weight coating (if specified)</li>
   * </ol>
   *
   * @param pipeInnerDiameter Pipe inner diameter [m]
   * @param wallThickness Steel wall thickness [m]
   * @param insulationThickness Insulation thickness [m] (0 for uninsulated)
   * @param concreteThickness Concrete coating thickness [m] (0 for none)
   * @param insulationMaterial Type of insulation material
   */
  public void createSubseaPipeConfig(double pipeInnerDiameter, double wallThickness,
      double insulationThickness, double concreteThickness,
      RadialThermalLayer.MaterialType insulationMaterial) {
    layers.clear();
    this.innerRadius = pipeInnerDiameter / 2.0;

    // Steel pipe wall
    addLayer(wallThickness, RadialThermalLayer.MaterialType.CARBON_STEEL);

    // FBE corrosion coating (typically 0.3-0.5 mm)
    addLayer(0.0004, RadialThermalLayer.MaterialType.FBE_COATING);

    // Insulation if specified
    if (insulationThickness > 0) {
      addLayer(insulationThickness, insulationMaterial);
    }

    // Concrete weight coating if specified
    if (concreteThickness > 0) {
      addLayer(concreteThickness, RadialThermalLayer.MaterialType.CONCRETE);
    }

    uValueDirty = true;
  }

  /**
   * Create a typical uninsulated subsea pipe.
   *
   * @param pipeInnerDiameter Pipe inner diameter [m]
   * @param wallThickness Steel wall thickness [m]
   */
  public void createBareSubseaPipe(double pipeInnerDiameter, double wallThickness) {
    createSubseaPipeConfig(pipeInnerDiameter, wallThickness, 0, 0, null);
  }

  /**
   * Create a typical insulated subsea pipe with PU foam.
   *
   * @param pipeInnerDiameter Pipe inner diameter [m]
   * @param wallThickness Steel wall thickness [m]
   * @param insulationThickness Insulation thickness [m]
   */
  public void createInsulatedSubseaPipe(double pipeInnerDiameter, double wallThickness,
      double insulationThickness) {
    createSubseaPipeConfig(pipeInnerDiameter, wallThickness, insulationThickness, 0,
        RadialThermalLayer.MaterialType.PU_FOAM);
  }

  /**
   * Create a buried onshore pipe configuration.
   *
   * @param pipeInnerDiameter Pipe inner diameter [m]
   * @param wallThickness Steel wall thickness [m]
   * @param burialDepth Depth of cover [m]
   * @param soilType Wet or dry soil
   */
  public void createBuriedOnshorePipe(double pipeInnerDiameter, double wallThickness,
      double burialDepth, RadialThermalLayer.MaterialType soilType) {
    layers.clear();
    this.innerRadius = pipeInnerDiameter / 2.0;

    // Steel pipe wall
    addLayer(wallThickness, RadialThermalLayer.MaterialType.CARBON_STEEL);

    // 3-layer PE coating (typical for onshore)
    addLayer(0.003, RadialThermalLayer.MaterialType.THREE_LAYER_PE);

    // Soil layer - modeled as equivalent thermal resistance
    // For buried pipes, use equivalent radius approach
    double outerR = getOuterRadius();
    double equivalentSoilThickness = calculateEquivalentSoilThickness(outerR, burialDepth);
    addLayer(equivalentSoilThickness, soilType);

    uValueDirty = true;
  }

  /**
   * Calculate equivalent soil thickness for buried pipe thermal model.
   *
   * <p>
   * Uses Carslaw &amp; Jaeger solution for isothermal cylinder below isothermal surface.
   * </p>
   *
   * @param pipeOuterRadius Pipe outer radius [m]
   * @param burialDepth Depth to pipe centerline [m]
   * @return Equivalent soil layer thickness [m]
   */
  private double calculateEquivalentSoilThickness(double pipeOuterRadius, double burialDepth) {
    // Shape factor: S = 2*pi / ln(2*H/r_o + sqrt((2*H/r_o)^2 - 1))
    // Equivalent thickness: t_eq = r_o * (exp(2*pi/S) - 1)
    double ratio = 2.0 * burialDepth / pipeOuterRadius;
    if (ratio <= 1.0) {
      ratio = 1.01; // Minimum for valid calculation
    }
    double shapeFactor = 2.0 * Math.PI / Math.log(ratio + Math.sqrt(ratio * ratio - 1.0));
    return pipeOuterRadius * (Math.exp(2.0 * Math.PI / shapeFactor) - 1.0);
  }

  /**
   * Get the current outer radius (including all layers).
   *
   * @return Outer radius in meters
   */
  public double getOuterRadius() {
    if (layers.isEmpty()) {
      return innerRadius;
    }
    return layers.get(layers.size() - 1).getOuterRadius();
  }

  /**
   * Calculate total thermal resistance per unit length.
   *
   * <p>
   * R_total = R_inner + sum(R_layers) + R_outer where:
   * </p>
   * <ul>
   * <li>R_inner = 1 / (h_i * 2 * π * r_i)</li>
   * <li>R_layer = ln(r_o/r_i) / (2 * π * k)</li>
   * <li>R_outer = 1 / (h_o * 2 * π * r_o)</li>
   * </ul>
   *
   * @return Total thermal resistance in (m·K)/W per unit length
   */
  public double calculateTotalThermalResistance() {
    double rTotal = 0.0;

    // Inner convective resistance
    if (innerHTC > 0) {
      rTotal += 1.0 / (innerHTC * 2.0 * Math.PI * innerRadius);
    }

    // Conductive resistance through each layer
    for (RadialThermalLayer layer : layers) {
      rTotal += layer.getThermalResistance();
    }

    // Outer convective resistance
    double ro = getOuterRadius();
    if (outerHTC > 0) {
      rTotal += 1.0 / (outerHTC * 2.0 * Math.PI * ro);
    }

    return rTotal;
  }

  /**
   * Calculate overall heat transfer coefficient (U-value) based on inner surface area.
   *
   * <p>
   * U_i = 1 / (A_i * R_total) = 1 / (2 * π * r_i * R_total)
   * </p>
   *
   * @return U-value in W/(m²·K)
   */
  public double calculateOverallUValue() {
    if (!uValueDirty && overallUValue > 0) {
      return overallUValue;
    }

    double rTotal = calculateTotalThermalResistance();
    if (rTotal <= 0) {
      overallUValue = 0.0;
    } else {
      overallUValue = 1.0 / (2.0 * Math.PI * innerRadius * rTotal);
    }

    uValueDirty = false;
    return overallUValue;
  }

  /**
   * Calculate heat loss per unit length.
   *
   * @return Heat loss in W/m (positive = heat leaving fluid)
   */
  public double calculateHeatLossPerLength() {
    double rTotal = calculateTotalThermalResistance();
    if (rTotal <= 0) {
      return 0.0;
    }
    return (fluidTemperature - ambientTemperature) / rTotal;
  }

  /**
   * Calculate steady-state temperature at the interface between two layers.
   *
   * @param layerIndex Layer index (0 = first layer after fluid)
   * @param atOuterSurface True for outer surface, false for inner
   * @return Temperature in Kelvin
   */
  public double calculateInterfaceTemperature(int layerIndex, boolean atOuterSurface) {
    if (layerIndex < 0 || layerIndex >= layers.size()) {
      return ambientTemperature;
    }

    double q = calculateHeatLossPerLength(); // W/m
    double T = fluidTemperature;

    // Subtract inner convective drop
    if (innerHTC > 0) {
      T -= q / (innerHTC * 2.0 * Math.PI * innerRadius);
    }

    // Subtract conductive drops through layers up to target
    for (int i = 0; i <= layerIndex; i++) {
      RadialThermalLayer layer = layers.get(i);
      double ri = layer.getInnerRadius();
      double ro = layer.getOuterRadius();
      double k = layer.getThermalConductivity();

      if (!atOuterSurface && i == layerIndex) {
        // Return inner surface temperature of this layer
        return T;
      }

      if (k > 0 && ro > ri) {
        T -= q * Math.log(ro / ri) / (2.0 * Math.PI * k);
      }
    }

    return T;
  }

  /**
   * Initialize all layers to a specified temperature.
   *
   * @param temperature Initial temperature in Kelvin
   */
  public void initializeLayerTemperatures(double temperature) {
    for (RadialThermalLayer layer : layers) {
      layer.initializeTemperature(temperature);
    }
  }

  /**
   * Initialize layer temperatures with linear interpolation between fluid and ambient.
   */
  public void initializeLayerTemperaturesLinear() {
    double rTotal = calculateTotalThermalResistance();
    double q = (fluidTemperature - ambientTemperature) / rTotal;

    double T = fluidTemperature;
    if (innerHTC > 0) {
      T -= q / (innerHTC * 2.0 * Math.PI * innerRadius);
    }

    for (RadialThermalLayer layer : layers) {
      double ri = layer.getInnerRadius();
      double ro = layer.getOuterRadius();
      double k = layer.getThermalConductivity();

      // Set to mean temperature of this layer
      double T_inner = T;
      if (k > 0 && ro > ri) {
        T -= q * Math.log(ro / ri) / (2.0 * Math.PI * k);
      }
      double T_outer = T;
      layer.initializeTemperature((T_inner + T_outer) / 2.0);
    }
  }

  /**
   * Perform transient thermal calculation for one time step.
   *
   * <p>
   * Uses explicit finite difference scheme for each layer. The heat balance for each layer is:
   * </p>
   * 
   * <pre>
   * m * Cp * dT/dt = Q_in - Q_out
   * </pre>
   *
   * @param dt Time step in seconds
   */
  public void updateTransient(double dt) {
    if (!enableThermalMass || layers.isEmpty()) {
      return;
    }

    int n = layers.size();
    double[] newTemperatures = new double[n];

    for (int i = 0; i < n; i++) {
      RadialThermalLayer layer = layers.get(i);
      double Ti = layer.getTemperature();
      double thermalMass = layer.getThermalMassPerLength(); // J/(K·m)

      // Heat flux from inside
      double Q_in;
      if (i == 0) {
        // From fluid to first layer
        double ri = layer.getInnerRadius();
        Q_in = innerHTC * 2.0 * Math.PI * ri * (fluidTemperature - Ti);
      } else {
        // From previous layer
        RadialThermalLayer prevLayer = layers.get(i - 1);
        double T_prev = prevLayer.getTemperature();
        double r_interface = layer.getInnerRadius();

        // Contact conductance at interface (simplified: use harmonic mean of conductivities)
        double k_prev = prevLayer.getThermalConductivity();
        double k_curr = layer.getThermalConductivity();
        double k_eff = 2.0 * k_prev * k_curr / (k_prev + k_curr + 1e-10);

        // Approximate contact width as mean of thicknesses
        double dx = (prevLayer.getThickness() + layer.getThickness()) / 2.0;
        Q_in = k_eff * 2.0 * Math.PI * r_interface / dx * (T_prev - Ti);
      }

      // Heat flux to outside
      double Q_out;
      if (i == n - 1) {
        // From last layer to ambient
        double ro = layer.getOuterRadius();
        Q_out = outerHTC * 2.0 * Math.PI * ro * (Ti - ambientTemperature);
      } else {
        // To next layer
        RadialThermalLayer nextLayer = layers.get(i + 1);
        double T_next = nextLayer.getTemperature();
        double r_interface = layer.getOuterRadius();

        double k_curr = layer.getThermalConductivity();
        double k_next = nextLayer.getThermalConductivity();
        double k_eff = 2.0 * k_curr * k_next / (k_curr + k_next + 1e-10);

        double dx = (layer.getThickness() + nextLayer.getThickness()) / 2.0;
        Q_out = k_eff * 2.0 * Math.PI * r_interface / dx * (Ti - T_next);
      }

      // Update temperature
      double dT = (Q_in - Q_out) * dt / thermalMass;
      newTemperatures[i] = Ti + dT;
    }

    // Apply new temperatures
    for (int i = 0; i < n; i++) {
      layers.get(i).setTemperature(newTemperatures[i]);
    }
  }

  /**
   * Calculate cooldown time from current state to a target temperature.
   *
   * <p>
   * Estimates time for pipe wall (first layer) to cool to target, assuming no flow. Uses lumped
   * capacitance approximation.
   * </p>
   *
   * @param targetTemperature Target temperature in Kelvin
   * @return Estimated cooldown time in hours
   */
  public double calculateCooldownTime(double targetTemperature) {
    if (layers.isEmpty()) {
      return 0.0;
    }

    // Total thermal mass
    double totalThermalMass = 0.0;
    double meanTemperature = 0.0;
    for (RadialThermalLayer layer : layers) {
      double mass = layer.getThermalMassPerLength();
      totalThermalMass += mass;
      meanTemperature += layer.getTemperature() * mass;
    }
    if (totalThermalMass > 0) {
      meanTemperature /= totalThermalMass;
    } else {
      meanTemperature = fluidTemperature;
    }

    // Outer thermal resistance (from pipe outer surface to ambient)
    double ro = getOuterRadius();
    double R_outer = 1.0 / (outerHTC * 2.0 * Math.PI * ro);

    // Time constant: tau = m*Cp * R
    double tau = totalThermalMass * R_outer;

    // Exponential decay: T(t) = T_amb + (T_0 - T_amb) * exp(-t/tau)
    // Solving for t: t = -tau * ln((T_target - T_amb)/(T_0 - T_amb))
    double dT0 = meanTemperature - ambientTemperature;
    double dT_target = targetTemperature - ambientTemperature;

    if (dT0 <= 0 || dT_target <= 0 || dT_target >= dT0) {
      return 0.0;
    }

    double cooldownSeconds = -tau * Math.log(dT_target / dT0);
    return cooldownSeconds / 3600.0; // Convert to hours
  }

  /**
   * Calculate cooldown time to hydrate formation temperature.
   *
   * @param hydrateTemperature Hydrate formation temperature in Kelvin
   * @return Time to reach hydrate temperature in hours
   */
  public double calculateHydrateCooldownTime(double hydrateTemperature) {
    return calculateCooldownTime(hydrateTemperature);
  }

  /**
   * Get total thermal mass per unit length of all layers.
   *
   * @return Thermal mass in J/(K·m)
   */
  public double getTotalThermalMass() {
    double total = 0.0;
    for (RadialThermalLayer layer : layers) {
      total += layer.getThermalMassPerLength();
    }
    return total;
  }

  /**
   * Get total mass per unit length of all layers.
   *
   * @return Mass in kg/m
   */
  public double getTotalMassPerLength() {
    double total = 0.0;
    for (RadialThermalLayer layer : layers) {
      total += layer.getMassPerLength();
    }
    return total;
  }

  /**
   * Get the list of thermal layers.
   *
   * @return Unmodifiable list of layers
   */
  public List<RadialThermalLayer> getLayers() {
    return java.util.Collections.unmodifiableList(layers);
  }

  /**
   * Get number of layers.
   *
   * @return Number of thermal layers
   */
  public int getNumberOfLayers() {
    return layers.size();
  }

  /**
   * Clear all layers.
   */
  public void clearLayers() {
    layers.clear();
    uValueDirty = true;
  }

  /**
   * Get inner heat transfer coefficient.
   *
   * @return h_inner in W/(m²·K)
   */
  public double getInnerHTC() {
    return innerHTC;
  }

  /**
   * Set inner (fluid-side) heat transfer coefficient.
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>Gas (low pressure): 20-50 W/(m²·K)</li>
   * <li>Gas (high pressure): 100-300 W/(m²·K)</li>
   * <li>Oil: 100-500 W/(m²·K)</li>
   * <li>Water: 500-10000 W/(m²·K)</li>
   * <li>Two-phase: 500-5000 W/(m²·K)</li>
   * </ul>
   *
   * @param htc Heat transfer coefficient in W/(m²·K)
   */
  public void setInnerHTC(double htc) {
    this.innerHTC = htc;
    uValueDirty = true;
  }

  /**
   * Get outer heat transfer coefficient.
   *
   * @return h_outer in W/(m²·K)
   */
  public double getOuterHTC() {
    return outerHTC;
  }

  /**
   * Set outer (ambient-side) heat transfer coefficient.
   *
   * <p>
   * Typical values:
   * </p>
   * <ul>
   * <li>Still air: 5-10 W/(m²·K)</li>
   * <li>Forced air/wind: 25-100 W/(m²·K)</li>
   * <li>Still water: 50-100 W/(m²·K)</li>
   * <li>Flowing water/current: 100-500 W/(m²·K)</li>
   * <li>Buried (soil contact): Modeled as conduction layer</li>
   * </ul>
   *
   * @param htc Heat transfer coefficient in W/(m²·K)
   */
  public void setOuterHTC(double htc) {
    this.outerHTC = htc;
    uValueDirty = true;
  }

  /**
   * Get inner radius.
   *
   * @return Inner radius in meters
   */
  public double getInnerRadius() {
    return innerRadius;
  }

  /**
   * Set inner radius (pipe bore).
   *
   * @param radius Inner radius in meters
   */
  public void setInnerRadius(double radius) {
    this.innerRadius = radius;
    uValueDirty = true;
  }

  /**
   * Get fluid temperature.
   *
   * @return Temperature in Kelvin
   */
  public double getFluidTemperature() {
    return fluidTemperature;
  }

  /**
   * Set fluid temperature.
   *
   * @param temperature Temperature in Kelvin
   */
  public void setFluidTemperature(double temperature) {
    this.fluidTemperature = temperature;
  }

  /**
   * Get ambient temperature.
   *
   * @return Temperature in Kelvin
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set ambient temperature.
   *
   * @param temperature Temperature in Kelvin
   */
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
  }

  /**
   * Check if thermal mass effects are enabled.
   *
   * @return True if transient thermal mass is considered
   */
  public boolean isEnableThermalMass() {
    return enableThermalMass;
  }

  /**
   * Enable or disable thermal mass effects.
   *
   * @param enable True to enable transient thermal mass calculations
   */
  public void setEnableThermalMass(boolean enable) {
    this.enableThermalMass = enable;
  }

  /**
   * Calculate inner heat transfer coefficient using Dittus-Boelter correlation.
   *
   * <p>
   * Nu = 0.023 * Re^0.8 * Pr^n where n = 0.4 for heating, 0.3 for cooling h = Nu * k / D
   * </p>
   *
   * @param velocity Fluid velocity [m/s]
   * @param density Fluid density [kg/m³]
   * @param viscosity Dynamic viscosity [Pa·s]
   * @param thermalConductivity Fluid thermal conductivity [W/(m·K)]
   * @param prandtl Prandtl number
   * @param isHeating True if fluid is being heated, false if cooled
   * @return Heat transfer coefficient in W/(m²·K)
   */
  public double calculateInnerHTCDittusBoelter(double velocity, double density, double viscosity,
      double thermalConductivity, double prandtl, boolean isHeating) {
    double D = 2.0 * innerRadius;
    double Re = density * velocity * D / viscosity;

    if (Re < 2300) {
      // Laminar: Nu = 3.66 (constant wall temperature)
      return 3.66 * thermalConductivity / D;
    }

    // Turbulent: Dittus-Boelter
    double n = isHeating ? 0.4 : 0.3;
    double Nu = 0.023 * Math.pow(Re, 0.8) * Math.pow(prandtl, n);
    return Nu * thermalConductivity / D;
  }

  /**
   * Get a summary string of the thermal configuration.
   *
   * @return Multi-line summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Multi-layer Thermal Configuration:\n");
    sb.append(
        String.format("  Inner radius: %.4f m (ID = %.1f mm)\n", innerRadius, innerRadius * 2000));
    sb.append(String.format("  Inner HTC: %.1f W/(m²·K)\n", innerHTC));
    sb.append(String.format("  Outer HTC: %.1f W/(m²·K)\n", outerHTC));
    sb.append(String.format("  Fluid temperature: %.1f K (%.1f °C)\n", fluidTemperature,
        fluidTemperature - 273.15));
    sb.append(String.format("  Ambient temperature: %.1f K (%.1f °C)\n", ambientTemperature,
        ambientTemperature - 273.15));

    sb.append("\nLayers (inside to outside):\n");
    for (int i = 0; i < layers.size(); i++) {
      RadialThermalLayer layer = layers.get(i);
      sb.append(String.format("  %d. %s: t=%.1f mm, k=%.3f W/(m·K), R=%.4f (m·K)/W\n", i + 1,
          layer.getName(), layer.getThickness() * 1000, layer.getThermalConductivity(),
          layer.getThermalResistance()));
    }

    sb.append(String.format("\nOuter radius: %.4f m (OD = %.1f mm)\n", getOuterRadius(),
        getOuterRadius() * 2000));
    sb.append(String.format("Total thermal resistance: %.4f (m·K)/W\n",
        calculateTotalThermalResistance()));
    sb.append(
        String.format("Overall U-value (inner basis): %.2f W/(m²·K)\n", calculateOverallUValue()));
    sb.append(String.format("Heat loss rate: %.1f W/m\n", calculateHeatLossPerLength()));

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("MultilayerThermalCalculator[%d layers, U=%.2f W/(m²·K), Q=%.1f W/m]",
        layers.size(), calculateOverallUValue(), calculateHeatLossPerLength());
  }
}
