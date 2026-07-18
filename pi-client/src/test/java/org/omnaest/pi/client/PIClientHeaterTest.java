package org.omnaest.pi.client;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.utils.ThreadUtils;

public class PIClientHeaterTest
{

    @Test
    @Ignore
    public void testHeater() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.1.246", 8080);

        Heater heater = new Heater(client, 27);

        while (true)
        {
            try
            {
                double temperatureCelsius = this.measureTemperature(client);
                System.out.println("Temperature (C): " + NumberFormat.getNumberInstance()
                                                                     .format(temperatureCelsius));
                if (temperatureCelsius < 10)
                {
                    heater.deactivate();
                    System.err.println("Temperature below 10 C");
                    System.out.println("Heater deactivated");
                }
                else if (temperatureCelsius > 80)
                {
                    heater.deactivate();
                    System.err.println("Temperature above 80 C");
                    System.out.println("Heater deactivated");
                }
                else if (temperatureCelsius < 70)
                {
                    heater.activate();
                    System.out.println("Heater activated");
                }
                else
                {
                    heater.deactivate();
                    System.out.println("Heater deactivated");
                }

                this.waitFor10Seconds();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private void waitFor10Seconds()
    {
        for (int ii = 0; ii < 10; ii++)
        {
            System.out.print(".");
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
        System.out.println();
    }

    @Test
    @Ignore
    public void testTermperature() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.1.246", 8080);

        Heater heater = new Heater(client, 27);
        heater.deactivate();

        while (true)
        {
            try
            {
                double temperatureCelsius = this.measureTemperature(client);
                System.out.println("Temperature (C): " + NumberFormat.getNumberInstance()
                                                                     .format(temperatureCelsius));

                this.waitFor10Seconds();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private double measureTemperature(PiClient client)
    {
        return client.forBMP180()
                     .getTemperature()
                     .getTemperatureCelsius();
    }

    public static class Heater
    {
        private PiClient client;
        private int      port;

        public Heater(PiClient client, int port)
        {
            super();
            this.client = client;
            this.port = port;

            this.init();
        }

        private void init()
        {
            this.client.enableGPIOForDigitalOutput(this.port);
        }

        public void activate()
        {
            this.client.setStateOfDigitalGPIOPort(this.port, false);
        }

        public void deactivate()
        {
            this.client.setStateOfDigitalGPIOPort(this.port, true);
        }
    }
}
