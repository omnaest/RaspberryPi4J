package org.omnaest.pi.client.domain.pressure;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PressureAndTemperature
{
    @JsonProperty
    private double pressureAbsolute;

    @JsonProperty
    private double pressureRelative;

    @JsonProperty
    private double temperature;
}