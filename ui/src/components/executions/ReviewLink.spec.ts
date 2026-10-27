import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const route = vi.hoisted(() => ({query: {} as Record<string, string>}))
const replace = vi.hoisted(() => vi.fn())
const reviewAllowed = vi.hoisted(() => vi.fn())
vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({replace}),
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        reviewAllowed,
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

import ReviewLink from "./ReviewLink.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

function mountReview(state: string) {
    return i18nMount(ReviewLink, {
        messages: {approval: {review: "Review"}},
        props: {
            execution: {id: "e1", namespace: "ns", taskRunList: [{id: "tr1", taskId: "approval", state: {current: state}}]} as unknown as Execution,
        },
        global: {
            stubs: {
                ReviewDialog: {props: ["modelValue"], template: "<div data-test=\"dialog\" :data-open=\"modelValue\" />"},
                ReviewedDialog: {props: ["modelValue"], template: "<div data-test=\"reviewed\" :data-open=\"modelValue\" />"},
            },
        },
    })
}

describe("ReviewLink", () => {
    beforeEach(() => {
        route.query = {}
        replace.mockReset()
        reviewAllowed.mockReset().mockResolvedValue(false)
    })

    it("shows the review that was made for a review link to an approval that is already decided", async () => {
        route.query = {review: "tr1"}
        const wrapper = mountReview("SUCCESS")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(wrapper.find("[data-test='reviewed']").attributes("data-open")).toBe("true")
        expect(replace).toHaveBeenCalledWith({query: {}})
    })

    it("does not open the dialog for a paused approval the viewer may not review", async () => {
        route.query = {review: "tr1"}
        const wrapper = mountReview("PAUSED")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").exists()).toBe(false)
        expect(replace).not.toHaveBeenCalled()
    })

    it("opens the dialog for a paused approval the viewer may review", async () => {
        route.query = {review: "tr1"}
        reviewAllowed.mockResolvedValue(true)
        const wrapper = mountReview("PAUSED")
        await flushPromises()

        expect(wrapper.find("[data-test='dialog']").attributes("data-open")).toBe("true")
    })
})
