<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useHead } from "@vueuse/head";
import { useRoute } from "vue-router";
import { computed, ref, watch } from "vue";
import { NewProjectForm } from "hangar-internal";
import { useVuelidate } from "@vuelidate/core";
import { AxiosError } from "axios";
import PageTitle from "~/lib/components/design/PageTitle.vue";
import { useSeo } from "~/composables/useSeo";
import Steps from "~/lib/components/design/Steps.vue";
import { Step } from "~/lib/types/components/design/Steps";
import Link from "~/lib/components/design/Link.vue";
import { convertSpigotProjects, getAllSpigotResourcesByAuthor, getSpigotAuthor, SpigotAuthor, SpigotResource } from "~/composables/useProjectImporter";
import InputText from "~/lib/components/ui/InputText.vue";
import InputSelect from "~/lib/components/ui/InputSelect.vue";
import UserAvatar from "~/components/UserAvatar.vue";
import Spoiler from "~/lib/components/design/Spoiler.vue";
import MarkdownEditor from "~/components/MarkdownEditor.vue";
import InputTag from "~/lib/components/ui/InputTag.vue";
import { useAuthStore } from "~/store/auth";
import { useBackendData, useCategoryOptions, useLicenseOptions } from "~/store/backendData";
import { minLength, maxLength, pattern, required, requiredIf, url } from "~/lib/composables/useValidationHelpers";
import { validProjectName } from "~/composables/useHangarValidations";
import Button from "~/lib/components/design/Button.vue";
import { useInternalApi } from "~/composables/useApi";
import { handleRequestError } from "~/composables/useErrorHandling";
import Spinner from "~/lib/components/design/Spinner.vue";
import { reactive } from "#imports";
import Alert from "~/lib/components/design/Alert.vue";
import IconMdiFileDocumentAlert from "~icons/mdi/file-document-alert";
import InputCheckbox from "~/lib/components/ui/InputCheckbox.vue";
import InputGroup from "~/lib/components/ui/InputGroup.vue";
import { ProjectCategory } from "~/types/enums";

const { t } = useI18n();
const route = useRoute();
const auth = useAuthStore();

const selectedStep = ref("intro");
const steps: Step[] = [
  {
    value: "intro",
    header: t("importer.step1.title"),
    showBack: false,
  },
  {
    value: "userSelection",
    header: t("importer.step2.title"),
    showBack: false,
    disableNext: computed(() => v.value.$errors.length > 0 || v.value.$pending),
    beforeNext: async () => {
      if (!(await v.value.$validate())) {
        return false;
      }
      spigotResources.value = [];
      if (spigotAuthor.value) {
        spigotResources.value = await getAllSpigotResourcesByAuthor(spigotAuthor.value.id);
        return true;
      } else {
        return false;
      }
    },
  },
  {
    value: "projectSelection",
    header: t("importer.step3.title"),
    showBack: true,
    disableNext: computed(() => v.value.$errors.length > 0 || v.value.$pending),
    showNext: computed(() => spigotResources.value?.length > 0),
    beforeNext: async () => {
      if (!(await v.value.$validate())) {
        return false;
      }
      hangarResources.value = [];
      if (auth.user) {
        // todo allow selecting owner id
        const ownerId = auth.user.id;
        const selected = spigotResources.value.filter((r) => selectedSpigotResources.value.includes(r.id));
        hangarResources.value = await convertSpigotProjects(selected, ownerId);
        return true;
      } else {
        return false;
      }
    },
  },
  {
    value: "projectConversion",
    header: t("importer.step4.title"),
    disableNext: computed(() => v.value.$errors.length > 0 || v.value.$pending),
    beforeNext: async () => {
      if (!(await v.value.$validate())) {
        return false;
      }
      createProjects();
      return true;
    },
  },
  { value: "finishing", header: t("importer.step5.title"), showNext: false, showBack: false },
];

const username = ref();
const spigotAuthor = ref<SpigotAuthor | null>(null);
const spigotResources = ref<SpigotResource[]>([]);
const selectedSpigotResources = ref<string[]>([]);
const hangarResources = ref<NewProjectForm[]>([]);

const additionalRules = {
  spigotAuthor: {
    required,
  },
};
const additionalModel = {
  spigotAuthor: spigotAuthor.value,
};
const v = useVuelidate(additionalRules, additionalModel);

watch(username, async () => {
  spigotAuthor.value = null;
  // TODO we need a debounce here but for some reason it doesn't work
  try {
    spigotAuthor.value = await getSpigotAuthor(username.value);
  } catch (e: AxiosError) {
    // don't need popup about not found and wrong format
    if (!e.message?.includes("404") && !e.message?.includes("400")) {
      handleRequestError(e);
    }
  }
});

