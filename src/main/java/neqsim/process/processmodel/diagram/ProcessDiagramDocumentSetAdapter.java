package neqsim.process.processmodel.diagram;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.model.EngineeringDiagramDesignationRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Diagnostic;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.Severity;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Creates immutable controlled diagram-document proposals from runnable NeqSim process objects.
 *
 * <p>
 * The adapter reuses {@link ProcessDiagramGraphAdapter}; it does not change scheduling, existing Graphviz output, DEXPI
 * writers, or Proteus/P&amp;ID APIs.
 * </p>
 */
public final class ProcessDiagramDocumentSetAdapter {
  private ProcessDiagramDocumentSetAdapter() {
  }

  /**
   * Creates one controlled single-area drawing proposal.
   *
   * @param processSystem process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()));
  }

  /**
   * Creates one controlled single-area proposal with reviewed project designations.
   *
   * @param processSystem process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param designationRegister reviewed project designation evidence
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile,
      EngineeringDiagramDesignationRegister designationRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister);
  }

  /**
   * Creates one controlled single-area proposal with reviewed designations and persistent manual layout evidence.
   *
   * @param processSystem process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param designationRegister reviewed project designation evidence
   * @param layoutRegister controlled manual sheet and layout evidence
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile,
      EngineeringDiagramDesignationRegister designationRegister, EngineeringDiagramLayoutRegister layoutRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister, layoutRegister);
  }

  /**
   * Creates one controlled single-area drawing proposal with a governed operating-case snapshot.
   *
   * @param processSystem successfully run process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable operating-case identity
   * @return immutable document-set proposal with unit-explicit calculated values and provenance
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()));
  }

  /**
   * Creates one single-area operating-case proposal with reviewed project designations.
   *
   * @param processSystem successfully run process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable operating-case identity
   * @param designationRegister reviewed project designation evidence
   * @return immutable governed document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId,
      EngineeringDiagramDesignationRegister designationRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister);
  }

  /**
   * Creates one governed operating-case proposal with reviewed designations and persistent layout evidence.
   *
   * @param processSystem successfully run process system to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable operating-case identity
   * @param designationRegister reviewed project designation evidence
   * @param layoutRegister controlled manual sheet and layout evidence
   * @return immutable governed document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessSystem(ProcessSystem processSystem, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId,
      EngineeringDiagramDesignationRegister designationRegister, EngineeringDiagramLayoutRegister layoutRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessSystem(processSystem, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister, layoutRegister);
  }

  /**
   * Creates one controlled multi-area drawing proposal with reciprocal off-page references.
   *
   * @param processModel multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()));
  }

  /**
   * Creates one controlled multi-area proposal with reviewed project designations.
   *
   * @param processModel multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param designationRegister reviewed project designation evidence
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile,
      EngineeringDiagramDesignationRegister designationRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister);
  }

  /**
   * Creates one controlled multi-area proposal with reviewed designations and persistent manual layout evidence.
   *
   * @param processModel multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param designationRegister reviewed project designation evidence
   * @param layoutRegister controlled manual sheet and layout evidence
   * @return immutable document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile,
      EngineeringDiagramDesignationRegister designationRegister, EngineeringDiagramLayoutRegister layoutRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister, layoutRegister);
  }

  /**
   * Creates one controlled multi-area drawing proposal with a governed operating-case snapshot.
   *
   * @param processModel successfully run multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable plant-wide operating-case identity
   * @return immutable document-set proposal with unit-explicit calculated values and provenance
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()));
  }

  /**
   * Creates one multi-area operating-case proposal with reviewed project designations.
   *
   * @param processModel successfully run multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable plant-wide operating-case identity
   * @param designationRegister reviewed project designation evidence
   * @return immutable governed document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId,
      EngineeringDiagramDesignationRegister designationRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister);
  }

  /**
   * Creates one governed multi-area operating-case proposal with designations and persistent layout evidence.
   *
   * @param processModel successfully run multi-area process model to adapt
   * @param plantId persistent plant identity
   * @param revision controlled source revision
   * @param drawingNumber controlled drawing number
   * @param title drawing-set title
   * @param profile requested content profile
   * @param operatingCaseId stable plant-wide operating-case identity
   * @param designationRegister reviewed project designation evidence
   * @param layoutRegister controlled manual sheet and layout evidence
   * @return immutable governed document-set proposal
   */
  public static EngineeringDiagramDocumentSet fromProcessModel(ProcessModel processModel, String plantId,
      String revision, String drawingNumber, String title, ContentProfile profile, String operatingCaseId,
      EngineeringDiagramDesignationRegister designationRegister, EngineeringDiagramLayoutRegister layoutRegister) {
    ProcessDiagramGraphAdapter.Result topology = ProcessDiagramGraphAdapter.fromProcessModel(processModel, plantId,
        revision, operatingCaseId);
    return EngineeringDiagramDocumentSet.fromGraph(topology.getGraph(), drawingNumber, title, profile,
        convert(topology.getDiagnostics()), designationRegister, layoutRegister);
  }

  private static List<Diagnostic> convert(List<ProcessDiagramGraphAdapter.Diagnostic> sourceDiagnostics) {
    List<Diagnostic> result = new ArrayList<Diagnostic>();
    for (ProcessDiagramGraphAdapter.Diagnostic diagnostic : sourceDiagnostics) {
      result.add(new Diagnostic(convert(diagnostic.getSeverity()), diagnostic.getCode(), diagnostic.getMessage(),
          diagnostic.getSubject()));
    }
    return result;
  }

  private static Severity convert(ProcessDiagramGraphAdapter.Severity severity) {
    if (severity == ProcessDiagramGraphAdapter.Severity.ERROR) {
      return Severity.ERROR;
    }
    if (severity == ProcessDiagramGraphAdapter.Severity.WARNING) {
      return Severity.WARNING;
    }
    return Severity.INFO;
  }
}
