package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;

/**
 * <p>
 * InterphaseAnnularFlow class for annular two-phase pipe flow.
 * </p>
 *
 * <p>
 * Implements transport coefficient correlations specific to annular flow regime, where a liquid
 * film flows along the pipe wall with a gas core in the center. The correlations account for film
 * Reynolds number and wave effects on mass/heat transfer.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseAnnularFlow extends InterphaseStratifiedFlow {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for InterphaseAnnularFlow.
   * </p>
   */
  public InterphaseAnnularFlow() {}

  /**
   * <p>
   * Constructor for InterphaseAnnularFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseAnnularFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For annular flow, the Sherwood number correlation depends on the phase:
   * </p>
   * <ul>
   * <li>Gas phase (core): Uses modified Dittus-Boelter correlation</li>
   * <li>Liquid phase (film): Uses Hewitt &amp; Hall-Taylor correlations accounting for film
   * Reynolds number and wave amplitude</li>
   * </ul>
   */
  @Override
  public double calcSherwoodNumber(int phaseNum, double reynoldsNumber, double schmidtNumber,
      FlowNodeInterface node) {
    if (phaseNum == 0) {
      // Gas core Sherwood number
      // Modified Dittus-Boelter for annular geometry
      if (reynoldsNumber < 2300) {
        return 3.66;
      } else {
        return 0.023 * Math.pow(reynoldsNumber, 0.8) * Math.pow(schmidtNumber, 0.33);
      }
    } else {
      // Liquid film Sherwood number (Hewitt & Hall-Taylor correlation)
      // Accounts for wavy film surface effects
      double filmReynolds = reynoldsNumber;

      if (filmReynolds < 300) {
        // Laminar film
        return 0.332 * Math.pow(filmReynolds, 0.5) * Math.pow(schmidtNumber, 0.33);
      } else {
        // Turbulent/wavy film - enhanced mass transfer
        // Sh = 0.0265 * Re_film^0.8 * Sc^0.33 (Hewitt correlation)
        double baseSh = 0.0265 * Math.pow(filmReynolds, 0.8) * Math.pow(schmidtNumber, 0.33);

        // Wave enhancement factor
        double waveEnhancement = 1.0 + 0.3 * Math.log10(Math.max(filmReynolds / 300.0, 1.0));
        waveEnhancement = Math.min(waveEnhancement, 1.5);

        return baseSh * waveEnhancement;
      }
    }
  }
}
