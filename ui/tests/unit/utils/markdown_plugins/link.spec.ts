import {describe, expect, it} from "vitest";
import MarkdownIt from "markdown-it";
import {createRouter, createMemoryHistory} from "vue-router";
import {linkTag} from "../../../../src/utils/markdown_plugins/link";
import linkify from "../../../../src/components/logs/linkify";

const render = (src: string) => new MarkdownIt({html: true}).use(linkTag).renderInline(src);

describe("linkTag markdown plugin", () => {
    it("renders a router-md tag for a valid link", () => {
        expect(render("Created new execution [[link execution=\"abc\" flowId=\"my-flow\" namespace=\"company.team\"]]"))
            .toBe("Created new execution <router-md execution=\"abc\" namespace=\"company.team\" flowId=\"my-flow\"></router-md>");
    });

    it("drops attributes that are not allowed", () => {
        const html = render("[[link execution=\"a\" namespace=\"b\" flowId=\"c\" style=\"position:fixed;inset:0\" onmouseover=\"alert(1)\"]]");
        expect(html).not.toContain("onmouseover");
        expect(html).not.toContain("style");
        expect(html).toBe("<router-md execution=\"a\" namespace=\"b\" flowId=\"c\"></router-md>");
    });

    it("does not render a tag when required attributes are missing", () => {
        const html = render("[[link style=\"color:red\" onmouseover=\"alert(1)\"]]");
        expect(html).not.toContain("<router-md");
        expect(html).not.toContain("onmouseover=\"");
    });

    it("escapes attribute values", () => {
        const html = render("[[link execution=\"a'><img/src/onerror=alert(1)>\" namespace=\"b\" flowId=\"c\"]]");
        expect(html).not.toContain("<img");
        expect(html).toContain("execution=\"a'&gt;&lt;img/src/onerror=alert(1)&gt;\"");
    });

    it("only matches the link tag at the current position", () => {
        const html = render("before [[link execution=\"a\" namespace=\"b\" flowId=\"c\"]] after");
        expect(html).toBe("before <router-md execution=\"a\" namespace=\"b\" flowId=\"c\"></router-md> after");
    });
});

describe("linkify", () => {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "executions/update", path: "/executions/:namespace/:flowId/:id", component: {}},
            {name: "flows/update", path: "/flows/edit/:namespace/:id", component: {}},
        ],
    });

    it("replaces valid router-md elements with anchors", () => {
        const element = document.createElement("div");
        element.innerHTML = render("[[link execution=\"a\" namespace=\"b\" flowId=\"c\"]]");
        linkify(element, router);
        expect(element.querySelector("router-md")).toBeNull();
        expect(Array.from(element.querySelectorAll("a")).map(a => a.getAttribute("href")))
            .toEqual(["/executions/b/c/a", "/flows/edit/b/c"]);
    });

    it("removes router-md elements missing required attributes", () => {
        const element = document.createElement("div");
        element.innerHTML = "<router-md onmouseover=\"alert(1)\" style=\"color:red\"></router-md><router-md execution=\"a\"></router-md>";
        linkify(element, router);
        expect(element.querySelector("router-md")).toBeNull();
        expect(element.innerHTML).toBe("");
    });
});
