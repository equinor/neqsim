package neqsim.thermo.util.mineral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.mercury.MercuryFluidConverter;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Tests for {@link ReservoirMercuryEstimator}.
 *
 * @author esol
 * @version 1.0
 */
public class ReservoirMercuryEstimatorTest {

  /**
   * Creates a North Sea type gas condensate without H2S and mercury.
   *
   * @param temperatureC temperature [C]
   * @param pressure pressure [bara]
   * @param h2s H2S mole fraction, zero to leave H2S out
   * @param mercury mercury mole fraction, zero to leave mercury out
   * @return fluid
   */
  private static SystemInterface fluid(double temperatureC, double pressure, double h2s, double mercury) {
    SystemInterface f = new SystemSrkEos(273.15 + temperatureC, pressure);
    f.addComponent("nitrogen", 0.005);
    f.addComponent("CO2", 0.02);
    f.addComponent("methane", 0.78);
    f.addComponent("ethane", 0.08);
    f.addComponent("propane", 0.04);
    f.addComponent("n-butane", 0.025);
    f.addComponent("n-hexane", 0.05);
    if (h2s > 0.0) {
      f.addComponent("H2S", h2s);
    }
    if (mercury > 0.0) {
      f.addComponent("mercury", mercury);
    }
    f.setMixingRule("classic");
    return f;
  }

  /**
   * The pure-cinnabar limit is hundreds of microgram per Sm3 in a cool reservoir and rises steeply; in a hot
   * high-pressure reservoir it passes the liquid mercury limit, which then controls.
   */
  @Test
  public void testLimitsRiseWithTemperatureAndLiquidMercuryTakesOver() {
    ReservoirMercuryEstimator est = new ReservoirMercuryEstimator();
    ReservoirMercuryEstimator.Estimate cool = est.estimate(fluid(50.0, 150.0, 0.0, 0.0));
    ReservoirMercuryEstimator.Estimate warm = est.estimate(fluid(110.0, 350.0, 0.0, 0.0));
    ReservoirMercuryEstimator.Estimate hot = est.estimate(fluid(170.0, 800.0, 0.0, 0.0));
    assertTrue(cool.getSulfideLimit("ug/Sm3") > 100.0 && cool.getSulfideLimit("ug/Sm3") < 1000.0,
        "50 C: " + cool.getSulfideLimit("ug/Sm3"));
    assertTrue(warm.getSulfideLimit("ug/Sm3") > 1.0e4 && warm.getSulfideLimit("ug/Sm3") < 2.0e5,
        "110 C: " + warm.getSulfideLimit("ug/Sm3"));
    assertEquals("cinnabar", cool.getControllingPhase());
    assertEquals("liquid mercury", hot.getControllingPhase());
    assertEquals(hot.getLiquidLimitMoleFraction(), hot.getLimitMoleFraction(), 0.0);
    assertFalse(cool.hasMeasuredMercury());
    assertTrue(cool.getH2SPpm() > 0.01 && cool.getH2SPpm() < 1.0, "buffered H2S at 50 C: " + cool.getH2SPpm());
    assertTrue(cool.getSulfideLimitLog10Uncertainty() > 0.3 && cool.getSulfideLimitLog10Uncertainty() < 0.8);
  }

  /** The sulfide limit is proportional to the HgS activity and inversely proportional to the H2S fugacity. */
  @Test
  public void testActivityAndHydrogenSulfideScaling() {
    ReservoirMercuryEstimator pure = new ReservoirMercuryEstimator();
    ReservoirMercuryEstimator trace = new ReservoirMercuryEstimator();
    trace.setMercurySulfideActivity(0.003);
    SystemInterface f = fluid(90.0, 280.0, 0.0, 0.0);
    double limit = pure.estimate(f).getSulfideLimitMoleFraction();
    assertEquals(0.003 * limit, trace.estimate(f).getSulfideLimitMoleFraction(), 1.0e-9 * limit);

    ReservoirMercuryEstimator measuredH2S = new ReservoirMercuryEstimator();
    measuredH2S.setHydrogenSulfideBasis(ReservoirMercuryEstimator.HydrogenSulfideBasis.FLUID);
    double one = measuredH2S.estimate(fluid(120.0, 400.0, 1.0e-6, 0.0)).getSulfideLimitMoleFraction();
    double ten = measuredH2S.estimate(fluid(120.0, 400.0, 1.0e-5, 0.0)).getSulfideLimitMoleFraction();
    assertEquals(10.0, one / ten, 0.2);
    assertThrows(IllegalArgumentException.class, () -> measuredH2S.estimate(fluid(120.0, 400.0, 0.0, 0.0)));
  }

