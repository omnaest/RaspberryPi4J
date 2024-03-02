package org.omnaest.pi.service.sensor.pressure;

import java.util.Optional;

import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;

public interface PressureSensorMS5837Service
{

    public Optional<PressureAndTemperature> readSensor(String sensorId);

    public String enableSensorAndGetSensorId(MS5837Model model);

    public void disableSensor(String sensorId);

}
