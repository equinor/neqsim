package neqsim.process.examples;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.CoordinateUnit;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.PinnedPosition;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetAssignment;
import neqsim.process.engineering.model.EngineeringDiagramLayoutRegister.SheetDefinition;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.EngineeringDiagramDualProfileDelivery;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer;

/**
 * Reproducible full-model engineering-diagram reference for the
 * {@code comparesimulations2.ipynb} separation and compression case.
 *
 * <p>The runnable NeqSim model is authoritative. The historical notebook illustration is context
 * only, so the illustrated {@code 24-VB-01} is deliberately not added when it is absent from the
 * canonical process. The generated P&amp;ID remains a teaching proposal requiring project,
 * discipline, and safety review.</p>
 */
public final class Comparesimulations2EngineeringDiagramReference {
  private static final String REVISION = "A";
  private static final String OPERATING_CASE = "NORMAL-DEFAULT-2026-09-07";
  private static final String SOURCE =
      "EvenSol/NeqSim-Colab:notebooks/process/comparesimulations2.ipynb"
          + "@68f13ad17dce03ee343e2f711437d57cdcb58f19#b46addc3";
  private static final String RECORDED_AT = "2026-09-07T00:00:00Z";
  private static final String RECORDED_BY = "NeqSim reference workflow";

  private Comparesimulations2EngineeringDiagramReference() {}

  /**
   * Creates and executes the notebook-equivalent default operating case.
   *
   * @return executed canonical process
   */
  public static ProcessSystem createExecutedProcess() {
    OilGasProcessSimulationOptimization simulation =
        new OilGasProcessSimulationOptimization();
    ProcessSystem process = simulation.createProcess();
    OilGasProcessSimulationOptimization.ProcessOutputResults results =
        simulation.runSimulation();
    if (Math.abs(results.getMassBalance()) > 0.01) {
      throw new IllegalStateException(
          "Default operating case does not close mass balance: "
              + results.getMassBalance() + " %");
    }
    if (process.getUnitOperations().stream()
        .anyMatch(unit -> "24-VB-01".equals(unit.getName()))) {
      throw new IllegalStateException(
          "24-VB-01 is not part of the canonical runnable model");
    }
    return process;
  }

  /**
   * Delivers fresh PFD and review-required P&amp;ID proposal bundles.
   *
   * @param process successfully executed canonical process
   * @param directory new destination directory
   * @return coordinated delivery report
   * @throws IOException when delivery validation or publication fails
   */
  public static EngineeringDiagramDualProfileDelivery.Report deliver(
      ProcessSystem process, Path directory) throws IOException {
    return EngineeringDiagramDualProfileDelivery.deliver(
        process, directory, request());
  }

  /**
   * Creates the controlled delivery request and proposed manual layout.
   *
   * @return immutable coordinated request
   */
  public static EngineeringDiagramDualProfileDelivery.Request request() {
    List<EngineeringDiagramDualProfileDelivery.BalanceBoundary> boundaries =
        new ArrayList<EngineeringDiagramDualProfileDelivery.BalanceBoundary>();
    boundaries.add(boundary("well stream", Direction.INLET));
    boundaries.add(boundary("fuel gas", Direction.OUTLET));
    boundaries.add(boundary("export gas", Direction.OUTLET));
    boundaries.add(boundary("export oil", Direction.OUTLET));

    return EngineeringDiagramDualProfileDelivery.Request
        .builder(
            "ANDREASEN-SEPARATION-COMPRESSION",
            REVISION,
            "PFD-ANDREASEN-001",
            "PID-ANDREASEN-001",
            "Separation and compression teaching case")
        .operatingCaseId(OPERATING_CASE)
        .balanceBoundaries(boundaries)
        .sheetFormat(NativeEngineeringDiagramRenderer.SheetFormat.A1_LANDSCAPE)
        .routingMode(
            NativeEngineeringDiagramRenderer.RoutingMode.FIXED_PORT_ORTHOGONAL)
        .layoutRegister(layoutRegister())
        .build();
  }

  private static EngineeringDiagramDualProfileDelivery.BalanceBoundary boundary(
      String streamName, Direction direction) {
    return new EngineeringDiagramDualProfileDelivery.BalanceBoundary(
        "BAL-ANDREASEN-PLANT",
        streamName,
        direction,
        SOURCE + ":teaching-boundary-assumption",
        EvidenceState.PROPOSED);
  }

  private static EngineeringDiagramLayoutRegister layoutRegister() {
    EngineeringDiagramLayoutRegister register =
        new EngineeringDiagramLayoutRegister()
            .withSheet(sheet("separation", "1", "Three-stage separation and oil export"))
            .withSheet(sheet("recompression", "2", "Flash-gas recompression and dew point"))
            .withSheet(sheet("export", "3", "Fuel split and gas export compression"));

    String[] separation = {
      "20-HA-01", "20-VA-01", "VLV-100", "MIX-101", "20-HA-02", "20-VA-02",
      "VLV-102", "MIX-102", "20-HA-03", "20-VA-03", "21-HA-01", "21-PA-01"
    };
    String[] recompression = {
      "23-HA-03", "23-VG-03", "23-PA-01", "LP oil recycle", "23-KA-03",
      "MIX-103", "23-HA-02", "23-VG-02", "23-KA-02", "MIX-100", "23-HA-01",
      "23-VG-01", "dew point recycle 1"
    };
    String[] export = {
      "23-KA-01", "24-HA-01", "24-VG-01", "dew point recycle 2", "splitter",
      "25-HA-01", "25-HA-02", "25-VG-01", "27-KA-01", "27-HA-01"
    };
    register = place(register, "separation", separation);
    register = place(register, "recompression", recompression);
    return place(register, "export", export);
  }

  private static SheetDefinition sheet(String key, String number, String title) {
    return new SheetDefinition(
        key,
        number,
        title,
        SOURCE + ":proposed-layout",
        EngineeringDiagramLayoutRegister.EvidenceState.PROPOSED,
        RECORDED_BY,
        RECORDED_AT,
        REVISION);
  }

  private static EngineeringDiagramLayoutRegister place(
      EngineeringDiagramLayoutRegister register, String sheet, String[] names) {
    EngineeringDiagramLayoutRegister result = register;
    for (int index = 0; index < names.length; index++) {
      String objectId = "equipment:" + names[index];
      double x = 90.0 + (index % 5) * 145.0;
      double y = 105.0 + (index / 5) * 150.0;
      result =
          result
              .withAssignment(
                  new SheetAssignment(
                      objectId,
                      sheet,
                      SOURCE + ":proposed-layout",
                      EngineeringDiagramLayoutRegister.EvidenceState.PROPOSED,
                      RECORDED_BY,
                      RECORDED_AT,
                      REVISION))
              .withPinnedPosition(
                  new PinnedPosition(
                      objectId,
                      sheet,
                      x,
                      y,
                      CoordinateUnit.MILLIMETRE,
                      SOURCE + ":proposed-layout",
                      EngineeringDiagramLayoutRegister.EvidenceState.PROPOSED,
                      RECORDED_BY,
                      RECORDED_AT,
                      REVISION));
    }
    return result;
  }

  /**
   * Command-line generation entry point.
   *
   * @param args optional destination directory
   * @throws IOException when delivery fails
   */
  public static void main(String[] args) throws IOException {
    Path destination =
        args.length == 0
            ? Paths.get("build", "comparesimulations2-engineering-diagrams")
            : Paths.get(args[0]);
    EngineeringDiagramDualProfileDelivery.Report report =
        deliver(createExecutedProcess(), destination);
    System.out.println(report.toJson());
  }
}
