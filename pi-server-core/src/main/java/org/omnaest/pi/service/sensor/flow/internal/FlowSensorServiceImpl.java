package org.omnaest.pi.service.sensor.flow.internal;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PreDestroy;

import org.omnaest.pi.client.domain.flow.FlowSensorDefinition;
import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort;
import org.omnaest.pi.service.sensor.flow.FlowSensorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;

import lombok.Builder;
import lombok.Data;

@Service
public class FlowSensorServiceImpl implements FlowSensorService
{
    @Autowired
    private GPIOService gpioService;

    private Map<Integer, FlowSensorContext> signalPortToFlowSensorContext = new ConcurrentHashMap<>();

    @Override
    public void enableFlowSensor(int signalPort, FlowSensorDefinition flowSensorDefinition)
    {
        double flowRateCoefficient = Optional.ofNullable(flowSensorDefinition)
                                             .map(FlowSensorDefinition::getFlowRateCoefficient)
                                             .filter(value -> value > 0.001)
                                             .orElse(7.5);
        this.signalPortToFlowSensorContext.computeIfAbsent(signalPort, port ->
        {
            DigitalInputGPIOPort portAccessor = this.gpioService.getDigitalInputGPIOPort(signalPort)
                                                                .withPullUpResistance()
                                                                .enable();

            AtomicLong counter = new AtomicLong();

            portAccessor.addStateChangeListener(stateChange ->
            {
                if (stateChange.isFallingEdge())
                {
                    counter.incrementAndGet();
                }
            });

            AtomicDouble flowRate = new AtomicDouble();
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(() ->
            {
                long currentCount = counter.getAndSet(0);
                flowRate.set(currentCount / flowRateCoefficient);
            }, 10, 1, TimeUnit.SECONDS);

            return FlowSensorContext.builder()
                                    .executorService(executorService)
                                    .flowRate(flowRate)
                                    .portAccessor(portAccessor)
                                    .build();
        });
    }

    @Override
    public void disableFlowSensor(int signalPort)
    {
        this.disableFlowSensor(this.signalPortToFlowSensorContext.remove(signalPort));
    }

    @Override
    public double getFlowRate(int signalPort)
    {
        return Optional.ofNullable(this.signalPortToFlowSensorContext.get(signalPort))
                       .map(FlowSensorContext::getFlowRate)
                       .map(AtomicDouble::get)
                       .orElse(Double.NaN);
    }

    @PreDestroy
    public void destroy()
    {
        this.signalPortToFlowSensorContext.values()
                                          .forEach(this::disableFlowSensor);
    }

    private void disableFlowSensor(FlowSensorContext context)
    {
        context.getExecutorService()
               .shutdownNow();
        context.getPortAccessor()
               .disable();
    }

    @Data
    @Builder
    private static class FlowSensorContext
    {
        private final ScheduledExecutorService executorService;
        private final AtomicDouble             flowRate;
        private final DigitalInputGPIOPort     portAccessor;
    }
}
