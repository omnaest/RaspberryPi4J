/*

	Copyright 2017 Danny Kunz

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.


*/
package org.omnaest.pi.service.gpio.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.omnaest.pi.service.gpio.GPIOService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinEdge;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.event.PinEventType;

@Service
public class GPIOServiceImpl implements GPIOService
{
    private static Logger LOG = LoggerFactory.getLogger(GPIOServiceImpl.class);

    private static final int PWM_DEFAULT_RANGE = 100;

    private GpioController gpioController;

    private final Map<Integer, GpioPinDigitalOutput> portToDigitalOutputPin = new ConcurrentHashMap<>();
    private final Map<Integer, GpioPinDigitalInput>  portToDigitalInputPin  = new ConcurrentHashMap<>();
    private final Map<Integer, GpioPinPwmOutput>     portToPWMOutputPin     = new ConcurrentHashMap<>();

    @PostConstruct
    public void init()
    {
        try
        {
            this.gpioController = GpioFactory.getInstance();
        }
        catch (UnsatisfiedLinkError e)
        {
            LOG.error("Unable to initialize pi4j GPIO library", e);
        }
    }

    @PreDestroy
    public void destroy()
    {
        this.gpioController.removeAllListeners();
        this.gpioController.shutdown();
    }

    @Override
    public void enableGPIOPortForDigitalOutput(int port)
    {
        if (!this.portToDigitalOutputPin.containsKey(port))
        {
            GpioPinDigitalOutput pin = this.gpioController.provisionDigitalOutputPin(RaspiPin.getPinByAddress(port));
            this.gpioController.export(PinMode.DIGITAL_OUTPUT, pin);
            this.portToDigitalOutputPin.put(port, pin);
            LOG.info("Enabled port " + port + " for digital output");
        }
    }

    @Override
    public void enableGPIOPortForPWM(int port)
    {
        GpioPinPwmOutput pin = this.gpioController.provisionPwmOutputPin(RaspiPin.getPinByAddress(port));
        this.gpioController.export(PinMode.PWM_OUTPUT, pin);
        this.portToPWMOutputPin.put(port, pin);
        LOG.info("Enabled port " + port + " for pwm output");
    }

    @Override
    public void enableGPIOPort(int port, boolean active)
    {
        GpioPinDigitalOutput pin = this.portToDigitalOutputPin.get(port);
        this.gpioController.setState(active, pin);
        LOG.info("Set state of port " + port + " to " + active);
    }

    @Override
    public void setGPIOPortPWMValue(int port, int value)
    {
        GpioPinPwmOutput pin = this.portToPWMOutputPin.get(port);
        pin.setPwm(value);

        LOG.info("Set pwm value of port " + port + " to " + value);
    }

    @Override
    public PwmGPIOPort getPwmGPIOPort(int port)
    {
        GpioController gpioController = this.gpioController;
        Map<Integer, GpioPinPwmOutput> portToPWMOutputPin = this.portToPWMOutputPin;
        return new PwmGPIOPort()
        {
            @Override
            public PwmGPIOPort enable()
            {
                portToPWMOutputPin.computeIfAbsent(port, digitalPort ->
                {
                    GpioPinPwmOutput pin = gpioController.provisionPwmOutputPin(RaspiPin.getPinByAddress(port));
                    gpioController.export(PinMode.PWM_OUTPUT, pin);
                    pin.setPwmRange(PWM_DEFAULT_RANGE);
                    LOG.info("Enabled port " + port + " for pwm output");

                    return pin;
                });
                return this;
            }

            @Override
            public PwmGPIOPort disable()
            {
                portToPWMOutputPin.computeIfPresent(port, (pwmPort, pin) ->
                {
                    gpioController.unexport(pin);
                    LOG.info("Disabled port " + pwmPort + " for pwm output");
                    return null;
                });
                return this;
            }

            @Override
            public double getState()
            {
                GpioPinPwmOutput pin = portToPWMOutputPin.get(port);
                int pwm = pin.getPwm();
                return pwm / (1.0 * PWM_DEFAULT_RANGE);
            }

            @Override
            public PwmGPIOPort setState(double value)
            {
                GpioPinPwmOutput pin = portToPWMOutputPin.get(port);

                pin.setPwm((int) Math.round(PWM_DEFAULT_RANGE * Math.max(0.0, Math.min(1.0, value))));

                LOG.info("Set pwm value of port " + port + " to " + value);

                return this;
            }

        };
    }

