package org.omnaest.pi.service.servo;

public interface ServoDriverService
{
    public PwmPin pwmPin(int index);

    public Servo servo(int index);

    public static interface Servo
    {
        public void applyAngle(int angle);

        public void applySpeed(double speed);

        public void applyDurationMinimum(int min);

        public void applyDurationMaximum(int max);

        public void applyDurationNeutral(int neutral);

    }

    public static interface PwmPin
    {
        public void set(boolean value);

        public default void enable()
        {
            this.set(true);
        }

        public default void disable()
        {
            this.set(false);
        }

        public void setPwm(double value);

    }
}
