package neqsim.thermodynamicoperations.flashops;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class RachfordRiceTest {
  /** Verifies the configured method selector and restores it even when a calculation fails. */
  @Test
  void testCalcBeta() throws Exception {
    double[] z = new double[] {0.7, 0.3};
    double[] K = new double[] {2.0, 0.01};
    String startMethod = RachfordRice.getMethod();
    try {
      RachfordRice rachfordRice = new RachfordRice();
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
      RachfordRice.setMethod("Nielsen2023");
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
      RachfordRice.setMethod("Michelsen2001");
      Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
    } finally {
      RachfordRice.setMethod(startMethod);
    }
  }

  /** Verifies the default public calculation path propagates failures to the test. */
  @Test
  void testCalcBetaMethod2() throws Exception {
    double[] z = new double[] {0.7, 0.3};
    double[] K = new double[] {2.0, 0.01};
    RachfordRice rachfordRice = new RachfordRice();
    Assertions.assertEquals(0.407070707, rachfordRice.calcBeta(K, z), 1e-6);
  }

  /** Verifies reciprocal orientation cannot mutate the caller's equilibrium-ratio array. */
  @Test
  void testCalcBetaNielsen2023DoesNotMutateInputArray() {
    double[] z = new double[] {0.9, 0.1};
    double[] k = new double[] {5.0, 0.2};
    double[] originalK = k.clone();

    RachfordRice rachfordRice = new RachfordRice();
    double beta = Assertions.assertDoesNotThrow(() -> rachfordRice.calcBetaNielsen2023(k, z));

    Assertions.assertTrue(beta > 0.0 && beta < 1.0);
    Assertions.assertArrayEquals(originalK, k, 0.0, "The caller's K array must remain unchanged");
  }

  /**
   * Checks conserved liquid inventory for exact-zero and extremely small partition coefficients in every method.
   *
   * @param method selected public calculation path
   * @param high large partition coefficient
   * @param low small partition coefficient
   * @param highFraction overall fraction of the high-K component
   */
  @ParameterizedTest
  @MethodSource("extremePartitionCases")
  void extremePartitionCoefficientsPreserveBalance(String method, double high, double low, double highFraction)
      throws Exception {
    double[] k = {high, low};
    double[] z = {highFraction, 1.0 - highFraction};
    double expected = highFraction / (1.0 - low) - (1.0 - highFraction) / (high - 1.0);
    verifySplit(method, k, z, expected);
  }

  /**
   * Ensures absent components cannot alter bounds or introduce undefined zero-times-infinity expressions.
   *
   * @param method selected public calculation path
   */
  @ParameterizedTest
  @MethodSource("methods")
  void zeroFeedComponentsDoNotAffectSplit(String method) throws Exception {
    verifySplit(method, new double[] {1e197, 1e-197, 0.0, Double.POSITIVE_INFINITY, Double.NaN},
        new double[] {0.7, 0.3, 0.0, 0.0, 0.0}, 0.7);
  }

  /**
   * A component with unit partition coefficient contributes exactly zero to the Rachford-Rice equation.
   *
   * @param method selected public calculation path
   */
  @ParameterizedTest
  @MethodSource("methods")
  void unitPartitionCoefficientRetainsItsInventory(String method) throws Exception {
    verifySplit(method, new double[] {1e197, 1e-197, 1.0}, new double[] {0.2, 0.3, 0.5}, 0.4);
  }

  /**
   * Uses normalized liquid and vapor compositions to construct a known multicomponent root.
   *
   * @param method selected public calculation path
   */
  @ParameterizedTest
  @MethodSource("methods")
  void mixedPartitionCoefficientsPreserveKnownRoot(String method) throws Exception {
    double[] k = {1e197, 1e-197, 3.0, 0.2, 1.0};
    double[] liquid = {3e-198, 0.3, 0.1, 0.25, 0.35};
    for (double expected : new double[] {0.2, 0.7}) {
      double[] z = new double[k.length];
      for (int component = 0; component < k.length; component++) {
        z[component] = (1.0 - expected) * liquid[component] + expected * k[component] * liquid[component];
      }
      verifySplit(method, k, z, expected);
    }
  }

  /**
   * Supplies both array algorithms and the equivalent system-based Michelsen path.
   *
   * @return public calculation paths
   */
  private static Stream<String> methods() {
    return Stream.of("Michelsen2001", "Nielsen2023", "System");
  }

  /**
   * Exercises ordinary and reciprocal orientations, exact zeros, and the mixed-brine range of K values.
   *
   * @return method, large K, small K, and large-K feed fraction
   */
  private static Stream<Arguments> extremePartitionCases() {
    List<Arguments> cases = new ArrayList<>();
    for (String method : new String[] {"Michelsen2001", "Nielsen2023", "System"}) {
      for (double[] values : new double[][] {{10.0, 0.0, 0.4}, {10.0, 1e-40, 0.4}, {1e197, 1e-197, 0.2},
          {1e197, 1e-197, 0.7}, {1e197, 0.0, 0.7}}) {
        cases.add(Arguments.of(method, values[0], values[1], values[2]));
      }
    }
    return cases.stream();
  }

  /**
   * Verifies the analytic two-phase root, both phase normalizations, component balances, and immutable inputs.
   *
   * @param method calculation path
   * @param k equilibrium ratios
   * @param z normalized overall composition
   * @param expected analytic vapor fraction
   * @throws Exception if the public solver cannot calculate the root
   */
  private static void verifySplit(String method, double[] k, double[] z, double expected) throws Exception {
    double[] originalK = k.clone();
    double[] originalZ = z.clone();
    RachfordRice solver = new RachfordRice();
    double beta;
    if ("System".equals(method)) {
      SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
      String[] names = {"methane", "ethane", "n-decane", "nitrogen", "water"};
      for (int component = 0; component < z.length; component++) {
        fluid.addComponent(names[component], z[component]);
      }
      fluid.init(0);
      for (int component = 0; component < k.length; component++) {
        fluid.getPhase(0).getComponent(component).setK(k[component]);
      }
      beta = solver.calcBetaS(fluid);
      for (int component = 0; component < k.length; component++) {
        Assertions.assertEquals(originalK[component], fluid.getPhase(0).getComponent(component).getK(), 0.0);
      }
    } else {
      beta = "Nielsen2023".equals(method) ? solver.calcBetaNielsen2023(k, z) : solver.calcBetaMichelsen2001(k, z);
    }
    Assertions.assertEquals(expected, beta, 1e-11, method + " vapor fraction");
    Assertions.assertArrayEquals(originalK, k, 0.0, "K input must remain unchanged");
    Assertions.assertArrayEquals(originalZ, z, 0.0, "Feed input must remain unchanged");
    double liquidSum = 0.0;
    double vaporSum = 0.0;
    for (int component = 0; component < k.length; component++) {
      if (z[component] == 0.0) {
        continue;
      }
      double liquid;
      double vapor;
      if (k[component] >= 1.0) {
        double denominator = beta + (1.0 - beta) / k[component];
        liquid = (z[component] / k[component]) / denominator;
        vapor = z[component] / denominator;
      } else {
        liquid = z[component] / (1.0 - beta + beta * k[component]);
        vapor = k[component] * liquid;
      }
      Assertions.assertTrue(Double.isFinite(liquid) && Double.isFinite(vapor));
      Assertions.assertEquals(z[component], beta * vapor + (1.0 - beta) * liquid, 1e-12);
      liquidSum += liquid;
      vaporSum += vapor;
    }
    Assertions.assertEquals(1.0, liquidSum, 1e-10, method + " liquid normalization");
    Assertions.assertEquals(1.0, vaporSum, 1e-10, method + " vapor normalization");
  }
}
