package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.util.DynamicProcessHelper;

/**
 * Full-process regression example for steady-to-dynamic three-stage oil separation and gas recompression.
 *
 * <p>
 * The test intentionally uses the public JSON flowsheet API so it also documents a complete runnable process
 * definition. Both anti-surge paths use the ordinary {@link Recycle}: the steady run converges the tear streams, then
 * the same objects perform ordered transient transport while separator pressure and level controllers are active.
 * </p>
 */
class ThreeStageSeparationDynamicProcessTest {
  private static final double FEED_FLOW_KG_PER_HOUR = 50000.0;

  @Test
  void fullProcessUsesSameStandardRecyclesInSteadyAndDynamicModes() {
    SimulationResult buildResult = ProcessSystem.fromJsonAndRun(fullProcessJson());
    assertTrue(buildResult.isSuccess(), buildResult.getErrors().toString());
    ProcessSystem process = buildResult.getProcessSystem();
    assertNotNull(process);
    assertTrue(process.hasRecycles());

    Recycle lpRecycle = (Recycle) process.getUnit("LP Recycle");
    Recycle mpRecycle = (Recycle) process.getUnit("MP Recycle");
    Separator hpSeparator = (Separator) process.getUnit("HP Separator");
    Separator mpSeparator = (Separator) process.getUnit("MP Separator");
    Separator lpSeparator = (Separator) process.getUnit("LP Separator");
    Compressor lpCompressor = (Compressor) process.getUnit("LP Recompressor");
    Compressor mpCompressor = (Compressor) process.getUnit("MP Recompressor");
    Splitter lpSplitter = (Splitter) process.getUnit("LP Discharge Splitter");
    Splitter mpSplitter = (Splitter) process.getUnit("MP Discharge Splitter");
    ThrottlingValve lpRecycleValve = (ThrottlingValve) process.getUnit("LP Anti-Surge Valve");
    ThrottlingValve mpRecycleValve = (ThrottlingValve) process.getUnit("MP Anti-Surge Valve");
    ThrottlingValve stabilizedOilValve = (ThrottlingValve) process.getUnit("LP Oil Valve");
    Mixer exportHeader = (Mixer) process.getUnit("Export Header");

    configureVesselGeometry(hpSeparator);
    configureVesselGeometry(mpSeparator);
    configureVesselGeometry(lpSeparator);
    process.run();

    assertTrue(lpRecycle.solved(), "LP steady recycle should converge");
    assertTrue(mpRecycle.solved(), "MP steady recycle should converge");
    assertFinitePositive(lpCompressor.getInletStream().getFlowRate("kg/hr"));
    assertFinitePositive(mpCompressor.getInletStream().getFlowRate("kg/hr"));
    assertEquals(FEED_FLOW_KG_PER_HOUR,
        stabilizedOilValve.getOutletStream().getFlowRate("kg/hr") + exportHeader.getOutletStream().getFlowRate("kg/hr"),
        0.01 * FEED_FLOW_KG_PER_HOUR);

    configureDynamicCompressor(lpCompressor, 10500.0);
    configureDynamicCompressor(mpCompressor, 11000.0);

    DynamicProcessHelper controls = new DynamicProcessHelper(process);
    controls.setDefaultTimeStep(0.25);
    controls.instrumentAndControl();

    assertNotNull(controls.getController("PC-HP Separator"));
    assertNotNull(controls.getController("LC-HP Separator"));
    assertNotNull(controls.getController("PC-MP Separator"));
    assertNotNull(controls.getController("LC-MP Separator"));
    assertNotNull(controls.getController("PC-LP Separator"));
    assertNotNull(controls.getController("LC-LP Separator"));
    assertFalse(lpRecycle.getCalculateSteadyState());
    assertFalse(mpRecycle.getCalculateSteadyState());

    // These valves are fixed pressure-reduction elements in this compact example. The splitters
    // prescribe recycle flow; only the standard Recycle changes behavior at the mode handover.
    lpRecycleValve.setCalculateSteadyState(true);
    mpRecycleValve.setCalculateSteadyState(true);

    Stream feed = (Stream) process.getUnit("Feed");
    feed.setFlowRate(0.9 * FEED_FLOW_KG_PER_HOUR, "kg/hr");
    process.setParallelTransientEnabled(true);
    for (int step = 0; step < 4; step++) {
      process.runTransient(0.25, UUID.randomUUID());
    }

    assertSame(lpRecycle, process.getUnit("LP Recycle"));
    assertSame(mpRecycle, process.getUnit("MP Recycle"));
    assertEquals(1.0, process.getTime(), 1.0e-12);
    assertEquals(1.0, lpRecycle.getTime(), 1.0e-12);
    assertEquals(1.0, mpRecycle.getTime(), 1.0e-12);
    assertOperatingState(hpSeparator, 20.0, 70.0);
    assertOperatingState(mpSeparator, 5.0, 30.0);
    assertOperatingState(lpSeparator, 0.5, 10.0);
    assertFinitePositive(lpCompressor.getPower("kW"));
    assertFinitePositive(mpCompressor.getPower("kW"));
    assertSplitBalance(lpSplitter);
    assertSplitBalance(mpSplitter);
  }

