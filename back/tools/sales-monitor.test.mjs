import test from 'node:test'
import assert from 'node:assert/strict'
import { rankedPeople } from '../../src/views/eos/crm/salesPerformance/metrics.js'

test('sales ranks retain zero-workload members and ties without mutating people', () => {
  const people = [{ userId: 4, newCustomers: 0 }, { userId: 3, newCustomers: 2 }, { userId: 2, newCustomers: 2 }, { userId: 1, newCustomers: 1 }]
  const ranks = rankedPeople(people, 'newCustomers')
  assert.deepEqual(ranks.map(p => [p.userId, p.rank]), [[2, 1], [3, 1], [1, 3], [4, 4]])
  assert.equal(people[0].userId, 4)
  assert.equal(people[0].rank, undefined)
})

test('each ranking uses its own result and retains members with no signed contracts', () => {
  const people = [{ userId: 1, newCustomers: 5, signedContracts: 0 }, { userId: 2, newCustomers: 1, signedContracts: 3 }, { userId: 3, newCustomers: 0, signedContracts: 0 }]
  assert.deepEqual(rankedPeople(people, 'newCustomers').map(p => p.userId), [1, 2, 3])
  assert.deepEqual(rankedPeople(people, 'signedContracts').map(p => [p.userId, p.rank]), [[2, 1], [1, 2], [3, 2]])
  assert.deepEqual(rankedPeople([], 'signedContracts'), [])
})
