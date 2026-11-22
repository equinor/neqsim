package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Exact passive pressure-relief import, source purity and canonical-to-native readback regressions. */
class PidNativePressureReliefTest {
  /** Actual passive hardware is imported atomically without a fake sensor or function. */
  @Test
  void importPreservesExistingGraphAndHasNoSignalEdges() {
    Fixture f = fixture();
    String before = new Gson().toJson(f.pid.toMap());
    PidDesignModel imported = imported(f);
    assertEquals(before, new Gson().toJson(f.pid.toMap()));
    assertEquals(2, imported.getElements().size());
    assertEquals("EXISTING", imported.getElements().get(0).getId());
    PidElement psv = imported.getElements().get(1);
    assertEquals(PidElementType.SAFETY_RELIEF_VALVE, psv.getType());
    assertTrue(psv.getConnectedElementIds().isEmpty());
    assertEquals(0, imported.getElementsByType(PidElementType.SAFETY_FUNCTION).size());
    assertEquals(0, imported.getElementsByType(PidElementType.MEASUREMENT).size());
    PidSimulationBindings b = bind(f, imported);
    PidDesignModel reused = PidPressureReliefImporter.fromNativeValve(f.project, imported, b, f.process, "PSV",
        "VESSEL", "TEE", "CV-BASIS");
    assertEquals(new Gson().toJson(imported.toMap()), new Gson().toJson(reused.toMap()));
    PidPressureReliefValidator.ConfiguredRelief receipt = PidPressureReliefValidator.validateConfigured(f.project,
        imported, b, f.process, "PSV");
    assertEquals("native-relief:PSV", receipt.toMap().get("pidElementId"));
  }

