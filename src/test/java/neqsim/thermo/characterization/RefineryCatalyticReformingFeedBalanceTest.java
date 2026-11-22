package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests multi-cut catalytic-reforming aggregation and defensive input handling. */
public class RefineryCatalyticReformingFeedBalanceTest {
  @Test
  public void mixedCarbonNumberFeedAggregatesClosedCutReceipts() {
    RefineryCatalyticReformingFeedBalance receipt = RefineryCatalyticReformingFeedBalance.calculate(new int[] {6, 8},
        new double[] {1.0, 2.0}, new double[] {2.0, 3.0}, new double[] {0.5, 1.0}, new double[] {0.25, 1.0});

    assertEquals(2, receipt.getCutBalances().length);
    assertEquals(3.0, receipt.getNaphtheneFeedFlowKmolPerHour(), 0.0);
    assertEquals(5.0, receipt.getParaffinFeedFlowKmolPerHour(), 0.0);
    assertEquals(0.5, receipt.getUnconvertedNaphtheneFlowKmolPerHour(), 0.0);
    assertEquals(1.5, receipt.getUnconvertedParaffinFlowKmolPerHour(), 0.0);
    assertEquals(6.0, receipt.getAromaticProductFlowKmolPerHour(), 0.0);
    assertEquals(21.5, receipt.getHydrogenProductFlowKmolPerHour(), 0.0);
    assertEquals(823.63844, receipt.getFeedMassFlowKgPerHour(), 1.0e-10);
    assertEquals(608.95064, receipt.getAromaticProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(43.34142, receipt.getHydrogenProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(780.29702, receipt.getReformateMassFlowKgPerHour(), 1.0e-10);
    assertEquals(823.63844, receipt.getTotalProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(0.9473780995457182, receipt.getReformateMassYieldFraction(), 1.0e-14);
    assertEquals(0.780408773059264, receipt.getAromaticMassFractionOfReformate(), 1.0e-14);
    assertEquals(739.3421802896912, receipt.getAromaticProductKgPerTonneFeed(), 1.0e-10);
    assertEquals(52.62190045428186, receipt.getHydrogenProductKgPerTonneFeed(), 1.0e-10);
    assertEquals(0.0, receipt.getMassBalanceResidualKgPerHour(), 1.0e-10);
    assertEquals(0.0, receipt.getCarbonAtomBalanceResidualKmolPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getHydrogenAtomBalanceResidualKmolPerHour(), 1.0e-12);
  }

  @Test
  public void returnedArraysCannotMutateReceipt() {
    int[] carbonNumbers = {6, 8};
    RefineryCatalyticReformingFeedBalance receipt = RefineryCatalyticReformingFeedBalance.calculate(carbonNumbers,
        new double[] {1.0, 2.0}, new double[] {2.0, 3.0}, new double[] {0.5, 1.0}, new double[] {0.25, 1.0});

    carbonNumbers[0] = 12;
    int[] returnedNumbers = receipt.getCarbonNumbers();
    returnedNumbers[1] = 10;
    RefineryCatalyticReformingStoichiometricBalance[] returnedBalances = receipt.getCutBalances();
    returnedBalances[0] = null;

    assertEquals(6, receipt.getCarbonNumbers()[0]);
    assertEquals(8, receipt.getCarbonNumbers()[1]);
    assertNotNull(receipt.getCutBalances()[0]);
  }

  @Test
  public void invalidShapesRowsAndDuplicateCarbonNumbersFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> RefineryCatalyticReformingFeedBalance.calculate(null,
        new double[] {1.0}, new double[] {1.0}, new double[] {0.5}, new double[] {0.5}));
    assertThrows(IllegalArgumentException.class, () -> RefineryCatalyticReformingFeedBalance.calculate(new int[] {6, 8},
        new double[] {1.0}, new double[] {1.0, 1.0}, new double[] {0.5, 0.5}, new double[] {0.5, 0.5}));
    assertThrows(IllegalArgumentException.class, () -> RefineryCatalyticReformingFeedBalance.calculate(new int[] {6, 6},
        new double[] {1.0, 1.0}, new double[] {1.0, 1.0}, new double[] {0.5, 0.5}, new double[] {0.5, 0.5}));
    assertThrows(IllegalArgumentException.class, () -> RefineryCatalyticReformingFeedBalance.calculate(new int[] {6},
        new double[] {0.0}, new double[] {0.0}, new double[] {0.5}, new double[] {0.5}));
  }
}
