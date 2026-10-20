package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the fail-closed catalytic-reforming stoichiometric balance receipt. */
public class RefineryCatalyticReformingStoichiometricBalanceTest {
  @Test
  public void mixedHexaneAndCyclohexaneCaseClosesMassAndAtoms() {
    RefineryCatalyticReformingStoichiometricBalance receipt = RefineryCatalyticReformingStoichiometricBalance
        .calculate(6, 1.0, 2.0, 0.5, 0.25);

    assertEquals(6, receipt.getCarbonNumber());
    assertEquals(0.5, receipt.getNaphtheneReactionExtentKmolPerHour(), 0.0);
    assertEquals(0.5, receipt.getParaffinReactionExtentKmolPerHour(), 0.0);
    assertEquals(0.5, receipt.getUnconvertedNaphtheneFlowKmolPerHour(), 0.0);
    assertEquals(1.5, receipt.getUnconvertedParaffinFlowKmolPerHour(), 0.0);
    assertEquals(1.0, receipt.getAromaticProductFlowKmolPerHour(), 0.0);
    assertEquals(3.5, receipt.getHydrogenProductFlowKmolPerHour(), 0.0);
    assertEquals(256.5156, receipt.getFeedMassFlowKgPerHour(), 1.0e-10);
    assertEquals(78.11364, receipt.getAromaticProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(7.05558, receipt.getHydrogenProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(receipt.getFeedMassFlowKgPerHour(), receipt.getTotalProductMassFlowKgPerHour(), 1.0e-10);
    assertEquals(304.5180877888129, receipt.getAromaticProductKgPerTonneFeed(), 1.0e-10);
    assertEquals(27.505461656133193, receipt.getHydrogenProductKgPerTonneFeed(), 1.0e-10);
    assertEquals(0.0, receipt.getMassBalanceResidualKgPerHour(), 1.0e-10);
    assertEquals(0.0, receipt.getCarbonAtomBalanceResidualKmolPerHour(), 1.0e-12);
    assertEquals(0.0, receipt.getHydrogenAtomBalanceResidualKmolPerHour(), 1.0e-12);
  }

  @Test
  public void zeroConversionReturnsFeedAndNoProducts() {
    RefineryCatalyticReformingStoichiometricBalance receipt = RefineryCatalyticReformingStoichiometricBalance
        .calculate(7, 1.0, 1.0, 0.0, 0.0);

    assertEquals(1.0, receipt.getUnconvertedNaphtheneFlowKmolPerHour(), 0.0);
    assertEquals(1.0, receipt.getUnconvertedParaffinFlowKmolPerHour(), 0.0);
    assertEquals(0.0, receipt.getAromaticProductFlowKmolPerHour(), 0.0);
    assertEquals(0.0, receipt.getHydrogenProductFlowKmolPerHour(), 0.0);
    assertEquals(receipt.getFeedMassFlowKgPerHour(), receipt.getTotalProductMassFlowKgPerHour(), 0.0);
  }

  @Test
  public void completeConversionsUseThreeAndFourHydrogenStoichiometry() {
    RefineryCatalyticReformingStoichiometricBalance receipt = RefineryCatalyticReformingStoichiometricBalance
        .calculate(8, 2.0, 3.0, 1.0, 1.0);

    assertEquals(5.0, receipt.getAromaticProductFlowKmolPerHour(), 0.0);
    assertEquals(18.0, receipt.getHydrogenProductFlowKmolPerHour(), 0.0);
    assertEquals(0.0, receipt.getMassBalanceResidualKgPerHour(), 1.0e-10);
  }

  @Test
  public void invalidInputsFailClosed() {
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingStoichiometricBalance.calculate(5, 1.0, 1.0, 0.5, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingStoichiometricBalance.calculate(6, -1.0, 1.0, 0.5, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingStoichiometricBalance.calculate(6, 0.0, 0.0, 0.5, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingStoichiometricBalance.calculate(6, 1.0, 1.0, Double.NaN, 0.5));
    assertThrows(IllegalArgumentException.class,
        () -> RefineryCatalyticReformingStoichiometricBalance.calculate(6, 1.0, 1.0, 0.5, 1.01));
  }
}