  private static void configureVesselGeometry(Separator separator) {
    separator.setInternalDiameter(2.0);
    separator.setSeparatorLength(5.0);
    separator.setLiquidLevel(0.5);
  }

  private static void configureDynamicCompressor(Compressor compressor, double designSpeed) {
    double designFlow = compressor.getInletStream().getFlowRate("m3/hr");
    double designHead = compressor.getPolytropicFluidHead();
    assertFinitePositive(designFlow);
    assertFinitePositive(designHead);
    compressor.generateCompressorChartFromDesignPoint(designSpeed, designFlow, designHead, 0.75, 5);
    compressor.getCompressorChart().setUseCompressorChart(true);
    compressor.setMinimumSpeed(7000.0);
    compressor.setMaximumSpeed(13000.0);
    compressor.setSpeed(designSpeed);
    compressor.setSolveSpeed(false);
    compressor.setTransientCalculationMode(Compressor.TransientCalculationMode.QUASI_STEADY);
  }

  private static void assertOperatingState(Separator separator, double minimumPressure, double maximumPressure) {
    double pressure = separator.getGasOutStream().getPressure("bara");
    assertTrue(Double.isFinite(pressure));
    assertTrue(pressure >= minimumPressure && pressure <= maximumPressure,
        separator.getName() + " pressure outside expected range: " + pressure);
    assertTrue(Double.isFinite(separator.getLiquidLevel()));
    assertTrue(separator.getLiquidLevel() >= 0.0 && separator.getLiquidLevel() <= 1.0,
        separator.getName() + " liquid level outside [0, 1]");
  }

  private static void assertSplitBalance(Splitter splitter) {
    double inlet = splitter.getInletStream().getFlowRate("kg/hr");
    double outlets = splitter.getSplitStream(0).getFlowRate("kg/hr") + splitter.getSplitStream(1).getFlowRate("kg/hr");
    assertEquals(inlet, outlets, Math.max(1.0e-6, 1.0e-8 * inlet), splitter.getName() + " should conserve mass");
  }

  private static void assertFinitePositive(double value) {
    assertTrue(Double.isFinite(value) && value > 0.0, "Expected a finite positive value, got " + value);
  }

