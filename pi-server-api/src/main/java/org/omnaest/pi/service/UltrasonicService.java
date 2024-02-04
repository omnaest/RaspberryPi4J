package org.omnaest.pi.service;

import org.omnaest.pi.domain.UltrasonicSensorConfiguration;

public interface UltrasonicService
{
    public static interface UltrasonicDistanceSensor
    {
        /**
         * Init sensor
         * 
         * @param configuration
         */
        public void init(UltrasonicSensorConfiguration configuration);

        /**
         * Returns the distance in millimeters
         * 
         * @return
         */
        public double getDistance();

    }

    public UltrasonicDistanceSensor getInstance(int index);
}
