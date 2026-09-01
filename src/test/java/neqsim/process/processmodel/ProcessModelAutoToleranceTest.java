package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the automatic convergence-accuracy selection: when the caller never asks for a specific tolerance, the
 * model uses an engineering-grade default instead of the historical 1e-4, and accepts a residual that has stopped
 * improving while already being accurate enough for process work.
 *
 * @author NeqSim
 * @version 1.0
 */
class ProcessModelAutoToleranceTest {

  /**
   * Creates a small two-component gas fluid.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a minimal two-area model that solves in a single pass.
   *
   * @return a runnable two-area ProcessModel
   */
  private static ProcessModel buildSimpleModel() {
    Stream feed = new Stream("feed", createGasFluid());
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    Separator separator = new Separator("separator", feed);

    ProcessSystem inlet = new ProcessSystem("inlet");
    inlet.add(feed);
    inlet.add(separator);

    Stream gas = new Stream("gas", separator.getGasOutStream());
    Heater heater = new Heater("heater", gas);
    heater.setOutTemperature(40.0, "C");

    ProcessSystem downstream = new ProcessSystem("downstream");
    downstream.add(gas);
    downstream.add(heater);

    ProcessModel model = new ProcessModel();
    model.add("inlet", inlet);
    model.add("downstream", downstream);
    return model;
  }

  /** A plain run() with no tolerance given must use the engineering-grade default. */
  @Test
  void plainRunUsesEngineeringDefaultTolerance() {
    ProcessModel model = buildSimpleModel();
    assertFalse(model.isToleranceExplicit(), "a fresh model has no explicit tolerance");
    assertTrue(model.isAutoTolerance(), "automatic tolerance selection is on by default");

    model.run();

    assertEquals(ProcessModel.DEFAULT_ENGINEERING_TOLERANCE, model.getFlowTolerance(), 1e-12);
    assertEquals(ProcessModel.DEFAULT_ENGINEERING_TOLERANCE, model.getTemperatureTolerance(), 1e-12);
    assertEquals(ProcessModel.DEFAULT_ENGINEERING_TOLERANCE, model.getPressureTolerance(), 1e-12);
    assertTrue(model.getAutoToleranceSummary().length() > 0, "the chosen accuracy must be reported");
    assertTrue(model.getConvergenceSummary().contains("Auto-accuracy"),
        "the convergence summary must state the accuracy that was used");
  }

  /** An explicitly requested tolerance must survive a run untouched. */
  @Test
  void explicitToleranceIsNeverOverridden() {
    ProcessModel model = buildSimpleModel();
    model.setTolerance(1.0e-6);
    assertTrue(model.isToleranceExplicit());

    model.run();

    assertEquals(1.0e-6, model.getFlowTolerance(), 1e-15);
    assertEquals(1.0e-6, model.getTemperatureTolerance(), 1e-15);
    assertEquals(1.0e-6, model.getPressureTolerance(), 1e-15);
    assertEquals("", model.getAutoToleranceSummary(), "no accuracy is auto-selected when one was given");
  }

  /** Each per-variable setter must also count as an explicit request. */
  @Test
  void perVariableSettersMarkToleranceExplicit() {
    ProcessModel flowModel = new ProcessModel();
    flowModel.setFlowTolerance(1.0e-5);
    assertTrue(flowModel.isToleranceExplicit());

    ProcessModel temperatureModel = new ProcessModel();
    temperatureModel.setTemperatureTolerance(1.0e-5);
    assertTrue(temperatureModel.isToleranceExplicit());

    ProcessModel pressureModel = new ProcessModel();
    pressureModel.setPressureTolerance(1.0e-5);
    assertTrue(pressureModel.isToleranceExplicit());
  }

  /** Disabling the feature must restore the historical 1e-4 default. */
  @Test
  void disablingAutoToleranceKeepsTheHistoricalDefault() {
    ProcessModel model = buildSimpleModel();
    model.setAutoTolerance(false);

    model.run();

    assertEquals(1.0e-4, model.getFlowTolerance(), 1e-15);
    assertEquals("", model.getAutoToleranceSummary());
  }

  /** runUntilConverged(maxIterations, tolerance) is an explicit request. */
  @Test
  void runUntilConvergedWithToleranceIsExplicit() {
    ProcessModel model = buildSimpleModel();
    model.runUntilConverged(5, 1.0e-5);

    assertTrue(model.isToleranceExplicit());
    assertEquals(1.0e-5, model.getFlowTolerance(), 1e-15);
  }

  /** The convergence report must expose the accuracy actually used. */
  @Test
  void convergenceReportExposesAutoTolerance() {
    ProcessModel model = buildSimpleModel();
    model.run();

    JsonObject root = JsonParser.parseString(model.getConvergenceReportJson()).getAsJsonObject();
    assertTrue(root.has("autoTolerance"), "report must contain the autoTolerance block");
    JsonObject autoTolerance = root.getAsJsonObject("autoTolerance");
    assertTrue(autoTolerance.get("enabled").getAsBoolean());
    assertFalse(autoTolerance.get("toleranceExplicit").getAsBoolean());
    assertEquals(ProcessModel.DEFAULT_ENGINEERING_TOLERANCE, autoTolerance.get("appliedTolerance").getAsDouble(),
        1e-12);
    assertEquals(ProcessModel.DEFAULT_AUTO_TOLERANCE_CEILING, autoTolerance.get("ceiling").getAsDouble(), 1e-12);
  }

  /** The ceiling is a configurable, validated engineering limit. */
  @Test
  void autoToleranceCeilingIsValidated() {
    ProcessModel model = new ProcessModel();
    assertEquals(ProcessModel.DEFAULT_AUTO_TOLERANCE_CEILING, model.getAutoToleranceCeiling(), 1e-12);

    model.setAutoToleranceCeiling(5.0e-3);
    assertEquals(5.0e-3, model.getAutoToleranceCeiling(), 1e-15);

    assertThrows(IllegalArgumentException.class, () -> model.setAutoToleranceCeiling(0.0));
    assertThrows(IllegalArgumentException.class, () -> model.setAutoToleranceCeiling(-1.0e-3));
    assertThrows(IllegalArgumentException.class, () -> model.setAutoToleranceCeiling(Double.NaN));
  }
}
