package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.choke.GilbertChokeFlow;
import neqsim.process.mechanicaldesign.valve.choke.MultiphaseChokeFlow;
import neqsim.process.mechanicaldesign.valve.choke.SachdevaChokeFlow;
import neqsim.thermo.system.SystemInterface;

/**
 * Valve sizing implementation using multiphase choke flow models.
 *
 * <p>
 * This class provides valve sizing calculations for production chokes using industry-standard
 * two-phase flow correlations. It implements the {@link ControlValveSizingInterface} to integrate
 * with the existing ThrottlingValve unit operation.
 * </p>
 *
 * <p>
 * <b>Available Models:</b>
 * </p>
 * <ul>
 * <li><b>Sachdeva</b> - Mechanistic model (Sachdeva et al., 1986, SPE 15657)</li>
 * <li><b>Gilbert</b> - Empirical correlation (Gilbert, 1954)</li>
 * <li><b>Baxendell</b> - Empirical correlation (Baxendell, 1958)</li>
 * <li><b>Ros</b> - Empirical correlation (Ros, 1960)</li>
 * <li><b>Achong</b> - Empirical correlation (Achong, 1961)</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>
 * // Configure valve to use Sachdeva multiphase model
 * ThrottlingValve choke = new ThrottlingValve("Production Choke", feed);
 * choke.getMechanicalDesign().setValveSizingStandard("Sachdeva");
 * choke.getMechanicalDesign().setChokeDiameter(0.5, "in");
 * choke.setOutletPressure(30.0, "bara");
 * choke.run();
 * </pre>
 *
 * @author esol
 * @version 1.0
 * @see SachdevaChokeFlow
 * @see GilbertChokeFlow
 */
