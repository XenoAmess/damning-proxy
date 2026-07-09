import { describe, it, expect } from 'vitest'
import { mountWithElementPlus } from '../../test-utils.js'
import LogCard from './LogCard.vue'

const baseLog = {
  id: 1,
  requestMethod: 'POST',
  requestPath: '/v1/proxy/inst/chat/completions',
  responseStatus: 200,
  requestTime: '2026-07-08T12:00:00.000Z',
  durationMs: 120,
  totalTokens: 42,
  streaming: true,
  instanceSlug: 'inst',
  _friendly: {
    userPrompt: 'hello',
    modelOutput: 'hi',
    requestMessages: [{}, {}, {}],
  },
}

function mountLogCard(log = {}, setCardRef = () => {}) {
  return mountWithElementPlus(LogCard, {
    props: { log: { ...baseLog, ...log }, setCardRef },
  })
}

describe('LogCard', () => {
  it('renders log meta information', () => {
    const wrapper = mountLogCard()
    expect(wrapper.text()).toContain('#1')
    expect(wrapper.text()).toContain('POST')
    expect(wrapper.text()).toContain('/v1/proxy/inst/chat/completions')
    expect(wrapper.text()).toContain('200')
    expect(wrapper.text()).toContain('inst')
  })

  it('adds error class for response status >= 400', () => {
    const wrapper = mountLogCard({ responseStatus: 500 })
    expect(wrapper.find('.log-card').classes()).toContain('error')
  })

  it('shows user prompt and model output for chat-like paths', () => {
    const wrapper = mountLogCard()
    expect(wrapper.text()).toContain('hello')
    expect(wrapper.text()).toContain('hi')
  })

  it('shows request type for non-chat paths', () => {
    const wrapper = mountLogCard({ requestPath: '/v1/proxy/inst/models' })
    expect(wrapper.text()).toContain('/v1/proxy/inst/models')
    expect(wrapper.text()).not.toContain('用户输入')
  })

  it('emits open when card body is clicked', async () => {
    const wrapper = mountLogCard()
    await wrapper.find('.log-card-body').trigger('click')
    expect(wrapper.emitted('open')).toBeTruthy()
    expect(wrapper.emitted('open')[0]).toEqual([1])
  })

  it('emits remove when delete button is clicked', async () => {
    const wrapper = mountLogCard()
    const deleteBtn = wrapper.findAll('.el-button').find((btn) => btn.text().includes('删除'))
    await deleteBtn.trigger('click')
    expect(wrapper.emitted('remove')).toBeTruthy()
    expect(wrapper.emitted('remove')[0]).toEqual([1])
  })

  it('emits open on enter key', async () => {
    const wrapper = mountLogCard()
    await wrapper.find('.log-card-body').trigger('keydown.enter')
    expect(wrapper.emitted('open')).toBeTruthy()
  })
})
