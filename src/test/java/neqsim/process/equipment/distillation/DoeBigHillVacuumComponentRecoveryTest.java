package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DoeBigHillVacuumComponentRecovery.ProductRecovery;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;

/** Qualification tests for {@link DoeBigHillVacuumComponentRecovery}. */
public class DoeBigHillVacuumComponentRecoveryTest {
  private static final double RECOVERY_CLOSURE_TOLERANCE = 5.0e-2;

  /** Require component flows and recoveries to be conservative and defensive. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  public void solvedCaseReturnsClosedDefensiveComponentRecoveries() {
    DoeBigHillVacuumFractionationCase model = createCase("Big Hill component recovery");
    model.getColumn().run(UUID.randomUUID());

    DoeBigHillVacuumComponentRecovery result =
        DoeBigHillVacuumComponentRecovery.evaluate(model);
    String[] expectedNames = {
        "DOE_BH_650_850_PC", "DOE_BH_850_1050_PC", "DOE_BH_1050_PLUS_PC" };
    assertArrayEquals(expectedNames, result.getComponentNames());
    assertNotSame(result.getComponentNames(), result.getComponentNames());

    double[] feedFlows = result.getFeedComponentMolarFlowsMolPerHour();
    assertEquals(expectedNames.length, feedFlows.length);
    assertNotSame(feedFlows, result.getFeedComponentMolarFlowsMolPerHour());
    ProductRecovery[] products = result.getProducts();
    assertEquals(2, products.length);
    assertEquals("Overhead", products[0].getProductLabel());
    assertEquals("Bottoms", products[1].getProductLabel());
    assertEquals(products[0], result.getProduct("Overhead"));
    assertEquals(products[1], result.getProduct("Bottoms"));
    assertNotSame(products, result.getProducts());
    assertThrows(IllegalArgumentException.class, () -> result.getProduct("VGO"));

    for (int componentIndex = 0; componentIndex < expectedNames.length; componentIndex++) {
      assertTrue(Double.isFinite(feedFlows[componentIndex]));
      assertTrue(feedFlows[componentIndex] > 0.0);
      double productFlowSum = 0.0;
      double recoverySum = 0.0;
      for (ProductRecovery product : products) {
        double[] componentFlows = product.getComponentMolarFlowsMolPerHour();
        double[] recoveries = product.getComponentMolarRecoveries();
        assertNotSame(componentFlows, product.getComponentMolarFlowsMolPerHour());
        assertNotSame(recoveries, product.getComponentMolarRecoveries());
        assertEquals(expectedNames.length, componentFlows.length);
        assertEquals(expectedNames.length, recoveries.length);
        assertTrue(componentFlows[componentIndex] >= 0.0);
        assertTrue(recoveries[componentIndex] >= 0.0);
        assertTrue(recoveries[componentIndex] <= 1.0 + RECOVERY_CLOSURE_TOLERANCE);
        assertEquals(componentFlows[componentIndex] / feedFlows[componentIndex],
            recoveries[componentIndex], 1.0e-12);
        assertEquals(componentFlows[componentIndex],
            product.getComponentMolarFlowMolPerHour(expectedNames[componentIndex]), 0.0);
        assertEquals(recoveries[componentIndex],
            product.getComponentMolarRecovery(expectedNames[componentIndex]), 0.0);
        productFlowSum += componentFlows[componentIndex];
        recoverySum += recoveries[componentIndex];
      }
      assertEquals(feedFlows[componentIndex], productFlowSum,
          RECOVERY_CLOSURE_TOLERANCE * feedFlows[componentIndex]);
      assertEquals(1.0, recoverySum, RECOVERY_CLOSURE_TOLERANCE);
    }

    assertTrue(result.getMaximumComponentRecoveryClosureError()
        <= RECOVERY_CLOSURE_TOLERANCE);
    assertThrows(IllegalArgumentException.class,
        () -> products[0].getComponentMolarRecovery("unknown"));

    feedFlows[0] = -1.0;
    products[0].getComponentMolarFlowsMolPerHour()[0] = -1.0;
    products[0].getComponentMolarRecoveries()[0] = -1.0;
    assertTrue(result.getFeedComponentMolarFlowsMolPerHour()[0] > 0.0);
    assertTrue(products[0].getComponentMolarFlowsMolPerHour()[0] >= 0.0);
    assertTrue(products[0].getComponentMolarRecoveries()[0] >= 0.0);
  }

  /** Reject null and unsolved cases before returning recovery evidence. */
  @Test
  public void invalidCasesFailClosed() {
    assertThrows(NullPointerException.class,
        () -> DoeBigHillVacuumComponentRecovery.evaluate(null));
    assertThrows(IllegalStateException.class,
        () -> DoeBigHillVacuumComponentRecovery.evaluate(createCase("Unsolved recovery")));
  }

  private static DoeBigHillVacuumFractionationCase createCase(String name) {
    OperatingInputs inputs =
        new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
    return DoeBigHillVacuumFractionationCase.create(name, 1000.0, inputs);
  }
}
