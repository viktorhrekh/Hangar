<script lang="ts" setup>
import { useI18n } from "vue-i18n";
import { HangarProject, HangarVersion, IPlatform } from "hangar-internal";
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import Button from "~/lib/components/design/Button.vue";
import Modal from "~/lib/components/modals/Modal.vue";
import { Platform } from "~/types/enums";
import { handleRequestError } from "~/composables/useErrorHandling";
import { useInternalApi } from "~/composables/useApi";
import VersionSelector from "~/components/VersionSelector.vue";

const props = defineProps<{
  project: HangarProject;
  version: HangarVersion;
  platform: IPlatform;
}>();

const i18n = useI18n();
const route = useRoute();
const router = useRouter();

const projectVersion = computed(() => {
  return props.version;
});

const loading = ref(false);
const selectedVersions = ref(projectVersion.value?.platformDependencies[props.platform.name.toUpperCase() as Platform]);

function save() {
  loading.value = true;
  useInternalApi(`versions/version/${props.project.id}/${projectVersion.value?.id}/savePlatformVersions`, "post", {
    platform: props.platform.name.toUpperCase(),
    versions: selectedVersions.value,
  })
    .catch((e) => handleRequestError(e))
    .then(async () => {
      await router.go(0);
    })
    .finally(() => {
      loading.value = false;
    });
}
</script>

<template>
  <Modal :title="i18n.t('version.edit.platformVersions', [platform.name])" window-classes="w-200">
    <div class="flex flex-row flex-wrap gap-5">
      <VersionSelector v-model="selectedVersions" :versions="platform.possibleVersions" open />
    </div>

    <Button class="mt-3" :disabled="loading" @click="save">{{ i18n.t("general.save") }}</Button>
    <template #activator="{ on }">
      <Button class="text-sm" v-bind="$attrs" v-on="on"><IconMdiPencil /></Button>
    </template>
  </Modal>
</template>
