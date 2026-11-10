function permissionNodes(nodes = [], parent = null, index = new Map()) {
  for (const node of nodes) {
    index.set(String(node.id), { ...node, parent })
    permissionNodes(node.children || [], node.id, index)
  }
  return index
}

// Module access and button grants are independent. Never expand saved parent grants.
export function permissionTreeState(nodes, keys = []) {
  const index = permissionNodes(nodes)
  // Keep grants for temporarily hidden/disabled modules when saving unrelated changes.
  const checked = [...new Map(keys.map(key => [String(key), index.get(String(key))?.id ?? key])).values()]
  const selected = new Set(checked.map(String))
  const halfChecked = new Set()
  for (const key of checked) {
    let parent = index.get(String(key))?.parent
    while (parent != null) {
      if (!selected.has(String(parent))) halfChecked.add(parent)
      parent = index.get(String(parent)).parent
    }
  }
  return { checked, halfChecked: [...halfChecked] }
}

// Cascade only after an explicit user click, not while loading or switching modes.
export function changePermissionSelection(nodes, keys, targetKey, checked, linked) {
  const index = permissionNodes(nodes)
  const target = index.get(String(targetKey))
  if (!target) return permissionTreeState(nodes, keys).checked
  const selected = new Map(permissionTreeState(nodes, keys).checked.map(key => [String(key), key]))
  const apply = node => {
    if (checked) selected.set(String(node.id), node.id)
    else selected.delete(String(node.id))
    if (linked) (node.children || []).forEach(apply)
  }
  apply(target)
  if (checked && linked) {
    let parent = target.parent
    while (parent != null) {
      selected.set(String(parent), parent)
      parent = index.get(String(parent)).parent
    }
  }
  return [...selected.values()]
}

export function allPermissionKeys(nodes) {
  return [...permissionNodes(nodes).values()].map(node => node.id)
}
