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
package org.omnaest.pi.controller;

import java.util.Optional;

import org.omnaest.pi.client.domain.flow.FlowSensorDefinition;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderAddress;
import org.omnaest.pi.client.domain.gpio.expander.GpioPortExpanderPort;
import org.omnaest.pi.client.domain.gyro.Orientation;
import org.omnaest.pi.client.domain.motor.L298nMotorControlDefinition;
import org.omnaest.pi.client.domain.motor.MotorMovementDefinition;
import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;
import org.omnaest.pi.domain.BMP180Measurement;
import org.omnaest.pi.domain.CameraSnapshot;
import org.omnaest.pi.domain.CameraSnapshotOptions;
import org.omnaest.pi.domain.UltrasonicSensorConfiguration;
import org.omnaest.pi.service.CameraService;
import org.omnaest.pi.service.EnvironmentService;
import org.omnaest.pi.service.UltrasonicService;
import org.omnaest.pi.service.compass.CompassService;
import org.omnaest.pi.service.compass.CompassService.Module;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.expander.GpioPortExpanderPCF8574Service;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.omnaest.pi.service.motor.MotorControlService;
import org.omnaest.pi.service.motor.MotorControlService.MotorControl;
import org.omnaest.pi.service.rotary.RotaryEncoderService;
import org.omnaest.pi.service.sensor.flow.FlowSensorService;
import org.omnaest.pi.service.sensor.gyro.GyroscopeService;
import org.omnaest.pi.service.sensor.pressure.PressureSensorMS5837Service;
import org.omnaest.pi.service.servo.ServoDriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController
{
    @Autowired
    private CameraService cameraService;

    @Autowired
    private GPIOService gpioService;

    @Autowired
    private ServoDriverService servoDriverService;

    @Autowired
    private UltrasonicService ultrasonicService;

    @Autowired
    private RotaryEncoderService rotaryEncoderService;

    @Autowired
    private CompassService compassService;

    @Autowired
    private GyroscopeService gyroscopeService;

    @Autowired
    private I2CService i2cService;

    @Autowired
    private GpioPortExpanderPCF8574Service gpioPortExpanderPCF8574Service;

    @Autowired
    private MotorControlService motorControlService;

    @Autowired
    private FlowSensorService flowSensorService;

    @Autowired
    private PressureSensorMS5837Service pressureSensorMS5837Service;

    @Autowired
    private EnvironmentService environmentService;

    @RequestMapping(method = RequestMethod.POST, path = "/snapshot")
    public CameraSnapshot getTemperatureData(@RequestBody CameraSnapshotOptions cameraSnapshotOptions)
    {
        return this.cameraService.takeSnapshot(cameraSnapshotOptions);
    }

    @PutMapping("/gpio/{port}/digital/output/enable")
    public void enableGPIODigitalOutput(@PathVariable("port") int port)
    {
        this.gpioService.enableGPIOPortForDigitalOutput(port);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/gpio/{port}/digital/input")
    public boolean getGPIODigitalInput(@PathVariable("port") int port)
    {
        return this.gpioService.getDigitalInputGPIOPort(port)
                               .enable()
                               .getState();
    }

    @PutMapping("/gpio/{port}/digital/output")
    public void setGPIODigitalOutput(@PathVariable("port") int port, @RequestBody boolean state)
    {
        this.gpioService.getDigitalOutputGPIOPort(port)
                        .enable()
                        .setState(state);
    }

    @PutMapping("/gpio/{port}/pwm")
    public void enableGPIOPWMOutput(@PathVariable("port") int port)
    {
        this.gpioService.enableGPIOPortForPWM(port);
    }

    @PutMapping("/gpio/{port}/state/{active}")
    public void enableGPIOPort(@PathVariable("port") int port, @PathVariable("active") boolean active)
    {
        this.gpioService.enableGPIOPort(port, active);
    }

    @PutMapping("/gpio/{port}/pwm/{value}")
    public void setGPIOPortPWMValue(@PathVariable("port") int port, @PathVariable("value") int value)
    {
        this.gpioService.setGPIOPortPWMValue(port, value);
    }

    @PutMapping("/servo/{bus}/{index}/angle")
    public void setServoAngle(@PathVariable("index") int servoIndex, @RequestBody int angle)
    {
        this.servoDriverService.servo(servoIndex)
                               .applyAngle(angle);
    }

    @PutMapping("/servo/{bus}/{index}/speed")
    public void setServoSpeed(@PathVariable("index") int servoIndex, @RequestBody double speed)
    {
        this.servoDriverService.servo(servoIndex)
                               .applySpeed(speed);
    }

    @PutMapping("/servo/{bus}/{index}/pin")
    public void enableServoPin(@PathVariable("index") int servoIndex)
    {
        this.servoDriverService.pwmPin(servoIndex)
                               .enable();
    }

    @PutMapping("/servo/{bus}/{index}/pin/pwm")
    public void setServoPwmPin(@PathVariable("index") int servoIndex, @RequestBody double value)
    {
        this.servoDriverService.pwmPin(servoIndex)
                               .setPwm(value);
    }

    @DeleteMapping("/servo/{bus}/{index}/pin")
    public void disableServoPin(@PathVariable("index") int servoIndex)
    {
        this.servoDriverService.pwmPin(servoIndex)
                               .disable();
    }

    @PutMapping("/servo/{bus}/{index}/state/maximum")
    public void setServoDurationMaximum(@PathVariable("index") int servoIndex, @RequestBody int max)
    {
        this.servoDriverService.servo(servoIndex)
                               .applyDurationMaximum(max);
    }

    @PutMapping("/servo/{bus}/{index}/state/minimum")
    public void setServoDurationMinimum(@PathVariable("index") int servoIndex, @RequestBody int min)
    {
        this.servoDriverService.servo(servoIndex)
                               .applyDurationMinimum(min);
    }

    @PutMapping("/servo/{bus}/{index}/state/neutral")
    public void setServoDurationNeutral(@PathVariable("index") int servoIndex, @RequestBody int neutral)
    {
        this.servoDriverService.servo(servoIndex)
                               .applyDurationNeutral(neutral);
    }

    @PutMapping("/sensor/ultrasonic/{index}")
    public void initUltrasonicSensor(@PathVariable("index") int index, @RequestBody UltrasonicSensorConfiguration configuration)
    {
        this.ultrasonicService.getInstance(index)
                              .init(configuration);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/sensor/ultrasonic/{index}/distance")
    public double getDistance(@PathVariable("index") int index)
    {
        return this.ultrasonicService.getInstance(index)
                                     .getDistance();
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/sensor/bmp180")
    public BMP180Measurement getBMP180TemperatureAndPressure()
    {
        return this.environmentService.getOrCreateBMP180SensorInstance()
                                      .flatMap(sensor -> sensor.measure())
                                      .get();
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/sensor/rotaryencoder/pin")
    public long getRotaryValue(@RequestParam("clkPort") int clkPort, @RequestParam("dtPort") int dtPort, @RequestParam("swPort") int swPort)
    {
        return this.rotaryEncoderService.getRotaryEncoderByPin(clkPort, dtPort, swPort)
                                        .getAsLong();
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/sensor/compass/i2c/direction")
    public int getCompassAngle(@RequestParam(name = "bus", defaultValue = "1") int bus, @RequestParam(name = "module", defaultValue = "QMC5883L") Module module)
    {
        return this.compassService.onBus(bus)
                                  .withModule(module)
                                  .getNorthDirectionAngle();
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/sensor/gyroscope/i2c/orientation")
    public Orientation getGyroscopeOrientation(@RequestParam(name = "bus", defaultValue = "1") int bus,
                                               @RequestParam(name = "numberOfSamplings", defaultValue = "1") int numberOfSamplings)
    {
        return this.gyroscopeService.getOrientation(numberOfSamplings);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE, path = "/i2c/bus/{bus}/address/{address}/{localaddress}/offset/{offset}")
    public byte getI2CData(@PathVariable(name = "bus") int bus, @RequestParam(name = "address", defaultValue = "0") int address,
                           @PathVariable(name = "localaddress") int localaddress, @PathVariable(name = "offset") int offset)
    {
        return this.i2cService.provision(bus)
                              .flatMap(control -> control.connectTo(address))
                              .flatMap(connector -> connector.read(localaddress, offset, 1))
                              .flatMap(ByteArray::getFirstByte)
                              .orElse(Byte.valueOf((byte) 0));
    }

    @PutMapping(path = "/i2c/bus/{bus}/address/{address}/{localaddress}/offset/{offset}")
    public void getI2CData(@PathVariable(name = "bus") int bus, @RequestParam(name = "address", defaultValue = "0") int address,
                           @PathVariable(name = "localaddress") int localaddress, @PathVariable(name = "offset") int offset, @RequestBody byte value)
    {
        this.i2cService.provision(bus)
                       .flatMap(control -> control.connectTo(address))
                       .ifPresent(connector -> connector.write(localaddress, value));
    }

    @PutMapping(path = "/motor")
    public String defineMotorControl(@RequestBody L298nMotorControlDefinition definition)
    {
        return this.motorControlService.defineMotorControl(definition)
                                       .getId();
    }

    @PutMapping(path = "/motor/{id}/movement")
    public void moveMotor(@PathVariable(name = "id") String id, @RequestBody MotorMovementDefinition movement)
    {
        this.motorControlService.getMotorControl(id)
                                .ifPresent(motor ->
                                {
                                    if (movement.getSpeed() > 0.001)
                                    {
                                        motor.move(movement.getDirection(), movement.getSpeed());
                                    }
                                    else
                                    {
                                        motor.stop();
                                    }
                                });
    }

    @DeleteMapping(path = "/motor/{id}/movement")
    public void stopMotor(@PathVariable(name = "id") String id)
    {
        this.motorControlService.getMotorControl(id)
                                .ifPresent(MotorControl::stop);
    }

    @PutMapping(path = "/sensor/flow/{port}")
    public void enableFlowSensor(@PathVariable(name = "port") int port, @RequestBody FlowSensorDefinition flowSensorDefinition)
    {
        this.flowSensorService.enableFlowSensor(port, flowSensorDefinition);
    }

    @GetMapping(path = "/sensor/flow/{port}")
    public double getFlowRate(@PathVariable(name = "port") int port)
    {
        return this.flowSensorService.getFlowRate(port);
    }

    @DeleteMapping(path = "/sensor/flow/{port}")
    public void disableFlowSensor(@PathVariable(name = "port") int port)
    {
        this.flowSensorService.disableFlowSensor(port);
    }

    @PostMapping(path = "/sensor/pressure/MS5837/{model}")
    public String enablePressureSensorMS5837(@PathVariable(name = "model") MS5837Model model)
    {
        return this.pressureSensorMS5837Service.enableSensorAndGetSensorId(model);
    }

    @GetMapping(path = "/sensor/pressure/MS5837/{sensorId}")
    public Optional<PressureAndTemperature> getPressureAndTemperatureFromPressureSensorMS5837(@PathVariable(name = "sensorId") String sensorId)
    {
        return this.pressureSensorMS5837Service.readSensor(sensorId);
    }

    @DeleteMapping(path = "/sensor/pressure/MS5837/{sensorId}")
    public void disablePressureSensorMS5837(@PathVariable(name = "sensorId") String sensorId)
    {
        this.pressureSensorMS5837Service.disableSensor(sensorId);
    }

    @PutMapping(path = "/gpio/expander/PCF8574/{address}/{port}")
    public void setGpioExpanderPort(@PathVariable(name = "address") GpioPortExpanderAddress address, @PathVariable(name = "port") GpioPortExpanderPort port,
                                    @RequestBody boolean value)
    {
        this.gpioPortExpanderPCF8574Service.access(address)
                                           .write(port, value);
    }

    @GetMapping(path = "/gpio/expander/PCF8574/{address}/{port}")
    public boolean getGpioExpanderPort(@PathVariable(name = "address") GpioPortExpanderAddress address, @PathVariable(name = "port") GpioPortExpanderPort port)
    {
        return this.gpioPortExpanderPCF8574Service.access(address)
                                                  .read(port);
    }

}
