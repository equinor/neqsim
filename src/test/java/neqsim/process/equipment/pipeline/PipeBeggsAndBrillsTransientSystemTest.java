package neqsim.process.equipment.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Integration-style tests exercising the dynamic Beggs & Brill pipeline together with a
 * throttling valve and separator across a variety of fluid conditions. Each test verifies that a
 * valve opening change results in a delayed separator response, illustrating the finite
 * propagation speed introduced by the transient pipeline model.
 */
public class PipeBeggsAndBrillsTransientSystemTest {

  private static final double PIPELINE_LENGTH_METERS = 100.0;
  private static final double PIPELINE_DIAMETER_METERS = 0.3;
  private static final double PIPELINE_ROUGHNESS_METERS = 5e-6;
  private static final double PIPELINE_ANGLE_DEGREES = 0.0;
  private static final int PIPELINE_SEGMENTS = 10;

  private static final double INITIAL_VALVE_OPENING = 40.0;
  private static final double FINAL_VALVE_OPENING = 80.0;
  private static final double VALVE_OUTLET_PRESSURE = 65.0;
  private static final double TIME_STEP_SECONDS = 20.0;
  private static final int MAX_TRANSIENT_STEPS = 400;
  private static final int TARGET_CONVERGENCE_STEPS = 2000;
  private static final double TARGET_RELATIVE_TOLERANCE = 1.0e-3;
  private static final double TARGET_ABSOLUTE_TOLERANCE = 1.0e-4;

  @Test
  public void transientPipelineDelaysSinglePhaseGasResponse() {
    SystemInterface fluid = createSinglePhaseGasFluid();
    Assertions.assertTrue(fluid.hasPhaseType("gas"));
    Assertions.assertFalse(fluid.hasPhaseType("oil"));
    Assertions.assertFalse(fluid.hasPhaseType("aqueous"));

    assertDelayedSeparatorResponse("single-phase gas", this::createSinglePhaseGasFluid,
        List.of(gasFlowProbe()));
  }

  @Test
  public void transientPipelineDelaysSinglePhaseOilResponse() {
    SystemInterface fluid = createSinglePhaseOilFluid();
    Assertions.assertFalse(fluid.hasPhaseType("gas"));
    Assertions.assertTrue(fluid.hasPhaseType("oil"));
    Assertions.assertFalse(fluid.hasPhaseType("aqueous"));

    assertDelayedSeparatorResponse("single-phase oil", this::createSinglePhaseOilFluid,
        List.of(liquidFlowProbe()));
  }

  @Test
  public void transientPipelineDelaysTwoPhaseGasOilResponse() {
    SystemInterface fluid = createTwoPhaseGasOilFluid();
    Assertions.assertTrue(fluid.hasPhaseType("gas"));
    Assertions.assertTrue(fluid.hasPhaseType("oil"));
    Assertions.assertFalse(fluid.hasPhaseType("aqueous"));

    assertDelayedSeparatorResponse("two-phase gas-oil", this::createTwoPhaseGasOilFluid,
        List.of(gasFlowProbe(), liquidFlowProbe()));
  }

  @Test
  public void transientPipelineDelaysThreePhaseGasOilWaterResponse() {
    SystemInterface fluid = createThreePhaseGasOilWaterFluid();
    Assertions.assertTrue(fluid.hasPhaseType("gas"));
    Assertions.assertTrue(fluid.hasPhaseType("oil"));
    Assertions.assertTrue(fluid.hasPhaseType("aqueous"));

    assertDelayedSeparatorResponse("three-phase gas-oil-aqueous",
        this::createThreePhaseGasOilWaterFluid, List.of(gasFlowProbe(), liquidFlowProbe()));
  }

