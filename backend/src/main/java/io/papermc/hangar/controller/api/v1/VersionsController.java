package io.papermc.hangar.controller.api.v1;

import io.papermc.hangar.controller.api.v1.interfaces.IVersionsController;
import io.papermc.hangar.controller.extras.pagination.annotations.ApplicableFilters;
import io.papermc.hangar.controller.extras.pagination.annotations.ConfigurePagination;
import io.papermc.hangar.controller.extras.pagination.filters.versions.VersionChannelFilter;
import io.papermc.hangar.controller.extras.pagination.filters.versions.VersionPlatformFilter;
import io.papermc.hangar.controller.extras.pagination.filters.versions.VersionPlatformVersionFilter;
import io.papermc.hangar.model.api.PaginatedResult;
import io.papermc.hangar.model.api.project.version.Version;
import io.papermc.hangar.model.api.project.version.VersionStats;
import io.papermc.hangar.model.api.requests.RequestPagination;
import io.papermc.hangar.model.common.NamedPermission;
import io.papermc.hangar.model.common.PermissionType;
import io.papermc.hangar.model.common.Platform;
import io.papermc.hangar.model.internal.versions.VersionUpload;
import io.papermc.hangar.security.annotations.Anyone;
import io.papermc.hangar.security.annotations.permission.PermissionRequired;
import io.papermc.hangar.security.annotations.ratelimit.RateLimit;
import io.papermc.hangar.security.annotations.unlocked.Unlocked;
import io.papermc.hangar.security.annotations.visibility.VisibilityRequired;
import io.papermc.hangar.service.api.VersionsApiService;
import io.papermc.hangar.service.internal.versions.DownloadService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Anyone
@Controller
@ResponseBody
@RateLimit(path = "apiversions", greedy = true)
public class VersionsController implements IVersionsController {

    private final DownloadService downloadService;
    private final VersionsApiService versionsApiService;

    @Autowired
    public VersionsController(final DownloadService downloadService, final VersionsApiService versionsApiService) {
        this.downloadService = downloadService;
        this.versionsApiService = versionsApiService;
    }

    @Unlocked
    @Override
    @RateLimit(overdraft = 5, refillTokens = 1, refillSeconds = 5)
    @PermissionRequired(type = PermissionType.PROJECT, perms = NamedPermission.CREATE_VERSION, args = "{#author, #slug}")
    public void uploadVersion(final String author, final String slug, final List<MultipartFile> files, final VersionUpload versionUpload) {
        this.versionsApiService.uploadVersion(author, slug, files, versionUpload);
    }

    @Override
    @VisibilityRequired(type = VisibilityRequired.Type.PROJECT, args = "{#author, #slug}")
    public Version getVersion(final String author, final String slug, final String name) {
        return this.versionsApiService.getVersion(author, slug, name);
    }

    @Override
    @VisibilityRequired(type = VisibilityRequired.Type.PROJECT, args = "{#author, #slug}")
    @ApplicableFilters({VersionChannelFilter.class, VersionPlatformFilter.class, VersionPlatformVersionFilter.class})
    public PaginatedResult<Version> getVersions(final String author, final String slug, @ConfigurePagination(defaultLimitString = "@hangarConfig.projects.initVersionLoad", maxLimit = 25) final @NotNull RequestPagination pagination) {
        return this.versionsApiService.getVersions(author, slug, pagination);
    }

    @Override
    @PermissionRequired(type = PermissionType.PROJECT, perms = NamedPermission.IS_SUBJECT_MEMBER, args = "{#author, #slug}")
    public Map<String, VersionStats> getVersionStats(final String author, final String slug, final String versionString, final Platform platform, final @NotNull OffsetDateTime fromDate, final @NotNull OffsetDateTime toDate) {
        return this.versionsApiService.getVersionStats(author, slug, versionString, platform, fromDate, toDate);
    }

    @Override
    @RateLimit(overdraft = 10, refillTokens = 2)
    @VisibilityRequired(type = VisibilityRequired.Type.VERSION, args = "{#author, #slug, #versionString, #platform}")
    public Resource downloadVersion(final String author, final String slug, final String versionString, final Platform platform) {
        return this.downloadService.getVersionFile(author, slug, versionString, platform, false, null);
    }
}
