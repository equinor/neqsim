package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the performance-oriented options on {@link Stream}: the configurable physical-property initialization level
 * and the cached vapor-pressure standard behind {@code getRVP}.
 *
 * @author ESOL
 */
class StreamPerformanceOptionsTest extends neqsim.NeqSimTest {

  /**
   * Creates a light stabilized-oil fluid used by the vapor-pressure tests.
   *
   * @return a fluid with a measurable Reid vapor pressure
   */
  private static SystemInterface createOilFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 5.0);
    fluid.addComponent("methane", 0.0006538);
    fluid.addComponent("ethane", 0.006538);
    fluid.addComponent("propane", 0.006538);
    fluid.addComponent("n-pentane", 0.545);
    fluid.addComponent("water", 0.00545);
    fluid.setMixingRule(2);
    return fluid;
  }

  /**
   * Builds a run stream around the light-oil fluid.
   *
   * @return a stream that has been run once
   */
  private static Stream createRunOilStream() {
    Stream stream = new Stream("oil", createOilFluid());
    stream.setTemperature(20.0, "C");
    stream.setPressure(5.0, "bara");
    stream.setFlowRate(100.0, "kg/hr");
    stream.run();
    return stream;
  }

  @Test
  public void testDefaultPropertyInitLevelIsFull() {
    Stream stream = createRunOilStream();
    assertEquals(Stream.PropertyInitLevel.FULL, stream.getPropertyInitLevel());
    assertTrue(stream.getFluid().getDensity("kg/m3") > 0.0);
  }

  @Test
  public void testDensityOnlyPropertyInitLevelStillGivesDensity() {
    Stream stream = createRunOilStream();
    double fullDensity = stream.getFluid().getDensity("kg/m3");

    stream.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);
    stream.setPressure(6.0, "bara");
    stream.run();
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());
    assertTrue(stream.getFluid().getDensity("kg/m3") > 0.0);
    assertEquals(fullDensity, stream.getFluid().getDensity("kg/m3"), 0.25 * fullDensity);

    stream.setPropertyInitLevel(null);
    assertEquals(Stream.PropertyInitLevel.FULL, stream.getPropertyInitLevel());
  }

  @Test
  public void testProcessSystemPropagatesPropertyInitLevel() {
    ProcessSystem process = new ProcessSystem();
    Stream stream = new Stream("oil", createOilFluid());
    stream.setTemperature(20.0, "C");
    stream.setPressure(5.0, "bara");
    stream.setFlowRate(100.0, "kg/hr");
    process.add(stream);

    process.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, process.getPropertyInitLevel());
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, stream.getPropertyInitLevel());

    // Units added after the level was set inherit it as well.
    Stream later = new Stream("oil 2", createOilFluid());
    process.add(later);
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, later.getPropertyInitLevel());

    process.run();
    assertTrue(stream.getFluid().getDensity("kg/m3") > 0.0);
  }

  @Test
  public void testCachedRvpStandardKeepsMethodsIndependent() {
    Stream stream = createRunOilStream();

    double vpcr4 = stream.getRVP(37.8, "C", "bara");
    double d6377 = stream.getRVP(37.8, "C", "bara", "RVP_ASTM_D6377");
    double vpcr4Again = stream.getRVP(37.8, "C", "bara");

    assertTrue(vpcr4 > 0.0);
    // Reading another method must not change what the default (VPCR4) call returns.
    assertEquals(vpcr4, vpcr4Again, 1e-12);
    assertEquals(0.834 * vpcr4, d6377, 1e-9);
  }

  @Test
  public void testRvpCacheIsInvalidatedWhenCompositionChanges() {
    Stream stream = createRunOilStream();
    double rvpBefore = stream.getRVP(37.8, "C", "bara");

    // More light ends -> higher vapor pressure.
    stream.getFluid().setMolarComposition(new double[] { 0.02, 0.05, 0.05, 0.5, 0.00545 });
    stream.run();
    double rvpAfter = stream.getRVP(37.8, "C", "bara");

    assertTrue(rvpAfter > rvpBefore + 1e-4,
        "RVP should increase after adding light ends, got " + rvpBefore + " -> " + rvpAfter);
  }
}
