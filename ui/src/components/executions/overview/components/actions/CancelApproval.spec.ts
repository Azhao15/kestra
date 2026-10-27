import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const cancelApproval = vi.hoisted(() => vi.fn())
const reviewAllowed = vi.hoisted(() => vi.fn())
const confirm = vi.hoisted(() => vi.fn())
const success = vi.hoisted(() => vi.fn())

vi.mock("../../../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        reviewAllowed,
        cancelApproval,
    }),
}))

vi.mock("../../../../../utils/toast", () => ({
    useToast: () => ({confirm, success}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

import CancelApproval from "./CancelApproval.vue"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import type {Execution} from "../../../../../stores/executions"

function mountAction() {
    return i18nMount(CancelApproval, {
        props: {
            execution: {id: "e1", namespace: "ns", taskRunList: [{id: "tr1", taskId: "approval", state: {current: "PAUSED"}}]} as unknown as Execution,
        },
        global: {stubs: {NavBarAction: {template: "<button v-bind=\"$attrs\"><slot /></button>"}}},
    })
}

describe("CancelApproval", () => {
    beforeEach(() => {
        cancelApproval.mockReset().mockResolvedValue(undefined)
        reviewAllowed.mockReset().mockResolvedValue(true)
        confirm.mockReset()
        success.mockReset()
    })

    it("cancels the paused approval request only once the user confirms", async () => {
        const wrapper = mountAction()
        await flushPromises()

        await wrapper.find("[data-test='cancel-approval-action']").trigger("click")

        expect(cancelApproval).not.toHaveBeenCalled()
        await confirm.mock.calls[0][1]()
        expect(cancelApproval).toHaveBeenCalledWith({id: "e1", taskRunId: "tr1"})
        expect(success).toHaveBeenCalledWith("approval.cancel done")
    })

    it("is hidden from a user who may not review the request", async () => {
        reviewAllowed.mockResolvedValue(false)
        const wrapper = mountAction()
        await flushPromises()

        expect(wrapper.find("[data-test='cancel-approval-action']").exists()).toBe(false)
    })
})
