package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

class PseudoComponentCombinerTest {
  private static final double TOLERANCE = 1e-8;

  private static SystemInterface createFluid1() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("ethane", 0.1);

    fluid.addTBPfraction("C7", 0.2, 0.100, 0.80);
    ComponentInterface c7 = fluid.getComponent("C7_PC");
    c7.setNormalBoilingPoint(350.0);
    c7.setTC(540.0);
    c7.setPC(28.0);
    c7.setAcentricFactor(0.32);

    fluid.addTBPfraction("C10", 0.1, 0.200, 0.85);
    ComponentInterface c10 = fluid.getComponent("C10_PC");
    c10.setNormalBoilingPoint(410.0);
    c10.setTC(620.0);
    c10.setPC(22.0);
    c10.setAcentricFactor(0.36);
    return fluid;
  }

  private static SystemInterface createFluid2() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("n-butane", 0.05);

    fluid.addTBPfraction("C8", 0.15, 0.120, 0.82);
    ComponentInterface c8 = fluid.getComponent("C8_PC");
    c8.setNormalBoilingPoint(380.0);
    c8.setTC(580.0);
    c8.setPC(26.0);
    c8.setAcentricFactor(0.34);

    fluid.addTBPfraction("C11", 0.05, 0.220, 0.86);
    ComponentInterface c11 = fluid.getComponent("C11_PC");
    c11.setNormalBoilingPoint(440.0);
    c11.setTC(660.0);
    c11.setPC(20.0);
    c11.setAcentricFactor(0.38);
    return fluid;
  }

  private static double mass(ComponentInterface component) {
    return component.getNumberOfmoles() * component.getMolarMass();
  }

  @Test
  @DisplayName("combine reservoir fluids follows Pedersen mixing weights")
  void testCombineReservoirFluids() {
    SystemInterface fluid1 = createFluid1();
    SystemInterface fluid2 = createFluid2();

    SystemInterface combined =
        PseudoComponentCombiner.combineReservoirFluids(2, Arrays.asList(fluid1, fluid2));

    assertEquals(1.0, combined.getComponent("methane").getNumberOfmoles(), TOLERANCE);
    assertEquals(0.1, combined.getComponent("ethane").getNumberOfmoles(), TOLERANCE);
    assertEquals(0.05, combined.getComponent("n-butane").getNumberOfmoles(), TOLERANCE);

    List<ComponentInterface> pseudoComponents =
        Arrays.stream(combined.getComponentNames()).map(combined::getComponent)
            .filter(component -> component.isIsTBPfraction() || component.isIsPlusFraction())
            .sorted((c1, c2) -> Double.compare(
                c1.getNormalBoilingPoint() > 0.0 ? c1.getNormalBoilingPoint() : c1.getMolarMass(),
                c2.getNormalBoilingPoint() > 0.0 ? c2.getNormalBoilingPoint() : c2.getMolarMass()))
            .collect(Collectors.toList());

    assertEquals(2, pseudoComponents.size());

    ComponentInterface low = pseudoComponents.get(0);
    assertEquals(0.35, low.getNumberOfmoles(), 1e-10);
    assertEquals(0.1085714286, low.getMolarMass(), 1e-9);
    assertEquals(0.809350649, low.getNormalLiquidDensity(), 1e-9);
    assertEquals(363.4767642, low.getNormalBoilingPoint(), 1e-6);
    assertEquals(557.9690189, low.getTC(), 1e-6);
    assertEquals(27.10154905, low.getPC(), 1e-6);
    assertEquals(0.328984509, low.getAcentricFactor(), 5e-4);

    ComponentInterface high = pseudoComponents.get(1);
    assertEquals(0.15, high.getNumberOfmoles(), 1e-10);
    assertEquals(0.2066666667, high.getMolarMass(), 1e-9);
    assertEquals(0.853521657, high.getNormalLiquidDensity(), 1e-9);
    assertEquals(420.5668016, high.getNormalBoilingPoint(), 1e-6);
    assertEquals(634.0890688, high.getTC(), 1e-6);
    assertEquals(21.29554656, high.getPC(), 1e-6);
    assertEquals(0.367044534, high.getAcentricFactor(), 5e-4);

    double expectedMass = mass(fluid1.getComponent("C7_PC")) + mass(fluid1.getComponent("C10_PC"))
        + mass(fluid2.getComponent("C8_PC")) + mass(fluid2.getComponent("C11_PC"));
    double combinedMass = mass(low) + mass(high);
    assertEquals(expectedMass, combinedMass, 1e-12);
  }

  private static SystemInterface createReferenceFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addTBPfraction("C7", 0.10, 0.090, 0.78);
    ComponentInterface c7 = fluid.getComponent("C7_PC");
    c7.setNormalBoilingPoint(340.0);
    c7.setTC(530.0);
    c7.setPC(29.0);
    c7.setAcentricFactor(0.31);

    fluid.addTBPfraction("C8", 0.12, 0.110, 0.81);
    ComponentInterface c8 = fluid.getComponent("C8_PC");
    c8.setNormalBoilingPoint(360.0);
    c8.setTC(550.0);
    c8.setPC(27.0);
    c8.setAcentricFactor(0.33);

    fluid.addTBPfraction("C9", 0.15, 0.150, 0.84);
    ComponentInterface c9 = fluid.getComponent("C9_PC");
    c9.setNormalBoilingPoint(380.0);
    c9.setTC(570.0);
    c9.setPC(25.0);
    c9.setAcentricFactor(0.35);
    return fluid;
  }

  private static SystemInterface createSourceFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.7);

    fluid.addTBPfraction("S1", 0.05, 0.090, 0.79);
    ComponentInterface s1 = fluid.getComponent("S1_PC");
    s1.setNormalBoilingPoint(330.0);
    s1.setTC(520.0);
    s1.setPC(30.0);
    s1.setAcentricFactor(0.30);

    fluid.addTBPfraction("S2", 0.07, 0.095, 0.80);
    ComponentInterface s2 = fluid.getComponent("S2_PC");
    s2.setNormalBoilingPoint(345.0);
    s2.setTC(535.0);
    s2.setPC(28.0);
    s2.setAcentricFactor(0.31);

    fluid.addTBPfraction("S3", 0.08, 0.120, 0.82);
    ComponentInterface s3 = fluid.getComponent("S3_PC");
    s3.setNormalBoilingPoint(365.0);
    s3.setTC(555.0);
    s3.setPC(26.0);
    s3.setAcentricFactor(0.33);

    fluid.addTBPfraction("S4", 0.09, 0.150, 0.83);
    ComponentInterface s4 = fluid.getComponent("S4_PC");
    s4.setNormalBoilingPoint(385.0);
    s4.setTC(575.0);
    s4.setPC(24.0);
    s4.setAcentricFactor(0.35);
    return fluid;
  }

  @Test
  @DisplayName("characterize to reference pseudo components")
  void testCharacterizeToReference() {
    SystemInterface source = createSourceFluid();
    SystemInterface reference = createReferenceFluid();

    SystemInterface characterized =
        PseudoComponentCombiner.characterizeToReference(source, reference);

    assertEquals(0.7, characterized.getComponent("methane").getNumberOfmoles(), TOLERANCE);

    List<ComponentInterface> pseudoComponents =
        Arrays.stream(characterized.getComponentNames()).map(characterized::getComponent)
            .filter(component -> component.isIsTBPfraction() || component.isIsPlusFraction())
            .sorted((c1, c2) -> Double.compare(
                c1.getNormalBoilingPoint() > 0.0 ? c1.getNormalBoilingPoint() : c1.getMolarMass(),
                c2.getNormalBoilingPoint() > 0.0 ? c2.getNormalBoilingPoint() : c2.getMolarMass()))
            .collect(Collectors.toList());

    assertEquals(3, pseudoComponents.size());

    ComponentInterface pc7 = characterized.getComponent("C7_PC");
    ComponentInterface pc8 = characterized.getComponent("C8_PC");
    ComponentInterface pc9 = characterized.getComponent("C9_PC");
    assertTrue(pc7 != null && pc8 != null && pc9 != null);

    assertEquals(0.12, pc7.getNumberOfmoles(), 1e-10);
    assertEquals(0.0929166667, pc7.getMolarMass(), 1e-9);
    assertEquals(0.795933811, pc7.getNormalLiquidDensity(), 1e-9);
    assertEquals(338.9461883, pc7.getNormalBoilingPoint(), 1e-6);
    assertEquals(528.9461883, pc7.getTC(), 1e-6);
    assertEquals(28.80717488, pc7.getPC(), 1e-6);
    assertEquals(0.305964126, pc7.getAcentricFactor(), 5e-4);

    assertEquals(0.08, pc8.getNumberOfmoles(), 1e-10);
    assertEquals(0.12, pc8.getMolarMass(), 1e-9);
    assertEquals(0.82, pc8.getNormalLiquidDensity(), 1e-9);
    assertEquals(365.0, pc8.getNormalBoilingPoint(), 1e-6);
    assertEquals(555.0, pc8.getTC(), 1e-6);
    assertEquals(26.0, pc8.getPC(), 1e-6);
    assertEquals(0.33, pc8.getAcentricFactor(), 1e-9);

    assertEquals(0.09, pc9.getNumberOfmoles(), 1e-10);
    assertEquals(0.15, pc9.getMolarMass(), 1e-9);
    assertEquals(0.83, pc9.getNormalLiquidDensity(), 1e-9);
    assertEquals(385.0, pc9.getNormalBoilingPoint(), 1e-6);
    assertEquals(575.0, pc9.getTC(), 1e-6);
    assertEquals(24.0, pc9.getPC(), 1e-6);
    assertEquals(0.35, pc9.getAcentricFactor(), 5e-4);

    double expectedMass = mass(source.getComponent("S1_PC")) + mass(source.getComponent("S2_PC"))
        + mass(source.getComponent("S3_PC")) + mass(source.getComponent("S4_PC"));
    double actualMass = mass(pc7) + mass(pc8) + mass(pc9);
    assertEquals(expectedMass, actualMass, 1e-12);
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
