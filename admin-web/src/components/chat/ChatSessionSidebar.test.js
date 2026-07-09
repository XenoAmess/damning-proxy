import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ChatSessionSidebar from './ChatSessionSidebar.vue'

const sessions = [
  { id: '1', title: 'Chat 1' },
  { id: '2', title: 'Chat 2' },
]

function mountSidebar(props = {}) {
  return mount(ChatSessionSidebar, {
    global: { plugins: [ElementPlus] },
    props: { sessions, currentSessionId: '1', ...props },
  })
}

describe('ChatSessionSidebar', () => {
  it('renders session titles', () => {
    const wrapper = mountSidebar()
    expect(wrapper.text()).toContain('Chat 1')
    expect(wrapper.text()).toContain('Chat 2')
  })

  it('shows placeholder title for sessions without one', () => {
    const wrapper = mountSidebar({
      sessions: [{ id: '3', title: '' }],
      currentSessionId: '3',
    })
    expect(wrapper.text()).toContain('新会话')
  })

  it('emits create when new-session button is clicked', async () => {
    const wrapper = mountSidebar()
    await wrapper.find('.el-button--primary').trigger('click')
    expect(wrapper.emitted('create')).toBeTruthy()
  })

  it('emits switch when a session menu item is clicked', async () => {
    const wrapper = mountSidebar()
    const items = wrapper.findAll('.el-menu-item')
    await items[1].trigger('click')
    expect(wrapper.emitted('switch')).toBeTruthy()
    expect(wrapper.emitted('switch')[0]).toEqual(['2'])
  })
})
