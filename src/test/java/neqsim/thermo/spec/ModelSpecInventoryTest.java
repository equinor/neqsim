package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The inventory classifies coverage debt, never interprets untested classes as supported models. */
class ModelSpecInventoryTest {
  @Test
  void everyConcreteSystemAndPhaseHasAnExplicitClassification() throws Exception {
    List<ModelSpecInventory.Entry> entries = ModelSpecInventory.load();
    List<ModelSpec> cases = ModelSpec.load();
    ModelSpecInventory.validate(entries, ModelSpecInventory.discover(), cases);
    ModelSpecInventory.report(entries, cases);
  }

  @Test
  void missingOrNewModelsCannotSilentlyBypassInventory() throws Exception {
    List<ModelSpecInventory.Entry> entries = ModelSpecInventory.load();
    Map<String, ModelSpecInventory.Kind> types = ModelSpecInventory.discover();
    List<ModelSpec> cases = ModelSpec.load();
    entries.remove(0);
    assertThrows(IllegalArgumentException.class, () -> ModelSpecInventory.validate(entries, types, cases));
    entries.addAll(ModelSpecInventory.load().subList(0, 1));
    types.put("neqsim.thermo.system.NewUnqualifiedModel", ModelSpecInventory.Kind.SYSTEM);
    assertThrows(IllegalArgumentException.class, () -> ModelSpecInventory.validate(entries, types, cases));
    entries.add(new ModelSpecInventory.Entry(new String[] {"neqsim.thermo.system.NewUnqualifiedModel", "SYSTEM", "DEBT",
        "-", "-", "not-qualified", "https://github.com/equinor/neqsim/issues/3792", "Needs a sourced fixture"}));
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> ModelSpecInventory.validate(entries, types, cases));
    assertTrue(error.getMessage().contains("cannot expand initial coverage debt"));
  }

  @Test
  void missingPropertyAndStaleTypeBindingsFail() throws Exception {
    List<ModelSpecInventory.Entry> entries = ModelSpecInventory.load();
    Map<String, ModelSpecInventory.Kind> types = ModelSpecInventory.discover();
    List<ModelSpec> cases = new ArrayList<ModelSpec>(ModelSpec.load());
    cases.removeIf(s -> s.property == ModelSpec.Property.DPSAT_DT);
    assertThrows(IllegalArgumentException.class, () -> ModelSpecInventory.validate(entries, types, cases));
    Map<String, ModelSpecInventory.Kind> incorrect = new TreeMap<String, ModelSpecInventory.Kind>(types);
    incorrect.remove(entries.get(0).type);
    assertThrows(IllegalArgumentException.class,
        () -> ModelSpecInventory.validate(entries, incorrect, ModelSpec.load()));
  }

  @Test
  void coveredFixtureCannotBeRelabelledAsDebt() throws Exception {
    List<ModelSpecInventory.Entry> entries = ModelSpecInventory.load();
    ModelSpecInventory.Entry first = entries.get(0);
    assertEquals(ModelSpecInventory.Status.PARTIAL, first.status);
    entries.set(0, new ModelSpecInventory.Entry(
        new String[] {first.type, first.kind.name(), "DEBT", "-", "-", "not-qualified", first.issue, first.review}));
    assertThrows(IllegalArgumentException.class,
        () -> ModelSpecInventory.validate(entries, ModelSpecInventory.discover(), ModelSpec.load()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-type", "unsupported-value", "property-loss", "duplicate-binding"})
  void falseQualificationFails(String fault) throws Exception {
    List<ModelSpecInventory.Entry> entries = ModelSpecInventory.load();
    ModelSpecInventory.Entry first = entries.get(0);
    String[] row = {first.type, first.kind.name(), first.status.name(), "SATURATION;ANTOINE_ANALYTIC",
        "PSAT;DPSAT_DT;T_SAT", first.domain, first.issue, first.review};
    if ("wrong-type".equals(fault)) {
      row[3] = "SRK";
    }
    if ("unsupported-value".equals(fault)) {
      row[2] = "UNSUPPORTED";
    }
    if ("property-loss".equals(fault)) {
      row[4] = "PSAT";
    }
    if ("duplicate-binding".equals(fault)) {
      row[3] += ";SRK";
    }
    entries.set(0, new ModelSpecInventory.Entry(row));
    assertThrows(IllegalArgumentException.class,
        () -> ModelSpecInventory.validate(entries, ModelSpecInventory.discover(), ModelSpec.load()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "version", "header", "duplicate", "kind", "status", "fixture", "property", "domain",
      "issue", "review", "columns"})
  void malformedInventoryFailsClosed(String fault) throws IOException {
    String version = "# neqsim-model-inventory-v1";
    String header = ModelSpecInventory.HEADER;
    String[] row = {"neqsim.thermo.system.SystemSrkEos", "SYSTEM", "PARTIAL", "SRK", "Z;HID", "catalog-only",
        "https://github.com/equinor/neqsim/issues/3792", "Review before expanding domains"};
    switch (fault) {
    case "version":
      version = "v99";
      break;
    case "header":
      header = "wrong";
      break;
    case "kind":
      row[1] = "ANY";
      break;
    case "status":
      row[2] = "SUPPORTED";
      break;
    case "fixture":
      row[3] = "UNKNOWN";
      break;
    case "property":
      row[4] = "UNKNOWN";
      break;
    case "domain":
      row[5] = "everywhere";
      break;
    case "issue":
      row[6] = "-";
      break;
    case "review":
      row[7] = "-";
      break;
    default:
      break;
    }
    String line = String.join("\t", row);
    String body = "empty".equals(fault) ? "" : line + "\n";
    if ("duplicate".equals(fault)) {
      body += line + "\n";
    }
    if ("columns".equals(fault)) {
      body = line + "\textra\n";
    }
    final String input = version + "\n" + header + "\n" + body;
    assertThrows(IllegalArgumentException.class, () -> ModelSpecInventory.parse(input));
  }
}
