// Refresh shared HR province/city options from the project's upstream administrative dataset.
// Source: https://github.com/modood/Administrative-divisions-of-China (MIT).
import fs from 'node:fs/promises'
const response = await fetch('https://raw.githubusercontent.com/modood/Administrative-divisions-of-China/master/dist/pca-code.json')
if (!response.ok) throw new Error('Location dataset download failed: ' + response.status)
const source = await response.json()
const locations = source.map(p => ({ name: p.name, cities: p.children.flatMap(c => /直辖县/.test(c.name) ? c.children.map(a => a.name) : [c.name]) }))
locations.push(
  { name: '香港特别行政区', cities: ['香港特别行政区'] },
  { name: '澳门特别行政区', cities: ['澳门特别行政区'] },
  { name: '台湾省', cities: ['台北市', '新北市', '桃园市', '台中市', '台南市', '高雄市', '基隆市', '新竹市', '嘉义市', '新竹县', '苗栗县', '彰化县', '南投县', '云林县', '嘉义县', '屏东县', '宜兰县', '花莲县', '台东县', '澎湖县', '金门县', '连江县'] }
)
await fs.writeFile(new URL('../src/main/resources/hr-work-locations.json', import.meta.url), JSON.stringify(locations, null, 2) + '\n')
console.log('Generated ' + locations.length + ' province options')
