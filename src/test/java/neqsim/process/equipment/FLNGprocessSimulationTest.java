package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.ScrubColumn;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;
import neqsim.process.equipment.separator.CryogenicSeparator;
import neqsim.process.equipment.separator.EndFlash;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.LNGTank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration test for a complete FLNG liquefaction process using the five FLNG-specific equipment
 * classes:
 * <ul>
 * <li>{@link CryogenicSeparator} - feed quality verification</li>
 * <li>{@link ScrubColumn} - heavy HC removal before MCHE</li>
 * <li>{@link LNGHeatExchanger} - main cryogenic heat exchanger with MITA</li>
 * <li>{@link EndFlash} - N2 rejection and LNG spec check</li>
 * <li>{@link LNGTank} - LNG storage with BOG calculation</li>
 * </ul>
 *
 * <p>
 * Based on the Tanzania lean gas FLNG study: 3.5 MTPA, C3MR liquefaction.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class FLNGprocessSimulationTest {

  /**
   * Test the full FLNG process train from pre-cooling through LNG storage.
   *
   * <p>
   * Process flow: Lean gas feed -> CryogenicSeparator (feed quality check) -> Pre-cooling (C3) ->
   * MCHE cooling (MR) -> JT Valve -> End Flash (N2 rejection) -> LNG Tank (BOG)
   * </p>
   *
   * <p>
   * The scrub column is tested separately in {@link #testScrubColumnInProcess()}.
   * </p>
   */
  @Test
  void testFLNGProcessTrain() {
    // ---- 1. Create treated lean gas (post-AGRU, post-dehydration) ----
    SystemInterface leanGas = new SystemSrkEos(273.15 + 25.0, 55.0);
    leanGas.addComponent("methane", 0.958);
    leanGas.addComponent("ethane", 0.027);
    leanGas.addComponent("propane", 0.006);
    leanGas.addComponent("i-butane", 0.0015);
    leanGas.addComponent("n-butane", 0.002);
    leanGas.addComponent("nitrogen", 0.005);
    leanGas.addComponent("CO2", 0.0005);
    leanGas.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    // ---- 2. Feed stream ----
    Stream feedStream = new Stream("Lean Gas Feed", leanGas);
    feedStream.setFlowRate(400000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(55.0, "bara");
    process.add(feedStream);

    // ---- 3. Cryogenic feed quality check ----
    CryogenicSeparator cryoFeedCheck = new CryogenicSeparator("Feed Quality Check", feedStream);
    cryoFeedCheck.setMaxCO2MolFrac(0.005);
    cryoFeedCheck.setMaxWaterPpm(1.0);
    process.add(cryoFeedCheck);

    // ---- 4. Pre-cooling (C3 refrigerant stages) ----
    Cooler precool1 = new Cooler("Pre-cool Stage 1", cryoFeedCheck.getGasOutStream());
    precool1.setOutTemperature(273.15 - 5.0);
    process.add(precool1);

    Cooler precool2 = new Cooler("Pre-cool Stage 2", precool1.getOutletStream());
    precool2.setOutTemperature(273.15 - 20.0);
    process.add(precool2);

    Cooler precool3 = new Cooler("Pre-cool Stage 3", precool2.getOutletStream());
    precool3.setOutTemperature(273.15 - 35.0);
    process.add(precool3);

    // ---- 5. Main Cryogenic Heat Exchanger stages (MR cooling) ----
    Cooler mche1 = new Cooler("MCHE Stage 1", precool3.getOutletStream());
    mche1.setOutTemperature(273.15 - 80.0);
    process.add(mche1);

    Cooler mche2 = new Cooler("MCHE Stage 2", mche1.getOutletStream());
    mche2.setOutTemperature(273.15 - 120.0);
    process.add(mche2);

    Cooler mche3 = new Cooler("MCHE Stage 3", mche2.getOutletStream());
    mche3.setOutTemperature(273.15 - 157.0);
    process.add(mche3);

    // ---- 6. JT valve (letdown to near-atmospheric pressure) ----
    ThrottlingValve jtValve = new ThrottlingValve("JT Valve", mche3.getOutletStream());
    jtValve.setOutletPressure(1.1);
    process.add(jtValve);

    // ---- 7. End flash drum (N2 rejection) ----
    EndFlash endFlash = new EndFlash("End Flash Drum", jtValve.getOutletStream());
    endFlash.setMaxN2InLNG(0.01);
    process.add(endFlash);

    // ---- 8. LNG Tank (BOG calculation) ----
    LNGTank lngTank = new LNGTank("LNG Storage Tank", endFlash.getLiquidOutStream());
    lngTank.setInsulationType(LNGTank.InsulationType.MEMBRANE);
    lngTank.setTankSurfaceArea(12000.0);
    lngTank.setAmbientTemperature(35.0, "C");
    lngTank.setLNGInventory(80000000.0);
    lngTank.setStoragePressure(1.1);
    process.add(lngTank);

    // ---- RUN THE PROCESS ----
    process.run();

    // ---- PRINT PROCESS PROFILE ----
    System.out.println("\n===== FLNG Process Temperature Profile =====");
    System.out.println("Feed:        " + fmt(feedStream.getTemperature("C")) + " C, "
        + fmt(feedStream.getPressure("bara")) + " bara");
    System.out
        .println("Pre-cool 1:  " + fmt(precool1.getOutletStream().getTemperature("C")) + " C");
    System.out
        .println("Pre-cool 2:  " + fmt(precool2.getOutletStream().getTemperature("C")) + " C");
    System.out
        .println("Pre-cool 3:  " + fmt(precool3.getOutletStream().getTemperature("C")) + " C");
    System.out.println("MCHE-1:      " + fmt(mche1.getOutletStream().getTemperature("C")) + " C");
    System.out.println("MCHE-2:      " + fmt(mche2.getOutletStream().getTemperature("C")) + " C");
    System.out.println("MCHE-3:      " + fmt(mche3.getOutletStream().getTemperature("C")) + " C");
    double jtOutTemp = jtValve.getOutletStream().getTemperature("C");
    System.out.println("JT valve:    " + fmt(jtOutTemp) + " C, "
        + fmt(jtValve.getOutletStream().getPressure("bara")) + " bara");

    // ---- VERIFY RESULTS ----

    // 1. CryogenicSeparator should confirm clean feed
    System.out.println("\n===== Feed Quality Check =====");
    System.out.println("CO2 risk: " + cryoFeedCheck.hasCO2FreezeOutRisk());
    System.out.println("Water risk: " + cryoFeedCheck.hasWaterIceRisk());
    System.out.println("CO2 frac: " + String.format("%.6f", cryoFeedCheck.getCO2MolFrac()));

    // 2. JT valve should produce sub-zero temperatures
    assertTrue(jtOutTemp < 0.0, "JT valve outlet should be sub-zero, got: " + jtOutTemp + " C");

    // 3. End flash drum should track N2 and methane
    double n2InLNG = endFlash.getN2InLNGMolFrac();
    double ch4InLNG = endFlash.getMethaneInLNGMolFrac();
    double flashGasRatio = endFlash.getFlashGasRatio();
    System.out.println("\n===== End Flash Results =====");
    System.out.println("N2 in LNG:      " + String.format("%.4f", n2InLNG));
    System.out.println("CH4 in LNG:     " + String.format("%.4f", ch4InLNG));
    System.out.println("Flash gas ratio: " + String.format("%.4f", flashGasRatio));
    System.out.println("LNG spec met:    " + endFlash.isLNGSpecMet());

    assertTrue(n2InLNG >= 0.0 && n2InLNG <= 1.0, "N2 in LNG should be valid");
    assertTrue(flashGasRatio >= 0.0 && flashGasRatio <= 1.0, "Flash gas ratio should be valid");

    // 4. LNG Tank should calculate BOG
    double bogRate = lngTank.getBoilOffRatePctPerDay();
    double bogMassFlow = lngTank.getBOGMassFlowRate();
    double heatIngress = lngTank.getHeatIngress();
    System.out.println("\n===== LNG Tank Results =====");
    System.out.println("Heat ingress:   " + fmt(heatIngress / 1000.0) + " kW");
    System.out.println("BOG rate:       " + String.format("%.4f", bogRate) + " %/day");
    System.out.println("BOG flow:       " + fmt(bogMassFlow) + " kg/hr");
    System.out.println("Insulation:     " + lngTank.getInsulationType());

    assertTrue(bogRate >= 0.0, "BOG rate should be non-negative");

    // BOG and LNG product streams
    if (lngTank.getBOGStream() != null) {
      System.out
          .println("BOG stream T:   " + fmt(lngTank.getBOGStream().getTemperature("C")) + " C");
    }
    if (lngTank.getLNGProductStream() != null) {
      System.out.println(
          "LNG product T:  " + fmt(lngTank.getLNGProductStream().getTemperature("C")) + " C");
    }
  }

  /**
   * Test the scrub column integrated in a simplified process.
   */
  @Test
  void testScrubColumnInProcess() {
    // Feed gas with some heavy components at pre-cooling conditions
    SystemInterface feedFluid = new SystemSrkEos(273.15 - 20.0, 55.0);
    feedFluid.addComponent("methane", 0.90);
    feedFluid.addComponent("ethane", 0.05);
    feedFluid.addComponent("propane", 0.02);
    feedFluid.addComponent("n-butane", 0.015);
    feedFluid.addComponent("n-pentane", 0.01);
    feedFluid.addComponent("nitrogen", 0.005);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("Scrub Feed", feedFluid);
    feed.setFlowRate(400000.0, "kg/hr");
    feed.setTemperature(-20.0, "C");
    feed.setPressure(55.0, "bara");

    ScrubColumn scrubCol = new ScrubColumn("Scrub Column", 5, true, true);
    scrubCol.addFeedStream(feed, 3);
    scrubCol.setHeavyKeyComponent("n-pentane");
    scrubCol.setMaxHeavyKeyInOverhead(0.001);
    scrubCol.setMinimumBottomsTemperature(-50.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(scrubCol);
    process.run();

    // Column should produce gas and liquid outlets
    assertNotNull(scrubCol.getGasOutStream(), "Scrub column should produce overhead gas");
    assertNotNull(scrubCol.getLiquidOutStream(), "Scrub column should produce bottoms liquid");

    System.out.println("\n===== Scrub Column Results =====");
    System.out.println(
        "Heavy key overhead: " + String.format("%.6f", scrubCol.getHeavyKeyInOverheadMolFrac()));
    System.out.println("NGL recovery: " + String.format("%.4f", scrubCol.getNGLRecovery()));
    System.out.println("Freeze-out risk: " + scrubCol.hasFreezeOutRisk());
    System.out.println("Overhead T: " + fmt(scrubCol.getGasOutStream().getTemperature("C")) + " C");
    System.out
        .println("Bottoms T: " + fmt(scrubCol.getLiquidOutStream().getTemperature("C")) + " C");

    assertTrue(scrubCol.getHeavyKeyInOverheadMolFrac() >= 0.0,
        "Heavy key in overhead should be tracked");
  }

  /**
   * Format a double value for display.
   *
   * @param val value to format
   * @return formatted string
   */
  private static String fmt(double val) {
    return String.format("%.1f", val);
  }

  /**
   * Test the LNGHeatExchanger with two streams (hot process gas + cold MR).
   *
   * <p>
   * This tests the MITA and composite curve features specifically.
   * </p>
   */
  @Test
  void testLNGHeatExchangerInProcess() {
    // Hot stream: pre-cooled natural gas
    SystemInterface hotGas = new SystemSrkEos(273.15 - 35.0, 55.0);
    hotGas.addComponent("methane", 0.958);
    hotGas.addComponent("ethane", 0.027);
    hotGas.addComponent("propane", 0.006);
    hotGas.addComponent("nitrogen", 0.005);
    hotGas.addComponent("CO2", 0.004);
    hotGas.setMixingRule("classic");

    Stream hotStream = new Stream("NG to MCHE", hotGas);
    hotStream.setFlowRate(400000.0, "kg/hr");
    hotStream.setTemperature(-35.0, "C");
    hotStream.setPressure(55.0, "bara");

    // Cold stream: mixed refrigerant (simplified as methane-ethane-propane mix)
    SystemInterface mrFluid = new SystemSrkEos(273.15 - 160.0, 3.0);
    mrFluid.addComponent("methane", 0.35);
    mrFluid.addComponent("ethane", 0.35);
    mrFluid.addComponent("propane", 0.30);
    mrFluid.setMixingRule("classic");

    Stream coldStream = new Stream("MR Cold", mrFluid);
    coldStream.setFlowRate(500000.0, "kg/hr");
    coldStream.setTemperature(-160.0, "C");
    coldStream.setPressure(3.0, "bara");

    // Create MCHE
    LNGHeatExchanger mche = new LNGHeatExchanger("MCHE");
    mche.addInStream(hotStream);
    mche.addInStream(coldStream);
    mche.setTemperatureApproach(3.0);
    mche.setNumberOfZones(10);
    mche.setExchangerType("BAHX");

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);
    process.add(mche);
    process.run();

    // Check MITA calculation
    double mita = mche.getMITA();
    System.out.println("MCHE MITA: " + String.format("%.2f", mita) + " K");
    System.out.println("MCHE type: " + mche.getExchangerType());
    System.out.println("MCHE zones: " + mche.getNumberOfZones());

    // Hot stream should be cooled, cold stream should be heated
    double hotOutTemp = mche.getOutStream(0).getTemperature("C");
    double coldOutTemp = mche.getOutStream(1).getTemperature("C");
    System.out.println("Hot out T: " + String.format("%.1f", hotOutTemp) + " C");
    System.out.println("Cold out T: " + String.format("%.1f", coldOutTemp) + " C");

    // Composite curves should be populated
    double[][] hotCurve = mche.getHotCompositeCurve();
    double[][] coldCurve = mche.getColdCompositeCurve();
    assertNotNull(hotCurve, "Hot composite curve should be computed");
    assertNotNull(coldCurve, "Cold composite curve should be computed");
    assertTrue(hotCurve.length > 0, "Hot composite curve should have data points");
    assertTrue(coldCurve.length > 0, "Cold composite curve should have data points");

    // UA per zone should be populated
    double[] uaZones = mche.getUAPerZone();
    assertNotNull(uaZones, "UA per zone should be computed");

    // MITA per zone
    double[] mitaZones = mche.getMITAPerZone();
    assertNotNull(mitaZones, "MITA per zone should be computed");
  }

  /**
   * Test EndFlash with a typical LNG composition at near-atmospheric pressure.
   */
  @Test
  void testEndFlashN2Rejection() {
    SystemInterface lngFluid = new SystemSrkEos(273.15 - 162.0, 1.1);
    lngFluid.addComponent("methane", 0.93);
    lngFluid.addComponent("ethane", 0.03);
    lngFluid.addComponent("propane", 0.005);
    lngFluid.addComponent("nitrogen", 0.035);
    lngFluid.setMixingRule("classic");

    Stream lngIn = new Stream("Sub-cooled LNG", lngFluid);
    lngIn.setFlowRate(350000.0, "kg/hr");
    lngIn.setTemperature(-162.0, "C");
    lngIn.setPressure(1.1, "bara");

    EndFlash endFlash = new EndFlash("End Flash", lngIn);
    endFlash.setMaxN2InLNG(0.01);

    ProcessSystem process = new ProcessSystem();
    process.add(lngIn);
    process.add(endFlash);
    process.run();

    System.out.println("End Flash N2 rejection test:");
    System.out.println("  N2 in LNG: " + String.format("%.4f", endFlash.getN2InLNGMolFrac()));
    System.out
        .println("  N2 in flash gas: " + String.format("%.4f", endFlash.getN2InFlashGasMolFrac()));
    System.out.println("  CH4 in LNG: " + String.format("%.4f", endFlash.getMethaneInLNGMolFrac()));
    System.out.println("  Flash gas ratio: " + String.format("%.4f", endFlash.getFlashGasRatio()));
    System.out.println("  Spec met: " + endFlash.isLNGSpecMet());

    assertTrue(endFlash.getFlashGasRatio() >= 0.0, "Flash gas ratio should be non-negative");
    assertTrue(endFlash.getMethaneInLNGMolFrac() > 0.5,
        "Methane should be the major LNG component");
  }

  /**
   * Test LNG Tank BOG with different insulation types.
   */
  @Test
  void testLNGTankInsulationComparison() {
    SystemInterface lngFluid = new SystemSrkEos(273.15 - 162.0, 1.1);
    lngFluid.addComponent("methane", 0.95);
    lngFluid.addComponent("ethane", 0.03);
    lngFluid.addComponent("propane", 0.01);
    lngFluid.addComponent("nitrogen", 0.01);
    lngFluid.setMixingRule("classic");

    LNGTank.InsulationType[] types = {LNGTank.InsulationType.MEMBRANE, LNGTank.InsulationType.MOSS,
        LNGTank.InsulationType.PRISMATIC};

    System.out.println("\nLNG Tank Insulation Comparison:");
    System.out.println(String.format("%-12s  %-8s  %-12s  %-10s", "Type", "U (W/m2K)",
        "BOG (%/day)", "BOG (kg/hr)"));

    for (LNGTank.InsulationType type : types) {
      Stream lngIn = new Stream("LNG_" + type, lngFluid.clone());
      lngIn.setFlowRate(350000.0, "kg/hr");
      lngIn.setTemperature(-162.0, "C");
      lngIn.setPressure(1.1, "bara");

      LNGTank tank = new LNGTank("Tank_" + type, lngIn);
      tank.setInsulationType(type);
      tank.setTankSurfaceArea(12000.0);
      tank.setAmbientTemperature(35.0, "C");
      tank.setLNGInventory(80000000.0);

      ProcessSystem proc = new ProcessSystem();
      proc.add(lngIn);
      proc.add(tank);
      proc.run();

      System.out.println(String.format("%-12s  %-8.3f  %-12.4f  %-10.1f", type,
          tank.getOverallHeatTransferCoefficient(), tank.getBoilOffRatePctPerDay(),
          tank.getBOGMassFlowRate()));

      assertTrue(tank.getBoilOffRatePctPerDay() > 0.0, "BOG rate should be positive for " + type);
      assertTrue(tank.getHeatIngress() > 0.0, "Heat ingress should be positive for " + type);
    }
  }
}
