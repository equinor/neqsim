package neqsim.process.engineering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.InstrumentScheduleGenerator;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builds a deterministic Norwegian-offshore engineering proposal from a NeqSim process.
 *
 * <p>
 * Rules identify functions and assessments normally required by the cited standards. They deliberately do not assign
 * SIL, final trip limits, materials, or relief capacity. Those decisions require project-specific hazard analysis,
 * design data, and accountable engineering approval.
 * </p>
 */
public final class NorsokOffshoreEngineeringBuilder {
  private final String name;
  private final ProcessSystem processSystem;
  private final EngineeringDesignBasis designBasis;
  private final List<EngineeringRule> additionalRules = new ArrayList<EngineeringRule>();
  private boolean registerProposedInstruments;

  private NorsokOffshoreEngineeringBuilder(String name, ProcessSystem processSystem) {
    this.name = name;
    this.processSystem = processSystem;
    this.designBasis = createDefaultDesignBasis();
  }

  /** Creates a builder for a process and engineering-project name. */
  public static NorsokOffshoreEngineeringBuilder from(String name, ProcessSystem processSystem) {
    return new NorsokOffshoreEngineeringBuilder(name, processSystem);
  }

  /**
   * Builds one governed engineering project per area in a multi-area process model.
   *
   * <p>
   * DEXPI packages are area documents, so a {@link ProcessModel} is intentionally split into its constituent
   * {@link ProcessSystem} objects. Shared inter-area streams remain present in each area's process topology and can be
   * reconciled by their model tags in a document-management layer.
   * </p>
   *
   * @param name common engineering project name
   * @param processModel simulated multi-area model
   * @param registerProposedInstruments true to add generated instruments to areas without measurement devices
   * @return engineering projects in process-model insertion order
   */
  public static List<EngineeringProject> fromProcessModel(String name, ProcessModel processModel,
      boolean registerProposedInstruments) {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    List<EngineeringProject> projects = new ArrayList<EngineeringProject>();
    for (String areaName : processModel.getProcessSystemNames()) {
      ProcessSystem area = processModel.get(areaName);
      if (area != null) {
        projects
            .add(from(name + " - " + areaName, area).registerProposedInstruments(registerProposedInstruments).build());
      }
    }
    return projects;
  }

  /**
   * Controls whether proposed ISA-style measurement devices are added to the runnable process and consequently to the
   * DEXPI P&amp;ID. The generated alarm and trip thresholds remain proposals requiring review.
   *
   * @param register true to add generated devices
   * @return this builder
   */
  public NorsokOffshoreEngineeringBuilder registerProposedInstruments(boolean register) {
    this.registerProposedInstruments = register;
    return this;
  }

  /** @return mutable design basis populated with the default offshore profile */
  public EngineeringDesignBasis getDesignBasis() {
    return designBasis;
  }

  /**
   * Adds a project or company-specific engineering rule without modifying the public NORSOK profile.
   *
   * @param rule additional deterministic rule
   * @return this builder
   */
  public NorsokOffshoreEngineeringBuilder addRule(EngineeringRule rule) {
    if (rule == null) {
      throw new IllegalArgumentException("rule must not be null");
    }
    additionalRules.add(rule);
    return this;
  }

