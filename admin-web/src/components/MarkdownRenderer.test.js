import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownRenderer from './MarkdownRenderer.vue'

vi.mock('../utils/clipboard.js', () => ({
  copyToClipboard: vi.fn().mockResolvedValue(undefined),
}))

import { copyToClipboard } from '../utils/clipboard.js'

describe('MarkdownRenderer', () => {
  it('renders markdown as html', () => {
    const wrapper = mount(MarkdownRenderer, { props: { content: '# Hello' } })
    expect(wrapper.html()).toContain('<h1>Hello</h1>')
  })

  it('sanitizes dangerous html', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '<script>alert(1)</script><p>safe</p>' },
    })
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.html()).toContain('<p>safe</p>')
  })

  it('adds copy button to code blocks', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconst a = 1\n```' },
    })
    const buttons = wrapper.findAll('.copy-code-btn')
    expect(buttons.length).toBe(1)
    expect(buttons[0].text()).toBe('复制')
  })

  it('copies code when copy button is clicked', async () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```\nhello\n```' },
    })
    const button = wrapper.find('.copy-code-btn')
    await button.trigger('click')
    expect(copyToClipboard).toHaveBeenCalled()
  })
})
