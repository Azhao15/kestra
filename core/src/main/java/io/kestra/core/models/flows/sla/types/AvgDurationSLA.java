package io.kestra.core.models.flows.sla.types;

import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@Getter
@NoArgsConstructor
public class AvgDurationSLA extends AbstractDurationSLA {
    @DurationMin(millis = 1, message = "must be a positive duration")
    private Duration durationThreshold;

    @Positive
    private Float percentageThreshold;

    @Builder.Default
    @NotNull
    @Min(1)
    private Integer minExecutions = 5;

    @NotNull
    private Duration lookbackPeriod;

    @Builder.Default
    @NotNull
    private List<State.Type> averageOverStates = List.of(State.Type.SUCCESS, State.Type.WARNING);

    @Override
    public Optional<Duration> computeDuration(RunContext runContext) {
        var executionStatisticRepository = ((DefaultRunContext) runContext).services().additionalService(ExecutionStatisticsRepositoryInterface.class);
        var flowInfo = runContext.flowInfo();
        var startDate = Instant.now().minus(this.lookbackPeriod);
        var statistics = executionStatisticRepository.statistics(flowInfo.tenantId(), flowInfo.namespace(), flowInfo.id(), averageOverStates, startDate, Instant.now(), DateUtils.GroupType.MONTH);

        var sum = 0L;
        var count = 0L;
        for (var statistic : statistics) {
            var bucketSum = statistic.getDuration().getSum();
            if (bucketSum != null) {
                sum += bucketSum.toMillis();
                count += statistic.getDuration().getCount();
            }
        }
        if (count == 0 || count < minExecutions) {
            return Optional.empty();
        }

        var avg = sum / count;
        if (percentageThreshold != null) {
            avg = (long) (avg * (1d + percentageThreshold));
        }
        if (durationThreshold != null) {
            avg = avg + durationThreshold.toMillis();
        }
        return Optional.of(Duration.ofMillis(avg));
    }

    @JsonIgnore
    @AssertTrue(message = "durationThreshold and percentageThreshold cannot both be set")
    public boolean isSingleThreshold() {
        return durationThreshold == null || percentageThreshold == null;
    }

    @Override
    protected String durationKind() {
        return "average";
    }
}
