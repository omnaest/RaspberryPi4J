package org.omnaest.pi.client.domain.motor;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class L298nMotorControlDefinition
{
    @JsonProperty
    private int forwardPort;

    @JsonProperty
    private int backwardPort;

    @JsonProperty
    private int pwmPort;
}