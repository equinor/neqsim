package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ReferenceProcessTest extends neqsim.NeqSimTest {
  @Test
  public void testProcess() {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 51.0);
    thermoSystem.addComponent("CO2", 51.0);
    thermoSystem.addComponent("methane", 51.0);
    thermoSystem.addComponent("ethane", 51.0);
    thermoSystem.addComponent("propane", 51.0);
    thermoSystem.addComponent("i-butane", 51.0);
    thermoSystem.addComponent("n-butane", 51.0);
    thermoSystem.addComponent("iC5", 51.0);
    thermoSystem.addComponent("nC5", 1.0);

    thermoSystem.addTBPfraction("C6", 1.0, 86.0 / 1000.0, 0.66);
    thermoSystem.addTBPfraction("C7", 1.0, 91.0 / 1000.0, 0.74);
    thermoSystem.addTBPfraction("C8", 1.0, 103.0 / 1000.0, 0.77);
    thermoSystem.addTBPfraction("C9", 1.0, 117.0 / 1000.0, 0.79);
    thermoSystem.addPlusFraction("C10_C12", 1.0, 145.0 / 1000.0, 0.80);
    thermoSystem.addPlusFraction("C13_C14", 1.0, 181.0 / 1000.0, 0.8279);
    thermoSystem.addPlusFraction("C15_C16", 1.0, 212.0 / 1000.0, 0.837);
    thermoSystem.addPlusFraction("C17_C19", 1.0, 248.0 / 1000.0, 0.849);
    thermoSystem.addPlusFraction("C20_C22", 1.0, 289.0 / 1000.0, 0.863);
    thermoSystem.addPlusFraction("C23_C25", 1.0, 330.0 / 1000.0, 0.875);
    thermoSystem.addPlusFraction("C26_C30", 1.0, 387.0 / 1000.0, 0.88);
    thermoSystem.addPlusFraction("C31_C38", 1.0, 471.0 / 1000.0, 0.90);
    thermoSystem.addPlusFraction("C38_C80", 1.0, 662.0 / 1000.0, 0.92);
    thermoSystem.setMixingRule("classic");
    // thermoSystem.setMultiPhaseCheck(true);

    thermoSystem.setMolarComposition(new double[] {0.005269, 0.039189, 0.700553, 0.091154, 0.050908,
        0.007751, 0.014665, 0.004249, 0.004878, 0.004541, 0.007189, 0.006904, 0.004355, 0.007658,
        0.003861, 0.003301, 0.002624, 0.001857, 0.001320, 0.001426, 0.001164, 0.000916});

    neqsim.process.equipment.stream.Stream wellStream =
        new neqsim.process.equipment.stream.Stream("well stream", thermoSystem);
    wellStream.setTemperature(70.0, "C");
    wellStream.setPressure(90.0, "bara");

    neqsim.process.equipment.separator.ThreePhaseSeparator firstStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("1st stage separator",
            wellStream);

    neqsim.process.equipment.heatexchanger.Heater oilHeaterSecondStage =
        new neqsim.process.equipment.heatexchanger.Heater("oil heater second stage",
            firstStageSeparator.getOilOutStream());
    oilHeaterSecondStage.setOutPressure(30.0);
    oilHeaterSecondStage.setOutTemperature(68.0, "C");

    neqsim.process.equipment.separator.ThreePhaseSeparator secondStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("2nd stage separator",
            oilHeaterSecondStage.getOutletStream());

    neqsim.process.equipment.heatexchanger.Heater oilHeaterThirdStage =
        new neqsim.process.equipment.heatexchanger.Heater("oil heater third stage",
            secondStageSeparator.getOilOutStream());
    oilHeaterThirdStage.setOutPressure(2.8);
    oilHeaterThirdStage.setOutTemperature(70.0, "C");

    neqsim.process.equipment.separator.ThreePhaseSeparator thirdStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("3rd stage separator",
            oilHeaterThirdStage.getOutletStream());

    neqsim.process.equipment.heatexchanger.Heater oilHeaterStandardStage =
        new neqsim.process.equipment.heatexchanger.Heater("oil heater standard stage",
            thirdStageSeparator.getOilOutStream());
    oilHeaterStandardStage.setOutPressure(1.01325);
    oilHeaterStandardStage.setOutTemperature(15.0, "C");

    neqsim.process.equipment.separator.ThreePhaseSeparator standardStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("standard stage separator",
            oilHeaterStandardStage.getOutletStream());

    neqsim.process.equipment.mixer.Mixer gasMixer =
        new neqsim.process.equipment.mixer.Mixer("gas mixer");
    gasMixer.addStream(firstStageSeparator.getGasOutStream());
    gasMixer.addStream(secondStageSeparator.getGasOutStream());
    gasMixer.addStream(thirdStageSeparator.getGasOutStream());
    gasMixer.addStream(standardStageSeparator.getGasOutStream());
    gasMixer.setOutTemperature(288.15);

    neqsim.process.equipment.stream.Stream exportoil = new neqsim.process.equipment.stream.Stream(
        "export oil", standardStageSeparator.getOilOutStream());
    neqsim.process.equipment.stream.Stream exportgas =
        new neqsim.process.equipment.stream.Stream("export gas", gasMixer.getOutletStream());

    neqsim.process.processmodel.ProcessSystem oilprocess =
        new neqsim.process.processmodel.ProcessSystem();
    oilprocess.add(wellStream);
    oilprocess.add(firstStageSeparator);
    oilprocess.add(oilHeaterSecondStage);
    oilprocess.add(secondStageSeparator);
    oilprocess.add(oilHeaterThirdStage);
    oilprocess.add(thirdStageSeparator);
    oilprocess.add(oilHeaterStandardStage);
    oilprocess.add(standardStageSeparator);
    oilprocess.add(gasMixer);
    oilprocess.add(exportoil);
    oilprocess.add(exportgas);

    oilprocess.run();

    assertEquals(15.0, gasMixer.getOutletStream().getTemperature("C"), 0.01);
    assertEquals(2278.2594247, exportgas.getFlowRate("Sm3/hr") / exportoil.getFlowRate("idSm3/hr"),
        0.01);
  }
}
