package neqsim.thermo;

import org.junit.jupiter.api.Test;

/**
 * Class for testing FluidCreator.
 */
public class FluidCreatorTest {
  @Test
  void testCreate() {
    String[] componentNames = new String[] {"methane", "ethane"};
    FluidCreator.create(componentNames);
  }

  @Test
  void testCreate2() {
    String fluidType = "air";
    FluidCreator.create(fluidType);
  }

  @Test
  void testCreate3() {
    String[] componentNames = new String[] {"methane", "ethane"};
    double[] rate = new double[] {1.0, 1.0};
    String unit = "kg/sec";
    FluidCreator.create(componentNames, rate, unit);
  }
}
