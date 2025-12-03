package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example illustrating how to compute hydrocarbon dew point using the analyser utility.
 */
public class HydrocarbonDewPointAnalyserExample {

  private HydrocarbonDewPointAnalyserExample() {}

  public static void main(String[] args) {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 50.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.addComponent("ethane", 0.1);
    thermoSystem.addComponent("propane", 0.1);
    thermoSystem.addComponent("i-butane", 0.001);
    thermoSystem.addComponent("n-butane", 0.001);
    thermoSystem.addComponent("i-pentane", 0.001);
    thermoSystem.addComponent("n-pentane", 0.0001);
    thermoSystem.setMixingRule("classic");

    Stream stream = new Stream("gas stream", thermoSystem);
    HydrocarbonDewPointAnalyser analyser = new HydrocarbonDewPointAnalyser("dew point", stream);

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.add(analyser);
    process.run();

    analyser.setReferencePressure(40.0);
    double dewPointC = analyser.getMeasuredValue("C");
    System.out.println("Hydrocarbon dew point at 40 bara: " + dewPointC + " C");
  }
}