  /**
   * Builds the project and applies deterministic requirement rules.
   *
   * @return generated governed engineering project
   */
  public EngineeringProject build() {
    EngineeringProject project = new EngineeringProject(name, processSystem, designBasis);
    if (registerProposedInstruments && processSystem.getMeasurementDevices().isEmpty()) {
      InstrumentScheduleGenerator generator = new InstrumentScheduleGenerator(processSystem);
      generator.setRegisterOnProcess(true);
      generator.generate();
    }
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      addCommonRequirements(project, unit);
      if (unit instanceof Separator) {
        addSeparatorRequirements(project, (Separator) unit);
      } else if (unit instanceof Compressor) {
        addCompressorRequirements(project, (Compressor) unit);
      } else if (unit instanceof Cooler) {
        addCoolerRequirements(project, (Cooler) unit);
      } else if (unit instanceof Heater) {
        addHeaterRequirements(project, (Heater) unit);
      } else if (unit instanceof Pump) {
        addPumpRequirements(project, (Pump) unit);
      } else if (unit instanceof ThrottlingValve) {
        addValveRequirements(project, (ThrottlingValve) unit);
      }
      for (EngineeringRule rule : additionalRules) {
        if (rule.supports(unit)) {
          rule.apply(project, unit);
        }
      }
    }
    return project;
  }

  private static EngineeringDesignBasis createDefaultDesignBasis() {
    return new EngineeringDesignBasis().setJurisdiction("Norwegian continental shelf")
        .setFacilityType("Offshore hydrocarbon production facility").setProjectPhase("Concept / pre-FEED")
        .addDesignCase("normal operation").addDesignCase("maximum production")
        .addDesignCase("minimum/turndown operation").addDesignCase("start-up and shutdown")
        .addDesignCase("blocked outlet and utility failure").addDesignCase("fire and depressurization")
        .addStandard(new EngineeringStandard("DEXPI", "2.0", "Data Exchange in the Process Industry",
            "Plant/process information model and XML exchange"))
        .addStandard(new EngineeringStandard("ISO 10628-1/2", "2014", "Diagrams for the chemical industry",
            "Flowsheet and graphical-diagram conventions"))
        .addStandard(new EngineeringStandard("IEC 62424", "2016", "Process control engineering requests",
            "Representation of process-control functions and exchange data"))
        .addStandard(new EngineeringStandard("IEC 61511", "2016", "Safety instrumented systems",
            "SIS lifecycle, SRS, SIL determination and verification"))
        .addStandard(new EngineeringStandard("ISO 10418", "2019", "Offshore process safety systems",
            "Analysis and design of surface process safeguarding"))
        .addStandard(new EngineeringStandard("API 521", "7th ed. 2020", "Pressure-relieving and depressurizing systems",
            "Relief and depressurization assessments"))
        .addStandard(new EngineeringStandard("API 520 Part I", "10th ed.",
            "Sizing and selection of pressure-relieving devices", "Pressure safety valve orifice sizing"))
        .addStandard(new EngineeringStandard("ASME B31.3", "Project applicable edition", "Process piping",
            "Topside piping pressure design and screening"))
        .addStandard(new EngineeringStandard("API 617", "9th ed. 2022", "Axial and centrifugal compressors",
            "Compressor train and performance-map requirements"))
        .addStandard(new EngineeringStandard("API 670", "5th ed. 2014", "Machinery protection systems",
            "Vibration, axial position, speed and machinery protection"))
        .addStandard(new EngineeringStandard("NORSOK P-002", "2023+AC:2024", "Process system design",
            "Offshore process and utility-system requirements"))
        .addStandard(new EngineeringStandard("NORSOK M-001", "Project applicable edition", "Materials selection",
            "Materials and corrosion screening for hydrocarbon facilities"))
        .addStandard(new EngineeringStandard("NORSOK M-506", "2017", "CO2 corrosion rate calculation model",
            "Screening calculation for CO2 corrosion in production and process systems"))
        .addStandard(new EngineeringStandard("ISO 15156", "2020 series",
            "Materials for use in H2S-containing oil and gas production environments",
            "Sour-service material screening"))
        .addStandard(new EngineeringStandard("NORSOK I-001", "2025+AC:2026", "Field instrumentation",
            "Field instrument design and installation"))
        .addStandard(new EngineeringStandard("NORSOK I-002", "2021", "Industrial automation and control systems",
            "Control, alarm, shutdown and automation functions"))
        .addStandard(new EngineeringStandard("NORSOK S-001", "2020+AC:2021", "Technical safety",
            "Technical-safety systems and barriers"))
        .addStandard(new EngineeringStandard("NORSOK Z-003", "Rev. 2, 1998", "Technical information flow requirements",
            "P&amp;ID symbols, tagging and drawing information"))
        .addStandard(new EngineeringStandard("NORSOK Z-013", "2024", "Risk and emergency preparedness assessment",
            "Risk assessment and verification basis"));
  }

  private static void addCommonRequirements(EngineeringProject project, ProcessEquipmentInterface unit) {
    add(project, unit, "DESIGN-BASIS", EngineeringRequirement.Type.ENGINEERING_ASSESSMENT,
        "Confirm equipment design envelope",
        "Maximum/minimum pressure and temperature must be established from all credible "
            + "operating and accidental cases.",
        "NORSOK P-002:2023+AC:2024", "ISO 10418:2019");
  }

  private static void addSeparatorRequirements(EngineeringProject project, Separator separator) {
    add(project, separator, "PRESSURE-CONTROL", EngineeringRequirement.Type.CONTROL, "Separator pressure control",
        "Maintain pressure within the approved operating envelope.", "NORSOK I-002:2021", "IEC 62424:2016");
    add(project, separator, "LEVEL-CONTROL", EngineeringRequirement.Type.CONTROL, "Separator liquid-level control",
        "Prevent gas blow-by and liquid carry-over under normal and transient conditions.", "NORSOK P-002:2023+AC:2024",
        "NORSOK I-002:2021");
    add(project, separator, "LEVEL-HH-TRIP", EngineeringRequirement.Type.TRIP,
        "Independent high-high level protective function",
        "Detect and mitigate liquid carry-over to downstream gas equipment; final architecture "
            + "and SIL require HAZOP/LOPA.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, separator, "LEVEL-LL-TRIP", EngineeringRequirement.Type.TRIP,
        "Independent low-low level protective function",
        "Detect and mitigate gas blow-by to a lower-pressure liquid system; final architecture requires HAZOP/LOPA.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, separator, "PRESSURE-HH-TRIP", EngineeringRequirement.Type.TRIP,
        "Independent high-high pressure protective function",
        "Detect abnormal high pressure and isolate the credible pressure source; final set point, voting and actions "
            + "require HAZOP/LOPA and the safety requirements specification.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, separator, "PRESSURE-LL-TRIP", EngineeringRequirement.Type.TRIP,
        "Independent low-low pressure protective function",
        "Detect abnormal loss of pressure and mitigate gas blow-by, leakage or downstream equipment consequences.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, separator, "RELIEF", EngineeringRequirement.Type.RELIEF,
        "Pressure-relief and depressurization assessment",
        "Assess blocked outlet, control failure, gas blow-by, fire, thermal expansion and connected-system scenarios.",
        "API 521:2020", "NORSOK P-002:2023+AC:2024");
  }

  private static void addCompressorRequirements(EngineeringProject project, Compressor compressor) {
    add(project, compressor, "ANTI-SURGE", EngineeringRequirement.Type.CONTROL,
        "Antisurge measurement, controller and recycle final element",
        "Protect the compressor across normal operation, turndown, start-up, shutdown and trip "
            + "transients using the approved map.",
        "API 617:2022", "NORSOK I-002:2021");
    add(project, compressor, "SUCTION-P-LL", EngineeringRequirement.Type.TRIP,
        "Low-low suction pressure protective function",
        "Prevent operation outside the approved compressor and seal-system envelope; final set "
            + "point requires vendor data.",
        "API 617:2022", "IEC 61511:2016");
    add(project, compressor, "DISCHARGE-P-HH", EngineeringRequirement.Type.TRIP,
        "High-high discharge pressure protective function",
        "Prevent casing or downstream-system overpressure; coordinate with relief and settle-out pressure.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, compressor, "DISCHARGE-T-HH", EngineeringRequirement.Type.TRIP,
        "High-high discharge temperature protective function",
        "Protect compressor internals, seals and downstream material-temperature limits.", "API 617:2022",
        "IEC 61511:2016");
    add(project, compressor, "MACHINERY-PROTECTION", EngineeringRequirement.Type.MECHANICAL_PROTECTION,
        "Machinery protection system",
        "Provide vendor-approved vibration, axial-position, bearing-temperature, speed and "
            + "lube/seal-system protection.",
        "API 670:2014", "API 617:2022");
    add(project, compressor, "ISOLATION-BLOWDOWN", EngineeringRequirement.Type.ENGINEERING_ASSESSMENT,
        "Isolation, check valve, settle-out and blowdown assessment",
        "Define shutdown isolation, reverse-flow prevention, settle-out pressure and depressurization requirements.",
        "ISO 10418:2019", "API 521:2020");
  }

  private static void addHeaterRequirements(EngineeringProject project, Heater heater) {
    add(project, heater, "OUTLET-T-HH", EngineeringRequirement.Type.TRIP,
        "High-high outlet temperature protective function",
        "Protect downstream equipment and fluid from excessive temperature; coordinate with "
            + "low-flow and heat-source trips.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, heater, "LOW-FLOW", EngineeringRequirement.Type.TRIP, "Low process-flow heat-source trip",
        "Prevent overheating when process flow is lost.", "ISO 10418:2019", "NORSOK I-002:2021");
  }

  private static void addCoolerRequirements(EngineeringProject project, Cooler cooler) {
    add(project, cooler, "OUTLET-T-HIGH", EngineeringRequirement.Type.ALARM, "High outlet temperature alarm",
        "Detect loss or degradation of cooling before downstream limits are exceeded.", "NORSOK I-002:2021");
    add(project, cooler, "TUBE-RUPTURE", EngineeringRequirement.Type.ENGINEERING_ASSESSMENT,
        "Tube/plate rupture pressure assessment",
        "Assess overpressure and contamination caused by communication between high- and low-pressure sides.",
        "API 521:2020", "NORSOK P-002:2023+AC:2024");
  }

  private static void addPumpRequirements(EngineeringProject project, Pump pump) {
    add(project, pump, "LOW-SUCTION", EngineeringRequirement.Type.TRIP, "Low suction pressure protective function",
        "Protect against cavitation and loss of liquid supply; final set point requires NPSH and vendor limits.",
        "ISO 10418:2019", "IEC 61511:2016");
    add(project, pump, "DEADHEAD", EngineeringRequirement.Type.ENGINEERING_ASSESSMENT,
        "Deadhead and minimum-flow protection assessment",
        "Assess thermal and pressure consequences at zero or low flow and provide minimum-flow protection as required.",
        "ISO 10418:2019", "NORSOK P-002:2023+AC:2024");
  }

  private static void addValveRequirements(EngineeringProject project, ThrottlingValve valve) {
    add(project, valve, "FAILURE-ACTION", EngineeringRequirement.Type.ENGINEERING_ASSESSMENT,
        "Confirm valve failure action and tightness",
        "Fail-open, fail-closed or fail-last action must follow the approved shutdown and control philosophy.",
        "NORSOK I-001:2025+AC:2026", "NORSOK I-002:2021");
  }

  private static void add(EngineeringProject project, ProcessEquipmentInterface unit, String suffix,
      EngineeringRequirement.Type type, String title, String rationale, String... standards) {
    String tag = unit.getName();
    String id = sanitize(tag) + "-" + suffix;
    EngineeringRequirement requirement = new EngineeringRequirement(id, tag, type, title, rationale);
    for (String standard : standards) {
      requirement.addStandardReference(standard);
    }
    project.addRequirement(requirement);
  }

  private static String sanitize(String value) {
    return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "-");
  }
}
