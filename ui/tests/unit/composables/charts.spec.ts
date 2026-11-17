import {describe, expect, it, vi} from "vitest";

vi.mock("override/stores/misc", () => ({useMiscStore: () => ({})}));

import {tooltip} from "../../../src/components/dashboard/composables/charts";

const payload = "<img src=x onerror=alert(1)>";

describe("charts tooltip", () => {
    it("escapes HTML in title and body lines", () => {
        const html = tooltip({
            title: [payload],
            body: [{lines: [`sum of ${payload}: 3`]}],
            labelColors: [{backgroundColor: "red\"><img src=x>", borderColor: "blue"}],
        }) as string;

        expect(html).not.toContain("<img");
        expect(html).toContain("<h6>&lt;img src=x onerror=alert(1)&gt;</h6>");
        expect(html).toContain("sum of &lt;img src=x onerror=alert(1)&gt;: 3<br />");
        expect(html).toContain("background:red&quot;&gt;&lt;img src=x&gt;");
    });

    it("keeps plain labels unchanged", () => {
        const html = tooltip({
            title: ["2024-01-01"],
            body: [{lines: ["Sum of rows: 3"]}],
            labelColors: [{backgroundColor: "#00ff00", borderColor: "#00ff00"}],
        });

        expect(html).toBe(
            "<h6>2024-01-01</h6><span class=\"square\" style=\"background:#00ff00; border-color:#00ff00\"></span>Sum of rows: 3<br />",
        );
    });

    it("returns undefined without body", () => {
        expect(tooltip({labelColors: []})).toBeUndefined();
    });
});
