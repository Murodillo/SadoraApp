import { describe, expect, it } from 'vitest'
import { imageMarkdown, linkTaskKeys, renderMarkdown } from './markdown'

describe('linkTaskKeys', () => {
  it('turns a task key into a link to its page', () => {
    expect(linkTaskKeys('See SAD-12, then SAD-3.')).toBe('See [SAD-12](/pm/SAD-12), then [SAD-3](/pm/SAD-3).')
  })

  it('leaves keys inside code, links and longer words alone', () => {
    const text = 'Run `pm task done SAD-4`\n\n```\nSAD-5\n```\n[SAD-6](/pm/SAD-6) XSAD-7 SAD-8x'
    expect(linkTaskKeys(text)).toBe(text)
  })
})

describe('renderMarkdown', () => {
  it('renders Markdown with task links and drops anything that could run', () => {
    const html = renderMarkdown('# Reja\n\nSAD-9 <img src=x onerror="alert(1)"> <script>alert(2)</script>')
    expect(html).toContain('<h1>Reja</h1>')
    expect(html).toContain('<a href="/pm/SAD-9">SAD-9</a>')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
  })

  it('marks a pasted picture by its id instead of a URL the browser could fetch unsigned', () => {
    const html = renderMarkdown(imageMarkdown('5f1c', 'ekran rasmi'))
    expect(html).toContain('data-pm-image="5f1c"')
    expect(html).toContain('alt="ekran rasmi"')
    expect(html).not.toContain('pm-image:')
  })

  it('shows an outside picture as a link, so opening a doc tells no one else who read it', () => {
    const html = renderMarkdown('![tracker](https://example.com/pixel.png)')
    expect(html).not.toContain('<img')
    expect(html).toContain('href="https://example.com/pixel.png"')
  })

  it('opens outside links in a new tab without handing over the panel', () => {
    const html = renderMarkdown('[GitHub](https://github.com)')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })
})
