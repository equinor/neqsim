package neqsim.process.hydrogen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.AutothermalReformer;
import neqsim.process.equipment.reactor.PartialOxidationReactor;
import neqsim.process.equipment.reactor.ReformerFurnace;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for hydrogen-production plant builder templates.
 */
public class HydrogenPlantBuilderTest extends neqsim.NeqSimTest {

  @Test
  public void testSmrBuilderCreatesRunnableTemplate() {
    ProcessSystem process = new SMRHydrogenPlantBuilder().setName("SMR test")
        .setMethaneFeedMolePerSec(5.0).setIncludePsa(false).build();

    assertNotNull(process.getUnit("SMR test reformer furnace"));
    process.run();

    ReformerFurnace furnace = (ReformerFurnace) process.getUnit("SMR test reformer furnace");
    assertNotNull(furnace.getSyngasOutStream());
    assertTrue(furnace.getTubeHeatDemandKW() >= 0.0);
  }

  @Test
  public void testAtrBuilderCreatesRunnableTemplate() {
    ProcessSystem process = new ATRHydrogenPlantBuilder().setName("ATR test")
        .setMethaneFeedMolePerSec(5.0).setIncludePsa(false).build();

    assertNotNull(process.getUnit("ATR test autothermal reformer"));
    process.run();

    AutothermalReformer reformer =
        (AutothermalReformer) process.getUnit("ATR test autothermal reformer");
    assertTrue(reformer.getMethaneConversion() >= 0.0);
    assertTrue(reformer.getSootRiskIndex() >= 0.0 && reformer.getSootRiskIndex() <= 1.0);
  }

  @Test
  public void testPoxBuilderCreatesRunnableTemplate() {
    ProcessSystem process =
        new POXHydrogenPlantBuilder().setName("POX test").setMethaneFeedMolePerSec(5.0).build();

    assertNotNull(process.getUnit("POX test partial oxidation"));
    process.run();

    PartialOxidationReactor reactor =
        (PartialOxidationReactor) process.getUnit("POX test partial oxidation");
    assertTrue(reactor.getMethaneConversion() >= 0.0);
    assertNotNull(reactor.getQuenchSection());
  }

  @Test
  public void testBlueHydrogenBuilderExposesCaptureReadiness() {
    BlueHydrogenPlantBuilder builder = new BlueHydrogenPlantBuilder().setCo2CaptureFraction(0.92)
        .setMethaneFeedMolePerSec(5.0).setIncludePsa(true);
    ProcessSystem process = builder.build();

    assertNotNull(process.getUnit("Blue Hydrogen Plant reformer furnace"));
    assertNotNull(process.getUnit("Blue Hydrogen Plant high temperature shift"));
    assertNotNull(process.getUnit("Blue Hydrogen Plant CO2 capture"));
    assertNotNull(process.getUnit("Blue Hydrogen Plant CO2 export compressor"));
    assertNotNull(process.getUnit("Blue Hydrogen Plant H2 export compressor"));
    assertTrue(builder.getCaptureReadinessSummary().contains("0.92"));
    process.run();
    assertTrue(builder.getHydrogenProductMassFlowKgPerHour() > 0.0);
    assertTrue(builder.getCapturedCo2MassFlowKgPerHour() > 0.0);
  }
}
