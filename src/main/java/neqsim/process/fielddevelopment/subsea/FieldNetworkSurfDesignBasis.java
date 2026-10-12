package neqsim.process.fielddevelopment.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FieldEdge;
import neqsim.process.equipment.network.FieldNetworkTopology.FieldNode;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork;

/**
 * Immutable SURF design basis derived from one canonical {@link FieldNetworkTopology}.
 *
 * <p>
 * This class is an aggregation view, not a second network. It reads stable typed identities and the geometry of the
 * corresponding {@link LoopedPipeNetwork} edges so field-development design and cost calculations use the same wells,
 * manifolds, terminations, flowlines, pipelines and risers as the hydraulic model.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FieldNetworkSurfDesignBasis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private int productionWellCount;
  private int injectionWellCount;
  private int treeCount;
  private int manifoldCount;
  private int templateCount;
  private int pletCount;
  private int plemCount;
  private int jumperCount;
  private int productionRiserCount;
  private int injectionRiserCount;
  private int sharedRiserCount;
  private double jumperLengthM;
  private double jumperDiameterLengthProductM2;
  private double infieldFlowlineLengthM;
  private double infieldDiameterLengthProductM2;
  private double exportPipelineLengthM;
  private double exportDiameterLengthProductM2;
  private double riserLengthM;
  private double riserDiameterLengthProductM2;
  private double waterDepthM;
  private final List<LineSegment> lineSegments = new ArrayList<LineSegment>();

  /**
   * Immutable identity and geometry for one canonical physical SURF edge.
   */
  public static final class LineSegment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String id;
    private final String equipmentTag;
    private final EdgeRole role;
    private final Service service;
    private final double lengthM;
    private final double diameterM;

    /**
     * Create one route-segment design basis.
     *
     * @param id canonical edge identifier
     * @param equipmentTag stable engineering equipment tag
     * @param role semantic edge role
     * @param service production, injection or shared service
     * @param lengthM exact hydraulic length in metres
     * @param diameterM exact hydraulic diameter in metres
     */
    private LineSegment(String id, String equipmentTag, EdgeRole role, Service service, double lengthM,
        double diameterM) {
      this.id = id;
      this.equipmentTag = equipmentTag;
      this.role = role;
      this.service = service;
      this.lengthM = lengthM;
      this.diameterM = diameterM;
    }

    /** @return canonical edge identifier */
    public String getId() {
      return id;
    }

    /** @return stable engineering equipment tag */
    public String getEquipmentTag() {
      return equipmentTag;
    }

    /** @return semantic edge role */
    public EdgeRole getRole() {
      return role;
    }

    /** @return production, injection or shared service */
    public Service getService() {
      return service;
    }

    /** @return exact hydraulic length in metres */
    public double getLengthM() {
      return lengthM;
    }

    /** @return exact hydraulic diameter in metres */
    public double getDiameterM() {
      return diameterM;
    }

    /** @return exact hydraulic diameter in inches */
    public double getDiameterInches() {
      return metresToInches(diameterM);
    }
  }

  /** Create an empty aggregation target. */
  private FieldNetworkSurfDesignBasis() {
  }

  /**
   * Derive a design basis from the exact typed topology and wrapped hydraulic geometry.
   *
   * @param topology canonical field topology
   * @return immutable aggregated SURF design basis
   */
  public static FieldNetworkSurfDesignBasis fromTopology(FieldNetworkTopology topology) {
    if (topology == null) {
      throw new IllegalArgumentException("Field network topology cannot be null");
    }
    FieldNetworkSurfDesignBasis basis = new FieldNetworkSurfDesignBasis();
    for (FieldNode node : topology.getNodes()) {
      basis.countNode(node);
    }
    for (FieldEdge edge : topology.getEdges()) {
      basis.countEdge(topology, edge);
    }
    if (basis.getTotalWellCount() == 0) {
      throw new IllegalArgumentException("SURF design basis requires at least one production or injection well");
    }
    return basis;
  }

  /**
   * Count one typed node.
   *
   * @param node typed node identity
   */
  private void countNode(FieldNode node) {
    if (node.getRole() == NodeRole.PRODUCTION_WELL) {
      productionWellCount++;
    } else if (node.getRole() == NodeRole.INJECTION_WELL) {
      injectionWellCount++;
    } else if (node.getRole() == NodeRole.TREE) {
      treeCount++;
    } else if (node.getRole() == NodeRole.MANIFOLD) {
      manifoldCount++;
    } else if (node.getRole() == NodeRole.TEMPLATE) {
      templateCount++;
    } else if (node.getRole() == NodeRole.PLET) {
      pletCount++;
    } else if (node.getRole() == NodeRole.PLEM) {
      plemCount++;
    }
  }

  /**
   * Count one physical typed edge and aggregate its exact hydraulic geometry.
   *
   * @param topology canonical topology
   * @param edge typed edge identity
   */
  private void countEdge(FieldNetworkTopology topology, FieldEdge edge) {
    EdgeRole role = edge.getRole();
    if (!isSurfLine(role)) {
      return;
    }
    LoopedPipeNetwork.NetworkPipe pipe = topology.getHydraulicNetwork().getPipe(edge.getId());
    double lengthM = pipe.getLength();
    double diameterM = pipe.getDiameter();
    if (!(lengthM > 0.0) || !Double.isFinite(lengthM) || !(diameterM > 0.0) || !Double.isFinite(diameterM)) {
      throw new IllegalArgumentException(
          "SURF edge '" + edge.getId() + "' requires finite positive length and diameter");
    }
    lineSegments
        .add(new LineSegment(edge.getId(), edge.getEquipmentTag(), role, edge.getService(), lengthM, diameterM));
    if (role == EdgeRole.JUMPER) {
      jumperCount++;
      jumperLengthM += lengthM;
      jumperDiameterLengthProductM2 += diameterM * lengthM;
    } else if (role == EdgeRole.FLOWLINE || role == EdgeRole.TIE_IN) {
      infieldFlowlineLengthM += lengthM;
      infieldDiameterLengthProductM2 += diameterM * lengthM;
    } else if (role == EdgeRole.TRUNKLINE || role == EdgeRole.PIPELINE) {
      exportPipelineLengthM += lengthM;
      exportDiameterLengthProductM2 += diameterM * lengthM;
    } else if (role == EdgeRole.RISER) {
      riserLengthM += lengthM;
      riserDiameterLengthProductM2 += diameterM * lengthM;
      if (edge.getService() == Service.INJECTION) {
        injectionRiserCount++;
      } else if (edge.getService() == Service.SHARED) {
        sharedRiserCount++;
      } else {
        productionRiserCount++;
      }
      double fromElevation = topology.getHydraulicNetwork().getNode(pipe.getFromNode()).getElevation();
      double toElevation = topology.getHydraulicNetwork().getNode(pipe.getToNode()).getElevation();
      waterDepthM = Math.max(waterDepthM, Math.abs(toElevation - fromElevation));
    }
  }

  /**
   * Check whether an edge contributes physical line geometry to SURF design.
   *
   * @param role edge role
   * @return true for jumpers, infield lines, export lines and risers
   */
  private static boolean isSurfLine(EdgeRole role) {
    return role == EdgeRole.JUMPER || role == EdgeRole.FLOWLINE || role == EdgeRole.TIE_IN || role == EdgeRole.TRUNKLINE
        || role == EdgeRole.PIPELINE || role == EdgeRole.RISER;
  }

  /**
   * Convert metres to inches.
   *
   * @param metres length in metres
   * @return length in inches
   */
  private static double metresToInches(double metres) {
    return metres / 0.0254;
  }

  /** @return production-well count */
  public int getProductionWellCount() {
    return productionWellCount;
  }

  /** @return injection-well count */
  public int getInjectionWellCount() {
    return injectionWellCount;
  }

  /** @return total production and injection well count */
  public int getTotalWellCount() {
    return productionWellCount + injectionWellCount;
  }

  /** @return explicit tree-node count */
  public int getTreeCount() {
    return treeCount;
  }

  /** @return explicit manifold-node count */
  public int getManifoldCount() {
    return manifoldCount;
  }

  /** @return explicit template-node count */
  public int getTemplateCount() {
    return templateCount;
  }

  /** @return total manifold and template distribution-unit count */
  public int getDistributionUnitCount() {
    return manifoldCount + templateCount;
  }

  /** @return number of well slots per distribution unit, rounded up */
  public int getSlotsPerDistributionUnit() {
    int units = getDistributionUnitCount();
    return units == 0 ? 0 : (int) Math.ceil((double) getTotalWellCount() / units);
  }

  /** @return PLET count */
  public int getPletCount() {
    return pletCount;
  }

  /** @return PLEM count */
  public int getPlemCount() {
    return plemCount;
  }

  /** @return jumper count */
  public int getJumperCount() {
    return jumperCount;
  }

  /** @return total jumper length in m */
  public double getTotalJumperLengthM() {
    return jumperLengthM;
  }

  /** @return mean jumper length in m, or zero when no jumper exists */
  public double getMeanJumperLengthM() {
    return jumperCount == 0 ? 0.0 : jumperLengthM / jumperCount;
  }

  /** @return length-weighted jumper diameter in inches, or zero when no jumper exists */
  public double getJumperDiameterInches() {
    return jumperLengthM == 0.0 ? 0.0 : metresToInches(jumperDiameterLengthProductM2 / jumperLengthM);
  }

  /** @return total infield flowline and tie-in length in km */
  public double getInfieldFlowlineLengthKm() {
    return infieldFlowlineLengthM / 1000.0;
  }

  /** @return length-weighted infield diameter in inches, or zero when no infield line exists */
  public double getInfieldFlowlineDiameterInches() {
    return infieldFlowlineLengthM == 0.0 ? 0.0
        : metresToInches(infieldDiameterLengthProductM2 / infieldFlowlineLengthM);
  }

  /** @return total trunkline and pipeline length in km */
  public double getExportPipelineLengthKm() {
    return exportPipelineLengthM / 1000.0;
  }

  /** @return length-weighted export diameter in inches, or zero when no export line exists */
  public double getExportPipelineDiameterInches() {
    return exportPipelineLengthM == 0.0 ? 0.0 : metresToInches(exportDiameterLengthProductM2 / exportPipelineLengthM);
  }

  /** @return production-riser count */
  public int getProductionRiserCount() {
    return productionRiserCount;
  }

  /** @return injection-riser count */
  public int getInjectionRiserCount() {
    return injectionRiserCount;
  }

  /** @return shared or reversible riser count */
  public int getSharedRiserCount() {
    return sharedRiserCount;
  }

  /** @return total production, injection and shared riser count */
  public int getTotalRiserCount() {
    return productionRiserCount + injectionRiserCount + sharedRiserCount;
  }

  /** @return mean riser length in m, or zero when no riser exists */
  public double getMeanRiserLengthM() {
    int count = getTotalRiserCount();
    return count == 0 ? 0.0 : riserLengthM / count;
  }

  /** @return length-weighted riser diameter in inches, or zero when no riser exists */
  public double getRiserDiameterInches() {
    return riserLengthM == 0.0 ? 0.0 : metresToInches(riserDiameterLengthProductM2 / riserLengthM);
  }

  /** @return maximum vertical riser span in m, or zero when elevations do not define one */
  public double getWaterDepthM() {
    return waterDepthM;
  }

  /**
   * Get exact canonical physical line segments in deterministic topology insertion order.
   *
   * @return immutable route-segment list
   */
  public List<LineSegment> getLineSegments() {
    return Collections.unmodifiableList(lineSegments);
  }
}
