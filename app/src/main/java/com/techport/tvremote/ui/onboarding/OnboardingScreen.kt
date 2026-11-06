package com.techport.tvremote.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.techport.tvremote.R
import com.techport.tvremote.core.permissions.LocalNetworkPermission
import com.techport.tvremote.core.permissions.findActivity
import com.techport.tvremote.core.permissions.openAppSettings

/**
 * First-launch screen: what the app does, that it stays on the local network, and (Android 17+)
 * why it needs local network access. The caller marks onboarding done on either callback.
 */
@Composable
fun OnboardingScreen(onFindTv: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val needsPermission = LocalNetworkPermission.isRuntimePermission
    var granted by rememberSaveable { mutableStateOf(LocalNetworkPermission.isGranted(context)) }
    // Set after a denial; the system stops showing the dialog after two, so we then offer Settings.
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    // The user may grant it from system Settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = LocalNetworkPermission.isGranted(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            onFindTv()
        } else {
            denied = true
            permanentlyDenied = context.findActivity()
                ?.shouldShowRequestPermissionRationale(LocalNetworkPermission.PERMISSION) == false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val viewport = maxHeight
        // Content on top, actions at the bottom (thumb reach); scrolls on small or landscape screens.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(28.dp).size(56.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.platform_onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.platform_onboarding_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                InfoRow(Icons.Rounded.Lock, stringResource(R.string.platform_onboarding_privacy))
                if (needsPermission && !granted) {
                    Spacer(Modifier.height(16.dp))
                    PermissionCard(denied = denied, permanentlyDenied = permanentlyDenied)
                }
            }

            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    !needsPermission || granted -> Button(onClick = onFindTv, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.action_find_tv))
                    }
                    permanentlyDenied -> {
                        Button(
                            onClick = { context.openAppSettings() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(stringResource(R.string.platform_onboarding_open_settings)) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onFindTv, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text(stringResource(R.string.platform_onboarding_continue_anyway))
                        }
                    }
                    else -> {
                        Button(
                            onClick = { launcher.launch(LocalNetworkPermission.PERMISSION) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(stringResource(R.string.platform_onboarding_allow_and_find)) }
                        if (denied) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onFindTv, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text(stringResource(R.string.platform_onboarding_continue_anyway))
                            }
                        }
                    }
                }
                TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.platform_onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionCard(denied: Boolean, permanentlyDenied: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.platform_onboarding_permission_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(
                    when {
                        permanentlyDenied -> R.string.platform_onboarding_permission_blocked
                        denied -> R.string.platform_onboarding_permission_denied
                        else -> R.string.platform_onboarding_permission_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
