import { createRouter, createWebHashHistory } from 'vue-router'
import Profiles from './views/Profiles.vue'
import Plugins from './views/Plugins.vue'
import PluginGroups from './views/PluginGroups.vue'
import Instances from './views/Instances.vue'
import Logs from './views/Logs.vue'

import Chat from './views/Chat.vue'

const routes = [
  { path: '/', redirect: '/instances' },
  { path: '/instances', component: Instances },
  { path: '/plugin-groups', component: PluginGroups },
  { path: '/plugins', component: Plugins },
  { path: '/profiles', component: Profiles },
  { path: '/chat', component: Chat },
  { path: '/logs', component: Logs },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
