package com.lhacenmed.sona.feature.settings.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsAvatar
import com.lhacenmed.sona.feature.settings.component.SettingsHeader
import com.lhacenmed.sona.feature.settings.component.SettingsLeadingIcon
import com.lhacenmed.sona.feature.settings.component.SettingsLinkItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.SettingsLoadStatus
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.update.github.GitHub
import com.lhacenmed.sona.feature.update.installedBuild

/** A page of the project's on the web, as About links to it. */
private class ProjectLink(val labelRes: Int, val url: String, val icon: @Composable () -> Painter)

private val ProjectLinks = listOf(
    ProjectLink(R.string.about_link_github, GitHub.REPOSITORY_URL) { painterResource(R.drawable.ic_github) },
    ProjectLink(R.string.about_link_releases, GitHub.RELEASES_URL) { rememberVectorPainter(Icons.Filled.NewReleases) },
    ProjectLink(R.string.about_link_issues, GitHub.ISSUES_URL) { rememberVectorPainter(Icons.Filled.BugReport) },
)

/** Someone Sona is by, or built on, as About lists them. */
private class Person(val name: String, val avatarUrl: String, val roleRes: Int, val profileUrl: String)

private val LeadDeveloper = Person(GitHub.OWNER, GitHub.AVATAR_URL, R.string.about_role_lead_developer, GitHub.PROFILE_URL)

/** The apps Sona's code is ported from, credited as ArchiveTune credits those it respects. */
private val BuiltOn = listOf(
    Person("Auxio", "https://github.com/OxygenCobalt.png", R.string.about_role_auxio, "https://github.com/OxygenCobalt/Auxio"),
    Person("ArchiveTune", "https://github.com/rukamori.png", R.string.about_role_archivetune, "https://github.com/rukamori/ArchiveTune"),
    Person("Fossify Music Player", "https://github.com/FossifyOrg.png", R.string.about_role_fossify, "https://github.com/FossifyOrg/Music-Player"),
)

/**
 * What Sona is and who it is by: the installed build, the project's pages and its libraries' licenses, its
 * developer, the apps it is built on, and its contributors - kept from last time, and asked for again
 * whenever the device is online.
 */
data object AboutScreen : Screen {
    override val titleRes: Int get() = R.string.about_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val uriHandler = LocalUriHandler.current
        val viewModel: AboutViewModel = hiltViewModel()
        val contributors by viewModel.contributors.load.collectAsStateWithLifecycle()
        val installedBuild = LocalContext.current.installedBuild()

        SettingsList {
            // Which version, which kind of build, and which of the release's APKs is installed.
            SettingsHeader(
                mark = painterResource(R.drawable.ic_sona_mark),
                title = stringResource(R.string.about_app_name),
                details = listOfNotNull(
                    installedBuild.versionName,
                    stringResource(if (installedBuild.isDebug) R.string.about_build_debug else R.string.about_build_release),
                    installedBuild.variant?.label,
                ).joinToString(" · "),
            )

            SettingsSection(stringResource(R.string.about_project)) {
                ProjectLinks.forEach { link ->
                    SettingsLinkItem(
                        title = stringResource(link.labelRes),
                        summary = link.url.withoutScheme(),
                        url = link.url,
                        leadingContent = { SettingsLeadingIcon(link.icon()) },
                    )
                }
                SettingsNavigationItem(
                    title = stringResource(R.string.about_licenses),
                    summary = stringResource(R.string.about_licenses_summary),
                    icon = Icons.Filled.Description,
                    onClick = { navigator.go(LicensesScreen) },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.about_lead_developer)) {
                PersonItem(LeadDeveloper)
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.about_built_on)) {
                BuiltOn.forEach { PersonItem(it) }
            }

            SettingsSectionDivider()

            // The longest section, so it folds away; every contributor, beyond those listed, is on its heading.
            SettingsSection(
                title = stringResource(R.string.about_contributors),
                actions = listOf(
                    TopBarAction(label = stringResource(R.string.about_all_contributors), icon = Icons.AutoMirrored.Filled.OpenInNew) {
                        runCatching { uriHandler.openUri(GitHub.CONTRIBUTORS_URL) }
                    },
                ),
                isCollapsible = true,
            ) {
                SettingsLoadStatus(contributors, onRetry = viewModel.contributors::retry)
                (contributors as? SettingsLoad.Loaded)?.items?.forEach { contributor ->
                    SettingsLinkItem(
                        title = contributor.login,
                        summary = contributor.profileUrl.withoutScheme(),
                        url = contributor.profileUrl,
                        leadingContent = { SettingsAvatar(contributor.avatarUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonItem(person: Person) {
    SettingsLinkItem(
        title = person.name,
        summary = stringResource(person.roleRes),
        url = person.profileUrl,
        leadingContent = { SettingsAvatar(person.avatarUrl) },
    )
}

/** "github.com/LhacenMed/Sona": a link as a row shows it. */
private fun String.withoutScheme(): String = removePrefix("https://")
