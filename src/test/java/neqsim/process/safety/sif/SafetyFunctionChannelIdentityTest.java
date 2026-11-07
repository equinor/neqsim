package neqsim.process.safety.sif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests actual-source identity and held-sample semantics of native safety channels. */
class SafetyFunctionChannelIdentityTest {
  /** A single physical sample is reused after the process value changes. */
  @Test
  void channelUsesHeldMeasurementAndDerivesItsActualTag() {
    ProcessSystem process = process();
    PressureTransmitter source = (PressureTransmitter) process.getMeasurementDevices().get(0);
    source.sampleTransient(0.1, UUID.randomUUID());
    SafetyFunctionChannel channel = SafetyFunctionChannel.highTripFromMeasurement(source, "bara", 41.0).build();
    ((Stream) process.getUnit("FEED")).setPressure(60.0, "bara");
    assertEquals("PT-A", channel.getTag());
    assertSame(source, channel.getMeasurementSource());
    assertFalse(channel.sample(process, 0.1).isTripped());
    source.sampleTransient(0.1, UUID.randomUUID());
    assertTrue(channel.sample(process, 0.1).isTripped());
    assertEquals("NATIVE_HELD_MEASUREMENT", channel.configurationMap().get("inputIdentityStatus"));
  }

  /** An original source cannot silently feed a copied process. */
  @Test
  void rejectsSourceCapturedFromAnotherProcessCopy() {
    ProcessSystem process = process();
    PressureTransmitter source = (PressureTransmitter) process.getMeasurementDevices().get(0);
    source.sampleTransient(0.1, UUID.randomUUID());
    SafetyFunctionChannel channel = SafetyFunctionChannel.highTripFromMeasurement(source, "bara", 41.0).build();
    ProcessSystem copy = process.copy();
    assertThrows(IllegalStateException.class, () -> channel.sample(copy, 0.1));
  }

  /** Missing held sampling, missing samples and unsupported units all fail closed. */
  @Test
  void rejectsDisabledSamplingMissingSampleAndUnsupportedUnit() {
    ProcessSystem process = process();
    PressureTransmitter source = (PressureTransmitter) process.getMeasurementDevices().get(0);
    source.setTransientSamplingEnabled(false);
    assertThrows(IllegalArgumentException.class,
        () -> SafetyFunctionChannel.highTripFromMeasurement(source, "bara", 41.0));
    source.setTransientSamplingEnabled(true);
    SafetyFunctionChannel channel = SafetyFunctionChannel.highTripFromMeasurement(source, "bara", 41.0).build();
    assertThrows(IllegalStateException.class, () -> channel.sample(process, 0.1));
    source.sampleTransient(0.1, UUID.randomUUID());
    SafetyFunctionChannel invalid = SafetyFunctionChannel.highTripFromMeasurement(source, "bananas", 41.0).build();
    assertThrows(IllegalArgumentException.class, () -> invalid.sample(process, 0.1));
    source.setTransientSamplingEnabled(false);
    assertThrows(IllegalStateException.class, () -> channel.sample(process, 0.1));
  }

  /** Manual trip reads an actual local binary button without a hidden direct action. */
  @Test
  void manualButtonIsLocalDiscreteIdentity() {
    ProcessSystem process = process();
    PushButton button = new PushButton("HS-001");
    process.add(button);
    SafetyFunctionChannel channel = SafetyFunctionChannel.manualTripFromPushButton(button).build();
    assertFalse(channel.sample(process, 0.1).isTripped());
    button.push();
    assertTrue(channel.sample(process, 0.1).isTripped());
    assertEquals("NATIVE_DISCRETE_PUSH_BUTTON", channel.configurationMap().get("inputIdentityStatus"));
    assertThrows(IllegalStateException.class, () -> channel.sample(process.copy(), 0.1));
  }

  /** Linked hidden actions cannot bypass the declared safety-function final-element path. */
  @Test
  void manualButtonRejectsHiddenLinkedAutomaticActions() {
    ProcessSystem process = process();
    PushButton button = new PushButton("HS-001");
    process.add(button);
    SafetyFunctionChannel channel = SafetyFunctionChannel.manualTripFromPushButton(button).build();
    button.linkToLogic(new ESDLogic("Hidden direct trip"));
    assertThrows(IllegalArgumentException.class, () -> SafetyFunctionChannel.manualTripFromPushButton(button));
    assertThrows(IllegalStateException.class, () -> channel.sample(process, 0.1));
  }

  /** Legacy arbitrary extractors remain supported and explicitly unverified. */
  @Test
  void legacyExtractorDoesNotAcquireMeasurementIdentityCredit() {
    SafetyFunctionChannel channel = SafetyFunctionChannel.highTrip("DECLARED", "bara", 41.0, process -> 45.0).build();
    assertEquals("CUSTOM_EXTRACTOR_UNVERIFIED", channel.configurationMap().get("inputIdentityStatus"));
  }

  /** @return small local native source with enabled held sampling */
  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("FEED", fluid);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    PressureTransmitter source = new PressureTransmitter("PT-A", feed);
    source.setTransientSamplingEnabled(true);
    process.add(source);
    return process;
  }
}
