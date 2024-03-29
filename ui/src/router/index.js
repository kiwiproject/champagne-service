import {createRouter, createWebHistory} from 'vue-router'
import {useCurrentUserStore} from "@/stores/currentUser";
import {useEnvironmentStore} from "@/stores/environments";

import HostConfigView from "@/views/Observability/HostConfigView.vue";
import LoginView from "@/views/LoginView.vue";
import NoDeployableSystemView from "@/views/NoDeployableSystemView.vue";
import EnvironmentView from "@/views/Observability/EnvironmentView.vue";
import ManageSystemView from "@/views/SystemAdmin/ManageSystemView.vue";
import ComponentView from "@/views/Observability/ComponentView.vue";
import TagView from "@/views/SystemAdmin/TagView.vue";
import BuildView from "@/views/Observability/BuildView.vue";
import TasksView from "@/views/Observability/TasksView.vue";
import SystemUsersView from "@/views/SystemAdmin/SystemUsersView.vue";
import UsersView from "@/views/ChampagneAdmin/UsersView.vue";
import SystemAuditsView from "@/views/SystemAdmin/SystemAuditsView.vue";
import AuditsView from "@/views/ChampagneAdmin/AuditsView.vue";
import SystemsView from "@/views/ChampagneAdmin/SystemsView.vue";
import ErrorsView from "@/views/ChampagneAdmin/ErrorsView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('../layouts/ChampagneLayout.vue'),
      children: [
        { path: '/', redirect: { name: 'hosts' }, name: 'root' },
        { path: '/hosts', component: HostConfigView, name: 'hosts' },
        { path: '/components', component: ComponentView, name: 'components' },
        { path: '/environments', component: EnvironmentView, name: 'environments' },
        { path: '/manageSystem', component: ManageSystemView, name: 'manageSystem' },
        { path: '/tags', component: TagView, name: 'tags' },
        { path: '/builds', component: BuildView, name: 'builds' },
        { path: '/tasks', component: TasksView, name: 'tasks' },
        { path: '/systemUsers', component: SystemUsersView, name: 'systemUsers' },
        { path: '/users', component: UsersView, name: 'users' },
        { path: '/systemAudits', component: SystemAuditsView, name: 'systemAudits' },
        { path: '/audits', component: AuditsView, name: 'audits' },
        { path: '/systems', component: SystemsView, name: 'systems' },
        { path: '/errors', component: ErrorsView, name: 'errors' },
        { path: '/noDeployableSystem', component: NoDeployableSystemView, name: 'noDeployableSystem' }
      ]
    },
    {
      path: '/login',
      component: () => import('../layouts/NotLoggedInLayout.vue'),
      children: [
        { path: '/', component: LoginView, name: 'login' }
      ]
    }
  ]
});

router.beforeEach(async (to) => {
  const currentUserStore = useCurrentUserStore();
  const environmentUserStore = useEnvironmentStore();

  if (!currentUserStore.isLoggedIn) {
    // Attempt to pull user info first just in case we have a session already
    await currentUserStore.loadUserInfo();
  }

  if (!currentUserStore.isLoggedInAndSystemChosen && to.name !== 'login' && to.name !== 'noDeployableSystem') {
    if (!currentUserStore.isLoggedIn) {
      return { name: 'login' };
    } else {
      await currentUserStore.loadDeployableSystems();

      if (currentUserStore.deployableSystems.length === 0) {
        return { name: 'login' };
      } else if (currentUserStore.deployableSystems.length === 1) {
        currentUserStore.makeDeployableSystemActive(currentUserStore.deployableSystems[0]);
        await environmentUserStore.loadEnvironments();
      } else {
        return { name: 'noDeployableSystem' }
      }
    }
  } else if (to.name !== 'login' && to.name !== 'noDeployableSystem') {
    await currentUserStore.loadDeployableSystems();
    await environmentUserStore.loadEnvironments();
  }
});

export default router
