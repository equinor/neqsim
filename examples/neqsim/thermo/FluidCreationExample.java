package neqsim.thermo;

/** Simple examples demonstrating the {@link FluidCreator} helper methods. */
public class FluidCreationExample {

  private FluidCreationExample() {}

  public static void main(String[] args) {
    String[] componentNames = new String[] {"methane", "ethane"};
    System.out.println("Creating fluid from component names...");
    FluidCreator.create(componentNames);

    String fluidType = "air";
    System.out.println("Creating predefined fluid type: " + fluidType);
    FluidCreator.create(fluidType);

    double[] rate = new double[] {1.0, 1.0};
    String unit = "kg/sec";
    System.out.println("Creating fluid with specified component flowrates...");
    FluidCreator.create(componentNames, rate, unit);
  }
}
