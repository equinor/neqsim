package neqsim.process.equipment.heatexchanger;

/**
 * Heater for process streams using condensing steam as heating medium. /** Sets the steam inlet
 * temperature.
 *
 * @param temperature steam inlet temperature
 * @param unit unit of temperature
 */
public void setSteamInletTemperature(double temperature,String unit){private double steamOutletTemperature=373.15; // K
private double steamPressure=1.0; // bara
private double steamFlowRate=0.0; // kg/s

public SteamHeater(String name){super(name);}

public SteamHeater(String name,StreamInterface inStream){super(name,inStream);setWaterModel();}

/**
 * Sets the steam inlet temperature. /** Sets the steam outlet temperature.
 *
 * @param temperature steam outlet temperature
 * @param unit unit of temperature
 */
public void setSteamOutletTemperature(double temperature,String unit){}

/** Set outlet condensate temperature. */
public void setSteamOutletTemperature(double temperature,String unit){steamOutletTemperature=new neqsim.util.unit.TemperatureUnit(temperature,unit).getValue("K");}

/**
 * Sets the steam outlet temperature. /** Sets the steam pressure.
 *
 * @param pressure steam pressure
 * @param unit unit of pressure
 */
public void setSteamPressure(double pressure,String unit){}

public double getSteamFlowRate(String unit){return new neqsim.util.unit.RateUnit(steamFlowRate,"kg/sec",1.0,1.0,0.0).getValue(unit);}

/**
 * Sets the steam pressure.
 *
 * @param pressure the steam pressure
 * @param unit the unit of pressure
 */
private void setWaterModel(){if(inStream!=null){inStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);}if(outStream!=null){outStream.getThermoSystem().setPhysicalPropertyModel(PhysicalPropertyModel.WATER);}}

@Override public void setInletStream(StreamInterface stream){super.setInletStream(stream);setWaterModel();}

@Override public void run(UUID id){super.run(id);calculateSteamFlowRate();}

private void calculateSteamFlowRate(){double pinMPa=steamPressure/10.0; // bara -> MPa
double hin=Iapws_if97.h_pt(pinMPa,steamInletTemperature);double hout=Iapws_if97.h_pt(pinMPa,steamOutletTemperature);double deltaH=hin-hout; // kJ/kg
                                                                                                                                            // released
                                                                                                                                            // per
                                                                                                                                            // kg
                                                                                                                                            // steam
logger.debug("DEBUG SteamHeater: getEnergyInput()="+getEnergyInput()+", hin="+hin+", hout="+hout+", deltaH="+deltaH);if(Math.abs(deltaH)<1e-6){steamFlowRate=0.0;return;}steamFlowRate=getEnergyInput()/(deltaH*1000.0);}}
