package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/** Regression tests for the independent defects reported in issue 3986. */
class GibbsReactorIssue3986Test extends neqsim.NeqSimTest {
  private static Object field(Object target, String name) throws Exception {
    Field field = GibbsReactor.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = GibbsReactor.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = GibbsReactor.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, GibbsReactor.GibbsComponent> components(GibbsReactor reactor) throws Exception {
    return (Map<String, GibbsReactor.GibbsComponent>) field(reactor, "componentMap");
  }

  private static GibbsReactor reactionFeed(int phases) {
    SystemInterface fluid = new SystemPrEos(1200.0, 1.0);
    fluid.addComponent("CO2", 0.5);
    fluid.addComponent("hydrogen", 0.5);
    fluid.addComponent("CO", 0.0);
    fluid.addComponent("water", 0.0);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.setNumberOfPhases(phases);
    fluid.setBeta(phases == 1 ? 1.0 : 0.5);
    fluid.init(1);
    GibbsReactor reactor = new GibbsReactor("reactor", new Stream("feed", fluid));
    reactor.setMinIterations(1);
    reactor.setMaxIterations(3000);
    reactor.setConvergenceTolerance(1e-8);
    return reactor;
  }

  @Test
  void totalFeedDoesNotDependOnPhaseSplit() {
    GibbsReactor split = reactionFeed(2);
    assertEquals(2, split.getInletStream().getThermoSystem().getNumberOfPhases());
    split.run();
    assertEquals(0.5, split.getInletMole().get(0), 1e-12);
    assertEquals(0.5, split.getInletMole().get(1), 1e-12);
    GibbsReactor single = reactionFeed(1);
    single.run();
    assertTrue(split.hasConverged());
    assertTrue(single.hasConverged());
    assertTrue(split.getElementMassBalanceError() < 1e-5);
    for (int i = 0; i < 4; i++) {
      assertEquals(single.getOutletMole().get(i), split.getOutletMole().get(i), 1e-7);
      assertEquals(split.getOutletMole().get(i),
          split.getOutletStream().getThermoSystem().getComponent(i).getNumberOfmoles(), 1e-7);
    }
    split.run();
    assertEquals(0.5, split.getInletMole().get(0), 1e-12);
  }

  @Test
  void databaseSeparatesSodiumArgonAndCharge() throws Exception {
    GibbsReactor reactor = new GibbsReactor("database");
    List<String> names = Arrays.asList(reactor.getElementNames());
    assertEquals(8, names.size());
    assertEquals(5, names.indexOf("Ar"));
    assertEquals(6, names.indexOf("Z"));
    Map<String, GibbsReactor.GibbsComponent> database = components(reactor);
    double[] argon = database.get("argon").getElements();
    double[] sodium = database.get("na+").getElements();
    assertEquals(1.0, argon[names.indexOf("Ar")]);
    assertEquals(0.0, argon[names.indexOf("Na")]);
    assertEquals(1.0, sodium[names.indexOf("Na")]);
    assertEquals(0.0, sodium[names.indexOf("Ar")]);
    assertEquals(1.0, sodium[names.indexOf("Z")]);
    assertEquals(-2.0, database.get("so4--").getElements()[names.indexOf("Z")]);
  }

  @Test
  @SuppressWarnings("unchecked")
  void neutralFeedRetainsChargeConstraintAndIonicCandidates() throws Exception {
    SystemInterface fluid = new SystemPrEos(600.0, 1.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("H+", 0.0);
    fluid.addComponent("OH-", 0.0);
    fluid.addComponent("hydrogen", 0.0);
    fluid.addComponent("oxygen", 0.0);
    GibbsReactor reactor = new GibbsReactor("charge", new Stream("feed", fluid));
    List<String> names = Arrays.asList(reactor.getElementNames());
    double[] balance = new double[names.size()];
    balance[names.indexOf("O")] = 1.0;
    balance[names.indexOf("H")] = 2.0;
    setField(reactor, "elementMoleBalanceIn", balance);
    setField(reactor, "processedComponents", Arrays.asList("water", "H+", "OH-", "hydrogen", "oxygen"));
    setField(reactor, "variableComponents", Arrays.asList("water", "H+", "OH-", "hydrogen", "oxygen"));
    invoke(reactor, "determineFeedExcludedComponents", new Class<?>[] {SystemInterface.class}, fluid);
    assertFalse(reactor.isComponentExcludedByFeed("H+"));
    assertFalse(reactor.isComponentExcludedByFeed("OH-"));
    List<Integer> active = (List<Integer>) invoke(reactor, "findActiveElements", new Class<?>[0]);
    assertTrue(active.contains(names.indexOf("Z")), "Zero net charge still requires electroneutrality");
    assertEquals(active, invoke(reactor, "getActiveElementIndices", new Class<?>[0]));
  }

  @Test
  void fallbackGibbsEnergySatisfiesGibbsHelmholtzIdentity() {
    GibbsReactor reactor = new GibbsReactor("reference");
    double missing = Double.NaN;
    GibbsReactor.GibbsComponent component = reactor.new GibbsComponent("synthetic", new double[8], new double[4],
        -100.0, -80.0, 100.0, missing, missing, missing, missing, missing, missing, missing, missing, missing, missing,
        missing, missing) {
      @Override
      public double[] calculateCorrectedHeatCapacityCoeffs(int index) {
        return new double[] {10.0, 0.2, 0.003, 0.00004};
      }
    };
    assertEquals(-80.0, component.calculateGibbsEnergy(298.15, 0), 1e-10);
    for (double temperature : new double[] {298.15, 500.0, 900.0}) {
      double step = 0.001;
      double derivative = (component.calculateGibbsEnergy(temperature + step, 0) / (temperature + step)
          - component.calculateGibbsEnergy(temperature - step, 0) / (temperature - step)) / (2 * step);
      assertEquals(component.calculateEnthalpy(temperature, 0), -temperature * temperature * derivative, 1e-5);
    }
  }

  @Test
  void regularizedMatrixIsUsedEvenWhenConditionNumberRemainsHigh() throws Exception {
    GibbsReactor reactor = reactionFeed(1);
    reactor.setMaxIterations(1);
    reactor.run();
    double[][] raw = reactor.getJacobianMatrix();
    int count = ((List<?>) field(reactor, "variableComponents")).size();
    double[] residual = (double[]) invoke(reactor, "getObjectiveVectorForVariables", new Class<?>[0]);
    double tau = 100.0;
    for (int i = 0; i < count; i++) {
      raw[i][i] += tau;
    }
    SimpleMatrix matrix = new SimpleMatrix(raw);
    assertTrue(matrix.conditionP2() > 1.0);
    double[] expected = matrix.solve(new SimpleMatrix(residual.length, 1, true, residual)).scale(-1).getDDRM()
        .getData();
    reactor.setUseRegularization(true);
    reactor.setRegularizationThreshold(1.0);
    reactor.setRegularizationTau(tau);
    assertArrayEquals(expected, reactor.performNewtonRaphsonIteration(), 1e-8);
  }

  @Test
  void iterationLimitReturnsFailure() {
    GibbsReactor reactor = reactionFeed(1);
    reactor.setMaxIterations(1);
    reactor.run();
    assertFalse(reactor.hasConverged());
    assertFalse(reactor.solveGibbsEquilibrium(0.05));
  }

  @Test
  void flashedTwoPhaseFeedConservesActualInventory() {
    SystemInterface fluid = new SystemPrEos(300.0, 10.0);
    fluid.addComponent("methane", 0.3);
    fluid.addComponent("water", 0.7);
    fluid.setMixingRule(2);
    Stream inlet = new Stream("condensing feed", fluid);
    inlet.run();
    assertEquals(2, inlet.getThermoSystem().getNumberOfPhases());
    GibbsReactor reactor = new GibbsReactor("homogeneous reactor", inlet);
    reactor.setConvergenceTolerance(1e-8);
    reactor.run();
    assertEquals(0.3, reactor.getInletMole().get(0), 1e-12);
    assertEquals(0.7, reactor.getInletMole().get(1), 1e-12);
    assertTrue(reactor.hasConverged());
    assertEquals(inlet.getFlowRate("kg/sec"), reactor.getOutletStream().getFlowRate("kg/sec"), 1e-9);
    assertEquals(2, inlet.getThermoSystem().getNumberOfPhases(), "The caller's feed must not be modified");
  }

  @Test
  @SuppressWarnings("unchecked")
  void dependentChargeBalanceDoesNotMakeJacobianSingular() throws Exception {
    GibbsReactor reactor = new GibbsReactor("dependent charge");
    List<String> names = Arrays.asList(reactor.getElementNames());
    double[] balance = new double[names.size()];
    balance[names.indexOf("O")] = 1.0;
    balance[names.indexOf("H")] = 2.0;
    setField(reactor, "elementMoleBalanceIn", balance);
    setField(reactor, "variableComponents", Arrays.asList("water", "H+", "OH-"));
    List<Integer> active = (List<Integer>) invoke(reactor, "findActiveElements", new Class<?>[0]);
    assertEquals(Arrays.asList(names.indexOf("O"), names.indexOf("H")), active);
    // For this species set Z = H - 2*O, so atom conservation also conserves charge.
    for (String name : Arrays.asList("water", "h+", "oh-")) {
      double[] elements = components(reactor).get(name).getElements();
      assertEquals(elements[names.indexOf("Z")], elements[names.indexOf("H")] - 2 * elements[names.indexOf("O")],
          1e-12);
    }
  }

  @Test
  void objectiveReadsFugacityFromItsArgument() throws Exception {
    GibbsReactor reactor = reactionFeed(1);
    reactor.setMaxIterations(1);
    reactor.run();
    SystemInterface evaluated = reactor.getOutletStream().getThermoSystem().clone();
    evaluated.setPressure(100.0);
    evaluated.init(3);
    invoke(reactor, "calculateObjectiveFunctionValues", new Class<?>[] {SystemInterface.class}, evaluated);
    double[] lambda = (double[]) field(reactor, "lambda");
    double total = 0.0;
    for (double amount : reactor.getOutletMole()) {
      total += amount;
    }
    for (int i = 0; i < evaluated.getNumberOfComponents(); i++) {
      String name = evaluated.getComponent(i).getComponentName();
      GibbsReactor.GibbsComponent component = components(reactor).get(name.toLowerCase());
      double lagrange = 0.0;
      for (int j = 0; j < lambda.length; j++) {
        lagrange += lambda[j] * component.getElements()[j];
      }
      double expected = component.calculateGibbsEnergy(evaluated.getTemperature(), i) + 8.314462618e-3
          * evaluated.getTemperature() * Math.log(evaluated.getPhase(0).getComponent(i).getFugacityCoefficient()
              * reactor.getOutletMole().get(i) / total * evaluated.getPressure())
          - lagrange;
      assertEquals(expected, reactor.getObjectiveFunctionValues().get(name), 1e-10);
    }
  }

  @Test
  void syntheticIonicEquilibriumConservesChargeSodiumAndArgon() throws Exception {
    for (double sodiumFeed : new double[] {0.0, 0.1}) {
      SystemInterface fluid = new neqsim.thermo.system.SystemIdealGas(600.0, 1.0);
      fluid.addComponent("water", 1.0);
      fluid.addComponent("hydrogen", 0.0);
      fluid.addComponent("oxygen", 0.0);
      fluid.addComponent("H+", 0.0);
      fluid.addComponent("OH-", 0.0);
      fluid.addComponent("Na+", sodiumFeed);
      fluid.addComponent("argon", 0.2);
      GibbsReactor reactor = new GibbsReactor("synthetic ions", new Stream("feed", fluid));
      // Equal standard chemical potentials give an interior solution that tests the
      // constraints without claiming physical validity of ideal-gas electrolyte data.
      Map<String, GibbsReactor.GibbsComponent> database = components(reactor);
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        String name = fluid.getComponent(i).getComponentName().toLowerCase();
        GibbsReactor.GibbsComponent original = database.get(name);
        double missing = Double.NaN;
        database.put(name, reactor.new GibbsComponent(name, original.getElements(), new double[4], 0.0, 0.0, 0.0, 0.0,
            missing, missing, missing, missing, missing, 0.0, missing, missing, missing, missing, missing));
      }
      reactor.setConvergenceTolerance(1e-8);
      reactor.run();
      assertTrue(reactor.hasConverged());
      List<String> names = Arrays.asList(reactor.getElementNames());
      double[] balance = reactor.getElementMoleBalanceOut();
      assertEquals(0.2, balance[names.indexOf("Ar")], 1e-8);
      assertEquals(sodiumFeed, balance[names.indexOf("Na")], 1e-8);
      assertEquals(sodiumFeed, balance[names.indexOf("Z")], 1e-8);
      SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
      double actualCharge = outlet.getComponent("H+").getNumberOfmoles() + outlet.getComponent("Na+").getNumberOfmoles()
          - outlet.getComponent("OH-").getNumberOfmoles();
      assertEquals(sodiumFeed, actualCharge, 1e-8);
      assertTrue(outlet.getComponent("H+").getNumberOfmoles() > 1e-4);
      assertTrue(outlet.getComponent("OH-").getNumberOfmoles() > 1e-4);
    }
  }
}
