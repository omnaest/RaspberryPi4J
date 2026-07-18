package org.omnaest.pi.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.pi.client.PiClient.Compass.Module;
import org.omnaest.pi.client.PiClient.FlowSensor;
import org.omnaest.pi.client.PiClient.Motor;
import org.omnaest.pi.client.PiClient.PressureSensorMS5837;
import org.omnaest.pi.client.PiClient.ServoControl;
import org.omnaest.pi.client.PiClient.ServoPinControl;
import org.omnaest.pi.client.domain.gyro.Orientation;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;
import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;
import org.omnaest.pi.client.domain.weight.HX711Definition.Gain;
import org.omnaest.utils.ThreadUtils;

public class PIRemoteClientTest
{

    @Test
    @Ignore
    public void testSetStateOfDigitalGPIOPort() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.246", 8080);

        int port = 15;
        client.enableGPIOForDigitalOutput(port);

        for (int ii = 0; ii < 50; ii++)
        {
            client.setStateOfDigitalGPIOPort(port, true);
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
            client.setStateOfDigitalGPIOPort(port, false);
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @Ignore
    public void testIdentifyPortViaLED() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.246", 8080);

        Arrays.asList(StringUtils.split("2 3 4 5 6 8 9 10 11 12 13 14 15 16 17 18 19 20 22 23 24 25 26 27", " "))
              .stream()
              .mapToInt(str -> NumberUtils.toInt(str))
              .forEach(port ->
              {
                  System.out.println("Port: " + port);
                  client.enableGPIOForDigitalOutput(port);

                  for (int ii = 0; ii < 4; ii++)
                  {
                      client.setStateOfDigitalGPIOPort(port, true);
                      ThreadUtils.sleepSilently(500, TimeUnit.MILLISECONDS);
                      client.setStateOfDigitalGPIOPort(port, false);
                      ThreadUtils.sleepSilently(500, TimeUnit.MILLISECONDS);
                  }
              });
    }

    public static enum Leg
    {
        RIGHT_FRONT(LegPart.RIGHT_FRONT_FOOT, LegPart.RIGHT_FRONT_KNEE, LegPart.RIGHT_FRONT_HIP), LEFT_FRONT(LegPart.LEFT_FRONT_FOOT, LegPart.LEFT_FRONT_KNEE, LegPart.LEFT_FRONT_HIP), RIGHT_MIDDLE(
                LegPart.RIGHT_MIDDLE_FOOT, LegPart.RIGHT_MIDDLE_KNEE, LegPart.RIGHT_MIDDLE_HIP), LEFT_MIDDLE(LegPart.LEFT_MIDDLE_FOOT, LegPart.LEFT_MIDDLE_KNEE,
                        LegPart.LEFT_MIDDLE_HIP), RIGHT_BACK(null, LegPart.RIGHT_BACK_KNEE, LegPart.RIGHT_BACK_HIP), LEFT_BACK(null, LegPart.LEFT_BACK_KNEE, LegPart.LEFT_BACK_HIP);

        private LegPart foot;
        private LegPart knee;
        private LegPart hip;

        private Leg(LegPart foot, LegPart knee, LegPart hip)
        {
            this.foot = foot;
            this.knee = knee;
            this.hip = hip;

        }

        public LegPart getFoot()
        {
            return this.foot;
        }

        public LegPart getKnee()
        {
            return this.knee;
        }

        public LegPart getHip()
        {
            return this.hip;
        }

        public Stream<LegPart> getParts()
        {
            return Arrays.asList(this.foot, this.knee, this.hip)
                         .stream()
                         .filter(part -> part != null);
        }

        public static Stream<Leg> stream()
        {
            return Arrays.asList(values())
                         .stream();
        }

    }

