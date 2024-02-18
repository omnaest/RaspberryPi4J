package org.omnaest.pi.service.sensor.gyro;

import org.omnaest.pi.client.domain.gyro.Orientation;

public interface GyroscopeService
{
    public Orientation getOrientation();

    public Orientation getOrientation(int numberOfSamplings);
}