  /** Metacinnabar holds mercury less strongly than cinnabar: a higher limit by a factor of about two at 120 °C. */
  @Test
  public void testMetacinnabarGivesTheHigherLimit() {
    ReservoirMercuryEstimator cinnabar = new ReservoirMercuryEstimator();
    ReservoirMercuryEstimator meta = new ReservoirMercuryEstimator();
    meta.setSulfidePhase(ReservoirMercuryEstimator.SulfidePhase.METACINNABAR);
    SystemInterface f = fluid(120.0, 400.0, 0.0, 0.0);
    double ratio = meta.estimate(f).getSulfideLimitMoleFraction() / cinnabar.estimate(f).getSulfideLimitMoleFraction();
    assertTrue(ratio > 1.3 && ratio < 3.5, "metacinnabar over cinnabar: " + ratio);
  }

  /**
   * A fluid with 1 microgram per Sm3 at 50 °C lies far below the pure-cinnabar limit: the apparent HgS activity is of
   * the order 0.003, the value Bryndzia et al. (2022) needed to match a global data set.
   */
  @Test
  public void testMeasuredMercuryGivesSaturationIndexAndApparentActivity() {
    double z = 1.0 / ReservoirMercuryEstimator.convertMoleFraction(1.0, "ug/Sm3");
    assertEquals(8.4834, ReservoirMercuryEstimator.convertMoleFraction(1.0e-9, "ug/Sm3"), 1.0e-3);
    ReservoirMercuryEstimator est = new ReservoirMercuryEstimator();
    ReservoirMercuryEstimator.Estimate e = est.estimate(fluid(50.0, 150.0, 0.0, z));
    assertTrue(e.hasMeasuredMercury());
    assertEquals(z, e.getMeasuredMoleFraction(), 1.0e-6 * z);
    assertTrue(e.getSulfideSaturationIndex() < -2.0 && e.getSulfideSaturationIndex() > -3.0,
        "saturation index " + e.getSulfideSaturationIndex());
    assertEquals(e.getMeasuredMoleFraction() / e.getSulfideLimitMoleFraction(), e.getApparentMercurySulfideActivity(),
        1.0e-12);
    assertTrue(e.getLiquidSaturationIndex() < e.getSulfideSaturationIndex());
    // the limit does not depend on how much mercury the fluid was given
    assertEquals(est.estimate(fluid(50.0, 150.0, 0.0, 0.0)).getSulfideLimitMoleFraction(),
        e.getSulfideLimitMoleFraction(), 1.0e-6 * e.getSulfideLimitMoleFraction());
  }

  /** The saturation temperature inverts the limit; a hundred times less mercury is held back about 60 K colder. */
  @Test
  public void testSulfideSaturationTemperatureInvertsTheLimit() {
    ReservoirMercuryEstimator est = new ReservoirMercuryEstimator();
    SystemInterface f = fluid(120.0, 400.0, 0.0, 0.0);
    double limit = est.estimate(f).getSulfideLimitMoleFraction();
    assertEquals(393.15, est.sulfideSaturationTemperature(f, limit), 0.2);
    double colder = est.sulfideSaturationTemperature(f, limit / 100.0);
    assertTrue(393.15 - colder > 45.0 && 393.15 - colder < 80.0, "hundred times less: " + (393.15 - colder));
    assertTrue(Double.isNaN(est.sulfideSaturationTemperature(f, 0.5)));
    assertThrows(IllegalArgumentException.class, () -> est.sulfideSaturationTemperature(f, 0.0));
    assertThrows(IllegalArgumentException.class, () -> est.sulfideSaturationTemperature(f, 1.0e-9, 400.0, 300.0));
  }

