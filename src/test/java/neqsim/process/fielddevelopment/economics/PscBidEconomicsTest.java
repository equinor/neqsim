package neqsim.process.fielddevelopment.economics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PscBidEconomics}.
 *
 * @author ESOL
 * @version 1.0
 */
class PscBidEconomicsTest {

  private static PscBidEconomics baseBid() {
    double[] production = {10.0, 25.0, 30.0, 28.0, 24.0, 20.0, 16.0, 13.0, 10.0, 8.0};
    double[] capex = {900.0, 900.0, 400.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    double[] opex = {20.0, 60.0, 80.0, 80.0, 75.0, 70.0, 65.0, 60.0, 55.0, 50.0};
    return new PscBidEconomics().setOilPrice(70.0).setDiscountRate(0.10).setProfitOilShareOffered(0.25)
        .setSignatureBonus(20.0).setExplorationProgramCost(60.0).setDiscoveryDelayYears(4).setChanceOfDiscovery(0.30)
        .setDevelopmentProfile(production, capex, opex);
  }

  @Test
  void stateAndContractorSplitPreTaxNetCashFlow() {
    PscBidEconomics.Result r = baseBid().evaluate();
    double sum = r.getContractorCashUndiscounted() + r.getStateTakeUndiscounted();
    double[] production = {10.0, 25.0, 30.0, 28.0, 24.0, 20.0, 16.0, 13.0, 10.0, 8.0};
    double revenue = 0.0;
    for (double p : production) {
      revenue += p * 70.0;
    }
    double expected = revenue - 2200.0 - 615.0;
    assertEquals(expected, sum, 1e-6);
    assertTrue(r.getGovernmentTake() > 0.0 && r.getGovernmentTake() < 1.0);
  }

  @Test
  void matchesCommunitySkillReferenceValues() {
    PscBidEconomics.Result r = baseBid().evaluate();
    assertEquals(400.1565453750645, r.getEmv(), 1e-6);
    assertEquals(0.8332498831507702, r.getBreakEvenProfitOilShare(), 1e-9);
    assertEquals(0.5990327868852459, r.getGovernmentTake(), 1e-12);
  }

  @Test
  void emvDecreasesWithOfferedShare() {
    PscBidEconomics bid = baseBid();
    assertTrue(bid.emvAtShare(0.10) > bid.emvAtShare(0.40));
    assertTrue(bid.emvAtShare(0.40) > bid.emvAtShare(0.70));
  }

  @Test
  void breakEvenShareGivesZeroEmv() {
    PscBidEconomics bid = baseBid().setChanceOfDiscovery(0.60);
    PscBidEconomics.Result r = bid.evaluate();
    double be = r.getBreakEvenProfitOilShare();
    assertTrue(be > 0.0 && be < 0.99);
    assertEquals(0.0, bid.emvAtShare(be), 1e-4);
    assertEquals(be - 0.25, r.getShareHeadroom(), 1e-12);
  }

  @Test
  void zeroChanceOfDiscoveryLosesPreDiscoveryCosts() {
    PscBidEconomics.Result r = baseBid().setChanceOfDiscovery(0.0).evaluate();
    assertEquals(r.getPvPreDiscoveryCost(), r.getEmv(), 1e-9);
    assertTrue(r.getEmv() < -20.0);
    assertFalse(r.isValueCreating());
    assertEquals(0.0, r.getBreakEvenProfitOilShare(), 1e-12);
  }

  @Test
  void workingInterestScalesLinearly() {
    double full = baseBid().setWorkingInterest(1.0).evaluate().getEmv();
    double seventy = baseBid().setWorkingInterest(0.70).evaluate().getEmv();
    assertEquals(0.70 * full, seventy, 1e-9);
  }

  @Test
  void higherPriceAndCostOilCapRaiseValue() {
    double base = baseBid().evaluate().getEmv();
    assertTrue(baseBid().setOilPrice(85.0).evaluate().getEmv() > base);
    assertTrue(baseBid().setCostOilCap(0.80).evaluate().getEmv() >= base);
  }

  @Test
  void volumeCasesAreWeightedAndNormalised() {
    PscBidEconomics single = baseBid();
    double mid = single.evaluate().getPvDevelopmentGivenDiscovery();
    PscBidEconomics cases = baseBid().setVolumeCases(new double[] {0.6, 1.0, 1.5}, new double[] {3.0, 4.0, 3.0});
    double expected = 0.3
        * baseBid().setVolumeCases(new double[] {0.6}, new double[] {1.0}).evaluate().getPvDevelopmentGivenDiscovery()
        + 0.4 * mid + 0.3 * baseBid().setVolumeCases(new double[] {1.5}, new double[] {1.0}).evaluate()
            .getPvDevelopmentGivenDiscovery();
    assertEquals(expected, cases.evaluate().getPvDevelopmentGivenDiscovery(), 1e-9);
  }

  @Test
  void priceLinkedShareReducesContractorValue() {
    double flat = baseBid().evaluate().getEmv();
    double stepped = baseBid().setPriceLinkedShare(0.005, 40.0).evaluate().getEmv();
    assertTrue(stepped < flat);
  }

  @Test
  void mismatchedProfileLengthsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new PscBidEconomics()
        .setDevelopmentProfile(new double[] {1.0, 2.0}, new double[] {1.0}, new double[] {1.0, 2.0}));
  }
}
