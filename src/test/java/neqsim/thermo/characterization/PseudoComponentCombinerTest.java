package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

class PseudoComponentCombinerTest {

  private static SystemInterface createFluid1() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("ethane", 0.1);
    fluid.addTBPfraction("C7", 0.4, 0.100, 0.80);
    fluid.addTBPfraction("C8", 0.3, 0.110, 0.82);
    return fluid;
  }

  private static SystemInterface createFluid2() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("n-butane", 0.05);
    fluid.addTBPfraction("C9", 0.35, 0.120, 0.83);
    fluid.addTBPfraction("C10", 0.25, 0.130, 0.85);
    return fluid;
  }

  private static double totalPseudoMass(SystemInterface fluid) {
    double totalMass = 0.0;
    for (String name : fluid.getComponentNames()) {
      ComponentInterface component = fluid.getComponent(name);
      if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
        totalMass += component.getNumberOfmoles() * component.getMolarMass();
      }
    }
    return totalMass;
  }

  private static double totalPseudoMoles(SystemInterface fluid) {
    double total = 0.0;
    for (String name : fluid.getComponentNames()) {
      ComponentInterface component = fluid.getComponent(name);
      if (component.isIsTBPfraction() || component.isIsPlusFraction()) {
        total += component.getNumberOfmoles();
      }
    }
    return total;
  }

  @Test
  @DisplayName("combine reservoir fluids preserves base components and pseudo totals")
  void testCombineReservoirFluids() {
    SystemInterface fluid1 = createFluid1();
    SystemInterface fluid2 = createFluid2();

    double expectedMethane = fluid1.getComponent("methane").getNumberOfmoles()
        + fluid2.getComponent("methane").getNumberOfmoles();
    double expectedEthane = fluid1.getComponent("ethane").getNumberOfmoles();
    double expectedButane = fluid2.getComponent("n-butane").getNumberOfmoles();

    double expectedPseudoMoles = totalPseudoMoles(fluid1) + totalPseudoMoles(fluid2);
    double expectedPseudoMass = totalPseudoMass(fluid1) + totalPseudoMass(fluid2);

    SystemInterface combined =
        PseudoComponentCombiner.combineReservoirFluids(3, Arrays.asList(fluid1, fluid2));

    assertEquals(expectedMethane, combined.getComponent("methane").getNumberOfmoles(), 1e-8);
    assertEquals(expectedEthane, combined.getComponent("ethane").getNumberOfmoles(), 1e-8);
    assertEquals(expectedButane, combined.getComponent("n-butane").getNumberOfmoles(), 1e-8);

    double combinedPseudoMoles = totalPseudoMoles(combined);
    double combinedPseudoMass = totalPseudoMass(combined);

    assertEquals(expectedPseudoMoles, combinedPseudoMoles, 1e-8);
    assertEquals(expectedPseudoMass, combinedPseudoMass, 1e-8);

    long pseudoCount = Arrays.stream(combined.getComponentNames())
        .map(combined::getComponent)
        .filter(comp -> comp.isIsTBPfraction() || comp.isIsPlusFraction()).count();
    assertEquals(3, pseudoCount);

    ComponentInterface[] pseudoComponents = Arrays.stream(combined.getComponentNames())
        .map(combined::getComponent)
        .filter(comp -> comp.isIsTBPfraction() || comp.isIsPlusFraction())
        .sorted((c1, c2) -> Double.compare(
            c1.getNormalBoilingPoint() > 0 ? c1.getNormalBoilingPoint() : c1.getMolarMass(),
            c2.getNormalBoilingPoint() > 0 ? c2.getNormalBoilingPoint() : c2.getMolarMass()))
        .toArray(ComponentInterface[]::new);

    for (int i = 1; i < pseudoComponents.length; i++) {
      double previousKey = pseudoComponents[i - 1].getNormalBoilingPoint() > 0
          ? pseudoComponents[i - 1].getNormalBoilingPoint()
          : pseudoComponents[i - 1].getMolarMass();
      double currentKey = pseudoComponents[i].getNormalBoilingPoint() > 0
          ? pseudoComponents[i].getNormalBoilingPoint()
          : pseudoComponents[i].getMolarMass();
      assertTrue(currentKey + 1e-9 >= previousKey);
    }
  }

  @Test
  @DisplayName("combine supports more pseudo components than input")
  void testCombineMorePseudoComponentsThanInput() {
    SystemInterface fluid1 = createFluid1();
    SystemInterface combined = PseudoComponentCombiner.combineReservoirFluids(5,
        Arrays.asList(fluid1, createFluid2()));

    long pseudoCount = Arrays.stream(combined.getComponentNames())
        .map(combined::getComponent)
        .filter(comp -> comp.isIsTBPfraction() || comp.isIsPlusFraction()).count();
    assertEquals(5, pseudoCount);

    double expectedPseudoMass = totalPseudoMass(fluid1) + totalPseudoMass(createFluid2());
    double combinedPseudoMass = totalPseudoMass(combined);
    assertEquals(expectedPseudoMass, combinedPseudoMass, 1e-8);
  }

  @Test
  @DisplayName("invalid input throws exception")
  void testInvalidInput() {
    assertThrows(IllegalArgumentException.class,
        () -> PseudoComponentCombiner.combineReservoirFluids(0, createFluid1()));
    assertThrows(IllegalArgumentException.class,
        () -> PseudoComponentCombiner.combineReservoirFluids(3));
  }
}

