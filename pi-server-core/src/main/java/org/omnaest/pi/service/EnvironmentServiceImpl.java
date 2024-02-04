package org.omnaest.pi.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.omnaest.pi.domain.BMP180Measurement;
import org.omnaest.pi.domain.Temperature;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.BusNumber;
import org.omnaest.pi.service.i2c.I2CService.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentServiceImpl implements EnvironmentService
{
    @Autowired
    private I2CService i2cService;

    private AtomicReference<BMP180Sensor> bmp180Sensor = new AtomicReference<>();

    @Override
    public Optional<BMP180Sensor> getOrCreateBMP180SensorInstance()
    {
        Supplier<BMP180Sensor> supplier = () -> this.i2cService.provision(BusNumber.BUS_1)
                                                               .flatMap(bus -> bus.connectTo(0x77))
                                                               .map(BPM180Device::new)
                                                               .flatMap(BPM180Device::calibrate)
                                                               .map(device -> new BMP180Sensor()
                                                               {
                                                                   @Override
                                                                   public Optional<BMP180Measurement> measure()
                                                                   {
                                                                       return device.measure();
                                                                   }
                                                               })
                                                               .orElse(null);
        return Optional.ofNullable(this.bmp180Sensor.updateAndGet(instance ->
        {
            if (instance != null)
            {
                return instance;
            }
            else
            {
                return supplier.get();
            }
        }));
    }

    private static class BPM180Device
    {
        private static final Logger LOG = LoggerFactory.getLogger(BPM180Device.class);

        private AddressConnector addressConnector;

        private int AC1;
        private int AC2;
        private int AC3;
        private int AC4;
        private int AC5;
        private int AC6;
        private int B1;
        private int B2;
        private int MB;
        private int MC;
        private int MD;

        public BPM180Device(AddressConnector addressConnector)
        {
            super();
            this.addressConnector = addressConnector;
        }

        public Optional<BPM180Device> calibrate()
        {
            return this.addressConnector.read(0xAA, 0, 22)
                                        .map(ByteArray::get)
                                        .map(data ->
                                        {
                                            this.AC1 = data[0] * 256 + data[1];
                                            this.AC2 = data[2] * 256 + data[3];
                                            this.AC3 = data[4] * 256 + data[5];
                                            this.AC4 = ((data[6] & 0xFF) * 256) + (data[7] & 0xFF);
                                            this.AC5 = ((data[8] & 0xFF) * 256) + (data[9] & 0xFF);
                                            this.AC6 = ((data[10] & 0xFF) * 256) + (data[11] & 0xFF);
                                            this.B1 = data[12] * 256 + data[13];
                                            this.B2 = data[14] * 256 + data[15];
                                            this.MB = data[16] * 256 + data[17];
                                            this.MC = data[18] * 256 + data[19];
                                            this.MD = data[20] * 256 + data[21];

                                            try
                                            {
                                                Thread.sleep(500);
                                            }
                                            catch (InterruptedException e)
                                            {
                                                //
                                            }

                                            return this;
                                        });
        }

        public Optional<BMP180Measurement> measure()
        {
            try
            {
                // Select measurement control register
                // Enable temperature measurement
                this.addressConnector.write(0xF4, (byte) 0x2E);
                Thread.sleep(100);

                byte[] temperatureData = this.addressConnector.read(0xF6, 0, 2)
                                                              .get()
                                                              .get();
                int temp = ((temperatureData[0] & 0xFF) * 256 + (temperatureData[1] & 0xFF));

                // Select measurement control register
                // Enable pressure measurement, OSS = 1
                this.addressConnector.write(0xF4, (byte) 0x74);
                Thread.sleep(100);

                // Read 3 bytes of data from address 0xF6(246)
                // pres msb1, pres msb, pres lsb
                byte[] pressureData = this.addressConnector.read(0xF6, 0, 3)
                                                           .get()
                                                           .get();
                double rawPressure = (((pressureData[0] & 0xFF) * 65536) + ((pressureData[1] & 0xFF) * 256) + (pressureData[2] & 0xFF)) / 128;

                // Callibration for Temperature
                double X1 = (temp - this.AC6) * this.AC5 / 32768.0;
                double X2 = (this.MC * 2048.0) / (X1 + this.MD);
                double B5 = X1 + X2;
                double temperatureCelsius = ((B5 + 8.0) / 16.0) / 10.0;

                // Calibration for Pressure
                double B6 = B5 - 4000;
                X1 = (this.B2 * (B6 * B6 / 4096.0)) / 2048.0;
                X2 = this.AC2 * B6 / 2048.0;
                double X3 = X1 + X2;
                double B3 = (((this.AC1 * 4 + X3) * 2) + 2) / 4.0;
                X1 = this.AC3 * B6 / 8192.0;
                X2 = (this.B1 * (B6 * B6 / 2048.0)) / 65536.0;
                X3 = ((X1 + X2) + 2) / 4.0;
                double B4 = this.AC4 * (X3 + 32768) / 32768.0;
                double B7 = ((rawPressure - B3) * (25000.0));

                double pressure = 0.0;
                if (B7 < 2147483648L)
                {
                    pressure = (B7 * 2) / B4;
                }
                else
                {
                    pressure = (B7 / B4) * 2;
                }
                X1 = (pressure / 256.0) * (pressure / 256.0);
                X1 = (X1 * 3038.0) / 65536.0;
                X2 = ((-7357) * pressure) / 65536.0;
                pressure = (pressure + (X1 + X2 + 3791) / 16.0) / 100;

                // Calculate Altitude
                double altitude = 44330 * (1 - Math.pow((pressure / 1013.25), 0.1903));

                Temperature temperature = new Temperature(temperatureCelsius);
                return Optional.of(new BMP180Measurement(altitude, pressure, temperature));
            }
            catch (Exception e)
            {
                LOG.error("Failed to read data from BPM180", e);
                return Optional.empty();
            }

        }

    }
}
