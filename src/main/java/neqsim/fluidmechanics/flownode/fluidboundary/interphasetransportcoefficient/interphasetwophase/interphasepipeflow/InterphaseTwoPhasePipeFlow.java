package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.InterphaseTwoPhase;

/**
 * <p>
 * InterphaseTwoPhasePipeFlow class.
 * </p>
 *
 * <p>
 * Base class for interphase transport coefficients in two-phase pipe flow. Provides methods for
 * calculating Sherwood numbers (mass transfer) and Nusselt numbers (heat transfer) based on flow
 * pattern-specific correlations.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphaseTwoPhasePipeFlow extends InterphaseTwoPhase {
  /**
   * <p>
   * Constructor for InterphaseTwoPhasePipeFlow.
   * </p>
   */
  public InterphaseTwoPhasePipeFlow() {}

  /**
   * <p>
   * Constructor for InterphaseTwoPhasePipeFlow.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphaseTwoPhasePipeFlow(FlowNodeInterface node) {
    // flowNode = node;
  }

  /**
   * <p>
   * Calculates the Sherwood number for interphase mass transfer. The Sherwood number is the
   * dimensionless mass transfer coefficient: Sh = k_L * L / D where k_L is the mass transfer
   * coefficient, L is the characteristic length, and D is the diffusivity.
   * </p>
   *
   * @param phaseNum the phase number (0 for gas, 1 for liquid)
   * @param reynoldsNumber the Reynolds number
   * @param schmidtNumber the Schmidt number (μ/ρD)
   * @param node the flow node
   * @return the Sherwood number
   */
  public double calcSherwoodNumber(int phaseNum, double reynoldsNumber, double schmidtNumber,
      FlowNodeInterface node) {
    // Default correlation: Chilton-Colburn analogy for turbulent flow
    // Sh = 0.023 * Re^0.83 * Sc^0.33 (Dittus-Boelter type)
    if (reynoldsNumber < 2300) {
      // Laminar flow - constant Sherwood number for fully developed flow
      return 3.66;
    } else {
      // Turbulent flow
      return 0.023 * Math.pow(reynoldsNumber, 0.83) * Math.pow(schmidtNumber, 0.33);
    }
  }

  /**
   * <p>
   * Calculates the wall Sherwood number for mass transfer to the pipe wall.
   * </p>
   *
   * @param phaseNum the phase number (0 for gas, 1 for liquid)
   * @param reynoldsNumber the Reynolds number
   * @param schmidtNumber the Schmidt number
   * @param node the flow node
   * @return the wall Sherwood number
   */
  public double calcWallSherwoodNumber(int phaseNum, double reynoldsNumber, double schmidtNumber,
      FlowNodeInterface node) {
    if (reynoldsNumber < 2300) {
      // Laminar: Graetz-Nusselt problem solution
      return 3.66;
    } else {
      // Gnielinski correlation for turbulent pipe flow
      // Sh = (f/8)(Re - 1000)Sc / (1 + 12.7(f/8)^0.5 * (Sc^(2/3) - 1))
      double f = calcWallFrictionFactor(phaseNum, node);
      double numerator = (f / 8.0) * (reynoldsNumber - 1000.0) * schmidtNumber;
      double denominator =
          1.0 + 12.7 * Math.sqrt(f / 8.0) * (Math.pow(schmidtNumber, 2.0 / 3.0) - 1.0);
      return numerator / denominator;
    }
  }

  /**
   * <p>
   * Calculates the Nusselt number for interphase heat transfer. The Nusselt number is the
   * dimensionless heat transfer coefficient: Nu = h * L / k where h is the heat transfer
   * coefficient, L is the characteristic length, and k is thermal conductivity.
   * </p>
   *
   * @param phaseNum the phase number (0 for gas, 1 for liquid)
   * @param reynoldsNumber the Reynolds number
   * @param prandtlNumber the Prandtl number (μ*Cp/k)
   * @param node the flow node
   * @return the Nusselt number
   */
  public double calcNusseltNumber(int phaseNum, double reynoldsNumber, double prandtlNumber,
      FlowNodeInterface node) {
    // Default correlation: Chilton-Colburn analogy for turbulent flow
    if (reynoldsNumber < 2300) {
      // Laminar flow - constant Nusselt number for fully developed flow
      return 3.66;
    } else {
      // Turbulent flow - Dittus-Boelter correlation
      return 0.023 * Math.pow(reynoldsNumber, 0.8) * Math.pow(prandtlNumber, 0.33);
    }
  }

  /**
   * <p>
   * Calculates the mass transfer coefficient from Sherwood number.
   * </p>
   *
   * @param sherwoodNumber the Sherwood number
   * @param characteristicLength the characteristic length in meters
   * @param diffusivity the diffusivity in m²/s
   * @return the mass transfer coefficient in m/s
   */
  public double calcMassTransferCoefficientFromSherwood(double sherwoodNumber,
      double characteristicLength, double diffusivity) {
    return sherwoodNumber * diffusivity / characteristicLength;
  }

  /**
   * <p>
   * Calculates the heat transfer coefficient from Nusselt number.
   * </p>
   *
   * @param nusseltNumber the Nusselt number
   * @param characteristicLength the characteristic length in meters
   * @param thermalConductivity the thermal conductivity in W/(m·K)
   * @return the heat transfer coefficient in W/(m²·K)
   */
  public double calcHeatTransferCoefficientFromNusselt(double nusseltNumber,
      double characteristicLength, double thermalConductivity) {
    return nusseltNumber * thermalConductivity / characteristicLength;
  }
}
