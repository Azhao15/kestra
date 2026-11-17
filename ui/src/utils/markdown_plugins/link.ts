import MarkdownIt from "markdown-it";

export const LINK_TAG_ATTRIBUTES = ["execution", "namespace", "flowId"] as const;

const LINK_TAG_REGEX = /^\[\[link(\s[^\]]*)?\]\]/;
const ATTRIBUTE_REGEX = /([A-Za-z]+)="([^"]*)"/g;

export function linkTag(md: MarkdownIt) {
    md.inline.ruler.after("emphasis", "link_custom", (state, silent) => {
        if (state.src.charCodeAt(state.pos) !== 0x5B /* [ */) return false;

        const match = state.src.slice(state.pos).match(LINK_TAG_REGEX);
        if (!match) return false;

        // Only keep the known attributes, they must have the following format: key="value"
        const attrs = new Map<string, string>();
        for (const [, name, value] of (match[1] ?? "").matchAll(ATTRIBUTE_REGEX)) {
            if ((LINK_TAG_ATTRIBUTES as readonly string[]).includes(name) && !attrs.has(name)) {
                attrs.set(name, value);
            }
        }

        if (!LINK_TAG_ATTRIBUTES.every(name => attrs.has(name))) return false;

        if (!silent) {
            const token = state.push("link_custom_open", "link", 1);
            token.markup = "[[link]]";
            token.attrs = LINK_TAG_ATTRIBUTES.map((name): [string, string] => [name, attrs.get(name)!]);
            state.push("link_custom_close", "link_custom", -1);
        }

        state.pos += match[0].length;

        return true;
    });

    md.renderer.rules.link_custom_open = (tokens, idx) => {
        const token = tokens[idx];
        const attrs = (token.attrs ?? [])
            .filter(([name]) => (LINK_TAG_ATTRIBUTES as readonly string[]).includes(name))
            .map(([name, value]) => `${name}="${md.utils.escapeHtml(value)}"`)
            .join(" ");

        return `<router-md ${attrs}>`;
    };
    md.renderer.rules.link_custom_close = () => "</router-md>";
}
