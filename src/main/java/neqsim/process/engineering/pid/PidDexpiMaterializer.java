package neqsim.process.engineering.pid;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Adds governed proposal objects and traceability attributes to a Proteus DEXPI document. */
public final class PidDexpiMaterializer {
  private PidDexpiMaterializer() {
  }

  public static void materialize(PidDesignModel model, Path dexpiFile) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Document document = factory.newDocumentBuilder().parse(dexpiFile.toFile());
      Element root = document.getDocumentElement();
      Set<String> existingTags = existingTags(document);
      appendPackageMetadata(document, root, model);
      Element proposalPipingSystem = createProposalPipingSystem(document);
      int index = 0;
      for (PidElement proposal : model.getElements()) {
        if (!existingTags.contains(proposal.getTag())) {
          appendProposal(document, root, proposalPipingSystem, proposal, index++);
        }
      }
      if (proposalPipingSystem.hasChildNodes()) {
        root.appendChild(proposalPipingSystem);
      }
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.transform(new DOMSource(document), new StreamResult(dexpiFile.toFile()));
    } catch (Exception exception) {
      throw new IOException("Could not materialize governed P&ID proposals in " + dexpiFile, exception);
    }
  }

  private static Set<String> existingTags(Document document) {
    Set<String> tags = new LinkedHashSet<String>();
    NodeList attributes = document.getElementsByTagName("GenericAttribute");
    for (int i = 0; i < attributes.getLength(); i++) {
      Element attribute = (Element) attributes.item(i);
      if (attribute.getAttribute("Name").contains("TagName")) {
        tags.add(attribute.getAttribute("Value"));
      }
    }
    return tags;
  }

  private static void appendPackageMetadata(Document document, Element root, PidDesignModel model) {
    Element attributes = document.createElement("GenericAttributes");
    attributes.setAttribute("Set", "NeqSimPidDesignModel");
    addAttribute(document, attributes, "SchemaVersion", "neqsim_pid_design_model.v1");
    addAttribute(document, attributes, "ProjectId", model.getProjectId());
    addAttribute(document, attributes, "ProfileId", model.getProfileId());
    addAttribute(document, attributes, "ProposalCount", String.valueOf(model.getElements().size()));
    addAttribute(document, attributes, "ModelSidecar", "pid-design-model.json");
    addAttribute(document, attributes, "CompletenessSidecar", "pid-completeness-report.json");
    addAttribute(document, attributes, "ApprovalStatus", "REVIEW_REQUIRED");
    root.appendChild(attributes);
  }

  private static Element createProposalPipingSystem(Document document) {
    Element system = document.createElement("PipingNetworkSystem");
    system.setAttribute("ID", "NeqSimPidProposal-PipingNetworkSystem");
    system.setAttribute("ComponentClass", "PipingNetworkSystem");
    system.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/PipingNetworkSystem");
    return system;
  }

  private static void appendProposal(Document document, Element root, Element pipingSystem, PidElement proposal,
      int index) {
    String componentClass = componentClass(proposal.getType());
    if (componentClass != null) {
      appendPipingProposal(document, pipingSystem, proposal, index, componentClass);
      return;
    }
    Element function = document.createElement("ProcessInstrumentationFunction");
    function.setAttribute("ID", "NeqSimPidProposal-" + safe(proposal.getId()));
    function.setAttribute("ComponentClass", "ProcessInstrumentationFunction");
    function.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/ProcessInstrumentationFunction");
    appendPosition(document, function, index);
    Element attributes = document.createElement("GenericAttributes");
    attributes.setAttribute("Set", "NeqSimPidProposal");
    addAttribute(document, attributes, "TagNameAssignmentClass", proposal.getTag());
    addAttribute(document, attributes, "PidElementType", proposal.getType().name());
    addAttribute(document, attributes, "EquipmentTag", proposal.getEquipmentTag());
    addAttribute(document, attributes, "RuleId", proposal.getRuleId());
    addAttribute(document, attributes, "ApprovalStatus", proposal.getStatus().name());
    function.appendChild(attributes);
    root.appendChild(function);
  }

  private static void appendPipingProposal(Document document, Element pipingSystem, PidElement proposal, int index,
      String componentClass) {
    Element segment = document.createElement("PipingNetworkSegment");
    segment.setAttribute("ID", "NeqSimPidProposal-Segment-" + safe(proposal.getId()));
    segment.setAttribute("ComponentClass", "PipingNetworkSegment");
    segment.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/PipingNetworkSegment");
    Element component = document.createElement("PipingComponent");
    component.setAttribute("ID", "NeqSimPidProposal-" + safe(proposal.getId()));
    component.setAttribute("ComponentClass", componentClass);
    component.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/" + componentClass);
    if ("BallValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "BALL_VALVE_SHAPE");
    } else if ("GlobeValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "GLOBE_VALVE_SHAPE");
    } else if ("SwingCheckValve".equals(componentClass)) {
      component.setAttribute("ComponentName", "CHECK_VALVE_SHAPE");
    }
    appendPosition(document, component, index);
    Element attributes = document.createElement("GenericAttributes");
    attributes.setAttribute("Set", "NeqSimPidProposal");
    addAttribute(document, attributes, "TagNameAssignmentClass", proposal.getTag());
    addAttribute(document, attributes, "PidElementType", proposal.getType().name());
    addAttribute(document, attributes, "EquipmentTag", proposal.getEquipmentTag());
    addAttribute(document, attributes, "RuleId", proposal.getRuleId());
    addAttribute(document, attributes, "ApprovalStatus", proposal.getStatus().name());
    component.appendChild(attributes);
    segment.appendChild(component);
    pipingSystem.appendChild(segment);
  }

  private static String componentClass(PidElementType type) {
    switch (type) {
    case CONTROL_VALVE:
      return "GlobeValve";
    case CHECK_VALVE:
      return "SwingCheckValve";
    case SAFETY_RELIEF_VALVE:
      return "SpringLoadedGlobeSafetyValve";
    case ISOLATION_VALVE:
    case SHUTDOWN_VALVE:
    case BLOWDOWN_VALVE:
    case DRAIN:
    case VENT:
      return "BallValve";
    default:
      return null;
    }
  }

  private static void appendPosition(Document document, Element parent, int index) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(20.0 + (index % 8) * 24.0));
    location.setAttribute("Y", String.valueOf(18.0 + (index / 8) * 18.0));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);
    parent.appendChild(position);
  }

  private static void addAttribute(Document document, Element parent, String name, String value) {
    Element attribute = document.createElement("GenericAttribute");
    attribute.setAttribute("Name", name);
    attribute.setAttribute("Value", value == null ? "" : value);
    attribute.setAttribute("Format", "string");
    parent.appendChild(attribute);
  }

  private static String safe(String value) {
    return value.replaceAll("[^A-Za-z0-9_.-]", "-");
  }
}
