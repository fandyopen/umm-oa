import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const source = fs.readFileSync(new URL('../../src/views/eos/recruitment/monitor/metrics.js', import.meta.url), 'utf8')
const { rankedPeople } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))

test('leaderboards keep ties, omit zero results and never reorder the personnel table', () => {
  const people = [
    { userId: 1, userName: 'A', entered: 2 },
    { userId: 2, userName: 'B', entered: 5 },
    { userId: 3, userName: 'C', entered: 0 },
    { userId: 4, userName: 'D', entered: 5 }
  ]
  assert.deepEqual(rankedPeople(people, 'entered').map(row => [row.userId, row.rank]), [[2, 1], [4, 1], [1, 3]])
  assert.deepEqual(people.map(row => row.userId), [1, 2, 3, 4])
  assert.equal(people[0].rank, undefined)
  assert.deepEqual(rankedPeople([], 'entered'), [])
})
