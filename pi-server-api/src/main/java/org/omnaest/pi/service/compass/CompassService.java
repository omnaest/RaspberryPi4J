package org.omnaest.pi.service.compass;

/**
 * {@link CompassService} working with the GY-271 compass module
 * 
 * @author omnaest
 */
public interface CompassService
{

    public CompassBus onBus(int bus);

    public static interface CompassBus
    {
        public Compass withModule(Module module);
    }

    public static interface Compass
    {
        /**
         * Returns the angle towards the north direction clockwise in degree
         * 
         * @return
         */
        public int getNorthDirectionAngle();

    }

    public static enum Module
    {
        QMC5883L(0x0D), HMC5883(0x1E);

        private int address;

        private Module(int address)
        {
            this.address = address;
        }

        public int getAddress()
        {
            return address;
        }

    }
}
