package org.omnaest.pi.client;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.omnaest.pi.client.domain.flow.FlowSensorDefinition;
import org.omnaest.pi.client.domain.gyro.Orientation;
import org.omnaest.pi.client.domain.motor.L298nMotorControlDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDirection;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;
import org.omnaest.utils.JSONHelper;
import org.omnaest.utils.rest.client.RestHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Remote {@link PiClient} implementation that utilizes REST communication to invoke the calls.
 * 
 * @author omnaest
 */
public class PIRemoteClient implements PiClient
{
    private String host;
    private int    port;

    private PIRemoteClient(String host, int port)
    {
        super();
        this.host = host;
        this.port = port;
    }

    public static PiClient newInstance(String host, int port)
    {
        return new PIRemoteClient(host, port);
    }

    @Override
    public DigitalPortControl forDigitalPort(int pin)
    {
        String host = this.host;
        int port = this.port;

        return new DigitalPortControl()
        {
            @Override
            public void setState(boolean state)
            {
                String url = "http://" + host + ":" + port + "/gpio/" + pin + "/digital/output";
                RestHelper.requestPut(url, "" + state);
            }

            @Override
            public boolean getState()
            {
                String url = "http://" + host + ":" + port + "/gpio/" + pin + "/digital/input";
                return BooleanUtils.toBoolean(RestHelper.requestGet(url));
            }
        };
    }

    @Override
    public void enableGPIOForDigitalOutput(int pin)
    {
        String url = "http://" + this.host + ":" + this.port + "/gpio/" + pin + "/digital/output/enable";
        RestHelper.requestPut(url, "");
    }

    @Override
    public void setStateOfDigitalGPIOPort(int pin, boolean active)
    {
        String url = "http://" + this.host + ":" + this.port + "/gpio/" + pin + "/state/" + active;
        RestHelper.requestPut(url, "");
    }

    @Override
    public void enableGPIOForPWMOutput(int pin)
    {
        String url = "http://" + this.host + ":" + this.port + "/gpio/" + pin + "/digital/output";
        RestHelper.requestPut(url, "");
    }

    @Override
    public void setValueOfPWMGPIOPort(int pin, int value)
    {
        String url = "http://" + this.host + ":" + this.port + "/gpio/" + pin + "/pwm/" + value;
        RestHelper.requestPut(url, "");
    }

