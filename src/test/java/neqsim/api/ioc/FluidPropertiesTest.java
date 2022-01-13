package neqsim.api.ioc;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class FluidPropertiesTest {
    @Test
    void testDoCalculation() {
        List<Double> pressure = Arrays.asList(1.38, 2.56, 4.3);
        List<Double> temperature = Arrays.asList(300.0, 310.0, 320.0);

        SystemInterface fluid = new SystemSrkEos();
        fluid.addComponent("methane", 100.0);

        CalcRequest req = new CalcRequest(pressure, temperature, 1, fluid);

        FluidProperties a = new FluidProperties();
        try {
            CalculationResult b = a.doCalculation(req);
            System.out.println(b.toString());
        } catch (NeqSimException e) {
            // TODO: handle exception
        }
    }
}
