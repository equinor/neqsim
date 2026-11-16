---
title: "NeqSim 3.30.0 release and migration notes"
description: "Changes since 3.29.0, in preparation. Removal of the deprecated Aspen IP.21 online-signal integration."
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
