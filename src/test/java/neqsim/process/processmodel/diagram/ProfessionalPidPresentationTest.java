package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolConvention;
import neqsim.process.engineering.model.EngineeringDiagramConventionRegister.SymbolShape;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.pid.NorsokPidRuleCatalog;
import neqsim.process.engineering.pid.PidDesignBasis;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidDesignSynthesizer;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks the opt-in proposal presentation without weakening completed-scene geometry checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProfessionalPidPresentationTest {
  /**
   * Separates actual directed process routes from unimplemented proposal functions and retains every identity.
   */
  @Test
  void rendersSmallReviewPackageWithoutClippingOrCollisions() throws Exception {
    ProcessSystem process = process();
    EngineeringProject project = NorsokOffshoreEngineeringBuilder.from("Synthetic P&ID review", process)
        .projectId("PID-LAYOUT").registerProposedInstruments(false).build().setRevision("A");
    PidDesignModel pid = PidDesignSynthesizer.synthesize(project, new PidDesignBasis("COMPLETE", "20"),
        NorsokPidRuleCatalog.completeProposals());
    EngineeringDiagramDocumentSet documents = ProcessDiagramDocumentSetAdapter.fromProcessSystem(process,
        project.getProjectId(), project.getRevision(), "PID-LAYOUT-001", project.getName(), ContentProfile.PID);
    EngineeringDiagramPidRegisters registers = EngineeringDiagramPidRegisters.fromDocumentSet(documents, pid);
    NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE, conventions(),
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL, registers, true).render();
    assertTrue(result.isComplete());
    for (NativeEngineeringDiagramRenderer.Diagnostic diagnostic : result.getDiagnostics()) {
      assertEquals(NativeEngineeringDiagramRenderer.Severity.INFO, diagnostic.getSeverity(),
          diagnostic.getCode() + ": " + diagnostic.getSubjectId());
    }
    String svg = result.getSvgBySheetId().values().iterator().next();
    assertTrue(svg.contains("Proposed functional links (not process piping)"));
    assertFalse(svg.contains("pid-proposal:"));
    assertTrue(svg.contains("pid-signal:"));
    assertTrue(svg.contains("font-weight=\"bold\""));
    assertTrue(new String(result.getPdf(), StandardCharsets.ISO_8859_1).contains("/BaseFont /Helvetica-Bold"));
    NodeList groups = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("g");
    Set<String> sourceTags = new HashSet<String>();
    int sourceRoutes = 0;
    int proposedLinks = 0;
    for (int index = 0; index < groups.getLength(); index++) {
      Element group = (Element) groups.item(index);
      if (group.hasAttribute("data-proposed")) {
        if (group.hasAttribute("data-edge")) {
          proposedLinks++;
          assertEquals(1, group.getElementsByTagName("polyline").getLength());
          assertTrue(((Element) group.getElementsByTagName("polyline").item(0)).hasAttribute("stroke-dasharray"));
        }
        continue;
      }
      if (group.hasAttribute("data-tag")) {
        sourceTags.add(group.getAttribute("data-tag"));
        assertTrue(group.getElementsByTagName("polygon").getLength() > 0);
        assertEquals(2, group.getElementsByTagName("text").getLength());
      }
      if (group.hasAttribute("data-stream")) {
        sourceRoutes++;
        assertEquals(1, group.getElementsByTagName("polyline").getLength());
        assertEquals(1, group.getElementsByTagName("polygon").getLength());
        assertEquals(1, group.getElementsByTagName("text").getLength());
      }
    }
    assertEquals(3, sourceTags.size());
    assertTrue(sourceTags.contains("20-FEED-001"));
    assertTrue(sourceTags.contains("20-VG-001"));
    assertTrue(sourceTags.contains("20-PCV-001"));
    assertEquals(2, sourceRoutes);
    assertTrue(proposedLinks > 0);
    for (PidElement proposal : pid.getElements()) {
      assertTrue(svg.contains(proposal.getTag()), proposal.getTag());
    }
    NativeEngineeringDiagramRenderer.Result legacy = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE, conventions(),
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL, registers).render();
    NativeEngineeringDiagramRenderer.Result explicitLegacy = new NativeEngineeringDiagramRenderer(documents,
        NativeEngineeringDiagramRenderer.SheetFormat.A3_LANDSCAPE, conventions(),
        NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL, registers, false).render();
    assertEquals(legacy.getSvgBySheetId(), explicitLegacy.getSvgBySheetId());
    assertArrayEquals(legacy.getPdf(), explicitLegacy.getPdf());
    assertFalse(legacy.getSvgBySheetId().values().iterator().next().contains("data-tag="));
    assertFalse(new String(legacy.getPdf(), StandardCharsets.ISO_8859_1).contains("/BaseFont /Helvetica-Bold"));
  }

  /**
   * Supplies proposed source-backed symbol conventions, including the legitimate feed boundary.
   *
   * @return explicit source-equipment and stream-terminal conventions
   */
  private static EngineeringDiagramConventionRegister conventions() {
    return new EngineeringDiagramConventionRegister()
        .withConvention(new SymbolConvention(EngineeringNode.Kind.EQUIPMENT, SymbolShape.PROCESS_EQUIPMENT, "#111827",
            "#ffffff", "synthetic native symbols", EvidenceState.PROPOSED, "", "", "2026-11-03", "A"))
        .withConvention(new SymbolConvention(EngineeringNode.Kind.LINE, SymbolShape.LINE_TERMINAL, "#111827", "#ffffff",
            "synthetic feed terminal", EvidenceState.PROPOSED, "", "", "2026-11-03", "A"));
  }

  /**
   * Creates a small stream-connected source process without unimplemented safety devices.
   *
   * @return converged process
   */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-heptane", 0.15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Separator separator = new Separator("20-VG-001", feed);
    ThrottlingValve valve = new ThrottlingValve("20-PCV-001", separator.getGasOutStream());
    valve.setOutletPressure(30.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.run();
    return process;
  }
}
