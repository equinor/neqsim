package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * {@link DepositSource} that computes a solid-precipitation deposition rate from a process stream using a NeqSim solid
 * (TP-solid) flash.
 *
 * <p>
 * This covers solids that the equation of state can drop out directly, most importantly elemental sulfur ({@code S8}),
 * but also wax or asphaltene when those solid checks are configured. The stream is cloned, multi-phase and the named
 * solid-phase check are enabled, a {@code TPSolidflash} is run at the stream conditions, and the resulting solid-phase
 * mass rate is multiplied by a capture fraction (the fraction of precipitated solids that stick to the impeller rather
 * than pass through).
 * </p>
 *
 * <p>
 * The stream should represent the fluid entering the compressor, including any entrained liquid (condensate/water
 * droplets) carried over from an upstream separator, since the entrained liquid is where dissolved sulfur or salt is
 * transported. Model entrainment upstream by mixing the carried-over liquid into the compressor feed (or by an
 * imperfect separator efficiency).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SolidFlashDepositSource implements DepositSource {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(SolidFlashDepositSource.class);

  private final StreamInterface stream;
  private final String solidComponent;
  private final DepositMechanism mechanism;
  private double captureFraction = 1.0;
  private double evaluationTemperatureC = Double.NaN;
  private double evaluationPressureBara = Double.NaN;
  private transient Compressor thermalCompressor;
  private String thermalNodeId;
  private double lastEvaluationTemperatureC = Double.NaN;
  private double lastEvaluationPressureBara = Double.NaN;
  private double lastLiquidEvaporatedFraction = Double.NaN;

  /**
   * Constructor.
   *
   * @param stream stream entering the compressor (including any entrained liquid)
   * @param solidComponent name of the component allowed to form a solid, for example "S8"
   * @param mechanism deposit mechanism (density) used by the deposit model
   * @param captureFraction fraction of precipitated solid that deposits on the machine (0-1)
   */
  public SolidFlashDepositSource(StreamInterface stream, String solidComponent, DepositMechanism mechanism,
      double captureFraction) {
    this.stream = stream;
    this.solidComponent = solidComponent;
    this.mechanism = mechanism;
    setCaptureFraction(captureFraction);
  }

  /** {@inheritDoc} */
  @Override
  public DepositMechanism getMechanism() {
    return mechanism;
  }

  /**
   * Total precipitated-solid rate at the stream conditions (before the capture fraction is applied). This is the
   * thermodynamic drop-out rate of the named solid.
   *
   * @param flowUnit mass-flow unit, for example "kg/hr"
   * @return precipitated solid rate in the requested unit (0 if no solid forms)
   */
  public double getPrecipitationRate(String flowUnit) {
    if (stream == null || stream.getThermoSystem() == null) {
      return 0.0;
    }
    try {
      SystemInterface sys = stream.getThermoSystem().clone();
      if (sys.getComponent(solidComponent) == null) {
        return 0.0;
      }
      double liquidBeforeKgPerHour = Double.NaN;
      double localTemperatureC = resolveEvaluationTemperatureC();
      double localPressureBara = resolveEvaluationPressureBara();
      if (Double.isFinite(localTemperatureC) || Double.isFinite(localPressureBara)) {
        liquidBeforeKgPerHour = calculateLiquidFlowKgPerHour(sys, stream.getTemperature("C"),
            stream.getPressure("bara"));
      }
      if (Double.isFinite(localTemperatureC)) {
        sys.setTemperature(localTemperatureC, "C");
      }
      if (Double.isFinite(localPressureBara)) {
        sys.setPressure(localPressureBara, "bara");
      }
      lastEvaluationTemperatureC = sys.getTemperature("C");
      lastEvaluationPressureBara = sys.getPressure("bara");
      sys.setMultiPhaseCheck(true);
      sys.setSolidPhaseCheck(solidComponent);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPSolidflash();
      updateLiquidEvaporationFraction(liquidBeforeKgPerHour, sys);
      if (!sys.hasPhaseType("solid")) {
        return 0.0;
      }
      return sys.getPhaseOfType("solid").getFlowRate(flowUnit);
    } catch (Exception e) {
      logger.warn("Solid precipitation flash failed for component {}: {}", solidComponent, e.getMessage());
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDepositRate(String flowUnit) {
    return getPrecipitationRate(flowUnit) * captureFraction;
  }

  /**
   * Get the capture fraction.
   *
   * @return fraction of precipitated solid that deposits on the machine (0-1)
   */
  public double getCaptureFraction() {
    return captureFraction;
  }

  /**
   * Set the capture fraction.
   *
   * @param captureFraction fraction of precipitated solid that deposits on the machine (0-1)
   */
  public void setCaptureFraction(double captureFraction) {
    this.captureFraction = Math.max(0.0, Math.min(1.0, captureFraction));
  }

  /**
   * Evaluate precipitation at a local equipment temperature and pressure rather than at the bulk inlet-stream
   * conditions. This is useful when entrained condensate reaches a warmer compressor shaft or seal surface and
   * evaporates locally.
   *
   * @param temperatureC local temperature in Celsius, or NaN to use the stream temperature
   * @param pressureBara local pressure in bara, or NaN to use the stream pressure
   */
  public void setEvaluationConditions(double temperatureC, double pressureBara) {
    if (!Double.isNaN(temperatureC) && !Double.isFinite(temperatureC)) {
      throw new IllegalArgumentException("evaluation temperature must be finite or NaN");
    }
    if (!Double.isNaN(pressureBara) && (!Double.isFinite(pressureBara) || pressureBara <= 0.0)) {
      throw new IllegalArgumentException("evaluation pressure must be positive or NaN");
    }
    evaluationTemperatureC = temperatureC;
    evaluationPressureBara = pressureBara;
    thermalCompressor = null;
    thermalNodeId = null;
  }

  /**
   * Use a local temperature from an attached compressor thermal model for every evaluation. Pressure defaults to the
   * compressor inlet pressure unless an explicit evaluation pressure was set first.
   *
   * @param compressor compressor with an attached and solved thermal model
   * @param nodeId thermal node identifier, for example {@link CompressorThermalModel#INLET_SHAFT}
   */
  public void setThermalNode(Compressor compressor, String nodeId) {
    if (compressor == null || compressor.getThermalModel() == null) {
      throw new IllegalArgumentException("compressor must have an attached thermal model");
    }
    if (nodeId == null || compressor.getThermalModel().getNode(nodeId) == null) {
      throw new IllegalArgumentException("thermal node must exist in the compressor model");
    }
    thermalCompressor = compressor;
    thermalNodeId = nodeId;
  }

  /** Stop using a compressor thermal node and return to explicit or stream conditions. */
  public void clearThermalNode() {
    thermalCompressor = null;
    thermalNodeId = null;
  }

  /** @return temperature used by the last precipitation flash in Celsius */
  public double getLastEvaluationTemperatureC() {
    return lastEvaluationTemperatureC;
  }

  /** @return pressure used by the last precipitation flash in bara */
  public double getLastEvaluationPressureBara() {
    return lastEvaluationPressureBara;
  }

  /**
   * Return the fraction of inlet liquid evaporated between bulk stream and local evaluation conditions. Returns NaN
   * when no local condition was configured or no inlet liquid existed.
   *
   * @return liquid evaporated fraction from 0 to 1, or NaN
   */
  public double getLastLiquidEvaporatedFraction() {
    return lastLiquidEvaporatedFraction;
  }

  private double resolveEvaluationTemperatureC() {
    if (thermalCompressor != null && thermalCompressor.getThermalModel() != null && thermalNodeId != null) {
      return thermalCompressor.getThermalModel().getTemperature(thermalNodeId, "C");
    }
    return evaluationTemperatureC;
  }

  private double resolveEvaluationPressureBara() {
    if (Double.isFinite(evaluationPressureBara)) {
      return evaluationPressureBara;
    }
    if (thermalCompressor != null && thermalCompressor.getInletStream() != null) {
      return thermalCompressor.getInletStream().getPressure("bara");
    }
    return Double.NaN;
  }

  private static double calculateLiquidFlowKgPerHour(SystemInterface baseSystem, double temperatureC,
      double pressureBara) {
    try {
      SystemInterface check = baseSystem.clone();
      check.setTemperature(temperatureC, "C");
      check.setPressure(pressureBara, "bara");
      check.setMultiPhaseCheck(true);
      new ThermodynamicOperations(check).TPflash();
      return getLiquidFlowKgPerHour(check);
    } catch (Exception ex) {
      return Double.NaN;
    }
  }

  private void updateLiquidEvaporationFraction(double liquidBeforeKgPerHour, SystemInterface evaluatedSystem) {
    if (!Double.isFinite(liquidBeforeKgPerHour) || liquidBeforeKgPerHour <= 0.0) {
      lastLiquidEvaporatedFraction = Double.NaN;
      return;
    }
    double liquidAfterKgPerHour = getLiquidFlowKgPerHour(evaluatedSystem);
    lastLiquidEvaporatedFraction = Math.max(0.0,
        Math.min(1.0, (liquidBeforeKgPerHour - liquidAfterKgPerHour) / liquidBeforeKgPerHour));
  }

  private static double getLiquidFlowKgPerHour(SystemInterface system) {
    double liquid = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phaseType = system.getPhase(i).getPhaseTypeName();
      if ("oil".equals(phaseType) || "aqueous".equals(phaseType) || "liquid".equals(phaseType)) {
        liquid += Math.max(0.0, system.getPhase(i).getFlowRate("kg/hr"));
      }
    }
    return liquid;
  }
}
