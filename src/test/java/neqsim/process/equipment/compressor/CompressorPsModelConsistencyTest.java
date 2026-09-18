package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** The compressor must solve entropy in the selected property's reference convention. */
class CompressorPsModelConsistencyTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(strings = { "GERG2008", "Leachman", "Vega" })
  void idealCompressionPreservesSelectedModelEntropy(String model) {
    SystemInterface fluid = new SystemSrkEos(308.15, 85.0);
    String component = model.equals("Vega") ? "helium" : model.equals("Leachman") ? "hydrogen" : "methane";
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule(2);
    Stream inlet = new Stream("inlet", fluid);
    inlet.run();
    double inletEntropy = modelEntropy(inlet.getThermoSystem(), model);
    Compressor compressor = new Compressor("compressor", inlet);
    compressor.setOutletPressure(150.0);
    compressor.setIsentropicEfficiency(1.0);
    compressor.setUsePolytropicCalc(false);
    compressor.setUseGERG2008(model.equals("GERG2008"));
    compressor.setUseLeachman(model.equals("Leachman"));
    compressor.setUseVega(model.equals("Vega"));
    compressor.run();
    SystemInterface outlet = compressor.getOutletStream().getThermoSystem();
    assertEquals(150.0, outlet.getPressure(), 1.0e-10);
    assertTrue(outlet.getTemperature() > inlet.getTemperature());
    assertTrue(Double.isFinite(compressor.getPower()) && compressor.getPower() > 0.0);
    assertEquals(inletEntropy, modelEntropy(outlet, model), 1.0e-5);
  }

  private double modelEntropy(SystemInterface fluid, String model) {
    if (model.equals("Leachman")) {
      return fluid.getPhase(0).getProperties_Leachman()[8];
    }
    if (model.equals("Vega")) {
      return fluid.getPhase(0).getProperties_Vega()[8];
    }
    return fluid.getPhase(0).getProperties_GERG2008()[8];
  }
}
