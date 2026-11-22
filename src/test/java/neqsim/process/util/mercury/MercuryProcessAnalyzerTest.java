package neqsim.process.util.mercury;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.adsorber.MercuryRemovalBed;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Tests mercury tracking through process simulations with {@link MercuryProcessAnalyzer} and free mercury accumulation
 * in separators.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MercuryProcessAnalyzerTest {
  /**
   * Create a wet gas condensate with mercury.
   *
   * @param mercury mole fraction of mercury in the feed
   * @return fluid at 60 C and 60 bara
   */
  private static SystemInterface createFeedFluid(double mercury) {
    SystemInterface fluid = new SystemUMRPRUMCEos(273.15 + 60.0, 60.0);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.addComponent("n-heptane", 0.03);
    fluid.addComponent("n-decane", 0.03);
    fluid.addComponent("water", 0.07);
    fluid.addComponent("mercury", mercury);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Build a process with inlet separation, recompression of the low pressure gas, gas cooling, a chilled dew point
   * control separator and export compression.
   *
   * @param mercury mole fraction of mercury in the feed
   * @param coldTemperature temperature of the dew point control separator in Celsius
   * @param mercuryRemovalBed true to put a mercury removal bed before the chiller
   * @return process that has not been run
   */
  private static ProcessSystem createGasProcess(double mercury, double coldTemperature, boolean mercuryRemovalBed) {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed", createFeedFluid(mercury));
    feed.setFlowRate(100000.0, "kg/hr");
    process.add(feed);
    ThreePhaseSeparator inletSeparator = new ThreePhaseSeparator("HP separator", feed);
    process.add(inletSeparator);
    ThrottlingValve valve = new ThrottlingValve("LP valve", inletSeparator.getOilOutStream());
    valve.setOutletPressure(8.0);
    process.add(valve);
    ThreePhaseSeparator lowPressureSeparator = new ThreePhaseSeparator("LP separator", valve.getOutletStream());
    process.add(lowPressureSeparator);
    Compressor recompressor = new Compressor("recompressor", lowPressureSeparator.getGasOutStream());
    recompressor.setOutletPressure(60.0);
    recompressor.setPolytropicEfficiency(0.75);
    process.add(recompressor);
    Cooler recompressionCooler = new Cooler("recompression cooler", recompressor.getOutletStream());
    recompressionCooler.setOutTemperature(273.15 + 30.0);
    process.add(recompressionCooler);
    Separator recompressionScrubber = new Separator("recompression scrubber", recompressionCooler.getOutletStream());
    process.add(recompressionScrubber);
    Mixer gasMixer = new Mixer("gas mixer");
    gasMixer.addStream(inletSeparator.getGasOutStream());
    gasMixer.addStream(recompressionScrubber.getGasOutStream());
    process.add(gasMixer);
    Cooler gasCooler = new Cooler("gas cooler", gasMixer.getOutletStream());
    gasCooler.setOutTemperature(273.15 + 25.0);
    process.add(gasCooler);
    Separator gasScrubber = new Separator("gas scrubber", gasCooler.getOutletStream());
    process.add(gasScrubber);
    StreamInterface chillerFeed = gasScrubber.getGasOutStream();
    if (mercuryRemovalBed) {
      MercuryRemovalBed bed = new MercuryRemovalBed("mercury removal bed", chillerFeed);
      process.add(bed);
      chillerFeed = bed.getOutletStream();
    }
    Cooler chiller = new Cooler("chiller", chillerFeed);
    chiller.setOutTemperature(273.15 + coldTemperature);
    process.add(chiller);
    Separator coldSeparator = new Separator("cold separator", chiller.getOutletStream());
    process.add(coldSeparator);
    Compressor exportCompressor = new Compressor("export compressor", coldSeparator.getGasOutStream());
    exportCompressor.setOutletPressure(150.0);
    process.add(exportCompressor);
    Cooler exportCooler = new Cooler("export cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(273.15 + 30.0);
    process.add(exportCooler);
    return process;
  }

  @Test
  void tracksMercuryAndFindsSolidMercuryInTheChilledGas() {
    ProcessSystem process = createGasProcess(5.0e-8, -45.0, false);
    process.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);

    // All mercury in the feed leaves with the products; nothing is held back.
    double feedMercury = report.getFeedMercuryFlowRate("g/day");
    assertEquals(902.3, feedMercury, 1.0);
    assertEquals(feedMercury, report.getProductMercuryFlowRate("g/day"), 1.0e-6 * feedMercury);
    assertEquals(0.0, report.getRetainedMercuryFlowRate("g/day"), 1.0e-6 * feedMercury);
    assertEquals(0.0, report.getFreeMercuryAccumulationRate("g/day"), 0.0);
    assertEquals(0.0, report.getMercuryBalanceError("g/day"), 1.0e-9 * feedMercury);

    // The inlet separator splits the mercury between gas, oil and water.
    MercuryStreamResult feed = report.getStream("feed");
    assertTrue(feed.isFeed());
    assertFalse(feed.hasFreeMercury());
    assertEquals(3, feed.getNumberOfPhases());
    double split = report.getStream("HP separator.gasOutStream").getMercuryFlowRate("g/day")
        + report.getStream("HP separator.liquidOutStream").getMercuryFlowRate("g/day")
        + report.getStream("HP separator.waterOutStream").getMercuryFlowRate("g/day");
    assertEquals(feedMercury, split, 1.0e-6 * feedMercury);
    assertTrue(feed.getMercuryConcentration("oil", "ug/kg") > feed.getMercuryConcentration("aqueous", "ug/kg"));
    assertEquals(feedMercury, feed.getPhaseMercuryFlowRate(0, "g/day") + feed.getPhaseMercuryFlowRate(1, "g/day")
        + feed.getPhaseMercuryFlowRate(2, "g/day"), 1.0e-6 * feedMercury);

    // Mercury stays dissolved down to ambient temperature and comes out as solid in the chilled gas at -45 C.
    assertTrue(report.getStream("gas cooler.outStream").getSaturationRatio() < 1.0);
    MercuryStreamResult chilledGas = report.getStream("chiller.outStream");
    assertTrue(chilledGas.getSaturationRatio() > 10.0);
    assertEquals("solid", chilledGas.getFreeMercuryState());
    assertTrue(chilledGas.getFreeMercuryFlowRate("g/day") > 0.9 * chilledGas.getMercuryFlowRate("g/day"));
    assertEquals(chilledGas.getMercuryFlowRate("g/day"),
        chilledGas.getFreeMercuryFlowRate("g/day") + chilledGas.getDissolvedMercuryFlowRate("g/day"), 1.0e-9);
    assertTrue(report.hasFreeMercury());
    assertTrue(report.getMaximumSaturationRatio() >= chilledGas.getSaturationRatio());

    // Without free mercury accumulation the separator sends the free mercury on with its outlet streams.
    assertEquals(3, report.getStreamsWithFreeMercury().size());
    assertTrue(report.getStream("cold separator.gasOutStream").hasFreeMercury());
    assertFalse(report.getStream("export cooler.outStream").hasFreeMercury());
    assertTrue(report.getStream("export cooler.outStream").isProduct());
  }

  @Test
  void separatorsHoldBackSolidMercury() {
    ProcessSystem process = createGasProcess(5.0e-8, -45.0, false);
    process.run();
    MercuryProcessReport before = MercuryProcessAnalyzer.analyze(process);

    assertEquals(5, MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, true));
    process.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);

    // The cold separator holds back the solid mercury that its feed carries.
    Separator coldSeparator = (Separator) process.getUnit("cold separator");
    assertTrue(coldSeparator.isFreeMercuryAccumulation());
    assertEquals("solid", coldSeparator.getFreeMercuryState());
    double freeInFeed = report.getStream("chiller.outStream").getFreeMercuryFlowRate("g/day");
    assertEquals(407.3, freeInFeed, 1.0);
    assertEquals(freeInFeed, coldSeparator.getFreeMercuryAccumulationRate("g/day"), 1.0e-5 * freeInFeed);
    assertEquals(coldSeparator.getFreeMercuryAccumulationRate("kg/hr") * 24.0e3,
        coldSeparator.getFreeMercuryAccumulationRate("g/day"), 1.0e-9);

    // The gas and liquid leave saturated, with the mercury content of the equilibrium calculation on the feed.
    MercuryStreamResult coldGas = report.getStream("cold separator.gasOutStream");
    assertFalse(coldGas.hasFreeMercury());
    assertEquals(1.0, coldGas.getSaturationRatio(), 1.0e-4);
    MercuryPhaseEquilibrium equilibrium = new MercuryPhaseEquilibrium(
        ((Cooler) process.getUnit("chiller")).getOutletStream().getFluid());
    equilibrium.run();
    assertEquals(equilibrium.getMercuryMoleFraction(0), coldGas.getMercuryMoleFraction(coldGas.getPhaseNumber("gas")),
        1.0e-4 * equilibrium.getMercuryMoleFraction(0));
    assertEquals(equilibrium.getMercuryConcentration(0, "ug/Sm3"), coldGas.getMercuryConcentration("gas", "ug/Sm3"),
        1.0e-4 * equilibrium.getMercuryConcentration(0, "ug/Sm3"));
    assertFalse(report.getStream("export cooler.outStream").hasFreeMercury());
    assertTrue(report.getStream("export cooler.outStream").getMercuryFlowRate("g/day") < 0.02
        * before.getStream("export cooler.outStream").getMercuryFlowRate("g/day"));

    // Feed mercury is found in the products and in the separator.
    MercuryUnitResult coldUnit = report.getUnit("cold separator");
    assertEquals("Separator", coldUnit.getType());
    assertEquals("solid", coldUnit.getFreeMercuryState());
    assertEquals(coldUnit.getRetainedMercuryFlowRate("g/day"), coldUnit.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * freeInFeed);
    assertEquals(coldUnit.getFreeMercuryAccumulationRate("g/day"), report.getFreeMercuryAccumulationRate("g/day"),
        1.0e-9);
    double feedMercury = report.getFeedMercuryFlowRate("g/day");
    assertEquals(feedMercury,
        report.getProductMercuryFlowRate("g/day") + report.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * feedMercury);

    // Separators without free mercury give the same result as before.
    assertEquals("none", ((Separator) process.getUnit("HP separator")).getFreeMercuryState());
    assertEquals(before.getStream("HP separator.gasOutStream").getMercuryFlowRate("g/day"),
        report.getStream("HP separator.gasOutStream").getMercuryFlowRate("g/day"), 1.0e-9);
    assertEquals(before.getStream("gas scrubber.gasOutStream").getMercuryConcentration("gas", "ug/Sm3"),
        report.getStream("gas scrubber.gasOutStream").getMercuryConcentration("gas", "ug/Sm3"), 1.0e-9);

    // Switching the option off again restores the first result.
    MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, false);
    process.run();
    assertEquals(0.0, coldSeparator.getFreeMercuryAccumulationRate("g/day"), 0.0);
    assertEquals(before.getStream("export cooler.outStream").getMercuryFlowRate("g/day"),
        MercuryProcessAnalyzer.analyze(process).getStream("export cooler.outStream").getMercuryFlowRate("g/day"),
        1.0e-6);
  }

  @Test
  void separatorsHoldBackLiquidMercury() {
    ProcessSystem process = createGasProcess(3.0e-6, -20.0, false);
    MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, true);
    process.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);

    // The feed holds more mercury than it dissolves, so liquid mercury settles in the three-phase inlet separator.
    MercuryStreamResult feed = report.getStream("feed");
    assertEquals("liquid", feed.getFreeMercuryState());
    ThreePhaseSeparator inletSeparator = (ThreePhaseSeparator) process.getUnit("HP separator");
    assertEquals("liquid", inletSeparator.getFreeMercuryState());
    assertEquals(feed.getFreeMercuryFlowRate("g/day"), inletSeparator.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * feed.getFreeMercuryFlowRate("g/day"));
    // The held back mercury closes the mass balance of the separator.
    double heldBack = inletSeparator.getFreeMercuryAccumulationRate("kg/hr");
    assertTrue(heldBack > 0.5);
    assertEquals(0.0, inletSeparator.getMassBalance("kg/hr"), 0.01 * heldBack);
    assertEquals(1.0, report.getStream("HP separator.gasOutStream").getSaturationRatio(), 1.0e-3);
    assertEquals(1.0, report.getStream("HP separator.liquidOutStream").getSaturationRatio(), 1.0e-3);
    assertFalse(report.getStream("HP separator.liquidOutStream").hasFreeMercury());
    assertEquals(feed.getMercuryConcentration("oil", "ug/kg"),
        report.getStream("HP separator.liquidOutStream").getMercuryConcentration("oil", "ug/kg"),
        1.0e-3 * feed.getMercuryConcentration("oil", "ug/kg"));

    // Pressure reduction, recompression with cooling and chilling each drop out more liquid mercury.
    assertEquals("liquid", ((Separator) process.getUnit("LP separator")).getFreeMercuryState());
    assertEquals("liquid", ((Separator) process.getUnit("recompression scrubber")).getFreeMercuryState());
    assertEquals("liquid", ((Separator) process.getUnit("cold separator")).getFreeMercuryState());
    assertEquals("liquid", report.getStream("chiller.outStream").getFreeMercuryState());
    assertFalse(report.getStream("cold separator.gasOutStream").hasFreeMercury());

    double feedMercury = report.getFeedMercuryFlowRate("g/day");
    assertTrue(report.getFreeMercuryAccumulationRate("g/day") > 0.5 * feedMercury);
    assertEquals(feedMercury,
        report.getProductMercuryFlowRate("g/day") + report.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * feedMercury);
    assertEquals(report.getRetainedMercuryFlowRate("g/day"), report.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * feedMercury);
  }

  @Test
  void mercuryRemovalBedKeepsTheChilledGasBelowSaturation() {
    ProcessSystem process = createGasProcess(5.0e-8, -45.0, true);
    MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, true);
    process.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);

    MercuryUnitResult bed = report.getUnit("mercury removal bed");
    assertEquals("MercuryRemovalBed", bed.getType());
    assertTrue(bed.getRetainedMercuryFlowRate("g/day") > 0.99 * bed.getInletMercuryFlowRate("g/day"));
    assertEquals(0.0, bed.getFreeMercuryAccumulationRate("g/day"), 0.0);
    assertEquals("none", bed.getFreeMercuryState());
    assertEquals(bed.getInletMercuryFlowRate("g/day") - bed.getOutletMercuryFlowRate("g/day"),
        bed.getRetainedMercuryFlowRate("g/day"), 1.0e-9);

    assertTrue(report.getStream("chiller.outStream").getSaturationRatio() < 1.0);
    assertFalse(report.hasFreeMercury());
    assertEquals(0.0, report.getFreeMercuryAccumulationRate("g/day"), 0.0);
    assertEquals(bed.getRetainedMercuryFlowRate("g/day"), report.getRetainedMercuryFlowRate("g/day"),
        1.0e-6 * bed.getRetainedMercuryFlowRate("g/day"));
  }

  @Test
  void plantModelWithTwoProcessAreas() {
    ProcessSystem separation = new ProcessSystem();
    Stream feed = new Stream("feed", createFeedFluid(5.0e-8));
    feed.setFlowRate(100000.0, "kg/hr");
    separation.add(feed);
    ThreePhaseSeparator inletSeparator = new ThreePhaseSeparator("HP separator", feed);
    separation.add(inletSeparator);

    ProcessSystem gasTreatment = new ProcessSystem();
    Cooler chiller = new Cooler("chiller", inletSeparator.getGasOutStream());
    chiller.setOutTemperature(273.15 - 45.0);
    gasTreatment.add(chiller);
    Separator coldSeparator = new Separator("cold separator", chiller.getOutletStream());
    gasTreatment.add(coldSeparator);

    ProcessModel plant = new ProcessModel();
    plant.add("separation", separation);
    plant.add("gas treatment", gasTreatment);
    assertEquals(2, MercuryProcessAnalyzer.setFreeMercuryAccumulation(plant, true));
    plant.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(plant);

    // The gas stream between the two areas is neither a feed nor a product.
    MercuryStreamResult gasBetweenAreas = report.getStream("HP separator.gasOutStream");
    assertFalse(gasBetweenAreas.isFeed());
    assertFalse(gasBetweenAreas.isProduct());
    assertEquals("HP separator", gasBetweenAreas.getSourceUnit());
    assertEquals("solid", report.getUnit("cold separator").getFreeMercuryState());
    double feedMercury = report.getFeedMercuryFlowRate("g/day");
    assertEquals(report.getStream("feed").getMercuryFlowRate("g/day"), feedMercury, 1.0e-9);
    assertEquals(feedMercury,
        report.getProductMercuryFlowRate("g/day") + report.getFreeMercuryAccumulationRate("g/day"),
        1.0e-5 * feedMercury);
  }

  @Test
  void processWithoutMercury() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);
    Separator separator = new Separator("separator", feed);
    process.add(separator);
    process.run();
    double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");

    MercuryProcessAnalyzer.setFreeMercuryAccumulation(process, true);
    process.run();
    assertEquals(gasFlow, separator.getGasOutStream().getFlowRate("kg/hr"), 1.0e-9);
    assertEquals("none", separator.getFreeMercuryState());

    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);
    assertEquals(3, report.getStreams().size());
    assertEquals(MercuryStreamResult.STATUS_NO_MERCURY, report.getStream("feed").getStatus());
    assertEquals(0, report.getStream("feed").getNumberOfPhases());
    assertEquals(1000.0, report.getStream("feed").getMassFlowRate(), 1.0e-6);
    assertEquals(0.0, report.getFeedMercuryFlowRate("kg/yr"), 0.0);
    assertEquals(0.0, report.getMaximumSaturationRatio(), 0.0);
    assertFalse(report.hasFreeMercury());
  }

  @Test
  void reportAsJsonAndInvalidInput() {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed", createFeedFluid(5.0e-8));
    feed.setFlowRate(100000.0, "kg/hr");
    process.add(feed);
    Separator separator = new Separator("separator", feed);
    process.add(separator);
    process.run();
    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(process);

    String json = report.toJson();
    assertTrue(json.contains("\"mercuryBalance\""));
    assertTrue(json.contains("\"feed_g_day\""));
    assertTrue(json.contains("\"mercury_ug_Sm3\""));
    assertTrue(json.contains("\"mercury_ug_kg\""));
    assertTrue(json.contains("\"unit\": \"separator\""));

    MercuryStreamResult feedResult = report.getStream("feed");
    assertEquals(MercuryStreamResult.STATUS_OK, feedResult.getStatus());
    assertEquals(333.15, feedResult.getTemperature(), 1.0e-9);
    assertEquals(60.0, feedResult.getPressure(), 1.0e-9);
    assertEquals(-1, feedResult.getPhaseNumber("solid"));
    assertTrue(Double.isNaN(feedResult.getMercuryConcentration("solid", "ug/kg")));
    assertEquals(feedResult.getMercuryMoleFraction(0) * 1.0e9, feedResult.getMercuryConcentration(0, "ppb"), 1.0e-9);
    assertEquals(feedResult.getMercuryFlowRate("kg/day") * 365.0, feedResult.getMercuryFlowRate("kg/yr"), 1.0e-9);

    assertThrows(IllegalArgumentException.class, () -> report.getStream("no such stream"));
    assertThrows(IllegalArgumentException.class, () -> report.getUnit("no such unit"));
    assertThrows(IllegalArgumentException.class, () -> feedResult.getMercuryFlowRate("litre/day"));
    assertThrows(IllegalArgumentException.class, () -> separator.getFreeMercuryAccumulationRate("litre/day"));
    assertThrows(IllegalArgumentException.class, () -> MercuryProcessAnalyzer.analyze((ProcessSystem) null));
    assertThrows(IllegalArgumentException.class, () -> MercuryProcessAnalyzer.analyze((ProcessModel) null));
    assertThrows(IllegalArgumentException.class,
        () -> MercuryProcessAnalyzer.setFreeMercuryAccumulation((ProcessSystem) null, true));
    assertThrows(IllegalArgumentException.class,
        () -> MercuryProcessAnalyzer.setFreeMercuryAccumulation((ProcessModel) null, true));
  }

  @Test
  void jsonProcessUsesTheMercuryModelAndFreeMercuryAccumulation() {
    String json = "{\"fluid\":{\"model\":\"UMRPRU\",\"temperature\":228.15,\"pressure\":60.0,"
        + "\"components\":{\"methane\":0.90,\"ethane\":0.06,\"propane\":0.03,\"n-heptane\":0.01,"
        + "\"mercury\":5.0e-9}},\"process\":["
        + "{\"type\":\"Stream\",\"name\":\"feed\",\"properties\":{\"flowRate\":[50000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"cold separator\",\"inlet\":\"feed\","
        + "\"properties\":{\"freeMercuryAccumulation\":true}}]}";
    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), result.getErrors().toString());
    ProcessSystem process = result.getProcessSystem();

    // The same process built in Java with the UMR-PRU mixing rule.
    SystemInterface fluid = new SystemUMRPRUMCEos(228.15, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.01);
    fluid.addComponent("mercury", 5.0e-9);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    Separator separator = new Separator("cold separator", feed);
    separator.setFreeMercuryAccumulation(true);
    ProcessSystem reference = new ProcessSystem();
    reference.add(feed);
    reference.add(separator);
    reference.run();

    Separator jsonSeparator = (Separator) process.getUnit("cold separator");
    assertEquals("solid", jsonSeparator.getFreeMercuryState());
    assertTrue(separator.getFreeMercuryAccumulationRate("g/day") > 0.0);
    assertEquals(separator.getFreeMercuryAccumulationRate("g/day"),
        jsonSeparator.getFreeMercuryAccumulationRate("g/day"),
        1.0e-6 * separator.getFreeMercuryAccumulationRate("g/day"));
    assertEquals(
        MercuryProcessAnalyzer.analyze(reference).getStream("gasOutStream").getMercuryConcentration("gas", "ug/Sm3"),
        MercuryProcessAnalyzer.analyze(process).getStream("gasOutStream").getMercuryConcentration("gas", "ug/Sm3"),
        1.0e-6);
  }

  @Test
  void jsonProcessConvertsAnE300FluidForTheMercuryStudy() {
    String json = "{\"fluid\":{\"e300FilePath\":\"src/test/java/neqsim/thermo/util/readwrite/fluid1.e300\","
        + "\"model\":\"UMRPRU\",\"mercuryContent\":[100.0,\"ug/Sm3\"],\"temperature\":333.15,\"pressure\":60.0},"
        + "\"process\":["
        + "{\"type\":\"Stream\",\"name\":\"feed\",\"properties\":{\"flowRate\":[200000.0,\"kg/hr\"]}},"
        + "{\"type\":\"Separator\",\"name\":\"inlet separator\",\"inlet\":\"feed\","
        + "\"properties\":{\"freeMercuryAccumulation\":true}}]}";
    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), result.getErrors().toString());

    MercuryProcessReport report = MercuryProcessAnalyzer.analyze(result.getProcessSystem());
    MercuryStreamResult feed = report.getStream("feed");
    assertEquals(MercuryStreamResult.STATUS_OK, feed.getStatus());
    assertEquals(2, feed.getNumberOfPhases());
    assertFalse(feed.hasFreeMercury());
    // The mercury content is per standard cubic metre of the whole fluid; most of it is in the oil.
    assertTrue(feed.getMercuryConcentration("gas", "ug/Sm3") > 5.0);
    assertTrue(feed.getMercuryConcentration("gas", "ug/Sm3") < 100.0);
    assertTrue(feed.getPhaseMercuryFlowRate(feed.getPhaseNumber("oil"), "g/day") > feed
        .getPhaseMercuryFlowRate(feed.getPhaseNumber("gas"), "g/day"));
    assertEquals(report.getFeedMercuryFlowRate("g/day"), report.getProductMercuryFlowRate("g/day"),
        1.0e-6 * report.getFeedMercuryFlowRate("g/day"));
  }
}