    @Override
    public UltrasonicSensorControl forUltrasonicSensor(int index)
    {
        return new UltrasonicSensorControl()
        {
            private UltrasonicSensorControl init(UltrasonicSensorConfigurationImpl configuration)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/ultrasonic/" + index;
                RestHelper.requestPut(url, JSONHelper.prettyPrint(configuration));
                return this;
            }

            @Override
            public UltrasonicSensorControl init(Consumer<UltrasonicSensorConfiguration> configurationConsumer)
            {
                UltrasonicSensorConfigurationImpl configuration = new UltrasonicSensorConfigurationImpl();
                configurationConsumer.accept(configuration);
                return this.init(configuration);
            }

            @Override
            public double getDistance()
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/ultrasonic/" + index + "/distance";
                return NumberUtils.toDouble(RestHelper.requestGet(url));
            }
        };
    }

    @Override
    public ServoControl forServo(int index)
    {
        int bus = 1;
        return this.forServo(index, bus);
    }

    @Override
    public ServoControl forServo(int index, int bus)
    {
        return new ServoControl()
        {
            @Override
            public ServoControl setAngle(int angle)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/servo/" + bus + "/" + index + "/angle";
                RestHelper.requestPut(url, "" + angle);
                return this;
            }

            @Override
            public ServoControl setDurationMaximum(int max)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/servo/" + bus + "/" + index + "/state/maximum";
                RestHelper.requestPut(url, "" + max);
                return this;
            }

            @Override
            public ServoControl setDurationMinimum(int min)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/servo/" + bus + "/" + index + "/state/minimum";
                RestHelper.requestPut(url, "" + min);
                return this;
            }

            @Override
            public ServoControl applySpeed(double speed)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/servo/" + bus + "/" + index + "/speed";
                RestHelper.requestPut(url, "" + speed);
                return this;
            }

            @Override
            public ServoControl setDurationNeutral(int neutral)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/servo/" + bus + "/" + index + "/state/neutral";
                RestHelper.requestPut(url, "" + neutral);
                return this;
            }
        };
    }

    @Override
    public BMP180 forBMP180()
    {
        return new BMP180()
        {
            @Override
            public BMP180MeasurementImpl getMeasurement()
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/bmp180";
                return JSONHelper.readFromString(RestHelper.requestGet(url), BMP180MeasurementImpl.class);
            }

            @Override
            public Temperature getTemperature()
            {
                return this.getMeasurement()
                           .getTemperature();
            }
        };
    }

    @Override
    public Compass forCompass()
    {
        return new Compass()
        {
            private Module module = Module.QMC5883L;
            private int    bus    = 1;

            @Override
            public int getNorthDirectionAngleClockwise()
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/compass/i2c/direction?module=" + this.module
                        + "&bus=" + this.bus;
                return NumberUtils.toInt(RestHelper.requestGet(url));
            }

            @Override
            public Compass usingModule(Module module)
            {
                this.module = module;
                return this;
            }

            @Override
            public Compass usingBus(int bus)
            {
                this.bus = bus;
                return this;
            }
        };
    }

    @Override
    public RotaryEncoder forRotaryEncoder()
    {
        return new RotaryEncoder()
        {
            private int clkPort;
            private int dtPort;
            private int swPort = -1;

            @Override
            public RotaryEncoder withClkPort(int clkPort)
            {
                this.clkPort = clkPort;
                return this;
            }

            @Override
            public RotaryEncoder withDtPort(int dtPort)
            {
                this.dtPort = dtPort;
                return this;
            }

            @Override
            public RotaryEncoder withSwPort(int swPort)
            {
                this.swPort = swPort;
                return this;
            }

            @Override
            public long getAsLong()
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/rotaryencoder/pin?clkPort=" + this.clkPort
                        + "&dtPort=" + this.dtPort + "&swPort=" + this.swPort;
                return NumberUtils.toLong(RestHelper.requestGet(url));
            }
        };
    }

    public static class UltrasonicSensorConfigurationImpl implements UltrasonicSensorConfiguration
    {
        @JsonProperty
        private int echoPort;

        @JsonProperty
        private int triggerPort;

        @JsonProperty
        private long pingTimeout = 30000000;

        @JsonProperty
        private long signalTimeout = 30000000;

        @JsonProperty
        private int[] signals = new int[] { 3, 10, 0 };

        public UltrasonicSensorConfigurationImpl()
        {
            super();
        }

        public int[] getSignals()
        {
            return this.signals;
        }

        @Override
        public UltrasonicSensorConfigurationImpl setSignals(int[] signals)
        {
            this.signals = signals;
            return this;
        }

        public int getEchoPort()
        {
            return this.echoPort;
        }

        public int getTriggerPort()
        {
            return this.triggerPort;
        }

        public long getPingTimeout()
        {
            return this.pingTimeout;
        }

        public long getSignalTimeout()
        {
            return this.signalTimeout;
        }

        @Override
        public UltrasonicSensorConfigurationImpl setEchoPort(int echoPort)
        {
            this.echoPort = echoPort;
            return this;
        }

        @Override
        public UltrasonicSensorConfigurationImpl setTriggerPort(int triggerPort)
        {
            this.triggerPort = triggerPort;
            return this;
        }

        @Override
        public UltrasonicSensorConfigurationImpl setPingTimeout(long pingTimeout)
        {
            this.pingTimeout = pingTimeout;
            return this;
        }

        @Override
        public UltrasonicSensorConfigurationImpl setSignalTimeout(long signalTimeout)
        {
            this.signalTimeout = signalTimeout;
            return this;
        }

        @Override
        public String toString()
        {
            return "UltrasonicSensorConfiguration [echoPort=" + this.echoPort + ", triggerPort=" + this.triggerPort + ", pingTimeout=" + this.pingTimeout
                    + ", signalTimeout=" + this.signalTimeout + ", signals=" + Arrays.toString(this.signals) + "]";
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemperatureImpl implements Temperature
    {
        @JsonProperty
        private double temperatureCelsius;

        private TemperatureImpl()
        {
            super();
        }

        public TemperatureImpl(double temperatureCelsius)
        {
            super();
            this.temperatureCelsius = temperatureCelsius;
        }

        @Override
        public double getTemperatureCelsius()
        {
            return this.temperatureCelsius;
        }

        @Override
        public double getTemperatureFahrenheit()
        {
            return this.temperatureCelsius * 1.8 + 32;
        }

        @Override
        public String toString()
        {
            return "Temperature [temperatureCelsius=" + this.temperatureCelsius + "]";
        }
    }

    public static class BMP180MeasurementImpl implements BMP180Measurement
    {
        @JsonProperty
        private double altitude;

        @JsonProperty
        private double pressure;

        @JsonProperty
        private Temperature temperature;

        private BMP180MeasurementImpl()
        {
            super();
        }

        public BMP180MeasurementImpl(double altitude, double pressure, Temperature temperature)
        {
            super();
            this.altitude = altitude;
            this.pressure = pressure;
            this.temperature = temperature;
        }

        @Override
        public double getAltitude()
        {
            return this.altitude;
        }

        @Override
        public double getPressure()
        {
            return this.pressure;
        }

        @Override
        public Temperature getTemperature()
        {
            return this.temperature;
        }

        @Override
        public String toString()
        {
            return "Measurement [altitude=" + this.altitude + ", pressure=" + this.pressure + ", temperature=" + this.temperature + "]";
        }

    }

    @Override
    public GyroscopeGY521 forGyroscopeGY521()
    {
        return new GyroscopeGY521()
        {
            private int numberOfSamplings = 1;

            @Override
            public GyroscopeGY521 withNumberOfSamplings(int numberOfSamplings)
            {
                this.numberOfSamplings = numberOfSamplings;
                return this;
            }

            @Override
            public Orientation getOrientation()
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/gyroscope/i2c/orientation?numberOfSamplings="
                        + this.numberOfSamplings;
                return JSONHelper.readFromString(RestHelper.requestGet(url), Orientation.class);
            }
        };
    }

    @Override
    public I2CAccess forI2C()
    {
        return new I2CAccess()
        {
            @Override
            public I2CBusAccess onBus(int bus)
            {
                return new I2CBusAccess()
                {

                    @Override
                    public byte readByte(int deviceAddress, int localAddress, int offset)
                    {
                        String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/i2c/bus/" + bus + "/address/" + deviceAddress
                                + "/" + localAddress + "/offset/" + offset;
                        return Optional.ofNullable(RestHelper.requestGet(url))
                                       .map(Byte::valueOf)
                                       .orElse((byte) 0);
                    }

                    @Override
                    public void writeByte(int deviceAddress, int localAddress, int offset, byte value)
                    {
                        String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/i2c/bus/" + bus + "/address/" + deviceAddress
                                + "/" + localAddress;
                        RestHelper.requestPut(url, String.valueOf(value));
                    }
                };
            }
        };
    }

    @Override
    public Motor motor()
    {
        return new Motor()
        {
            private String id;

            @Override
            public Motor defineL298nAt(int fowardPin, int backwardPin, int pwmPin)
            {
                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/motor";
                this.id = RestHelper.requestPut(url, JSONHelper.prettyPrint(new L298nMotorControlDefinition(fowardPin, backwardPin, pwmPin)));
                return this;
            }

            @Override
            public Motor move(MotorMovementDirection direction, double speed)
            {
                if (this.id == null)
                {
                    throw new IllegalArgumentException("Please define the motor first.");
                }

                String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/motor/" + this.id + "/movement";

                RestHelper.requestPut(url, JSONHelper.prettyPrint(new MotorMovementDefinition(direction, speed)));

                return this;
            }

        };
    }

    @Override
    public DisabledFlowSensor flowSensor(int signalPin)
    {
        return new FlowSensorImpl(signalPin);
    }

    @Override
    public DisabledPressureSensorMS5837 pressureSensorMS5837()
    {
        return new PressureSensorMS5837Impl();
    }

    private class PressureSensorMS5837Impl implements DisabledPressureSensorMS5837, PressureSensorMS5837
    {
        private String sensorId;

        @Override
        public PressureSensorMS5837 enable()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/pressure/MS5837";
            this.sensorId = RestHelper.requestPost(url, "");
            return this;
        }

        @Override
        public PressureAndTemperature read()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/pressure/MS5837/" + this.sensorId;
            return Optional.ofNullable(RestHelper.requestGet(url))
                           .filter(StringUtils::isNotBlank)
                           .map(json -> JSONHelper.readFromString(json, PressureAndTemperature.class))
                           .orElse(PressureAndTemperature.builder()
                                                         .build());
        }

        @Override
        public DisabledPressureSensorMS5837 disable()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/pressure/MS5837/" + this.sensorId;
            RestHelper.requestDelete(url);
            return this;
        }
    }

    private class FlowSensorImpl implements FlowSensor, DisabledFlowSensor
    {
        private final int signalPin;
        private double    flowRateCoefficient = 7.5;

        private FlowSensorImpl(int signalPin)
        {
            this.signalPin = signalPin;
        }

        @Override
        public DisabledFlowSensor withFlowRateCoefficient(double flowRateCoefficient)
        {
            this.flowRateCoefficient = flowRateCoefficient;
            return this;
        }

        @Override
        public FlowSensor enable()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/flow/" + this.signalPin;
            RestHelper.requestPut(url, JSONHelper.prettyPrint(FlowSensorDefinition.builder()
                                                                                  .flowRateCoefficient(this.flowRateCoefficient)
                                                                                  .build()));
            return this;
        }

        @Override
        public DisabledFlowSensor disable()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/flow/" + this.signalPin;
            RestHelper.requestDelete(url);
            return this;
        }

        @Override
        public double getFlowRate()
        {
            String url = "http://" + PIRemoteClient.this.host + ":" + PIRemoteClient.this.port + "/sensor/flow/" + this.signalPin;
            return Optional.ofNullable(RestHelper.requestGet(url))
                           .map(NumberUtils::toDouble)
                           .orElse(0.0);
        }
    }
}
