const KIMI_CLI_VERSION = '1.41.0'
const USER_AGENT = `KimiCLI/${KIMI_CLI_VERSION}`

export { KIMI_CLI_VERSION, USER_AGENT }

export function asciiHeaderValue(value, fallback = 'unknown') {
  const sanitized = String(value || fallback)
    .replace(/[^\x20-\x7e]/g, '')
    .trim()
  return sanitized || fallback
}

function generateRandomHex(length) {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID().replace(/-/g, '').toLowerCase()
  }

  const array = new Uint8Array(length / 2)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(array)
  } else {
    for (let i = 0; i < array.length; i++) {
      array[i] = Math.floor(Math.random() * 256)
    }
  }
  return Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('')
}

export function generateKimiDeviceId() {
  return generateRandomHex(32)
}

export function buildKimiHeaders(deviceId, env = {}) {
  const locationHostname = env.locationHostname ?? globalThis.location?.hostname ?? 'unknown'
  const platform = env.platform ?? navigator?.platform ?? 'unknown'
  const userAgent = env.userAgent ?? navigator?.userAgent ?? 'unknown'

  return {
    'User-Agent': USER_AGENT,
    'X-Msh-Platform': 'kimi_cli',
    'X-Msh-Version': KIMI_CLI_VERSION,
    'X-Msh-Device-Name': asciiHeaderValue(locationHostname),
    'X-Msh-Device-Model': asciiHeaderValue(platform),
    'X-Msh-Device-Id': asciiHeaderValue(deviceId),
    'X-Msh-Os-Version': asciiHeaderValue(userAgent),
  }
}