  private void assertDelayedSeparatorResponse(String scenarioDescription,
      Supplier<SystemInterface> fluidSupplier, List<FlowProbe> probes) {
    ProcessComponents dynamic = buildProcess(fluidSupplier.get(), INITIAL_VALVE_OPENING);
    dynamic.process.run();

    ProcessComponents steadyTarget = buildProcess(fluidSupplier.get(), FINAL_VALVE_OPENING);
    steadyTarget.process.run();
    steadyTarget.valve.setCalculateSteadyState(false);
    steadyTarget.pipeline.setCalculateSteadyState(false);
    steadyTarget.process.setTimeStep(TIME_STEP_SECONDS);
    steadyTarget.process.runTransient();

    Map<FlowProbe, Double> targetFlows = readFlows(steadyTarget.separator, probes);
    Map<FlowProbe, Double> previousTargetFlows = targetFlows;

    for (int step = 0; step < TARGET_CONVERGENCE_STEPS; step++) {
      steadyTarget.process.runTransient();
      Map<FlowProbe, Double> currentFlows = readFlows(steadyTarget.separator, probes);
      boolean stable = true;
      for (FlowProbe probe : probes) {
        double previous = previousTargetFlows.get(probe);
        double current = currentFlows.get(probe);
        double tolerance = Math.max(TARGET_ABSOLUTE_TOLERANCE,
            TARGET_RELATIVE_TOLERANCE * Math.max(Math.abs(current), Math.abs(previous)));
        if (Math.abs(current - previous) > tolerance) {
          stable = false;
          break;
        }
      }
      previousTargetFlows = currentFlows;
      targetFlows = currentFlows;
      if (stable) {
        break;
      }
    }

    dynamic.valve.setCalculateSteadyState(false);
    dynamic.pipeline.setCalculateSteadyState(false);

    dynamic.process.setTimeStep(TIME_STEP_SECONDS);
    dynamic.process.runTransient();

    Map<FlowProbe, Double> baselineFlows = readFlows(dynamic.separator, probes);
    dynamic.valve.setPercentValveOpening(FINAL_VALVE_OPENING);
    dynamic.process.runTransient();

    Map<FlowProbe, Double> firstStepFlows = readFlows(dynamic.separator, probes);

    Map<FlowProbe, Double> tolerances = new LinkedHashMap<>();
    Map<FlowProbe, Double> firstStepDeltas = new LinkedHashMap<>();

    for (FlowProbe probe : probes) {
      double baseline = baselineFlows.get(probe);
      double target = targetFlows.get(probe);
      double totalChange = Math.abs(target - baseline);
      Assertions.assertTrue(totalChange > 1e-6,
          scenarioDescription + " " + probe.description() + " change should be non-zero");

      double firstStepChange = Math.abs(firstStepFlows.get(probe) - baseline);
      Assertions.assertTrue(firstStepChange < 0.6 * totalChange,
          scenarioDescription + " " + probe.description()
              + " should respond more slowly than the final steady change");

      double tolerance = Math.max(0.2 * totalChange, 0.02);
      tolerances.put(probe, tolerance);
      firstStepDeltas.put(probe, firstStepChange);
    }

    Map<FlowProbe, Double> latestFlows = new LinkedHashMap<>(firstStepFlows);
    int additionalSteps = 0;

    while (additionalSteps < MAX_TRANSIENT_STEPS) {
      boolean withinTolerance = true;
      for (FlowProbe probe : probes) {
        double target = targetFlows.get(probe);
        double current = latestFlows.get(probe);
        double tolerance = tolerances.get(probe);
        if (Math.abs(current - target) > tolerance) {
          withinTolerance = false;
          break;
        }
      }
      if (withinTolerance) {
        break;
      }
      dynamic.process.runTransient();
      latestFlows = readFlows(dynamic.separator, probes);
      additionalSteps++;
    }

    Assertions.assertTrue(additionalSteps > 0,
        scenarioDescription + " should require multiple transient steps");

    for (FlowProbe probe : probes) {
      double baseline = baselineFlows.get(probe);
      double target = targetFlows.get(probe);
      double firstStepChange = firstStepDeltas.get(probe);
      double finalFlow = latestFlows.get(probe);

      Assertions.assertTrue(Math.abs(finalFlow - baseline) > firstStepChange,
          scenarioDescription + " " + probe.description()
              + " should continue moving away from the original steady flow");
      double totalChange = Math.abs(target - baseline);
      double finalDelta = Math.abs(finalFlow - baseline);
      Assertions.assertTrue(finalDelta >= Math.max(0.02 * totalChange, 0.01),
          scenarioDescription + " " + probe.description()
              + " should reflect a significant change after multiple steps");
      double initialTargetGap = Math.abs(baseline - target);
      double finalTargetGap = Math.abs(finalFlow - target);
      Assertions.assertTrue(finalTargetGap < initialTargetGap,
          scenarioDescription + " " + probe.description()
              + " should move closer to the final steady value over time");
      Assertions.assertTrue(Math.abs(firstStepFlows.get(probe) - target) > finalTargetGap,
          scenarioDescription + " " + probe.description()
              + " should be closer to the final value after the additional steps than after the"
              + " first step");
    }
  }

