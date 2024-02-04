package org.omnaest.pi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BMP180Measurement
{
    @JsonProperty
    private double altitude;

    @JsonProperty
    private double pressure;

    @JsonProperty
    private Temperature temperature;

    public BMP180Measurement(double altitude, double pressure, Temperature temperature)
    {
        super();
        this.altitude = altitude;
        this.pressure = pressure;
        this.temperature = temperature;
    }

    public double getAltitude()
    {
        return this.altitude;
    }

    public double getPressure()
    {
        return this.pressure;
    }

    public Temperature getTemperature()
    {
        return this.temperature;
    }

    @Override
    public String toString()
    {
        return "Measurement [altitude=" + this.altitude + ", pressure=" + this.pressure + ", temperature=" + this.temperature + "]";
    }

}