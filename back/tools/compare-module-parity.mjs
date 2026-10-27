import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const toolDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(toolDir, '..')
const workspaceDir = path.resolve(projectDir, '..')
const docsDir = path.join(projectDir, 'docs')
const routes = JSON.parse(
  fs.readFileSync(path.join(projectDir, 'db/source/remote-routers.json'), 'utf8')
).data

const modules = []
function walk(items, topLevel) {
  for (const item of items || []) {
    const component = item.component || ''
    const special = ['Layout', 'ParentView'].includes(component)
    const componentFile = component && !special
      ? path.join(workspaceDir, 'src/views', `${component}.vue`)
      : null
    modules.push({
      moduleId: item.moduleId,
      title: item.meta?.title || item.moduleName,
      path: item.path || '',
      component,
      topLevel,
      hasChildren: Boolean(item.children?.length),
      componentExists: special || Boolean(componentFile && fs.existsSync(componentFile)),
      componentFile: componentFile
        ? path.relative(workspaceDir, componentFile).replaceAll('\\', '/')
        : ''
    })
    walk(item.children, topLevel)
  }
}

for (const route of routes) {
  walk([route], route.meta?.title || route.moduleName)
}

const leaves = modules.filter(module => !module.hasChildren)
const missingComponents = leaves.filter(module => !module.componentExists)
const byArea = routes.map(route => {
  const areaModules = modules.filter(module => module.topLevel === (route.meta?.title || route.moduleName))
  return {
    title: route.meta?.title || route.moduleName,
    modules: areaModules.length,
    leaves: areaModules.filter(module => !module.hasChildren).length,
    missingComponents: areaModules.filter(module => !module.hasChildren && !module.componentExists).length
  }
})

fs.mkdirSync(docsDir, { recursive: true })
fs.writeFileSync(
  path.join(docsDir, 'module-parity.json'),
  `${JSON.stringify({ totals: {
    areas: routes.length,
    modules: modules.length,
    leaves: leaves.length,
    missingComponents: missingComponents.length
  }, byArea, modules, missingComponents }, null, 2)}\n`
)

const lines = [
  '# Module Parity',
  '',
  `Reference modules: **${modules.length}**`,
  '',
  `Leaf pages: **${leaves.length}**`,
  '',
  `Missing local Vue components: **${missingComponents.length}**`,
  '',
  '## By Area',
  '',
  '| Area | Modules | Leaf pages | Missing components |',
  '| --- | ---: | ---: | ---: |',
  ...byArea.map(area =>
    `| ${area.title} | ${area.modules} | ${area.leaves} | ${area.missingComponents} |`
  ),
  '',
  '## Missing Components',
  '',
  '| Area | Page | Component |',
  '| --- | --- | --- |',
  ...missingComponents.map(module =>
    `| ${module.topLevel} | ${module.title} | \`${module.component || '(empty)'}\` |`
  )
]
fs.writeFileSync(path.join(docsDir, 'module-parity.md'), `${lines.join('\n')}\n`)
console.log(
  `Compared ${modules.length} modules: ${missingComponents.length} missing component files`
)
