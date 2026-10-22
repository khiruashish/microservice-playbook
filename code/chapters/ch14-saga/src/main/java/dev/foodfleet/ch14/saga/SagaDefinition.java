package dev.foodfleet.ch14.saga;

import java.util.List;

/** An ordered list of steps, validated: compensatable*, pivot?, retriable*. */
public record SagaDefinition<C>(String name, List<SagaStep<C>> steps) {

    public SagaDefinition {
        steps = List.copyOf(steps);
        int phase = 0;                                   // 0 compensatable, 1 after pivot / retriable
        int pivots = 0;
        for (SagaStep<C> s : steps) {
            switch (s.kind()) {
                case COMPENSATABLE -> {
                    if (phase > 0) {
                        throw new IllegalArgumentException(name + ": compensatable step " + s.name() + " after the pivot");
                    }
                }
                case PIVOT -> {
                    if (++pivots > 1) {
                        throw new IllegalArgumentException(name + ": more than one pivot");
                    }
                    phase = 1;
                }
                case RETRIABLE -> phase = 1;
            }
        }
    }
}