function remove(project: NewProjectForm) {
  hangarResources.value = hangarResources.value.filter((p) => p !== project);
}

const status = reactive<Record<string, { project: NewProjectForm; loading: boolean; success: boolean; result: string; errors: string[] }>>({});

function createProjects() {
  for (const project of hangarResources.value) {
    createProject(project);
  }
}

function createProject(project: NewProjectForm) {
  status[project.name] = { project, loading: true, success: true, result: "", errors: [] };
  if (!project.pageContent) {
    project.pageContent = "# " + project.name + "  \nWelcome to your new project!";
  }
  if (!project.util.isCustomLicense.value) {
    project.settings.license.name = null;
  }
  if (project.util.licenseUnset.value) {
    project.settings.license.url = null;
  }
  useInternalApi<string>("projects/create", "post", project, { timeout: 10000 })
    .then((u) => {
      status[project.name].success = true;
      status[project.name].result = u;
    })
    .catch((err) => {
      if (err.response?.data.fieldErrors != null) {
        for (const e of err.response.data.fieldErrors) {
          status[project.name].errors.push(t(e.errorMsg));
        }
      } else {
        status[project.name].errors.push("Unknown error");
      }

      handleRequestError(err, "project.new.error.create");
    })
    .finally(() => {
      status[project.name].loading = false;
    });
}

useHead(useSeo(t("importer.title"), null, route, null));
</script>

