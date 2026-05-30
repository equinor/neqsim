package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Validates that the low-flow bypass feature works end-to-end on a {@link ProcessModel} composed of
 * multiple {@link ProcessSystem} areas — mirroring the
 * {@code task_solve/testosb/process_model.ipynb} pattern where a manifold {@link Splitter} feeds
 * parallel compressor trains (active export branch + an "ht injection compressors" branch that is
 * frequently turned off by setting an injection rate close to zero).
 *
 * <p>
 * Demonstrates three configuration approaches:
 * </p>
 * <ol>
 * <li><b>Auto-bypass</b> via {@code htTrain.setSectionLowFlowThreshold(threshold_kg_hr)} — every
 * unit in the area whose inlet mass flow falls below the threshold deactivates itself for the
 * current run.</li>
 * <li><b>Manual lock</b> via {@code unit.setLockedInactive(true)} — direct per-unit hard
 * bypass.</li>
 * <li><b>Manual section</b> via {@code plant.deactivateSection(area, startUnit)} — traverses
 * registered {@link ProcessConnection} MATERIAL edges and locks every downstream unit.</li>
 * </ol>
 */
public class ProcessModelLowFlowBypassParallelTrainsTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  /**
   * Builds a 2-stage compressor area directly off a splitter outlet (no Stream wrapper — wrapping
   * would snapshot the splitter's initial state and decouple it from subsequent runs).
   */
  private static ProcessSystem buildCompressorTrain(String areaName, StreamInterface feed,
      double outletPressureBara) {
    ProcessSystem area = new ProcessSystem();
    area.setName(areaName);

    Compressor k1 = new Compressor(areaName + "_K1", feed);
    k1.setOutletPressure(outletPressureBara * 0.5);
    k1.setIsentropicEfficiency(0.78);

    Heater ic = new Heater(areaName + "_IC", k1.getOutletStream());
    ic.setOutTemperature(308.15);

    Compressor k2 = new Compressor(areaName + "_K2", ic.getOutletStream());
    k2.setOutletPressure(outletPressureBara);
    k2.setIsentropicEfficiency(0.78);

    area.add(k1);
    area.add(ic);
    area.add(k2);
    return area;
  }

  @Test
  public void splitterFractionToHtTrainNearZeroAutoBypassesEveryUnitInThatTrain() {
    double totalFlow = 200000.0; // kg/hr
    double htFraction = 1.0e-6; // mirrors injection_gas_rate_ht ~ 0.001 MSm3/day in testosb
    double exportFraction = 1.0 - htFraction;

    Stream feed = new Stream("feed", makeGas(totalFlow));
    Splitter manifold = new Splitter("manifold", feed, 2);
    manifold.setSplitFactors(new double[] {exportFraction, htFraction});

    ProcessSystem manifoldArea = new ProcessSystem();
    manifoldArea.setName("manifold");
    manifoldArea.add(feed);
    manifoldArea.add(manifold);

    ProcessSystem exportTrain = buildCompressorTrain("export", manifold.getSplitStream(0), 150.0);
    ProcessSystem htTrain =
        buildCompressorTrain("ht_injection_compressors", manifold.getSplitStream(1), 350.0);

    htTrain.setSectionLowFlowThreshold(1.0);

    ProcessModel plant = new ProcessModel();
    plant.add("manifold", manifoldArea);
    plant.add("export", exportTrain);
    plant.add("ht_injection_compressors", htTrain);
    plant.run();

    StreamInterface htSplit = manifold.getSplitStream(1);
    assertTrue(htSplit.getFlowRate("kg/hr") < 1.0,
        "HT split flow should be near zero, got " + htSplit.getFlowRate("kg/hr"));

    Compressor htK1 = (Compressor) htTrain.getUnit("ht_injection_compressors_K1");
    Heater htIC = (Heater) htTrain.getUnit("ht_injection_compressors_IC");
    Compressor htK2 = (Compressor) htTrain.getUnit("ht_injection_compressors_K2");
    assertFalse(htK1.isActive(), "HT K1 should be auto-bypassed");
    assertFalse(htIC.isActive(), "HT intercooler should be auto-bypassed");
    assertFalse(htK2.isActive(), "HT K2 should be auto-bypassed");

    Compressor expK2 = (Compressor) exportTrain.getUnit("export_K2");
    assertTrue(expK2.isActive(), "Export K2 should be active");
    StreamInterface expOut = expK2.getOutletStream();
    assertEquals(150.0, expOut.getPressure("bara"), 0.5);
    assertEquals(totalFlow * exportFraction, expOut.getFlowRate("kg/hr"), totalFlow * 1e-3);
  }

  @Test
  public void manualLockedInactiveTurnsOffEveryUnitInTheTrain() {
    double totalFlow = 100000.0;
    Stream feed = new Stream("feed", makeGas(totalFlow));
    Splitter manifold = new Splitter("manifold", feed, 2);
    manifold.setSplitFactors(new double[] {0.5, 0.5});

    ProcessSystem manifoldArea = new ProcessSystem();
    manifoldArea.add(feed);
    manifoldArea.add(manifold);

    ProcessSystem exportTrain = buildCompressorTrain("export", manifold.getSplitStream(0), 150.0);
    ProcessSystem htTrain = buildCompressorTrain("ht", manifold.getSplitStream(1), 350.0);

    ProcessModel plant = new ProcessModel();
    plant.add("manifold", manifoldArea);
    plant.add("export", exportTrain);
    plant.add("ht_injection_compressors", htTrain);
    plant.run();

    assertTrue(((Compressor) htTrain.getUnit("ht_K1")).isActive());

    htTrain.getUnit("ht_K1").setLockedInactive(true);
    htTrain.getUnit("ht_IC").setLockedInactive(true);
    htTrain.getUnit("ht_K2").setLockedInactive(true);

    plant.run();
    assertFalse(((Compressor) htTrain.getUnit("ht_K1")).isActive());
    assertFalse(((Heater) htTrain.getUnit("ht_IC")).isActive());
    assertFalse(((Compressor) htTrain.getUnit("ht_K2")).isActive());

    Compressor expK2 = (Compressor) exportTrain.getUnit("export_K2");
    assertTrue(expK2.isActive());
    assertEquals(150.0, expK2.getOutletStream().getPressure("bara"), 0.5);

    plant.activateAll();
    plant.run();
    assertTrue(((Compressor) htTrain.getUnit("ht_K1")).isActive());
  }

  @Test
  public void deactivateSectionWithProcessConnectionsLocksDownstreamUnits() {
    double totalFlow = 80000.0;
    Stream feed = new Stream("feed", makeGas(totalFlow));
    Splitter manifold = new Splitter("manifold", feed, 2);
    manifold.setSplitFactors(new double[] {0.5, 0.5});

    ProcessSystem manifoldArea = new ProcessSystem();
    manifoldArea.add(feed);
    manifoldArea.add(manifold);

    ProcessSystem htTrain = buildCompressorTrain("ht", manifold.getSplitStream(1), 350.0);

    ProcessModel plant = new ProcessModel();
    plant.add("manifold", manifoldArea);
    plant.add("ht_injection_compressors", htTrain);
    plant.run();

    // deactivateSection() traverses MATERIAL ProcessConnection edges AND stream wiring
    // (outlet of one unit == inlet of another). No explicit connect() needed for the latter.
    int locked = plant.deactivateSection("ht_injection_compressors", "ht_K1");
    assertTrue(locked >= 3,
        "expected K1 + IC + K2 to be locked via stream-wiring traversal, got " + locked);
    assertTrue(htTrain.getUnit("ht_K1").isLockedInactive());
    assertTrue(htTrain.getUnit("ht_IC").isLockedInactive());
    assertTrue(htTrain.getUnit("ht_K2").isLockedInactive());

    plant.run();
    assertFalse(((Compressor) htTrain.getUnit("ht_K2")).isActive());
  }

  @Test
  public void setFlowRatesPatternMirrorsTestosbInjectionConfig() {
    // testosb sets: tex_gas_splitter.setFlowRates([-1.0, injection_gas_rate_ht + 1e-6], 'MSm3/day')
    // The -1.0 on the export branch means "absorb whatever the other branches do not consume".
    double totalFlow = 50000.0;
    Stream feed = new Stream("feed", makeGas(totalFlow));
    Splitter manifold = new Splitter("manifold", feed, 2);
    manifold.setFlowRates(new double[] {-1.0, 0.5}, "kg/hr"); // 0.5 kg/hr to HT branch

    ProcessSystem manifoldArea = new ProcessSystem();
    manifoldArea.add(feed);
    manifoldArea.add(manifold);

    ProcessSystem htTrain = buildCompressorTrain("ht", manifold.getSplitStream(1), 350.0);
    htTrain.setSectionLowFlowThreshold(1.0);

    ProcessModel plant = new ProcessModel();
    plant.add("manifold", manifoldArea);
    plant.add("ht", htTrain);
    plant.run();

    assertEquals(0.5, manifold.getSplitStream(1).getFlowRate("kg/hr"), 1e-6);
    assertFalse(((Compressor) htTrain.getUnit("ht_K1")).isActive());
    assertEquals(totalFlow - 0.5, manifold.getSplitStream(0).getFlowRate("kg/hr"), 1e-3);
  }

  /**
   * Mirrors the exact structure used by {@code task_solve/testosb/process_model.ipynb}: an upstream
   * {@code tex_gas_splitter} routes a tiny fraction (injection_gas_rate_ht ≈ 0.001 MSm3/day) to a
   * second {@code manifold_upstream_ht_injection_compressors} which then splits that tiny stream
   * across two parallel HT injection trains (A: 99.99%, B: 0.01%). With
   * {@link ProcessSystem#setSectionLowFlowThreshold(double)} applied to both HT trains, train B
   * (which receives a near-zero stream) must auto-bypass while train A and the export branch run
   * normally and the full {@link ProcessModel} converges.
   */
  @Test
  public void dualHtTrainsMirrorsOsebergTestosbNotebookStructure() {
    double totalFlow = 500000.0; // kg/hr — main feed
    double htFractionOfTotal = 5e-6; // testosb: 0.001 MSm3/day out of ~MSm3/day-scale feed
    double aSplit = 0.9999; // injection_gas_rate_ht_split_to_train_A
    double bSplit = 1.0 - aSplit;

    Stream feed = new Stream("feed", makeGas(totalFlow));

    // Upstream tex_gas_splitter: export vs HT injection (Pattern A)
    Splitter texSplitter = new Splitter("tex_gas_splitter", feed, 2);
    texSplitter.setSplitFactors(new double[] {1.0 - htFractionOfTotal, htFractionOfTotal});
    ProcessSystem texArea = new ProcessSystem();
    texArea.setName("tex process");
    texArea.add(feed);
    texArea.add(texSplitter);

    // HT manifold: split HT stream A/B
    Splitter htManifold = new Splitter("manifold", texSplitter.getSplitStream(1), 2);
    htManifold.setSplitFactors(new double[] {aSplit, bSplit});
    ProcessSystem htManifoldArea = new ProcessSystem();
    htManifoldArea.setName("ht injection compressor manifold");
    htManifoldArea.add(htManifold);

    // Parallel HT trains A and B
    ProcessSystem htA = buildCompressorTrain("HT_A", htManifold.getSplitStream(0), 350.0);
    ProcessSystem htB = buildCompressorTrain("HT_B", htManifold.getSplitStream(1), 350.0);

    // Drop-in snippet from the testosb notebook documentation
    htA.setSectionLowFlowThreshold(1.0);
    htB.setSectionLowFlowThreshold(1.0);

    // Export train consumes the bulk
    ProcessSystem exportTrain =
        buildCompressorTrain("export", texSplitter.getSplitStream(0), 150.0);

    ProcessModel plant = new ProcessModel();
    plant.add("tex process", texArea);
    plant.add("ht injection compressor manifold", htManifoldArea);
    plant.add("HT injection process A", htA);
    plant.add("HT injection process B", htB);
    plant.add("export", exportTrain);
    plant.run();

    // Train B should be auto-bypassed (received ~totalFlow * 5e-6 * 1e-4 << 1 kg/hr)
    assertFalse(((Compressor) htB.getUnit("HT_B_K1")).isActive(),
        "HT train B K1 should be auto-bypassed (feed << 1 kg/hr)");
    assertFalse(((Compressor) htB.getUnit("HT_B_K2")).isActive(),
        "HT train B K2 should be auto-bypassed");

    // Train A may or may not bypass depending on htFractionOfTotal*aSplit*totalFlow; here it
    // receives ~2.5 kg/hr which is above the 1 kg/hr threshold, so it runs.
    double htAFeed = htManifold.getSplitStream(0).getFlowRate("kg/hr");
    if (htAFeed >= 1.0) {
      assertTrue(((Compressor) htA.getUnit("HT_A_K1")).isActive(),
          "HT train A should run (feed " + htAFeed + " kg/hr above threshold)");
    }

    // Export branch must still run accurately — total mass balance preserved.
    Compressor expK2 = (Compressor) exportTrain.getUnit("export_K2");
    assertTrue(expK2.isActive(), "Export K2 must be active");
    assertEquals(150.0, expK2.getOutletStream().getPressure("bara"), 0.5);
    assertEquals(totalFlow * (1.0 - htFractionOfTotal),
        expK2.getOutletStream().getFlowRate("kg/hr"), totalFlow * 1e-3);
  }
}
