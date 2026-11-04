package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

@Controller("/api/v1/{tenant}/concurrency-limit")
public class ConcurrencyLimitController {
    @Inject
    private ConcurrencyLimitService concurrencyLimitService;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = { "Flows" }, summary = "Search for flow concurrency limits")
    public PagedResults<ConcurrencyLimit> searchConcurrencyLimits() {
        var results = concurrencyLimitService.find(tenantService.resolveTenant());
        return PagedResults.of(new ArrayListTotal<>(results, results.size()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put("/{namespace}/{flowId}")
    @Operation(tags = { "Flows" }, summary = "Update a flow concurrency limit")
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Body ConcurrencyLimit concurrencyLimit) {
        String tenantId = tenantService.resolveTenant();
        if (
            isMismatch(concurrencyLimit.getTenantId(), tenantId)
                || isMismatch(concurrencyLimit.getNamespace(), namespace)
                || isMismatch(concurrencyLimit.getFlowId(), flowId)
        ) {
            throw new IllegalArgumentException("The concurrency limit tenant, namespace and flowId must match the request path.");
        }
        if (concurrencyLimit.getRunning() == null || concurrencyLimit.getRunning() < 0) {
            throw new IllegalArgumentException("The concurrency limit 'running' value must be zero or greater.");
        }

        var existing = concurrencyLimitService.findById(tenantId, namespace, flowId);
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(concurrencyLimitService.update(existing.get().withRunning(concurrencyLimit.getRunning())));
    }

    private static boolean isMismatch(String bodyValue, String expected) {
        return bodyValue != null && !bodyValue.equals(expected);
    }
}
