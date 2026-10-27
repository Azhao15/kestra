import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const loadFlow = vi.hoisted(() => vi.fn())

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flow: undefined, loadFlow}),
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: {id: "e1", namespace: "company.team", flowId: "demo", state: {current: "SUCCESS"}},
        flowGraph: undefined,
        subflowsExecutions: {},
        loadAugmentedGraph: vi.fn().mockResolvedValue(undefined),
    }),
}))

vi.mock("../inputs/LowCodeEditor.vue", () => ({default: {template: "<div />"}}))

import Topology from "./Topology.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

function rejectionHandlerOf(mock: ReturnType<typeof vi.fn>) {
    let handler: ((e: unknown) => unknown) | undefined
    mock.mockReturnValueOnce({catch: (onRejected: (e: unknown) => unknown) => {
        handler = onRejected
        return new Promise(() => {})
    }})
    return () => handler!
}

describe("Topology", () => {
    it("loads the flow source silently for a viewer without access to the flow", async () => {
        const forbidden = {status: 403, __kestra_handled: false}
        loadFlow.mockRejectedValueOnce(forbidden)

        i18nMount(Topology)
        await flushPromises()

        expect(loadFlow).toHaveBeenCalledWith({namespace: "company.team", id: "demo"})
        expect(forbidden.__kestra_handled).toBe(true)
    })

    it("still surfaces a failure other than a missing permission while loading the flow source", async () => {
        const handler = rejectionHandlerOf(loadFlow)

        i18nMount(Topology)
        await flushPromises()

        expect(() => handler()({status: 500})).toThrow()
    })
})
