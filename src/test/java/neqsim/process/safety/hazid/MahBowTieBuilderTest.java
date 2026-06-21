package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.safety.risk.bowtie.BowTieModel;

/**
 * Unit tests for {@link MahBowTieBuilder} and {@link MahCatalogue}.
 *
 * @author ESOL
 * @version 1.0
 */
class MahBowTieBuilderTest {

  @Test
  void catalogueHasEntryForEveryMah() {
    for (MahType m : MahType.values()) {
      assertNotNull(MahCatalogue.threatsFor(m), m.name() + " threats");
      assertNotNull(MahCatalogue.consequencesFor(m), m.name() + " consequences");
      assertNotNull(MahCatalogue.barriersFor(m), m.name() + " barriers");
      assertFalse(MahCatalogue.threatsFor(m).isEmpty(), m.name() + " threats not empty");
      assertFalse(MahCatalogue.consequencesFor(m).isEmpty(), m.name() + " consequences not empty");
      assertFalse(MahCatalogue.barriersFor(m).isEmpty(), m.name() + " barriers not empty");
    }
  }

  @Test
  void buildTopsideHydrocarbonRelease() {
    BowTieModel m = MahBowTieBuilder.build(MahType.TOPSIDE_HYDROCARBON_RELEASE);
    assertEquals(MahType.TOPSIDE_HYDROCARBON_RELEASE.name(), m.getHazardId());
    assertTrue(m.getThreats().size() >= 4);
    assertTrue(m.getConsequences().size() >= 3);
    assertTrue(m.getBarriers().size() >= 5);
    for (BowTieModel.Threat t : m.getThreats()) {
      assertEquals(MahBowTieBuilder.DEFAULT_THREAT_FREQUENCY, t.getFrequency(), 1e-12);
    }
    for (BowTieModel.Barrier b : m.getBarriers()) {
      assertEquals(MahBowTieBuilder.DEFAULT_BARRIER_PFD, b.getPfd(), 1e-12);
    }
  }

  @Test
  void buildAllMahWithoutError() {
    for (MahType m : MahType.values()) {
      BowTieModel bt = MahBowTieBuilder.build(m);
      assertNotNull(bt);
      assertFalse(bt.getThreats().isEmpty());
      assertFalse(bt.getConsequences().isEmpty());
      assertFalse(bt.getBarriers().isEmpty());
    }
  }
}
