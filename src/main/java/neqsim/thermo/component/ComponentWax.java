package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentWax class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentWax extends ComponentSolid {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentWax.class);

  /**
   * <p>
   * Constructor for ComponentWax.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentWax(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase1) {
    if (!isWaxFormer()) {
      fugacityCoefficient = 1.0e50;
      return fugacityCoefficient;
    }

    return fugcoef2(phase1);
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef2(PhaseInterface phase1) {
    try {
      refPhase.setTemperature(phase1.getTemperature());
    } catch (Exception ex) {
      // System.out.println("compname " + componentName);
      logger.error(ex.getMessage(), ex);
    }
    refPhase.setPressure(phase1.getPressure());
    refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 1, PhaseType.LIQUID, 1.0);
    refPhase.getComponent(0).fugcoef(refPhase);

    double liquidPhaseFugacity =
        refPhase.getComponent(0).getFugacityCoefficient() * refPhase.getPressure();

    double liquidDenisty = refPhase.getMolarVolume();
    double solidDensity = liquidDenisty * 0.9;
    double refPressure = 1.0;
    double presTerm = -(liquidDenisty - solidDensity) * (phase1.getPressure() - refPressure) / R
        / phase1.getTemperature();

    // Heat capacity difference solid-liquid (Pedersen et al., 1991)
    // DeltaCp_SL = 0.3033 * MW - 4.635e-4 * MW * T [cal/mol/K], converted to J
    double mw = getMolarMass() * 1000.0;
    double deltaCpSL = (0.3033 * mw - 4.635e-4 * mw * phase1.getTemperature()) * 4.184;
    double cpTerm = deltaCpSL / R * (getTriplePointTemperature() / phase1.getTemperature() - 1.0
        - Math.log(getTriplePointTemperature() / phase1.getTemperature()));

    SolidFug =
        getx() * liquidPhaseFugacity
            * Math.exp(-getHeatOfFusion() / (R * phase1.getTemperature())
                * (1.0 - phase1.getTemperature() / getTriplePointTemperature()) + cpTerm
                + presTerm);

    fugacityCoefficient = SolidFug / (phase1.getPressure() * getx());
    return fugacityCoefficient;
  }
}
