package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/** Builds an explicit, JPype-friendly case matrix without introducing hidden process-design defaults. */
public final class VerticalSliceCaseMatrixFactory {
  private VerticalSliceCaseMatrixFactory() {
  }

  public static Builder builder(String feedTag, String compressorTag) {
    return new Builder(feedTag, compressorTag);
  }

  /** Explicit scalar boundary conditions for one steady engineering case. */
  private static final class Definition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String name;
    private final EngineeringDesignCase.Type type;
    private final double feedFlowKgPerHr;
    private final double feedPressureBara;
    private final double feedTemperatureC;
    private final double compressorOutletPressureBara;
    private final String evidenceReference;
    private final String approvalStatus;

    Definition(String id, String name, EngineeringDesignCase.Type type, double feedFlowKgPerHr, double feedPressureBara,
        double feedTemperatureC, double compressorOutletPressureBara, String evidenceReference, String approvalStatus) {
      this.id = text(id, "caseId");
      this.name = text(name, "caseName");
      if (type == null) {
        throw new IllegalArgumentException("case type is required");
      }
      this.type = type;
      this.feedFlowKgPerHr = nonNegative(feedFlowKgPerHr, "feedFlowKgPerHr");
      this.feedPressureBara = positive(feedPressureBara, "feedPressureBara");
      this.feedTemperatureC = finite(feedTemperatureC, "feedTemperatureC");
      this.compressorOutletPressureBara = positive(compressorOutletPressureBara, "compressorOutletPressureBara");
      this.evidenceReference = text(evidenceReference, "evidenceReference");
      this.approvalStatus = text(approvalStatus, "approvalStatus");
    }

    EngineeringDesignCase toCase(final String feedTag, final String compressorTag) {
      return new EngineeringDesignCase(id, name, type, new EngineeringDesignCase.Configurator() {
        private static final long serialVersionUID = 1000L;

        @Override
        public void configure(ProcessSystem process) {
          Object feed = process.getUnit(feedTag);
          Object compressor = process.getUnit(compressorTag);
          if (!(feed instanceof StreamInterface)) {
            throw new IllegalStateException(feedTag + " is missing or is not StreamInterface");
          }
          if (!(compressor instanceof Compressor)) {
            throw new IllegalStateException(compressorTag + " is missing or is not Compressor");
          }
          StreamInterface stream = (StreamInterface) feed;
          stream.setFlowRate(feedFlowKgPerHr, "kg/hr");
          stream.setPressure(feedPressureBara, "bara");
          stream.setTemperature(feedTemperatureC, "C");
          ((Compressor) compressor).setOutletPressure(compressorOutletPressureBara, "bara");
        }
      }).setDescription("Explicit vertical-slice boundary conditions; transient event actions are separate")
          .setCaseGroup("INLET_COMPRESSION_EXPORT").setApprovalStatus(approvalStatus)
          .addInput(new EngineeringDesignCase.Input("feedFlow", feedFlowKgPerHr, "kg/hr", evidenceReference))
          .addInput(new EngineeringDesignCase.Input("feedPressure", feedPressureBara, "bara", evidenceReference))
          .addInput(new EngineeringDesignCase.Input("feedTemperature", feedTemperatureC, "C", evidenceReference))
          .addInput(new EngineeringDesignCase.Input("compressorOutletPressure", compressorOutletPressureBara, "bara",
              evidenceReference))
          .addEvidenceReference(evidenceReference);
    }
  }

  /** Mutable definition builder; every case remains explicit and evidence-linked. */
  public static final class Builder {
    private final String feedTag;
    private final String compressorTag;
    private final List<Definition> definitions = new ArrayList<Definition>();
    private final Set<String> ids = new LinkedHashSet<String>();

    private Builder(String feedTag, String compressorTag) {
      this.feedTag = text(feedTag, "feedTag");
      this.compressorTag = text(compressorTag, "compressorTag");
    }

    public Builder addCase(String id, String name, EngineeringDesignCase.Type type, double feedFlowKgPerHr,
        double feedPressureBara, double feedTemperatureC, double compressorOutletPressureBara, String evidenceReference,
        String approvalStatus) {
      if (!ids.add(text(id, "caseId"))) {
        throw new IllegalArgumentException("Duplicate engineering case " + id);
      }
      definitions.add(new Definition(id, name, type, feedFlowKgPerHr, feedPressureBara, feedTemperatureC,
          compressorOutletPressureBara, evidenceReference, approvalStatus));
      return this;
    }

    /**
     * Adds the complete matrix to a project only when all case types required by the acceptance policy are present.
     */
    public EngineeringProject applyTo(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
      if (project == null || policy == null) {
        throw new IllegalArgumentException("project and policy are required");
      }
      Set<EngineeringDesignCase.Type> present = EnumSet.noneOf(EngineeringDesignCase.Type.class);
      for (Definition definition : definitions) {
        present.add(definition.type);
      }
      Set<EngineeringDesignCase.Type> missing = EnumSet.copyOf(policy.getRequiredCaseTypes());
      missing.removeAll(present);
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing vertical-slice case definitions " + missing);
      }
      for (Definition definition : definitions) {
        project.addDesignCase(definition.toCase(feedTag, compressorTag));
      }
      return project;
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }
}
