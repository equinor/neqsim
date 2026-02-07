package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Implementation of AGA Report No. 3 (API 14.3 / ISO 5167) - Orifice Metering of Natural Gas and
 * Other Related Hydrocarbon Fluids.
 *
 * <p>
 * AGA 3 (also published as API MPMS Chapter 14.3) specifies the measurement of natural gas flow
 * rate using orifice meters. It is the primary custody transfer metering standard in North America.
 * The standard consists of four parts:
 * </p>
 * <ul>
 * <li>Part 1: General equations and uncertainty guidelines</li>
 * <li>Part 2: Specification and installation requirements</li>
 * <li>Part 3: Natural gas applications</li>
 * <li>Part 4: Background, development, implementation and calculation procedures</li>
 * </ul>
 *
 * <p>
 * Key calculations:
 * </p>
 * <ul>
 * <li>Mass flow rate from differential pressure and orifice geometry</li>
 * <li>Discharge coefficient per Reader-Harris/Gallagher (RG) equation</li>
 * <li>Expansion factor for compressible flow</li>
 * <li>Flow rate in standard cubic feet per day (SCFD) or Sm3/d</li>
 * <li>Gas density at flowing conditions from composition (uses AGA 8 or ISO 12213)</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_AGA3 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_AGA3.class);

  /** Orifice bore diameter in meters. */
  private double orificeDiameter = 0.0508; // 2 inch default

  /** Pipe internal diameter in meters. */
  private double pipeDiameter = 0.2032; // 8 inch default

  /** Differential pressure across orifice in Pa. */
  private double differentialPressure = 5000.0; // 50 mbar

  /** Static pressure (upstream) in Pa absolute. */
  private double staticPressure = 7000000.0; // ~70 bara

  /** Flowing gas temperature in K. */
  private double flowingTemperature = 288.15; // 15C

  /** Tap type: "flange", "corner", "D-D/2". */
  private String tapType = "flange";

  /** Beta ratio (d/D). */
  private double betaRatio = 0.0;

  /** Discharge coefficient (Cd). */
  private double dischargeCoefficient = 0.0;

  /** Expansion factor (Y). */
  private double expansionFactor = 0.0;

  /** Velocity of approach factor (Ev). */
  private double velocityOfApproachFactor = 0.0;

  /** Calculated mass flow rate in kg/s. */
  private double massFlowRate = 0.0;

  /** Calculated volume flow rate at standard conditions in Sm3/h. */
  private double standardVolumeFlowRate = 0.0;

  /** Gas density at flowing conditions in kg/m3. */
  private double flowingDensity = 0.0;

  /** Gas density at standard conditions in kg/m3. */
  private double standardDensity = 0.0;

  /** Isentropic exponent (kappa). */
  private double isentropicExponent = 1.3;

  /** Reynolds number at orifice. */
  private double reynoldsNumber = 0.0;

  /** Dynamic viscosity at flowing conditions in Pa*s. */
  private double viscosity = 0.0;

  /**
   * Constructor for Standard_AGA3.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_AGA3(SystemInterface thermoSystem) {
    super("Standard_AGA3", "Orifice Metering of Natural Gas (AGA 3 / API 14.3 / ISO 5167)",
        thermoSystem);
  }

  /**
   * Sets the orifice geometry.
   *
   * @param orificeDiameterM orifice bore diameter in meters
   * @param pipeDiameterM pipe internal diameter in meters
   */
  public void setOrificeDimensions(double orificeDiameterM, double pipeDiameterM) {
    this.orificeDiameter = orificeDiameterM;
    this.pipeDiameter = pipeDiameterM;
  }

  /**
   * Sets the differential pressure.
   *
   * @param dpPa differential pressure in Pa
   */
  public void setDifferentialPressure(double dpPa) {
    this.differentialPressure = dpPa;
  }

  /**
   * Sets the static (upstream) pressure.
   *
   * @param pressurePa static pressure in Pa absolute
   */
  public void setStaticPressure(double pressurePa) {
    this.staticPressure = pressurePa;
  }

  /**
   * Sets the flowing gas temperature.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setFlowingTemperature(double temperatureK) {
    this.flowingTemperature = temperatureK;
  }

  /**
   * Sets the pressure tap type.
   *
   * @param type "flange", "corner", or "D-D/2"
   */
  public void setTapType(String type) {
    this.tapType = type;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      betaRatio = orificeDiameter / pipeDiameter;

      // Get gas properties at flowing conditions
      SystemInterface flowingSystem = thermoSystem.clone();
      flowingSystem.setTemperature(flowingTemperature);
      flowingSystem.setPressure(staticPressure / 1.0e5); // Pa to bara
      flowingSystem.init(0);
      flowingSystem.init(1);
      ThermodynamicOperations ops = new ThermodynamicOperations(flowingSystem);
      ops.TPflash();
      flowingSystem.initPhysicalProperties();

      flowingDensity = flowingSystem.getPhase(0).getDensity("kg/m3");
      viscosity = flowingSystem.getPhase(0).getViscosity("kg/msec");
      isentropicExponent = flowingSystem.getPhase(0).getCp() / flowingSystem.getPhase(0).getCv();

      // Get density at standard conditions
      SystemInterface stdSystem = thermoSystem.clone();
      stdSystem.setTemperature(288.15); // 15C
      stdSystem.setPressure(1.01325); // 1 atm
      stdSystem.init(0);
      stdSystem.init(1);
      ops = new ThermodynamicOperations(stdSystem);
      ops.TPflash();
      standardDensity = stdSystem.getPhase(0).getDensity("kg/m3");

      // Step 1: Initial estimate of discharge coefficient (Cd) using RG equation
      // Reader-Harris/Gallagher equation for Cd (AGA 3 Part 1, ISO 5167)
      reynoldsNumber = 1.0e6; // Initial estimate for iterative Cd
      dischargeCoefficient = calculateDischargeCoefficient(betaRatio, reynoldsNumber);

      // Step 2: Expansion factor Y (AGA 3, ISO 5167)
      expansionFactor = calculateExpansionFactor(betaRatio, differentialPressure, staticPressure,
          isentropicExponent);

      // Step 3: Velocity of approach factor Ev
      velocityOfApproachFactor = 1.0 / Math.sqrt(1.0 - Math.pow(betaRatio, 4));

      // Step 4: Mass flow rate
      // qm = Cd * Ev * Y * (pi/4) * d^2 * sqrt(2 * rho * dP)
      double areaOrifice = (Math.PI / 4.0) * orificeDiameter * orificeDiameter;
      massFlowRate = dischargeCoefficient * velocityOfApproachFactor * expansionFactor * areaOrifice
          * Math.sqrt(2.0 * flowingDensity * differentialPressure);

      // Iterate to refine Cd based on actual Reynolds number
      for (int iter = 0; iter < 5; iter++) {
        if (viscosity > 0.0 && orificeDiameter > 0.0) {
          reynoldsNumber = 4.0 * massFlowRate / (Math.PI * orificeDiameter * viscosity);
        }
        dischargeCoefficient = calculateDischargeCoefficient(betaRatio, reynoldsNumber);
        massFlowRate = dischargeCoefficient * velocityOfApproachFactor * expansionFactor
            * areaOrifice * Math.sqrt(2.0 * flowingDensity * differentialPressure);
      }

      // Step 5: Convert to standard volume flow rate
      if (standardDensity > 0.0) {
        standardVolumeFlowRate = massFlowRate / standardDensity * 3600.0; // Sm3/h
      }

    } catch (Exception ex) {
      logger.error("AGA 3 calculation failed", ex);
    }
  }

  /**
   * Calculates discharge coefficient using Reader-Harris/Gallagher equation.
   *
   * @param beta beta ratio d/D
   * @param re Reynolds number based on orifice diameter
   * @return discharge coefficient Cd
   */
  private double calculateDischargeCoefficient(double beta, double re) {
    double beta4 = Math.pow(beta, 4);
    double beta8 = beta4 * beta4;

    // Infinite Reynolds number coefficient
    double cInf =
        0.5961 + 0.0261 * beta * beta - 0.216 * beta8 + 0.000521 * Math.pow(1.0e6 * beta / re, 0.7);

    // Upstream tap term
    double l1 = 0.0;
    double l2prime = 0.0;
    if ("flange".equals(tapType)) {
      l1 = 25.4e-3 / pipeDiameter; // 1 inch / D
      l2prime = 25.4e-3 / pipeDiameter;
    } else if ("corner".equals(tapType)) {
      l1 = 0.0;
      l2prime = 0.0;
    } else { // D and D/2 taps
      l1 = 1.0;
      l2prime = 0.47;
    }

    double a1 = Math.pow(19000.0 * beta / re, 0.8);
    double m2 = 2.0 * l2prime / (1.0 - beta);

    double upstreamTerm = (0.0188 + 0.0063 * a1) * beta * beta * beta * Math.sqrt(beta)
        * (1.0 - 0.11 * a1) * l1 / (1.0 - beta);

    double downstreamTerm = -0.031 * (m2 - 0.8 * Math.pow(m2, 1.1)) * beta * beta * beta * beta
        * (1.0 + 0.8 * Math.exp(-10.0 * l1));

    return cInf + upstreamTerm + downstreamTerm;
  }

  /**
   * Calculates the expansion factor for compressible flow.
   *
   * @param beta beta ratio
   * @param dp differential pressure in Pa
   * @param p1 upstream static pressure in Pa
   * @param kappa isentropic exponent
   * @return expansion factor Y
   */
  private double calculateExpansionFactor(double beta, double dp, double p1, double kappa) {
    // ISO 5167 / AGA 3 expansion factor
    double tau = dp / p1;
    return 1.0 - (0.351 + 0.256 * Math.pow(beta, 4) + 0.93 * Math.pow(beta, 8))
        * (1.0 - Math.pow(1.0 - tau, 1.0 / kappa));
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double value = getValue(returnParameter);
    if ("massFlowRate".equals(returnParameter)) {
      if ("kg/h".equals(returnUnit)) {
        return value * 3600.0;
      }
      if ("lb/h".equals(returnUnit)) {
        return value * 3600.0 * 2.20462;
      }
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      if ("SCFD".equals(returnUnit) || "scf/d".equals(returnUnit)) {
        return value * 24.0 * 35.3147; // Sm3/h to scf/d
      }
      if ("MMSCFD".equals(returnUnit)) {
        return value * 24.0 * 35.3147 / 1.0e6;
      }
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("massFlowRate".equals(returnParameter)) {
      return massFlowRate;
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      return standardVolumeFlowRate;
    }
    if ("dischargeCoefficient".equals(returnParameter) || "Cd".equals(returnParameter)) {
      return dischargeCoefficient;
    }
    if ("expansionFactor".equals(returnParameter) || "Y".equals(returnParameter)) {
      return expansionFactor;
    }
    if ("betaRatio".equals(returnParameter)) {
      return betaRatio;
    }
    if ("reynoldsNumber".equals(returnParameter) || "Re".equals(returnParameter)) {
      return reynoldsNumber;
    }
    if ("flowingDensity".equals(returnParameter)) {
      return flowingDensity;
    }
    if ("isentropicExponent".equals(returnParameter)) {
      return isentropicExponent;
    }
    return massFlowRate;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("massFlowRate".equals(returnParameter)) {
      return "kg/s";
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      return "Sm3/h";
    }
    if ("dischargeCoefficient".equals(returnParameter) || "Cd".equals(returnParameter)
        || "expansionFactor".equals(returnParameter) || "Y".equals(returnParameter)
        || "betaRatio".equals(returnParameter)) {
      return "-";
    }
    if ("reynoldsNumber".equals(returnParameter) || "Re".equals(returnParameter)) {
      return "-";
    }
    if ("flowingDensity".equals(returnParameter)) {
      return "kg/m3";
    }
    if ("isentropicExponent".equals(returnParameter)) {
      return "-";
    }
    return "kg/s";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    // AGA 3 validity: 0.1 <= beta <= 0.75, Re >= 4000
    return betaRatio >= 0.1 && betaRatio <= 0.75 && reynoldsNumber >= 4000;
  }
}
