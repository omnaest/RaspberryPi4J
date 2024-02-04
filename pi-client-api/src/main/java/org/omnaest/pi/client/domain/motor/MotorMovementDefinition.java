package org.omnaest.pi.client.domain.motor;

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
public class MotorMovementDefinition
{
    @JsonProperty
    private MotorMovementDirection direction;

    @JsonProperty
    private double speed;

}
