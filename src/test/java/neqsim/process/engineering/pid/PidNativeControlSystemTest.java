package neqsim.process.engineering.pid;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.controllerdevice.AntiSurgeController;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripCompressorAction;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.measurementdevice.CompressorSurgeMarginMeasurement;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies source identity, canonical configuration and actual field-input causality in native control handoff.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PidNativeControlSystemTest {
  /** Checks complete unchanged JSON handoff, including the default unlimited controller output rate. */
  @Test
  void unchangedCanonicalJsonRoundtripConfiguresOnlyActualCopy() {
    Fixture fixture = new Fixture();
    PidDesignModel restored = PidDesignModel.fromJson(new Gson().toJson(fixture.pid.toMap()));
    PidSimulationBindings bindings = PidNativeControlSystem.bind(fixture.project, restored);
    ProcessSystem copy = fixture.process.copy();
    PidNativeControlSystem.ConfiguredControlSystem receipt = PidNativeControlSystem
        .applyCanonicalConfiguration(fixture.project, restored, bindings, copy);
    receipt
        .assertSameConfiguration(PidNativeControlSystem.validateConfigured(fixture.project, restored, bindings, copy));
    assertTrue(receipt.toMap().toString().contains("UNBOUNDED"));
    assertEquals(0.2, fixture.controller.getSurgeMarginSetPoint(), 0.0);
    assertEquals(4, restored.getElements().size());
  }

  /** Checks real pressure-control output uses RPM bounds and exact compressor/controller wiring. */
  @Test
  void nativePressureControllerGovernsActualCompressorSpeedPolicy() {
    Fixture fixture = new Fixture();
    PressureTransmitter pressure = new PressureTransmitter("30-PT-101", fixture.compressor.getOutletStream());
    pressure.setTransientSamplingEnabled(true);
    ControllerDeviceBaseClass pic = new ControllerDeviceBaseClass("30-PIC-101");
    pic.setTransmitter(pressure);
    pic.setUnit("bara");
    pic.setControllerSetPoint(50.0);
    pic.setOutputLimits(7000.0, 11000.0);
    fixture.compressor.setMinimumSpeed(7000.0);
    fixture.compressor.setMaximumSpeed(11000.0);
    fixture.compressor.setControllerSpeedRateLimitEnabled(true);
    fixture.compressor.setController(pic);
    fixture.process.add(pressure);
    fixture.process.add(pic);
    PidDesignModel model = PidNativeControlSystem.fromProcess(fixture.project);
    PidSimulationBindings bindings = PidNativeControlSystem.bind(fixture.project, model);
    assertEquals("COMPRESSOR_SPEED", tag(model, "30-K-100-SPEED-DEMAND").getAttributes().get("nativeActuatorRole"));
    assertTrue(tag(model, "30-PT-101").getConnectedElementIds().contains(tag(model, "30-PIC-101").getId()));
    ProcessSystem configured = fixture.process.copy();
    PidNativeControlSystem.ConfiguredControlSystem before = PidNativeControlSystem
        .applyCanonicalConfiguration(fixture.project, model, bindings, configured);
    assertTrue(before.toMap().toString().contains("maximumSpeedRpm=11000.0"));
    ((Compressor) configured.getUnit("30-K-100")).setMaximumSpeed(10000.0);
    assertThrows(IllegalArgumentException.class,
        () -> PidNativeControlSystem.validateConfigured(fixture.project, model, bindings, configured));
    assertEquals(11000.0, fixture.compressor.getMaximumSpeed(), 0.0);
  }

  /** Checks actual PID defaults survive the same explicit unbounded-limit JSON policy. */
  @Test
  @SuppressWarnings("unchecked")
  void nativePidDefaultConfigurationRoundtripsWithoutDroppedLimits() {
    ControllerDeviceBaseClass source = new ControllerDeviceBaseClass("LIC");
    Map<String, Object> values = new Gson().fromJson(new Gson().toJson(source.getControlConfiguration()), Map.class);
    source.applyControlConfiguration(values);
    assertEquals(new Gson().toJsonTree(values), new Gson().toJsonTree(source.getControlConfiguration()));
    Map<String, Object> bad = new LinkedHashMap<String, Object>(values);
    bad.remove("minimumOutput");
    assertThrows(IllegalArgumentException.class, () -> source.applyControlConfiguration(bad));
  }

  /** Checks a governed tuning revision invalidates old bindings and changes native readback/fingerprint. */
  @Test
  void canonicalRevisionRequiresRebindingAndActuallyChangesController() {
    Fixture fixture = new Fixture();
    ProcessSystem before = fixture.process.copy();
    String previous = PidNativeControlSystem
        .applyCanonicalConfiguration(fixture.project, fixture.pid, fixture.bindings, before).getFingerprint();
    Map<String, Object> configuration = values(tag(fixture.pid, "30-FIC-100"));
    configuration.put("surgeMarginSetPoint", Double.valueOf(0.35));
    tag(fixture.pid, "30-FIC-100").attribute("nativeControlConfiguration", configuration);
    assertThrows(IllegalStateException.class, () -> fixture.bindings.assertCurrent(fixture.project, fixture.pid));
    PidSimulationBindings rebound = PidNativeControlSystem.bind(fixture.project, fixture.pid);
    ProcessSystem copy = fixture.process.copy();
    PidNativeControlSystem.ConfiguredControlSystem actual = PidNativeControlSystem
        .applyCanonicalConfiguration(fixture.project, fixture.pid, rebound, copy);
    assertNotEquals(previous, actual.getFingerprint());
    assertEquals(0.35,
        ((AntiSurgeController) ((ESDValve) copy.getUnit("30-ASV-100")).getController()).getSurgeMarginSetPoint(), 0.0);
    assertEquals(0.2, fixture.controller.getSurgeMarginSetPoint(), 0.0);
  }

  /** Checks the protected source can never be passed as a configurable copy. */
  @Test
  void sourceAliasesAndIgnoredActuatorChangesAreRejected() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalArgumentException.class, () -> PidNativeControlSystem
        .applyCanonicalConfiguration(fixture.project, fixture.pid, fixture.bindings, fixture.process));
    ProcessSystem copy = fixture.process.copy();
    ((ESDValve) copy.getUnit("30-ASV-100")).setOpeningTravelTime(8.0);
    assertThrows(IllegalArgumentException.class,
        () -> PidNativeControlSystem.validateConfigured(fixture.project, fixture.pid, fixture.bindings, copy));
  }

  /** Checks extra causal arrows and a remapped actual field endpoint cannot receive configuration credit. */
  @Test
  void falseInputWiringAndWrongActualFieldTapAreRejected() {
    Fixture fixture = new Fixture();
    tag(fixture.pid, "30-FT-100").connect(tag(fixture.pid, "30-FIC-100").getId());
    PidSimulationBindings bindings = PidNativeControlSystem.bind(fixture.project, fixture.pid);
    assertThrows(IllegalArgumentException.class, () -> PidNativeControlSystem.validateConfigured(fixture.project,
        fixture.pid, bindings, fixture.process.copy()));
    Fixture other = new Fixture();
    ProcessSystem copy = other.process.copy();
    ((VolumeFlowTransmitter) copy.getMeasurementDevice("30-FT-100"))
        .setStream(((Compressor) copy.getUnit("30-K-100")).getOutletStream());
    assertThrows(IllegalArgumentException.class,
        () -> PidNativeControlSystem.validateConfigured(other.project, other.pid, other.bindings, copy));
  }

  /** Checks actual inlet FT faults propagate through the held calculated signal into native controller demand. */
  @Test
  void heldFieldFlowBiasChangesActualAntiSurgeCommandAndRestoresSampleState() {
    Fixture fixture = new Fixture();
    UUID normal = UUID.randomUUID();
    fixture.flow.sampleTransient(0.1, normal);
    fixture.margin.sampleTransient(0.1, normal);
    assertEquals(fixture.compressor.getDistanceToSurge(), fixture.margin.getTransientSample(), 1.0e-12);
    fixture.controller.runTransient(0.0, 0.1, normal);
    assertEquals(0.0, fixture.controller.getResponse(), 1.0e-12);
    CompressorSurgeMarginMeasurement.MarginState state = fixture.margin.captureTransientState();
    Map<String, Object> heldReference = fixture.margin.getLastReferenceEvidence();
    assertEquals(normal.toString(), heldReference.get("physicalStepIdentifier"));
    assertEquals(fixture.flow.getTransientSample("m^3/hr"),
        ((Number) heldReference.get("actualInletFlowIndicationM3PerHour")).doubleValue(), 0.0);
    assertTrue(fixture.margin.isLastReferenceWithinMap());
    fixture.compressor.setSpeed(9000.0);
    fixture.compressor.run();
    assertEquals(heldReference, fixture.margin.getLastReferenceEvidence());
    fixture.flow.setFault(SensorFaultType.STUCK_AT_VALUE, fixture.flow.getTransientSample("m^3/hr") * 0.1);
    UUID fault = UUID.randomUUID();
    fixture.flow.sampleTransient(0.1, fault);
    fixture.margin.sampleTransient(0.1, fault);
    fixture.controller.runTransient(0.0, 0.1, fault);
    assertEquals(100.0, fixture.controller.getResponse(), 1.0e-12);
    assertTrue(fixture.margin.getTransientSample() < 0.0);
    fixture.margin.restoreTransientState(state);
    assertTrue(fixture.margin.hasTransientSample(normal));
    assertFalse(fixture.margin.hasTransientSample(fault));
    assertEquals(heldReference, fixture.margin.getLastReferenceEvidence());
    assertEquals(10000.0, ((Number) heldReference.get("nativeSpeedRpm")).doubleValue(), 0.0);
    assertEquals(9000.0, fixture.compressor.getSpeed(), 0.0);
  }

  /** Checks missing current physical samples cannot silently fall back to a true-model or live field reading. */
  @Test
  void missingHeldInputAndUndeclaredInstrumentFaultAreRejected() {
    Fixture fixture = new Fixture();
    assertThrows(IllegalStateException.class, () -> fixture.controller.runTransient(0.0, 0.1, UUID.randomUUID()));
    ProcessSystem copy = fixture.process.copy();
    ((VolumeFlowTransmitter) copy.getMeasurementDevice("30-FT-100")).setFault(SensorFaultType.BIAS, 1.0);
    assertThrows(IllegalArgumentException.class,
        () -> PidNativeControlSystem.validateConfigured(fixture.project, fixture.pid, fixture.bindings, copy));
    Map<String, Object> fault = new LinkedHashMap<String, Object>();
    fault.put("faultType", "BIAS");
    fault.put("faultParameter", Double.valueOf(1.0));
    Map<String, Map<String, Object>> declared = new LinkedHashMap<String, Map<String, Object>>();
    declared.put("30-FT-100", fault);
    assertTrue(PidNativeControlSystem.validateConfigured(fixture.project, fixture.pid, fixture.bindings, copy, declared)
        .toMap().toString().contains("BIAS"));
  }

  /** Checks native driver-stop and fail-open targets require the explicit bounded compressor safety role. */
  @Test
  void importsActualCompressorStopAndFailOpenTargetsWithoutInventingPhysicalHardware() {
    Fixture fixture = new Fixture();
    ProcessSystem configured = fixture.process.copy();
    ESDValve valve = (ESDValve) configured.getUnit("30-ASV-100");
    Compressor compressor = (Compressor) configured.getUnit("30-K-100");
    ESDLogic logic = new ESDLogic("30-ESD-100");
    ParallelActionGroup group = new ParallelActionGroup("Actual driver stop and recycle opening");
    group.addAction(new TripValveAction(valve));
    group.addAction(new TripCompressorAction(compressor));
    logic.addAction(group, 0.0);
    ClosedLoopSafetyFunction function = ClosedLoopSafetyFunction
        .builder("30-SIF-100", "30-SIF-100", configured, VotingPattern.ONE_OUT_OF_ONE, logic)
        .addChannel(SafetyFunctionChannel
            .highTripFromMeasurement(configured.getMeasurementDevice("30-FT-100"), "m^3/hr", 1000.0).build())
        .build();
    assertTrue(Boolean.TRUE.equals(logic.getTripActionConfiguration().get("supportedTripPolicy")));
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionImporter.fromNativeFunction(fixture.project,
        fixture.pid, fixture.bindings, configured, function, "ESD"));
    PidDesignModel imported = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, fixture.pid,
        fixture.bindings, configured, function, "COMPRESSOR_ESD");
    PidSimulationBindings bindings = PidNativeControlSystem.bind(fixture.project, imported);
    PidSafetyFunctionValidator.ConfiguredFunction receipt = PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, configured, function.getName(), function);
    assertTrue(receipt.toMap().toString().contains("NATIVE_COMPRESSOR_STOP_PROFILE"));
    assertEquals(Boolean.FALSE, tag(imported, "30-K-100-DRIVER-TRIP").getAttributes().get("physicalHardwareAdded"));
    Map<String, Object> registers = PidNativeControlSystem.automationRegisters(fixture.project, imported, bindings);
    assertEquals(4, ((List<?>) registers.get("ioList")).size());
    assertFalse(((List<?>) registers.get("causeAndEffect")).isEmpty());
    ClosedLoopSafetyFunction second = ClosedLoopSafetyFunction
        .builder("30-SIF-SECOND", "shared-driver", configured, VotingPattern.ONE_OUT_OF_ONE, logic)
        .addChannel(SafetyFunctionChannel
            .highTripFromMeasurement(configured.getMeasurementDevice("30-FT-100"), "m^3/hr", 1000.0).build())
        .build();
    PidDesignModel sharedDriver = PidSafetyFunctionImporter.fromNativeFunction(fixture.project, imported, bindings,
        configured, second, "COMPRESSOR_ESD");
    assertEquals(imported.getElements().size() + 1, sharedDriver.getElements().size(),
        "multiple native functions must reuse an unchanged driver policy after canonical JSON roundtrip");
    ((TripCompressorAction) group.getActions().get(1)).setCommandAvailable(false);
    valve.setActuatorAvailable(false);
    valve.setStrokeTime(4.0);
    assertThrows(IllegalArgumentException.class, () -> PidSafetyFunctionValidator.validateConfigured(fixture.project,
        imported, bindings, configured, function.getName(), function));
    Map<String, Map<String, Object>> targetFaults = new LinkedHashMap<String, Map<String, Object>>();
    Map<String, Object> valveFault = new LinkedHashMap<String, Object>();
    valveFault.put("actuatorAvailable", Boolean.FALSE);
    valveFault.put("strokeSeconds", Double.valueOf(4.0));
    targetFaults.put(valve.getName(), valveFault);
    Map<String, Object> driverFault = new LinkedHashMap<String, Object>();
    driverFault.put("commandAvailable", Boolean.FALSE);
    targetFaults.put(compressor.getName(), driverFault);
    assertTrue(PidSafetyFunctionValidator
        .validateConfigured(fixture.project, imported, bindings, configured, function.getName(), function,
            Collections.<String, SafetyFunctionChannel.FaultMode>emptyMap(), targetFaults)
        .toMap().toString().contains("declaredTargetFaultOverrides"));
    Map<String, Map<String, Object>> controlFaults = new LinkedHashMap<String, Map<String, Object>>();
    controlFaults.put(valve.getName(), valveFault);
    assertTrue(PidNativeControlSystem.validateConfigured(fixture.project, imported, bindings, configured, controlFaults)
        .toMap().toString().contains("declaredFaultOverrides"));
  }

  /**
   * Finds one actual canonical tag.
   *
   * @param model graph
   * @param name tag
   * @return canonical object
   */
  private static PidElement tag(PidDesignModel model, String name) {
    for (PidElement element : model.getElements()) {
      if (name.equals(element.getTag())) {
        return element;
      }
    }
    throw new IllegalArgumentException(name);
  }

  /**
   * Reads a detached editable static policy.
   *
   * @param element governed canonical object
   * @return detached policy
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> values(PidElement element) {
    return new Gson().fromJson(new Gson().toJson(element.getAttributes().get("nativeControlConfiguration")), Map.class);
  }

  /** Configuration-only source fixture; the separate public train owns physical recycle benchmark coverage. */
  private static final class Fixture {
    private final ProcessSystem process = new ProcessSystem();
    private final Compressor compressor;
    private final VolumeFlowTransmitter flow;
    private final CompressorSurgeMarginMeasurement margin;
    private final AntiSurgeController controller;
    private final EngineeringProject project;
    private final PidDesignModel pid;
    private final PidSimulationBindings bindings;

    /** Builds an actual steady gas path and full-precision synthetic map for configuration tests. */
    private Fixture() {
      SystemSrkEos fluid = new SystemSrkEos(300.0, 30.0);
      fluid.addComponent("methane", 1.0);
      fluid.setMixingRule("classic");
      Stream feed = new Stream("30-FEED-100", fluid);
      feed.setFlowRate(10000.0, "kg/hr");
      feed.run();
      compressor = new Compressor("30-K-100", feed);
      compressor.setUsePolytropicCalc(true);
      compressor.setPolytropicEfficiency(0.8);
      compressor.setOutletPressure(50.0);
      compressor.setSpeed(10000.0);
      compressor.run();
      double q = feed.getFlowRate("m3/hr");
      double h = compressor.getPolytropicFluidHead();
      double[] speeds = {8000.0, 10000.0, 12000.0};
      double[][] flows = {{q * .48, q * .8, q * 1.12}, {q * .6, q, q * 1.4}, {q * .72, q * 1.2, q * 1.68}};
      double[][] heads = {{h * .768, h * .64, h * .448}, {h * 1.2, h, h * .7}, {h * 1.728, h * 1.44, h * 1.008}};
      double[][] efficiencies = {{80.0, 80.0, 80.0}, {80.0, 80.0, 80.0}, {80.0, 80.0, 80.0}};
      compressor.getCompressorChart().setCurves(new double[] {16.04, 300.0, 30.0, 1.0}, speeds, flows, heads,
          efficiencies);
      compressor.getCompressorChart().setHeadUnit("kJ/kg");
      compressor.getCompressorChart().getSurgeCurve().setCurve(null, new double[] {q * .48, q * .6, q * .72},
          new double[] {h * .768, h * 1.2, h * 1.728});
      ESDValve valve = new ESDValve("30-ASV-100", compressor.getOutletStream());
      valve.setOutletPressure(30.0);
      valve.setCv(100.0);
      valve.setFailSafePosition(100.0);
      valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
      valve.setOpeningTravelTime(0.3);
      valve.setClosingTravelTime(2.0);
      valve.setStrokeTime(0.3);
      valve.setPhysicalStepIdempotencyEnabled(true);
      flow = new VolumeFlowTransmitter("30-FT-100", feed);
      flow.setTransientSamplingEnabled(true);
      margin = new CompressorSurgeMarginMeasurement("30-XY-100", compressor, flow);
      margin.setTransientSamplingEnabled(true);
      controller = new AntiSurgeController("30-FIC-100", compressor, valve);
      controller.setSurgeMarginMeasurement(margin);
      controller.setSurgeMarginSetPoint(0.2);
      controller.setIntegralTime(0.0);
      valve.setController(controller);
      process.add(feed);
      process.add(compressor);
      process.add(valve);
      process.add(flow);
      process.add(margin);
      process.add(controller);
      process.run();
      valve.setPercentValveOpening(0.0);
      valve.setCalculateSteadyState(false);
      project = NorsokOffshoreEngineeringBuilder.from("Synthetic compressor control fixture", process, "2.0.1")
          .projectId("COMPRESSOR-CONTROL-TEST").registerProposedInstruments(false).build();
      pid = PidNativeControlSystem.fromProcess(project);
      bindings = PidNativeControlSystem.bind(project, pid);
    }
  }
}
