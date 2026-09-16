<script setup>
import { Languages } from 'lucide-vue-next'
import { useI18n } from '../i18n'

const { locale, supportedLocales, setLocale, t } = useI18n()
</script>

<template>
  <label class="language-switcher" :title="t('common.language')">
    <Languages :size="16" aria-hidden="true" />
    <span class="sr-only">{{ t('common.language') }}</span>
    <select :value="locale" :aria-label="t('common.language')" @change="setLocale($event.target.value)">
      <option v-for="option in supportedLocales" :key="option.code" :value="option.code">{{ option.label }}</option>
    </select>
  </label>
</template>
