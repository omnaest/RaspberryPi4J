package org.omnaest.pi.client;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.omnaest.pi.client.PiClient.ServoControl;
import org.omnaest.pi.client.SprinklerEngine.SprinklerHead.Speed;
import org.omnaest.utils.MapUtils;
import org.omnaest.utils.ThreadUtils;

public class SprinklerEngine
{
    private final SprinklerHead     head;
    private final DiscEndSensors    discEndSensors;
    private final DiscRotaryEncoder discRotaryEncoder;

    public SprinklerEngine(PiClient client)
    {
        super();
        this.head = new SprinklerHead(client);
        this.discEndSensors = new DiscEndSensors(client);
        this.discRotaryEncoder = new DiscRotaryEncoder(client);
    }

    public void calibrateDisc()
    {
        this.discRotaryEncoder.calibrateMaximum();
        this.calibrate(Direction.COUNTERCLOCKWISE);
        this.discRotaryEncoder.calibrateMinimum();
        this.calibrate(Direction.CLOCKWISE);
        this.discRotaryEncoder.calibrateMaximum();
    }

    private void calibrate(Direction direction)
    {
        this.head.stopRotation();
        this.discRotaryEncoder.getAngle();
        this.head.rotate(direction, Speed.SLOW);
        ThreadUtils.sleep()
                   .inIntervalOf(50, TimeUnit.MILLISECONDS)
                   .andExecute((ii) -> System.out.println(this.discRotaryEncoder.getAngle() + " deg"))
                   .until(() -> this.discEndSensors.isEndReached(direction));
        this.head.rotate(direction.inverted(), Speed.SLOW);
        ThreadUtils.sleep()
                   .inIntervalOf(50, TimeUnit.MILLISECONDS)
                   .andExecute((ii) -> System.out.println(this.discRotaryEncoder.getAngle() + " deg"))
                   .until(() -> this.discEndSensors.isEndReached(direction.inverted()));
        this.head.stopRotation();
    }

    public static class DiscRotaryEncoder
    {
        private final PiClient client;
        private long           max = 0;
        private long           min = 0;

        public DiscRotaryEncoder(PiClient client)
        {
            super();
            this.client = client;
        }

        public void calibrateMaximum()
        {
            this.max = this.resolveRotaryEncoderValue();
        }

        public void calibrateMinimum()
        {
            this.min = this.resolveRotaryEncoderValue();
        }

        public int getAngle()
        {
            long value = this.resolveRotaryEncoderValue();
            System.out.println(value);
            long range = Math.max(1, this.max - this.min);
            int angle = (int) (350 * (value - this.min) / range);
            System.out.println("->" + angle + " deg");
            return angle;
        }

        private long resolveRotaryEncoderValue()
        {
            return this.client.forRotaryEncoder()
                              .withClkPort(15)
                              .withDtPort(16)
                              .getAsLong();
        }
    }

    public static class DiscEndSensors
    {
        private final PiClient client;

        public DiscEndSensors(PiClient client)
        {
            super();
            this.client = client;
        }

        public boolean isEndReached(Direction direction)
        {
            int pin = Direction.CLOCKWISE.equals(direction) ? 0 : 0; // TODO this needs two sensors
            boolean state = this.client.forDigitalPort(pin)
                                       .getState();
            System.out.println(state);
            return !state;
        }

    }

    public static enum Direction
    {
        CLOCKWISE, COUNTERCLOCKWISE;

        public Direction inverted()
        {
            return Direction.CLOCKWISE.equals(this) ? Direction.COUNTERCLOCKWISE : Direction.CLOCKWISE;
        }
    }

    public static class SprinklerHead
    {
        private final PiClient client;

        private final int      servoIndex1 = 12;
        private final int      servoIndex2 = 13;

        public SprinklerHead(PiClient client)
        {
            super();
            this.client = client;
        }

        public static enum Speed
        {
            SLOW, NORMAL, FAST
        }

        public void stopRotation()
        {
            int servoIndex1 = 12;
            int servoIndex2 = 13;

            this.client.forServo(servoIndex1)
                       .setDurationMaximum(4000)
                       .setDurationMinimum(1)
                       .setAngle(0);
            this.client.forServo(servoIndex2)
                       .setDurationMaximum(4000)
                       .setDurationMinimum(1)
                       .setAngle(0);
        }

        public void rotate(Direction direction, Speed speed)
        {
            Map<Integer, int[]> servoToForwardBackward = this.determineServoConfiguration(speed);
            //            servoToForwardBackward.put(8, new int[] { 120, 140 }); // water supply
            //            servoToForwardBackward.put(7, new int[] { 90, 170 }); // head vertical

            boolean counterClockwise = Direction.COUNTERCLOCKWISE.equals(direction) ? true : false;
            {
                ServoControl servoInOut = this.client.forServo(this.servoIndex1);
                {
                    int ii = counterClockwise ? servoToForwardBackward.get(this.servoIndex1)[0] : servoToForwardBackward.get(this.servoIndex1)[1];
                    servoInOut.setAngle(ii);
                }
            }
            {
                ServoControl servoInOut = this.client.forServo(this.servoIndex2);
                {
                    int ii = counterClockwise ? servoToForwardBackward.get(this.servoIndex2)[0] : servoToForwardBackward.get(this.servoIndex2)[1];
                    servoInOut.setAngle(ii);
                }
            }
        }

        private Map<Integer, int[]> determineServoConfiguration(Speed speed)
        {
            if (Speed.FAST.equals(speed))
            {
                return MapUtils.builder()
                               .put(13, new int[] {100, 155}) // lower main circle
                               .put(12, new int[] {100, 155}) // upper main circle
                               .build();
            }
            else if (Speed.NORMAL.equals(speed))
            {
                return MapUtils.builder()
                               .put(13, new int[] {115, 140}) // lower main circle
                               .put(12, new int[] {115, 140}) // upper main circle
                               .build();
            }
            else if (Speed.SLOW.equals(speed))
            {
                return MapUtils.builder()
                               .put(13, new int[] {122, 135}) // lower main circle
                               .put(12, new int[] {122, 135}) // upper main circle
                               .build();
            }
            else
            {
                throw new IllegalArgumentException("Unknown speed: " + speed);
            }
        }
    }
}
