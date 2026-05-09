package org.omnaest.pi.client.domain.weight;

import org.omnaest.pi.client.PiClient.Interaction;

public interface Nau7802Definition extends Interaction
{
    Nau7802Definition usingBus(int bus);

    int readValue();

    void calibrate();

}
