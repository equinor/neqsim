package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * A liquid rate step on a liquid-full line must settle without ringing.
 *
 * @author ESOL
 * @version 1.0
 */
class TwoFluidPipeLiquidStepTest extends neqsim.NeqSimTest {

  private TwoFluidPipe makeOilLine() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 15.0);
    fluid.addComponent("n-heptane", 0.5);
    fluid.addComponent("n-decane", 0.5);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("oil", fluid);
    feed.setFlowRate(40.0, "kg/sec");
    feed.setTemperature(40.0, "C");
    feed.setPressure(15.0, "bara");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe("oil line", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.2);
    pipe.setRoughness(4.6e-5);
    pipe.setNumberOfSections(50);
    pipe.setOutletPressure(10.0, "bara");
    pipe.run();
    return pipe;
  }

  @Test
  void liquidFullLineSelectsCoupledPressureAndSettlesWithoutRinging() {
    TwoFluidPipe pipe = makeOilLine();
    assertFalse(pipe.isCoupledPressureMomentumEnabled());
    Stream feed = (Stream) pipe.getInletStream();
    feed.setFlowRate(20.0, "kg/sec");
    feed.run();
    UUID id = UUID.randomUUID();
    double outflow = Double.NaN;
    // The step launches a water-hammer swing of period 4L/c (about 16 s here) that friction damps; the defect was a
    // non-physical ringing of several hundred seconds. After 200 s the outflow must stay at the new inflow.
    for (int step = 0; step < 300; step++) {
      pipe.runTransient(2.0, id);
      outflow = pipe.getOutletStream().getFlowRate("kg/sec");
      if (step >= 100) {
        assertEquals(20.0, outflow, 0.5, "outflow still ringing at t=" + 2.0 * (step + 1) + " s");
      }
    }
    assertTrue(pipe.isCoupledPressureMomentumEnabled(), "a liquid-full line should select the coupled solve");
    assertEquals(20.0, outflow, 0.3, "outflow must settle to the new inflow");
  }

  @Test
  void explicitChoiceIsRespected() {
    TwoFluidPipe pipe = makeOilLine();
    pipe.setEnableCoupledPressureMomentum(false);
    pipe.runTransient(2.0, UUID.randomUUID());
    assertFalse(pipe.isCoupledPressureMomentumEnabled());
  }
}
