package io.papermc.hangar.service.internal.projects;

import io.papermc.hangar.db.customtypes.LoggedActionType;
import io.papermc.hangar.db.customtypes.LoggedActionType.ProjectContext;
import io.papermc.hangar.db.dao.HangarDao;
import io.papermc.hangar.db.dao.internal.HangarProjectsDAO;
import io.papermc.hangar.db.dao.internal.HangarUsersDAO;
import io.papermc.hangar.db.dao.internal.table.projects.ProjectsDAO;
import io.papermc.hangar.exceptions.HangarApiException;
import io.papermc.hangar.model.api.project.Project;
import io.papermc.hangar.model.common.Permission;
import io.papermc.hangar.model.db.OrganizationTable;
import io.papermc.hangar.model.db.UserTable;
import io.papermc.hangar.model.db.projects.ProjectOwner;
import io.papermc.hangar.model.db.projects.ProjectTable;
import io.papermc.hangar.model.db.roles.ProjectRoleTable;
import io.papermc.hangar.model.internal.HangarProject;
import io.papermc.hangar.model.internal.HangarProject.HangarProjectInfo;
import io.papermc.hangar.model.internal.HangarProjectFlag;
import io.papermc.hangar.model.internal.HangarProjectPage;
import io.papermc.hangar.model.internal.api.requests.projects.ProjectSettingsForm;
import io.papermc.hangar.model.internal.user.JoinableMember;
import io.papermc.hangar.service.HangarService;
import io.papermc.hangar.service.VisibilityService.ProjectVisibilityService;
import io.papermc.hangar.service.internal.OrganizationService;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
public class ProjectService extends HangarService {

    public static final String AUTHOR = "author";
    public static final String SLUG = "slug";

    private final ProjectsDAO projectsDAO;
    private final HangarUsersDAO hangarUsersDAO;
    private final HangarProjectsDAO hangarProjectsDAO;
    private final ProjectVisibilityService projectVisibilityService;
    private final OrganizationService organizationService;
    private final ProjectPageService projectPageService;

    @Autowired
    public ProjectService(HangarDao<ProjectsDAO> projectDAO, HangarDao<HangarUsersDAO> hangarUsersDAO, HangarDao<HangarProjectsDAO> hangarProjectsDAO, ProjectVisibilityService projectVisibilityService, OrganizationService organizationService, ProjectPageService projectPageService) {
        this.projectsDAO = projectDAO.get();
        this.hangarUsersDAO = hangarUsersDAO.get();
        this.hangarProjectsDAO = hangarProjectsDAO.get();
        this.projectVisibilityService = projectVisibilityService;
        this.organizationService = organizationService;
        this.projectPageService = projectPageService;
    }

    @Nullable
    public ProjectTable getProjectTable(@Nullable Long projectId) {
        return getProjectTable(projectId, projectsDAO::getById);
    }

    public ProjectTable getProjectTable(@Nullable String author, @Nullable String slug) {
        return getProjectTable(author, slug, projectsDAO::getBySlug);
    }

    @Nullable
    public ProjectOwner getProjectOwner(long userId) {
        if (Objects.equals(getHangarUserId(), userId)) {
            return getHangarPrincipal();
        }
        return organizationService.getOrganizationTablesWithPermission(userId, Permission.CreateProject).stream().filter(ot -> ot.getUserId() == userId).findFirst().orElse(null);
    }

    public ProjectOwner getProjectOwner(String userName) {
        Pair<UserTable, OrganizationTable> pair = hangarUsersDAO.getUserAndOrg(userName);
        if (pair.getRight() != null) {
            return pair.getRight();
        }
        return pair.getLeft();
    }

    public HangarProject getHangarProject(String author, String slug) {
        Pair<Long, Project> project = hangarProjectsDAO.getProject(author, slug, getHangarUserId());
        ProjectOwner projectOwner = getProjectOwner(author);
        List<JoinableMember<ProjectRoleTable>> members = hangarProjectsDAO.getProjectMembers(project.getLeft());
        // only include visibility change if not public (and if so, only include the user and comment)
        HangarProjectInfo info = hangarProjectsDAO.getHangarProjectInfo(project.getLeft());
        Map<Long, HangarProjectPage> pages = projectPageService.getProjectPages(project.getLeft());
        return new HangarProject(project.getRight(), project.getLeft(), projectOwner, members, "", "", info, pages.values());
    }

    public void saveSettings(String author, String slug, ProjectSettingsForm settingsForm) {
        ProjectTable projectTable = getProjectTable(author, slug);
        if (projectTable == null) {
            throw new HangarApiException(HttpStatus.NOT_FOUND);
        }
        projectTable.setCategory(settingsForm.getCategory());
        projectTable.setKeywords(settingsForm.getSettings().getKeywords());
        projectTable.setHomepage(settingsForm.getSettings().getHomepage());
        projectTable.setIssues(settingsForm.getSettings().getIssues());
        projectTable.setSource(settingsForm.getSettings().getSource());
        projectTable.setSupport(settingsForm.getSettings().getSupport());
        projectTable.setLicenseName(settingsForm.getSettings().getLicense().getName());
        projectTable.setLicenseUrl(settingsForm.getSettings().getLicense().getUrl());
        projectTable.setForumSync(settingsForm.getSettings().isForumSync());
        projectTable.setDescription(settingsForm.getDescription());
        projectsDAO.update(projectTable);
        // TODO is icon change?
        // TODO role updates
        refreshHomeProjects();
        userActionLogService.project(LoggedActionType.PROJECT_SETTINGS_CHANGED.with(ProjectContext.of(projectTable.getId())), "", "");
    }

    // TODO implement flag view
    public List<HangarProjectFlag> getHangarProjectFlags(String author, String slug) {
        return hangarProjectsDAO.getHangarProjectFlags(author, slug);
    }

    public void refreshHomeProjects() {
        hangarProjectsDAO.refreshHomeProjects();
    }

    public List<UserTable> getProjectWatchers(long projectId) {
        return projectsDAO.getProjectWatchers(projectId);
    }

    @Nullable
    private <T> ProjectTable getProjectTable(@Nullable T identifier, @NotNull Function<T, ProjectTable> projectTableFunction) {
        if (identifier == null) {
            return null;
        }
        return projectVisibilityService.checkVisibility(projectTableFunction.apply(identifier));
    }

    @Nullable
    private <T> ProjectTable getProjectTable(@Nullable T identifierOne, @Nullable T identifierTwo, @NotNull BiFunction<T, T, ProjectTable> projectTableFunction) {
        if (identifierOne == null || identifierTwo == null) {
            return null;
        }
        return projectVisibilityService.checkVisibility(projectTableFunction.apply(identifierOne, identifierTwo));
    }
}
