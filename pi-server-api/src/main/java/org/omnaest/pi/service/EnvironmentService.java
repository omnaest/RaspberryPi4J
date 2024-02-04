package org.omnaest.pi.service;

import java.util.Optional;

import org.omnaest.pi.domain.BMP180Measurement;

public interface EnvironmentService
{

    public static interface BMP180Sensor
    {
        public Optional<BMP180Measurement> measure();
    }

    public Optional<BMP180Sensor> getOrCreateBMP180SensorInstance();

}
