import storage from 'store'
import { normalizeAppearance, appearanceStorageKey } from '/src/utils/appearance.mjs'

export default {
  namespaced: true,
  state: { preferences: normalizeAppearance(), username: '' },
  mutations: {
    load(state, { username, preferences }) {
      state.username = username
      state.preferences = normalizeAppearance(preferences)
    },
    set(state, preferences) { state.preferences = normalizeAppearance(preferences) }
  },
  actions: {
    load({ commit }, username) {
      let preferences
      try { preferences = username ? storage.get(appearanceStorageKey(username)) : undefined } catch { /* Use defaults if browser storage is unavailable. */ }
      commit('load', { username: username || '', preferences })
    },
    save({ commit, state }, value) {
      const preferences = normalizeAppearance(value)
      if (state.username) {
        try { storage.set(appearanceStorageKey(state.username), preferences) } catch { /* Keep the current session usable without browser storage. */ }
      }
      commit('set', preferences)
    }
  }
}