public class ControlValveSizing_MultiphaseChoke
    implements ControlValveSizingInterface, Serializable {
  private static final long serialVersionUID = 1L;

  /** The valve mechanical design object. */
  private ValveMechanicalDesign valveMechanicalDesign;

  /** The multiphase choke flow model. */
  private MultiphaseChokeFlow chokeModel;

  /** Model type name for reporting. */
  private String modelType = "Sachdeva";

  /** Whether choked (critical) flow is allowed. */
  private boolean allowChoked = true;

  /** Choke diameter in meters. */
  private double chokeDiameter = 0.0254; // Default 1 inch

  /** Pressure drop ratio factor for choked flow (not used for choke models but required). */
  private double xT = 0.137;

  /**
   * Constructs a new multiphase choke sizing method with default Sachdeva model.
   *
   * @param valveMechanicalDesign the parent valve mechanical design
   */
  public ControlValveSizing_MultiphaseChoke(ValveMechanicalDesign valveMechanicalDesign) {
    this.valveMechanicalDesign = valveMechanicalDesign;
    this.chokeModel = new SachdevaChokeFlow(chokeDiameter);
  }

  /**
   * Constructs a new multiphase choke sizing method with specified model type.
   *
   * @param valveMechanicalDesign the parent valve mechanical design
   * @param modelType model type: "Sachdeva", "Gilbert", "Baxendell", "Ros", or "Achong"
   */
  public ControlValveSizing_MultiphaseChoke(ValveMechanicalDesign valveMechanicalDesign,
      String modelType) {
    this.valveMechanicalDesign = valveMechanicalDesign;
    this.modelType = modelType;
    setChokeModel(modelType);
  }

  /**
   * Sets the choke flow model type.
   *
   * @param modelType model type: "Sachdeva", "Gilbert", "Baxendell", "Ros", or "Achong"
   */
  public void setChokeModel(String modelType) {
    this.modelType = modelType;

    if (modelType.equalsIgnoreCase("Sachdeva")) {
      this.chokeModel = new SachdevaChokeFlow(chokeDiameter);
    } else if (modelType.equalsIgnoreCase("Gilbert")) {
      GilbertChokeFlow gilbertModel = new GilbertChokeFlow(chokeDiameter);
      gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.GILBERT);
      this.chokeModel = gilbertModel;
    } else if (modelType.equalsIgnoreCase("Baxendell")) {
      GilbertChokeFlow gilbertModel = new GilbertChokeFlow(chokeDiameter);
      gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.BAXENDELL);
      this.chokeModel = gilbertModel;
    } else if (modelType.equalsIgnoreCase("Ros")) {
      GilbertChokeFlow gilbertModel = new GilbertChokeFlow(chokeDiameter);
      gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ROS);
      this.chokeModel = gilbertModel;
    } else if (modelType.equalsIgnoreCase("Achong")) {
      GilbertChokeFlow gilbertModel = new GilbertChokeFlow(chokeDiameter);
      gilbertModel.setCorrelationType(GilbertChokeFlow.CorrelationType.ACHONG);
      this.chokeModel = gilbertModel;
    } else {
      // Default to Sachdeva
      this.chokeModel = new SachdevaChokeFlow(chokeDiameter);
    }
  }

  /**
   * Sets the choke diameter.
   *
   * @param diameter diameter value
   * @param unit unit: "m", "mm", "in", "64ths"
   */
  public void setChokeDiameter(double diameter, String unit) {
    this.chokeDiameter = convertToMeters(diameter, unit);
    chokeModel.setChokeDiameter(this.chokeDiameter);
  }

  /**
   * Gets the choke diameter in meters.
   *
   * @return choke diameter in meters
   */
  public double getChokeDiameter() {
    return chokeDiameter;
  }

  /**
   * Sets the discharge coefficient.
   *
   * @param cd discharge coefficient (typically 0.75-0.90)
   */
  public void setDischargeCoefficient(double cd) {
    chokeModel.setDischargeCoefficient(cd);
  }

  /**
   * Gets the discharge coefficient.
   *
   * @return discharge coefficient
   */
  public double getDischargeCoefficient() {
    return chokeModel.getDischargeCoefficient();
  }

  /**
   * Converts diameter to meters from various units.
   */
  private double convertToMeters(double value, String unit) {
    switch (unit.toLowerCase()) {
      case "m":
        return value;
      case "mm":
        return value / 1000.0;
      case "in":
      case "inch":
      case "inches":
        return value * 0.0254;
      case "64ths":
      case "64th":
        return value / 64.0 * 0.0254;
      default:
        return value;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> calcValveSize(double percentOpening) {
    Map<String, Object> results = new HashMap<>();

    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    if (valve == null || valve.getInletStream() == null) {
      results.put("error", "No inlet stream connected");
      return results;
    }

    StreamInterface inletStream = valve.getInletStream();
    SystemInterface fluid = inletStream.getThermoSystem();

    if (fluid == null) {
      results.put("error", "No fluid in inlet stream");
      return results;
    }

    double P1 = fluid.getPressure("Pa");
    double P2 = valve.getOutletPressure() * 1e5; // bara to Pa

    // Apply opening adjustment to effective choke diameter
    double effectiveDiameter = chokeDiameter * Math.sqrt(percentOpening / 100.0);
    chokeModel.setChokeDiameter(effectiveDiameter);

    // Get comprehensive sizing results
    Map<String, Object> chokeResults = chokeModel.calculateSizingResults(fluid, P1, P2);

    // Transfer choke results
    results.putAll(chokeResults);

    // Add model info
    results.put("modelType", modelType);
    results.put("modelName", chokeModel.getModelName());
    results.put("nominalChokeDiameter", chokeDiameter);
    results.put("effectiveChokeDiameter", effectiveDiameter);
    results.put("percentOpening", percentOpening);

    // Calculate Kv equivalent for compatibility
    double massFlow = (Double) chokeResults.getOrDefault("massFlowRate", 0.0);
    double density = fluid.getDensity("kg/m3");
    double deltaP = (P1 - P2) / 1e5; // Pa to bar
    if (deltaP > 0 && density > 0) {
      double Q_m3h = massFlow / density * 3600.0;
      double Kv = Q_m3h * Math.sqrt(density / 1000.0 / deltaP);
      results.put("Kv", Kv);
    } else {
      results.put("Kv", 0.0);
    }

    return results;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateFlowRateFromValveOpening(double actualKv, StreamInterface inletStream,
      StreamInterface outletStream) {
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      return 0.0;
    }

    SystemInterface fluid = inletStream.getThermoSystem();
    double P1 = fluid.getPressure("Pa");
    double P2 = outletStream.getThermoSystem().getPressure("Pa");

    // For choke models, get the percent opening directly from the valve
    // rather than trying to back-calculate from Kv
    ThrottlingValve valve = (ThrottlingValve) valveMechanicalDesign.getProcessEquipment();
    double percentOpening = valve.getPercentValveOpening();

    // Calculate effective diameter based on valve opening
    double effectiveDiameter = chokeDiameter * Math.sqrt(percentOpening / 100.0);
    chokeModel.setChokeDiameter(effectiveDiameter);

    double massFlow = chokeModel.calculateMassFlowRate(fluid, P1, P2);
    double density = fluid.getDensity("kg/m3");

    if (density > 0) {
      return massFlow / density; // Return volumetric flow m3/s
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calculateValveOpeningFromFlowRate(double Q, double actualKv,
      StreamInterface inletStream, StreamInterface outletStream) {
    // Iterative solution to find opening that gives target flow
    double tolerance = 0.001;
    int maxIterations = 50;

    double openingLow = 0.0;
    double openingHigh = 100.0;

    for (int i = 0; i < maxIterations; i++) {
      double openingMid = (openingLow + openingHigh) / 2.0;

      double effectiveDiameter = chokeDiameter * Math.sqrt(openingMid / 100.0);
      chokeModel.setChokeDiameter(effectiveDiameter);

      SystemInterface fluid = inletStream.getThermoSystem();
      double P1 = fluid.getPressure("Pa");
      double P2 = outletStream.getThermoSystem().getPressure("Pa");

      double massFlow = chokeModel.calculateMassFlowRate(fluid, P1, P2);
      double density = fluid.getDensity("kg/m3");
      double calcQ = massFlow / density;

      if (Math.abs(calcQ - Q) / Q < tolerance) {
        return openingMid;
      }

      if (calcQ > Q) {
        openingHigh = openingMid;
      } else {
        openingLow = openingMid;
      }
    }

    return (openingLow + openingHigh) / 2.0;
  }

  /** {@inheritDoc} */
  @Override
  public double findOutletPressureForFixedKv(double actualKv, StreamInterface inletStream) {
    if (inletStream == null || inletStream.getThermoSystem() == null) {
      return 0.0;
    }

    SystemInterface fluid = inletStream.getThermoSystem();
    double P1 = fluid.getPressure("Pa");

    // Calculate target mass flow from Kv
    double maxKv = valveMechanicalDesign.getValveCvMax() * 0.865;
    if (maxKv > 0) {
      double openingFraction = Math.min(1.0, actualKv / maxKv);
      double effectiveDiameter = chokeDiameter * Math.sqrt(openingFraction);
      chokeModel.setChokeDiameter(effectiveDiameter);
    }

    // For choke models, if we're in critical flow, P2 is the critical pressure
    // Otherwise, we need flow rate info to determine P2
    double gasQuality = chokeModel.calculateGasQuality(fluid);
    double gamma = fluid.getGamma2();
    double criticalRatio = chokeModel.calculateCriticalPressureRatio(gasQuality, gamma);

    // Return critical pressure as a conservative estimate
    // (actual P2 depends on downstream conditions)
    return P1 * criticalRatio;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAllowChoked() {
    return allowChoked;
  }

  /** {@inheritDoc} */
  @Override
  public void setAllowChoked(boolean allowChoked) {
    this.allowChoked = allowChoked;
  }

  /** {@inheritDoc} */
  @Override
  public double getxT() {
    return xT;
  }

  /** {@inheritDoc} */
  @Override
  public void setxT(double xT) {
    this.xT = xT;
  }

  /**
   * Checks if laminar flow is allowed.
   *
   * @return true (choke models handle all flow regimes)
   */
  public boolean isAllowLaminar() {
    return true; // Choke models handle all flow regimes
  }

  /**
   * Sets whether laminar flow is allowed (no effect for choke models).
   *
   * @param allowLaminar laminar flow flag (ignored)
   */
  public void setAllowLaminar(boolean allowLaminar) {
    // Not applicable for choke models
  }

  /**
   * Gets the underlying choke flow model.
   *
   * @return the multiphase choke flow model
   */
  public MultiphaseChokeFlow getChokeModel() {
    return chokeModel;
  }

  /**
   * Gets the model type name.
   *
   * @return model type name
   */
  public String getModelType() {
    return modelType;
  }

  /**
   * Gets comprehensive choke sizing results as a formatted string.
   *
   * @param inletStream the inlet stream
   * @param outletPressure_bara outlet pressure in bara
   * @return formatted results string
   */
  public String getChokeReport(StreamInterface inletStream, double outletPressure_bara) {
    SystemInterface fluid = inletStream.getThermoSystem();
    double P1 = fluid.getPressure("Pa");
    double P2 = outletPressure_bara * 1e5;

    Map<String, Object> results = chokeModel.calculateSizingResults(fluid, P1, P2);

    StringBuilder sb = new StringBuilder();
    sb.append("=== Multiphase Choke Flow Report ===\n");
    sb.append(String.format("Model: %s\n", chokeModel.getModelName()));
    sb.append(String.format("Choke Diameter: %.2f mm (%.3f in)\n", chokeDiameter * 1000,
        chokeDiameter / 0.0254));
    sb.append(String.format("Discharge Coefficient: %.3f\n", chokeModel.getDischargeCoefficient()));
    sb.append("\n--- Operating Conditions ---\n");
    sb.append(String.format("Upstream Pressure: %.2f bara\n", P1 / 1e5));
    sb.append(String.format("Downstream Pressure: %.2f bara\n", P2 / 1e5));
    sb.append(String.format("Pressure Ratio (P2/P1): %.3f\n", results.get("actualPressureRatio")));
    sb.append("\n--- Flow Results ---\n");
    sb.append(String.format("Mass Flow Rate: %.4f kg/s\n", results.get("massFlowRate")));
    sb.append(String.format("Gas Quality: %.3f\n", results.get("gasQuality")));
    sb.append(String.format("GLR: %.1f Sm3/Sm3\n", results.get("GLR")));
    sb.append(String.format("Flow Regime: %s\n", results.get("flowRegime")));
    sb.append(
        String.format("Critical Pressure Ratio: %.3f\n", results.get("criticalPressureRatio")));
    sb.append(String.format("Is Choked: %s\n", results.get("isChoked")));

    return sb.toString();
  }
}
