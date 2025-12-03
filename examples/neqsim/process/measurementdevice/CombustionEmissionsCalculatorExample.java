package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Demonstrates calculating combustion emissions from a fuel gas stream.
 */
public class CombustionEmissionsCalculatorExample {

  private CombustionEmissionsCalculatorExample() {}

  public static void main(String[] args) {
    SystemInterface fuelGas = new SystemSrkEos(190, 10);
    fuelGas.addComponent("nitrogen", 0.01);
    fuelGas.addComponent("CO2", 0.01);
    fuelGas.addComponent("methane", 1.0);
    fuelGas.addComponent("ethane", 0.05);
    fuelGas.addComponent("propane", 0.01);
    fuelGas.addComponent("n-butane", 0.001);
    fuelGas.addComponent("i-butane", 0.001);

    Stream feedStream = new Stream("fuel gas", fuelGas);
    feedStream.setFlowRate(1.0, "kg/hr");
    feedStream.run();

    CombustionEmissionsCalculator emissionsCalc =
        new CombustionEmissionsCalculator("emissions", feedStream);
    double emissions = emissionsCalc.getMeasuredValue("kg/hr");
    System.out.println("Total combustion emissions: " + emissions + " kg/hr");
  }
}
