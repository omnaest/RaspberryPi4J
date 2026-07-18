package org.omnaest.pi.service.sensor.weight.internal;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.BooleanUtils;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort;
import org.omnaest.pi.service.gpio.GPIOService.DigitalOutputGPIOPort;
import org.omnaest.pi.service.sensor.weight.Nau7802Service;
import org.omnaest.pi.service.sensor.weight.WeightService;
import org.omnaest.pi.service.utils.ThreadUtils;
import org.omnaest.utils.bitset.Bits;
import org.omnaest.utils.duration.DurationCapture;
import org.omnaest.utils.duration.DurationCapture.DurationMeasurement;
import org.omnaest.utils.duration.DurationCapture.MeasurementResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Configuration
public class WeightServiceImpl implements WeightService
{
    @Autowired
    private GPIOService    gpioService;

    @Autowired
    private Nau7802Service nau7802Service;

    @Override
    public int readValueFromNau7802(int bus)
    {
        return this.nau7802Service.usingBus(bus)
                                  .readValue();
    }

    @Override
    public long readValueFromHX711(HX711PortConfiguration portConfiguration)
    {
        DigitalInputGPIOPort dataPort = this.gpioService.getDigitalInputGPIOPort(portConfiguration.getDataPort())
                                                        .withNoPullResistance()
                                                        .enable();
        DigitalOutputGPIOPort clockPort = this.gpioService.getDigitalOutputGPIOPort(portConfiguration.getClockPort())
                                                          .enable()
                                                          .setState(false);

        try
        {
            Gain gain = portConfiguration.getGain();

            clockPort.setState(false);
            this.waitUntilDataPortIsReady(dataPort);
            this.readRawData(gain, dataPort, clockPort); // dummy read to set the gain correctly

            this.sleep(1, TimeUnit.MILLISECONDS);
            clockPort.setState(false);
            this.waitUntilDataPortIsReady(dataPort);
            long value = this.readRawData(gain, dataPort, clockPort);

            log.info("Weight value read from sensor on (" + dataPort + ") : " + value);

            this.sleep(1, TimeUnit.MILLISECONDS);
            return value;
        }
        finally
        {
            dataPort.disable();
            clockPort.disable();
        }
    }

    private void sleep(int duration, TimeUnit timeUnit)
    {
        long nanos = timeUnit.toNanos(duration);
        if (nanos <= TimeUnit.MICROSECONDS.toNanos(10))
        {
            long start = System.nanoTime();
            while (System.nanoTime() - start < nanos)
            {
                // do nothing
            }
        }
        else
        {
            ThreadUtils.sleep(duration, timeUnit);
        }
    }

    private long readRawData(Gain gain, DigitalInputGPIOPort dataPort, DigitalOutputGPIOPort clockPort)
    {
        Bits bits = Bits.newInstance();

        int numberOfDataBits = 24 + gain.getAdditionalBits();
        for (int i = 0; i < numberOfDataBits; i++)
        {
            clockPort.setState(true);
            this.sleep(200, TimeUnit.NANOSECONDS);
            clockPort.setState(false);
            this.sleep(200, TimeUnit.NANOSECONDS);

            bits.setIndex(i, dataPort.getState());
        }

        clockPort.setState(true);
        this.sleep(200, TimeUnit.NANOSECONDS);
        clockPort.setState(false);

        log.info("Received HX711 sensor data on (" + dataPort + ") : " + bits.toBinaryDigits()
                                                                             .toUpperCaseString());

        long result = 0;
        for (boolean value : bits.toBooleanArray())
        {
            result = result << 1;
            result += BooleanUtils.toInteger(value);
        }

        return result ^ 0x800000;
    }

    private void waitUntilDataPortIsReady(DigitalInputGPIOPort dataPort)
    {
        DurationMeasurement durationMeasurement = DurationCapture.newInstance()
                                                                 .start();
        this.sleep(1, TimeUnit.MILLISECONDS);
        while (dataPort.getState())
        {
            this.sleep(1, TimeUnit.MILLISECONDS);

            MeasurementResult durationMeasurementResult = durationMeasurement.stop();
            if (durationMeasurementResult.getDuration(TimeUnit.SECONDS) >= 5)
            {
                throw new IllegalStateException("Waiting for data port to be ready timed out after " + durationMeasurementResult.getDurationAsCanonicalString()
                                                + ". Port = " + dataPort);
            }
        }
    }

}
