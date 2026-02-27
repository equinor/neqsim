package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Erosion rate prediction for oil and gas production systems.
 *
 * <p>
 * Implements industry-standard erosion prediction models:
 * </p>
 * <ul>
 * <li>API RP 14E - Erosional velocity limits for production piping</li>
 * <li>DNV RP O501 - Erosive wear in piping systems (sand erosion)</li>
 * </ul>
 *
 * <p>
 * API RP 14E provides a simple erosional velocity limit:
 * </p>
 *
 * <pre>
 * {@code
 * Ve = C / sqrt(rho_m)
 * }
 * </pre>
 *
 * <p>
 * where Ve is erosional velocity (m/s), C is an empirical constant (typically 100-150 for
 * continuous/intermittent service in imperial units), and rho_m is mixture density (kg/m3).
 * </p>
 *
 * <p>
 * DNV RP O501 provides a mechanistic sand erosion model:
 * </p>
 *
 * <pre>
 * {@code
 * E = K * F(alpha) * Vp ^ n * (dp / d_ref) ^ 0.2 * (rho_p / rho_ref)
 * }
 * </pre>
 *
 * <p>
 * where E is erosion rate (mm/yr or kg removed per kg sand), K is a material constant, F(alpha) is
 * an impact angle function, Vp is particle velocity, dp is particle diameter, and n is a velocity
 * exponent (typically 2.6).
 * </p>
 *
 * <p>
 * The calculator covers:
 * </p>
 * <ul>
 * <li>Straight pipe erosion</li>
 * <li>Pipe bend/elbow erosion</li>
 * <li>Tee erosion (blind tee, standard tee)</li>
 * <li>Reducer/contraction erosion</li>
 * <li>Choke/valve erosion</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * ErosionPredictionCalculator calc = new ErosionPredictionCalculator();
 * calc.setMixtureDensity(150.0); // kg/m3
 * calc.setMixtureVelocity(10.0); // m/s
 * calc.setPipeDiameter(0.1524); // 6 inch in metres
 * calc.setSandRate(50.0); // kg/day
 * calc.setSandParticleDiameter(0.25); // mm
 * calc.setPipeMaterial("carbon_steel");
 * calc.setGeometry("elbow");
 * calc.calculate();
 * double erosionRate = calc.getErosionRate(); // mm/yr
 * double erosionalVelocity = calc.getErosionalVelocity(); // m/s
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ErosionPredictionCalculator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ========== Input Parameters ==========

  /** Mixture density in kg/m3. */
  private double mixtureDensity = 100.0;

  /** Gas density in kg/m3. */
  private double gasDensity = 50.0;

  /** Liquid density in kg/m3. */
  private double liquidDensity = 800.0;

  /** Mixture velocity in m/s. */
  private double mixtureVelocity = 10.0;

  /** Gas velocity (superficial) in m/s. */
  private double gasVelocity = 10.0;

  /** Liquid velocity (superficial) in m/s. */
  private double liquidVelocity = 0.5;

  /** Internal pipe diameter in metres. */
  private double pipeDiameter = 0.1524; // 6 inch

  /** Wall thickness in mm. */
  private double wallThickness = 10.0;

  /** Sand production rate in kg/day. */
  private double sandRate = 0.0;

  /** Sand particle diameter in mm. */
  private double sandParticleDiameter = 0.25;

  /** Sand particle density in kg/m3. */
  private double sandParticleDensity = 2650.0;

  /** Mixture dynamic viscosity in Pa.s. */
  private double mixtureViscosity = 0.001;

  /**
   * Pipe geometry type: "straight", "elbow", "tee", "blind_tee", "reducer", "choke", "weld".
   */
  private String geometry = "elbow";

  /** Bend radius / pipe diameter ratio for elbows (R/D). Typical: 1.5 for long-radius. */
  private double bendRadiusRatio = 1.5;

  /**
   * Pipe material: "carbon_steel", "duplex_steel", "super_duplex", "13cr", "inconel", "titanium".
   */
  private String pipeMaterial = "carbon_steel";

  /** API RP 14E C-factor. 100 for continuous service, 150 for intermittent (imperial units). */
  private double apiCFactor = 100.0;

  /** Corrosion allowance in mm. */
  private double corrosionAllowance = 3.0;

  /** Design life in years. */
  private double designLife = 25.0;

  // ========== Output Results ==========

  /** Erosional velocity per API RP 14E in m/s. */
  private double erosionalVelocity = 0.0;

  /** Velocity ratio (actual / erosional). */
  private double velocityRatio = 0.0;

  /** DNV RP O501 erosion rate in mm/year. */
  private double erosionRateMmPerYear = 0.0;

  /** Cumulative erosion over design life in mm. */
  private double cumulativeErosion = 0.0;

  /** Remaining wall thickness after erosion and corrosion in mm. */
  private double remainingWallThickness = 0.0;

  /** Whether the velocity is within API RP 14E limits. */
  private boolean withinApiLimits = true;

  /** Whether the cumulative erosion is within acceptable limits. */
  private boolean withinErosionLimits = true;

  /** Risk level: "low", "medium", "high", "critical". */
  private String riskLevel = "low";

  // ========== DNV RP O501 Material Constants ==========

  /**
   * Gets the material erosion constant K for DNV RP O501.
   *
   * @param material the pipe material identifier
   * @return material constant K in (m/s)^(-n)
   */
  private double getMaterialConstant(String material) {
    switch (material.toLowerCase()) {
      case "carbon_steel":
        return 2.0e-9;
      case "duplex_steel":
      case "22cr":
        return 2.0e-9;
      case "super_duplex":
      case "25cr":
        return 2.0e-9;
      case "13cr":
        return 2.0e-9;
      case "inconel":
      case "inconel_625":
        return 1.0e-9;
      case "titanium":
        return 1.0e-9;
      case "tungsten_carbide":
        return 5.0e-10;
      default:
        return 2.0e-9; // Default to carbon steel
    }
  }

  /**
   * Gets the velocity exponent n for DNV RP O501.
   *
   * @param material the pipe material identifier
   * @return velocity exponent n (typically 2.6 for ductile materials)
   */
  private double getVelocityExponent(String material) {
    switch (material.toLowerCase()) {
      case "tungsten_carbide":
        return 2.3;
      default:
        return 2.6; // Ductile materials
    }
  }

  /**
   * Calculates the geometry factor G for different pipe geometries per DNV RP O501.
   *
   * @return geometry factor (dimensionless)
   */
  private double getGeometryFactor() {
    switch (geometry.toLowerCase()) {
      case "straight":
        return 0.0; // No direct sand impaction in straight pipe (use different model)
      case "elbow":
      case "bend":
        // GF depends on R/D ratio; lower R/D = higher erosion
        if (bendRadiusRatio <= 1.0) {
          return 1.0;
        } else if (bendRadiusRatio <= 1.5) {
          return 0.8;
        } else if (bendRadiusRatio <= 3.0) {
          return 0.5;
        } else {
          return 0.3;
        }
      case "tee":
        return 0.6;
      case "blind_tee":
        return 0.3;
      case "reducer":
      case "contraction":
        return 0.4;
      case "choke":
      case "valve":
        return 2.0;
      case "weld":
        return 1.2;
      default:
        return 1.0;
    }
  }

  /**
   * Impact angle function F(alpha) for DNV RP O501.
   *
   * <p>
   * For ductile materials, maximum erosion occurs at 20-30 degree impact angle. For brittle
   * materials, maximum is at 90 degrees.
   * </p>
   *
   * @param impactAngleDeg impact angle in degrees
   * @return impact angle function value
   */
  private double impactAngleFunction(double impactAngleDeg) {
    double alpha = Math.toRadians(impactAngleDeg);
    // Ductile material model (Finnie/Bitter combined)
    double sinAlpha = Math.sin(alpha);
    double cosAlpha = Math.cos(alpha);

    if (impactAngleDeg <= 0) {
      return 0.0;
    }

    // Simplified Oka model for ductile materials
    double a = sinAlpha;
    double b = 1.0 + 0.6 * (1.0 - sinAlpha);
    return a * b;
  }

  /**
   * Estimates the particle impact velocity from the flow velocity.
   *
   * <p>
   * In multiphase flow, sand particles are accelerated by the fluid. The particle velocity is
   * typically 60-80% of the fluid velocity depending on particle size and fluid properties.
   * </p>
   *
   * @return estimated particle velocity in m/s
   */
  private double estimateParticleVelocity() {
    // Simple Stokes drag model for particle velocity ratio
    // Based on particle Stokes number
    double dpMetres = sandParticleDiameter / 1000.0;
    double stokesNumber = sandParticleDensity * dpMetres * dpMetres * mixtureVelocity
        / (18.0 * mixtureViscosity * pipeDiameter);

    // Velocity ratio based on Stokes number
    double velocityRatioParticle;
    if (stokesNumber > 10.0) {
      velocityRatioParticle = 0.9;
    } else if (stokesNumber > 1.0) {
      velocityRatioParticle = 0.8;
    } else {
      velocityRatioParticle = 0.7;
    }

    return mixtureVelocity * velocityRatioParticle;
  }

  /**
   * Performs the full erosion prediction calculation.
   *
   * <p>
   * Calculates:
   * </p>
   * <ul>
   * <li>API RP 14E erosional velocity</li>
   * <li>DNV RP O501 sand erosion rate (if sand present)</li>
   * <li>Cumulative erosion over design life</li>
   * <li>Remaining wall thickness</li>
   * <li>Risk assessment</li>
   * </ul>
   */
  public void calculate() {
    // 1. API RP 14E erosional velocity
    // Original formula: Ve = C / sqrt(rho_m) with rho_m in lb/ft3, Ve in ft/s
    // Convert to SI: rho_m in kg/m3, Ve in m/s
    // C_SI = C_imperial * sqrt(lb/ft3 to kg/m3) * (ft/s to m/s)
    // Factor: C_SI = C * sqrt(16.018) * 0.3048 = C * 1.22
    if (mixtureDensity > 0) {
      double rhoLbFt3 = mixtureDensity * 0.062428; // kg/m3 to lb/ft3
      double veFtS = apiCFactor / Math.sqrt(rhoLbFt3);
      erosionalVelocity = veFtS * 0.3048; // ft/s to m/s
    }

    // Velocity ratio
    velocityRatio = (erosionalVelocity > 0) ? mixtureVelocity / erosionalVelocity : 0.0;
    withinApiLimits = velocityRatio <= 1.0;

    // 2. DNV RP O501 sand erosion
    if (sandRate > 0 && !geometry.equalsIgnoreCase("straight")) {
      double k = getMaterialConstant(pipeMaterial);
      double n = getVelocityExponent(pipeMaterial);
      double gf = getGeometryFactor();
      double vp = estimateParticleVelocity();
      double dpRef = 0.00025; // reference particle diameter 0.25 mm
      double dpActual = sandParticleDiameter / 1000.0;
      double rhoRef = 2650.0; // reference sand density

      // DNV RP O501 erosion rate: E [kg/kg] = K * F(alpha) * Vp^n * (dp/dpRef)^0.2
      // * GF
      double impactAngle = estimateImpactAngle();
      double fAlpha = impactAngleFunction(impactAngle);

      double erosionKgPerKg = k * fAlpha * Math.pow(vp, n) * Math.pow(dpActual / dpRef, 0.2) * gf
          * (sandParticleDensity / rhoRef);

      // Convert to mm/year:
      // E [mm/yr] = erosionKgPerKg * sandRate [kg/day] * 365.25 / (rho_wall * A_erode)
      // rho_wall = material density (7850 kg/m3 for steel)
      // A_erode = eroded area (approximate)
      double materialDensity = getMaterialDensity(pipeMaterial);
      double erodedArea = getErodedArea();

      if (erodedArea > 0 && materialDensity > 0) {
        double sandRateKgPerYear = sandRate * 365.25;
        // Mass loss per year in kg:
        double massLossKgPerYear = erosionKgPerKg * sandRateKgPerYear;
        // Volume loss per year in m3:
        double volumeLossM3PerYear = massLossKgPerYear / materialDensity;
        // Depth of erosion = volume / area, convert to mm:
        erosionRateMmPerYear = (volumeLossM3PerYear / erodedArea) * 1000.0;
      }
    } else if (sandRate > 0) {
      // Straight pipe: much lower erosion, use empirical reduction
      double k = getMaterialConstant(pipeMaterial);
      double n = getVelocityExponent(pipeMaterial);
      double vp = estimateParticleVelocity();
      erosionRateMmPerYear = k * Math.pow(vp, n) * sandRate * 365.25 * 0.01;
    }

    // 3. Cumulative erosion
    cumulativeErosion = erosionRateMmPerYear * designLife;

    // 4. Remaining wall thickness
    remainingWallThickness = wallThickness - cumulativeErosion - corrosionAllowance;
    withinErosionLimits = remainingWallThickness > 0;

    // 5. Risk assessment
    assessRisk();
  }

  /**
   * Estimates the impact angle for the given geometry.
   *
   * @return impact angle in degrees
   */
  private double estimateImpactAngle() {
    switch (geometry.toLowerCase()) {
      case "straight":
        return 1.0;
      case "elbow":
      case "bend":
        // Impact angle depends on R/D and particle properties
        return Math.max(5.0, 45.0 / bendRadiusRatio);
      case "tee":
        return 45.0;
      case "blind_tee":
        return 90.0;
      case "reducer":
      case "contraction":
        return 15.0;
      case "choke":
      case "valve":
        return 30.0;
      case "weld":
        return 20.0;
      default:
        return 30.0;
    }
  }

  /**
   * Gets the material density for wall thickness calculations.
   *
   * @param material the pipe material identifier
   * @return density in kg/m3
   */
  private double getMaterialDensity(String material) {
    switch (material.toLowerCase()) {
      case "titanium":
        return 4500.0;
      case "inconel":
      case "inconel_625":
        return 8440.0;
      default:
        return 7850.0; // Carbon and alloy steels
    }
  }

  /**
   * Estimates the eroded area based on geometry and pipe size.
   *
   * @return eroded area in m2
   */
  private double getErodedArea() {
    double r = pipeDiameter / 2.0;
    switch (geometry.toLowerCase()) {
      case "elbow":
      case "bend":
        // Erosion scar is approximately 5-10% of the bend outer wall area
        return Math.PI * pipeDiameter * pipeDiameter * 0.1;
      case "tee":
      case "blind_tee":
        return Math.PI * r * r * 0.5;
      case "reducer":
      case "contraction":
        return Math.PI * r * r * 0.3;
      case "choke":
      case "valve":
        return Math.PI * r * r * 0.2;
      case "weld":
        return Math.PI * pipeDiameter * 0.005; // Thin ring
      case "straight":
        return Math.PI * pipeDiameter * 1.0; // per metre
      default:
        return Math.PI * r * r;
    }
  }

  /**
   * Performs risk assessment based on calculated erosion rate and velocity.
   */
  private void assessRisk() {
    if (erosionRateMmPerYear > 1.0 || velocityRatio > 1.5) {
      riskLevel = "critical";
    } else if (erosionRateMmPerYear > 0.5 || velocityRatio > 1.0) {
      riskLevel = "high";
    } else if (erosionRateMmPerYear > 0.1 || velocityRatio > 0.8) {
      riskLevel = "medium";
    } else {
      riskLevel = "low";
    }
  }

  // ========== Getters for Results ==========

  /**
   * Gets the API RP 14E erosional velocity.
   *
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return erosionalVelocity;
  }

  /**
   * Gets the velocity ratio (actual / erosional).
   *
   * @return velocity ratio (dimensionless)
   */
  public double getVelocityRatio() {
    return velocityRatio;
  }

  /**
   * Gets the DNV RP O501 erosion rate.
   *
   * @return erosion rate in mm/year
   */
  public double getErosionRate() {
    return erosionRateMmPerYear;
  }

  /**
   * Gets the cumulative erosion over the design life.
   *
   * @return cumulative erosion in mm
   */
  public double getCumulativeErosion() {
    return cumulativeErosion;
  }

  /**
   * Gets the remaining wall thickness after erosion and corrosion allowance.
   *
   * @return remaining wall thickness in mm
   */
  public double getRemainingWallThickness() {
    return remainingWallThickness;
  }

  /**
   * Checks whether the velocity is within API RP 14E limits.
   *
   * @return true if within limits
   */
  public boolean isWithinApiLimits() {
    return withinApiLimits;
  }

  /**
   * Checks whether cumulative erosion is within acceptable limits.
   *
   * @return true if within limits
   */
  public boolean isWithinErosionLimits() {
    return withinErosionLimits;
  }

  /**
   * Gets the risk level.
   *
   * @return risk level string: "low", "medium", "high", or "critical"
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  // ========== Setters for Input Parameters ==========

  /**
   * Sets the mixture density.
   *
   * @param mixtureDensity mixture density in kg/m3
   */
  public void setMixtureDensity(double mixtureDensity) {
    this.mixtureDensity = mixtureDensity;
  }

  /**
   * Sets the gas density.
   *
   * @param gasDensity gas density in kg/m3
   */
  public void setGasDensity(double gasDensity) {
    this.gasDensity = gasDensity;
  }

  /**
   * Sets the liquid density.
   *
   * @param liquidDensity liquid density in kg/m3
   */
  public void setLiquidDensity(double liquidDensity) {
    this.liquidDensity = liquidDensity;
  }

  /**
   * Sets the mixture velocity.
   *
   * @param mixtureVelocity mixture velocity in m/s
   */
  public void setMixtureVelocity(double mixtureVelocity) {
    this.mixtureVelocity = mixtureVelocity;
  }

  /**
   * Sets the gas superficial velocity.
   *
   * @param gasVelocity gas velocity in m/s
   */
  public void setGasVelocity(double gasVelocity) {
    this.gasVelocity = gasVelocity;
  }

  /**
   * Sets the liquid superficial velocity.
   *
   * @param liquidVelocity liquid velocity in m/s
   */
  public void setLiquidVelocity(double liquidVelocity) {
    this.liquidVelocity = liquidVelocity;
  }

  /**
   * Sets the internal pipe diameter.
   *
   * @param pipeDiameter pipe internal diameter in metres
   */
  public void setPipeDiameter(double pipeDiameter) {
    this.pipeDiameter = pipeDiameter;
  }

  /**
   * Sets the pipe wall thickness.
   *
   * @param wallThickness wall thickness in mm
   */
  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  /**
   * Sets the sand production rate.
   *
   * @param sandRate sand rate in kg/day
   */
  public void setSandRate(double sandRate) {
    this.sandRate = sandRate;
  }

  /**
   * Sets the sand particle diameter.
   *
   * @param sandParticleDiameter particle diameter in mm
   */
  public void setSandParticleDiameter(double sandParticleDiameter) {
    this.sandParticleDiameter = sandParticleDiameter;
  }

  /**
   * Sets the sand particle density.
   *
   * @param sandParticleDensity particle density in kg/m3
   */
  public void setSandParticleDensity(double sandParticleDensity) {
    this.sandParticleDensity = sandParticleDensity;
  }

  /**
   * Sets the mixture dynamic viscosity.
   *
   * @param mixtureViscosity viscosity in Pa.s
   */
  public void setMixtureViscosity(double mixtureViscosity) {
    this.mixtureViscosity = mixtureViscosity;
  }

  /**
   * Sets the pipe geometry type.
   *
   * @param geometry geometry type: "straight", "elbow", "tee", "blind_tee", "reducer", "choke",
   *        "weld"
   */
  public void setGeometry(String geometry) {
    this.geometry = geometry;
  }

  /**
   * Sets the bend radius to pipe diameter ratio (R/D) for elbows.
   *
   * @param bendRadiusRatio R/D ratio (typical: 1.5 for long-radius, 1.0 for short-radius)
   */
  public void setBendRadiusRatio(double bendRadiusRatio) {
    this.bendRadiusRatio = bendRadiusRatio;
  }

  /**
   * Sets the pipe material.
   *
   * @param pipeMaterial material identifier: "carbon_steel", "duplex_steel", "super_duplex",
   *        "13cr", "inconel", "titanium"
   */
  public void setPipeMaterial(String pipeMaterial) {
    this.pipeMaterial = pipeMaterial;
  }

  /**
   * Sets the API RP 14E C-factor.
   *
   * @param apiCFactor C-factor (100 for continuous, 150 for intermittent, in imperial units)
   */
  public void setApiCFactor(double apiCFactor) {
    this.apiCFactor = apiCFactor;
  }

  /**
   * Sets the corrosion allowance.
   *
   * @param corrosionAllowance corrosion allowance in mm
   */
  public void setCorrosionAllowance(double corrosionAllowance) {
    this.corrosionAllowance = corrosionAllowance;
  }

  /**
   * Sets the design life.
   *
   * @param designLife design life in years
   */
  public void setDesignLife(double designLife) {
    this.designLife = designLife;
  }

  /**
   * Gets the mixture density.
   *
   * @return mixture density in kg/m3
   */
  public double getMixtureDensity() {
    return mixtureDensity;
  }

  /**
   * Gets the mixture velocity.
   *
   * @return mixture velocity in m/s
   */
  public double getMixtureVelocity() {
    return mixtureVelocity;
  }

  /**
   * Gets the sand rate.
   *
   * @return sand rate in kg/day
   */
  public double getSandRate() {
    return sandRate;
  }

  /**
   * Gets the pipe geometry type.
   *
   * @return geometry type string
   */
  public String getGeometry() {
    return geometry;
  }

  /**
   * Gets the pipe material.
   *
   * @return material identifier string
   */
  public String getPipeMaterial() {
    return pipeMaterial;
  }

  /**
   * Converts results to a map suitable for JSON serialization.
   *
   * @return map of all inputs and results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input parameters
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("mixtureDensity_kgm3", mixtureDensity);
    inputs.put("mixtureVelocity_ms", mixtureVelocity);
    inputs.put("pipeDiameter_m", pipeDiameter);
    inputs.put("wallThickness_mm", wallThickness);
    inputs.put("sandRate_kgday", sandRate);
    inputs.put("sandParticleDiameter_mm", sandParticleDiameter);
    inputs.put("geometry", geometry);
    inputs.put("pipeMaterial", pipeMaterial);
    inputs.put("apiCFactor", apiCFactor);
    inputs.put("corrosionAllowance_mm", corrosionAllowance);
    inputs.put("designLife_years", designLife);
    result.put("inputs", inputs);

    // API RP 14E results
    Map<String, Object> apiResults = new LinkedHashMap<String, Object>();
    apiResults.put("erosionalVelocity_ms", erosionalVelocity);
    apiResults.put("velocityRatio", velocityRatio);
    apiResults.put("withinApiLimits", withinApiLimits);
    result.put("API_RP_14E", apiResults);

    // DNV RP O501 results
    Map<String, Object> dnvResults = new LinkedHashMap<String, Object>();
    dnvResults.put("erosionRate_mmyr", erosionRateMmPerYear);
    dnvResults.put("cumulativeErosion_mm", cumulativeErosion);
    dnvResults.put("remainingWallThickness_mm", remainingWallThickness);
    dnvResults.put("withinErosionLimits", withinErosionLimits);
    result.put("DNV_RP_O501", dnvResults);

    // Risk assessment
    result.put("riskLevel", riskLevel);

    return result;
  }

  /**
   * Returns JSON representation of all inputs and results.
   *
   * @return a JSON string
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
