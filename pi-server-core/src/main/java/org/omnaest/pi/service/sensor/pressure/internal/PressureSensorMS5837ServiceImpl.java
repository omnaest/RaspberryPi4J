package org.omnaest.pi.service.sensor.pressure.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.omnaest.pi.client.domain.pressure.MS5837Model;
import org.omnaest.pi.client.domain.pressure.PressureAndTemperature;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.BusNumber;
import org.omnaest.pi.service.i2c.I2CService.I2CBusControl;
import org.omnaest.pi.service.sensor.pressure.PressureSensorMS5837Service;
import org.omnaest.utils.ThreadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PressureSensorMS5837ServiceImpl implements PressureSensorMS5837Service
{
    @Autowired
    private I2CService i2cService;

    private final Map<String, PressureSensorContext> sensorIdToContext = new ConcurrentHashMap<>();

    @Override
    public String enableSensorAndGetSensorId(MS5837Model model)
    {
        I2CBusControl busControl = this.i2cService.provision(BusNumber.BUS_1)
                                                  .orElseThrow(() -> new IllegalStateException("Unable to provision I2C bus for pressure sensor"));
        AddressConnector address = busControl.connectTo(0x76)
                                             .orElseThrow(() -> new IllegalArgumentException("Unable to connect to pressure sensor address via I2C"));

        double initialPressure = this.readPressureAndTemperature(address, 0, model)
                                     .getPressureAbsolute();
        PressureSensorContext context = PressureSensorContext.builder()
                                                             .address(address)
                                                             .initalPressure(initialPressure)
                                                             .model(model)
                                                             .build();

        String sensorId = UUID.randomUUID()
                              .toString();
        this.sensorIdToContext.put(sensorId, context);

        return sensorId;
    }

    private Function<Long, PressureAndTemperature> createPressureAndTemperatureFunction(AddressConnector address, double initalPressure, MS5837Model model)
    {
        address.write((byte) 0x1E);
        ThreadUtils.sleepSilently(20, TimeUnit.MILLISECONDS);

        int C1 = address.read(0xA2, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C1"))
                        .asIntFromMsbToLsb(0);
        int C2 = address.read(0xA4, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C2"))
                        .asIntFromMsbToLsb(0);
        int C3 = address.read(0xA6, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C3"))
                        .asIntFromMsbToLsb(0);
        int C4 = address.read(0xA8, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C4"))
                        .asIntFromMsbToLsb(0);
        int C5 = address.read(0xAA, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C5"))
                        .asIntFromMsbToLsb(0);
        int C6 = address.read(0xAC, 0, 2)
                        .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for C6"))
                        .asIntFromMsbToLsb(0);

        address.write((byte) 0x40);
        ThreadUtils.sleepSilently(20, TimeUnit.MILLISECONDS);

        long D1 = address.read(0x00, 0, 3)
                         .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for D1"))
                         .asLongFromMsbToLsb(0, 2);

        address.write((byte) 0x50);
        ThreadUtils.sleepSilently(20, TimeUnit.MILLISECONDS);
        return D2 ->
        {
            if (MS5837Model.MS5837_02BA.equals(model))
            {
                long dT = D2 - C5 * 256;
                long TEMP = 2000 + dT * C6 / 8388608l;
                long OFF = C2 * 131072l + (C4 * dT) / 64l;
                long SENS = C1 * 65536l + (C3 * dT) / 128l;
                long T2 = 0;
                long OFF2 = 0;
                long SENS2 = 0;

                if (TEMP < 2000)
                {
                    T2 = 11 * (dT * dT) / 34359738368L;
                    OFF2 = 31 * (TEMP - 2000) ^ 2 / 8;
                    SENS2 = 63 * (TEMP - 2000) ^ 2 / 32;
                }

                TEMP = TEMP - T2;
                OFF = OFF - OFF2;
                SENS = SENS - SENS2;
                double pressureAbsolute = (((D1 * SENS) / 2097152) - OFF) / 32768 / 100.0;
                double temperature = TEMP / 100.0;

                double pressureRelative = pressureAbsolute - initalPressure;
                return PressureAndTemperature.builder()
                                             .pressureAbsolute(pressureAbsolute)
                                             .pressureRelative(pressureRelative)
                                             .temperature(temperature)
                                             .build();
            }
            else
            {
                long dT = D2 - C5 * 256;
                long TEMP = 2000 + dT * C6 / 8388608l;
                long OFF = C2 * 65536l + (C4 * dT) / 128l;
                long SENS = C1 * 32768l + (C3 * dT) / 256l;
                long T2 = 0;
                long OFF2 = 0;
                long SENS2 = 0;

                if (TEMP >= 2000)
                {
                    T2 = 2 * (dT * dT) / 137438953472l;
                    OFF2 = ((TEMP - 2000) * (TEMP - 2000)) / 16;
                    SENS2 = 0;
                }
                else if (TEMP < 2000)
                {
                    T2 = 3 * (dT * dT) / 8589934592l;
                    OFF2 = 3 * ((TEMP - 2000) * (TEMP - 2000)) / 2;
                    SENS2 = 5 * ((TEMP - 2000) * (TEMP - 2000)) / 8;
                    if (TEMP < -1500)
                    {
                        OFF2 = OFF2 + 7 * ((TEMP + 1500) * (TEMP + 1500));
                        SENS2 = SENS2 + 4 * ((TEMP + 1500) * (TEMP + 1500));
                    }
                }

                TEMP = TEMP - T2;
                OFF = OFF - OFF2;
                SENS = SENS - SENS2;
                double pressureAbsolute = (((D1 * SENS) / 2097152) - OFF) / 8192 / 10.0;
                double temperature = TEMP / 100.0;

                double pressureRelative = pressureAbsolute - initalPressure;
                return PressureAndTemperature.builder()
                                             .pressureAbsolute(pressureAbsolute)
                                             .pressureRelative(pressureRelative)
                                             .temperature(temperature)
                                             .build();
            }
        };
    }

    @Override
    public void disableSensor(String sensorId)
    {
        this.sensorIdToContext.remove(sensorId);
    }

    @Override
    public Optional<PressureAndTemperature> readSensor(String sensorId)
    {
        return Optional.ofNullable(this.sensorIdToContext.get(sensorId))
                       .map(context -> this.readPressureAndTemperature(context.getAddress(), context.getInitalPressure(), context.getModel()));
    }

    private PressureAndTemperature readPressureAndTemperature(AddressConnector address, double initalPressure, MS5837Model model)
    {
        Function<Long, PressureAndTemperature> pressureAndTemperatureFunction = this.createPressureAndTemperatureFunction(address, initalPressure, model);

        long D2 = address.read(0x00, 0, 3)
                         .orElseThrow(() -> new IllegalStateException("Unable read pressure sensor I2C address for D2"))
                         .asLongFromMsbToLsb(0, 2);

        PressureAndTemperature pressureAndTemperature = pressureAndTemperatureFunction.apply(D2);

        log.info("Pressure (absolute)    : " + pressureAndTemperature.getPressureAbsolute() + " mbar");
        log.info("Pressure (relative)    : " + pressureAndTemperature.getPressureRelative() + " mbar");
        log.info("Temperature            : " + pressureAndTemperature.getTemperature() + " C");
        return pressureAndTemperature;
    }

    @Data
    @Builder
    private static class PressureSensorContext
    {
        private final AddressConnector address;
        private final double           initalPressure;
        private final MS5837Model      model;
    }
}
