import { describe, expect, it } from 'vitest'
import { buildTree, parentChoices } from './docs'
import type { PmDocNode } from './types'

function page(id: string, parentId?: string): PmDocNode {
  return { id, parentId, title: id, position: 1, version: 1, archived: false, updatedAt: '2026-10-23T00:00:00Z' }
}

const pages = [page('specs'), page('chat', 'specs'), page('album', 'chat'), page('audits')]

describe('buildTree', () => {
  it('nests each page under its parent and keeps orphans at the top', () => {
    const roots = buildTree([...pages, page('lost', 'gone')])
    expect(roots.map((node) => node.id)).toEqual(['specs', 'audits', 'lost'])
    expect(roots[0]!.children[0]!.children.map((node) => node.id)).toEqual(['album'])
  })
})

describe('parentChoices', () => {
  it('never offers a page itself or anything under it as its new parent', () => {
    expect(parentChoices(pages, 'chat').map((option) => option.id)).toEqual(['specs', 'audits'])
  })

  it('indents each choice by its depth', () => {
    expect(parentChoices(pages).map((option) => option.label)).toEqual(['specs', '— chat', '— — album', 'audits'])
  })
})
