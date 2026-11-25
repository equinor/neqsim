---
title: Export a well as OLGA GENKEY text
description: Build an auditable OLGA well FLOWPATH fragment from NeqSim inflow and trajectory data, including a tested synthetic-screening example.
---

`OlgaWellGenkeyExporter` converts a liquid inflow-performance relationship and a
well trajectory into an OLGA GENKEY fragment. It emits case-level `MATERIAL`,
`WALL`, and `TABULAR` declarations followed by a
`NETWORKCOMPONENT TYPE=FLOWPATH` block containing `GEOMETRY`, `PIPE`,
`HEATTRANSFER`, and `RESERVOIRCONTACT` keywords.

The class is a format generator, not an OLGA simulator. A licensed OLGA installation
is still required to embed the fragment in a complete case, run OLGA's rule check,
and qualify the model for its intended decision. An independent design review must
confirm the fluid, geometry, thermal basis, material properties, discretisation, and
well-deliverability inputs.

## Maintained source and executable contract

The example is maintained against the current
[`OlgaWellGenkeyExporter`](../../src/main/java/neqsim/process/fielddevelopment/olga/OlgaWellGenkeyExporter.java),
[`SyntheticWellTrajectory`](../../src/main/java/neqsim/process/fielddevelopment/olga/SyntheticWellTrajectory.java), and
[`InflowPerformance`](../../src/main/java/neqsim/process/equipment/reservoir/InflowPerformance.java)
sources. The
[guide regression](../../src/test/java/neqsim/documentation/OlgaWellGenkeyExporterGuideDocumentationTest.java)
extracts the complete Java fence, compiles it with Java 8 syntax, enables assertions,
and executes its `main` method.

Run the focused executable documentation gate from the repository root:

```bash
./mvnw -Dtest=OlgaWellGenkeyExporterGuideDocumentationTest test
```

## Inputs that remain the caller's responsibility

| Input | Required engineering basis |
| --- | --- |
| Inflow performance | A calibrated or explicitly screening `InflowPerformance` with pressure and liquid-rate units checked |
| Trajectory | Real planned/as-drilled survey data whenever available; otherwise an explicitly disclosed synthetic screening trajectory |
| Pipe | Per-segment inner diameter and reviewed roughness |
| Wall | Thickness, material label, conductivity, heat capacity, and density |
| Thermal boundary | Overall heat-transfer coefficient and ambient temperature |
| Reservoir | Bottom-hole/reservoir temperature |
| PVT | A separately generated OLGA fluid table and its exact label, if the optional `FLUID=` linkage is used |

The exporter deliberately supplies no engineering defaults for those values. It also
does not invent a choke or Cv table, segment grading, gas-oil ratio, or water-gas
ratio. Each consecutive trajectory-node pair becomes one `PIPE` with
`NSEGMENT=1`; provide additional reviewed nodes when finer resolution is needed.

## Coordinate and array contract

`setTrajectory(xMetre, yMetre, diameterMetre)` accepts one x/y coordinate pair per
node and one diameter per segment. Therefore
`diameterMetre.length == xMetre.length - 1`. The OLGA elevation convention used by
the exporter is y negative with depth.

`SyntheticWellTrajectory` returns measured depth (MD) and true vertical depth
(TVD), not OLGA x/y coordinates. The example below derives horizontal displacement
for each station from
`sqrt(deltaMD^2 - deltaTVD^2)` and maps TVD to negative y. Use this conversion only
for a planar screening profile. A real survey can contain azimuth changes and must be
projected with its reviewed coordinate reference system instead.

## Complete Java 8 example

The numbers below are invented, round tutorial fixtures so the program remains
executable. They are not field data or recommended defaults. Replace every fixture
with the approved basis for the well, retain its provenance, and replace the
synthetic trajectory with a real survey as soon as one is available. Run the program
with Java assertions enabled (for example, `java -ea OlgaWellGenkeyExportExample`).

