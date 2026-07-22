package neqsim.process.safety.hazid;

import java.io.Serializable;
import java.util.List;
import neqsim.process.safety.risk.bowtie.BowTieModel;

/**
 * Builds a {@link BowTieModel} skeleton for a given {@link MahType}, pre-populated with the typical threats,
 * consequences, and barrier classes from {@link MahCatalogue}.
 *
 * <p>
 * The resulting bow-tie is a workshop starting point: PFD values, frequencies and severities are placeholders that the
 * project team must validate and refine. The intent is to ensure no "obvious" threat or barrier class is missed at
 * HAZID screening.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class MahBowTieBuilder implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Default placeholder threat frequency (events per year). */
  public static final double DEFAULT_THREAT_FREQUENCY = 1.0e-3;
  /** Default placeholder barrier PFD-on-demand. */
  public static final double DEFAULT_BARRIER_PFD = 1.0e-2;
  /** Default placeholder consequence severity (1..5). */
  public static final int DEFAULT_CONSEQUENCE_SEVERITY = 4;

  private MahBowTieBuilder() {
    // utility class
  }

  /**
   * Builds a bow-tie skeleton for the requested MAH.
   *
   * @param mah MAH type
   * @return populated {@link BowTieModel}
   */
  public static BowTieModel build(MahType mah) {
    if (mah == null) {
      throw new IllegalArgumentException("mah must not be null");
    }
    BowTieModel model = new BowTieModel(mah.name(), mah.getDescription());

    List<String> threats = MahCatalogue.threatsFor(mah);
    for (int i = 0; i < threats.size(); i++) {
      model.addThreat(new BowTieModel.Threat("T" + (i + 1), threats.get(i), DEFAULT_THREAT_FREQUENCY));
    }

    List<String> cons = MahCatalogue.consequencesFor(mah);
    for (int i = 0; i < cons.size(); i++) {
      model.addConsequence(new BowTieModel.Consequence("C" + (i + 1), cons.get(i), DEFAULT_CONSEQUENCE_SEVERITY));
    }

    List<String> barriers = MahCatalogue.barriersFor(mah);
    for (int i = 0; i < barriers.size(); i++) {
      BowTieModel.Barrier b = new BowTieModel.Barrier("B" + (i + 1), barriers.get(i), DEFAULT_BARRIER_PFD);
      model.addBarrier(b);
    }

    return model;
  }
}
