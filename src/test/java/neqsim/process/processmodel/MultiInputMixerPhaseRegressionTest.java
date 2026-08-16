package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Regression coverage for reused multiphase state in a feed-forward process. */
public class MultiInputMixerPhaseRegressionTest {
  @Test
  public void reusedMultiInputProcessPreservesAqueousPhaseAfterFlowChange() {
    ProcessSystem process = buildProcess();
    process.run();

    Stream inhibitor = (Stream) process.getUnit("inhibitor stream");
    inhibitor.setFlowRate(0.1, "kg/hr");
    process.run();

    Stream outlet = (Stream) process.getUnit("downstream stream");
    assertTrue(outlet.getFluid().hasPhaseType("gas"));
    assertTrue(outlet.getFluid().hasPhaseType("aqueous"));
  }

  private ProcessSystem buildProcess() {
    SystemInterface feed = new SystemSrkCPAstatoil(298.15, 1.01325);
    String[] names = { "N2", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "c-C5", "22-dim-C3", "n-hexane", "n-heptane", "n-octane", "n-nonane" };
    double[] amounts = { 0.41, 9.249, 73.263, 9.269, 4.75, 0.52, 1.34, 0.29, 0.36, 0.02, 0.02, 0.29, 0.25, 0.05, 0.02 };
    for (int index = 0; index < names.length; index++) {
      feed.addComponent(names[index], amounts[index]);
    }
    feed.addComponent("water", 0.0);
    feed.addComponent("MEG", 0.0);
    feed.setMixingRule(10);
    feed.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();
    Stream gas = new Stream("gas stream", feed);
    gas.setFlowRate(168958.0, "Sm3/hr");
    gas.setTemperature(29.0, "C");
    gas.setPressure(74.1, "barg");
    process.add(gas);

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", gas);
    process.add(saturator);
    Stream saturatedGas = (Stream) saturator.getOutStream();
    saturatedGas.setName("saturated gas stream");
    process.add(saturatedGas);

    SystemInterface inhibitorFluid = feed.clone();
    double inhibitorMoleFraction = (89.0 / 62.07) / ((89.0 / 62.07) + (11.0 / 18.01528));
    double[] inhibitorComposition = new double[feed.getNumberOfComponents()];
    for (int index = 0; index < feed.getNumberOfComponents(); index++) {
      String componentName = feed.getComponent(index).getName();
      if (componentName.equals("water")) {
        inhibitorComposition[index] = 1.0 - inhibitorMoleFraction;
      } else if (componentName.equals("MEG")) {
        inhibitorComposition[index] = inhibitorMoleFraction;
      }
    }
    inhibitorFluid.setMolarComposition(inhibitorComposition);

    Stream inhibitor = new Stream("inhibitor stream", inhibitorFluid);
    inhibitor.setFlowRate(0.01, "kg/hr");
    inhibitor.setTemperature(29.0, "C");
    inhibitor.setPressure(74.1, "barg");
    process.add(inhibitor);

    StaticMixer mixer = new StaticMixer("multiphase mixer");
    mixer.addStream(inhibitor);
    mixer.addStream(saturatedGas);
    process.add(mixer);

    Heater pipeline = new Heater("downstream heater", mixer.getOutletStream());
    pipeline.setOutPressure(36.2, "barg");
    pipeline.setOutTemperature(5.3, "C");
    process.add(pipeline);

    Stream outlet = (Stream) pipeline.getOutStream();
    outlet.setName("downstream stream");
    process.add(outlet);
    process.add(new HydrateEquilibriumTemperatureAnalyser("hydrate analyser", outlet));

    Heater separatorHeater = new Heater("separator heater", outlet);
    separatorHeater.setOutPressure(35.0, "barg");
    separatorHeater.setOutTemperature(19.0, "C");
    process.add(separatorHeater);
    process.add(new ThreePhaseSeparator("three phase separator", separatorHeater.getOutletStream()));
    return process;
  }
}
