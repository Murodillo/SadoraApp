import DOMPurify from 'dompurify'
import { Marked } from 'marked'

/*
 * Docs, task descriptions and comments are Markdown, written by the team and by Claude
 * sessions. It is rendered here and sanitised before it reaches the page: the text comes
 * from many hands, and the panel's token sits in this tab.
 */

const IMAGE_SCHEME = 'pm-image:'
const KEY = /(?<![\w/[-])SAD-(\d{1,9})(?![\w\]])/g

/** `SAD-12` → a link to its task page; text inside code spans, code blocks and existing links is left as written. */
export function linkTaskKeys(markdown: string): string {
  // Split out fenced blocks, inline code and links, and only touch what lies between them.
  const protectedParts = /(```[\s\S]*?```|`[^`\n]*`|\[[^\]\n]*\]\([^)\n]*\))/g
  return markdown
    .split(protectedParts)
    .map((part, index) => (index % 2 === 1 ? part : part.replace(KEY, '[SAD-$1](/pm/SAD-$1)')))
    .join('')
}

/** The Markdown for a pasted picture: an id the panel fetches with its token, not a URL. */
export function imageMarkdown(id: string, alt: string): string {
  return `![${alt.replace(/[[\]]/g, '')}](${IMAGE_SCHEME}${id})`
}

const marked = new Marked({
  gfm: true,
  breaks: true,
  renderer: {
    image({ href, text }) {
      if (href.startsWith(IMAGE_SCHEME)) {
        return `<img data-pm-image="${escapeAttr(href.slice(IMAGE_SCHEME.length))}" alt="${escapeAttr(text)}">`
      }
      // A picture from elsewhere would tell its host who opened the doc, and when; it stays a link.
      return `<a href="${escapeAttr(href)}">${escapeAttr(text || href)}</a>`
    },
  },
})

const purifier = DOMPurify()
// Links out of the panel open in a new tab and carry no opener or referrer back to it.
purifier.addHook('afterSanitizeAttributes', (node) => {
  // The same for a raw <img> written into the Markdown: only pasted pictures load, by id.
  if (node.tagName === 'IMG') node.removeAttribute('src')
  if (node.tagName === 'A') {
    const href = node.getAttribute('href') ?? ''
    if (/^https?:/i.test(href)) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  }
})

/** Safe HTML for Markdown: task keys linked, pasted pictures marked by id, nothing that runs. */
export function renderMarkdown(markdown: string): string {
  const html = marked.parse(linkTaskKeys(markdown), { async: false })
  return purifier.sanitize(html, { ADD_ATTR: ['target'] })
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
}
