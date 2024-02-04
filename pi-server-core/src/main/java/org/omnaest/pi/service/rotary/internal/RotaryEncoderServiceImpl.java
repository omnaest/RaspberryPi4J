package org.omnaest.pi.service.rotary.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.omnaest.pi.service.gpio.GPIOService;
import org.omnaest.pi.service.gpio.GPIOService.DigitalInputGPIOPort;
import org.omnaest.pi.service.rotary.RotaryEncoderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RotaryEncoderServiceImpl implements RotaryEncoderService
{
    private static final Logger LOG = LoggerFactory.getLogger(RotaryEncoderServiceImpl.class);

    @Autowired
    private GPIOService gpioService;

    private Map<RotaryEncoderIdentifier, RotaryEncoderContext> rotaryEncoderIdentifierToContext = new ConcurrentHashMap<>();
    private ScheduledExecutorService                           executorService                  = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init()
    {
        this.executorService.scheduleWithFixedDelay(() ->
        {
            this.rotaryEncoderIdentifierToContext.entrySet()
                                                 .forEach(identifierAndContext ->
                                                 {
                                                     RotaryEncoderContext context = identifierAndContext.getValue();
                                                     List<RotaryEncoderState> rotaryEncoderStates = context.getRotaryEncoderStates();
                                                     if (!rotaryEncoderStates.isEmpty())
                                                     {
                                                         LOG.info("Calculating rotary state for " + identifierAndContext.getKey());
                                                         rotaryEncoderStates.forEach(new Consumer<RotaryEncoderState>()
                                                         {
                                                             private int clockwiseCounter        = 0;
                                                             private int counterClockwiseCounter = 0;

                                                             @Override
                                                             public void accept(RotaryEncoderState state)
                                                             {
                                                                 LOG.info("" + state);

                                                                 RotaryEncoderInputSource rotaryEncoderInputSource = state.getInputSource();
                                                                 if (RotaryEncoderInputSource.CLK.equals(rotaryEncoderInputSource))
                                                                 {
                                                                     context.setCurrentClkState(state.getValue());
                                                                 }
                                                                 else if (RotaryEncoderInputSource.DT.equals(rotaryEncoderInputSource))
                                                                 {
                                                                     context.setCurrentDtState(state.getValue());
                                                                 }

                                                                 if (context.hasClkReachedNextState() && !context.hasDtReachedNextState())
                                                                 {
                                                                     this.clockwiseCounter++;
                                                                 }
                                                                 else if (!context.hasClkReachedNextState() && context.hasDtReachedNextState())
                                                                 {
                                                                     this.counterClockwiseCounter++;
                                                                 }
                                                                 else if (context.hasDtAndClkReachedNextState())
                                                                 {
                                                                     LOG.info("Clockwise counter: " + this.clockwiseCounter);
                                                                     LOG.info("Counter clockwise counter: " + this.counterClockwiseCounter);
                                                                     if (this.clockwiseCounter < this.counterClockwiseCounter)
                                                                     {
                                                                         context.switchToNextStateAndIncrementValue();
                                                                     }
                                                                     else
                                                                     {
                                                                         context.switchToNextStateAndDecrementValue();
                                                                     }
                                                                     this.clockwiseCounter = 0;
                                                                     this.counterClockwiseCounter = 0;
                                                                 }
                                                             }
                                                         });
                                                     }
                                                 });
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy()
    {
        this.executorService.shutdownNow();
    }

    private static class RotaryEncoderContext
    {
        private BlockingQueue<RotaryEncoderState> rotaryEncoderStates = new LinkedBlockingQueue<>();
        private AtomicBoolean                     nextActiveState     = new AtomicBoolean(true);
        private AtomicBoolean                     currentClkState     = new AtomicBoolean(false);
        private AtomicBoolean                     currentDtState      = new AtomicBoolean(false);
        private AtomicLong                        value               = new AtomicLong();

        public RotaryEncoderContext()
        {
            super();
        }

        public boolean hasDtReachedNextState()
        {
            return this.currentDtState.get() == this.nextActiveState.get();
        }

        public boolean hasClkReachedNextState()
        {
            return this.currentClkState.get() == this.nextActiveState.get();
        }

        public void switchToNextStateAndDecrementValue()
        {
            this.switchToNextState();
            this.value.decrementAndGet();
            LOG.info("Decremented rotary encoder value: " + this.value.get());
        }

        private void switchToNextState()
        {
            this.nextActiveState.set(!this.nextActiveState.get());
        }

        public void switchToNextStateAndIncrementValue()
        {
            this.switchToNextState();
            this.value.incrementAndGet();
            LOG.info("Incremented rotary encoder value: " + this.value.get());
        }

        public boolean hasDtAndClkReachedNextState()
        {
            return this.getNextActiveState() == this.getCurrentClkState() && this.getNextActiveState() == this.getCurrentDtState();
        }

        public void setCurrentClkState(boolean clkState)
        {
            if (this.currentClkState.get() != this.nextActiveState.get())
            {
                this.currentClkState.set(clkState);
            }
        }

        public void setCurrentDtState(boolean dtState)
        {
            if (this.currentDtState.get() != this.nextActiveState.get())
            {
                this.currentDtState.set(dtState);
            }
        }

        public List<RotaryEncoderState> getRotaryEncoderStates()
        {
            List<RotaryEncoderState> states = new ArrayList<>();
            this.rotaryEncoderStates.drainTo(states);
            return states;
        }

        public boolean getNextActiveState()
        {
            return this.nextActiveState.get();
        }

        public boolean getCurrentClkState()
        {
            return this.currentClkState.get();
        }

        public boolean getCurrentDtState()
        {
            return this.currentDtState.get();
        }

        public void addState(RotaryEncoderState rotaryEncoderState)
        {
            try
            {
                this.rotaryEncoderStates.put(rotaryEncoderState);
            }
            catch (InterruptedException e)
            {
                LOG.warn("Unexpected interrupt signal", e);
            }
        }

        public long getValue()
        {
            return this.value.get();
        }

        public void resetValue()
        {
            this.value.set(0);
        }

    }

    private static class RotaryEncoderIdentifier
    {
        private int clkPort;
        private int dtPort;

        public RotaryEncoderIdentifier(int clkPort, int dtPort)
        {
            super();
            this.clkPort = clkPort;
            this.dtPort = dtPort;
        }

        @Override
        public String toString()
        {
            return "RotaryEncoderIdentifier [clkPort=" + this.clkPort + ", dtPort=" + this.dtPort + "]";
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + this.clkPort;
            result = prime * result + this.dtPort;
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            RotaryEncoderIdentifier other = (RotaryEncoderIdentifier) obj;
            if (this.clkPort != other.clkPort)
            {
                return false;
            }
            if (this.dtPort != other.dtPort)
            {
                return false;
            }
            return true;
        }

    }

    @Override
    public RotaryEncoder getRotaryEncoderByPin(int clkPort, int dtPort, int switchPort)
    {
        RotaryEncoderIdentifier rotaryEncoderIdentifier = new RotaryEncoderIdentifier(clkPort, dtPort);
        RotaryEncoderContext context = this.rotaryEncoderIdentifierToContext.computeIfAbsent(rotaryEncoderIdentifier, id -> new RotaryEncoderContext());

        DigitalInputGPIOPort dtPortAccessor = this.gpioService.getDigitalInputGPIOPort(dtPort)
                                                              .withPullUpResistance()
                                                              .enable();

        DigitalInputGPIOPort clkPortAccessor = this.gpioService.getDigitalInputGPIOPort(clkPort)
                                                               .withPullUpResistance()
                                                               .enable();

        clkPortAccessor.addStateChangeListener(stateChange -> context.addState(new RotaryEncoderState(RotaryEncoderInputSource.CLK, stateChange.getCurrent())));
        dtPortAccessor.addStateChangeListener(stateChange -> context.addState(new RotaryEncoderState(RotaryEncoderInputSource.DT, stateChange.getCurrent())));

        return new RotaryEncoder()
        {
            private int     minimum          = 0;
            private int     maximum          = Integer.MAX_VALUE;
            private boolean circularRotation = false;

            @Override
            public RotaryEncoder withMinimum(int minimum)
            {
                this.minimum = minimum;
                return this;
            }

            @Override
            public RotaryEncoder withMaximum(int maximum)
            {
                this.maximum = maximum;
                return this;
            }

            @Override
            public RotaryEncoder withCircularRotation(boolean circularRotation)
            {
                this.circularRotation = circularRotation;
                return this;
            }

            @Override
            public RotaryEncoder withCircularRotation()
            {
                return this.withCircularRotation(true);
            }

            @Override
            public RotaryEncoder reset()
            {
                context.resetValue();
                return this;
            }

            @Override
            public long getAsLong()
            {
                int range = this.maximum - this.minimum;
                long effectiveValue = this.circularRotation ? Math.abs(context.getValue() % (range + 1)) : Math.max(0, Math.min(context.getValue(), range));
                return effectiveValue + this.minimum;
            }
        };
    }

    private static class RotaryEncoderState
    {
        private RotaryEncoderInputSource inputSource;
        private boolean                  value;

        public RotaryEncoderState(RotaryEncoderInputSource inputSource, boolean value)
        {
            super();
            this.inputSource = inputSource;
            this.value = value;
        }

        public RotaryEncoderInputSource getInputSource()
        {
            return this.inputSource;
        }

        public boolean getValue()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "RotaryEncoderState [inputSource=" + this.inputSource + ", value=" + this.value + "]";
        }

    }

    private static enum RotaryEncoderInputSource
    {
        CLK, DT
    }
}
