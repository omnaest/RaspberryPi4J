package org.omnaest.pi.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnaest.pi.Application;
import org.omnaest.pi.service.servo.ServoDriverSimulationControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Seam test for {@link ServoTools}: drives each tool's {@code call()} handler directly against the REAL
 * {@code ServoDriverService} bean (built atop the {@code PwmChipDriver} seam) wired under the {@code simulation}
 * profile, and asserts observable state via {@link ServoDriverSimulationControl}.
 *
 * <p>Expected on/off tick values are hand-derived from {@code ServoDriverServiceImpl}'s and
 * {@code SimulatedPwmChipDriver}'s own documented formulas (duration-minimum=1, duration-maximum=3200,
 * period-duration-micros=20480 defaults; {@code off = round(durationMicros / periodMicros * 4096)}), the same
 * "hand-derive against the documented decode path" style as {@code SimulationProfileSmokeTest}. A wrong channel
 * index or a wrong argument value would produce a different, mismatching value, so the assertions bite on the
 * adapter's parameter forwarding.
 */
@SpringBootTest(classes = Application.class, properties = "spring.profiles.active=simulation")
class ServoToolsTest
{

    private static final int             PERIOD_DURATION_MICROS = 20480;

    @Autowired
    private ServoTools                   servoTools;

    @Autowired
    private ServoDriverSimulationControl servoDriverSimulationControl;

    @Test
    void setAngle_setsExpectedOffTicksOnTheGivenChannelOnly()
    {
        int channel = 5;
        int otherChannel = 6;
        int angle = 180;
        int expectedOff = expectedOffForDuration(determineServoDuration(1, 3200, angle));

        CallToolResult result = specOf("servo_set_angle").call()
                                                         .apply(null, Map.of("index", channel, "angle", angle));

        assertThat(result.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedOff);
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(otherChannel)).containsExactly(0, 0);
    }

    @Test
    void setSpeed_positiveAndNegative_setExpectedOffTicks()
    {
        int channel = 7;
        int durationNeutral = 1600; // durationMaximum / 2 default
        int maxToNeutralDifference = Math.abs(3200 - durationNeutral);
        int minToNeutralDifference = Math.abs(durationNeutral - 1);

        double positiveSpeed = 0.5;
        int expectedPositiveDuration = (int) Math.round(durationNeutral + maxToNeutralDifference * positiveSpeed);
        int expectedPositiveOff = expectedOffForDuration(expectedPositiveDuration);

        CallToolResult positiveResult = specOf("servo_set_speed").call()
                                                                 .apply(null, Map.of("index", channel, "speed", positiveSpeed));
        assertThat(positiveResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedPositiveOff);

        double negativeSpeed = -0.5;
        int expectedNegativeDuration = (int) Math.round(durationNeutral + minToNeutralDifference * negativeSpeed);
        int expectedNegativeOff = expectedOffForDuration(expectedNegativeDuration);

        CallToolResult negativeResult = specOf("servo_set_speed").call()
                                                                 .apply(null, Map.of("index", channel, "speed", negativeSpeed));
        assertThat(negativeResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedNegativeOff);
    }

    @Test
    void pinEnableThenDisable_togglesAlwaysOnAlwaysOff()
    {
        int channel = 8;

        CallToolResult enableResult = specOf("servo_pin_enable").call()
                                                                .apply(null, Map.of("index", channel));
        assertThat(enableResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.isAlwaysOn(channel)).isTrue();
        assertThat(this.servoDriverSimulationControl.isAlwaysOff(channel)).isFalse();

        CallToolResult disableResult = specOf("servo_pin_disable").call()
                                                                  .apply(null, Map.of("index", channel));
        assertThat(disableResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.isAlwaysOff(channel)).isTrue();
        assertThat(this.servoDriverSimulationControl.isAlwaysOn(channel)).isFalse();
    }

    @Test
    void pinSetPwm_setsProportionalOffTicks()
    {
        int channel = 9;
        double value = 0.5;
        int expectedOff = (int) Math.round(value * 4096);

        CallToolResult result = specOf("servo_pin_set_pwm").call()
                                                           .apply(null, Map.of("index", channel, "value", value));

        assertThat(result.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedOff);
    }

    @Test
    void setDurationMaximum_changesSubsequentAngleComputation()
    {
        int channel = 10;
        int newMax = 4000;

        CallToolResult maxResult = specOf("servo_set_duration_maximum").call()
                                                                       .apply(null, Map.of("index", channel, "max", newMax));
        assertThat(maxResult.isError()).isFalse();

        int angle = 360;
        int expectedOff = expectedOffForDuration(determineServoDuration(1, newMax, angle));
        int defaultOff = expectedOffForDuration(determineServoDuration(1, 3200, angle));
        assertThat(expectedOff).isNotEqualTo(defaultOff);

        CallToolResult angleResult = specOf("servo_set_angle").call()
                                                              .apply(null, Map.of("index", channel, "angle", angle));
        assertThat(angleResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedOff);
    }

    @Test
    void setDurationMinimum_changesSubsequentAngleComputation()
    {
        int channel = 11;
        int newMin = 100;

        CallToolResult minResult = specOf("servo_set_duration_minimum").call()
                                                                       .apply(null, Map.of("index", channel, "min", newMin));
        assertThat(minResult.isError()).isFalse();

        int angle = 0;
        int expectedOff = expectedOffForDuration(determineServoDuration(newMin, 3200, angle));
        int defaultOff = expectedOffForDuration(determineServoDuration(1, 3200, angle));
        assertThat(expectedOff).isNotEqualTo(defaultOff);

        CallToolResult angleResult = specOf("servo_set_angle").call()
                                                              .apply(null, Map.of("index", channel, "angle", angle));
        assertThat(angleResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedOff);
    }

    @Test
    void setDurationNeutral_changesSubsequentZeroSpeedComputation()
    {
        int channel = 12;
        int newNeutral = 1000;

        CallToolResult neutralResult = specOf("servo_set_duration_neutral").call()
                                                                           .apply(null, Map.of("index", channel, "neutral", newNeutral));
        assertThat(neutralResult.isError()).isFalse();

        int expectedOff = expectedOffForDuration(newNeutral);
        int defaultOff = expectedOffForDuration(1600);
        assertThat(expectedOff).isNotEqualTo(defaultOff);

        CallToolResult speedResult = specOf("servo_set_speed").call()
                                                              .apply(null, Map.of("index", channel, "speed", 0.0));
        assertThat(speedResult.isError()).isFalse();
        assertThat(this.servoDriverSimulationControl.getPwmOnOffValues(channel)).containsExactly(0, expectedOff);
    }

    private static int determineServoDuration(int durationMinimum, int durationMaximum, int angle)
    {
        return (int) Math.round(durationMinimum + (durationMaximum - durationMinimum) * (angle / 360.0));
    }

    private static int expectedOffForDuration(int durationMicros)
    {
        return (int) Math.round(durationMicros / (double) PERIOD_DURATION_MICROS * 4096);
    }

    private SyncToolSpecification specOf(String toolName)
    {
        List<SyncToolSpecification> specs = this.servoTools.specs();
        return specs.stream()
                    .filter(spec -> toolName.equals(spec.tool()
                                                        .name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tool spec not found: " + toolName));
    }
}
