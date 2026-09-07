package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** The flash and hydraulic split must share the same in-situ liquid property closure. */
class TwoFluidPipeThreePhaseViscosityConsistencyTest {
  @Test
  void flashedSteadyViscosityIsAlreadyConsistentWithTheHydraulicSplit() throws Exception {
    for (int interval : new int[] { 1, 3 }) {
      SystemSrkEos fluid = new SystemSrkEos(293.15, 30.0);
      fluid.addComponent("methane", 0.4);
      fluid.addComponent("n-pentane", 0.2);
      fluid.addComponent("n-heptane", 0.2);
      fluid.addComponent("water", 0.2);
      fluid.setMixingRule("classic");
      fluid.setMultiPhaseCheck(true);
      Stream inlet = new Stream("three-phase", fluid);
      inlet.setFlowRate(8.0, "kg/sec");
      inlet.run();
      TwoFluidPipe pipe = new TwoFluidPipe("viscosity consistency", inlet);
      pipe.setLength(100.0);
      pipe.setDiameter(0.15);
      pipe.setNumberOfSections(4);
      pipe.setElevationProfile(new double[] { 0.0, 5.0, 10.0, 15.0 });
      pipe.setSteadyStateFlashInterval(interval);
      pipe.run();
      assertTrue(pipe.isSteadyStateConverged());
      Field field = TwoFluidPipe.class.getDeclaredField("sections");
      field.setAccessible(true);
      for (TwoFluidSection section : (TwoFluidSection[]) field.get(pipe)) {
        assertTrue(section.getOilHoldup() > 0.0 && section.getWaterHoldup() > 0.0);
        double viscosity = section.getLiquidViscosity();
        double[] state = section.getStateVector();
        TwoFluidSection recovered = section.clone();
        recovered.updateThreePhaseProperties();
        assertEquals(viscosity, recovered.getLiquidViscosity(), 1e-12,
            "a hydraulic property refresh must not replace the flashed viscosity with another closure");
        assertArrayEquals(state, section.getStateVector(), 0.0);
      }
    }
  }
}
