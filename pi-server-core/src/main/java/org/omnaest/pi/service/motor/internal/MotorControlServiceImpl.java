package org.omnaest.pi.service.motor.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.omnaest.pi.client.domain.motor.L298nMotorControlDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalOutputGPIOPort;
import org.omnaest.pi.service.gpio.GPIOService.PwmGPIOPort;
import org.omnaest.pi.service.motor.MotorControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MotorControlServiceImpl implements MotorControlService
{
    @Autowired
    private GPIOService gpioService;

    private Map<String, MotorControl>           motorControlIdToMotorControl           = new ConcurrentHashMap<>();
    private Map<L298nMotorControlDefinition, String> motorControlDefinitionToMotorControlId = new ConcurrentHashMap<>();

    @Override
    public MotorControl defineMotorControl(L298nMotorControlDefinition definition)
    {
        return this.getMotorControl(this.motorControlDefinitionToMotorControlId.computeIfAbsent(definition, md ->
        {
            String id = UUID.randomUUID()
                            .toString();

            DigitalOutputGPIOPort forwardPort = this.gpioService.getDigitalOutputGPIOPort(definition.getForwardPort());
            DigitalOutputGPIOPort backwardPort = this.gpioService.getDigitalOutputGPIOPort(definition.getBackwardPort());
            PwmGPIOPort pwmGPIOPort = this.gpioService.getPwmGPIOPort(definition.getPwmPort());

            forwardPort.enable()
                       .setState(false);
            backwardPort.enable()
                        .setState(false);
            pwmGPIOPort.enable()
                       .setState(0.0);

            this.motorControlIdToMotorControl.put(id, new MotorControlImpl(id, forwardPort, backwardPort, pwmGPIOPort));

            return id;
        }))
                   .get();
    }

    @Override
    public Optional<MotorControl> getMotorControl(String id)
    {
        return Optional.ofNullable(this.motorControlIdToMotorControl.get(id));
    }

    private static class MotorControlImpl implements MotorControl
    {
        private final String                id;
        private final DigitalOutputGPIOPort forwardPort;
        private final DigitalOutputGPIOPort backwardPort;
        private final PwmGPIOPort           pwmGPIOPort;

        private MotorControlImpl(String id, DigitalOutputGPIOPort forwardPort, DigitalOutputGPIOPort backwardPort, PwmGPIOPort pwmGPIOPort)
        {
            this.backwardPort = backwardPort;
            this.pwmGPIOPort = pwmGPIOPort;
            this.id = id;
            this.forwardPort = forwardPort;
        }

        @Override
        public String getId()
        {
            return this.id;
        }

        @Override
        public MotorControl stop()
        {
            this.backwardPort.setState(false);
            this.forwardPort.setState(false);
            this.pwmGPIOPort.setState(0.0);
            return this;
        }

        @Override
        public MotorControl move(MotorMovementDirection direction, double speed)
        {
            if (MotorMovementDirection.FORWARDS.equals(direction))
            {
                this.backwardPort.setState(false);
                if (this.backwardPort.getState())
                {
                    throw new IllegalStateException("Cannot activate foward pin as backward pin is still enabled.");
                }

                this.pwmGPIOPort.setState(speed);

                this.forwardPort.setState(true);
            }
            else
            {
                this.forwardPort.setState(false);
                if (this.forwardPort.getState())
                {
                    throw new IllegalStateException("Cannot activate backward pin as forward pin is still enabled.");
                }

                this.pwmGPIOPort.setState(speed);

                this.backwardPort.setState(true);
            }
            return this;
        }
    }
}
