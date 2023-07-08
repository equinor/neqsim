package neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode;

import org.junit.jupiter.api.Test;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class StratifiedFlowNodeTest {
  @Test
  void testInit() {
    SystemInterface testSystem = new SystemSrkEos(313.3, 100.01325);
    testSystem.addComponent("methane", 1100, "kg/hr", 0);
    testSystem.addComponent("nC10", 11.1, "kg/hr", 1);
    testSystem.setMixingRule(2);
    testSystem.initBeta();
    
    PipeData pipe1 = new PipeData(0.1, 0.00025);

    StratifiedFlowNode test = new StratifiedFlowNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.001);
    test.getFluidBoundary().setHeatTransferCalc(false);
    test.getFluidBoundary().setMassTransferCalc(true);

    for (int i = 0; i < 100; i++) {
      test.initFlowCalc();
      test.calcFluxes();
      test.update();
      /*
       * System.out.println( "flux methane " + test.getFluidBoundary().getInterphaseMolarFlux(0) +
       * " [mol/m2*sec]"); System.out.println( "flux nC10 " +
       * test.getFluidBoundary().getInterphaseMolarFlux(1) + " [mol/m2*sec]");
       * System.out.println("gas velocity " + test.getSuperficialVelocity(0) + " m/sec");
       * System.out.println("liquid velocity " + test.getVelocity(1) + " m/sec");
       * System.out.println("liquid holdup " + test.getPhaseFraction(1) + "-");
       * System.out.println("interface contact area " + test.getInterphaseContactLength(0) +
       * "m2/m"); System.out.println("gas flow rate " + test.getMassFlowRate(0) * 60 * 60 +
       * " kg/hr"); System.out.println("liquid flow rate " + test.getMassFlowRate(1) * 60 * 60 +
       * " kg/hr");
       */

    }
  }
}
