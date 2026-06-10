package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * Calculator class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Calculator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Calculator.class);

  ArrayList<ProcessEquipmentInterface> inputVariable = new ArrayList<ProcessEquipmentInterface>();
  private ProcessEquipmentInterface outputVariable;
  private transient BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> calculationMethod;
  private Runnable simpleCalculationMethod;
  String type = "sumTEG";

  /** Anti-surge: emit a warning if compressor stays below this fraction of surge after update. */
  private static final double ANTI_SURGE_STUCK_THRESHOLD = 0.98;

  /**
   * <p>
   * Constructor for Calculator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Calculator(String name) {
    super(name);
  }

  /**
   * <p>
   * addInputVariable.
   * </p>
   *
   * @param unit a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addInputVariable(ProcessEquipmentInterface unit) {
    inputVariable.add(unit);
  }

  /**
   * <p>
   * addInputVariable.
   * </p>
   *
   * @param units a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addInputVariable(ProcessEquipmentInterface... units) {
    for (ProcessEquipmentInterface unit : units) {
      inputVariable.add(unit);
    }
  }

  /**
   * <p>
   * Getter for the field <code>inputVariable</code>.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<ProcessEquipmentInterface> getInputVariable() {
    return inputVariable;
  }

  /**
   * <p>
   * Getter for the field <code>outputVariable</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getOutputVariable() {
    return outputVariable;
  }

  /**
   * <p>
   * runAntiSurgeCalc.
   * </p>
   *
   * @param id a {@link java.util.UUID} object
   */
  public void runAntiSurgeCalc(UUID id) {
    Compressor compressor = (Compressor) inputVariable.get(0);

    Splitter antiSurgeSplitter = (Splitter) outputVariable;

    double inletFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double surgeFlow = compressor.getSurgeFlowRate();
    double currentRecycle = antiSurgeSplitter.getSplitStream(1).getFlowRate("m3/hr");

    // Guard against non-finite state coming from a failed compressor run or an
    // inactive surge curve. Without this the proportional update below can
    // propagate NaN into the splitter setpoint and deadlock the recycle loop.
    if (!Double.isFinite(inletFlow) || !Double.isFinite(surgeFlow)
        || !Double.isFinite(currentRecycle) || surgeFlow <= 0.0) {
      logger.warn("Anti-surge calc skipped: non-finite input (inlet=" + inletFlow + " m3/hr"
          + ", surge=" + surgeFlow + " m3/hr, current=" + currentRecycle + " m3/hr)");
      setCalculationIdentifier(id);
      return;
    }

    // If we are comfortably above the surge line, close the recycle valve to
    // (effectively) zero. This short-circuit avoids the proportional step
    // overshooting when the compressor is operating far from surge.
    if (inletFlow > 1.2 * surgeFlow) {
      double minRecycle = Math.max(inletFlow / 1.0e6, 1.0e-6);
      applyAntiSurgeRecycle(antiSurgeSplitter, minRecycle, id);
      return;
    }

    // Proportional anti-surge step with a per-iteration bound. The step is
    // proportional to the surge-inlet gap (NOT to (gap - currentRecycle)) so
    // the fixed point is inletFlow == surgeFlow regardless of the recycle
    // path topology. This matches the legacy formula exactly while adding a
    // 25%-of-max-flow per-iteration cap to prevent single-step overshoot.
    double rawStep = 0.5 * (surgeFlow - inletFlow);
    double maxStep = 0.25 * Math.max(currentRecycle, Math.max(inletFlow, surgeFlow));
    double cappedStep = Math.max(-maxStep, Math.min(maxStep, rawStep));
    double flowAntiSurge = Math.max(currentRecycle + cappedStep, inletFlow / 1.0e6);

    applyAntiSurgeRecycle(antiSurgeSplitter, flowAntiSurge, id);

    // Diagnostic: if (inletFlow + flowAntiSurge) is still well below surge
    // after this update, the loop is likely stuck (e.g. recycle path is not
    // wired back into the compressor inlet, or outer iteration cap is too
    // low). Surfacing this is much friendlier than a silent in-surge result.
    double projected = inletFlow + flowAntiSurge;
    if (projected < ANTI_SURGE_STUCK_THRESHOLD * surgeFlow) {
      logger.warn("Anti-surge: compressor still below surge after recycle update "
          + "(projected total=" + projected + " m3/hr, surge=" + surgeFlow
          + " m3/hr). Check that the recycle stream feeds back into the compressor "
          + "inlet and that the outer recycle iteration cap is sufficient.");
    }
  }

  /**
   * Commands an absolute recycle setpoint on the anti-surge splitter.
   *
   * <p>
   * The recycle leg ({@code splitStream(1)}) is driven DIRECTLY to the requested flow instead of
   * going through {@link Splitter#run(UUID)} / {@code calcSplitFactors()}. The latter caps the
   * recycle leg at the present splitter-inlet snapshot, which is fatal for an anti-surge recycle:
   * when the net forward flow is small the recycle must be allowed to momentarily exceed the
   * current inlet so the feedback loop can ramp the compressor up to the surge line within a few
   * iterations (growth per pass &asymp; 0.5&middot;(surge-inlet)) instead of growing by only the
   * net feed each pass (which stalls the compressor deep in surge).
   * </p>
   *
   * <p>
   * The forward/remainder leg ({@code splitStream(0)}) is then set to {@code max(0, inlet-recycle)}
   * so the splitter snapshot is mass-consistent (forward + recycle == inlet) whenever the demand is
   * physically realizable (recycle &le; inlet, which always holds at convergence). The forward leg
   * never feeds the recycle loop, so recomputing it does not affect convergence.
   * </p>
   *
   * @param antiSurgeSplitter the splitter whose recycle leg is the anti-surge bypass
   * @param recycleFlow the absolute recycle setpoint in m3/hr (must be finite and non-negative)
   * @param id the calculation identifier propagated to the updated streams
   */
  private void applyAntiSurgeRecycle(Splitter antiSurgeSplitter, double recycleFlow, UUID id) {
    // Keep the configured flow-rate spec in sync so a later FULL splitter run in
    // the recycle loop commands the same absolute recycle setpoint.
    antiSurgeSplitter.setFlowRates(new double[] {-1, recycleFlow}, "m3/hr");

    // Drive the recycle leg directly to the absolute setpoint (bypasses the
    // calcSplitFactors() cap so the loop can ramp to the surge line quickly).
    StreamInterface recycleLeg = antiSurgeSplitter.getSplitStream(1);
    recycleLeg.setFlowRate(recycleFlow, "m3/hr");
    recycleLeg.run();

    // Recompute the forward/remainder leg so the splitter snapshot closes mass
    // whenever the demand is realizable (recycle <= inlet). When the controller
    // is transiently demanding more recycle than the present throughput (e.g. a
    // standalone splitter, or early ramp), the forward leg clamps to zero; the
    // recycle loop then grows the inlet to absorb the demand.
    StreamInterface forwardLeg = antiSurgeSplitter.getSplitStream(0);
    double inletMass = antiSurgeSplitter.getInletStream().getFlowRate("kg/hr");
    double recycleMass = recycleLeg.getFlowRate("kg/hr");
    double forwardMass = Math.max(0.0, inletMass - recycleMass);
    forwardLeg.setFlowRate(forwardMass, "kg/hr");
    forwardLeg.run();

    antiSurgeSplitter.setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (simpleCalculationMethod != null) {
      simpleCalculationMethod.run();
      setCalculationIdentifier(id);
      return;
    }

    if (calculationMethod != null) {
      try {
        calculationMethod.accept(inputVariable, outputVariable);
      } catch (Exception ex) {
        logger.error("Error in custom calculation", ex);
      }
      setCalculationIdentifier(id);
      return;
    }
    double sum = 0.0;
    if (name.startsWith("anti surge calculator")) {
      runAntiSurgeCalc(id);
      return;
    }

    if (name.startsWith("MEG makeup calculator")) {
      for (int i = 0; i < inputVariable.size(); i++) {
        sum += inputVariable.get(i).getFluid().getPhase(0).getComponent("MEG").getFlowRate("kg/hr");
      }
    } else {
      for (int i = 0; i < inputVariable.size(); i++) {
        sum += inputVariable.get(i).getFluid().getComponent("TEG").getFlowRate("kg/hr");
      }
    }

    // System.out.println("make up TEG " + sum);
    // ((Stream) outputVariable).setFlowRate(sum, "kg/hr");
    try {
      if (sum < 0.0) {
        sum = 0.0;
      }
      ((neqsim.process.equipment.stream.Stream) outputVariable).setFlowRate(sum, "kg/hr");
      outputVariable.run();
      outputVariable.setCalculationIdentifier(id);
    } catch (Exception ex) {
      logger.info("flow rate error " + sum);
      logger.error("error in calculator", ex);
    }
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * Setter for the field <code>outputVariable</code>.
   * </p>
   *
   * @param outputVariable a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setOutputVariable(ProcessEquipmentInterface outputVariable) {
    this.outputVariable = outputVariable;
  }

  /**
   * <p>
   * Setter for the field <code>calculationMethod</code>.
   * </p>
   *
   * @param calculationMethod a {@link java.util.function.BiConsumer} object
   */
  public void setCalculationMethod(
      BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> calculationMethod) {
    this.calculationMethod = calculationMethod;
  }

  /**
   * <p>
   * Setter for the field <code>calculationMethod</code>.
   * </p>
   *
   * @param calculationMethod a {@link java.lang.Runnable} object
   */
  public void setCalculationMethod(Runnable calculationMethod) {
    this.simpleCalculationMethod = calculationMethod;
  }
}
