package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for compressor water wash simulation based on SNA 27AKA60 field data.
 *
 * <p>
 * Two scenarios are tested:
 * <ul>
 * <li>Prior to wash test: dry gas compression with outlet temperature specified</li>
 * <li>During wash test: wet gas compression with water injection and polytropic efficiency
 * specified</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorWaterWashProcessTest extends neqsim.NeqSimTest {
  /** Inlet pressure in bara. */
  private static final double COMP_PIN = 26.9 + 1.0;
  /** Inlet temperature in Kelvin. */
  private static final double COMP_TIN = 27.0 + 273.15;
  /** Outlet pressure in bara. */
  private static final double COMP_POUT = 121.0 + 1.0;
  /** Outlet temperature in Kelvin (prior to wash). */
  private static final double COMP_TOUT = 167.0 + 273.15;
  /** Mass flow rate in kg/hr. */
  private static final double COMP_FLOW = 149520.0;

  /** Main gas feed fluid. */
  private SystemInterface mainFeed;
  /** Pure water wash fluid. */
  private SystemInterface washFluid;

  /**
   * Sets up the gas and wash fluids before each test.
   */
  @BeforeEach
  public void setUp() {
    // Create PR EoS fluid at inlet conditions
    mainFeed = new SystemPrEos(COMP_TIN, COMP_PIN);
    mainFeed.addComponent("nitrogen", 2.53);
    mainFeed.addComponent("CO2", 0.51);
    mainFeed.addComponent("methane", 68.54);
    mainFeed.addComponent("ethane", 12.81);
    mainFeed.addComponent("propane", 10.27);
    mainFeed.addComponent("i-butane", 1.02);
    mainFeed.addComponent("n-butane", 2.75);
    mainFeed.addComponent("i-pentane", 0.44);
    mainFeed.addComponent("n-pentane", 0.53);
    mainFeed.addComponent("n-hexane", 0.61);
    mainFeed.setMixingRule("classic");
    mainFeed.setMultiPhaseCheck(true);

    // Create wash fluid: same component list, pure water
    washFluid = new SystemPrEos(COMP_TIN, COMP_PIN);
    washFluid.addComponent("water", 1.0);
    washFluid.addComponent("nitrogen", 0.0);
    washFluid.addComponent("CO2", 0.0);
    washFluid.addComponent("methane", 0.0);
    washFluid.addComponent("ethane", 0.0);
    washFluid.addComponent("propane", 0.0);
    washFluid.addComponent("i-butane", 0.0);
    washFluid.addComponent("n-butane", 0.0);
    washFluid.addComponent("i-pentane", 0.0);
    washFluid.addComponent("n-pentane", 0.0);
    washFluid.addComponent("n-hexane", 0.0);
    washFluid.setMixingRule("classic");
    washFluid.setMultiPhaseCheck(true);
  }

  /**
   * Test compressor operation prior to water wash test. Outlet temperature is specified and
   * polytropic efficiency is back-calculated.
   */
  @Test
  void testPriorToWashTest() {
    ProcessSystem processOps = new ProcessSystem();

    // Inlet feed stream
    Stream inletStream = new Stream("StreamBase", mainFeed.clone());
    inletStream.setTemperature(COMP_TIN, "K");
    inletStream.setPressure(COMP_PIN, "bara");
    inletStream.setFlowRate(COMP_FLOW, "kg/hr");
    processOps.add(inletStream);

    // Inlet scrubber
    Separator feedSeparator = new Separator("InletFlash", inletStream);
    processOps.add(feedSeparator);

    // Gas from scrubber
    Stream feedGasStream = new Stream("StreamFlashed", feedSeparator.getGasOutStream());
    processOps.add(feedGasStream);

    // Mixer (gas only, no water)
    Mixer mixerFeed = new Mixer("feedMix");
    mixerFeed.addStream(feedGasStream);
    processOps.add(mixerFeed);

    // Feed to compressor
    Stream feedStream = new Stream("StreamIn", mixerFeed.getOutletStream());
    processOps.add(feedStream);

    // Compressor with outlet temperature specified
    Compressor compressor1 = new Compressor("27AKA60", feedStream);
    compressor1.setOutletPressure(COMP_POUT);
    compressor1.setOutTemperature(COMP_TOUT);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicMethod("detailed");
    compressor1.setNumberOfCompressorCalcSteps(10);
    compressor1.getPropertyProfile().setActive(true);
    processOps.add(compressor1);

    processOps.run();

    // Verify results
    double power = compressor1.getPower() / 1.0e6;
    double polytropicHead = compressor1.getPolytropicHead("kJ/kg");
    double polytropicEfficiency = compressor1.getPolytropicEfficiency();

    double outletPressure = compressor1.getOutletStream().getPressure("bara");
    double outletTemperature = compressor1.getOutletStream().getTemperature("K");

    System.out.println("=== Prior to wash test ===");
    System.out.println("Power: " + power + " MW");
    System.out.println("Polytropic head: " + polytropicHead + " kJ/kg");
    System.out.println("Polytropic efficiency: " + polytropicEfficiency * 100.0 + " %");
    System.out.println("Inlet volume rate: " + feedStream.getFlowRate("m3/hr") + " m3/hr");
    System.out.println("Outlet pressure: " + outletPressure + " bara");
    System.out.println("Outlet temperature: " + (outletTemperature - 273.15) + " C");

    // Sanity checks on compressor operation
    assertTrue(power > 0, "Compressor power should be positive");
    assertTrue(polytropicHead > 0, "Polytropic head should be positive");
    assertTrue(polytropicEfficiency > 0.5 && polytropicEfficiency < 1.0,
        "Polytropic efficiency should be between 50% and 100%");
    assertEquals(COMP_POUT, outletPressure, 1.0, "Outlet pressure should match setpoint");
    assertEquals(COMP_TOUT, outletTemperature, 2.0, "Outlet temperature should match setpoint");
  }

  /**
   * Test compressor operation during water wash test. Water is injected into the gas feed and
   * polytropic efficiency is specified.
   */
  @Test
  void testDuringWashTest() {
    double feedWaterRate = 5.0 * 1000.0; // 5000 kg/hr
    double polytropicEfficiency = 64.66 * 0.933 / 100.0;

    ProcessSystem processOps = new ProcessSystem();

    // Inlet feed stream
    Stream inletStream = new Stream("StreamBase", mainFeed.clone());
    inletStream.setTemperature(COMP_TIN, "K");
    inletStream.setPressure(COMP_PIN, "bara");
    inletStream.setFlowRate(COMP_FLOW, "kg/hr");
    processOps.add(inletStream);

    // Inlet scrubber
    Separator feedSeparator = new Separator("InletFlash", inletStream);
    processOps.add(feedSeparator);

    // Gas from scrubber
    Stream feedGasStream = new Stream("StreamFlashed", feedSeparator.getGasOutStream());
    processOps.add(feedGasStream);

    // Wash water stream
    Stream feedAqStream = new Stream("WashLiquid", washFluid.clone());
    feedAqStream.setFlowRate(feedWaterRate, "kg/hr");
    feedAqStream.setTemperature(COMP_TIN, "K");
    feedAqStream.setPressure(COMP_PIN, "bara");
    processOps.add(feedAqStream);

    // Mixer: water + gas
    Mixer mixerFeed = new Mixer("feedMix");
    mixerFeed.addStream(feedAqStream);
    mixerFeed.addStream(feedGasStream);
    processOps.add(mixerFeed);

    // Feed to compressor
    Stream feedStream = new Stream("StreamIn", mixerFeed.getOutletStream());
    processOps.add(feedStream);

    // Compressor with efficiency specified
    Compressor compressor1 = new Compressor("27AKA60", feedStream);
    compressor1.setOutletPressure(COMP_POUT);
    compressor1.setPolytropicEfficiency(polytropicEfficiency);
    compressor1.setUsePolytropicCalc(true);
    compressor1.setPolytropicMethod("detailed");
    compressor1.setNumberOfCompressorCalcSteps(10);
    compressor1.getPropertyProfile().setActive(true);
    processOps.add(compressor1);

    processOps.run();

    // Verify results
    double power = compressor1.getPower() / 1.0e6;
    double polytropicHead = compressor1.getPolytropicHead("kJ/kg");
    double actualEfficiency = compressor1.getPolytropicEfficiency();
    double outletPressure = compressor1.getOutletStream().getPressure("bara");
    double outletTemperature = compressor1.getOutletStream().getTemperature("C");
    double totalFlow = compressor1.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    double waterMassFraction =
        feedAqStream.getThermoSystem().getFlowRate("kg/hr") * 100.0 / totalFlow;
    boolean hasAqueousOutlet =
        compressor1.getOutletStream().getThermoSystem().hasPhaseType("aqueous");

    System.out.println("=== During wash test ===");
    System.out.println("Power: " + power + " MW");
    System.out.println("Polytropic head: " + polytropicHead + " kJ/kg");
    System.out.println("Polytropic efficiency: " + actualEfficiency * 100.0 + " %");
    System.out.println("Inlet volume rate: " + feedStream.getFlowRate("m3/hr") + " m3/hr");
    System.out.println("Outlet pressure: " + (outletPressure - 1.0) + " barg");
    System.out.println("Outlet temperature: " + outletTemperature + " C");
    System.out.println("Water mass fraction: " + waterMassFraction + " %");
    System.out.println("Aqueous phase in outlet: " + hasAqueousOutlet);

    // Sanity checks
    assertTrue(power > 0, "Compressor power should be positive");
    assertTrue(polytropicHead > 0, "Polytropic head should be positive");
    assertEquals(polytropicEfficiency, actualEfficiency, 0.01,
        "Polytropic efficiency should match specified value");
    assertEquals(COMP_POUT, outletPressure, 1.0, "Outlet pressure should match setpoint");
    // With water injection, evaporative cooling limits temperature rise
    // Expect modest temperature increase (>inlet, but not necessarily >100C)
    assertTrue(outletTemperature > COMP_TIN - 273.15,
        "Outlet temperature should be above inlet temperature");
    assertTrue(waterMassFraction > 0.0 && waterMassFraction < 10.0,
        "Water mass fraction should be reasonable");

    // Regression test: property profile length must equal numberOfCompressorCalcSteps.
    // Previously, when PSflash diverged inside a step (common with water/MEG on cubic
    // EOS), the loop did 'continue' and skipped propertyProfile.addFluid(), producing
    // a profile shorter than requested and breaking index-based access in user code.
    int profileLength = compressor1.getPropertyProfile().getFluid().size();
    assertEquals(10, profileLength,
        "Property profile must contain exactly numberOfCompressorCalcSteps fluids");

    // Pressure must increase monotonically across the profile.
    double pPrev = feedStream.getPressure("bara");
    for (int i = 0; i < profileLength; i++) {
      double pStep = compressor1.getPropertyProfile().getFluid().get(i).getPressure("bara");
      assertTrue(pStep >= pPrev - 1e-6,
          "Profile pressure must be monotonically increasing (step " + i + ")");
      pPrev = pStep;
    }
  }

  /**
   * Test the effect of varying water wash rate on compressor outlet conditions.
   */
  @Test
  void testVaryingWashRate() {
    double polytropicEfficiency = 64.66 * 0.933 / 100.0;
    double[] washRates = {0.0, 2000.0, 5000.0};
    double[] outletTemperatures = new double[washRates.length];

    for (int i = 0; i < washRates.length; i++) {
      ProcessSystem processOps = new ProcessSystem();

      Stream inletStream = new Stream("StreamBase", mainFeed.clone());
      inletStream.setTemperature(COMP_TIN, "K");
      inletStream.setPressure(COMP_PIN, "bara");
      inletStream.setFlowRate(COMP_FLOW, "kg/hr");
      processOps.add(inletStream);

      Separator feedSeparator = new Separator("InletFlash", inletStream);
      processOps.add(feedSeparator);

      Stream feedGasStream = new Stream("StreamFlashed", feedSeparator.getGasOutStream());
      processOps.add(feedGasStream);

      Mixer mixerFeed = new Mixer("feedMix");

      if (washRates[i] > 0.0) {
        Stream feedAqStream = new Stream("WashLiquid", washFluid.clone());
        feedAqStream.setFlowRate(washRates[i], "kg/hr");
        feedAqStream.setTemperature(COMP_TIN, "K");
        feedAqStream.setPressure(COMP_PIN, "bara");
        processOps.add(feedAqStream);
        mixerFeed.addStream(feedAqStream);
      }
      mixerFeed.addStream(feedGasStream);
      processOps.add(mixerFeed);

      Stream feedStream = new Stream("StreamIn", mixerFeed.getOutletStream());
      processOps.add(feedStream);

      Compressor compressor1 = new Compressor("27AKA60", feedStream);
      compressor1.setOutletPressure(COMP_POUT);
      compressor1.setPolytropicEfficiency(polytropicEfficiency);
      compressor1.setUsePolytropicCalc(true);
      compressor1.setPolytropicMethod("detailed");
      compressor1.setNumberOfCompressorCalcSteps(10);
      processOps.add(compressor1);

      processOps.run();

      outletTemperatures[i] = compressor1.getOutletStream().getTemperature("C");
      System.out.println(
          "Wash rate " + washRates[i] + " kg/hr -> outlet T = " + outletTemperatures[i] + " C");
    }

    // More water should reduce outlet temperature (evaporative cooling)
    assertTrue(outletTemperatures[2] < outletTemperatures[0],
        "Higher wash rate should reduce outlet temperature");
  }

  /**
   * Regression test for pure-associating-fluid robustness on a cubic EOS.
   *
   * <p>
   * Pure MEG on PR EOS makes the PSflash Newton ill-conditioned (single component, strong
   * association). Before the Newton-fallback fix, the detailed polytropic loop produced
   * non-physical outlet conditions (T &lt; -200 C, density &gt; 10000 kg/m3). After the fix the
   * recovery path solves S(T,P)=S_target via 1D Newton on T at fixed P (using TPflash, which is
   * robust for these fluids), so the outlet must be physical for any EOS.
   */
  @Test
  void testPureMEGCompressionOnPREos() {
    SystemInterface megFluid = new SystemPrEos(300.0, 5.0);
    megFluid.addComponent("MEG", 1.0);
    megFluid.setMixingRule("classic");

    Stream feed = new Stream("MEG feed", megFluid);
    feed.setTemperature(300.0, "K");
    feed.setPressure(5.0, "bara");
    feed.setFlowRate(1000.0, "kg/hr");

    Compressor compressor = new Compressor("MEG compressor", feed);
    compressor.setOutletPressure(50.0);
    compressor.setPolytropicEfficiency(0.75);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicMethod("detailed");
    compressor.setNumberOfCompressorCalcSteps(10);
    compressor.getPropertyProfile().setActive(true);

    ProcessSystem processOps = new ProcessSystem();
    processOps.add(feed);
    processOps.add(compressor);
    processOps.run();

    double tOut = compressor.getOutletStream().getTemperature("C");
    double rhoOut = compressor.getOutletStream().getFluid().getDensity("kg/m3");
    int profileLength = compressor.getPropertyProfile().getFluid().size();

    System.out.println("Pure MEG on PR: outlet T = " + tOut + " C, rho = " + rhoOut + " kg/m3");

    // Outlet must be physical: T above inlet (compression heats), finite, not catastrophic.
    assertTrue(Double.isFinite(tOut), "Outlet temperature must be finite");
    assertTrue(tOut > 20.0, "Outlet T must be above inlet (26.85 C) — got " + tOut + " C");
    assertTrue(tOut < 500.0, "Outlet T must be physically bounded — got " + tOut + " C");
    assertTrue(Double.isFinite(rhoOut) && rhoOut > 0.0 && rhoOut < 5000.0,
        "Outlet density must be in physical range — got " + rhoOut + " kg/m3");
    assertEquals(10, profileLength,
        "Property profile must contain exactly numberOfCompressorCalcSteps fluids");
  }
}
