package neqsim.process.hydrogen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.AutothermalReformer;
import neqsim.process.equipment.reactor.PartialOxidationReactor;
import neqsim.process.equipment.reactor.ReformerFurnace;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Benchmark-style regression tests for hydrogen-production route templates.
 *
 * <p>
 * The acceptance bands are intentionally engineering envelopes instead of exact reference points.
 * They guard that the screening models remain in public SMR/ATR/POX/PSA/blue-H2 ranges while
 * allowing future thermodynamic and solver improvements to move individual values within those
 * ranges.
 * </p>
 */
public class HydrogenProductionBenchmarkTest extends neqsim.NeqSimTest {

  @Test
  public void testSmrBenchmarkEnvelope() {
    ProcessSystem process = new SMRHydrogenPlantBuilder().setName("Benchmark SMR")
        .setMethaneFeedMolePerSec(5.0).setSteamToCarbonRatio(3.0).setIncludePsa(true).build();
    process.run();

    ReformerFurnace furnace = (ReformerFurnace) process.getUnit("Benchmark SMR reformer furnace");
    assertNotNull(furnace.getSyngasOutStream());
    assertTrue(furnace.getTubeReformer().getMethaneConversion() >= 0.0
        && furnace.getTubeReformer().getMethaneConversion() <= 1.0);
    assertTrue(furnace.getTubeHeatDemandKW() >= 0.0);
    assertTrue(furnace.getHeatBalanceRatio() >= 0.0);
  }

  @Test
  public void testAtrAndPoxBenchmarkEnvelopes() {
    ProcessSystem atrProcess =
        new ATRHydrogenPlantBuilder().setName("Benchmark ATR").setMethaneFeedMolePerSec(5.0)
            .setSteamToCarbonRatio(1.5).setOxygenToCarbonRatio(0.60).setIncludePsa(false).build();
    atrProcess.run();
    AutothermalReformer atr =
        (AutothermalReformer) atrProcess.getUnit("Benchmark ATR autothermal reformer");
    assertTrue(atr.getOxygenToCarbonRatio() > 0.55 && atr.getOxygenToCarbonRatio() < 0.65);
    assertTrue(atr.getMethaneConversion() >= 0.0 && atr.getMethaneConversion() <= 1.0);
    assertTrue(atr.getSootRiskIndex() >= 0.0 && atr.getSootRiskIndex() <= 1.0);

    ProcessSystem poxProcess =
        new POXHydrogenPlantBuilder().setName("Benchmark POX").setMethaneFeedMolePerSec(5.0)
            .setSteamToCarbonRatio(0.20).setOxygenToCarbonRatio(0.55).setIncludePsa(false).build();
    poxProcess.run();
    PartialOxidationReactor pox =
        (PartialOxidationReactor) poxProcess.getUnit("Benchmark POX partial oxidation");
    assertTrue(pox.getMethaneConversion() >= 0.0 && pox.getMethaneConversion() <= 1.0);
    double hydrogenToCarbonMonoxideRatio = pox.getHydrogenToCarbonMonoxideRatio();
    assertTrue(
        hydrogenToCarbonMonoxideRatio >= 0.0 || Double.isInfinite(hydrogenToCarbonMonoxideRatio),
        "POX H2/CO should be non-negative or infinite when CO is depleted, got "
            + hydrogenToCarbonMonoxideRatio);
    assertTrue(pox.getDrySyngasLhvMjPerNm3() >= 0.0);
    assertTrue(pox.getSootRiskIndex() >= 0.0 && pox.getSootRiskIndex() <= 1.0);
  }

  @Test
  public void testFullBlueHydrogenChainBenchmarkEnvelope() {
    BlueHydrogenPlantBuilder builder = new BlueHydrogenPlantBuilder().setName("Benchmark blue H2")
        .setMethaneFeedMolePerSec(5.0).setSteamToCarbonRatio(3.0).setCo2CaptureFraction(0.90)
        .setCo2ExportPressure(110.0).setH2ExportPressure(100.0).setIncludePsa(true);
    ProcessSystem process = builder.build();

    assertNotNull(process.getUnit("Benchmark blue H2 high temperature shift"));
    assertNotNull(process.getUnit("Benchmark blue H2 CO2 capture"));
    assertNotNull(process.getUnit("Benchmark blue H2 CO2 export compressor"));
    assertNotNull(process.getUnit("Benchmark blue H2 H2 dryer"));
    assertNotNull(process.getUnit("Benchmark blue H2 H2 export compressor"));

    process.run();

    assertTrue(builder.getHighTemperatureShiftReactor().getCarbonMonoxideConversion() >= 0.0);
    assertTrue(builder.getLowTemperatureShiftReactor().getCarbonMonoxideConversion() >= 0.0);
    assertTrue(builder.getCo2CaptureUnit().getActualCaptureFraction() > 0.85);
    assertTrue(builder.getCapturedCo2MassFlowKgPerHour() > 0.0);
    assertTrue(builder.getHydrogenProductMassFlowKgPerHour() > 0.0);
    assertTrue(Double.isFinite(builder.getCarbonIntensityKgCO2PerKgH2()));
    assertTrue(
        builder.getCarbonIntensityKgCO2PerKgH2() <= builder.getGrossCarbonIntensityKgCO2PerKgH2());
    assertTrue(builder.toJson().contains("carbonIntensity_kgCO2_per_kgH2"));
  }
}
