<template>
  <figure class="rule-map" :aria-label="map.title">
    <figcaption><h3><a-icon type="branches" />{{ map.title }}</h3><p>{{ map.description }}</p></figcaption>
    <div class="rule-root">{{ map.root }}</div>
    <div class="rule-branches">
      <section v-for="(branch, index) in map.branches" :key="branch.title" class="rule-branch" :class="'branch-' + index">
        <h4>{{ branch.title }}</h4>
        <ol class="rule-nodes" :style="{ '--node-count': branch.nodes.length }">
          <li v-for="node in branch.nodes" :key="node.title" class="rule-node"><strong>{{ node.title }}</strong><p>{{ node.text }}</p></li>
        </ol>
        <p class="rule-note"><a-icon type="reload" />{{ branch.note }}</p>
      </section>
    </div>
    <p class="rule-result">{{ map.footer }}</p>
  </figure>
</template>

<script>
import { ruleMindMaps } from '../ruleMindMaps.js'

export default {
  name: 'RuleMindMap',
  props: {
    kind: { type: String, required: true, validator: value => Object.prototype.hasOwnProperty.call(ruleMindMaps, value) }
  },
  computed: {
    map() { return ruleMindMaps[this.kind] }
  }
}
</script>

<style lang="less" scoped>
.rule-map {
  min-width: 0; margin: 18px 0 24px; padding: 18px; border: 1px solid #dcece9;
  border-radius: 8px; background: #fbfefd; color: #475467; line-height: 1.7;
  h3 { margin: 0; font-size: 17px; font-weight: 600; color: #17212b; }
  h3 .anticon { margin-right: 8px; color: #0b9e92; }
  figcaption > p { margin: 6px 0 16px; color: #667085; }
}
.rule-root { width: fit-content; max-width: 100%; margin: 0 auto; padding: 9px 20px; border-radius: 6px; background: #0aafa2; color: #fff; text-align: center; font-weight: 600; }
.rule-branches {
  position: relative; display: grid; gap: 14px; padding-top: 18px;
  &::before { content: ''; position: absolute; top: 0; left: 50%; height: 18px; border-left: 2px solid #a8dcd5; }
}
.rule-branch {
  min-width: 0; padding: 14px; border: 1px solid #dcece9; border-radius: 6px; background: #fff;
  h4 { margin: 0 0 12px; color: #087c74; font-size: 14px; font-weight: 600; }
}
.rule-nodes { display: grid; grid-template-columns: repeat(var(--node-count), minmax(0, 1fr)); gap: 24px; padding: 0; margin: 0; list-style: none; }
.rule-node {
  position: relative; min-width: 0; padding: 10px; background: #eef9f6; border-radius: 5px; overflow-wrap: anywhere;
  strong { color: #087c74; font-weight: 600; }
  p { margin: 5px 0 0; font-size: 12px; }
  &:not(:last-child)::after { content: '→'; position: absolute; top: 50%; right: -19px; transform: translateY(-50%); color: #6d9f98; font-size: 17px; }
}
.rule-note { display: flex; align-items: baseline; gap: 7px; margin: 12px 0 0; color: #667085; font-size: 12px; }
.branch-1 {
  border-color: #dfe8f4;
  h4, .rule-node strong { color: #27649a; }
  .rule-node { background: #f0f5fc; }
  .rule-node::after { color: #7c9dc2; }
}
.branch-2 {
  border-color: #eee2ca;
  h4, .rule-node strong { color: #8a651f; }
  .rule-node { background: #fff8eb; }
  .rule-node::after { color: #b79b63; }
}
.rule-result { margin: 14px 0 0; color: #087c74; font-size: 12px; }
@media (max-width: 1100px) {
  .rule-nodes { grid-template-columns: 1fr; gap: 20px; }
  .rule-node:not(:last-child)::after { content: '↓'; top: auto; right: 50%; bottom: -23px; transform: translateX(50%); }
}
@media (max-width: 600px) {
  .rule-map, .rule-branch { padding: 12px; }
}
</style>
