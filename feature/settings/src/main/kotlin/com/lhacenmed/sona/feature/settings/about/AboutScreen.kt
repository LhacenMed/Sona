@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.about

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.update.github.Contributor
import com.lhacenmed.sona.feature.update.github.GitHub
import com.lhacenmed.sona.feature.update.installedVersionName

/** How wide About's content grows before it stops and centres - ArchiveTune's `AboutDimensions`. */
private val ContentMaxWidth = 840.dp
private val DialogContentMaxWidth = 920.dp
private val HorizontalHeroBreakpoint = 600.dp

/** ArchiveTune's `AboutSpacing`. */
private val SpacingXxs = 4.dp
private val SpacingXs = 8.dp
private val SpacingSm = 16.dp
private val SpacingMd = 24.dp

/** A link About offers: where it goes, and how it is drawn. */
private class AboutLink(val labelRes: Int, val url: String, val icon: @Composable () -> Painter)

private val GitHubIcon: @Composable () -> Painter = { painterResource(R.drawable.ic_github) }

/** Someone Sona is by, or built on, as About lists them. */
private class Person(
    val name: String,
    val avatarUrl: String,
    val roleRes: Int,
    val profileUrl: String,
)

private val ProjectLinks = listOf(
    AboutLink(R.string.about_link_github, GitHub.REPOSITORY_URL, GitHubIcon),
    AboutLink(R.string.about_link_releases, GitHub.RELEASES_URL) { rememberVectorPainter(Icons.Filled.NewReleases) },
    AboutLink(R.string.about_link_issues, GitHub.ISSUES_URL) { rememberVectorPainter(Icons.Filled.BugReport) },
)

private val LeadDeveloper = Person(GitHub.OWNER, GitHub.AVATAR_URL, R.string.about_role_lead_developer, GitHub.PROFILE_URL)

/** The apps Sona's code is ported from, credited as ArchiveTune credits those it respects. */
private val BuiltOn = listOf(
    Person("Auxio", "https://github.com/OxygenCobalt.png", R.string.about_role_auxio, "https://github.com/OxygenCobalt/Auxio"),
    Person("ArchiveTune", "https://github.com/rukamori.png", R.string.about_role_archivetune, "https://github.com/rukamori/ArchiveTune"),
    Person("Fossify Music Player", "https://github.com/FossifyOrg.png", R.string.about_role_fossify, "https://github.com/FossifyOrg/Music-Player"),
)

/**
 * What Sona is and who it is by - ArchiveTune's `AboutScreen`: its identity and links, its libraries'
 * licenses, its developer, the apps it is built on, and its contributors.
 */
data object AboutScreen : Screen {
    override val titleRes: Int get() = R.string.about_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val viewModel: AboutViewModel = hiltViewModel()
        val contributors by viewModel.contributors.collectAsStateWithLifecycle()
        val licenses by viewModel.licenses.collectAsStateWithLifecycle()
        val openUri = { uri: String -> runCatching { uriHandler.openUri(uri) } }
        val isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().screenList(listState),
            contentPadding = PaddingValues(top = SpacingXs, bottom = LocalBottomContentPadding.current),
            verticalArrangement = Arrangement.spacedBy(SpacingSm),
        ) {
            item(key = "identity") {
                ContentContainer {
                    IdentityCard(
                        versionName = context.installedVersionName(),
                        buildLabel = stringResource(if (isDebugBuild) R.string.about_build_debug else R.string.about_build_release),
                        onOpenUri = { openUri(it) },
                    )
                }
            }
            item(key = "project") {
                ContentContainer {
                    ActionListItem(
                        title = stringResource(R.string.about_licenses),
                        icon = rememberVectorPainter(Icons.Filled.Info),
                        index = 0,
                        count = 1,
                        onClick = viewModel::openLicenses,
                    )
                }
            }
            item(key = "lead") {
                ContentContainer {
                    PeopleSection(
                        title = stringResource(R.string.about_lead_developer),
                        people = listOf(LeadDeveloper),
                        onOpenUri = { openUri(it) },
                        prominentFirst = true,
                    )
                }
            }
            item(key = "built_on") {
                ContentContainer {
                    PeopleSection(title = stringResource(R.string.about_built_on), people = BuiltOn, onOpenUri = { openUri(it) })
                }
            }
            item(key = "contributors") {
                ContentContainer {
                    ContributorsSection(state = contributors, onOpenUri = { openUri(it) }, onRetry = viewModel::loadContributors)
                }
            }
        }

        licenses?.let { state -> LicensesDialog(state = state, onRetry = viewModel::openLicenses, onDismiss = viewModel::closeLicenses) }
    }
}

@Composable
private fun ContentContainer(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SpacingSm), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) { content() }
    }
}

@Composable
private fun IdentityCard(versionName: String, buildLabel: String, onOpenUri: (String) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= HorizontalHeroBreakpoint) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SpacingMd),
                    horizontalArrangement = Arrangement.spacedBy(SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Identity(versionName, buildLabel, Alignment.Start, Modifier.weight(1f))
                    LinkChips(Arrangement.spacedBy(SpacingXs, Alignment.End), onOpenUri, Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(SpacingMd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SpacingMd),
                ) {
                    Identity(versionName, buildLabel, Alignment.CenterHorizontally)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LinkChips(Arrangement.spacedBy(SpacingXs, Alignment.CenterHorizontally), onOpenUri)
                }
            }
        }
    }
}

