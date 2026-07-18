package org.omnaest.pi.client;

import org.junit.Ignore;
import org.junit.Test;

public class SprinklerTest
{
    private SprinklerEngine engine = new SprinklerEngine(PIRemoteClient.newInstance("192.168.0.123", 8080));

    @Test
    @Ignore
    public void test()
    {
        this.engine.calibrateDisc();
    }
}
