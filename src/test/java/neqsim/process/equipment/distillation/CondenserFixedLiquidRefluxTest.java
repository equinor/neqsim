package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    assertFalse(condenser.isFixedLiquidRefluxSpecificationSatisfied(),
        "the conservative split must report that the requested reflux was infeasible");
    assertTrue(condenser.getFixedLiquidRefluxSpecificationResidual() > 0.0,
        "an infeasible reflux request must expose a positive normalized shortfall");
    assertEquals(condenser.getLastAvailableFixedLiquidReflux(), condenser.getLastFixedLiquidReflux(),
        feed.getFlowRate("kg/hr") * 1.0e-8, "all available condensate must be returned as reflux");
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
    condenser.setSeparation_with_liquid_reflux(true, requestedRefluxKgPerSecond, " kg/sec ");

    condenser.run();

    assertEquals("kg/sec", condenser.getFixedLiquidRefluxUnit(),
        "fixed reflux units must be normalized before conversion and cache fingerprinting");
    assertEquals(availableLiquidKgPerHour / 2.0, condenser.getLiquidOutStream().getFlowRate("kg/hr"),
        availableLiquidKgPerHour * 1.0e-8, "the feasible reflux stream must satisfy the requested unit and value");
    assertTrue(condenser.isFixedLiquidRefluxSpecificationSatisfied(),
        "a feasible fixed reflux must satisfy its specification");
    assertEquals(0.0, condenser.getFixedLiquidRefluxSpecificationResidual(), 1.0e-12,
        "a feasible fixed reflux must have zero shortfall");
    List<StreamInterface> products = getProducts(condenser);
    assertMassAndComponentBalance(feed, products);
    assertEnergyBalance(feed, condenser, products);
    assertPhysical(products);
  }

  /**
   * A changed fixed reflux value must invalidate exact column reuse and an infeasible request must fail its
   * specification gate without breaking the external balance.
   */
  @Test
  public void changedInfeasibleColumnRefluxInvalidatesReuseAndFailsLoudly() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("ethane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("binary fixed reflux feed", fluid);
    feed.setFlowRate(2.0, "mol/sec");
    feed.run();

    DistillationColumn column = new DistillationColumn("fixed reflux column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.5);
    column.setMaxNumberOfIterations(60);
    column.getCondenser().setSeparation_with_liquid_reflux(true, 0.0, "kg/hr");
    column.run();
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    column.run();
    assertTrue(column.wasSequentialWarmStateReused(), "an unchanged accepted state must be reused exactly");
    assertEquals(0, column.getLastIterationCount(), "exact reuse must execute zero tray iterations");

    double impossibleRefluxKgPerHour = 2.0 * feed.getFlowRate("kg/hr");
    column.getCondenser().setSeparation_with_liquid_reflux(true, impossibleRefluxKgPerHour, "kg/hr");
    column.run();

    assertFalse(column.wasSequentialWarmStateReused(),
        "a changed fixed reflux value must invalidate the exact warm state");
    assertTrue(column.getLastIterationCount() > 0, "the changed fixed reflux equations must execute tray iterations");
    assertFalse(column.solved(), "an infeasible fixed reflux specification must not report a solved column");
    assertEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus(),
        column.getConvergenceDiagnostics());
    assertTrue(column.getCondenser().getFixedLiquidRefluxSpecificationResidual() > 0.0,
        "the column condenser must retain the infeasible specification residual");
    assertTrue(column.getConvergenceDiagnostics().contains("fixed liquid reflux"),
        "column diagnostics must expose the requested, available, delivered, and residual reflux values");

    List<StreamInterface> externalProducts = new ArrayList<>();
    externalProducts.add(column.getGasOutStream());
    externalProducts.add(column.getLiquidOutStream());
    StreamInterface liquidProduct = column.getCondenser().getLiquidProductStream();
    assertNotNull(liquidProduct, "fixed reflux mode must expose the separate condenser liquid product");
    externalProducts.add(liquidProduct);
    assertMassAndComponentBalance(feed, externalProducts);
    assertPhysical(externalProducts);
  }

  /** A rejected fixed-reflux tray state must not survive beside full-feed fallback products. */
  @Test
  public void fixedRefluxFallbackDiscardsRejectedLiquidProductInventory() {
    assertFixedRefluxFallbackClosesExternalBalance(100.0);
    assertFixedRefluxFallbackClosesExternalBalance(120.0);
  }

  /**
   * Run one fixed-reflux fallback case and verify that only one feed inventory remains exposed.
   *
   * @param refluxFlowKgPerHour requested fixed reflux in kg/hr
   */
  private static void assertFixedRefluxFallbackClosesExternalBalance(double refluxFlowKgPerHour) {
    SystemSrkEos fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("fixed reflux fallback feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("fixed reflux fallback column", 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getCondenser().setOutTemperature(293.15);
    column.getReboiler().setOutTemperature(353.15);
    column.setCondenserLiquidReflux(refluxFlowKgPerHour, "kg/hr");
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.2);
    column.setMaxNumberOfIterations(120, true);
    column.run();

    assertTrue(column.wasFeedFlashFallbackApplied(), "the regression must exercise guarded fallback handling");
    assertEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus());
    assertFalse(column.solved(), "balanced fallback products must not be reported as a rigorous column solution");
    assertFalse(column.getCondenser().isFixedLiquidRefluxSpecificationSatisfied(),
        "the rejected condenser split must not retain a satisfied specification diagnostic");

    StreamInterface liquidProduct = column.getCondenser().getLiquidProductStream();
    assertNotNull(liquidProduct);
    assertEquals(0.0, liquidProduct.getFlowRate("kg/hr"), 1.0e-12,
        "fallback products already contain the full feed inventory");
    List<StreamInterface> products = new ArrayList<>();
    products.add(column.getGasOutStream());
    products.add(column.getLiquidOutStream());
    products.add(liquidProduct);
    assertMassAndComponentBalance(feed, products);
    assertPhysical(products);

    column.run();
    assertTrue(column.wasFeedFlashFallbackApplied());
    assertEquals(0.0, column.getCondenser().getLiquidProductStream().getFlowRate("kg/hr"), 1.0e-12);
    products.set(0, column.getGasOutStream());
    products.set(1, column.getLiquidOutStream());
    products.set(2, column.getCondenser().getLiquidProductStream());
    assertMassAndComponentBalance(feed, products);
    assertEquals(20.0, feed.getTemperature("C"), 1.0e-12,
        "column fallback handling must preserve the caller-owned feed state");
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
    StreamInterface liquidProduct = condenser.getLiquidProductStream();
    assertNotNull(liquidProduct, "fixed reflux mode must expose the separate condenser liquid product");
    products.add(liquidProduct);
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