  private static String fullProcessJson() {
    return "{" + "\"fluid\":{\"model\":\"SRK\",\"temperature\":333.15,\"pressure\":60.0,"
        + "\"mixingRule\":\"classic\",\"components\":{\"methane\":0.38,"
        + "\"ethane\":0.04,\"propane\":0.03,\"n-heptane\":0.55}}," + "\"autoRun\":true,\"process\":["
        + "{\"type\":\"Stream\",\"name\":\"Feed\",\"properties\":{"
        + "\"flowRate\":[50000.0,\"kg/hr\"],\"temperature\":[60.0,\"C\"]," + "\"pressure\":[60.0,\"bara\"]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"Feed Valve\",\"inlet\":\"Feed\","
        + "\"properties\":{\"outletPressure\":[50.0,\"bara\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"HP Separator\",\"inlet\":\"Feed Valve.out\"},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"HP Gas Valve\","
        + "\"inlet\":\"HP Separator.gasOut\",\"properties\":{\"outletPressure\":[49.0,\"bara\"]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"HP Oil Valve\","
        + "\"inlet\":\"HP Separator.liquidOut\",\"properties\":{\"outletPressure\":[15.0,\"bara\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"MP Separator\",\"inlet\":\"HP Oil Valve.out\"},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"MP Gas Valve\","
        + "\"inlet\":\"MP Separator.gasOut\",\"properties\":{\"outletPressure\":[13.0,\"bara\"]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"MP Oil Valve\","
        + "\"inlet\":\"MP Separator.liquidOut\",\"properties\":{\"outletPressure\":[3.0,\"bara\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"LP Separator\",\"inlet\":\"MP Oil Valve.out\"},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"LP Gas Valve\","
        + "\"inlet\":\"LP Separator.gasOut\",\"properties\":{\"outletPressure\":[2.0,\"bara\"]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"LP Oil Valve\","
        + "\"inlet\":\"LP Separator.liquidOut\",\"properties\":{\"outletPressure\":[1.2,\"bara\"]}},"
        + "{\"type\":\"Stream\",\"name\":\"Stabilized Oil\",\"inlet\":\"LP Oil Valve.out\"},"
        + "{\"type\":\"Mixer\",\"name\":\"LP Suction Mixer\","
        + "\"inlets\":[\"LP Gas Valve.out\",\"LP Recycle.out\"]},"
        + "{\"type\":\"Compressor\",\"name\":\"LP Recompressor\","
        + "\"inlet\":\"LP Suction Mixer.out\",\"properties\":{\"outletPressure\":[13.0,\"bara\"]}},"
        + "{\"type\":\"Cooler\",\"name\":\"LP Aftercooler\",\"inlet\":\"LP Recompressor.out\","
        + "\"properties\":{\"outTemperature\":[35.0,\"C\"]}},"
        + "{\"type\":\"Splitter\",\"name\":\"LP Discharge Splitter\","
        + "\"inlet\":\"LP Aftercooler.out\",\"properties\":{\"splitFactors\":[0.98,0.02]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"LP Anti-Surge Valve\","
        + "\"inlet\":\"LP Discharge Splitter.splitStream_1\"," + "\"properties\":{\"outletPressure\":[2.0,\"bara\"]}},"
        + "{\"type\":\"Recycle\",\"name\":\"LP Recycle\",\"inlet\":\"LP Anti-Surge Valve.out\"},"
        + "{\"type\":\"Mixer\",\"name\":\"MP Suction Mixer\","
        + "\"inlets\":[\"MP Gas Valve.out\",\"LP Discharge Splitter.splitStream_0\",\"MP Recycle.out\"]},"
        + "{\"type\":\"Compressor\",\"name\":\"MP Recompressor\","
        + "\"inlet\":\"MP Suction Mixer.out\",\"properties\":{\"outletPressure\":[49.0,\"bara\"]}},"
        + "{\"type\":\"Cooler\",\"name\":\"MP Aftercooler\",\"inlet\":\"MP Recompressor.out\","
        + "\"properties\":{\"outTemperature\":[35.0,\"C\"]}},"
        + "{\"type\":\"Splitter\",\"name\":\"MP Discharge Splitter\","
        + "\"inlet\":\"MP Aftercooler.out\",\"properties\":{\"splitFactors\":[0.98,0.02]}},"
        + "{\"type\":\"ThrottlingValve\",\"name\":\"MP Anti-Surge Valve\","
        + "\"inlet\":\"MP Discharge Splitter.splitStream_1\"," + "\"properties\":{\"outletPressure\":[13.0,\"bara\"]}},"
        + "{\"type\":\"Recycle\",\"name\":\"MP Recycle\",\"inlet\":\"MP Anti-Surge Valve.out\"},"
        + "{\"type\":\"Mixer\",\"name\":\"Export Header\","
        + "\"inlets\":[\"HP Gas Valve.out\",\"MP Discharge Splitter.splitStream_0\"]},"
        + "{\"type\":\"Stream\",\"name\":\"Export Gas\",\"inlet\":\"Export Header.out\"}]}";
  }
}
