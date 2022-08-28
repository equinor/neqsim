package neqsim.thermo;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;

public class FluidCreatorTest {
  @Test
  void testCreate() {
    String[] componentNames = new String[]{"methane", "ethane"};
    SystemInterface fluid = FluidCreator.create(componentNames);  
  }

  @Test
  void testCreate2() {
    String fluidType = "air";
    SystemInterface fluid = FluidCreator.create(fluidType);
  }

  @Test
  void testCreate3() {
    String[] componentNames = new String[] {"methane", "ethane"};
    double[] rate = new double[] {1.0, 1.0};
    String unit = "kg/sec";
    SystemInterface fluid = FluidCreator.create(componentNames, rate, unit);
  }
}
