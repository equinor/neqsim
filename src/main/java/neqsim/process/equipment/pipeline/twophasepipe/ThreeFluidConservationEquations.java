package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;

/**
 * Conservation equations for three-fluid (gas-oil-water) pipe flow model.
 *
 * <p>
 * Extends the two-fluid model to include water as a third phase. The model solves 7 coupled PDEs:
 * </p>
 * <ul>
 * <li>Gas mass conservation</li>
 * <li>Oil mass conservation</li>
 * <li>Water mass conservation</li>
 * <li>Gas momentum conservation</li>
 * <li>Oil momentum conservation</li>
 * <li>Water momentum conservation</li>
 * <li>Mixture energy conservation</li>
 * </ul>
 *
 * <h2>Momentum Source Terms</h2> Each phase has:
 * <ul>
 * <li>Pressure gradient: -A_k ∂P/∂x</li>
 * <li>Wall friction: -τ_wk S_wk</li>
 * <li>Interfacial friction: ±τ_i S_i</li>
 * <li>Gravity: -ρ_k g sin(θ) A_k</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ThreeFluidConservationEquations implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Gravitational acceleration (m/s²). */
  private static final double G = 9.81;

  /** Surface temperature for heat transfer (K). */
  private double surfaceTemperature = 288.15;

  /** Heat transfer coefficient (W/(m²·K)). */
  private double heatTransferCoefficient = 0.0;

  /** Enable heat transfer from surroundings. */
  private boolean enableHeatTransfer = false;

  /**
   * Result container for three-fluid RHS calculation.
   */
  public static class ThreeFluidRHS implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Gas mass RHS: d(αg ρg A)/dt. */
    public double gasMass;

    /** Oil mass RHS: d(αo ρo A)/dt. */
    public double oilMass;

    /** Water mass RHS: d(αw ρw A)/dt. */
    public double waterMass;

    /** Gas momentum RHS: d(Mg)/dt. */
    public double gasMomentum;

    /** Oil momentum RHS: d(Mo)/dt. */
    public double oilMomentum;

    /** Water momentum RHS: d(Mw)/dt. */
    public double waterMomentum;

    /** Mixture energy RHS: d(E)/dt. */
    public double energy;

    /** Gas wall shear stress (Pa). */
    public double gasWallShear;

    /** Oil wall shear stress (Pa). */
    public double oilWallShear;

    /** Water wall shear stress (Pa). */
    public double waterWallShear;

    /** Gas-oil interfacial shear stress (Pa). */
    public double gasOilInterfacialShear;

    /** Oil-water interfacial shear stress (Pa). */
    public double oilWaterInterfacialShear;

    /** Gas-water interfacial shear stress (Pa), when no oil layer is present. */
    public double gasWaterInterfacialShear;
  }

  /**
   * Default constructor.
   */
  public ThreeFluidConservationEquations() {
  }

  /**
   * Calculate RHS of conservation equations for a three-fluid section.
   *
   * @param section Current section state
   * @param dPdx Pressure gradient (Pa/m)
   * @param upstreamSection Upstream section (for fluxes)
   * @param downstreamSection Downstream section (for fluxes)
   * @return ThreeFluidRHS with all source terms
   */
  public ThreeFluidRHS calcRHS(ThreeFluidSection section, double dPdx, ThreeFluidSection upstreamSection,
      ThreeFluidSection downstreamSection) {
    ThreeFluidRHS rhs = new ThreeFluidRHS();

    double diameter = section.getDiameter();
    double area = section.getArea();
    double inclination = section.getInclination();
    double roughness = section.getRoughness();

    // Get phase properties
    double alphaG = section.getGasHoldup();
    double alphaO = section.getOilHoldup();
    double alphaW = section.getWaterHoldup();

    double rhoG = section.getGasDensity();
    double rhoO = section.getOilDensity();
    double rhoW = section.getWaterDensity();

    double uG = section.getGasVelocity();
    double uO = section.getOilVelocity();
    double uW = section.getWaterVelocity();

    double muG = section.getGasViscosity();
    double muO = section.getOilViscosity();
    double muW = section.getWaterViscosity();

    // Update geometry
    section.updateThreeLayerGeometry();

    // Get geometry values
    double sWallG = alphaG > 0.0
        ? Math.max(0.0, Math.PI * diameter - section.getOilWettedPerimeter() - section.getWaterWettedPerimeter())
        : 0.0;
    double sWallO = section.getOilWettedPerimeter();
    double sWallW = section.getWaterWettedPerimeter();
    double sIntGO = section.getGasOilInterfacialWidth();
    double sIntOW = section.getOilWaterInterfacialWidth();
    double sIntGW = section.getGasWaterInterfacialWidth();

    // Calculate wall friction for each phase
    double reG = alphaG > 0.0 ? rhoG * Math.abs(uG) * diameter / muG : 0.0;
    double reO = alphaO > 0.0 ? rhoO * Math.abs(uO) * diameter / muO : 0.0;
    double reW = alphaW > 0.0 ? rhoW * Math.abs(uW) * diameter / muW : 0.0;

    double fG = calculateFrictionFactor(reG, roughness, diameter);
    double fO = calculateFrictionFactor(reO, roughness, diameter);
    double fW = calculateFrictionFactor(reW, roughness, diameter);

    rhs.gasWallShear = 0.5 * fG * rhoG * uG * Math.abs(uG);
    rhs.oilWallShear = 0.5 * fO * rhoO * uO * Math.abs(uO);
    rhs.waterWallShear = 0.5 * fW * rhoW * uW * Math.abs(uW);

    // Calculate interfacial friction
    // Gas-oil interface
    double relVelGO = uG - uO;
    double fiGO = calculateInterfacialFrictionFactor(alphaG, alphaO, rhoG, rhoO, relVelGO, diameter,
        section.getGasOilSurfaceTension());
    rhs.gasOilInterfacialShear = 0.5 * fiGO * rhoG * relVelGO * Math.abs(relVelGO);

    // Oil-water interface
    double relVelOW = uO - uW;
    double fiOW = calculateInterfacialFrictionFactor(alphaO, alphaW, rhoO, rhoW, relVelOW, diameter,
        section.getOilWaterSurfaceTension());
    rhs.oilWaterInterfacialShear = 0.5 * fiOW * rhoO * relVelOW * Math.abs(relVelOW);

    double relVelGW = uG - uW;
    double fiGW = sIntGW > 0.0
        ? calculateInterfacialFrictionFactor(alphaG, alphaW, rhoG, rhoW, relVelGW, diameter,
            section.getGasWaterSurfaceTension())
        : 0.0;
    rhs.gasWaterInterfacialShear = 0.5 * fiGW * rhoG * relVelGW * Math.abs(relVelGW);

    // ===== Mass conservation equations (no sources in basic model) =====
    // Mass flux contributions come from numerical scheme, these are source terms only
    rhs.gasMass = section.getOilEvaporationRate() + section.getWaterEvaporationRate();
    rhs.oilMass = -section.getOilEvaporationRate();
    rhs.waterMass = -section.getWaterEvaporationRate();

    // ===== Momentum conservation equations =====
    double sinTheta = Math.sin(inclination);

    // Gas momentum: pressure, wall friction, interfacial (with oil), gravity
    double areaG = alphaG * area;
    rhs.gasMomentum = -areaG * dPdx // Pressure
        - rhs.gasWallShear * sWallG // Wall friction
        - rhs.gasOilInterfacialShear * sIntGO // Interfacial (gas side loses)
        - rhs.gasWaterInterfacialShear * sIntGW - rhoG * G * sinTheta * areaG; // Gravity

    // Oil momentum: pressure, wall friction, interfacial (gas and water), gravity
    double areaO = alphaO * area;
    rhs.oilMomentum = -areaO * dPdx // Pressure
        - rhs.oilWallShear * sWallO // Wall friction
        + rhs.gasOilInterfacialShear * sIntGO // Interfacial (oil gains from gas)
        - rhs.oilWaterInterfacialShear * sIntOW // Interfacial (oil loses to water)
        - rhoO * G * sinTheta * areaO; // Gravity

    // Water momentum: pressure, wall friction, interfacial (with oil), gravity
    double areaW = alphaW * area;
    rhs.waterMomentum = -areaW * dPdx // Pressure
        - rhs.waterWallShear * sWallW // Wall friction
        + rhs.oilWaterInterfacialShear * sIntOW // Interfacial (water gains from oil)
        + rhs.gasWaterInterfacialShear * sIntGW - rhoW * G * sinTheta * areaW; // Gravity

    // Transferred mass carries its donor phase momentum, with equal and opposite exchange.
    double oilTransferMomentum = section.getOilEvaporationRate() * (section.getOilEvaporationRate() >= 0.0 ? uO : uG);
    double waterTransferMomentum = section.getWaterEvaporationRate()
        * (section.getWaterEvaporationRate() >= 0.0 ? uW : uG);
    rhs.gasMomentum += oilTransferMomentum + waterTransferMomentum;
    rhs.oilMomentum -= oilTransferMomentum;
    rhs.waterMomentum -= waterTransferMomentum;

    // ===== Energy equation (mixture) =====
    // Heat transfer to surroundings: Q = h * π * D * (T_surface - T_fluid)
    if (enableHeatTransfer && heatTransferCoefficient > 0) {
      double T_fluid = section.getTemperature();
      double Q_wall = heatTransferCoefficient * Math.PI * diameter * (surfaceTemperature - T_fluid);
      rhs.energy = Q_wall; // W/m (heat gain from surroundings)
    } else {
      rhs.energy = 0.0;
    }
    // Conservative energy excludes gravitational potential, so include gravity work.
    rhs.energy -= G * sinTheta * (rhoG * areaG * uG + rhoO * areaO * uO + rhoW * areaW * uW);

    return rhs;
  }

  /**
   * Calculate Colebrook-White friction factor.
   *
   * @param re Reynolds number
   * @param roughness pipe wall roughness in meters
   * @param diameter pipe inner diameter in meters
   * @return Fanning friction factor
   */
  private double calculateFrictionFactor(double re, double roughness, double diameter) {
    if (re <= 0.0) {
      return 0.0;
    }

    if (re < 2300) {
      // Laminar: f = 64/Re (Darcy), Fanning = 16/Re
      return 16.0 / re;
    }

    // Turbulent: Haaland approximation
    double relRoughness = roughness / diameter;
    double term1 = Math.pow(relRoughness / 3.7, 1.11);
    double term2 = 6.9 / re;

    double f = 1.0 / Math.pow(-1.8 * Math.log10(term1 + term2), 2);
    return f / 4.0; // Convert Darcy to Fanning
  }

  /**
   * Calculate interfacial friction factor.
   *
   * <p>
   * Uses a simplified model based on relative velocity and density ratio.
   * </p>
   *
   * @param alpha1 phase 1 volume fraction
   * @param alpha2 phase 2 volume fraction
   * @param rho1 phase 1 density (kg/m³)
   * @param rho2 phase 2 density (kg/m³)
   * @param relVel relative velocity between phases (m/s)
   * @param diameter pipe diameter (m)
   * @param surfaceTension surface tension (N/m)
   * @return interfacial friction factor (dimensionless)
   */
  private double calculateInterfacialFrictionFactor(double alpha1, double alpha2, double rho1, double rho2,
      double relVel, double diameter, double surfaceTension) {
    if (alpha1 <= 0.0 || alpha2 <= 0.0) {
      return 0.0;
    }

    // Froude number based on relative velocity
    double densityDiff = Math.abs(rho2 - rho1);
    if (densityDiff < 1.0) {
      densityDiff = 1.0;
    }

    double fr = Math.abs(relVel) / Math.sqrt(G * diameter * densityDiff / rho1);

    // Simple correlation: increases with Froude number
    double fi = 0.01 * (1.0 + 10.0 * fr * fr);

    return Math.min(0.1, fi);
  }

  /**
   * Get state vector from section (for numerical integrator).
   *
   * @param section Three-fluid section
   * @return State vector [gasMass, oilMass, waterMass, gasMom, oilMom, waterMom, energy]
   */
  public double[] getStateVector(ThreeFluidSection section) {
    return section.getStateVector();
  }

  /**
   * Set state vector to section.
   *
   * @param section Three-fluid section
   * @param state State vector [gasMass, oilMass, waterMass, gasMom, oilMom, waterMom, energy]
   */
  public void setStateVector(ThreeFluidSection section, double[] state) {
    if (state == null || state.length != 7) {
      throw new IllegalArgumentException("Three-fluid state must contain seven conservative variables");
    }
    section.setStateVector(state);
  }

  /**
   * Set surface temperature for heat transfer calculations.
   *
   * @param temperature Surface temperature [K]
   */
  public void setSurfaceTemperature(double temperature) {
    this.surfaceTemperature = temperature;
  }

  /**
   * Get surface temperature.
   *
   * @return Surface temperature [K]
   */
  public double getSurfaceTemperature() {
    return surfaceTemperature;
  }

  /**
   * Set heat transfer coefficient.
   *
   * @param coefficient Heat transfer coefficient [W/(m²·K)]
   */
  public void setHeatTransferCoefficient(double coefficient) {
    this.heatTransferCoefficient = coefficient;
  }

  /**
   * Get heat transfer coefficient.
   *
   * @return Heat transfer coefficient [W/(m²·K)]
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * Enable or disable heat transfer modeling.
   *
   * @param enable true to enable heat transfer
   */
  public void setEnableHeatTransfer(boolean enable) {
    this.enableHeatTransfer = enable;
  }

  /**
   * Check if heat transfer is enabled.
   *
   * @return true if heat transfer is enabled
   */
  public boolean isEnableHeatTransfer() {
    return enableHeatTransfer;
  }
}
