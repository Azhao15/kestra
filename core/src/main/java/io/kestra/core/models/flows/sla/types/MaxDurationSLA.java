package io.kestra.core.models.flows.sla.types;

import java.time.Duration;
import java.util.Optional;

import io.kestra.core.runners.RunContext;
import io.kestra.core.validations.DurationMax;

import org.hibernate.validator.constraints.time.DurationMin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class MaxDurationSLA extends AbstractDurationSLA {
    @NotNull
    @DurationMax
    @DurationMin(millis = 1, message = "must be a positive duration")
    private Duration duration;

    @Override
    public Optional<Duration> computeDuration(RunContext runContext) {
        return Optional.of(duration);
    }

    @Override
    protected String durationKind() {
        return "maximum";
    }
}
