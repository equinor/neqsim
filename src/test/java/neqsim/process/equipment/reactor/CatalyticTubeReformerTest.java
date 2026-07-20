package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CatalyticTubeReformer}.
 */
public class CatalyticTubeReformerTest extends neqsim.NeqSimTest {
  @Test
  void testSteamMethaneReformingConversion() {
    SystemInterface feedFluid = new SystemSrkEos(650.0, 25.0);
    feedFluid.addComponent("methane", 1.0);
    feedFluid.addComponent("water", 3.0);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("reformer feed", feedFluid);
    feed.setFlowRate(100.0, "kmol/hr");
    feed.run();

    CatalyticTubeReformer reformer = new CatalyticTubeReformer("primary reformer", feed);
    reformer.setReformingTemperature(1123.15, "K");
    reformer.setPressureDrop(2.0);
    reformer.run();

    assertTrue(reformer.getMethaneConversion() > 0.75,
        "Hot steam-methane reforming should not remain trapped near the inlet composition");
    assertTrue(reformer.getMethaneConversion() < 0.90,
        "The equilibrium conversion should remain within the expected screening range");
    assertTrue(reformer.getHeatDuty("kW") > 0.0,
        "Steam-methane reforming and feed heating should require positive duty");
    assertEquals(3.0, reformer.getSteamToCarbonRatio(), 1.0e-8);
  }
}
