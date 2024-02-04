package org.omnaest.pi.client.domain.gyro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Orientation
{
    @JsonProperty
    public double x;

    @JsonProperty
    public double y;

    @JsonProperty
    public double z;

    @JsonCreator
    protected Orientation()
    {
        super();
    }

    public Orientation(double x, double y, double z)
    {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX()
    {
        return this.x;
    }

    public double getY()
    {
        return this.y;
    }

    public double getZ()
    {
        return this.z;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Orientation [x=")
               .append(this.x)
               .append(", y=")
               .append(this.y)
               .append(", z=")
               .append(this.z)
               .append("]");
        return builder.toString();
    }

}
