import { describe, it, expect, beforeEach } from 'vitest'
import { getAllSessions, saveAllSessions, deleteSession, clearAllSessions } from './chatStorage.js'

beforeEach(async () => {
  await clearAllSessions()
})

describe('chatStorage', () => {
  it('saves and retrieves sessions', async () => {
    await saveAllSessions([
      { id: '1', title: 'A' },
      { id: '2', title: 'B' },
    ])
    const sessions = await getAllSessions()
    expect(sessions).toHaveLength(2)
    expect(sessions.map((s) => s.title).sort()).toEqual(['A', 'B'])
  })

  it('deletes a single session', async () => {
    await saveAllSessions([
      { id: '1', title: 'A' },
      { id: '2', title: 'B' },
    ])
    await deleteSession('1')
    const sessions = await getAllSessions()
    expect(sessions).toHaveLength(1)
    expect(sessions[0].id).toBe('2')
  })

  it('clears all sessions', async () => {
    await saveAllSessions([{ id: '1', title: 'A' }])
    await clearAllSessions()
    const sessions = await getAllSessions()
    expect(sessions).toHaveLength(0)
  })
})
