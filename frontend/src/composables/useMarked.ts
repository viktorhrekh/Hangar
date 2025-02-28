import { marked } from "marked";
import markedLinkifyIt from "marked-linkify-it";
import markedExtendedTables from "marked-extended-tables";

const renderer = {
  heading(text: string, level: number) {
    const escapedText = text.toLowerCase().replaceAll(/\W+/g, "-");
    return `
            <h${level}>
              ${text}
              <a id="${escapedText}" class="headeranchor" href="#${escapedText}">
                <span class="header-link"></span>
                <svg class="ml-2 text-xl inline-flex mb-1" preserveAspectRatio="xMidYMid meet" viewBox="0 0 24 24" width="1.2em" height="1em"><path fill="currentColor" d="M10.59 13.41c.41.39.41 1.03 0 1.42c-.39.39-1.03.39-1.42 0a5.003 5.003 0 0 1 0-7.07l3.54-3.54a5.003 5.003 0 0 1 7.07 0a5.003 5.003 0 0 1 0 7.07l-1.49 1.49c.01-.82-.12-1.64-.4-2.42l.47-.48a2.982 2.982 0 0 0 0-4.24a2.982 2.982 0 0 0-4.24 0l-3.53 3.53a2.982 2.982 0 0 0 0 4.24m2.82-4.24c.39-.39 1.03-.39 1.42 0a5.003 5.003 0 0 1 0 7.07l-3.54 3.54a5.003 5.003 0 0 1-7.07 0a5.003 5.003 0 0 1 0-7.07l1.49-1.49c-.01.82.12 1.64.4 2.43l-.47.47a2.982 2.982 0 0 0 0 4.24a2.982 2.982 0 0 0 4.24 0l3.53-3.53a2.982 2.982 0 0 0 0-4.24a.973.973 0 0 1 0-1.42Z"></path></svg>
              </a>
            </h${level}>`;
  },
};
marked.use({ renderer });
marked.use(markedExtendedTables());
marked.use(markedLinkifyIt());

export function parseMarkdown(text: string): string {
  return marked.parse(text);
}
