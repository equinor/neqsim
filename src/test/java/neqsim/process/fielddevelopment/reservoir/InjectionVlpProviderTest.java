package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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
  void bottomHolePressureIsWellheadPlusHydrostaticMinusFriction() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    double whp = 60.0;
    double bhp = provider.bottomHolePressure(1000.0, whp, 0.0, 0.0, 0.0);
    double hydrostatic = 1000.0 * 9.80665 * 3000.0 / 1.0e5;
    assertTrue(bhp - whp > 0.97 * hydrostatic && bhp - whp < 1.06 * hydrostatic,
        "BHP minus WHP " + (bhp - whp) + " bar against hydrostatic " + hydrostatic);
  }

  @Test
  void frictionGrowsWithRateAndHeadFollowsWellheadPressureOneToOne() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), 3000.0, 0.157).setTemperatures(15.0, 15.0);
    double low = provider.bottomHolePressure(2000.0, 50.0, 0.0, 0.0, 0.0);
    double high = provider.bottomHolePressure(10000.0, 50.0, 0.0, 0.0, 0.0);
    assertTrue(high < low - 1.0,
        "increased friction reduces the pressure delivered downhole at fixed wellhead pressure");
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
    assertTrue(bhpDeviated < bhpVertical, "longer measured length adds friction and reduces downhole pressure");
    assertTrue(bhpVertical - bhpDeviated < 6.0, "but the head is the same, so the difference stays small");
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
    assertTrue(result.getBhp(2, 0, 0, 0, 0) < result.getBhp(0, 0, 0, 0, 0));
    assertTrue(result.getBhp(1, 1, 0, 0, 0) > result.getBhp(1, 0, 0, 0, 0));
  }

  /**
   * A level surveyed interval has no hydrostatic head: pressure must fall by the Darcy-Weisbach loss.
   */
  @Test
  void horizontalWaterInjectionMatchesDarcyWeisbachPressureLoss() {
    double length = 1000.0;
    double diameter = 0.157;
    double rate = 5000.0;
    double whp = 100.0;
    SystemInterface reference = water();
    reference.setTemperature(288.15);
    reference.setPressure(1.01325);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double massRate = rate * reference.getDensity("kg/m3") / 86400.0;
    reference.setPressure(whp);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double density = reference.getDensity("kg/m3");
    double viscosity = reference.getViscosity("kg/msec");
    double velocity = massRate / (density * Math.PI * diameter * diameter / 4.0);
    double reynolds = density * velocity * diameter / viscosity;
    double inverseRootFriction = -1.8 * Math.log10(Math.pow(4.5e-5 / diameter / 3.7, 1.11) + 6.9 / reynolds);
    double expectedLoss = length / diameter * density * velocity * velocity
        / (2.0 * inverseRootFriction * inverseRootFriction * 1.0e5);

    InjectionVlpProvider provider = new InjectionVlpProvider(water(), new double[] {0.0, length},
        new double[] {0.0, 0.0}, new double[] {diameter}).setTemperatures(15.0, 15.0);
    double bhp = provider.bottomHolePressure(rate, whp, 0.0, 0.0, 0.0);
    assertTrue(bhp < whp, "friction cannot create pressure in a horizontal injection interval");
    assertEquals(expectedLoss, whp - bhp, 0.01 * expectedLoss,
        "the inlet-density analytical approximation allows one percent for liquid compressibility");
  }

  /**
   * Injection tables retain sub-wellhead pressures and decreasing rate dependence from the live provider.
   */
  @Test
  void horizontalInjectionTablePreservesTheProvidersPressures() {
    InjectionVlpProvider provider = new InjectionVlpProvider(water(), new double[] {0.0, 1000.0},
        new double[] {0.0, 0.0}, new double[] {0.157}).setTemperatures(15.0, 15.0);
    double[] rates = {2000.0, 5000.0};
    double[] pressures = {40.0, 100.0};
    VfpTableBuilder.Result result = VfpTableBuilder.injector(3, "WAT").setRates(rates).setOutletPressures(pressures)
        .setRateMonotoneMode(VfpTableBuilder.RateMonotoneMode.FULL).build(provider);
    for (int f = 0; f < rates.length; f++) {
      for (int t = 0; t < pressures.length; t++) {
        assertEquals(provider.bottomHolePressure(rates[f], pressures[t], 0.0, 0.0, 0.0), result.getBhp(f, t, 0, 0, 0),
            1.0e-9);
      }
    }
    assertTrue(result.getWarnings().isEmpty(), result.getWarnings().toString());
  }

  /**
   * Gas injection also dissipates pressure and rejects a rate whose pressure march leaves the positive domain.
   */
  @Test
  void horizontalGasInjectionLosesPressureAndCollapsedProfilesReturnNaN() {
    SystemInterface gas = new SystemSrkEos(288.15, 80.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    InjectionVlpProvider provider = new InjectionVlpProvider(gas, new double[] {0.0, 1000.0}, new double[] {0.0, 0.0},
        new double[] {0.1}).setTemperatures(15.0, 15.0);
    double low = provider.bottomHolePressure(1.0e5, 80.0, 0.0, 0.0, 0.0);
    double high = provider.bottomHolePressure(2.0e5, 80.0, 0.0, 0.0, 0.0);
    assertTrue(low > 0.0 && low < 80.0);
    assertTrue(high > 0.0 && high < low);
    assertTrue(Double.isNaN(provider.bottomHolePressure(1.0e8, 1.0, 0.0, 0.0, 0.0)));
  }

  @Test
  void invalidSurveyIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new InjectionVlpProvider(water(), new double[] {0.0, 0.0}, new double[] {0.0, 10.0}, new double[] {0.1}));
    assertThrows(IllegalArgumentException.class,
        () -> new InjectionVlpProvider(water(), new double[] {0.0, 10.0}, new double[] {0.0, 10.0}, new double[] {}));
  }

  /**
   * Both constructors retain validation when partially constructed providers cannot be subclassed.
   */
  @Test
  void invalidVerticalGeometryIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new InjectionVlpProvider(water(), 0.0, 0.157));
    assertThrows(IllegalArgumentException.class, () -> new InjectionVlpProvider(water(), -100.0, 0.157));
    assertThrows(IllegalArgumentException.class, () -> new InjectionVlpProvider(water(), 3000.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new InjectionVlpProvider(water(), 3000.0, -0.157));
  }
}