    @Override
    public DigitalOutputGPIOPort getDigitalOutputGPIOPort(int port)
    {
        GpioController gpioController = this.gpioController;
        Map<Integer, GpioPinDigitalOutput> portToDigitalOutputPin = this.portToDigitalOutputPin;
        return new DigitalOutputGPIOPort()
        {
            @Override
            public DigitalOutputGPIOPort enable()
            {
                portToDigitalOutputPin.computeIfAbsent(port, digitalPort ->
                {
                    GpioPinDigitalOutput pin = gpioController.provisionDigitalOutputPin(RaspiPin.getPinByAddress(digitalPort));
                    gpioController.export(PinMode.DIGITAL_OUTPUT, pin);
                    LOG.info("Enabled port " + port + " for digital output");
                    return pin;
                });
                return this;
            }

            @Override
            public DigitalOutputGPIOPort disable()
            {
                portToDigitalOutputPin.computeIfPresent(port, (digitalPort, pin) ->
                {
                    gpioController.unexport(pin);
                    LOG.info("Disabled port " + digitalPort + " for digital output");
                    return null;
                });
                return this;
            }

            @Override
            public boolean getState()
            {
                GpioPinDigitalOutput pin = portToDigitalOutputPin.get(port);
                boolean active = PinState.HIGH.equals(gpioController.getState(pin));
                LOG.info("Gets the state for digital output port " + port + ": " + active);
                return active;
            }

            @Override
            public DigitalOutputGPIOPort setState(boolean active)
            {
                GpioPinDigitalOutput pin = portToDigitalOutputPin.get(port);
                gpioController.setState(active, pin);
                LOG.info("Sets the state for digital output port " + port + " to " + active);
                return this;
            }
        };
    }

    @Override
    public DigitalInputGPIOPort getDigitalInputGPIOPort(int port)
    {
        GpioController gpioController = this.gpioController;
        Map<Integer, GpioPinDigitalInput> portToDigitalInputPin = this.portToDigitalInputPin;
        return new DigitalInputGPIOPortImpl(portToDigitalInputPin, port, gpioController);
    }

    private static class DigitalInputGPIOPortImpl implements DigitalInputGPIOPort
    {
        private final Map<Integer, GpioPinDigitalInput> portToDigitalInputPin;
        private final int                               port;
        private final GpioController                    gpioController;
        private PinPullResistance                       pullResistance = PinPullResistance.PULL_UP;
        private boolean                                 enabled        = false;

        public DigitalInputGPIOPortImpl(Map<Integer, GpioPinDigitalInput> portToDigitalInputPin, int port, GpioController gpioController)
        {
            this.portToDigitalInputPin = portToDigitalInputPin;
            this.port = port;
            this.gpioController = gpioController;
        }

        @Override
        public DigitalInputGPIOPort withPullDownResistance()
        {
            this.validatePortIsNotYetEnabled();
            this.pullResistance = PinPullResistance.PULL_DOWN;
            return this;
        }

        @Override
        public DigitalInputGPIOPort withPullUpResistance()
        {
            this.validatePortIsNotYetEnabled();
            this.pullResistance = PinPullResistance.PULL_UP;
            return this;
        }

        private void validatePortIsNotYetEnabled()
        {
            if (this.enabled)
            {
                throw new IllegalArgumentException("Port " + this.port + " is already enabled, please call this method before calling enable().");
            }
        }

        @Override
        public DigitalInputGPIOPort withNoPullResistance()
        {
            this.validatePortIsNotYetEnabled();
            this.pullResistance = PinPullResistance.OFF;
            return this;
        }

        @Override
        public DigitalInputGPIOPort enable()
        {
            this.portToDigitalInputPin.computeIfAbsent(this.port, digitalPort ->
            {
                GpioPinDigitalInput pin = this.gpioController.provisionDigitalInputPin(RaspiPin.getPinByAddress(digitalPort), this.pullResistance);
                this.gpioController.export(PinMode.DIGITAL_INPUT, pin);
                this.enabled = true;
                LOG.info("Enabled port " + this.port + " for digital input");
                return pin;
            });
            return this;
        }

        @Override
        public DigitalInputGPIOPort disable()
        {
            this.portToDigitalInputPin.computeIfPresent(this.port, (digitalPort, pin) ->
            {
                pin.removeAllListeners();
                pin.removeAllTriggers();
                this.gpioController.unexport(pin);
                this.enabled = false;
                LOG.info("Disabled port " + digitalPort + " for digital input");
                return null;
            });
            return this;
        }

        @Override
        public boolean isEnabled()
        {
            return this.enabled;
        }

        @Override
        public boolean getState()
        {
            GpioPinDigitalInput pin = this.portToDigitalInputPin.get(this.port);
            boolean active = PinState.HIGH.equals(this.gpioController.getState(pin));
            LOG.info("Gets the state for digital input port " + this.port + ": " + active);
            return active;
        }

        @Override
        public DigitalInputGPIOPort addStateChangeListener(Consumer<DigitalInputPinStateChange> stateChangeListener)
        {
            GpioPinDigitalInput pin = this.portToDigitalInputPin.get(this.port);
            pin.addListener(new GpioPinListenerDigital()
            {
                @Override
                public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event)
                {
                    PinEventType eventType = event.getEventType();
                    if (PinEventType.DIGITAL_STATE_CHANGE.equals(eventType))
                    {
                        PinEdge edge = event.getEdge();
                        if (PinEdge.FALLING.equals(edge))
                        {
                            stateChangeListener.accept(new DigitalInputPinStateChange(true, false));
                        }
                        else if (PinEdge.RISING.equals(edge))
                        {
                            stateChangeListener.accept(new DigitalInputPinStateChange(false, true));
                        }
                        else if (PinEdge.BOTH.equals(edge))
                        {
                            stateChangeListener.accept(new DigitalInputPinStateChange(true, true));
                        }
                        else if (PinEdge.NONE.equals(edge))
                        {
                            stateChangeListener.accept(new DigitalInputPinStateChange(false, false));
                        }
                    }
                }
            });
            return this;
        }
    }

}
