package org.omnaest.pi.service.sensor.flow;

import org.omnaest.pi.client.domain.flow.FlowSensorDefinition;

public interface FlowSensorService
{
    /**
     * Returns the flow rate in L/min
     * 
     * @param signalPort
     * @return
     */
    public double getFlowRate(int signalPort);

    public void disableFlowSensor(int signalPort);

    public void enableFlowSensor(int signalPort, FlowSensorDefinition flowSensorDefinition);
}
