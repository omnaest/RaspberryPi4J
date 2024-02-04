package org.omnaest.pi.service.motor;

import java.util.Optional;

import org.omnaest.pi.client.domain.motor.L298nMotorControlDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;

/**
 * The {@link MotorControlService} allows to control motor drivers like the L298n
 * 
 * @author omnaest
 */
public interface MotorControlService
{

    public Optional<MotorControl> getMotorControl(String id);

    public MotorControl defineMotorControl(L298nMotorControlDefinition definition);

    public static interface MotorControl
    {
        public String getId();

        public MotorControl move(MotorMovementDirection direction, double speed);

        public MotorControl stop();
    }

}
