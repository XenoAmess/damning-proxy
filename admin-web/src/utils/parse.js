import DOMPurify from 'dompurify'

export function parseThink(content) {
  if (typeof content !== 'string') {
    return { text: content || '', reasoning: '' }
  }
  const match = content.match(/<think>([\s\S]*?)<\/think>/)
  if (!match) {
    return { text: content, reasoning: '' }
  }
  const reasoning = match[1].trim()
  const text = content.replace(/<think>[\s\S]*?<\/think>/, '').trim()
  return { text, reasoning }
}

export function sanitizeHtml(html) {
  if (typeof html !== 'string') return html
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'pre', 'a', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'img', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'target'],
  })
}

export function formatBytes(value) {
  if (value === null || value === undefined) return '-'
  const n = Number(value)
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(2) + ' MB'
  return (n / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}
