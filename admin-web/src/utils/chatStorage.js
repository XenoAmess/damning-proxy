const DB_NAME = 'damning-proxy-chat'
const DB_VERSION = 1
const SESSION_STORE = 'sessions'

function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)
    request.onupgradeneeded = (event) => {
      const db = event.target.result
      if (!db.objectStoreNames.contains(SESSION_STORE)) {
        db.createObjectStore(SESSION_STORE, { keyPath: 'id' })
      }
    }
  })
}

export async function getAllSessions() {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSION_STORE, 'readonly')
    const store = tx.objectStore(SESSION_STORE)
    const request = store.getAll()
    request.onsuccess = () => resolve(request.result || [])
    request.onerror = () => reject(request.error)
  })
}

export async function saveAllSessions(sessions) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSION_STORE, 'readwrite')
    const store = tx.objectStore(SESSION_STORE)
    store.clear()
    for (const session of sessions) {
      store.put(session)
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function deleteSession(id) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSION_STORE, 'readwrite')
    const store = tx.objectStore(SESSION_STORE)
    const request = store.delete(id)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}

export async function clearAllSessions() {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(SESSION_STORE, 'readwrite')
    const store = tx.objectStore(SESSION_STORE)
    const request = store.clear()
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}
