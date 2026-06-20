/**
 * Copy text to the system clipboard. Falls back to the legacy
 * document.execCommand technique when the page is served over HTTP
 * (insecure context where the Clipboard API is unavailable).
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // navigator.clipboard is only available in secure contexts
    const el = document.createElement('textarea')
    el.value = text
    el.setAttribute('readonly', '')
    el.style.position = 'absolute'
    el.style.left = '-9999px'
    document.body.appendChild(el)
    el.select()
    try {
      document.execCommand('copy')
    } catch {
      // execCommand('copy') can also fail on some browsers
      document.body.removeChild(el)
      throw new Error('Clipboard API and execCommand both unavailable')
    }
    document.body.removeChild(el)
  }
}
