import type { PmDocNode } from './types'

/* The docs tree's small logic: nesting the flat list, and where a page may move. */

export interface TreeNode extends PmDocNode {
  children: TreeNode[]
}

/** The flat list as a tree; a page whose parent is not in the list sits at the top. */
export function buildTree(nodes: PmDocNode[]): TreeNode[] {
  const byId = new Map(nodes.map((node) => [node.id, { ...node, children: [] as TreeNode[] }]))
  const roots: TreeNode[] = []
  for (const node of byId.values()) {
    const parent = node.parentId ? byId.get(node.parentId) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/** The pages a page may move under: anything but itself and what hangs under it. */
export function parentChoices(nodes: PmDocNode[], self?: string): { id: string; label: string }[] {
  const blocked = new Set<string>()
  if (self) {
    blocked.add(self)
    let grew = true
    while (grew) {
      grew = false
      for (const node of nodes) {
        if (node.parentId && blocked.has(node.parentId) && !blocked.has(node.id)) {
          blocked.add(node.id)
          grew = true
        }
      }
    }
  }
  const out: { id: string; label: string }[] = []
  const walk = (list: TreeNode[], depth: number) =>
    list.forEach((node) => {
      if (blocked.has(node.id)) return
      out.push({ id: node.id, label: `${'— '.repeat(depth)}${node.title}` })
      walk(node.children, depth + 1)
    })
  walk(buildTree(nodes), 0)
  return out
}
