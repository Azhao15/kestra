package io.kestra.core.models.flows.sla.types;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.Services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvgDurationSLATest {

    @Test
    void shouldEvaluateToAViolationWhenExecutionExceedsComputedDuration() throws InternalException, InterruptedException {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var execution = Execution.builder().state(new State().withState(State.Type.RUNNING)).build();
        Thread.sleep(100);

        // When
        Optional<Violation> violation = sla.evaluate(null, execution, Map.of(SLA.SLA_DURATION_ATTRIBUTE, Duration.ofMillis(50)));

        // Then
        assertThat(violation).isPresent();
        assertThat(violation.get().reason()).contains("exceed the average duration of PT0.05S.");
    }

    @Test
    void shouldEvaluateToNoViolationWhenExecutionIsBelowComputedDuration() throws InternalException {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var execution = Execution.builder().state(new State().withState(State.Type.RUNNING)).build();

        // When
        Optional<Violation> violation = sla.evaluate(null, execution, Map.of(SLA.SLA_DURATION_ATTRIBUTE, Duration.ofSeconds(10)));

        // Then
        assertThat(violation).isEmpty();
    }

    @Test
    void shouldWeightBucketAveragesByExecutionCount() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).minExecutions(5).build();
        var runContext = runContextWith(List.of(
            bucket(Duration.ofSeconds(10), 1),
            bucket(Duration.ofSeconds(20), 9)
        ));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(19));
    }

    @Test
    void shouldIgnoreBucketsWithoutAverage() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).minExecutions(5).build();
        var runContext = runContextWith(List.of(
            bucket(Duration.ofSeconds(10), 5),
            bucket(null, 5)
        ));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(10));
    }

    @Test
    void shouldNotCountBucketsWithoutAverageTowardsMinExecutions() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).minExecutions(5).build();
        var runContext = runContextWith(List.of(
            bucket(Duration.ofSeconds(10), 2),
            bucket(null, 5)
        ));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).isEmpty();
    }

    @Test
    void shouldNotComputeDurationWhenNoStatistics() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var runContext = runContextWith(List.of());

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).isEmpty();
    }

    @Test
    void shouldNotComputeDurationWhenNotEnoughExecutions() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).minExecutions(5).build();
        var runContext = runContextWith(List.of(bucket(Duration.ofSeconds(10), 4)));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).isEmpty();
    }

    @Test
    void shouldComputeDurationWhenExactlyMinExecutions() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).minExecutions(5).build();
        var runContext = runContextWith(List.of(bucket(Duration.ofSeconds(10), 5)));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(10));
    }

    @Test
    void shouldAddPercentageThresholdToAverage() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).percentageThreshold(0.5f).build();
        var runContext = runContextWith(List.of(bucket(Duration.ofSeconds(10), 5)));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldAddDurationThresholdToAverage() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).durationThreshold(Duration.ofSeconds(5)).build();
        var runContext = runContextWith(List.of(bucket(Duration.ofSeconds(10), 5)));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldAddDurationThresholdOnceAcrossMultipleBuckets() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).durationThreshold(Duration.ofSeconds(10)).build();
        var runContext = runContextWith(List.of(
            bucket(Duration.ofSeconds(10), 3),
            bucket(Duration.ofSeconds(20), 3)
        ));

        // When
        Optional<Duration> duration = sla.computeDuration(runContext);

        // Then
        assertThat(duration).contains(Duration.ofSeconds(25));
    }

    @Test
    void shouldAverageOverSuccessAndWarningByDefault() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).build();
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);

        // When
        sla.computeDuration(runContextFor(repository));

        // Then
        verify(repository).statistics(any(), any(), any(), eq(List.of(State.Type.SUCCESS, State.Type.WARNING)), any(), any(), any());
    }

    @Test
    void shouldComputeAverageOverConfiguredStates() {
        // Given
        var sla = AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7)).averageOverStates(List.of(State.Type.FAILED)).build();
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);
        when(repository.statistics(any(), any(), any(), eq(List.of(State.Type.FAILED)), any(), any(), any()))
            .thenReturn(List.of(bucket(Duration.ofSeconds(40), 5)));
        when(repository.statistics(any(), any(), any(), eq(List.of(State.Type.SUCCESS)), any(), any(), any()))
            .thenReturn(List.of(bucket(Duration.ofSeconds(10), 5)));

        // When
        Optional<Duration> duration = sla.computeDuration(runContextFor(repository));

        // Then
        assertThat(duration).contains(Duration.ofSeconds(40));
    }

    private static DailyExecutionStatistics bucket(Duration avg, long count) {
        return DailyExecutionStatistics.builder()
            .startDate(Instant.now())
            .duration(DailyExecutionStatistics.Duration.builder().avg(avg).sum(avg == null ? null : avg.multipliedBy(count)).count(count).build())
            .build();
    }

    private static RunContext runContextWith(List<DailyExecutionStatistics> statistics) {
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);
        when(repository.statistics(any(), any(), any(), any(), any(), any(), any())).thenReturn(statistics);
        return runContextFor(repository);
    }

    private static RunContext runContextFor(ExecutionStatisticsRepositoryInterface repository) {
        var services = mock(Services.class);
        when(services.additionalService(ExecutionStatisticsRepositoryInterface.class)).thenReturn(repository);
        var runContext = mock(DefaultRunContext.class);
        when(runContext.services()).thenReturn(services);
        when(runContext.flowInfo()).thenReturn(new RunContext.FlowInfo("tenant", "ns", "flow", 1));
        return runContext;
    }
}
