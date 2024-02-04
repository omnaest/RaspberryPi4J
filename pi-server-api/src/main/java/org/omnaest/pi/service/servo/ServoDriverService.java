package org.omnaest.pi.service.servo;

public interface ServoDriverService
{
    /**
     * @param index
     * @return
     */
    public Servo servo(int index);

    public static interface Servo
    {
        public void applyAngle(int angle);

        public void applySpeed(double speed);

        public void applyDurationMinimum(int min);

        public void applyDurationMaximum(int max);

        public void applyDurationNeutral(int neutral);

    }
}
