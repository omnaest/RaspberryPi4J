package org.omnaest.pi.client;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.omnaest.pi.client.domain.gyro.Orientation;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;
import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;

public interface PiClient
{

    RotaryEncoder forRotaryEncoder();

    Compass forCompass();

    BMP180 forBMP180();

    GyroscopeGY521 forGyroscopeGY521();

    ServoControl forServo(int index, int bus);

    ServoControl forServo(int index);

    UltrasonicSensorControl forUltrasonicSensor(int index);

    void setValueOfPWMGPIOPort(int pin, int value);

    void enableGPIOForPWMOutput(int pin);

    void setStateOfDigitalGPIOPort(int pin, boolean active);

    void enableGPIOForDigitalOutput(int pin);

    DigitalPortControl forDigitalPort(int pin);

    I2CAccess forI2C();

    Motor motor();

    DisabledFlowSensor flowSensor(int pin);

    DisabledPressureSensorMS5837 pressureSensorMS5837(MS5837Model model);

    public static interface DisabledPressureSensorMS5837
    {
        public PressureSensorMS5837 enable();
    }

    public static interface PressureSensorMS5837
    {
        public PressureAndTemperature read();

        public DisabledPressureSensorMS5837 disable();
    }

    public static interface Motor
    {
        public Motor defineL298nAt(int fowardPin, int backwardPin, int pwmPin);

        /**
         * @param direction
         * @param speed
         *            0.0 to 1.0
         * @return
         */
        public Motor move(MotorMovementDirection direction, double speed);

    }

    public static interface DigitalPortControl
    {
        public boolean getState();

        public void setState(boolean state);
    }

    public static interface UltrasonicSensorControl
    {

        /**
         * Similar to {@link #init(UltrasonicSensorConfiguration)}
         * 
         * @param configurationConsumer
         * @return
         */
        public UltrasonicSensorControl init(Consumer<UltrasonicSensorConfiguration> configurationConsumer);

        /**
         * Returns the distance in millimeters
         * 
         * @return
         */
        public double getDistance();
    }

    public static interface ServoControl
    {
        public ServoControl setAngle(int angle);

        public ServoControl applySpeed(double speed);

        public ServoControl setDurationMinimum(int min);

        public ServoControl setDurationMaximum(int max);

        public ServoControl setDurationNeutral(int neutral);
    }

    public static interface BMP180
    {
        public Temperature getTemperature();

        public BMP180Measurement getMeasurement();
    }

    public static interface Compass
    {
        public Compass usingModule(Module module);

        public Compass usingBus(int bus);

        public int getNorthDirectionAngleClockwise();

        public static enum Module
        {
            QMC5883L, HMC5883
        }
    }

    public static interface GyroscopeGY521
    {
        public GyroscopeGY521 withNumberOfSamplings(int numberOfSamplings);

        public Orientation getOrientation();
    }

    public static interface RotaryEncoder extends LongSupplier
    {
        public RotaryEncoder withClkPort(int port);

        public RotaryEncoder withDtPort(int port);

        public RotaryEncoder withSwPort(int port);

        /**
         * Returns the encoder value
         * 
         * @return
         */
        @Override
        public long getAsLong();
    }

    public static interface UltrasonicSensorConfiguration
    {

        public UltrasonicSensorConfiguration setSignalTimeout(long signalTimeout);

        public UltrasonicSensorConfiguration setPingTimeout(long pingTimeout);

        public UltrasonicSensorConfiguration setTriggerPort(int triggerPort);

        public UltrasonicSensorConfiguration setEchoPort(int echoPort);

        public UltrasonicSensorConfiguration setSignals(int[] signals);

    }

    public static interface Temperature
    {

        public double getTemperatureFahrenheit();

        public double getTemperatureCelsius();

    }

    public static interface BMP180Measurement
    {

        public Temperature getTemperature();

        public double getPressure();

        public double getAltitude();

    }

    public static interface I2CAccess
    {
        public I2CBusAccess onBus(int bus);
    }

    public static interface I2CBusAccess
    {
        public byte readByte(int deviceAddress, int localAddress, int offset);

        public void writeByte(int deviceAddress, int localAddress, int offset, byte value);
    }

    public static interface DisabledFlowSensor
    {
        public DisabledFlowSensor withFlowRateCoefficient(double flowRateCoefficient);

        /**
         * Enables
         * 
         * @see #withFlowRateCoefficient(double)
         * @return
         */
        public FlowSensor enable();
    }

    public static interface FlowSensor
    {

        public double getFlowRate();

        public DisabledFlowSensor disable();

    }

}
