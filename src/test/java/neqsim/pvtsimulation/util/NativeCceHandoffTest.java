package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.simulation.DepletionSnapshot;
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;
import neqsim.pvtsimulation.util.DepletionPvtHistoryGenerator.NativeResult;
import neqsim.pvtsimulation.util.MaterialBalancePvtHistory.Path;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Closed-mass native handoff qualification, distinct from withdrawal protocols.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NativeCceHandoffTest {
  /** Numerical component drift must be rejected rather than relabeled as CCE production or hidden by clipping. */
  @Test
  void closedMassDriftRejectsInsteadOfBecomingWithdrawal() {
    ConstantMassExpansion simulation = new ConstantMassExpansion(model(.7).getOriginalFluid());
    simulation.setTemperature(323.15, "K");
    simulation.setPressures(new double[] {500, 300});
    simulation.thermoOps = new ThermodynamicOperations(simulation.getThermoSystem()) {
      private static final long serialVersionUID = 1L;

      /** Perturbs one named inventory after a real equilibrium calculation. */
      @Override
      public void TPflash() {
        super.TPflash();
        getSystem().addComponent(0, 1e-6);
      }
    };
    IllegalStateException failure = assertThrows(IllegalStateException.class, simulation::runNativeExpansion);
    assertTrue(failure.getMessage().contains("closed-mass"));
    assertTrue(failure.getMessage().contains("methane"));
  }

  /**
   * Compares complete closed cells with six independently calculated binary SRK coexistence states.
   *
   * @throws IOException if the frozen reference cannot be read
   */
  @Test
  void independentBinaryEquilibriumAndWholeCellVolume() throws IOException {
    InputStream input = getClass().getResourceAsStream("/neqsim/pvtsimulation/native-cce/reference.json");
    assertNotNull(input);
    try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      JsonArray references = new Gson().fromJson(reader, JsonArray.class);
      assertEquals(6, references.size());
      for (JsonElement element : references) {
        JsonObject reference = element.getAsJsonObject();
        double methane = reference.get("methane").getAsDouble();
        NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(methane), false, Path.CCE,
            "synthetic independent binary closed-mass reference", reference.get("temperatureK").getAsDouble(),
            new double[] {200, 100, 50}, separator());
        int row = reference.get("pressureBara").getAsInt() == 200 ? 0
            : reference.get("pressureBara").getAsInt() == 100 ? 1 : 2;
        SystemInterface state = result.getSnapshots()[row].getEquilibriumFluid();
        double volume = reference.get("volumeM3").getAsDouble();
        assertEquals(volume, state.getCorrectedVolume(), volume * 1e-8);
        assertEquals(reference.get("gasMoleFraction").getAsDouble(), state.getPhase("gas").getBeta(), 1e-8);
        for (int component = 0; component < 2; component++) {
          assertEquals(reference.getAsJsonArray("liquidComposition").get(component).getAsDouble(),
              state.getPhase("oil").getComponent(component).getx(), 1e-8);
          assertEquals(reference.getAsJsonArray("gasComposition").get(component).getAsDouble(),
              state.getPhase("gas").getComponent(component).getx(), 1e-8);
        }
      }
    }
  }

  /** Native evidence has no invented saturation reference and does not populate legacy normalized arrays. */
  @Test
  void absoluteStatesDoNotRequireSaturationReference() {
    ConstantMassExpansion simulation = new ConstantMassExpansion(model(.7).getOriginalFluid());
    simulation.setTemperature(323.15, "K");
    simulation.setPressures(new double[] {500, 300, 200, 100});
    DepletionSnapshot[] states = simulation.runNativeExpansion();
    assertEquals(0, simulation.getSaturationPressure(), 0);
    assertEquals(4, states.length);
    SystemInterface restored = states[0].getRetainedFluid();
    restored.setPressure(1);
    assertEquals(500, states[0].getRetainedFluid().getPressure(), 0);
    assertArrayEquals(new double[2], states[3].getProducedComponentMoles(), 0);
  }

  /** A closed-mass gas/oil/gas path is not a gas-withdrawal CVD tail. */
  @Test
  void closedMassPhaseDisappearanceRetainsTheWholeCell() {
    PvtSeparatorPath surface = new PvtSeparatorPath(new double[] {1.0133}, new double[] {288.71},
        "fixed final product basis");
    NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(.95), false, Path.CCE,
        "closed-mass lower-dew crossing", 373.15, new double[] {500, 300, 100, 2, 1.2}, surface);
    assertEquals(1, result.getSnapshots()[0].getEquilibriumFluid().getNumberOfPhases());
    assertEquals(2, result.getSnapshots()[1].getEquilibriumFluid().getNumberOfPhases());
    assertEquals(1, result.getSnapshots()[4].getEquilibriumFluid().getNumberOfPhases());
    assertTrue(result.getSnapshots()[4].getEquilibriumFluid().hasPhaseType("gas"));
    for (DepletionSnapshot snapshot : result.getSnapshots()) {
      assertEquals(1, snapshot.getRetainedFluid().getTotalNumberOfMoles(), 1e-12);
      assertArrayEquals(new double[2], snapshot.getProducedComponentMoles(), 0);
    }
  }

  /** Failed pressure-step flashes must not be replaced by stale native states. */
  @Test
  void pressureStepFailureRetainsCause() {
    ConstantMassExpansion simulation = new ConstantMassExpansion(model(.7).getOriginalFluid());
    simulation.setTemperature(323.15, "K");
    simulation.setPressures(new double[] {500, 300});
    simulation.thermoOps = new ThermodynamicOperations(simulation.getThermoSystem()) {
      private static final long serialVersionUID = 1L;

      /** Injects an error only in the specified experiment row, outside the saturation scan. */
      @Override
      public void TPflash() {
        if (getSystem().getPressure() == 500) {
          throw new IllegalStateException("injected CCE flash failure");
        }
        super.TPflash();
      }
    };
    IllegalStateException error = assertThrows(IllegalStateException.class, simulation::runNativeExpansion);
    assertTrue(error.getMessage().contains("CCE"));
    assertEquals("injected CCE flash failure", error.getCause().getMessage());
  }

  /**
   * Freezes a declared binary SRK sample without fitting.
   *
   * @param methane methane mole fraction
   * @return original/tuned checkpoint bundle
   */
  private CalibratedPvtModel model(double methane) {
    SystemInterface fluid = new SystemSrkEos(323.15, 500);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-decane", 1 - methane);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    return new CalibratedPvtModel(fluid, fluid, "synthetic CCE sample", "original SRK", "tuned SRK",
        "declared synthetic closed-mass protocol", new String[] {"CCE-holdout"});
  }

  /**
   * Returns the fixed product basis, not a reservoir withdrawal constraint.
   *
   * @return immutable separator train
   */
  private PvtSeparatorPath separator() {
    return new PvtSeparatorPath(new double[] {10, 1.0133}, new double[] {303.15, 288.71},
        "synthetic train, final 15.56 C / 101.33 kPa absolute");
  }

  /** CCE retains all components through both oil and gas phase appearance for either EOS checkpoint. */
  @Test
  void closedMassAcrossPhaseAppearanceAndCheckpointSelection() {
    for (double methane : new double[] {.7, .95}) {
      double temperature = methane == .7 ? 323.15 : 373.15;
      for (boolean tuned : new boolean[] {false, true}) {
        NativeResult result = DepletionPvtHistoryGenerator.replayNative(model(methane), tuned, Path.CCE,
            "whole CCE-holdout", temperature, new double[] {500, 300, 200, 100, 50}, separator());
        assertEquals(tuned, result.isTuned());
        assertEquals(Path.CCE, result.getPath());
        assertEquals("whole CCE-holdout", result.getPathReference());
        assertEquals(1, result.getSnapshots()[0].getEquilibriumFluid().getNumberOfPhases());
        assertEquals(2, result.getSnapshots()[3].getEquilibriumFluid().getNumberOfPhases());
        for (int row = 0; row < result.getSnapshots().length; row++) {
          DepletionSnapshot snapshot = result.getSnapshots()[row];
          SystemInterface cell = snapshot.getRetainedFluid();
          assertArrayEquals(new double[] {methane, 1 - methane}, cell.getMolarComposition(), 1e-12);
          assertArrayEquals(new double[2], snapshot.getProducedComponentMoles(), 0);
          assertFalse(result.hasWithdrawal(row));
          final int index = row;
          assertThrows(IllegalStateException.class, () -> result.getWithdrawalProducts(index));
          double[][] products = result.getRetainedProducts(row).getComponentMoles();
          for (int component = 0; component < 2; component++) {
            assertEquals(new double[] {methane, 1 - methane}[component],
                cell.getComponent(component).getNumberOfmoles(), 1e-10);
            assertEquals(cell.getComponent(component).getNumberOfmoles(),
                products[0][component] + products[1][component] + products[2][component], 1e-10);
          }
        }
        NativeResult restored = SerializationUtils.clone(result);
        assertEquals(500, restored.getSnapshots()[0].getRetainedFluid().getPressure(), 0);
        assertEquals(result.getModel().getProvenance(), restored.getModel().getProvenance());
      }
    }
  }

  /** Pure gas and liquid limits agree with an independent SRK pressure-equation root at fixed total moles. */
  @Test
  void independentPureFluidVolumes() {
    for (double methane : new double[] {0, 1}) {
      double temperature = methane == 0 ? 323.15 : 373.15;
      CalibratedPvtModel model = model(methane);
      SystemInterface original = model.getOriginalFluid();
      int component = methane == 0 ? 1 : 0;
      double tc = original.getComponent(component).getTC();
      double pc = original.getComponent(component).getPC() * 1e5;
      double omega = original.getComponent(component).getAcentricFactor();
      double r = 8.3144621;
      double c = Math.cbrt(2) - 1;
      double b = c / 3 * r * tc / pc;
      double m = .48 + 1.574 * omega - .176 * omega * omega;
      double a = r * r * tc * tc / (9 * c * pc) * Math.pow(1 + m * (1 - Math.sqrt(temperature / tc)), 2);
      NativeResult result = DepletionPvtHistoryGenerator.replayNative(model, false, Path.CCE, "pure closed mass",
          temperature, new double[] {500, 300, 200}, separator());
      for (DepletionSnapshot snapshot : result.getSnapshots()) {
        SystemInterface cell = snapshot.getRetainedFluid();
        double pressure = cell.getPressure() * 1e5;
        double low = b * (1 + 1e-10);
        double high = Math.max(100 * b, 100 * r * temperature / pressure);
        for (int iteration = 0; iteration < 150; iteration++) {
          double v = (low + high) / 2;
          double calculated = r * temperature / (v - b) - a / (v * (v + b));
          if (calculated > pressure)
            low = v;
          else
            high = v;
        }
        double expected = (low + high) / 2;
        assertEquals(expected, cell.getCorrectedVolume(), expected * 1e-8);
        assertEquals(1, cell.getTotalNumberOfMoles(), 1e-12);
      }
    }
  }
}