  /**
   * The liquid mercury limit from the standard-state data agrees with the dissolved mercury of
   * {@link MercuryPhaseEquilibrium}, which uses the pure-mercury fugacity of the UMR-PRU-MC equation of state.
   */
  @Test
  public void testLiquidLimitAgreesWithMercuryPhaseEquilibrium() {
    SystemInterface f = MercuryFluidConverter.toUmrPruMc(fluid(100.0, 300.0, 0.0, 0.0), 1.0e6, "ug/Sm3");
    MercuryPhaseEquilibrium equilibrium = new MercuryPhaseEquilibrium(f);
    equilibrium.run();
    assertTrue(equilibrium.hasFreeMercury());
    double dissolved = equilibrium.getMercuryConcentration(0, "ug/Sm3");
    double limit = new ReservoirMercuryEstimator().estimate(f).getLiquidLimit("ug/Sm3");
    assertEquals(dissolved, limit, 0.15 * dissolved);
  }

  /** The example of docs/thermo/reservoir_mercury_limits.md with the values quoted there. */
  @Test
  public void testDocumentationExample() {
    SystemInterface gas = new SystemSrkEos(273.15 + 110.0, 350.0);
    gas.addComponent("nitrogen", 0.5);
    gas.addComponent("CO2", 2.0);
    gas.addComponent("methane", 78.0);
    gas.addComponent("ethane", 8.0);
    gas.addComponent("propane", 4.0);
    gas.addComponent("n-butane", 2.5);
    gas.addComponent("n-hexane", 5.0);
    gas.setMixingRule("classic");
    SystemInterface fluid = MercuryFluidConverter.toUmrPruMc(gas, 1.0, "ug/Sm3");

    ReservoirMercuryEstimator estimator = new ReservoirMercuryEstimator();
    ReservoirMercuryEstimator.Estimate pure = estimator.estimate(fluid);
    double cinnabarLimit = pure.getSulfideLimit("ug/Sm3");
    double liquidLimit = pure.getLiquidLimit("ug/Sm3");
    double bufferedH2S = pure.getH2SPpm();
    double saturationIndex = pure.getSulfideSaturationIndex();

    estimator.setMercurySulfideActivity(0.003);
    double traceLimit = estimator.estimate(fluid).getSulfideLimit("ug/Sm3");

    assertEquals(38000.0, cinnabarLimit, 3000.0);
    assertEquals(58000.0, liquidLimit, 5000.0);
    assertEquals(1.9, bufferedH2S, 0.2);
    assertEquals(-4.6, saturationIndex, 0.1);
    assertEquals(113.0, traceLimit, 9.0);
    assertEquals("cinnabar", pure.getControllingPhase());
    assertEquals(cinnabarLimit, pure.getLimit("ug/Sm3"), 1.0e-9);
    assertEquals(fluid.getPressure(), pure.getPressure(), 1.0e-9);
    assertEquals(383.15, pure.getTemperature(), 1.0e-9);
    assertTrue(pure.getFluid().hasComponent("mercury"));
    assertTrue(pure.getMercuryFugacityCoefficient() > 0.2 && pure.getMercuryFugacityCoefficient() < 0.45);
    assertTrue(pure.getLiquidMercuryFugacity() > pure.getSulfideMercuryFugacity());
    assertFalse(pure.getWarnings().isEmpty());
  }

  /** Invalid input is rejected with a message. */
  @Test
  public void testInvalidInput() {
    ReservoirMercuryEstimator est = new ReservoirMercuryEstimator();
    SystemInterface noCo2 = new SystemSrkEos(373.15, 300.0);
    noCo2.addComponent("methane", 1.0);
    noCo2.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> est.estimate(noCo2));
    assertThrows(IllegalArgumentException.class, () -> est.setMercurySulfideActivity(0.0));
    assertThrows(IllegalArgumentException.class, () -> est.setWaterActivity(1.5));
    assertThrows(IllegalArgumentException.class, () -> est.setSulfidePhase(null));
    assertThrows(IllegalArgumentException.class, () -> ReservoirMercuryEstimator.convertMoleFraction(1.0e-9, "kg"));
  }
}