    public static enum LegPart
    {
        RIGHT_FRONT_FOOT(0, 30, false), RIGHT_FRONT_KNEE(1, 60, false), RIGHT_FRONT_HIP(2, 35, true), //
        LEFT_FRONT_FOOT(3, 30, false), LEFT_FRONT_KNEE(4, 60, true), LEFT_FRONT_HIP(5, 35, false), RIGHT_MIDDLE_FOOT(6, 30, true), RIGHT_MIDDLE_KNEE(7, 60, true), RIGHT_MIDDLE_HIP(8, 35,
                true), LEFT_MIDDLE_FOOT(9, 30, true), LEFT_MIDDLE_KNEE(10, 60, false), LEFT_MIDDLE_HIP(11, 35, false),
        //RIGHT_BACK_FOOT(6, 30),
        RIGHT_BACK_KNEE(14, 60, true), RIGHT_BACK_HIP(15, 35, true),
        //LEFT_BACK_FOOT(9, 30),
        LEFT_BACK_KNEE(12, 60, false), LEFT_BACK_HIP(13, 35, false);

        private int     servoIndex;
        private int     defaultAngle;
        private boolean counterClockWise;

        private LegPart(int servoIndex, int defaultAngle, boolean counterClockWise)
        {
            this.servoIndex = servoIndex;
            this.defaultAngle = defaultAngle;
            this.counterClockWise = counterClockWise;
        }

        public boolean isCounterClockWise()
        {
            return this.counterClockWise;
        }

        public int getServoIndex()
        {
            return this.servoIndex;
        }

        public int getDefaultAngle()
        {
            return this.defaultAngle;
        }

        public static Stream<LegPart> stream()
        {
            return Arrays.asList(values())
                         .stream();
        }

    }

    public static interface LegControl
    {
        public void moveToDefault();

        public void moveToOpen();
    }

    @Test
    @Ignore
    public void testServo() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.246", 8080);

