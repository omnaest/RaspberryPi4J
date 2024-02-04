package org.omnaest.pi.domain;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UltrasonicSensorConfiguration
{
    @JsonProperty
    private int echoPort;

    @JsonProperty
    private int triggerPort;

    @JsonProperty
    private long pingTimeout;

    @JsonProperty
    private long signalTimeout;

    @JsonProperty
    private int[] signals = new int[] { 5, 20, 0 };

    public UltrasonicSensorConfiguration()
    {
        super();
    }

    public int[] getSignals()
    {
        return this.signals;
    }

    public void setSignals(int[] signals)
    {
        this.signals = signals;
    }

    public int getEchoPort()
    {
        return this.echoPort;
    }

    public int getTriggerPort()
    {
        return this.triggerPort;
    }

    public long getPingTimeout()
    {
        return this.pingTimeout;
    }

    public long getSignalTimeout()
    {
        return this.signalTimeout;
    }

    public void setEchoPort(int echoPort)
    {
        this.echoPort = echoPort;
    }

    public void setTriggerPort(int triggerPort)
    {
        this.triggerPort = triggerPort;
    }

    public void setPingTimeout(long pingTimeout)
    {
        this.pingTimeout = pingTimeout;
    }

    public void setSignalTimeout(long signalTimeout)
    {
        this.signalTimeout = signalTimeout;
    }

    @Override
    public String toString()
    {
        return "UltrasonicSensorConfiguration [echoPort=" + this.echoPort + ", triggerPort=" + this.triggerPort + ", pingTimeout=" + this.pingTimeout
                + ", signalTimeout=" + this.signalTimeout + ", signals=" + Arrays.toString(this.signals) + "]";
    }

}