import { createApp } from 'vue'
import 'x-markdown-vue/style'

import './style.css'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
