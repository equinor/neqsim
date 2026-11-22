package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerical and phase-dispatch regressions for every interfacial-tension selector in SRK and PR fluids.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class SurfaceTensionMultiphaseTest {
  /** Supported named models. */
  private static final String[] MODELS = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory",
      "Linear Gradient Theory", "Full Gradient Theory", "cDFT"};
  /** Ordered interface names. */
  private static final String[][] PAIRS = {{"gas", "oil"}, {"gas", "aqueous"}, {"oil", "aqueous"}};

  /**
   * Supplies every model, EOS and two-phase fluid combination.
   *
   * @return test arguments
   */
  static Stream<Arguments> twoPhaseCases() {
    return Stream.of(false, true).flatMap(pr -> Stream.of(MODELS)
        .flatMap(model -> IntStream.range(0, 3).mapToObj(pair -> Arguments.of(pr, model, pair))));
  }

  /**
   * Supplies every model, EOS and three-phase temperature combination.
   *
   * @return test arguments
   */
  static Stream<Arguments> threePhaseCases() {
    return Stream.of(false, true).flatMap(pr -> Stream.of(MODELS)
        .flatMap(model -> Stream.of(300.0, 320.0).map(temperature -> Arguments.of(pr, model, temperature))));
  }

  /**
   * Supplies all numbered model sets in each EOS.
   *
   * @return test arguments
   */
  static Stream<Arguments> numberedCases() {
    return Stream.of(false, true).flatMap(pr -> IntStream.range(0, 6).mapToObj(set -> Arguments.of(pr, set)));
  }

  /**
   * Flashes an SRK or PR fluid and initializes its physical properties.
   *
   * @param pr whether to use PR rather than SRK
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param names component names
   * @param moles component molar amounts
   * @return flashed fluid
   */
  private static SystemInterface fluid(boolean pr, double temperature, double pressure, String[] names,
      double[] moles) {
    SystemInterface fluid = pr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], moles[i]);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Creates a synthetic three-phase methane/heptane/water fluid.
   *
   * @param pr whether to use PR
   * @param temperature temperature in K
   * @return fluid with gas, oil and aqueous phases
   */
  private static SystemInterface threePhaseFluid(boolean pr, double temperature) {
    SystemInterface fluid = fluid(pr, temperature, 10.0, new String[] {"methane", "n-heptane", "water"},
        new double[] {0.5, 0.3, 0.2});
    assertEquals(3, fluid.getNumberOfPhases());
    for (String name : new String[] {"gas", "oil", "aqueous"}) {
      assertTrue(fluid.hasPhaseType(name), name);
    }
    return fluid;
  }

  /**
   * Checks finite values, physical screening bounds, units and exact repeatability without changing the bulk phases.
   * These bounds are numerical screening criteria, not an experimental qualification of every model.
   *
   * @param fluid flashed fluid
   * @param model selected named model, or a numbered-set description
   * @param pair ordered interface names
   * @return calculated interfacial tension in N/m
   */
  private static double checkInterface(SystemInterface fluid, String model, String[] pair) {
    int first = fluid.getPhaseNumberOfPhase(pair[0]);
    int second = fluid.getPhaseNumberOfPhase(pair[1]);
    double firstVolume = fluid.getPhase(first).getMolarVolume();
    double secondVolume = fluid.getPhase(second).getMolarVolume();
    int phases = fluid.getNumberOfPhases();
    double sigma = fluid.getInterfacialTension(pair[0], pair[1]);
    String label = model + " " + pair[0] + "/" + pair[1];
    assertTrue(Double.isFinite(sigma) && sigma > 1e-6 && sigma < 0.2, label + " = " + sigma);
    if ("gas".equals(pair[0]) && "oil".equals(pair[1])) {
      assertTrue(sigma > 0.003 && sigma < 0.06, label + " = " + sigma);
    } else if (!("Parachor".equals(model) && "oil".equals(pair[0]))) {
      // Explicit Parachor is not calibrated for oil/water and can suffer component cancellation.
      assertTrue(sigma > 0.01 && sigma < 0.15, label + " = " + sigma);
    }
    if ("cDFT".equals(model) && "aqueous".equals(pair[1])) {
      double expected = "gas".equals(pair[0]) ? new ParachorSurfaceTension(fluid).calcSurfaceTension(first, second)
          : new FirozabadiRamleyInterfaceTension(fluid).calcSurfaceTension(first, second);
      assertEquals(expected, sigma, 1e-12, "Aqueous cDFT must use its documented correlation fallback");
    }
    assertEquals(sigma, fluid.getInterphaseProperties().getSurfaceTension(first, second), 1e-10, label);
    assertEquals(sigma, fluid.getInterfacialTension(first, second), 1e-10, label);
    assertEquals(firstVolume, fluid.getPhase(first).getMolarVolume(), 0.0, label);
    assertEquals(secondVolume, fluid.getPhase(second).getMolarVolume(), 0.0, label);
    assertEquals(phases, fluid.getNumberOfPhases());
    return sigma;
  }

  /**
   * Checks gas/oil, gas/water and oil/water as independent two-phase calculations for all models and both EOS.
   *
   * @param pr whether to use PR
   * @param model named model
   * @param pair interface case
   */
  @ParameterizedTest(name = "PR={0}, {1}, pair={2}")
  @MethodSource("twoPhaseCases")
  void twoPhaseNumbersAreFiniteAndRepeatable(boolean pr, String model, int pair) {
    SystemInterface fluid;
    if (pair == 0) {
      fluid = fluid(pr, 310.0, 50.0, new String[] {"methane", "n-decane"}, new double[] {0.6, 0.4});
    } else if (pair == 1) {
      fluid = fluid(pr, 313.15, 50.0, new String[] {"CO2", "water"}, new double[] {0.02, 0.98});
    } else {
      fluid = fluid(pr, 300.0, 1.0, new String[] {"n-heptane", "water"}, new double[] {0.5, 0.5});
    }
    assertEquals(2, fluid.getNumberOfPhases());
    String[] names = PAIRS[pair];
    fluid.getInterphaseProperties().setInterfacialTensionModel(names[0], names[1], model);
    checkInterface(fluid, model, names);
  }

  /**
   * Checks all three interfaces in the same fluid, including repeated evaluations in reverse interface order.
   *
   * @param pr whether to use PR
   * @param model named model
   * @param temperature temperature in K
   */
  @ParameterizedTest(name = "PR={0}, {1}, T={2} K")
  @MethodSource("threePhaseCases")
  void threePhaseNumbersUseTheRequestedBulkPhases(boolean pr, String model, double temperature) {
    SystemInterface fluid = threePhaseFluid(pr, temperature);
    double[] tensions = new double[PAIRS.length];
    for (int i = 0; i < PAIRS.length; i++) {
      String[] pair = PAIRS[i];
      fluid.getInterphaseProperties().setInterfacialTensionModel(pair[0], pair[1], model);
      tensions[i] = checkInterface(fluid, model, pair);
    }
    for (int i = PAIRS.length - 1; i >= 0; i--) {
      assertEquals(tensions[i], fluid.getInterfacialTension(PAIRS[i][0], PAIRS[i][1]), 1e-10);
    }
  }

  /**
   * Locks the routing and useful magnitude of every numbered model set for all three phase pairs.
   *
   * @param pr whether to use PR
   * @param set numbered model set
   */
  @ParameterizedTest(name = "PR={0}, model set={1}")
  @MethodSource("numberedCases")
  void numberedSetsCalculateAllThreeInterfaces(boolean pr, int set) {
    SystemInterface fluid = threePhaseFluid(pr, 300.0);
    fluid.getInterphaseProperties().setInterfacialTensionModel(set);
    for (String[] pair : PAIRS) {
      checkInterface(fluid, "set " + set, pair);
    }
  }

  /**
   * Checks correlations against a fixed SRK benchmark and their expected temperature trend at a nearby state.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void correlationReferenceValuesAndTemperatureTrend(boolean pr) {
    SystemInterface cold = threePhaseFluid(pr, 300.0);
    SystemInterface warm = threePhaseFluid(pr, 320.0);
    double[] references = {0.019287109180868643, 0.07106700485341315, 0.050062130249984675};
    for (int i = 0; i < PAIRS.length; i++) {
      String[] pair = PAIRS[i];
      String model = i == 2 ? "Firozabadi Ramley" : "Parachor";
      cold.getInterphaseProperties().setInterfacialTensionModel(pair[0], pair[1], model);
      warm.getInterphaseProperties().setInterfacialTensionModel(pair[0], pair[1], model);
      double coldTension = cold.getInterfacialTension(pair[0], pair[1]);
      double warmTension = warm.getInterfacialTension(pair[0], pair[1]);
      assertTrue(warmTension < coldTension, model + " " + pair[0] + "/" + pair[1]);
      if (!pr) {
        assertEquals(references[i], coldTension, references[i] * 1e-6);
      }
    }
  }

}
