import { createRouter, createWebHashHistory } from 'vue-router'
import Profiles from './views/Profiles.vue'
import Plugins from './views/Plugins.vue'
import PluginGroups from './views/PluginGroups.vue'
import Instances from './views/Instances.vue'
import Logs from './views/Logs.vue'

const routes = [
  { path: '/', redirect: '/instances' },
  { path: '/profiles', component: Profiles },
  { path: '/plugins', component: Plugins },
  { path: '/plugin-groups', component: PluginGroups },
  { path: '/instances', component: Instances },
  { path: '/logs', component: Logs },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
