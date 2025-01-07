package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * AmineDiffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class AmineDiffusivity extends SiddiqiLucasMethod {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AmineDiffusivity.class);

  /**
   * <p>
   * Constructor for AmineDiffusivity.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public AmineDiffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public void calcEffectiveDiffusionCoefficients() {
    super.calcEffectiveDiffusionCoefficients();
    double co2waterdiff =
        0.03389 * Math.exp(-2213.7 / liquidPhase.getPhase().getTemperature()) * 1e-4; // Tammi
                                                                                      // (1994)
                                                                                      // -
                                                                                      // Pcheco
    double n2owaterdiff =
        0.03168 * Math.exp(-2209.4 / liquidPhase.getPhase().getTemperature()) * 1e-4;
    double n2oaminediff = 5.533e-8 * liquidPhase.getPhase().getTemperature()
        / Math.pow(liquidPhase.getViscosity(), 0.545) * 1e-4; // stoke einstein - pacheco
    try {
      double molConsMDEA = liquidPhase.getPhase().getComponent("MDEA").getx()
          * liquidPhase.getPhase().getDensity() / liquidPhase.getPhase().getMolarMass();
      molConsMDEA += liquidPhase.getPhase().getComponent("MDEA+").getx()
          * liquidPhase.getPhase().getDensity() / liquidPhase.getPhase().getMolarMass();
      effectiveDiffusionCoefficient[liquidPhase.getPhase().getComponent("CO2")
          .getComponentNumber()] = n2oaminediff * co2waterdiff / n2owaterdiff;
      effectiveDiffusionCoefficient[liquidPhase.getPhase().getComponent("MDEA")
          .getComponentNumber()] =
              0.0207
                  * Math.exp(
                      -2360.7 / liquidPhase.getPhase().getTemperature() - 24.727e-5 * molConsMDEA)
                  * 1e-4;
    } catch (Exception ex) {
      logger.error("error eff diff calc ", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    calcEffectiveDiffusionCoefficients();
    if (liquidPhase.getPhase().getComponent(i).getComponentName().equals("MDEA")) {
      return effectiveDiffusionCoefficient[liquidPhase.getPhase().getComponent("MDEA")
          .getComponentNumber()];
    } else {
      return effectiveDiffusionCoefficient[liquidPhase.getPhase().getComponent("CO2")
          .getComponentNumber()];
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    calcEffectiveDiffusionCoefficients();
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        if (liquidPhase.getPhase().getComponent(i).getComponentName().equals("MDEA")) {
          binaryDiffusionCoefficients[i][j] = effectiveDiffusionCoefficient[liquidPhase.getPhase()
              .getComponent("MDEA").getComponentNumber()];
        } else {
          binaryDiffusionCoefficients[i][j] = effectiveDiffusionCoefficient[liquidPhase.getPhase()
              .getComponent("CO2").getComponentNumber()];
        }
      }
    }
    return binaryDiffusionCoefficients;
  }
}
