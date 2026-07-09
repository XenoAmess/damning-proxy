import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

export function mountWithElementPlus(component, options = {}) {
  const { global = {}, attachTo, ...rest } = options
  return mount(component, {
    attachTo: attachTo === undefined ? document.body : attachTo,
    global: {
      plugins: [ElementPlus, ...(global.plugins || [])],
      ...global,
    },
    ...rest,
  })
}
