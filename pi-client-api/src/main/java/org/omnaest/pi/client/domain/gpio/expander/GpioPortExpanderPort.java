package org.omnaest.pi.client.domain.gpio.expander;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum GpioPortExpanderPort
{
    P0(0), P1(1), P2(2), P3(3), P4(4), P5(5), P6(6), P7(7);

    private final int port;
}
