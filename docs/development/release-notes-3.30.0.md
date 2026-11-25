---
title: "NeqSim 3.30.0 release and migration notes"
description: "Changes since 3.29.0, in preparation. Removal of the deprecated Aspen IP.21 online-signal integration; changed TwoFluidPipe annular friction and feed split."
---

# NeqSim 3.30.0

**Status: in preparation.** This page collects breaking changes and migration
notes for the next minor release while it is being assembled on master. The POM
version is bumped as part of the release preparation, not by the individual
changes listed here.

## Breaking changes

### Legacy Aspen IP.21 online-signal integration removed

`neqsim.util.database.AspenIP21Database`,
`neqsim.process.measurementdevice.online.OnlineSignal` and the legacy signal
methods on `MeasurementDeviceInterface` / `MeasurementDeviceBaseClass`
(`getOnlineSignal()`, `setOnlineSignal(OnlineSignal)`, `isOnlineSignal()`,
`setIsOnlineSignal(boolean, String, String)`, `getOnlineValue()`) are removed.
They were deprecated in 3.25.0 (first published in 3.26.0, issue
[#4186](https://github.com/equinor/neqsim/issues/4186)) and relied on the
`sun.jdbc.odbc` bridge driver, which no JDK since Java 8 ships, with a
placeholder connection string.

Migration:

- Acquire historian samples with an application-managed client and pass the
  checked value and unit to
  `MeasurementDeviceBaseClass.setOnlineMeasurementValue(double, String)`; read it
  with `getOnlineMeasurementValue()`.
- Custom `MeasurementDeviceInterface` implementations must drop their
  `getOnlineSignal()` and `isOnlineSignal()` overrides.
- `ProcessSystem.reportMeasuredValues()` no longer prints an "Online value" line.
- `ModelPredictiveController` and all measurement devices no longer report an
  "external I/O" transient-state coverage blocker; that mode no longer exists.
- P&ID engineering packages (`PidNativeControlSystem`) no longer emit the
  `onlineSignal` key in native instrument configurations. Packages exported
  earlier with `onlineSignal: false` still validate and apply; `onlineSignal: true`
  is rejected.
- `serialVersionUID` values are unchanged. Serialized measurement devices whose
  `onlineSignal` field was `null` still deserialize. A stream containing a
  non-null `OnlineSignal` instance fails with `ClassNotFoundException` and must be
  recreated from its model inputs.

See the [API lifecycle guidance](api-lifecycle.md#legacy-aspen-ip21-historian-integration)
and [measurement devices](../process/equipment/measurement_devices.md#historian-measurements).

## Changed results

### `TwoFluidPipe`: annular wall friction and the feed split with a prescribed outlet pressure

Steady `TwoFluidPipe` results change in two places
(issue [#5266](https://github.com/equinor/neqsim/issues/5266)). No method is removed or renamed.

- **Annular wall friction is charged at the flowing (no-slip) mixture density.** Sections
  classified as annular return a lower friction gradient than before: about 5 per cent lower on
  a lean high-pressure gas-condensate line and 20 to 30 per cent lower on a low-pressure line
  with a large liquid mass fraction. Against OLGA 2025.1.0 on four cases the pressure drop is
  now 3 to 7 per cent below the reference, where it was 0.6 to 33 per cent above. Stratified,
  slug and dispersed sections are unchanged, as is the holdup closure.
  `setAnnularFlowingDensityFriction(false)` restores the earlier holdup-weighted density.
- **With a prescribed outlet pressure the feed split is flashed at the solved inlet pressure.**
  The pressure of the inlet stream used to set the gas, oil and aqueous rates carried down the
  line; it is now only a starting value. Results change wherever the stream pressure differed
  from the solved inlet pressure. A transient continued from the steady solution admits the same
  split while the inlet stream keeps its temperature, pressure and composition.

Migration:

- Rerun steady cases that contain annular sections and compare the pressure drop; a margin that
  was sized on the earlier conservative result should be reviewed, because the new result is on
  the low side of the reference.
- Outer loops that iterated the inlet stream pressure to the solved inlet pressure are no longer
  needed for a steady solve and can be removed; keeping them is harmless.
- Serialized pipes written before this release load with the holdup-weighted density.

See [TwoFluidPipe friction model](../process/TWOFLUIDPIPE_MODEL.md#friction-model).