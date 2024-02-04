package org.omnaest.pi.service.gyro.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.omnaest.pi.client.domain.gyro.Orientation;
import org.omnaest.pi.service.gyro.GyroscopeService;
import org.omnaest.pi.service.i2c.I2CService;
import org.omnaest.pi.service.i2c.I2CService.AddressConnector;
import org.omnaest.pi.service.i2c.I2CService.BusNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GyroscopeServiceImpl implements GyroscopeService
{
    private static final Logger LOG = LoggerFactory.getLogger(GyroscopeServiceImpl.class);

    @Autowired
    private I2CService i2cService;

    private final AtomicReference<Gyroscope> gyroscope = new AtomicReference<>();

    @Override
    public Orientation getOrientation()
    {
        int numberOfSamplings = 1;
        return this.getOrientation(numberOfSamplings);
    }

    @Override
    public Orientation getOrientation(int numberOfSamplings)
    {
        return this.gyroscope.updateAndGet(gyroscope -> gyroscope != null ? gyroscope
                : new Gyroscope(this.i2cService.provision(BusNumber.BUS_1)
                                               .flatMap(bus -> bus.connectTo(0x68))))
                             .getOrientation(numberOfSamplings)
                             .orElse(null);
    }

    private static class Gyroscope
    {
        private Optional<AddressConnector> addressConnector;

        public Gyroscope(Optional<AddressConnector> addressConnector)
        {
            this.addressConnector = addressConnector;

            this.initialize(addressConnector);
        }

        private void initialize(Optional<AddressConnector> addressConnector)
        {
            addressConnector.ifPresent(connector -> Arrays.asList(SetupRegister.values())
                                                          .forEach(register -> applyRegister(register, connector)));
        }

        public Optional<Orientation> getOrientation(int numberOfSamplings)
        {
            return this.addressConnector.map(addressConnector ->
            {
                List<Orientation> samples = IntStream.range(0, numberOfSamplings)
                                                     .mapToObj(sampleIndex ->
                                                     {
                                                         int x = readRegister(ReadRegister.GYROSCOPE_X, addressConnector).orElse(Integer.MAX_VALUE);
                                                         int y = readRegister(ReadRegister.GYROSCOPE_Y, addressConnector).orElse(Integer.MAX_VALUE);
                                                         int z = readRegister(ReadRegister.GYROSCOPE_Z, addressConnector).orElse(Integer.MAX_VALUE);

                                                         return new Orientation(x, y, z);
                                                     })
                                                     .collect(Collectors.toList());

                double x = samples.stream()
                                  .mapToDouble(Orientation::getX)
                                  .average()
                                  .orElse(Integer.MAX_VALUE);
                double y = samples.stream()
                                  .mapToDouble(Orientation::getY)
                                  .average()
                                  .orElse(Integer.MAX_VALUE);
                double z = samples.stream()
                                  .mapToDouble(Orientation::getZ)
                                  .average()
                                  .orElse(Integer.MAX_VALUE);
                return new Orientation(x, y, z);
            });
        }

        private static Optional<Integer> readRegister(ReadRegister readRegister, AddressConnector addressConnector)
        {
            LOG.debug("Read register: " + readRegister);
            return addressConnector.read(readRegister.getHighAddress(), 0, 2)
                                   .map(register ->
                                   {
                                       int value = register.asIntFromMsbToLsb(0);
                                       LOG.debug("Register (" + readRegister + ") value: " + value);
                                       return value;
                                   });
        }

        private static void applyRegister(SetupRegister register, AddressConnector addressConnector)
        {
            LOG.debug("Writing register: " + register);
            addressConnector.write(register.getAddress(), register.getValue());
        }
    }

    private static enum SetupRegister
    {
        //        MPU6050_RA_SMPLRT_DIV(25, 0b00000000),
        //        MPU6050_RA_CONFIG(26, 0b00000001),
        //        MPU6050_RA_GYRO_CONFIG(27, 0b00011000),
        //        MPU6050_RA_ACCEL_CONFIG(28, 0b00000000),
        //        MPU6050_RA_FIFO_EN(35, 0b00000000),
        //        MPU6050_RA_INT_ENABLE(56, 0b00000000),
        MPU6050_RA_PWR_MGMT_1(107, 0b00000000), MPU6050_RA_PWR_MGMT_2(108, 0b00000000);

        private int  address;
        private byte value;

        private SetupRegister(int address, int value)
        {
            this.address = address;
            this.value = (byte) value;
        }

        public int getAddress()
        {
            return this.address;
        }

        public byte getValue()
        {
            return this.value;
        }

    }

    private static enum ReadRegister
    {
        ACCELERATION_X(59,
                60),
        ACCELERATION_Y(61, 62),
        ACCELERATION_Z(63, 64),
        TEMPERATURE(65, 66),
        GYROSCOPE_X(67, 68),
        GYROSCOPE_Y(69, 70),
        GYROSCOPE_Z(71, 72);

        private int highAddress;
        private int lowAddress;

        private ReadRegister(int highAddress, int lowAddress)
        {
            this.highAddress = highAddress;
            this.lowAddress = lowAddress;

        }

        public int getHighAddress()
        {
            return this.highAddress;
        }

        @SuppressWarnings("unused")
        public int getLowAddress()
        {
            return this.lowAddress;
        }

    }
}
