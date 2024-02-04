package org.omnaest.pi.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.omnaest.pi.domain.UltrasonicSensorConfiguration;
import org.omnaest.pi.service.utils.NanoDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

@Service
public class UltrasonicServiceImpl implements UltrasonicService
{
    private static final Logger LOG = LoggerFactory.getLogger(UltrasonicServiceImpl.class);

    private Map<Integer, UltrasonicDistanceSensor> indexToUltrasonicDistanceSensor = new ConcurrentHashMap<>();

    @Override
    public UltrasonicDistanceSensor getInstance(int index)
    {
        return this.indexToUltrasonicDistanceSensor.computeIfAbsent(index, i -> new UltrasonicDistanceSensorImpl());
    }

    public static class UltrasonicDistanceSensorImpl implements UltrasonicDistanceSensor
    {
        private long pingTimeout   = 1000;
        private long signalTimeout = 1000;

        private GpioPinDigitalOutput triggerPin;
        private GpioPinDigitalInput  echoPin;

        private boolean initialized = false;
        private int[]   signals;

        public UltrasonicDistanceSensorImpl()
        {
        }

        @Override
        public void init(UltrasonicSensorConfiguration configuration)
        {
            if (!this.initialized)
            {
                LOG.info("Initializing ultrasonic sensor " + configuration);
                this.initialized = true;

                this.pingTimeout = configuration.getPingTimeout();
                this.signalTimeout = configuration.getSignalTimeout();

                GpioController controller = GpioFactory.getInstance();
                this.triggerPin = controller.provisionDigitalOutputPin(RaspiPin.getPinByAddress(configuration.getTriggerPort()), "pin_trig", PinState.HIGH);
                this.triggerPin.setShutdownOptions(true, PinState.LOW);
                this.echoPin = controller.provisionDigitalInputPin(RaspiPin.getPinByAddress(configuration.getEchoPort()), PinPullResistance.PULL_DOWN);

                this.signals = configuration.getSignals();
            }
        }

        @Override
        public double getDistance()
        {
            //
            this.assertInitialized();

            // 
            this.sendSignal();

            //
            return this.waitForIncomingSignal()
                       .flatMap(startTime -> this.waitForSignalEnd()
                                                 .map(endTime ->
                                                 {
                                                     long signalTravelDuration = endTime - startTime;
                                                     double distance = signalTravelDuration / 5830.9037900874635568513119533528;

                                                     LOG.debug("Ultrasonic signal received after " + signalTravelDuration + " ns with a distance of " + distance
                                                             + " mm");
                                                     return distance;
                                                 }))
                       .orElse(Double.NEGATIVE_INFINITY);
        }

        private void assertInitialized()
        {
            if (!this.initialized)
            {
                throw new IllegalStateException("Ultrasonic sensor not initialized");
            }
        }

        private void sendSignal()
        {
            LOG.debug("Sending ultrasonic signal " + Arrays.toString(this.signals));

            boolean state = false;
            for (int duration : this.signals)
            {
                this.triggerPin.setState(state);
                busyWait(duration, TimeUnit.MICROSECONDS);
                state = !state;
            }
        }

        private Optional<Long> waitForIncomingSignal()
        {
            Long startTime = null;

            NanoDuration duration = NanoDuration.start();
            boolean timeout = false;
            while (this.echoPin.isLow())
            {
                if (duration.stop() >= this.pingTimeout)
                {
                    LOG.warn("Signal start wait timeout after " + duration.stop() + " ns");
                    timeout = true;
                    break;
                }
            }

            if (!timeout)
            {
                startTime = System.nanoTime();
            }

            return Optional.ofNullable(startTime);
        }

        private Optional<Long> waitForSignalEnd()
        {
            Long endTime = null;
            {
                NanoDuration duration = NanoDuration.start();
                boolean timeout = false;
                while (this.echoPin.isHigh())
                {
                    if (duration.stop() >= this.signalTimeout)
                    {
                        LOG.warn("Signal end wait timeout after " + duration.stop() + " ns");
                        timeout = true;
                        break;
                    }
                }

                if (!timeout)
                {
                    endTime = System.nanoTime();
                }
            }
            return Optional.ofNullable(endTime);
        }

        private static void busyWait(long duration, TimeUnit timeUnit)
        {
            NanoDuration durationCounter = NanoDuration.start();
            long targetDuration = timeUnit.toNanos(duration);
            while (durationCounter.stop() < targetDuration)
            {
            }
        }

    }
}