  private ProcessComponents buildProcess(SystemInterface baseFluid, double valveOpening) {
    SystemInterface workingFluid = baseFluid.clone();

    Stream feed = new Stream("feed", workingFluid);
    feed.setFlowRate(workingFluid.getFlowRate("kg/hr"), "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(VALVE_OUTLET_PRESSURE);
    valve.setPercentValveOpening(valveOpening);
    valve.setKv(35.0);

    PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipeline", valve.getOutletStream());
    pipeline.setPipeWallRoughness(PIPELINE_ROUGHNESS_METERS);
    pipeline.setLength(PIPELINE_LENGTH_METERS);
    pipeline.setAngle(PIPELINE_ANGLE_DEGREES);
    pipeline.setDiameter(PIPELINE_DIAMETER_METERS);
    pipeline.setNumberOfIncrements(PIPELINE_SEGMENTS);

    Separator separator = new Separator("separator");
    separator.addStream(pipeline.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(pipeline);
    process.add(separator);

    return new ProcessComponents(process, valve, pipeline, separator);
  }

  private Map<FlowProbe, Double> readFlows(Separator separator, List<FlowProbe> probes) {
    Map<FlowProbe, Double> values = new LinkedHashMap<>();
    for (FlowProbe probe : probes) {
      values.put(probe, probe.read(separator));
    }
    return values;
  }

  private SystemInterface createSinglePhaseGasFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 35.0, 80.0);
    fluid.createDatabase(true);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.setPressure(80.0, "bara");
    fluid.setTemperature(35.0, "C");
    fluid.setTotalFlowRate(36000.0, "kg/hr");
    ThermodynamicOperations flash = new ThermodynamicOperations(fluid);
    flash.TPflash();
    fluid.initPhysicalProperties();
    return fluid;
  }

  private SystemInterface createSinglePhaseOilFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 150.0);
    fluid.createDatabase(true);
    fluid.addComponent("nC10", 0.8);
    fluid.addComponent("nC7", 0.2);
    fluid.setMixingRule(2);
    fluid.setPressure(150.0, "bara");
    fluid.setTemperature(25.0, "C");
    fluid.setTotalFlowRate(45000.0, "kg/hr");
    ThermodynamicOperations flash = new ThermodynamicOperations(fluid);
    flash.TPflash();
    fluid.initPhysicalProperties();
    return fluid;
  }

  private SystemInterface createTwoPhaseGasOilFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 75.0);
    fluid.createDatabase(true);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("nC10", 0.35);
    fluid.setMixingRule(2);
    fluid.setPressure(75.0, "bara");
    fluid.setTemperature(30.0, "C");
    fluid.setTotalFlowRate(36000.0, "kg/hr");
    ThermodynamicOperations flash = new ThermodynamicOperations(fluid);
    flash.TPflash();
    fluid.initPhysicalProperties();
    return fluid;
  }

  private SystemInterface createThreePhaseGasOilWaterFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 80.0);
    fluid.createDatabase(true);
    fluid.addComponent("methane", 100.0);
    fluid.addComponent("n-heptane", 80.0);
    fluid.addComponent("water", 60.0);
    fluid.setMixingRule("classic");
    fluid.setPressure(80.0, "bara");
    fluid.setTemperature(25.0, "C");
    fluid.setTotalFlowRate(36000.0, "kg/hr");
    fluid.setMultiPhaseCheck(true);
    ThermodynamicOperations flash = new ThermodynamicOperations(fluid);
    flash.TPflash();
    fluid.initPhysicalProperties();
    return fluid;
  }

  private static FlowProbe gasFlowProbe() {
    return new FlowProbe("gas outlet",
        separator -> separator.getGasOutStream().getFlowRate("kg/sec"));
  }

  private static FlowProbe liquidFlowProbe() {
    return new FlowProbe("liquid outlet",
        separator -> separator.getLiquidOutStream().getFlowRate("kg/sec"));
  }

  private static final class FlowProbe {
    private final String description;
    private final Function<Separator, Double> extractor;

    private FlowProbe(String description, Function<Separator, Double> extractor) {
      this.description = description;
      this.extractor = extractor;
    }

    double read(Separator separator) {
      return extractor.apply(separator);
    }

    String description() {
      return description;
    }
  }

  private static final class ProcessComponents {
    private final ProcessSystem process;
    private final ThrottlingValve valve;
    private final PipeBeggsAndBrills pipeline;
    private final Separator separator;

    private ProcessComponents(ProcessSystem process, ThrottlingValve valve,
        PipeBeggsAndBrills pipeline, Separator separator) {
      this.process = process;
      this.valve = valve;
      this.pipeline = pipeline;
      this.separator = separator;
    }
  }
}

