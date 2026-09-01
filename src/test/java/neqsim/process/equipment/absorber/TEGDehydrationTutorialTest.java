package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Executes the conservative equilibrium-contact example in docs/tutorials/teg_dehydration_tutorial.md.
 */
class TEGDehydrationTutorialTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TEGDehydrationTutorialTest.class);

  private static double componentFlow(StreamInterface stream, String componentName) {
    double flow = 0.0;
    for (int phaseNumber = 0; phaseNumber < stream.getFluid().getNumberOfPhases(); phaseNumber++) {
      ComponentInterface component = stream.getFluid().getPhase(phaseNumber).getComponent(componentName);
      if (component != null) {
        flow += component.getFlowRate("kg/hr");
      }
    }
    return flow;
  }

  @Test
  void equilibriumContactExampleConservesMass() {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.addComponent("propane", 0.02);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.setMixingRule(10);

    Stream gasFeed = new Stream("dry gas basis", gasFluid);
    gasFeed.setFlowRate(1.0, "MSm3/day");
    gasFeed.setTemperature(30.0, "C");
    gasFeed.setPressure(70.0, "bara");

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", gasFeed);

    SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    tegFluid.addComponent("TEG", 99.5, "kg/hr");
    tegFluid.addComponent("water", 0.5, "kg/hr");
    tegFluid.setMixingRule(10);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(3000.0, "kg/hr");
    leanTeg.setTemperature(30.0, "C");
    leanTeg.setPressure(70.0, "bara");

    Mixer equilibriumContact = new Mixer("equilibrium contact");
    equilibriumContact.addStream(saturator.getOutletStream());
    equilibriumContact.addStream(leanTeg);

    Separator phaseSplitter = new Separator("gas and rich TEG separator", equilibriumContact.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(gasFeed);
    process.add(saturator);
    process.add(leanTeg);
    process.add(equilibriumContact);
    process.add(phaseSplitter);
    process.run();

    StreamInterface wetGas = saturator.getOutletStream();
    StreamInterface mixedContact = equilibriumContact.getOutletStream();
    StreamInterface productGas = phaseSplitter.getGasOutStream();
    StreamInterface richTeg = phaseSplitter.getLiquidOutStream();

    double wetWater = wetGas.getFluid().getPhase("gas").getComponent("water").getx();
    double productWater = productGas.getFluid().getPhase("gas").getComponent("water").getx();

    double wetWaterFlow = componentFlow(wetGas, "water");
    double leanWaterFlow = componentFlow(leanTeg, "water");
    double productWaterFlow = componentFlow(productGas, "water");
    double richWaterFlow = componentFlow(richTeg, "water");
    double waterTransferred = wetWaterFlow - productWaterFlow;
    double richTegFlow = richTeg.getFlowRate("kg/hr");
    double waterResidual = wetWaterFlow + leanWaterFlow - productWaterFlow - richWaterFlow;
    double totalMassResidual = wetGas.getFlowRate("kg/hr") + leanTeg.getFlowRate("kg/hr")
        - productGas.getFlowRate("kg/hr") - richTeg.getFlowRate("kg/hr");

    logger.info("Wet gas water {} mol-ppm", wetWater * 1.0e6);
    logger.info("Product gas water {} mol-ppm", productWater * 1.0e6);

    double wetWaterPpm = wetWater * 1.0e6;
    double productWaterPpm = productWater * 1.0e6;

    assertTrue(wetWaterPpm > 700.0 && wetWaterPpm < 900.0,
        "Saturated-gas water must remain in the validated screening range: " + wetWaterPpm);
    assertTrue(productWaterPpm > 40.0 && productWaterPpm < 55.0,
        "Product-gas water must remain in the validated screening range: " + productWaterPpm);
    assertTrue(productWater < 0.1 * wetWater,
        "The equilibrium contact must remove at least 90% of the gas-phase water");
    assertTrue(waterTransferred > 22.0 && waterTransferred < 25.0,
        "Water transfer must remain in the validated screening range: " + waterTransferred);
    assertTrue(richTegFlow > 3040.0 && richTegFlow < 3050.0,
        "Rich-liquid flow must remain in the validated screening range: " + richTegFlow);
    assertTrue(richTegFlow > leanTeg.getFlowRate("kg/hr"));
    assertTrue(mixedContact.getFluid().getNumberOfPhases() > 1);
    assertEquals(wetWaterFlow + leanWaterFlow, componentFlow(mixedContact, "water"), 1.0e-8);
    assertEquals(0.0, waterResidual, 1.0e-8);
    assertEquals(0.0, totalMassResidual, 1.0e-8);
  }
}
