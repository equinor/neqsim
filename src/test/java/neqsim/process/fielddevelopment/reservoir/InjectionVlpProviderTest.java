package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Tests the injection lift performance provider and its use in a VFPINJ table.
 *
 * @author ESOL
 * @version 1.0
 */
class InjectionVlpProviderTest {

  private SystemInterface water() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 20.0, 100.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    return fluid;
  }

  @Test
  void bottomHolePressureIsWellheadPlusHydrostaticPlusFriction() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    double whp = 60.0;
    double bhp = provider.bottomHolePressure(5000.0, whp, 0.0, 0.0, 0.0);
    double hydrostatic = 1000.0 * 9.80665 * 3000.0 / 1.0e5;
    assertTrue(bhp - whp > 0.97 * hydrostatic && bhp - whp < 1.06 * hydrostatic,
        "BHP minus WHP " + (bhp - whp) + " bar against hydrostatic " + hydrostatic);
  }

  @Test
  void frictionGrowsWithRateAndHeadFollowsWellheadPressureOneToOne() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    double low = provider.bottomHolePressure(2000.0, 50.0, 0.0, 0.0, 0.0);
    double high = provider.bottomHolePressure(10000.0, 50.0, 0.0, 0.0, 0.0);
    assertTrue(high > low + 1.0, "friction should increase with rate");
    double shifted = provider.bottomHolePressure(10000.0, 100.0, 0.0, 0.0, 0.0);
    assertEquals(50.0, shifted - high, 2.0,
        "an extra 50 bar of wellhead pressure adds 50 bar (plus a small density effect)");
  }

  @Test
  void deviatedSurveyUsesTrueVerticalDepthForTheHead() {
    double[] md = {0.0, 1000.0, 5000.0};
    double[] tvd = {0.0, 1000.0, 3000.0};
    InjectionVlpProvider deviated = new InjectionVlpProvider(water(), md, tvd, new double[] {0.157, 0.157})
        .setTemperatures(15.0, 15.0);
    InjectionVlpProvider vertical = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    double bhpDeviated = deviated.bottomHolePressure(4000.0, 50.0, 0.0, 0.0, 0.0);
    double bhpVertical = vertical.bottomHolePressure(4000.0, 50.0, 0.0, 0.0, 0.0);
    assertTrue(bhpDeviated > bhpVertical, "longer measured length adds friction");
    assertTrue(bhpDeviated - bhpVertical < 6.0, "but the head is the same, so the difference stays small");
  }

  @Test
  void densityFactorRaisesTheHead() {
    InjectionVlpProvider fresh = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    InjectionVlpProvider brine = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0)
        .setDensityFactor(1.026);
    double diff = brine.bottomHolePressure(3000.0, 50.0, 0.0, 0.0, 0.0)
        - fresh.bottomHolePressure(3000.0, 50.0, 0.0, 0.0, 0.0);
    assertEquals(0.026 * 1000.0 * 9.80665 * 3000.0 / 1.0e5, diff, 1.5);
  }

  @Test
  void buildsAVfpInjTable() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(10.0, 80.0);
    VfpTableBuilder.Result result = VfpTableBuilder.injector(3, "WAT").setDatumDepth(3000.0)
        .setRates(new double[] {1000.0, 5000.0, 10000.0}).setOutletPressures(new double[] {40.0, 100.0})
        .build(provider);
    String deck = result.toDeckString();
    assertTrue(deck.contains("VFPINJ"));
    assertTrue(result.getBhp(2, 0, 0, 0, 0) > result.getBhp(0, 0, 0, 0, 0));
    assertTrue(result.getBhp(1, 1, 0, 0, 0) > result.getBhp(1, 0, 0, 0, 0));
  }

  @Test
  void invalidSurveyIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new InjectionVlpProvider(water(), new double[] {0.0, 0.0}, new double[] {0.0, 10.0}, new double[] {0.1}));
    assertThrows(IllegalArgumentException.class,
        () -> new InjectionVlpProvider(water(), new double[] {0.0, 10.0}, new double[] {0.0, 10.0}, new double[] {}));
  }
}
