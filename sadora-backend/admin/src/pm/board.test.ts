import { describe, expect, it } from 'vitest'
import { columns, dropPosition, positionBetween } from './board'
import type { PmTaskSummary } from './types'

function card(key: string, status: PmTaskSummary['status'], position: number): PmTaskSummary {
  return {
    id: key,
    key,
    title: key,
    type: 'task',
    status,
    priority: 'medium',
    components: [],
    labels: [],
    position,
    archived: false,
    subtasks: 0,
    subtasksDone: 0,
    comments: 0,
    createdAt: '2026-10-23T00:00:00Z',
    updatedAt: '2026-10-23T00:00:00Z',
  }
}

describe('positionBetween', () => {
  it('lands between its neighbours, or a step past the only one', () => {
    expect(positionBetween(1, 2)).toBe(1.5)
    expect(positionBetween(3, undefined)).toBe(4)
    expect(positionBetween(undefined, 3)).toBe(2)
    expect(positionBetween(undefined, undefined)).toBe(1)
  })
})

describe('columns', () => {
  it('puts every card in its status column, top to bottom by position', () => {
    const grouped = columns([card('SAD-2', 'todo', 2), card('SAD-1', 'todo', 1), card('SAD-3', 'done', 1)])
    expect(grouped.todo.map((task) => task.key)).toEqual(['SAD-1', 'SAD-2'])
    expect(grouped.done.map((task) => task.key)).toEqual(['SAD-3'])
    expect(grouped.backlog).toEqual([])
  })
})

describe('dropPosition', () => {
  const column = [card('SAD-1', 'todo', 1), card('SAD-2', 'todo', 2), card('SAD-3', 'todo', 3)]

  it('puts a card from another column at the slot it was dropped on', () => {
    expect(dropPosition(column, 'SAD-9', 0)).toBe(0)
    expect(dropPosition(column, 'SAD-9', 1)).toBe(1.5)
    expect(dropPosition(column, 'SAD-9', 3)).toBe(4)
  })

  it('ignores the dragged card itself when it moves inside its own column', () => {
    // SAD-1 dropped below SAD-2: between 2 and 3.
    expect(dropPosition(column, 'SAD-1', 2)).toBe(2.5)
    // SAD-3 dropped on top.
    expect(dropPosition(column, 'SAD-3', 0)).toBe(0)
  })
})
