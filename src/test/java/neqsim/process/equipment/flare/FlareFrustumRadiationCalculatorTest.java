package neqsim.process.equipment.flare;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FlareFrustumRadiationCalculator}.
 */
public class FlareFrustumRadiationCalculatorTest {
  /**
   * A calm case should give an almost-vertical flame and a finite radiant flux.
   */
  @Test
  void testCalmCaseNearVertical() {
    FlareFrustumRadiationCalculator calc = new FlareFrustumRadiationCalculator();
    calc.setDuty(50.0, 45.0e6, 0.20);
    calc.setFlameGeometry(60.0, 120.0, 0.0, 100.0);
    calc.setReceptor(150.0, 1.5, 1.0, 6300.0);
    calc.calcRadiation();

    assertTrue(calc.getFlameTiltDegrees() < 1.0, "No wind should give a near-vertical flame");
    assertTrue(calc.getRadiantHeatFlux() > 0.0, "Radiant flux should be positive");
    assertTrue(calc.getRadiatedPower() > 0.0, "Radiated power should be positive");
    assertNotNull(calc.toJson());
  }

  /**
   * A strong crosswind should tilt the flame and move the centroid downwind.
   */
  @Test
  void testWindTiltsFlame() {
    FlareFrustumRadiationCalculator calm = new FlareFrustumRadiationCalculator();
    calm.setDuty(50.0, 45.0e6, 0.20);
    calm.setFlameGeometry(60.0, 60.0, 1.0, 100.0);
    calm.setReceptor(150.0, 1.5, 1.0, 6300.0);
    calm.calcRadiation();

    FlareFrustumRadiationCalculator windy = new FlareFrustumRadiationCalculator();
    windy.setDuty(50.0, 45.0e6, 0.20);
    windy.setFlameGeometry(60.0, 60.0, 60.0, 100.0);
    windy.setReceptor(150.0, 1.5, 1.0, 6300.0);
    windy.calcRadiation();

    assertTrue(windy.getFlameTiltDegrees() > calm.getFlameTiltDegrees(),
        "Higher wind should tilt the flame more");
    assertTrue(windy.getCentroidHorizontal() > calm.getCentroidHorizontal(),
        "Wind should move the centroid downwind");
  }

  /**
   * A close receptor should exceed the allowable flux, while a far receptor should pass.
   */
  @Test
  void testAllowableFluxCheck() {
    FlareFrustumRadiationCalculator near = new FlareFrustumRadiationCalculator();
    near.setDuty(200.0, 45.0e6, 0.30);
    near.setFlameGeometry(80.0, 120.0, 10.0, 60.0);
    near.setReceptor(20.0, 1.5, 1.0, 6300.0);
    near.calcRadiation();

    FlareFrustumRadiationCalculator far = new FlareFrustumRadiationCalculator();
    far.setDuty(200.0, 45.0e6, 0.30);
    far.setFlameGeometry(80.0, 120.0, 10.0, 60.0);
    far.setReceptor(400.0, 1.5, 1.0, 6300.0);
    far.calcRadiation();

    assertTrue(near.getRadiantHeatFlux() > far.getRadiantHeatFlux(),
        "Closer receptor should see higher flux");
    assertTrue(far.isWithinAllowable(), "Far receptor should be within the allowable flux");
  }

  /**
   * The {@code fromFlare} bridge should populate mass flow and lower heating value from a run
   * process flare.
   */
  @Test
  void testFromProcessFlare() {
    neqsim.thermo.system.SystemSrkEos fluid = new neqsim.thermo.system.SystemSrkEos(298.15, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(5.0, "bara");
    Flare flare = new Flare("flare", feed);
    neqsim.process.processmodel.ProcessSystem process =
        new neqsim.process.processmodel.ProcessSystem();
    process.add(feed);
    process.add(flare);
    process.run();

    FlareFrustumRadiationCalculator calc = new FlareFrustumRadiationCalculator();
    calc.fromFlare(flare);
    calc.setFlameGeometry(60.0, 120.0, 5.0, 100.0);
    calc.setReceptor(150.0, 1.5, 1.0, 6300.0);
    calc.calcRadiation();

    assertTrue(calc.getTotalHeatRelease() > 0.0,
        "Total heat release should be populated from the flare");
    assertTrue(calc.getRadiantHeatFlux() > 0.0, "Radiant flux should be positive");
    assertTrue(calc.getRadiatedPower() > 0.0, "Radiated power should be positive");
    assertNotNull(calc.toJson());
  }
}