  /** Missing capacity evidence is rejected without modifying source or canonical input. */
  @Test
  void rejectsMissingCapacityBasis() {
    Fixture f = fixture();
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefImporter.fromNativeValve(f.project, f.pid,
        f.bindings, f.process, "PSV", "VESSEL", "TEE", "UNREGISTERED"));
    assertEquals(1, f.pid.getElements().size());
  }

  /** Route identity is checked using actual stream references, not matching labels. */
  @Test
  void rejectsWrongTeeAndDisconnectedInlet() {
    Fixture f = fixture();
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefImporter.fromNativeValve(f.project, f.pid,
        f.bindings, f.process, "PSV", "VESSEL", "MISSING", "CV-BASIS"));
    SafetyReliefValve valve = (SafetyReliefValve) f.process.getUnit("PSV");
    valve.setInletStream(new Stream("PSV-INLET", valve.getInletStream().getThermoSystem().clone()));
    assertThrows(IllegalStateException.class, () -> imported(f));
  }

  /** Canonical settings must not be applied to the governed source. */
  @Test
  void rejectsConfigurationOfOriginalSource() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefValidator.applyCanonicalConfiguration(f.project,
        model, bind(f, model), f.process, "PSV"));
  }

  /** Fresh bindings do not excuse an unconsumed set pressure edit; native reconfiguration changes response. */
  @Test
  void canonicalSetpointEditChangesFingerprintAndActualOpening() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    PidSimulationBindings originalBindings = bind(f, model);
    ProcessSystem beforeProcess = f.process.copy();
    PidPressureReliefValidator.ConfiguredRelief before = PidPressureReliefValidator
        .applyCanonicalConfiguration(f.project, model, originalBindings, beforeProcess, "PSV");
    Map<String, Object> revised = config(model);
    revised.put("setPressureBar", Double.valueOf(42));
    revised.put("fullLiftPressureBar", Double.valueOf(46.2));
    revised.put("reseatPressureBar", Double.valueOf(39.06));
    model.getElements().get(1).attribute("simulationPressureReliefConfiguration", revised);
    assertThrows(IllegalStateException.class, () -> originalBindings.assertCurrent(f.project, model));
    PidSimulationBindings rebound = bind(f, model);
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, rebound, beforeProcess, "PSV"));
    ProcessSystem afterProcess = f.process.copy();
    PidPressureReliefValidator.ConfiguredRelief after = PidPressureReliefValidator
        .applyCanonicalConfiguration(f.project, model, rebound, afterProcess, "PSV");
    assertNotEquals(before.getFingerprint(), after.getFingerprint());
    assertThrows(IllegalStateException.class, () -> before.assertSameConfiguration(after));
    SafetyReliefValve oldValve = (SafetyReliefValve) beforeProcess.getUnit("PSV");
    SafetyReliefValve changedValve = (SafetyReliefValve) afterProcess.getUnit("PSV");
    for (SafetyReliefValve valve : new SafetyReliefValve[] {oldValve, changedValve}) {
      valve.getInletStream().setPressure(41.5, "bara");
      valve.getInletStream().run();
      valve.setCalculateSteadyState(false);
      valve.runTransient(.1, UUID.randomUUID());
    }
    assertTrue(oldValve.getOpenFraction() > 0);
    assertEquals(0, changedValve.getOpenFraction());
    assertEquals(41.0, ((SafetyReliefValve) f.process.getUnit("PSV")).getSetPressureBar());
    assertEquals(0, ((SafetyReliefValve) f.process.getUnit("PSV")).getOpenFraction());
  }

  /** Derived pressure values and units cannot silently disagree with the native setter. */
  @Test
  void rejectsIncoherentDerivedPressureAndWrongUnits() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    Map<String, Object> c = config(model);
    c.put("fullLiftPressureBar", Double.valueOf(99));
    model.getElements().get(1).attribute("simulationPressureReliefConfiguration", c);
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefValidator.applyCanonicalConfiguration(f.project,
        model, bind(f, model), f.process.copy(), "PSV"));
    c.put("pressureUnit", "barg");
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefValidator.applyCanonicalConfiguration(f.project,
        model, bind(f, model), f.process.copy(), "PSV"));
  }

  /** Actual route and mechanical settings remain part of configured-copy readback. */
  @Test
  void rejectsChangedBackpressureAndIgnoredNativeSettings() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    ProcessSystem copy = f.process.copy();
    ((SafetyReliefValve) copy.getUnit("PSV")).setOutletPressure(7.0);
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, bind(f, model), copy, "PSV"));
    ((SafetyReliefValve) copy.getUnit("PSV")).setOutletPressure(2.0);
    ((SafetyReliefValve) copy.getUnit("PSV")).setFp(.5);
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, bind(f, model), copy, "PSV"));
  }

  /** A second commanded actuator is unsupported for this passive relief contract. */
  @Test
  void rejectsSecondaryTravelModel() {
    Fixture f = fixture();
    ((SafetyReliefValve) f.process.getUnit("PSV")).setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
    assertThrows(IllegalArgumentException.class, () -> imported(f));
  }

  /** Passive hardware must not gain an invented PLC signal on reuse. */
  @Test
  void rejectsFakePlcSignal() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    model.getElements().get(1).connect("EXISTING");
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, bind(f, model), f.process, "PSV"));
  }

  /** Actual tee, normal branch and relief branch each appear once in both native drawing views. */
  @Test
  void rendersActualPassiveBranchWithoutAnActuatorOrFakeControlSignal() {
    Fixture f = fixture();
    PidDesignModel empty = new PidDesignModel(f.project.getProjectId(), "ACTUAL-PASSIVE");
    PidDesignModel model = PidPressureReliefImporter.fromNativeValve(f.project, empty,
        PidSimulationBindings.from(f.project, empty), f.process, "PSV", "VESSEL", "TEE", "CV-BASIS");
    PidSimulationBindings bindings = bind(f, model);
    neqsim.process.engineering.model.EngineeringDiagramDocumentSet documents = neqsim.process.processmodel.diagram.ProcessDiagramDocumentSetAdapter
        .fromProcessSystem(f.process, f.project.getProjectId(), f.project.getRevision(), "PSV-PID",
            "Actual passive relief", neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile.PID);
    neqsim.process.processmodel.diagram.ProfessionalPidDiagramRenderer renderer = new neqsim.process.processmodel.diagram.ProfessionalPidDiagramRenderer(
        f.project, model, bindings, documents,
        new neqsim.process.engineering.model.EngineeringDiagramConventionRegister());
    neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer.Result result = renderer.render();
    assertTrue(result.isComplete(), new Gson().toJson(result.getDiagnostics()));
    assertEquals(1, result.getSvgBySheetId().size());
    String svg = result.getSvgBySheetId().values().iterator().next();
    assertTrue(svg.contains("data-symbol-family=\"PIPE_TEE\""));
    assertTrue(svg.contains("data-symbol-family=\"RELIEF_VALVE\""));
    assertTrue(svg.contains("DISPOSAL"));
    assertFalse(svg.contains("PLC"));
    for (Map<String, Object> layout : renderer.getLayoutSpecifications().values()) {
      int tees = 0;
      int reliefs = 0;
      for (Object row : (java.util.List<?>) layout.get("nodes")) {
        Map<?, ?> node = (Map<?, ?>) row;
        if ("TEE".equals(node.get("id"))) {
          tees++;
        }
        if ("PSV".equals(node.get("id"))) {
          reliefs++;
        }
      }
      assertEquals(1, tees);
      assertEquals(1, reliefs);
    }
    renderer = new neqsim.process.processmodel.diagram.ProfessionalPidDiagramRenderer(f.project, model, bindings,
        documents, new neqsim.process.engineering.model.EngineeringDiagramConventionRegister());
    result = renderer.renderProcessFlowOverview();
    assertTrue(result.isComplete(), new Gson().toJson(result.getDiagnostics()));
  }

  /** A healthy proposal can exercise explicitly declared motion and boundary faults without redefining design. */
  @Test
  void explicitCaseFaultsHaveDifferentReceiptsAndCannotBeImplicit() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    PidSimulationBindings bindings = bind(f, model);
    ProcessSystem healthy = f.process.copy();
    PidPressureReliefValidator.ConfiguredRelief before = PidPressureReliefValidator
        .applyCanonicalConfiguration(f.project, model, bindings, healthy, "PSV");
    Map<String, Object> overrides = new LinkedHashMap<String, Object>();
    overrides.put("liftMotionAvailable", Boolean.FALSE);
    ProcessSystem fault = f.process.copy();
    PidPressureReliefValidator.ConfiguredRelief after = PidPressureReliefValidator
        .applyCanonicalConfiguration(f.project, model, bindings, fault, "PSV", overrides, Double.valueOf(7));
    assertNotEquals(before.getFingerprint(), after.getFingerprint());
    assertFalse(((SafetyReliefValve) fault.getUnit("PSV")).isLiftMotionAvailable());
    assertEquals(7, ((SafetyReliefValve) fault.getUnit("PSV")).getOutletPressure());
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, bindings, fault, "PSV"));
    assertTrue(((SafetyReliefValve) healthy.getUnit("PSV")).isLiftMotionAvailable());
    overrides.put("openingLaw", "MODULATING");
    assertThrows(IllegalArgumentException.class, () -> PidPressureReliefValidator.applyCanonicalConfiguration(f.project,
        model, bindings, f.process.copy(), "PSV", overrides, null));
  }

  /** Incoming PLC/function edges are as invalid as outgoing ones on a self-acting passive PSV. */
  @Test
  void rejectsIncomingFakeSignalAndDisabledIdempotency() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    model.getElements().get(0).connect(model.getElements().get(1).getId());
    assertThrows(IllegalArgumentException.class,
        () -> PidPressureReliefValidator.validateConfigured(f.project, model, bind(f, model), f.process, "PSV"));
    ((SafetyReliefValve) f.process.getUnit("PSV")).setPhysicalStepIdempotencyEnabled(false);
    assertThrows(IllegalArgumentException.class, () -> imported(f));
  }

  /** The passive native DEXPI component is reused without an invented process-control function or actuator. */
  @Test
  void nativeDexpiReusesPassiveSafetyValveAndActualTee() throws Exception {
    Fixture f = fixture();
    PidDesignModel empty = new PidDesignModel(f.project.getProjectId(), "ACTUAL-PASSIVE");
    PidDesignModel model = PidPressureReliefImporter.fromNativeValve(f.project, empty,
        PidSimulationBindings.from(f.project, empty), f.process, "PSV", "VESSEL", "TEE", "CV-BASIS");
    java.nio.file.Path file = java.nio.file.Files.createTempFile("passive-psv-", ".xml");
    try {
      neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata metadata = neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata
          .builder("2026-11-05T00:00:00Z", "Passive PSV test", "NeqSim", "test")
          .plantProperty(
              neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE,
              "NATIVE-PSV")
          .build();
      neqsim.process.engineering.dexpi.DexpiEngineeringExporter.exportDexpi201(f.project, file, metadata, false);
      neqsim.process.engineering.dexpi.Dexpi201PidMaterializer.Result result = neqsim.process.engineering.dexpi.Dexpi201PidMaterializer
          .materialize(f.project, model, bind(f, model), file);
      assertTrue(result.isComplete(), result.toJson());
      org.w3c.dom.Document xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .parse(file.toFile());
      org.w3c.dom.NodeList objects = xml.getElementsByTagName("Object");
      int safety = 0;
      int tee = 0;
      int functions = 0;
      for (int i = 0; i < objects.getLength(); i++) {
        org.w3c.dom.Element object = (org.w3c.dom.Element) objects.item(i);
        String type = object.getAttribute("type");
        if ("Plant/Piping.SafetyValveOrFitting".equals(type)) {
          safety++;
        }
        if ("Plant/Piping.PipeTee".equals(type)) {
          tee++;
        }
        if (type.contains("InstrumentationFunction") || type.contains("InstrumentedFunction")) {
          functions++;
        }
      }
      assertEquals(1, safety);
      assertEquals(1, tee);
      assertEquals(0, functions);
    } finally {
      java.nio.file.Files.deleteIfExists(file);
    }
  }

  /** Absolute settings are not mislabeled as the official gauge property without a declared atmosphere. */
  @Test
  void nativeGaugeMappingRequiresExplicitAtmosphericReferenceAndChangesReceipt() throws Exception {
    Fixture f = fixture();
    PidDesignModel empty = new PidDesignModel(f.project.getProjectId(), "ACTUAL-PASSIVE");
    PidDesignModel model = PidPressureReliefImporter.fromNativeValve(f.project, empty,
        PidSimulationBindings.from(f.project, empty), f.process, "PSV", "VESSEL", "TEE", "CV-BASIS");
    PidSimulationBindings bindings = bind(f, model);
    PidPressureReliefValidator.ConfiguredRelief original = PidPressureReliefValidator.validateConfigured(f.project,
        model, bindings, f.process, "PSV");
    java.nio.file.Path file = java.nio.file.Files.createTempFile("passive-psv-gauge-", ".xml");
    try {
      neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata metadata = neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata
          .builder("2026-11-05T00:00:00Z", "Passive PSV gauge test", "NeqSim", "test")
          .plantProperty(
              neqsim.process.processmodel.dexpi.Dexpi20PlantExportMetadata.PlantProperty.PROCESS_PLANT_IDENTIFICATION_CODE,
              "NATIVE-PSV")
          .build();
      neqsim.process.engineering.dexpi.DexpiEngineeringExporter.exportDexpi201(f.project, file, metadata, false);
      neqsim.process.engineering.dexpi.Dexpi201PidMaterializer.Result absolute = neqsim.process.engineering.dexpi.Dexpi201PidMaterializer
          .materialize(f.project, model, bindings, file);
      String xml = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
      assertFalse(xml.contains("property=\"SetPressureHigh\""));
      assertTrue(absolute.toJson().contains("HOLD_ATMOSPHERIC_REFERENCE_REQUIRED"));
      model.getElements().get(0).attribute("simulationAtmosphericReferenceBara", Double.valueOf(1.01325));
      assertThrows(IllegalStateException.class, () -> bindings.assertCurrent(f.project, model));
      PidSimulationBindings revised = bind(f, model);
      PidPressureReliefValidator.ConfiguredRelief after = PidPressureReliefValidator.validateConfigured(f.project,
          model, revised, f.process, "PSV");
      assertNotEquals(original.getFingerprint(), after.getFingerprint());
      assertThrows(IllegalStateException.class, () -> original.assertSameConfiguration(after));
      neqsim.process.engineering.dexpi.DexpiEngineeringExporter.exportDexpi201(f.project, file, metadata, false);
      neqsim.process.engineering.dexpi.Dexpi201PidMaterializer.Result mapped = neqsim.process.engineering.dexpi.Dexpi201PidMaterializer
          .materialize(f.project, model, revised, file);
      assertTrue(mapped.getAssessment().isSchemaAndProfileConformant(), mapped.toJson());
      org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .parse(file.toFile());
      org.w3c.dom.NodeList data = document.getElementsByTagName("Data");
      int count = 0;
      for (int i = 0; i < data.getLength(); i++) {
        org.w3c.dom.Element item = (org.w3c.dom.Element) data.item(i);
        assertNotEquals("SetPressureLow", item.getAttribute("property"));
        if ("SetPressureHigh".equals(item.getAttribute("property"))) {
          count++;
          assertEquals("Core/PhysicalQuantities.PressureGaugeUnit.Bar",
              ((org.w3c.dom.Element) item.getElementsByTagName("DataReference").item(0)).getAttribute("data"));
          assertEquals(41.0 - 1.01325, Double.parseDouble(item.getElementsByTagName("Double").item(0).getTextContent()),
              1.0e-12);
        }
      }
      assertEquals(1, count);
      model.getElements().get(0).attribute("simulationAtmosphericReferenceBara", Double.valueOf(Double.NaN));
      assertThrows(IllegalArgumentException.class,
          () -> PidPressureReliefValidator.validateConfigured(f.project, model, bind(f, model), f.process, "PSV"));
    } finally {
      java.nio.file.Files.deleteIfExists(file);
    }
  }

  /** Unqualified pilot or balanced hardware cannot acquire a conventional spring-relief drawing. */
  @Test
  void rejectsUnqualifiedPilotAndBalancedReliefTypes() {
    for (SafetyReliefValve.ValveType type : new SafetyReliefValve.ValveType[] {
        SafetyReliefValve.ValveType.PILOT_MODULATING, SafetyReliefValve.ValveType.BALANCED_BELLOWS}) {
      Fixture f = fixture();
      ((SafetyReliefValve) f.process.getUnit("PSV")).setValveType(type);
      assertThrows(IllegalArgumentException.class, () -> imported(f));
    }
  }

  /** Finite transient response cannot share a receipt with instantaneous steady dispatch. */
  @Test
  void responseExecutionModeChangesConfigurationFingerprint() {
    Fixture f = fixture();
    PidDesignModel model = imported(f);
    PidSimulationBindings bindings = bind(f, model);
    PidPressureReliefValidator.ConfiguredRelief steady = PidPressureReliefValidator.validateConfigured(f.project, model,
        bindings, f.process, "PSV");
    ProcessSystem finite = f.process.copy();
    ((SafetyReliefValve) finite.getUnit("PSV")).setCalculateSteadyState(false);
    PidPressureReliefValidator.ConfiguredRelief dynamic = PidPressureReliefValidator.validateConfigured(f.project,
        model, bindings, finite, "PSV");
    assertNotEquals(steady.getFingerprint(), dynamic.getFingerprint());
    assertThrows(IllegalStateException.class, () -> steady.assertSameConfiguration(dynamic));
    assertEquals("FINITE_NATIVE_TRANSIENT", dynamic.toMap().get("nativeResponseMode"));
  }

  /** Uncapped subcritical Cv extrapolation cannot qualify a high-ratio passive gas relief case. */
  @Test
  void rejectsDisabledNativeGasChoking() {
    Fixture f = fixture();
    ((SafetyReliefValve) f.process.getUnit("PSV")).setAllowChoked(false);
    assertThrows(IllegalArgumentException.class, () -> imported(f));
  }

  /**
   * @param f source fixture
   * @return atomically imported canonical graph
   */
  private static PidDesignModel imported(Fixture f) {
    return PidPressureReliefImporter.fromNativeValve(f.project, f.pid, f.bindings, f.process, "PSV", "VESSEL", "TEE",
        "CV-BASIS");
  }

  /**
   * @param f source fixture
   * @param pid current graph
   * @return fresh equipment binding
   */
  private static PidSimulationBindings bind(Fixture f, PidDesignModel pid) {
    return PidSimulationBindings.from(f.project, pid).bindEquipment("PSV", "PSV");
  }

  /**
   * @param model canonical graph
   * @return detached editable configuration
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> config(PidDesignModel model) {
    return new LinkedHashMap<String, Object>(
        (Map<String, Object>) model.getElements().get(1).getAttributes().get("simulationPressureReliefConfiguration"));
  }

  /** @return solved native protected vessel and real passive relief branch */
  private static Fixture fixture() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 40);
    fluid.addComponent("methane", .9);
    fluid.addComponent("n-heptane", .1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000, "kg/hr");
    Separator vessel = new Separator("VESSEL", feed);
    vessel.getGasOutStream().setName("GAS");
    Splitter tee = new Splitter("TEE", vessel.getGasOutStream(), 2);
    tee.setSplitFactors(new double[] {1, 0});
    tee.getSplitStream(0).setName("NORMAL-INLET");
    tee.getSplitStream(1).setName("PSV-INLET");
    ThrottlingValve normal = new ThrottlingValve("PCV", tee.getSplitStream(0));
    normal.setOutletPressure(35);
    SafetyReliefValve psv = new SafetyReliefValve("PSV", tee.getSplitStream(1));
    psv.configureConventionalSnap(41, .1, .07, 20);
    psv.setAllowChoked(true);
    psv.setPhysicalStepIdempotencyEnabled(true);
    psv.setOutletPressure(2);
    psv.getOutletStream().setName("DISPOSAL");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(vessel);
    process.add(tee);
    process.add(normal);
    process.add(psv);
    process.run();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Passive relief test", process, "2.0.1")
        .projectId("NATIVE-PSV").registerProposedInstruments(false).build().setRevision("A");
    project.addEvidenceRecord(new EngineeringEvidenceRecord("CV-BASIS", "SYNTHETIC_CV_ASSUMPTION", "A")
        .setTitle("Synthetic rated US Cv only; no certified relief capacity")
        .setSourceOrganization("NeqSim public test").linkEquipment("PSV"));
    PidDesignModel pid = new PidDesignModel(project.getProjectId(), "TEST");
    pid.add(new PidElement("EXISTING", "EXISTING", PidElementType.NOZZLE).equipment("VESSEL"));
    return new Fixture(process, project, pid);
  }

  /** Solved source fixture. */
  private static final class Fixture {
    final ProcessSystem process;
    final EngineeringProject project;
    final PidDesignModel pid;
    final PidSimulationBindings bindings;

    /**
     * @param p source process
     * @param e governed project
     * @param m canonical graph
     */
    Fixture(ProcessSystem p, EngineeringProject e, PidDesignModel m) {
      process = p;
      project = e;
      pid = m;
      bindings = PidSimulationBindings.from(e, m);
    }
  }
}
