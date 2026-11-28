package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphaseonephase.interphasepipeflow.InterphasePipeFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseDropletFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseSlugFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseStratifiedFlow;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.stirredcell.InterphaseStirredCellFlow;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * The turbulent wall heat and mass transfer coefficients of every transport-coefficient class against Dittus-Boelter.
 *
 * <p>
 * The classes return Darcy friction factors, so the Chilton-Colburn analogy divides by eight. With the Fanning form
 * applied to the Darcy factor the coefficients were four times higher.
 * </p>
 */
class ChiltonColburnAnalogyTest {
  /** Wall heat and mass transfer of the gas phase follow 0.023 Re^0.8 X^(1/3), times the stated slug enhancement. */
  @Test
  void turbulentWallCoefficientsFollowDittusBoelter() {
    InterphaseTransportCoefficientInterface[] closures = {new InterphasePipeFlow(), new InterphaseStratifiedFlow(),
        new InterphaseDropletFlow(), new InterphaseStirredCellFlow(), new InterphaseSlugFlow()};
    // The slug class multiplies its friction factor by 1.2 and its wall heat and mass transfer by 1.4.
    double[] enhancement = {1.0, 1.0, 1.0, 1.0, 1.2 * 1.4};
    for (double gasVelocity : new double[] {0.05, 0.2, 1.0}) {
      StratifiedFlowNode node = node(gasVelocity);
      double reynolds = node.getReynoldsNumber(0);
      assertTrue(reynolds > 1.0e4 && reynolds < 5.0e6, "Reynolds number " + reynolds);
      double hydraulicDiameter = node.getHydraulicDiameter(0);
      double kinematicViscosity = node.getBulkSystem().getPhase(0).getPhysicalProperties().getKinematicViscosity();
      double density = node.getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
      double specificHeat = node.getBulkSystem().getPhase(0).getCp() / node.getBulkSystem().getPhase(0).getMolarMass()
          / node.getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
      for (int closure = 0; closure < closures.length; closure++) {
        String name = closures[closure].getClass().getSimpleName() + " at Re " + reynolds;
        double schmidt = 1.2;
        double sherwood = closures[closure].calcWallMassTransferCoefficient(0, schmidt, node) * hydraulicDiameter
            / (kinematicViscosity / schmidt) / enhancement[closure];
        double expectedSherwood = 0.023 * Math.pow(reynolds, 0.8) * Math.pow(schmidt, 1.0 / 3.0);
        assertEquals(expectedSherwood, sherwood, 0.2 * expectedSherwood, "Sh, " + name);
        double prandtl = 0.8;
        double nusselt = closures[closure].calcWallHeatTransferCoefficient(0, prandtl, node) * hydraulicDiameter
            / (density * specificHeat * kinematicViscosity / prandtl) / enhancement[closure];
        double expectedNusselt = 0.023 * Math.pow(reynolds, 0.8) * Math.pow(prandtl, 1.0 / 3.0);
        assertEquals(expectedNusselt, nusselt, 0.2 * expectedNusselt, "Nu, " + name);
      }
    }
  }

  /** A reversed gas phase has the wall coefficients of a forward one. */
  @Test
  void wallCoefficientsDependOnSpeedMagnitude() {
    InterphaseTransportCoefficientInterface[] closures = {new InterphasePipeFlow(), new InterphaseStratifiedFlow(),
        new InterphaseDropletFlow(), new InterphaseStirredCellFlow(), new InterphaseSlugFlow()};
    StratifiedFlowNode forward = node(1.0);
    StratifiedFlowNode reversed = node(1.0);
    reversed.setVelocity(0, -1.0);
    for (InterphaseTransportCoefficientInterface closure : closures) {
      String name = closure.getClass().getSimpleName();
      double mass = closure.calcWallMassTransferCoefficient(0, 1.2, forward);
      double heat = closure.calcWallHeatTransferCoefficient(0, 0.8, forward);
      assertTrue(mass > 0.0 && heat > 0.0, name);
      assertEquals(mass, closure.calcWallMassTransferCoefficient(0, 1.2, reversed), 1.0e-12 * mass, name);
      assertEquals(heat, closure.calcWallHeatTransferCoefficient(0, 0.8, reversed), 1.0e-12 * heat, name);
    }
  }

  private static StratifiedFlowNode node(double gasVelocity) {
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
    node.setVelocity(1, 0.05);
    node.init();
    return node;
  }
}
