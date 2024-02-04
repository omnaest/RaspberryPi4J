package org.omnaest.pi.service.compass.internal;

import org.omnaest.pi.service.compass.CompassService;
import org.omnaest.pi.service.i2c.I2CService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @see CompassService
 * @author omnaest
 */
@Service
public class CompassServiceImpl implements CompassService
{
    @Autowired
    private I2CService i2cService;

    @Override
    public CompassBus onBus(int bus)
    {
        I2CService i2cService = this.i2cService;
        return new CompassBus()
        {
            @Override
            public Compass withModule(Module module)
            {
                return new Compass()
                {
                    @Override
                    public int getNorthDirectionAngle()
                    {
                        int address = module.getAddress();
                        return i2cService.provision(bus)
                                         .flatMap(control -> control.connectTo(address))
                                         .flatMap(connector -> connector.write(0xB, (byte) 0x01)
                                                                        .write(0x9, (byte) /* 0x1D */ 0B11010001)
                                                                        //                                                                        .wait(500, TimeUnit.MILLISECONDS)
                                                                        .waitUntilBitIsTrue(0x06, (byte) 0B00000001)
                                                                        .read(0x00, 0, 6)
                                                                        .map(data ->
                                                                        {
                                                                            int x = data.asIntFromMsbToLsb(0, 1);
                                                                            if (x > 32767)
                                                                            {
                                                                                x -= 65536;
                                                                            }

                                                                            int y = data.asIntFromMsbToLsb(2, 3);
                                                                            if (y > 32767)
                                                                            {
                                                                                y -= 65536;
                                                                            }

                                                                            int z = data.asIntFromMsbToLsb(4, 5);
                                                                            if (z > 32767)
                                                                            {
                                                                                z -= 65536;
                                                                            }

                                                                            int result = (int) Math.round((-Math.atan2(y, x) * 180.0 / Math.PI) + 180);

                                                                            return result;
                                                                        }))
                                         .orElse(-1);
                    }
                };
            }
        };
    }

}