        List<LegControl> legControls = Leg.stream()
                                          .map(leg -> new LegControl() {
                                              {
                                                  leg.getParts()
                                                     .forEach(legPart ->
                                                     {
                                                         int index = legPart.getServoIndex();
                                                         client.forServo(index)
                                                               .setDurationMaximum(4000)
                                                               .setDurationMinimum(1);
                                                     });
                                              }

                                              @Override
                                              public void moveToDefault()
                                              {
                                                  leg.getParts()
                                                     .forEach(part ->
                                                     {
                                                         int percentage = part.getDefaultAngle();
                                                         this.moveTo(percentage, part);
                                                     });
                                              }

                                              @Override
                                              public void moveToOpen()
                                              {
                                                  Arrays.asList(leg.getKnee())
                                                        .stream()
                                                        .filter(part -> part != null)
                                                        .forEach(part ->
                                                        {
                                                            int step = 30;
                                                            int percentage = part.getDefaultAngle() + (part.isCounterClockWise() ? 1 * step : -1 * step);
                                                            this.moveTo(percentage, part);
                                                        });
                                              }

                                              private void moveTo(int percentage, LegPart legPart)
                                              {
                                                  int index = legPart.getServoIndex();

                                                  int min = 40;
                                                  int max = 240;

                                                  int angle = (int) (min + percentage / 100.0 * (max - min));
                                                  client.forServo(index)
                                                        .setAngle(angle);
                                                  ThreadUtils.sleepSilently(400, TimeUnit.MILLISECONDS);
                                              }
                                          })
                                          .collect(Collectors.toList());
        legControls.stream()
                   .forEach(leg ->
                   {
                       //                       leg.moveToDefault();
                       leg.moveToOpen();
                   });
    }

    @Test
    @Ignore
    public void testRadarServo() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.246", 8080);

        ServoControl servo = client.forServo(15);
        servo.setDurationMaximum(2500)
             .setDurationMinimum(900);

        int angle = 100;
        for (int r = 0; r < 1; r++)
        {
            for (int ii = 0; ii <= 360; ii += 5)
            {
                int effectiveAngle = (int) Math.round(Math.sin(Math.toRadians(ii)) * angle);
                servo.setAngle(effectiveAngle);
            }
        }

        ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);

        servo.setAngle(0);

    }
    //
    //    @Test
    //    @Ignore
    //    public void testRadarSignal() throws Exception
    //    {
    //        PIClient client = new PIClient("192.168.1.246", 8080);
    //
    //        UltrasonicSensorControl sensor = client.forUltrasonicSensor(1);
    //        sensor.init(c -> c.setEchoPort(15) //27=16
    //                          .setTriggerPort(16)); //26=12
    //
    //        //        ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);
    //
    //        PiRadarEngine radar = PiRadarEngine.newInstance()
    //                                           .setMaxDistance(2000);
    //
    //        radar.render()
    //             .periodically(1, TimeUnit.SECONDS)
    //             .accept(result -> result.asMap()
    //                                     .writeToFileSilently(new File("C:/Temp/radar_pi.svg")));
    //
    //        ExecutorService executorService = Executors.newCachedThreadPool();
    //        executorService.submit(() ->
    //        {
    //            for (int ii = 0; ii < 1000; ii++)
    //            {
    //                double distance = sensor.getDistance();
    //
    //                radar.accept(new Measurement().setAngle(ii)
    //                                              .setDistance(distance)
    //                                              .setSpread(3));
    //                System.out.println(distance + " mm");
    //                ThreadUtils.sleepSilently(200, TimeUnit.MILLISECONDS);
    //            }
    //        });
    //
    //        ThreadUtils.sleepSilently(60, TimeUnit.SECONDS);
    //    }
    //
    //    @Test
    //    @Ignore
    //    public void testRadarServoWithRadar() throws Exception
    //    {
    //        PIClient client = new PIClient("192.168.1.246", 8080);
    //
    //        //
    //        UltrasonicSensorControl sensor = client.forUltrasonicSensor(1);
    //        sensor.init(c -> c.setEchoPort(15) //27=16
    //                          .setTriggerPort(16)); //26=12
    //
    //        PiRadarEngine radar = PiRadarEngine.newInstance();
    //
    //        radar.render()
    //             .periodically(1, TimeUnit.SECONDS)
    //             .accept(result -> result.asMap()
    //                                     .writeToFileSilently(new File("C:/Temp/radar_pi.svg")));
    //
    //        //
    //        ServoControl servo = client.forServo(15);
    //        servo.setDurationMaximum(2000)
    //             .setDurationMinimum(1000);
    //
    //        int angle = 145;
    //        int spread = 10;
    //        for (int r = 0; r < 150; r++)
    //        {
    //            int effectiveSpread = spread * (1 + 3 * ((r + 1) % 2));
    //            for (int ii = 0; ii <= 360; ii += effectiveSpread)
    //            {
    //                int effectiveAngle = (int) Math.round(Math.sin(Math.toRadians(ii)) * angle);
    //                servo.setAngle(effectiveAngle);
    //
    //                ThreadUtils.sleepSilently(50, TimeUnit.MILLISECONDS);
    //
    //                double distance = sensor.getDistance();
    //
    //                radar.accept(new Measurement().setAngle(effectiveAngle)
    //                                              .setDistance(distance)
    //                                              .setSpread(effectiveSpread));
    //                System.out.println(distance + " mm at angle " + effectiveAngle);
    //            }
    //        }
    //
    //        ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);
    //
    //        servo.setAngle(0);
    //
    //    }

    @Test
    @Ignore
    public void testContinousServo() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.1.246", 8080);

        double speed = 110.0 / 100.0;

        double speedLeft = speed;
        double speedRight = 0 * speed;
        {
            ServoControl servo = client.forServo(0);
            servo.setDurationMaximum(1700)
                 .setDurationNeutral(1450)
                 .setDurationMinimum(1100);

            servo.applySpeed(1.0 * speedLeft);
        }
        {
            ServoControl servo = client.forServo(1);
            servo.setDurationMaximum(1700)
                 .setDurationNeutral(1450)
                 .setDurationMinimum(1100);

            servo.applySpeed(-1.0 * speedRight);
        }
        ThreadUtils.sleepSilently(4000, TimeUnit.MILLISECONDS);

        client.forServo(0)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);
        client.forServo(1)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);
    }

    @Test
    @Ignore
    public void testContinousServoForSaltControl() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.226", 8080);

        double speed = 100.0 / 100.0;

        int servo1Index = 0;
        int servo2Index = 1;
        {
            ServoControl servoInOut = client.forServo(servo1Index);

            ServoControl servoRotate = client.forServo(servo2Index);
            //            servo.setDurationMaximum(2000)
            //                 .setDurationNeutral(1000)
            //                 .setDurationMinimum(200);

            //40 full out
            //220 full in

            servoRotate.setAngle(45);
            for (int ii = 40; ii <= 220; ii += 20)
            {
                servoInOut.setAngle(ii);
                ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);
                System.out.println(ii);
            }
            //            
            //            servo.setAngle(220);
            //            servo.setAngle(40);

            //            servoInOut.setAngle(220);

            //            servo.setDurationMaximum(1700)
            //                 .setDurationNeutral(1450)
            //                 .setDurationMinimum(1100);
            //
            //            servo.applySpeed(1.0 * speed);
        }

        ThreadUtils.sleepSilently(2000, TimeUnit.MILLISECONDS);

        client.forServo(servo1Index)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);
        client.forServo(servo2Index)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);

    }

    @Test
    @Ignore
    public void testContinousServo35kg() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.226", 8080);

        int servoIndex = 2;
        ServoControl servoInOut = client.forServo(servoIndex);
        //            servo.setDurationMaximum(2000)
        //                 .setDurationNeutral(1000)
        //                 .setDurationMinimum(200);

        //40 full out
        //220 full in

        for (int ii = 40; ii <= 220; ii += 20)
        {
            servoInOut.setAngle(ii);
            ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);
            System.out.println(ii);
        }

        ThreadUtils.sleepSilently(4000, TimeUnit.MILLISECONDS);

        client.forServo(servoIndex)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);

    }

    @Test
    @Ignore
    public void testMG90STowerProContinuous() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.226", 8080);

        Map<Integer, int[]> servoToForwardBackward = new HashMap<>();
        servoToForwardBackward.put(13, new int[] {130, 140}); // lower main circle
        servoToForwardBackward.put(12, new int[] {120, 145}); // upper main circle
        servoToForwardBackward.put(8, new int[] {120, 140}); // water supply
        servoToForwardBackward.put(7, new int[] {90, 170}); // head vertical
        int servoIndex = 12;
        ServoControl servoInOut = client.forServo(servoIndex);

        boolean forward = true;
        {
            int ii = forward ? servoToForwardBackward.get(servoIndex)[0] : servoToForwardBackward.get(servoIndex)[1];
            servoInOut.setAngle(ii);
            //            ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);
            System.out.println(ii);
        }

        ThreadUtils.sleepSilently(500, TimeUnit.MILLISECONDS);

        client.forServo(servoIndex)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);

    }

    @Test
    @Ignore
    public void testRotateSprinklerHead() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        Map<Integer, int[]> servoToForwardBackward = new HashMap<>();
        servoToForwardBackward.put(13, new int[] {122, 135}); // lower main circle
        servoToForwardBackward.put(12, new int[] {122, 135}); // upper main circle
        servoToForwardBackward.put(8, new int[] {120, 140}); // water supply
        servoToForwardBackward.put(7, new int[] {90, 170}); // head vertical

        int servoIndex1 = 12;
        int servoIndex2 = 13;
        boolean forward = true;
        {
            ServoControl servoInOut = client.forServo(servoIndex1);
            {
                int ii = forward ? servoToForwardBackward.get(servoIndex1)[0] : servoToForwardBackward.get(servoIndex1)[1];
                servoInOut.setAngle(ii);
                System.out.println(ii);
            }
        }
        {
            ServoControl servoInOut = client.forServo(servoIndex2);
            {
                int ii = forward ? servoToForwardBackward.get(servoIndex2)[0] : servoToForwardBackward.get(servoIndex2)[1];
                servoInOut.setAngle(ii);
                System.out.println(ii);
            }
        }

        int pin = 0;
        int durationInMilliseconds = 10000;
        int meassureInterval = 30;
        for (int ii = 0; ii < durationInMilliseconds / meassureInterval; ii++)
        {
            boolean state = client.forDigitalPort(pin)
                                  .getState();
            System.out.println(state);
            ThreadUtils.sleepSilently(meassureInterval, TimeUnit.MILLISECONDS);
            if (!state)
            {
                break;
            }
        }

        client.forServo(servoIndex1)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);
        client.forServo(servoIndex2)
              .setDurationMaximum(4000)
              .setDurationMinimum(1)
              .setAngle(0);

    }

    @Test
    @Ignore
    public void testForRotaryEncoder() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.226", 8080);

        IntStream.range(0, 100)
                 .forEach(i ->
                 {
                     long rotaryValue = client.forRotaryEncoder()
                                              .withClkPort(2)
                                              .withDtPort(3)
                                              .getAsLong();
                     System.out.println(rotaryValue);
                     ThreadUtils.sleepSilently(1000, TimeUnit.MILLISECONDS);

                 });
    }

    @Test
    @Ignore
    public void testKY010Module() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        int pin = 0;
        for (int ii = 0; ii < 50; ii++)
        {
            boolean state = client.forDigitalPort(pin)
                                  .getState();
            System.out.println(state);
            ThreadUtils.sleepSilently(100, TimeUnit.MILLISECONDS);
        }

    }

    @Test
    @Ignore
    public void testForCompass() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.226", 8080);

        CompassTracer compassTracer = new CompassTracer();
        IntStream.range(0, 1000)
                 .forEach(i ->
                 {
                     int angle = client.forCompass()
                                       .usingModule(Module.QMC5883L)
                                       .getNorthDirectionAngleClockwise();

                     compassTracer.accept(angle);

                     System.out.println(angle);
                     System.out.println(compassTracer.getAsDouble());
                     System.out.println();
                     ThreadUtils.sleepSilently(100, TimeUnit.MILLISECONDS);

                 });

    }

    public static class CompassTracer implements DoubleConsumer, DoubleSupplier
    {
        private double orientation = 0.0;
        private int    weight      = 10;

        @Override
        public void accept(double angle)
        {
            double effectiveAngle = angle;
            if (Math.abs(this.orientation - (angle + 360)) < Math.abs(this.orientation - angle))
            {
                effectiveAngle = angle + 360;
            }
            else if (Math.abs(this.orientation - (angle - 360)) < Math.abs(this.orientation - angle))
            {
                effectiveAngle = angle - 360;
            }

            System.out.println("e: " + effectiveAngle);

            this.orientation = ((this.orientation * (this.weight - 1) + effectiveAngle) / (1.0 * this.weight));

            if (this.orientation >= 360)
            {
                this.orientation -= 360;
            }
            else if (this.orientation < 0)
            {
                this.orientation += 360;
            }
        }

        @Override
        public double getAsDouble()
        {
            return this.orientation;
        }

    }

    @Test
    @Ignore
    public void testForGyroscope() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        while (true)
        {
            Orientation orientation = client.forGyroscopeGY521()
                                            .withNumberOfSamplings(500)
                                            .getOrientation();

            System.out.println(Math.round(orientation.getX() / 100.0) + " : " + Math.round(orientation.getY() / 100.0) + " : "
                               + Math.round(orientation.getZ() / 100.0));
        }
    }

    @Test
    @Ignore
    public void testI2C() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        for (int ii = 1; ii < 120; ii++)
        {

            byte value = client.forI2C()
                               .onBus(1)
                               .readByte(0x68, 0x68, ii);
            System.out.println(value);
        }
    }

    @Test
    @Ignore
    public void testMotor() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        {
            int fowardPin = 15;
            int backwardPin = 16;
            int pwmPin = 1;
            Motor motor = client.motor()
                                .defineL298nAt(fowardPin, backwardPin, pwmPin)
                                .move(MotorMovementDirection.BACKWARDS, 1.0);
            ThreadUtils.sleepSilently(20, TimeUnit.SECONDS);
            motor.move(MotorMovementDirection.FORWARDS, 0.0);
        }

        {
            int fowardPin = 21;
            int backwardPin = 22;
            int pwmPin = 23;
            Motor motor = client.motor()
                                .defineL298nAt(fowardPin, backwardPin, pwmPin)
                                .move(MotorMovementDirection.BACKWARDS, 1.0);
            ThreadUtils.sleepSilently(20, TimeUnit.SECONDS);
            motor.move(MotorMovementDirection.FORWARDS, 0.0);
        }

        {
            int fowardPin = 15;
            int backwardPin = 16;
            int pwmPin = 1;
            Motor motor = client.motor()
                                .defineL298nAt(fowardPin, backwardPin, pwmPin)
                                .move(MotorMovementDirection.FORWARDS, 1.0);
            ThreadUtils.sleepSilently(20, TimeUnit.SECONDS);
            motor.move(MotorMovementDirection.FORWARDS, 0.0);
        }

        {
            int fowardPin = 21;
            int backwardPin = 22;
            int pwmPin = 23;
            Motor motor = client.motor()
                                .defineL298nAt(fowardPin, backwardPin, pwmPin)
                                .move(MotorMovementDirection.FORWARDS, 1.0);
            ThreadUtils.sleepSilently(20, TimeUnit.SECONDS);
            motor.move(MotorMovementDirection.FORWARDS, 0.0);
        }
    }

    @Test
    @Ignore
    public void testFlowSensor()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        FlowSensor flowSensor = client.flowSensor(4)
                                      .withFlowRateCoefficient(98.0)
                                      .enable();

        for (int ii = 0; ii < 120; ii++)
        {
            double flowRate = flowSensor.getFlowRate();

            System.out.println("flow rate L/min: " + org.omnaest.utils.NumberUtils.formatter()
                                                                                  .format(flowRate));
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }

        //        flowSensor.disable();
    }

    @Test
    @Ignore
    public void testMS5837Sensor()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        PressureSensorMS5837 sensor = client.pressureSensorMS5837(MS5837Model.MS5837_02BA)
                                            .enable();
        for (int ii = 0; ii < 120; ii++)
        {
            PressureAndTemperature pressureAndTemperature = sensor.read();
            System.out.println("pressure   : " + org.omnaest.utils.NumberUtils.formatter()
                                                                              .withMaximumFractionDigits(2)
                                                                              .format(pressureAndTemperature.getPressureRelative())
                               + "/" + org.omnaest.utils.NumberUtils.formatter()
                                                                    .withMaximumFractionDigits(2)
                                                                    .format(pressureAndTemperature.getPressureAbsolute())
                               + " (" + pressureAndTemperature.getTemperature() + "°C)");

            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
        sensor.disable();
    }

    @Test
    @Ignore
    public void testHX711WeightSensor()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.187", 8080);

        int clockPort = 14;
        int dataPort = 13;
        long baseValue = client.weightSensorHX711()
                               .usingGain(Gain.CHANNEL_A_NORMAL)
                               .usingClockPort(clockPort)
                               .usingDataPort(dataPort)
                               .readValue();

        for (int ii = 0; ii < 120; ii++)
        {
            long value = client.weightSensorHX711()
                               .usingGain(Gain.CHANNEL_A_NORMAL)
                               .usingClockPort(clockPort)
                               .usingDataPort(dataPort)
                               .readValue()
                         - baseValue;

            System.out.println(value + " (" + baseValue + ")");

            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @Ignore
    public void testNAU7802WeightSensor()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.187", 8080);

        //        client.weightSensorNAU7802()
        //              .usingBus(1)
        //              .calibrate();

        double maxWeightValue = 200000;

        int baseValue = client.weightSensorNAU7802()
                              .usingBus(1)
                              .readValue();

        for (int ii = 0; ii < 120; ii++)
        {
            int value = client.weightSensorNAU7802()
                              .usingBus(1)
                              .readValue()
                        - baseValue;

            System.out.println(org.omnaest.utils.NumberUtils.formatter()
                                                            .asPercentage()
                                                            .format(1.0 * value / maxWeightValue)
                               + " - " + value + " (" + baseValue + ")");

            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @Ignore
    public void testLPS28PressureSensor()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.187", 8080);

        double maxPressureValue = 1000;

        double baseValue = client.pressureSensorLPS28()
                                 .usingBus(1)
                                 .readPressure();

        for (int ii = 0; ii < 120; ii++)
        {
            double rawPressure = client.pressureSensorLPS28()
                                       .usingBus(1)
                                       .readPressure();
            double value = rawPressure - baseValue;

            System.out.println(org.omnaest.utils.NumberUtils.formatter()
                                                            .asPercentage()
                                                            .format(1.0 * value / maxPressureValue)
                               + " - " + rawPressure + " -> " + value + " (" + baseValue + ")");

            double temperature = client.pressureSensorLPS28()
                                       .usingBus(1)
                                       .readTemperature();
            System.out.println("Temperature: " + temperature);

            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @Ignore
    public void testGPIOPortOfHX711() throws Exception
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.187", 8080);

        int clockPort = 14;
        client.enableGPIOForDigitalOutput(clockPort);

        int dataPort = 13;
        client.enableGPIOForDigitalOutput(dataPort);

        for (int ii = 0; ii < 50; ii++)
        {
            client.setStateOfDigitalGPIOPort(clockPort, true);
            client.setStateOfDigitalGPIOPort(dataPort, true);
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
            client.setStateOfDigitalGPIOPort(clockPort, false);
            client.setStateOfDigitalGPIOPort(dataPort, false);
            ThreadUtils.sleepSilently(1, TimeUnit.SECONDS);
        }

    }

    @Test
    @Ignore
    public void testPCA9685ServoPin()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        ServoPinControl pin = client.forServoPin(10);

        for (int ii = 0; ii < 120; ii++)
        {
            pin.enable();
            System.out.println("enabled");

            ThreadUtils.sleepSilently(3, TimeUnit.SECONDS);

            pin.disable();
            System.out.println("disabled");

            ThreadUtils.sleepSilently(3, TimeUnit.SECONDS);
        }

    }

    @Test
    @Ignore
    public void testPCA9685ServoPwmPin()
    {
        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);

        ServoPinControl pin = client.forServoPin(10);

        for (int ii = 0; ii < 120; ii++)
        {
            Stream.of(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
                  .forEach(value ->
                  {
                      pin.setPwmValue(value);
                      System.out.println("Set pwm value: " + value);
                      ThreadUtils.sleepSilently(3, TimeUnit.SECONDS);
                  });
        }

    }

    @Test
    @Ignore
    public void testPCA9685ServoPwmPinWithPumpControl()
    {
        //        PiClient client = PIRemoteClient.newInstance("192.168.0.123", 8080);
        PiClient client = PIRemoteClient.newInstance("192.168.0.187", 8080);

        // PWM, in1,in2
        //0,1,2,3
        //4,5,6,7
        ServoPinControl pwmPin = client.forServoPin(4);
        ServoPinControl pinForward = client.forServoPin(5);
        ServoPinControl pinBackward = client.forServoPin(6);

        for (int ii = 0; ii < 120; ii++)
        {
            System.out.println("Disabled");
            pinForward.disable();
            pinBackward.disable();
            pwmPin.setPwmValue(0);

            ThreadUtils.sleepSilently(3, TimeUnit.SECONDS);

            if (ii % 2 == 0)
            {
                pinForward.enable();
                System.out.println("Enabled (forwards)");
            }
            else
            {
                pinBackward.enable();
                System.out.println("Enabled (backwards)");
            }

            Stream.of(0.0, 0.3, 0.4, 0.5)
                  .forEach(value ->
                  {
                      pwmPin.setPwmValue(value);
                      System.out.println("Set pwm value: " + value);
                      ThreadUtils.sleepSilently(3, TimeUnit.SECONDS);
                  });
        }

    }
}
