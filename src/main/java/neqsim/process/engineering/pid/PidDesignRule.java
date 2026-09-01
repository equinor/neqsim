package neqsim.process.engineering.pid;

import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;

/** Deterministic rule that proposes traceable P&amp;ID elements for supported equipment. */
public interface PidDesignRule {
  String getId();

  int getOrder();

  boolean supports(ProcessEquipmentInterface equipment);

  List<PidElement> propose(PidDesignContext context, ProcessEquipmentInterface equipment);
}
