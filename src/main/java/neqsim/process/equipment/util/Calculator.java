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

/**
 * Calculator class.
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
   * Anti-surge: absolute upper bound on the recycle flow expressed as a multiple of the compressor surge flow. Holding
   * a compressor on its surge line never requires recycling more than the surge flow itself (recycle = surgeFlow -
   * freshFeed, and freshFeed &ge; 0), so this generous multiple caps the recycle setpoint. It breaks the pathological
   * runaway where the per-iteration percentage step compounds geometrically and inflates the recycle to physically
   * impossible values, which otherwise prevents the outer recycle loop from ever converging.
   */
  private static final double ANTI_SURGE_MAX_RECYCLE_FACTOR = 2.0;

  /**
   * Constructor for Calculator.
   *
   * @param name a {@link java.lang.String} object
   */
  public Calculator(String name) {
    super(name);
  }

  /**
   * addInputVariable.
   *
   * @param unit a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addInputVariable(ProcessEquipmentInterface unit) {
    inputVariable.add(unit);
  }

  /**
   * addInputVariable.
   *
   * @param units a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void addInputVariable(ProcessEquipmentInterface... units) {
    for (ProcessEquipmentInterface unit : units) {
      inputVariable.add(unit);
    }
  }

  /**
   * Getter for the field <code>inputVariable</code>.
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<ProcessEquipmentInterface> getInputVariable() {
    return inputVariable;
  }

  /**
   * Getter for the field <code>outputVariable</code>.
   *
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ProcessEquipmentInterface getOutputVariable() {
    return outputVariable;
  }

  /**
   * runAntiSurgeCalc.
   *
   * @param id a {@link java.util.UUID} object
   */
  public void runAntiSurgeCalc(UUID id) {
    Compressor compressor = (Compressor) inputVariable.get(0);

    Splitter antiSurgeSplitter = (Splitter) outputVariable;

    double inletFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double surgeFlow = compressor.getSurgeFlowRate();
    double currentRecycle = antiSurgeSplitter.getSplitStream(1).getFlowRate("m3/hr");

    // Guard against a surge flow extrapolated far beyond the surge curve data.
    // The surge curve interpolates flow from the operating head; when the head
    // falls outside the curve's fitted range (e.g. a surge curve assigned to a
    // duty it was not measured for) the extrapolation can return a physically
    // impossible surge flow orders of magnitude above the curve, which makes the
    // proportional step below chase an unreachable target and the recycle grow
    // without bound. Clamp the surge flow to the maximum flow that defines the
    // surge curve so the anti-surge control stays inside the measured envelope.
    try {
      double[] curveFlow = antiSurgeSplitter == null ? null
          : compressor.getCompressorChart().getSurgeCurve().getSortedFlow();
      if (curveFlow != null && curveFlow.length > 0) {
        double maxCurveFlow = curveFlow[0];
        for (int i = 1; i < curveFlow.length; i++) {
          if (curveFlow[i] > maxCurveFlow) {
            maxCurveFlow = curveFlow[i];
          }
        }
        if (Double.isFinite(maxCurveFlow) && maxCurveFlow > 0.0 && surgeFlow > maxCurveFlow) {
          surgeFlow = maxCurveFlow;
        }
      }
    } catch (Exception ex) {
      logger.debug("Anti-surge: could not read surge curve flow range for clamping", ex);
    }

    // Guard against non-finite state coming from a failed compressor run or an
    // inactive surge curve. Without this the proportional update below can
    // propagate NaN into the splitter setpoint and deadlock the recycle loop.
    if (!Double.isFinite(inletFlow) || !Double.isFinite(surgeFlow) || !Double.isFinite(currentRecycle)
        || surgeFlow <= 0.0) {
      logger.warn("Anti-surge calc skipped: non-finite input (inlet=" + inletFlow + " m3/hr" + ", surge=" + surgeFlow
          + " m3/hr, current=" + currentRecycle + " m3/hr)");
      setCalculationIdentifier(id);
      return;
    }

    // If we are comfortably above the surge line, close the recycle valve to
    // (effectively) zero. This short-circuit avoids the proportional step
    // overshooting when the compressor is operating far from surge.
    if (inletFlow > 1.2 * surgeFlow) {
      double minRecycle = Math.max(inletFlow / 1.0e6, 1.0e-6);
      antiSurgeSplitter.setFlowRates(new double[] { -1, minRecycle }, "m3/hr");
      antiSurgeSplitter.getSplitStream(1).setFlowRate(minRecycle, "m3/hr");
      antiSurgeSplitter.getSplitStream(1).run();
      antiSurgeSplitter.setCalculationIdentifier(id);
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

    // Absolute upper bound on the recycle setpoint. The recycle required to hold
    // the compressor on the surge line can never exceed the surge flow itself, so
    // capping at a generous multiple of the surge flow leaves normal operation
    // untouched while breaking the geometric runaway that otherwise inflates the
    // recycle without bound (e.g. an injection-compressor recycle growing to tens
    // of millions of m3/hr) and stops the outer recycle loop from converging.
    double maxRecycle = ANTI_SURGE_MAX_RECYCLE_FACTOR * surgeFlow;
    if (flowAntiSurge > maxRecycle) {
      flowAntiSurge = maxRecycle;
    }

    antiSurgeSplitter.setFlowRates(new double[] { -1, flowAntiSurge }, "m3/hr");
    antiSurgeSplitter.getSplitStream(1).setFlowRate(flowAntiSurge, "m3/hr");
    antiSurgeSplitter.getSplitStream(1).run();
    antiSurgeSplitter.setCalculationIdentifier(id);

    // Diagnostic: if (inletFlow + flowAntiSurge) is still well below surge
    // after this update, the loop is likely stuck (e.g. recycle path is not
    // wired back into the compressor inlet, or outer iteration cap is too
    // low). Surfacing this is much friendlier than a silent in-surge result.
    double projected = inletFlow + flowAntiSurge;
    if (projected < ANTI_SURGE_STUCK_THRESHOLD * surgeFlow) {
      logger.warn("Anti-surge: compressor still below surge after recycle update " + "(projected total=" + projected
          + " m3/hr, surge=" + surgeFlow + " m3/hr). Check that the recycle stream feeds back into the compressor "
          + "inlet and that the outer recycle iteration cap is sufficient.");
    }
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
   * Setter for the field <code>outputVariable</code>.
   *
   * @param outputVariable a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void setOutputVariable(ProcessEquipmentInterface outputVariable) {
    this.outputVariable = outputVariable;
  }

  /**
   * Setter for the field <code>calculationMethod</code>.
   *
   * @param calculationMethod a {@link java.util.function.BiConsumer} object
   */
  public void setCalculationMethod(
      BiConsumer<ArrayList<ProcessEquipmentInterface>, ProcessEquipmentInterface> calculationMethod) {
    this.calculationMethod = calculationMethod;
  }

  /**
   * Setter for the field <code>calculationMethod</code>.
   *
   * @param calculationMethod a {@link java.lang.Runnable} object
   */
  public void setCalculationMethod(Runnable calculationMethod) {
    this.simpleCalculationMethod = calculationMethod;
  }
}