<template>
  <div>
    <PageTitle>{{ t("importer.title") }}</PageTitle>

    <Steps v-model="selectedStep" :steps="steps" button-lang-key="importer.step">
      <template #intro>
        <p>{{ t("importer.step1.text1") }}</p>
        <p class="inline-flex items-center space-x-2">
          <IconMdiFileDocumentAlert />
          <Link to="/guidelines">
            {{ t("importer.step1.text2") }}
          </Link>
        </p>
      </template>
      <template #userSelection>
        <p class="mb-2">{{ t("importer.step2.text") }}</p>
        <InputText v-model="username" label="Username" :rules="[required()]" />
        <div v-if="spigotAuthor" class="flex items-center mt-2">
          <UserAvatar :username="spigotAuthor.username" :avatar-url="spigotAuthor.avatar" disable-link size="md" />
          <div class="ml-4">
            <div class="text-xl">{{ spigotAuthor.username }} (#{{ spigotAuthor.id }})</div>
            {{ spigotAuthor.resource_count }} resources
          </div>
        </div>
        <div v-else-if="username" class="mt-2">Nothing found...</div>
      </template>
      <template #projectSelection>
        <Alert v-if="spigotResources.length === 0">No projects found!</Alert>
        <InputGroup
          v-model="selectedSpigotResources"
          :rules="[required('Select at least one project!'), minLength()(1)]"
          :label="t('importer.step3.label')"
          :silent-errors="false"
        >
          <div class="grid gap-1" style="grid-template-columns: repeat(auto-fit, minmax(250px, 1fr))">
            <div v-for="project in spigotResources" :key="project.id" class="inline-flex">
              <InputCheckbox v-model="selectedSpigotResources" :label="project.title" :value="project.id" class="flex-shrink-0" />
            </div>
          </div>
        </InputGroup>
      </template>
      <template #projectConversion>
        <div v-for="project in hangarResources" :key="project.externalId" class="flex mb-4 pb-4 border-b-2px">
          <div>
            <UserAvatar :username="project.name" :avatar-url="project.avatarUrl" disable-link class="flex-shrink-0" />
            <div class="text-xl mt-4"><Button @click="remove(project)">Remove</Button></div>
          </div>
          <div class="ml-4 flex-grow">
            <!-- todo rules and shit -->
            <div class="text-xl">
              <InputText
                v-model="project.name"
                label="Name"
                :maxlength="useBackendData.validations.project.name.max"
                counter
                :rules="[
                  required(),
                  maxLength()(useBackendData.validations.project.name.max),
                  pattern()(useBackendData.validations.project.name.regex),
                  validProjectName()(() => project.ownerId),
                ]"
              />
            </div>
            <div class="mt-2">
              <InputText
                v-model="project.description"
                label="Description"
                :rules="[required()]"
                :maxlength="useBackendData.validations.project.desc.max"
                counter
              />
            </div>
            <div class="mt-2">
              <InputSelect
                v-model="project.category"
                :values="useCategoryOptions"
                label="Category"
                :rules="[required(), (category) => category !== ProjectCategory.UNDEFINED]"
                i18n-text-values
              />
            </div>
            <Spoiler title="Description" :open="false" class="!max-w-full mt-2">
              <template #content>
                <ClientOnly>
                  <MarkdownEditor :raw="project.pageContent" :editing="true" :deletable="false" :saveable="false" :cancellable="false" no-padding-top />
                </ClientOnly>
              </template>
            </Spoiler>
            <Spoiler title="Additional Information" :open="false" class="!max-w-full mt-2">
              <template #content>
                <p>{{ t("project.new.step3.description") }}</p>
                <div class="text-lg mt-4 flex gap-2 items-center">
                  <IconMdiLink />
                  {{ t("project.new.step3.links") }}
                  <hr />
                </div>
                <div class="flex flex-wrap">
                  <div class="basis-full mt-4">
                    <InputText v-model.trim="project.settings.homepage" :label="t('project.new.step3.homepage')" :rules="[url()]" />
                  </div>
                  <div class="basis-full mt-4">
                    <InputText v-model.trim="project.settings.issues" :label="t('project.new.step3.issues')" :rules="[url()]" />
                  </div>
                  <div class="basis-full mt-4">
                    <InputText v-model.trim="project.settings.source" :label="t('project.new.step3.source')" :rules="[url()]" />
                  </div>
                  <div class="basis-full mt-4">
                    <InputText v-model.trim="project.settings.support" :label="t('project.new.step3.support')" :rules="[url()]" />
                  </div>
                  <div class="basis-full mt-4"><InputText v-model.trim="project.settings.wiki" :label="t('project.new.step3.wiki')" :rules="[url()]" /></div>
                </div>
                <div class="text-lg mt-6 flex gap-2 items-center">
                  <IconMdiLicense />
                  {{ t("project.new.step3.license") }}
                  <hr />
                </div>
                <div class="flex md:gap-2 lt-md:flex-wrap">
                  <div class="basis-full mt-4" :md="project.util.isCustomLicense ? 'basis-4/12' : 'basis-6/12'">
                    <InputSelect
                      v-model="project.settings.license.type"
                      :values="useLicenseOptions"
                      :label="t('project.new.step3.type')"
                      :rules="[required()]"
                    />
                  </div>
                  <div v-if="project.util.isCustomLicense" class="basis-full md:basis-8/12 mt-4">
                    <InputText
                      v-model.trim="project.settings.license.name"
                      :label="t('project.new.step3.customName')"
                      :rules="[
                        requiredIf()(project.util.isCustomLicense),
                        maxLength()(useBackendData.validations.project.license.max),
                        pattern()(useBackendData.validations.project.license.regex),
                      ]"
                    />
                  </div>
                  <div v-if="!project.util.licenseUnset" class="basis-full mt-4" :md="project.util.isCustomLicense ? 'basis-full' : 'basis-6/12'">
                    <InputText v-model.trim="project.settings.license.url" :label="t('project.new.step3.url')" :rules="[url()]" />
                  </div>
                </div>
                <div class="text-lg mt-6 flex gap-2 items-center">
                  <IconMdiCloudSearch />
                  {{ t("project.new.step3.keywords") }}
                  <hr />
                </div>
                <div class="flex">
                  <div class="mt-4 basis-full">
                    <InputTag
                      v-model="project.settings.keywords"
                      :label="t('project.new.step3.keywords')"
                      :rules="[maxLength()(useBackendData?.validations?.project?.keywords?.max || 5)]"
                      :maxlength="useBackendData?.validations?.project?.keywords?.max || 5"
                    />
                  </div>
                </div>
              </template>
            </Spoiler>
          </div>
        </div>
      </template>
      <template #finishing>
        <div class="flex flex-col">
          <div v-for="s in status" :key="s.project.name" class="text-center mb-2">
            <div class="text-xl font-bold">{{ s.project.name }}</div>
            <div v-if="s.loading"><Spinner class="stroke-red-500" :diameter="20" :stroke="2" /></div>
            <template v-else-if="s.errors && s.errors.length > 0">
              <div>
                {{ t("project.new.error.create") }}
                {{ s.errors }}
              </div>
              <div class="mt-2"><Button @click="createProject(s.project)"> Retry </Button></div>
            </template>
            <template v-else-if="s.success">
              Success!
              <Link :href="s.result" target="_blank">Open project</Link>
            </template>
          </div>
          <Alert class="text-center">Don't forget to upload your jar files!</Alert>
        </div>
      </template>
    </Steps>
  </div>
</template>