@Composable
private fun Identity(
    versionName: String,
    buildLabel: String,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(SpacingSm)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_sona_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier.padding(SpacingSm).size(64.dp),
            )
        }
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingXs, horizontalAlignment),
            verticalArrangement = Arrangement.spacedBy(SpacingXs),
        ) {
            MetadataBadge(versionName)
            MetadataBadge(buildLabel)
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Badge(
        modifier = Modifier.heightIn(min = 32.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun LinkChips(
    horizontalArrangement: Arrangement.Horizontal,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(SpacingXs),
    ) {
        ProjectLinks.forEach { link ->
            AssistChip(
                onClick = { onOpenUri(link.url) },
                leadingIcon = { Icon(link.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(stringResource(link.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = Modifier.padding(horizontal = SpacingXs, vertical = SpacingXxs),
    )
}

@Composable
private fun ActionListItem(title: String, icon: Painter, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = aboutListItemColors(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        leadingContent = { LeadingIcon(icon) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun PeopleSection(
    title: String,
    people: List<Person>,
    onOpenUri: (String) -> Unit,
    prominentFirst: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingXs)) {
        SectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            people.forEachIndexed { index, person ->
                val isProminent = prominentFirst && index == 0
                PersonListItem(
                    person = person,
                    index = index,
                    count = people.size,
                    avatarSize = if (isProminent) 64.dp else 52.dp,
                    minHeight = if (isProminent) 96.dp else 80.dp,
                    onOpenUri = onOpenUri,
                )
            }
        }
    }
}

@Composable
private fun PersonListItem(
    person: Person,
    index: Int,
    count: Int,
    avatarSize: Dp,
    minHeight: Dp,
    onOpenUri: (String) -> Unit,
) {
    SegmentedListItem(
        onClick = { onOpenUri(person.profileUrl) },
        shapes = if (count == 1) {
            ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge)
        } else {
            ListItemDefaults.segmentedShapes(index = index, count = count)
        },
        colors = aboutListItemColors(),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        leadingContent = {
            AsyncImage(
                model = person.avatarUrl,
                contentDescription = person.name,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        supportingContent = {
            Text(
                text = stringResource(person.roleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            FilledTonalIconButton(onClick = { onOpenUri(person.profileUrl) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.about_link_github),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        Text(text = person.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ContributorsSection(
    state: AboutLoad<Contributor>,
    onOpenUri: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingXs)) {
        SectionHeader(stringResource(R.string.about_contributors))
        when (state) {
            AboutLoad.Loading -> StatusCard(stringResource(R.string.about_loading), onRetry = null)
            AboutLoad.Empty -> StatusCard(stringResource(R.string.about_no_results), onRetry = onRetry)
            AboutLoad.Failed -> StatusCard(stringResource(R.string.about_error), onRetry = onRetry)
            is AboutLoad.Loaded -> {
                val count = state.items.size + 1
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    state.items.forEachIndexed { index, contributor ->
                        SegmentedListItem(
                            onClick = { onOpenUri(contributor.profileUrl) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                            colors = aboutListItemColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            leadingContent = {
                                AsyncImage(
                                    model = contributor.avatarUrl,
                                    contentDescription = contributor.login,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            },
                        ) {
                            Text(
                                text = contributor.login,
                                style = MaterialTheme.typography.bodyLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    SegmentedListItem(
                        onClick = { onOpenUri(GitHub.CONTRIBUTORS_URL) },
                        shapes = ListItemDefaults.segmentedShapes(index = count - 1, count = count),
                        colors = aboutListItemColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        leadingContent = { LeadingIcon(rememberVectorPainter(Icons.Filled.AddCircle)) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.about_more), style = MaterialTheme.typography.bodyLargeEmphasized)
                    }
                }
            }
        }
    }
}

/** Loading - with no [onRetry] - or a message to retry from. */
@Composable
private fun StatusCard(message: String, onRetry: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        StatusContent(message, onRetry, Modifier.fillMaxWidth().padding(SpacingMd))
    }
}

@Composable
private fun StatusContent(message: String, onRetry: (() -> Unit)?, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingSm, Alignment.CenterVertically),
    ) {
        if (onRetry == null) LoadingIndicator(modifier = Modifier.size(32.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            TextButton(onClick = onRetry, shapes = buttonPressShapes()) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** The libraries' licenses, over the whole window - ArchiveTune's `AboutFullScreenDialog`. */
@Composable
private fun LicensesDialog(
    state: AboutLoad<DependencyLicense>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                TopAppBar(
                    title = { Text(stringResource(R.string.about_licenses), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.about_close))
                        }
                    },
                )
                val contentModifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                when (state) {
                    AboutLoad.Loading -> StatusContent(stringResource(R.string.about_loading), onRetry = null, modifier = contentModifier)
                    AboutLoad.Empty -> StatusContent(stringResource(R.string.about_no_results), onRetry, contentModifier)
                    AboutLoad.Failed -> StatusContent(stringResource(R.string.about_error), onRetry, contentModifier)
                    is AboutLoad.Loaded -> LicenseList(state.items, contentModifier)
                }
            }
        }
    }
}

@Composable
private fun LicenseList(licenses: List<DependencyLicense>, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().widthIn(max = DialogContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(licenses, key = { index, license -> "${license.name}:${license.version.orEmpty()}:$index" }) { index, license ->
                SegmentedListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = licenses.size),
                    colors = aboutListItemColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    leadingContent = { LeadingIcon(rememberVectorPainter(Icons.Filled.Info)) },
                    overlineContent = license.version?.let { version ->
                        {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = license.licenses ?: stringResource(R.string.about_license_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) {
                    Text(text = license.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LeadingIcon(icon: Painter) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(SpacingSm).size(20.dp))
    }
}

@Composable
private fun aboutListItemColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