```java
import java.util.Arrays;
import neqsim.process.equipment.reservoir.InflowPerformance;
import neqsim.process.fielddevelopment.olga.OlgaWellGenkeyExporter;
import neqsim.process.fielddevelopment.olga.SyntheticWellTrajectory;

public final class OlgaWellGenkeyExportExample {
  private static final double TUBING_INNER_DIAMETER_METRE = 0.124;

  private OlgaWellGenkeyExportExample() {}

  public static void main(String[] args) {
    SyntheticWellTrajectory.Statistics statistics =
        new SyntheticWellTrajectory.Statistics(
            new SyntheticWellTrajectory.DepthTier[] {
              new SyntheticWellTrajectory.DepthTier(
                  "shallow", 10, 2.0, 200.0, 1000.0),
              new SyntheticWellTrajectory.DepthTier(
                  "medium", 10, 1.0, 400.0, 2000.0),
              new SyntheticWellTrajectory.DepthTier(
                  "deep", 10, 0.5, 600.0, 3000.0)
            },
            80.0,
            "Tutorial fixture only; invented values, not a real well population.");

    SyntheticWellTrajectory.Result trajectory =
        SyntheticWellTrajectory.generateSyntheticTrajectory(
            2500.0, 300.0, null, 8, statistics);

    double[] xMetre = horizontalCoordinates(trajectory);
    double[] yMetre = negativeTvdCoordinates(trajectory);
    double[] diameterMetre = new double[xMetre.length - 1];
    Arrays.fill(diameterMetre, TUBING_INNER_DIAMETER_METRE);

    InflowPerformance ipr = InflowPerformance.composite(200.0, 70.68, 66.94);
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    exporter.setTrajectory(xMetre, yMetre, diameterMetre);
    exporter.setInflowPerformance(ipr);
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(
        0.012, "m", "CARBON_STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");

    String genkeyText = exporter.toGenkeyText();
    String disclosure =
        SyntheticWellTrajectory.syntheticTrajectoryWarning(trajectory);

    assert "synthetic_template".equals(trajectory.provenance);
    assert disclosure.startsWith(
        SyntheticWellTrajectory.SYNTHETIC_TRAJECTORY_WARNING_PREFIX);
    assert diameterMetre.length == xMetre.length - 1;
    assert genkeyText.contains("MATERIAL");
    assert genkeyText.contains("WALL");
    assert genkeyText.contains("TABULAR");
    assert genkeyText.contains("NETWORKCOMPONENT TYPE=FLOWPATH");
    assert genkeyText.contains("GEOMETRY");
    assert genkeyText.contains("PIPE");
    assert genkeyText.contains("HEATTRANSFER");
    assert genkeyText.contains("RESERVOIRCONTACT");
  }

  private static double[] horizontalCoordinates(
      SyntheticWellTrajectory.Result trajectory) {
    double[] xMetre = new double[trajectory.md.length];
    for (int i = 1; i < xMetre.length; i++) {
      double deltaMd = trajectory.md[i] - trajectory.md[i - 1];
      double deltaTvd = trajectory.tvd[i] - trajectory.tvd[i - 1];
      double horizontalSquared =
          Math.max(0.0, deltaMd * deltaMd - deltaTvd * deltaTvd);
      xMetre[i] = xMetre[i - 1] + Math.sqrt(horizontalSquared);
    }
    return xMetre;
  }

  private static double[] negativeTvdCoordinates(
      SyntheticWellTrajectory.Result trajectory) {
    double[] yMetre = new double[trajectory.tvd.length];
    for (int i = 0; i < yMetre.length; i++) {
      yMetre[i] = -trajectory.tvd[i];
    }
    return yMetre;
  }
}
```

The generated text is an embeddable fragment. It is not a complete OLGA case and the
example does not write a file. If a reviewed file is needed, use
`writeTo(Path)` with an application-controlled destination.

## Synthetic trajectory governance

`SyntheticWellTrajectory` contains no statistics dataset. The caller must construct
`Statistics` from data it is permitted to use and supply a specific
`basisDescription`. Preserve
`SyntheticWellTrajectory.syntheticTrajectoryWarning(result)` alongside the model
and inspect `result.warnings`; the canonical warning is non-empty even when the
generation itself produces no exceptional warning.

A synthetic profile is screening evidence only. It is not a planned or as-drilled
survey, does not represent doglegs, S-shaped paths, multi-target geometry, or azimuth
changes, and must never be presented as the real well. Replace it with approved
Prosper, SMDA, drilling-engineering, or survey data before design- or safety-relevant
OLGA work.

## Optional PVT linkage

Calling `setPvtFluidLabel("MY_FLUID")` only adds a `FLUID=` reference to the
fragment. It does not generate PVT data. The label must exactly match a fluid table
already produced and qualified for the complete OLGA case. See
[OLGA PVT table generation](../integration/olga_pvt_table_generation.md) for the
separate PVT workflow.

## Qualification checklist

1. Replace all tutorial fixtures with traceable, unit-checked project inputs.
2. Prefer a real trajectory and document the coordinate projection.
3. Confirm that every segment diameter maps to the intended node pair.
4. Embed the fragment in the complete case with its qualified PVT fluid.
5. Run the licensed OLGA rule check and resolve every diagnostic.
6. Compare the resulting pressure, temperature, rate, and transient behaviour with
   independent evidence suitable for the decision.
7. Retain the input basis, generated fragment, rule-check output, and review record.
