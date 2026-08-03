package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for the condenser's fixed liquid reflux split. */
public class CondenserFixedLiquidRefluxTest {
  /** Fixed reflux above the available condensate must not manufacture material. */
  @Test
  public void infeasibleFixedLiquidRefluxConservesInventory() {
    Stream feed = createHydrocarbonFeed();
    Condenser condenser = createCondenser(feed);
    condenser.setSeparation_with_liquid_reflux(true, 2.0 * feed.getFlowRate("kg/hr"), "kg/hr");

    condenser.run();

    assertNotNull(condenser.getLiquidProductStream());
    List<StreamInterface> products = getProducts(condenser);
    assertMassAndComponentBalance(feed, products);
    assertEnergyBalance(feed, condenser, products);
    assertTrue(condenser.getLiquidOutStream().getFlowRate("kg/hr") <= feed.getFlowRate("kg/hr"),
        "an infeasible reflux request must not exceed the complete feed inventory");
    assertPhysical(products);
  }

  /** A feasible fixed reflux must retain the requested unit conversion and close all balances. */
  @Test
  public void feasibleFixedLiquidRefluxUsesRequestedUnitsAndClosesBalances() {
    Stream referenceFeed = createHydrocarbonFeed();
    Condenser reference = createCondenser(referenceFeed);
    reference.run();
    double availableLiquidKgPerHour = reference.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(availableLiquidKgPerHour > 1.0,
        "the reference condenser must produce enough liquid to exercise a non-zero split");

    Stream feed = createHydrocarbonFeed();
    Condenser condenser = createCondenser(feed);
    double requestedRefluxKgPerSecond = availableLiquidKgPerHour / 7200.0;
    condenser.setSeparation_with_liquid_reflux(true, requestedRefluxKgPerSecond, "kg/sec");

    condenser.run();

    assertEquals(availableLiquidKgPerHour / 2.0, condenser.getLiquidOutStream().getFlowRate("kg/hr"),
        availableLiquidKgPerHour * 1.0e-8, "the feasible reflux stream must satisfy the requested unit and value");
    List<StreamInterface> products = getProducts(condenser);
    assertMassAndComponentBalance(feed, products);
    assertEnergyBalance(feed, condenser, products);
    assertPhysical(products);
  }

  private static Stream createHydrocarbonFeed() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 8.0);
    fluid.addComponent("methane", 0.05);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.25);
    fluid.addComponent("n-butane", 0.30);
    fluid.addComponent("n-pentane", 0.30);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("fixed reflux feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(8.0, "bara");
    feed.run();
    return feed;
  }

  private static Condenser createCondenser(Stream feed) {
    Condenser condenser = new Condenser("fixed reflux condenser");
    condenser.addStream(feed);
    condenser.setOutTemperature(278.15);
    return condenser;
  }

  private static List<StreamInterface> getProducts(Condenser condenser) {
    List<StreamInterface> products = new ArrayList<>();
    products.add(condenser.getGasOutStream());
    products.add(condenser.getLiquidOutStream());
    products.add(condenser.getLiquidProductStream());
    return products;
  }

  private static void assertMassAndComponentBalance(Stream feed, List<StreamInterface> products) {
    double feedMass = feed.getFlowRate("kg/hr");
    double productMass = products.stream().mapToDouble(stream -> stream.getFlowRate("kg/hr")).sum();
    assertEquals(feedMass, productMass, feedMass * 1.0e-8, "condenser products must conserve total mass");

    double feedMolarFlow = feed.getFlowRate("mol/hr");
    for (int component = 0; component < feed.getThermoSystem().getNumberOfComponents(); component++) {
      double feedComponentFlow = feedMolarFlow * feed.getThermoSystem().getComponent(component).getz();
      double productComponentFlow = 0.0;
      for (StreamInterface product : products) {
        productComponentFlow += product.getFlowRate("mol/hr")
            * product.getThermoSystem().getComponent(component).getz();
      }
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-10, Math.abs(feedComponentFlow) * 1.0e-8),
          "condenser products must conserve component " + component);
    }
  }

  private static void assertEnergyBalance(Stream feed, Condenser condenser, List<StreamInterface> products) {
    feed.getThermoSystem().init(2);
    double feedEnthalpy = feed.getThermoSystem().getEnthalpy();
    double productEnthalpy = 0.0;
    for (StreamInterface product : products) {
      product.getThermoSystem().init(2);
      productEnthalpy += product.getThermoSystem().getEnthalpy();
    }
    double expectedProductEnthalpy = feedEnthalpy + condenser.getDuty();
    assertEquals(expectedProductEnthalpy, productEnthalpy, Math.max(1.0e-6, Math.abs(expectedProductEnthalpy) * 1.0e-8),
        "condenser duty and product enthalpies must close the energy balance");
  }

  private static void assertPhysical(List<StreamInterface> products) {
    for (StreamInterface product : products) {
      double flow = product.getFlowRate("mol/hr");
      assertTrue(Double.isFinite(flow) && flow >= 0.0, "product flow must be finite and non-negative");
      assertTrue(Double.isFinite(product.getTemperature("K")) && product.getTemperature("K") > 0.0,
          "product temperature must be physical");
      assertTrue(Double.isFinite(product.getPressure("bara")) && product.getPressure("bara") > 0.0,
          "product pressure must be physical");
      if (flow > 1.0e-12) {
        double compositionSum = 0.0;
        for (double moleFraction : product.getThermoSystem().getMolarComposition()) {
          assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0 && moleFraction <= 1.0,
              "flowing product compositions must be physical");
          compositionSum += moleFraction;
        }
        assertEquals(1.0, compositionSum, 1.0e-10, "flowing product composition must sum to one");
      }
    }
  }
}
