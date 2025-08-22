# Battery storage unit

The `BatteryStorage` unit stores electrical energy for later use. It maintains an
internal state-of-charge and exchanges power with the rest of a process through
its energy stream. Positive duty on the energy stream represents charging while
negative duty represents power delivery.

The battery can be combined with other power generation units such as
`FuelCell` or `GasTurbine` to buffer excess electricity or supply power during
demand peaks.

```java
BatteryStorage battery = new BatteryStorage("battery", 5.0e5);
FuelCell cell = new FuelCell("cell", fuel, oxidant);

cell.run();
// store half of the produced power for one hour
battery.charge(-cell.getEnergyStream().getDuty() / 2.0, 1.0);
battery.run();
```
