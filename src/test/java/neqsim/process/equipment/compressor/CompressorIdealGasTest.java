package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemIdealGas;

public class CompressorIdealGasTest extends neqsim.NeqSimTest {
  @Test
  public void testIdealGasCompressorIsentropic() {
    double Tin = 300.0;
    double Pin = 1.0;
    double Pout = 10.0;

    SystemIdealGas gas = new SystemIdealGas(Tin, Pin);
    gas.addComponent("nitrogen", 1.0);

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1.0, "kg/sec");
    feed.setTemperature(Tin, "K");
    feed.setPressure(Pin, "bara");
    feed.run();

    double cp = feed.getFluid().getPhase(0).getCp("J/molK");
    double cv = feed.getFluid().getPhase(0).getCv("J/molK");
    double gamma = cp / cv;

    Compressor compressor = new Compressor("comp", feed);
    compressor.setUsePolytropicCalc(false);
    compressor.setIsentropicEfficiency(1.0);
    compressor.setOutletPressure(Pout);
    compressor.run();

    double T2Expected = Tin * Math.pow(Pout / Pin, (gamma - 1.0) / gamma);
    assertEquals(T2Expected, compressor.getOutletStream().getTemperature("K"), 5.0);

    double massFlow = feed.getFluid().getFlowRate("kg/sec");
    double mw = feed.getFluid().getPhase(0).getMolarMass();
    double hIn = feed.getFluid().getPhase(0).getEnthalpy("J/mol");
    double hOut = compressor.getOutletStream().getFluid().getPhase(0).getEnthalpy("J/mol");
    double expectedPower = massFlow * (hOut - hIn) / mw;
    assertEquals(expectedPower, compressor.getPower(), 1e-6);
  }
}
