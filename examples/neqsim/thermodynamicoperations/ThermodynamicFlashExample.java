package neqsim.thermodynamicoperations;

import java.util.Arrays;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations.FlashType;

/**
 * Example illustrating common flash and property calculation scenarios from the test suite.
 */
public class ThermodynamicFlashExample {

  private ThermodynamicFlashExample() {}

  public static void main(String[] args) {
    runSimpleFlash();
    runDefinedFluidPropertyFlash();
  }

  private static void runSimpleFlash() {
    SystemInterface thermoSystem = new SystemSrkEos(280.0, 10.0);
    thermoSystem.addComponent("methane", 0.7);
    thermoSystem.addComponent("ethane", 0.3);
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);

    double pressure = 10.0;
    double temperature = 20.0;
    ops.flash(FlashType.PT, pressure, temperature, "bara", "C");
    ops.getSystem().init(2);
    ops.getSystem().initPhysicalProperties();
    Double[] ptFluidProperties = ops.getSystem().getProperties().getValues();

    ops.getSystem().init(0);
    ops.flash(FlashType.TP, temperature, pressure, "C", "bara");
    ops.getSystem().init(2);
    ops.getSystem().initPhysicalProperties();
    Double[] tpFluidProperties = ops.getSystem().getProperties().getValues();

    System.out.println("PT flash properties: " + Arrays.toString(ptFluidProperties));
    System.out.println("TP flash properties: " + Arrays.toString(tpFluidProperties));
  }

  private static void runDefinedFluidPropertyFlash() {
    Double[] sp1 = new Double[] {22.1, 23.2, 24.23, 25.98, 25.23, 26.1, 27.3, 28.7, 23.5, 22.7};
    Double[] sp2 = new Double[] {288.1, 290.1, 295.1, 301.2, 299.3, 310.2, 315.3, 310.0, 305.2, 312.7};

    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
    fluid.addComponent("nitrogen", 0.79);
    fluid.addComponent("oxygen", 0.21);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);
    CalculationResult res = fluidOps.propertyFlash(Arrays.asList(sp1), Arrays.asList(sp2), 1,
        Arrays.asList("O2", "N2"), Arrays.asList(Arrays.asList(98.0, 98.0, 98.0, 98.0, 98.0,
            98.0, 98.0, 98.0, 98.0, 98.0), Arrays.asList(2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0,
                2.0, 2.0)));

    System.out.println("Property flash calculation errors: " + Arrays.toString(res.calculationError));
  }
}
