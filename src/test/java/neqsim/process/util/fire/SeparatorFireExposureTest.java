package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.util.fire.SeparatorFireExposure.FireExposureResult;
import neqsim.process.util.fire.SeparatorFireExposure.FireScenarioConfig;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class SeparatorFireExposureTest {
  @Test
  public void evaluateFireExposureReturnsGeometryDrivenAreas() {
    SystemInterface system = new SystemSrkEos(300.0, 20.0);
    system.addComponent("methane", 90.0);
    system.addComponent("n-heptane", 10.0);
    system.setMixingRule(2);

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.setTemperature(35.0, "C");
    feed.setPressure(20.0, "bara");

    Separator separator = new Separator("test separator", feed);
    separator.setInternalDiameter(2.0);
    separator.setSeparatorLength(4.0);
    separator.setLiquidLevel(0.5); // 50% liquid height
    separator.run();

    FireScenarioConfig config = new FireScenarioConfig().setEnvironmentalFactor(0.82)
        .setWallThicknessM(0.018).setAllowableTensileStrengthPa(2.1e8);

    FireExposureResult result = separator.evaluateFireExposure(config);

    assertEquals(separator.getWettedArea(), result.wettedArea(), 1.0e-6);
    assertEquals(separator.getUnwettedArea(), result.unwettedArea(), 1.0e-6);
    assertTrue(result.poolFireHeatLoad() > 0.0);
    assertTrue(result.radiativeHeatFlux() > 0.0);
    assertTrue(result.wettedWall().outerWallTemperatureK() > separator.getTemperature() - 1.0e-9);
    assertTrue(result.vonMisesStressPa() > 0.0);
    assertEquals(result.ruptureMarginPa() > 0.0, !result.isRuptureLikely());
    assertFalse(Double.isNaN(result.unwettedWall().outerWallTemperatureK()));
  }

  @Test
  public void fireHeatDutyRaisesSeparatorTemperatureDuringTransient() {
    SystemInterface system = new SystemSrkEos(305.0, 15.0);
    system.addComponent("methane", 95.0);
    system.addComponent("n-butane", 5.0);
    system.setMixingRule(2);

    Stream feed = new Stream("feed", system);
    feed.setFlowRate(1500.0, "kg/hr");
    feed.setTemperature(32.0, "C");
    feed.setPressure(15.0, "bara");

    Separator separator = new Separator("fire test separator", feed);
    separator.setInternalDiameter(1.5);
    separator.setSeparatorLength(3.0);
    separator.setLiquidLevel(0.4);
    separator.run();

    FireScenarioConfig config = new FireScenarioConfig();
    FireExposureResult result = separator.evaluateFireExposure(config);

    double initialTemperature = separator.getThermoSystem().getTemperature();
    double timeStepSeconds = 5.0;

    separator.setCalculateSteadyState(false);
    separator.setDuty(result.totalFireHeat());
    separator.runTransient(timeStepSeconds, java.util.UUID.randomUUID());
    double newTemperature = separator.getThermoSystem().getTemperature();

    assertTrue(newTemperature > initialTemperature);
    assertEquals(result.totalFireHeat(), separator.getHeatInput(), 1.0e-9);
  }

  @Test
  public void flareRadiationOptionUsesActualHeatDuty() {
    SystemInterface system = new SystemSrkEos(310.0, 18.0);
    system.addComponent("methane", 100.0);
    system.setMixingRule(2);

    Stream feed = new Stream("flare feed", system);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(18.0, "bara");

    Separator separator = new Separator("radiation separator", feed);
    separator.setInternalDiameter(1.8);
    separator.setSeparatorLength(3.5);
    separator.setLiquidLevel(0.3);
    separator.run();

    Stream toFlare = new Stream("to flare", separator.getGasOutStream());
    Flare flare = new Flare("emergency flare", toFlare);
    flare.setRadiantFraction(0.2);
    flare.setFlameHeight(45.0);
    flare.run();

    double distanceM = 40.0;
    double expectedFlux = flare.estimateRadiationHeatFlux(distanceM);

    FireScenarioConfig config = new FireScenarioConfig();
    FireExposureResult base = separator.evaluateFireExposure(config);
    FireExposureResult withFlare = separator.evaluateFireExposure(config, flare, distanceM);

    assertTrue(withFlare.totalFireHeat() > base.totalFireHeat());
    assertEquals(expectedFlux, withFlare.flareRadiativeFlux(), 1.0e-6);
    assertTrue(withFlare.flareRadiativeHeat() > 0.0);
  }
}
