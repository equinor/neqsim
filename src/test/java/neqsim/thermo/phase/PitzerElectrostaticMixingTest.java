package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests the parameter-free Pitzer unequal-charge electrostatic mixing kernel. */
class PitzerElectrostaticMixingTest {
  /** PHREEQC reference Aphi chosen independently of NeqSim's dielectric correlation. */
  private static final double REFERENCE_APHI = 0.392;

  @Test
  void matchesPublicDomainPhreeqcReferenceValues() {
    double[] ionicStrength = { 0.01, 0.1, 1.0, 5.0 };
    double[] expectedTheta = { -1.0359898656543214, -0.41477912893379515, -0.14413003876826347, -0.06569004968633078 };
    double[] expectedDerivative = { 37.21045792015009, 1.795520864060653, 0.06915494279053402, 0.006497453538073315 };
    double[] result = new double[2];

    for (int i = 0; i < ionicStrength.length; i++) {
      PitzerElectrostaticMixing.calculate(1.0, 2.0, ionicStrength[i], REFERENCE_APHI, result);
      assertEquals(expectedTheta[i], result[0], 2.0e-14);
      assertEquals(expectedDerivative[i], result[1], 2.0e-13);
    }
  }

  @Test
  void derivativeMatchesCenteredFiniteDifference() {
    double ionicStrength = 1.0;
    double step = 1.0e-5;
    double[] lower = new double[2];
    double[] center = new double[2];
    double[] upper = new double[2];
    PitzerElectrostaticMixing.calculate(1.0, 2.0, ionicStrength - step, REFERENCE_APHI, lower);
    PitzerElectrostaticMixing.calculate(1.0, 2.0, ionicStrength, REFERENCE_APHI, center);
    PitzerElectrostaticMixing.calculate(1.0, 2.0, ionicStrength + step, REFERENCE_APHI, upper);
    assertEquals((upper[0] - lower[0]) / (2.0 * step), center[1], 2.0e-9);
  }

  @Test
  void equalChargesAreExactlyZeroAndChargeSignIsSymmetric() {
    double[] positive = new double[2];
    double[] negative = new double[2];
    PitzerElectrostaticMixing.calculate(2.0, 2.0, 1.0, REFERENCE_APHI, positive);
    assertEquals(0.0, positive[0]);
    assertEquals(0.0, positive[1]);

    PitzerElectrostaticMixing.calculate(1.0, 2.0, 1.0, REFERENCE_APHI, positive);
    PitzerElectrostaticMixing.calculate(-1.0, -2.0, 1.0, REFERENCE_APHI, negative);
    assertEquals(positive[0], negative[0], 0.0);
    assertEquals(positive[1], negative[1], 0.0);
  }

  @Test
  void threadLocalWorkspaceIsDeterministic() throws InterruptedException {
    int threadCount = 4;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
    for (int thread = 0; thread < threadCount; thread++) {
      new Thread(() -> {
        try {
          start.await();
          double[] result = new double[2];
          for (int repeat = 0; repeat < 1000; repeat++) {
            PitzerElectrostaticMixing.calculate(1.0, 2.0, 1.0, REFERENCE_APHI, result);
            assertEquals(-0.14413003876826347, result[0], 2.0e-14);
            assertEquals(0.06915494279053402, result[1], 2.0e-13);
          }
        } catch (Throwable ex) {
          failure.compareAndSet(null, ex);
        } finally {
          done.countDown();
        }
      }).start();
    }
    start.countDown();
    done.await();
    if (failure.get() != null) {
      throw new AssertionError("Concurrent Etheta evaluation failed", failure.get());
    }
  }

  @Test
  void rejectsInvalidScientificInputs() {
    double[] result = new double[2];
    assertThrows(IllegalArgumentException.class,
        () -> PitzerElectrostaticMixing.calculate(1.0, -1.0, 1.0, REFERENCE_APHI, result));
    assertThrows(IllegalArgumentException.class,
        () -> PitzerElectrostaticMixing.calculate(1.0, 2.0, -1.0, REFERENCE_APHI, result));
    assertThrows(IllegalArgumentException.class,
        () -> PitzerElectrostaticMixing.calculate(1.0, 2.0, 1.0, REFERENCE_APHI, new double[1]));
  }
}
