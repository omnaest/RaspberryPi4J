package org.omnaest.pi.service.sensor.pressure;

import java.util.Optional;

import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;

public interface PressureSensorMS5837Service
{

    public Optional<PressureAndTemperature> readSensor(String sensorId);

    public String enableSensorAndGetSensorId();

    public void disableSensor(String sensorId);

}
