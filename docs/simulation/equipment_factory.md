---
title: Equipment factory usage
description: The `EquipmentFactory` provides a single entry-point for instantiating process equipment that can
---

# Equipment factory usage

The `EquipmentFactory` provides a single entry-point for instantiating process equipment that can
be automatically wired into a `ProcessSystem`. The factory supports every value listed in
`EquipmentEnum`, including the energy storage and production classes (`WindTurbine`,
`BatteryStorage`, and `SolarPanel`).

## Basic creation

```java
ProcessEquipmentInterface pump = EquipmentFactory.createEquipment("pump1", EquipmentEnum.Pump);
ProcessEquipmentInterface stream = EquipmentFactory.createEquipment("feed", "stream");
```

The string based overload is tolerant of the common aliases that existed historically (for example
`valve` and `separator_3phase`). Unknown identifiers now throw an exception instead of silently
creating the wrong equipment.

## Equipment with mandatory collaborators

Some equipment types cannot be instantiated without additional collaborators. The factory now
prevents creation of partially initialised objects and exposes dedicated helpers instead:

```java
StreamInterface motive = new Stream("motive");
StreamInterface suction = new Stream("suction");
Ejector ejector = EquipmentFactory.createEjector("ej-1", motive, suction);

SystemInterface reservoirFluid = new SystemSrkEos(273.15, 100.0);
ReservoirCVDsim cvd = EquipmentFactory.createReservoirCVDsim("cvd", reservoirFluid);
```

Attempting to create these units through the generic method now results in an informative exception
message that points to the correct helper method.
