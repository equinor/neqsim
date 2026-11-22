package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Physical state and convergence regressions for composition acceleration, issue 3999.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class RecycleCompositionAccelerationTest extends neqsim.NeqSimTest {
  /**
   * Builds a flashed binary stream.
   *
   * @param methane overall methane mole fraction
   * @param temperature temperature in kelvin
   * @return binary stream at 19.5 bara and 1 mol/s
   */
  private Stream stream(double methane, double temperature) {
    SystemInterface fluid = new SystemPrEos(temperature, 19.5);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-butane", 1.0 - methane);
    fluid.setMixingRule(2);
    Stream stream = new Stream("inlet", fluid);
    stream.setFlowRate(1.0, "mol/sec");
    stream.run();
    return stream;
  }

  /**
   * Connects a recycle with an explicit outlet.
   *
   * @param inlet return stream
   * @param method convergence method
   * @return configured recycle after its first pass
   */
  private Recycle recycle(Stream inlet, AccelerationMethod method) {
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(inlet.clone("outlet"));
    recycle.setAccelerationMethod(method);
    recycle.setWegsteinDelayIterations(0);
    recycle.run();
    return recycle;
  }

  /**
   * Invokes the internal state application to isolate clipping and flash behavior.
   *
   * @param recycle recycle under test
   * @param values proposed composition
   * @throws Exception if reflection or the method invocation fails
   */
  private void apply(Recycle recycle, double[] values) throws Exception {
    Method method = Recycle.class.getDeclaredMethod("applyStreamValues", double[].class);
    method.setAccessible(true);
    method.invoke(recycle, (Object) values);
  }

  /**
   * Verifies phase normalization and reconstruction of every component inventory.
   *
   * @param fluid flashed fluid
   */
  private void assertConsistent(SystemInterface fluid) {
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double sum = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double x = fluid.getPhase(p).getComponent(i).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0);
        sum += x;
      }
      assertEquals(1.0, sum, 1e-10);
    }
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double moles = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        moles += fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
      }
      assertEquals(fluid.getComponent(i).getNumberOfmoles(), moles, 1e-9);
      assertEquals(fluid.getComponent(i).getz(), moles / fluid.getTotalNumberOfMoles(), 1e-9);
    }
  }

  /**
   * The accelerated coordinates must be bulk composition, independent of phase split.
   *
   * @throws Exception if reflective extraction fails
   */
  @Test
  void extractsOnlyOverallComposition() throws Exception {
    Stream inlet = stream(0.5, 250.0);
    assertEquals(2, inlet.getFluid().getNumberOfPhases());
    Method method = Recycle.class.getDeclaredMethod("extractStreamValues", StreamInterface.class);
    method.setAccessible(true);
    double[] values = (double[]) method.invoke(new Recycle("recycle"), inlet);
    assertArrayEquals(new double[] {0.5, 0.5}, values, 1e-12);
  }

  /**
   * A new tear composition must update inventories and retain equilibrium at the flashed T, P and molar flow.
   *
   * @throws Exception if reflective application fails
   */
  @Test
  void appliesCompositionThroughBulkInventoryAndFlash() throws Exception {
    Stream inlet = stream(0.5, 250.0);
    Recycle recycle = recycle(inlet, AccelerationMethod.DIRECT_SUBSTITUTION);
    apply(recycle, new double[] {0.7, 0.3});
    SystemInterface result = recycle.getOutStream().getFluid();
    assertEquals(0.7, result.getComponent(0).getz(), 1e-12);
    assertEquals(250.0, result.getTemperature(), 1e-12);
    assertEquals(19.5, result.getPressure(), 1e-12);
    assertEquals(1.0, result.getFlowRate("mol/sec"), 1e-12);
    assertConsistent(result);
    SystemInterface reference = inlet.getFluid().clone();
    reference.setMolarComposition(new double[] {0.7, 0.3});
    new ThermodynamicOperations(reference).TPflash();
    assertEquals(reference.getNumberOfPhases(), result.getNumberOfPhases());
    for (int p = 0; p < result.getNumberOfPhases(); p++) {
      assertEquals(reference.getBeta(p), result.getBeta(p), 1e-10);
      for (int i = 0; i < result.getNumberOfComponents(); i++) {
        assertEquals(reference.getPhase(p).getComponent(i).getx(), result.getPhase(p).getComponent(i).getx(), 1e-10);
      }
    }
    assertEquals(0.5, inlet.getFluid().getComponent(0).getz(), 1e-12);
  }

  /**
   * A clipped zero must remove the component without depending on the low-level setx setter.
   *
   * @throws Exception if reflective application fails
   */
  @Test
  void clippingCanRemoveAComponentAndChangePhaseCount() throws Exception {
    Recycle recycle = recycle(stream(0.5, 250.0), AccelerationMethod.DIRECT_SUBSTITUTION);
    apply(recycle, new double[] {1.1, -0.1});
    SystemInterface result = recycle.getOutStream().getFluid();
    assertEquals(0.0, result.getComponent(1).getNumberOfmoles(), 0.0);
    assertEquals(0.0, result.getComponent(1).getz(), 0.0);
    assertEquals(1, result.getNumberOfPhases());
    assertConsistent(result);
  }

  /**
   * Unusable proposals leave the original flashed state intact.
   *
   * @throws Exception if reflective application fails
   */
  @Test
  void rejectsInvalidOrEmptyCompositionWithoutMutation() throws Exception {
    Recycle recycle = recycle(stream(0.5, 250.0), AccelerationMethod.DIRECT_SUBSTITUTION);
    SystemInterface original = recycle.getOutStream().getFluid();
    for (double[] values : new double[][] {{Double.NaN, 0.5}, {Double.POSITIVE_INFINITY, 0.5}, {-1.0, -1.0}, {0.5}}) {
      apply(recycle, values);
      assertSame(original, recycle.getOutStream().getFluid());
      assertConsistent(original);
    }
  }

  /** Broyden must build a composition-sized Jacobian. */
  @Test
  void broydenDimensionMatchesAppliedCoordinates() {
    Recycle recycle = recycle(stream(0.5, 250.0), AccelerationMethod.BROYDEN);
    assertEquals(2, recycle.getBroydenAccelerator().getDimension());
  }

  /** q=0 must publish the current flashed return, including its composition. */
  @Test
  void zeroQUsesDirectSubstitutionAndInactiveSlotsAreZero() {
    Stream inlet = stream(0.2, 350.0);
    Recycle recycle = recycle(inlet, AccelerationMethod.WEGSTEIN);
    recycle.setWegsteinQMin(0.0);
    recycle.setWegsteinQMax(0.0);
    for (double methane : new double[] {0.4, 0.6, 0.8}) {
      inlet.getFluid().setMolarComposition(new double[] {methane, 1.0 - methane});
      inlet.setTemperature(350.0 + methane, "K");
      inlet.setPressure(19.5 + methane, "bara");
      inlet.setFlowRate(1.0 + methane, "mol/sec");
      inlet.run();
      recycle.run();
      SystemInterface result = recycle.getOutletStream().getFluid();
      assertEquals(inlet.getFluid().getPhase(0).getComponent(0).getx(), result.getPhase(0).getComponent(0).getx(),
          1e-12);
      assertEquals(inlet.getTemperature(), result.getTemperature(), 1e-12);
      assertEquals(inlet.getPressure(), result.getPressure(), 1e-12);
      assertEquals(inlet.getFlowRate("mol/sec"), result.getFlowRate("mol/sec"), 1e-12);
      assertArrayEquals(new double[5], recycle.getWegsteinQFactors(), 0.0);
      assertConsistent(result);
    }
  }

  /** A fully damped step cannot make an unresolved fixed-point residual look converged. */
  @Test
  void convergenceUsesUnacceleratedResidual() {
    Stream inlet = stream(0.2, 350.0);
    Recycle recycle = recycle(inlet, AccelerationMethod.WEGSTEIN);
    recycle.setWegsteinQMin(1.0);
    recycle.setWegsteinQMax(1.0);
    recycle.setTolerance(1e-10);
    inlet.getFluid().setMolarComposition(new double[] {0.6, 0.4});
    inlet.run();
    recycle.run();
    assertEquals(0.2, recycle.getOutletStream().getFluid().getComponent(0).getz(), 1e-12);
    assertEquals(0.8, recycle.getErrorComposition(), 1e-12);
    assertFalse(recycle.solved());
  }

  /**
   * A coupled binary composition with a known fixed point must converge through the public stream API.
   *
   * @param method acceleration method
   */
  @ParameterizedTest
  @EnumSource(value = AccelerationMethod.class, names = {"WEGSTEIN", "BROYDEN"})
  void convergesAnalyticalCompositionMap(AccelerationMethod method) {
    Stream inlet = stream(0.2, 450.0);
    Recycle recycle = recycle(inlet, method);
    recycle.getBroydenAccelerator().setDelayIterations(0);
    for (int pass = 0; pass < 3; pass++) {
      double methane = 0.5 * recycle.getOutletStream().getFluid().getComponent(0).getz() + 0.25;
      inlet.getFluid().setMolarComposition(new double[] {methane, 1.0 - methane});
      inlet.run();
      recycle.run();
    }
    assertEquals(0.5, recycle.getOutletStream().getFluid().getComponent(0).getz(), 1e-10);
    assertConsistent(recycle.getOutletStream().getFluid());
    if (method == AccelerationMethod.WEGSTEIN) {
      double[] factors = recycle.getCompositionWegsteinQFactors();
      assertEquals(2, factors.length);
      double original = factors[0];
      factors[0] = 123.0;
      assertEquals(original, recycle.getCompositionWegsteinQFactors()[0], 0.0);
      double[] legacy = recycle.getWegsteinQFactors();
      assertEquals(5, legacy.length);
      assertArrayEquals(new double[3], new double[] {legacy[0], legacy[1], legacy[2]}, 0.0);
      assertEquals(original, legacy[3], 0.0);
    }
  }

  /**
   * Both changed dimension and changed component identity invalidate old acceleration history.
   *
   * @param method acceleration method
   */
  @ParameterizedTest
  @EnumSource(value = AccelerationMethod.class, names = {"WEGSTEIN", "BROYDEN"})
  void changedComponentListRestartsAcceleration(AccelerationMethod method) {
    Recycle recycle = recycle(stream(0.2, 450.0), method);
    for (int count : new int[] {2, 3}) {
      SystemInterface fluid = new SystemPrEos(450.0, 19.5);
      fluid.addComponent("n-butane", 0.5);
      fluid.addComponent("methane", 0.5);
      if (count == 3) {
        fluid.addComponent("ethane", 0.5);
      }
      fluid.setMixingRule(2);
      Stream inlet = new Stream("replacement", fluid);
      inlet.run();
      recycle.replaceStream(0, inlet);
      recycle.run();
      assertEquals(10.0, recycle.getErrorComposition());
      assertNull(recycle.getCompositionWegsteinQFactors());
      recycle.run();
      assertConsistent(recycle.getOutletStream().getFluid());
      if (method == AccelerationMethod.BROYDEN) {
        assertEquals(count, recycle.getBroydenAccelerator().getDimension());
      } else {
        assertEquals(count, recycle.getCompositionWegsteinQFactors().length);
      }
    }
  }

  /**
   * A thermal recycle must recover the adiabatic mass and energy balances at convergence.
   *
   * @param method acceleration method
   */
  @ParameterizedTest
  @EnumSource(AccelerationMethod.class)
  void adiabaticMixerSplitterLoopConservesEnergy(AccelerationMethod method) {
    Stream feed = stream(0.4, 310.0);
    feed.setName("feed");
    Stream tear = stream(0.2, 280.0);
    tear.setName("tear");
    tear.setFlowRate(0.1, "mol/sec");
    tear.run();
    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);
    Splitter splitter = new Splitter("splitter", mixer.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] {0.75, 0.25});
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(0));
    recycle.setOutletStream(tear);
    recycle.setAccelerationMethod(method);
    recycle.setTolerance(1e-7);
    recycle.setFlowTolerance(1e-11);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(tear);
    process.add(mixer);
    process.add(splitter);
    process.add(recycle);
    process.run();
    assertTrue(recycle.solved(), method + " must converge");
    SystemInterface product = splitter.getSplitStream(1).getFluid();
    product.init(3);
    feed.getFluid().init(3);
    assertEquals(feed.getFluid().getEnthalpy(), product.getEnthalpy(),
        1e-5 * Math.max(1.0, Math.abs(feed.getFluid().getEnthalpy())));
    assertEquals(310.0, product.getTemperature(), 1e-4);
    assertEquals(3.0, tear.getFlowRate("mol/sec"), 1e-6);
    for (int i = 0; i < feed.getFluid().getNumberOfComponents(); i++) {
      assertEquals(feed.getFluid().getComponent(i).getNumberOfmoles(), product.getComponent(i).getNumberOfmoles(),
          1e-7);
    }
    assertConsistent(tear.getFluid());
  }

  /**
   * All acceleration methods must recover the analytical component balances of an actual flowsheet.
   *
   * @param method recycle convergence method
   */
  @ParameterizedTest
  @EnumSource(AccelerationMethod.class)
  void componentSelectiveRecycleConservesFeedAndProduct(AccelerationMethod method) {
    Stream feed = stream(0.4, 350.0);
    feed.setName("feed");
    Stream tear = stream(0.2, 350.0);
    tear.setName("tear");
    tear.setFlowRate(0.1, "mol/sec");
    tear.run();
    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);
    ComponentSplitter splitter = new ComponentSplitter("splitter", mixer.getOutletStream());
    double[] fractions = {0.8, 0.3};
    splitter.setSplitFactors(fractions);
    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(0));
    recycle.setOutletStream(tear);
    recycle.setAccelerationMethod(method);
    recycle.setTolerance(1e-9);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(tear);
    process.add(mixer);
    process.add(splitter);
    process.add(recycle);
    process.run();
    assertTrue(recycle.solved(), method + " must converge");
    SystemInterface product = splitter.getSplitStream(1).getFluid();
    for (int i = 0; i < fractions.length; i++) {
      double feedMoles = feed.getFluid().getComponent(i).getNumberOfmoles();
      double expectedRecycle = fractions[i] * feedMoles / (1.0 - fractions[i]);
      assertEquals(feedMoles, product.getComponent(i).getNumberOfmoles(), 1e-7);
      assertEquals(expectedRecycle, tear.getFluid().getComponent(i).getNumberOfmoles(), 1e-7);
    }
    assertEquals(feed.getFlowRate("kg/sec"), splitter.getSplitStream(1).getFlowRate("kg/sec"), 1e-8);
    assertConsistent(tear.getFluid());
  }
}
