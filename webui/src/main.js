import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n, { locale } from './i18n'
import './style.css'

document.documentElement.lang = locale.value
createApp(App).use(createPinia()).use(i18n).use(router).mount('#app')
