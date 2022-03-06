package neqsim.thermodynamicOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ThermodynamicOperationsTest {

    @Test
    void testNeqSimPython() {
        SystemInterface thermoSystem = new neqsim.thermo.system.SystemSrkEos(280.0, 10.0);
        thermoSystem.addComponent("methane", 0.7);
        thermoSystem.addComponent("ethane", 0.3);

        ThermodynamicOperations thermoOps =
                new neqsim.thermodynamicOperations.ThermodynamicOperations(thermoSystem);
        List<Double> jP = Arrays.asList(new Double[] {10.0});
        List<Double> jT = Arrays.asList(new Double[] {280.0});
        CalculationResult res = thermoOps.propertyFlash(jP, jT, 1, null, null);
        Assertions.assertEquals(1.0, res.fluidProperties[0][0], "Number of phases mismatch");
        Assertions.assertEquals(thermoSystem.getPressure("Pa"), res.fluidProperties[0][1], 1e-5,
                "Pressure mismatch");
        Assertions.assertEquals(thermoSystem.getTemperature("K"), res.fluidProperties[0][2], 1e-5,
                "Temperature mismatch");

        CalculationResult res2 = thermoOps.propertyFlash(jP, jT, 0, null, null);
        Assertions.assertEquals(res2.calculationError[0],
                "neqsim.util.exception.InvalidInputException: ThermodynamicOperations:propertyFlash - Input mode must be 1, 2 or 3");
    }

    @Test
    void testComponentNames() {
        SystemInterface system = new neqsim.thermo.system.SystemSrkEos();
        system.addComponent("H2O");
        system.addComponent("N2");
        system.addComponent("CO2");
        system.addComponent("C1");
        system.addComponent("C2");
        system.addComponent("C3");
        system.addComponent("iC4");
        system.addComponent("nC4");
        system.addComponent("iC5");
        system.addComponent("nC5");
        system.addComponent("C6");
    }

    @Test
    void testPropertyFlash() {
        SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);

        List<Double> Sp1 = Arrays.asList(
                new Double[] {100000.0, 1000000.0, 10000000.0, 20000000.0, 100000.0, 1000000.0,
                        10000000.0, 20000000.0, 100000.0, 1000000.0, 10000000.0, 20000000.0});
        List<Double> Sp2 = Arrays.asList(new Double[] {288.15, 288.15, 288.15, 288.15, 303.15,
                303.15, 303.15, 303.15, 423.15, 423.15, 423.15, 423.15});

        fluid.addComponent("water", 0.01);
        fluid.addComponent("nitrogen", 0.02);
        fluid.addComponent("CO2", 0.03);
        fluid.addComponent("methane", 0.81);
        fluid.addComponent("ethane", 0.04);
        fluid.addComponent("propane", 0.03);
        fluid.addComponent("i-butane", 0.02);
        fluid.addComponent("n-butane", 0.01);
        fluid.addComponent("i-pentane", 0.01);
        fluid.addComponent("n-pentane", 0.01);
        fluid.addComponent("n-hexane", 0.01);
        fluid.setMolarComposition(new double[] {0.054, 0.454, 1.514, 89.92, 5.324, 1.535, 0.232,
                0.329, 0.094, 0.107, 0.437});

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        CalculationResult s = ops.propertyFlash(Sp1, Sp2, 1, null, null);

        Assertions.assertEquals(new Double(100), s.fluidProperties[0][4],
                "Mole count didn't return expected result");
    }

    @Test
    void testpropertyFlashOnline() {
        String[] components = {"nitrogen", "oxygen"};
        double[] fractions = {0.79, 0.21};
        int len = 10;
        List<List<Double>> onlineFractions = createDummyRequest(fractions, len);

        SystemInterface fluid = new SystemSrkEos(298, 1.0);
        fluid.addComponents(components); // , fractions);
        // fluid.setMixingRule("classic");
        // fluid.setTotalFlowRate(1, "mole/sec");
        // fluid.init(0);

        Double[] pressure = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 4.0, 3.5, 3.0, 2.5};
        Double[] temperature =
                {301.0, 301.5, 302.0, 302.5, 303.0, 304.0, 304.0, 303.5, 303.0, 302.5};

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature),
                1, Arrays.asList(components), onlineFractions);
        Assertions.assertEquals(len, s.fluidProperties.length);
    }

    @Test
    void testpropertyFlashOnlineSingle() {
        String[] components = {"nitrogen"};
        double[] fractions = {0.98};
        int len = 10;
        List<List<Double>> onlineFractions = createDummyRequest(fractions, len);

        SystemInterface fluid = new SystemSrkEos(298, 1.0);
        fluid.addComponents(components);
        fluid.addComponent("oxygen");

        Double[] pressure =
                new Double[] {22.1, 23.2, 24.23, 25.98, 25.23, 26.1, 27.3, 28.7, 23.5, 22.7};
        Double[] temperature =
                new Double[] {288.1, 290.1, 295.1, 301.2, 299.3, 310.2, 315.3, 310.0, 305.2, 312.7};

        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        CalculationResult s = ops.propertyFlash(Arrays.asList(pressure), Arrays.asList(temperature),
                1, Arrays.asList(components), onlineFractions);
        Assertions.assertEquals(len, s.fluidProperties.length);
    }

    private List<List<Double>> createDummyRequest(double[] fractions, int len) {
        List<List<Double>> onlineFractions = new ArrayList<List<Double>>();

        for (double d : fractions) {
            List<Double> l = new ArrayList<Double>();
            for (int i = 0; i < len; i++) {
                l.add(d);
            }
            onlineFractions.add(l);
        }

        return onlineFractions;
    }
}
