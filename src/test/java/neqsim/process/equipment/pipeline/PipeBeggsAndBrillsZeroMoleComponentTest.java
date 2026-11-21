package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Regression test: a CPA well stream that declares an inhibitor (MEG) with zero moles flows through
 * {@link PipeBeggsAndBrills}.
 *
 * <p>
 * neqsim 3.29.0 failed with "PhaseSrkCPAs:molarVolumeChangePhase - Variable Molar volume is NaN" for this case, which
 * is common when a study prepares an MEG-injection case and runs the uninhibited base case with the same fluid.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipeBeggsAndBrillsZeroMoleComponentTest {
  /** A zero-mole MEG component does not make the pipe calculation fail. */
  @Test
  void zeroMoleInhibitorInCpaFluid() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 70.0, 112.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("n-heptane", 8.0);
    fluid.addTBPfraction("C10", 3.0, 0.134, 0.79);
    fluid.addComponent("water", 45.0);
    fluid.addComponent("MEG", 0.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    Stream stream = new Stream("well", fluid);
    stream.setFlowRate(250000.0, "kg/hr");
    stream.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("flowline", stream);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.3048);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(5);
    pipe.setConstantSurfaceTemperature(4.0, "C");
    pipe.setHeatTransferCoefficient(4.0);
    pipe.run();

    double pOut = pipe.getOutletStream().getPressure("bara");
    assertTrue(Double.isFinite(pOut) && pOut > 100.0 && pOut < 112.0, "outlet pressure " + pOut);
  }
}
