package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Chilton-Colburn analogy of the stratified-flow transfer coefficients against the Dittus-Boelter correlation. */
class InterphaseStratifiedFlowAnalogyTest {
  /**
   * The friction factor of the class is the Darcy factor, so the analogy divides it by eight. With that the turbulent
   * gas-side Sherwood and Nusselt numbers of a nearly smooth pipe reproduce 0.023 Re^0.8 X^(1/3); with the Fanning form
   * applied to the Darcy factor they were four times higher.
   */
  @Test
  void turbulentGasSideCoefficientsFollowDittusBoelter() {
    for (double gasVelocity : new double[] {0.05, 0.2, 1.0}) {
      StratifiedFlowNode node = node(gasVelocity, 0.05);
      InterphaseStratifiedFlow coefficients = new InterphaseStratifiedFlow();
      double reynolds = node.getReynoldsNumber(0);
      assertTrue(reynolds > 1.0e4 && reynolds < 5.0e6, "Reynolds number " + reynolds);
      double hydraulicDiameter = node.getHydraulicDiameter(0);
      double kinematicViscosity = node.getBulkSystem().getPhase(0).getPhysicalProperties().getKinematicViscosity();
      for (double schmidt : new double[] {0.7, 1.0, 2.0}) {
        double coefficient = coefficients.calcInterphaseMassTransferCoefficient(0, schmidt, node);
        double sherwood = coefficient * hydraulicDiameter / (kinematicViscosity / schmidt);
        double dittusBoelter = 0.023 * Math.pow(reynolds, 0.8) * Math.pow(schmidt, 1.0 / 3.0);
        assertEquals(dittusBoelter, sherwood, 0.2 * dittusBoelter, "Sh at Re " + reynolds + ", Sc " + schmidt);
      }
      double prandtl = 0.8;
      double heatCoefficient = coefficients.calcInterphaseHeatTransferCoefficient(0, prandtl, node);
      double density = node.getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
      double specificHeat = node.getBulkSystem().getPhase(0).getCp() / node.getBulkSystem().getPhase(0).getMolarMass()
          / node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
      // Nu = h d / lambda with lambda = rho cp nu / Pr
      double nusselt = heatCoefficient * hydraulicDiameter / (density * specificHeat * kinematicViscosity / prandtl);
      double dittusBoelterHeat = 0.023 * Math.pow(reynolds, 0.8) * Math.pow(prandtl, 1.0 / 3.0);
      assertEquals(dittusBoelterHeat, nusselt, 0.2 * dittusBoelterHeat, "Nu at Re " + reynolds);
    }
  }

  /** A phase flowing backwards has the coefficients of one flowing forwards, and a liquid at rest a positive one. */
  @Test
  void coefficientsDependOnSpeedMagnitudeAndStayPositiveAtRest() {
    InterphaseStratifiedFlow coefficients = new InterphaseStratifiedFlow();
    StratifiedFlowNode forward = node(1.0, 0.05);
    StratifiedFlowNode reversed = node(1.0, 0.05);
    reversed.setVelocity(0, -1.0);
    reversed.setVelocity(1, -0.05);
    for (int phase = 0; phase < 2; phase++) {
      double expected = coefficients.calcInterphaseMassTransferCoefficient(phase, 1.5, forward);
      assertTrue(expected > 0.0);
      assertEquals(expected, coefficients.calcInterphaseMassTransferCoefficient(phase, 1.5, reversed),
          1.0e-12 * expected);
      double expectedHeat = coefficients.calcInterphaseHeatTransferCoefficient(phase, 1.5, forward);
      assertEquals(expectedHeat, coefficients.calcInterphaseHeatTransferCoefficient(phase, 1.5, reversed),
          1.0e-12 * Math.abs(expectedHeat));
    }
    StratifiedFlowNode resting = node(1.0, 0.05);
    resting.setVelocity(1, 0.0);
    resting.init();
    double atRest = coefficients.calcInterphaseMassTransferCoefficient(1, 200.0, resting);
    double diffusivity = resting.getBulkSystem().getPhase(1).getPhysicalProperties().getKinematicViscosity() / 200.0;
    assertEquals(3.41 * diffusivity / (resting.getHydraulicDiameter(1) / 4.0), atRest, 1.0e-12 * atRest);
    assertTrue(atRest < coefficients.calcInterphaseMassTransferCoefficient(1, 200.0, forward));
  }

  private static StratifiedFlowNode node(double gasVelocity, double liquidVelocity) {
    SystemInterface fluid = new SystemSrkEos(277.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    PipeData pipe = new PipeData(1.0, 1.0e-6);
    pipe.setNodeLength(1.0);
    StratifiedFlowNode node = new StratifiedFlowNode(fluid, pipe);
    node.setPhaseFraction(0, 0.98);
    node.setPhaseFraction(1, 0.02);
    node.setVelocity(0, gasVelocity);
    node.setVelocity(1, liquidVelocity);
    node.init();
    return node;
  }
}
