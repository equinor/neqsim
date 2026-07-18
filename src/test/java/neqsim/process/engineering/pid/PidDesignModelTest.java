package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.NorsokOffshoreEngineeringBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Contract tests for governed P&amp;ID proposal identity, tagging and provenance. */
public class PidDesignModelTest extends NeqSimTest {
  @Test
  void synthesizerCreatesDeterministicReviewGovernedElements() {
    EngineeringProject project = createProject();
    PidDesignBasis basis = new PidDesignBasis("NORSOK-OFFSHORE-CONCEPT", "20");
    PidRuleCatalog catalog = new PidRuleCatalog().add(new SeparatorPressureRule());

    PidDesignModel first = PidDesignSynthesizer.synthesize(project, basis, catalog);
    PidDesignModel second = PidDesignSynthesizer.synthesize(project, basis, catalog);

    assertEquals(1, first.getElements().size());
    PidElement element = first.getElements().get(0);
    assertEquals("20-PT-001", element.getTag());
    assertEquals(element.getTag(), second.getElements().get(0).getTag());
    assertEquals("20-VG-001", element.getEquipmentTag());
    assertEquals(PidProposalStatus.REVIEW_REQUIRED, element.getStatus());
    assertEquals("PID-SEPARATOR-PRESSURE", element.getRuleId());
    assertFalse(element.getRequirementIds().isEmpty());
    assertTrue(element.getStandardReferences().contains("ANSI/ISA-5.1:2024"));

    Map<String, Object> document = first.toMap();
    assertEquals(Boolean.FALSE, document.get("fitnessForConstruction"));
    assertEquals("REVIEW_REQUIRED", document.get("qualificationStatus"));
  }

  @Test
  void duplicateRuleIdsAndElementTagsFailClosed() {
    PidRuleCatalog catalog = new PidRuleCatalog().add(new SeparatorPressureRule());
    assertThrows(IllegalArgumentException.class, () -> catalog.add(new SeparatorPressureRule()));

    PidDesignModel model = new PidDesignModel("PROJECT", "PROFILE");
    model.add(new PidElement("one", "20-PT-001", PidElementType.MEASUREMENT));
    assertThrows(IllegalArgumentException.class,
        () -> model.add(new PidElement("two", "20-PT-001", PidElementType.INDICATOR)));
  }

  @Test
  void tagAllocatorReservesExplicitTagsAndReusesStableKeys() {
    PidTagAllocator allocator = new PidTagAllocator(new PidDesignBasis("PROFILE", "23"));
    allocator.reserve("23-PT-001");
    String allocated = allocator.allocate("PT", "VESSEL-A-PRESSURE");
    assertEquals("23-PT-002", allocated);
    assertEquals(allocated, allocator.allocate("PT", "VESSEL-A-PRESSURE"));
  }

  private static EngineeringProject createProject() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    Stream feed = new Stream("20-FEED-001", fluid);
    feed.setFlowRate(1.0, "MSm3/day");
    Separator separator = new Separator("20-VG-001", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();
    return NorsokOffshoreEngineeringBuilder.from("P&ID synthesis test", process).projectId("PID-TEST").build();
  }

  private static final class SeparatorPressureRule implements PidDesignRule {
    @Override
    public String getId() {
      return "PID-SEPARATOR-PRESSURE";
    }

    @Override
    public int getOrder() {
      return 100;
    }

    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
      return equipment instanceof Separator;
    }

    @Override
    public List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment) {
      List<PidElement> proposals = new ArrayList<PidElement>();
      String stableKey = equipment.getName() + "-PRESSURE";
      String tag = context.getTagAllocator().allocate("PT", stableKey);
      PidElement element = new PidElement("instrument:" + tag, tag, PidElementType.MEASUREMENT)
          .equipment(equipment.getName()).description("Separator pressure transmitter")
          .provenance(getId(), "Measure pressure for control and protection review")
          .standard("ANSI/ISA-5.1:2024");
      context.requirements(equipment.getName(), null).stream()
          .filter(requirement -> requirement.getId().contains("PRESSURE"))
          .forEach(requirement -> element.requirement(requirement.getId()));
      proposals.add(element);
      return proposals;
    }
  }
}
