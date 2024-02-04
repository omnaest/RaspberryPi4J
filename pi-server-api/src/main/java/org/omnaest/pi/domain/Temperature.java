package org.omnaest.pi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Temperature
{
    @JsonProperty
    private double temperatureCelsius;

    public Temperature(double temperatureCelsius)
    {
        super();
        this.temperatureCelsius = temperatureCelsius;
    }

    public double getTemperatureCelsius()
    {
        return this.temperatureCelsius;
    }

    public double getTemperatureFahrenheit()
    {
        return this.temperatureCelsius * 1.8 + 32;
    }

    @Override
    public String toString()
    {
        return "Temperature [temperatureCelsius=" + this.temperatureCelsius + "]";
    }

}