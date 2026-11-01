import Vue from 'vue'

const configuration = Vue.observable({ actions: {} })

export function setModuleActionConfigurations(actions = []) {
  configuration.actions = Object.fromEntries(actions.map(action => [`${action.moduleId}:${action.actionCode}`, action]))
}

export function getModuleActionConfiguration(moduleId, actionCode) {
  return configuration.actions[`${moduleId}:${actionCode}`]
}

export function moduleActionName(moduleId, actionCode, fallback) {
  return getModuleActionConfiguration(moduleId, actionCode)?.actionName || fallback
}
