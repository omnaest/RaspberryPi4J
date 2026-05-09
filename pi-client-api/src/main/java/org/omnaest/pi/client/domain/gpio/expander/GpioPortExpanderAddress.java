package org.omnaest.pi.client.domain.gpio.expander;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum GpioPortExpanderAddress
{
    A20(0x20), A21(0x21), A22(0x22), A23(0x23), A24(0x24), A25(0x25), A26(0x26), A27(0x27);

    private final int address;
}