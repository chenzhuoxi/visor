package com.macedonga.visor.device

import com.macedonga.visor.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.macedonga.visor.connection.GlassesService
import com.macedonga.visor.connection.connectionWords
import com.macedonga.visor.connection.words
import com.macedonga.visor.core.connection.DeviceInfo
import com.macedonga.visor.core.connection.DeviceState
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.connection.GlassesLink
import com.macedonga.visor.core.connection.GlassesTarget
import com.macedonga.visor.core.identity.IdentityBundle
import com.macedonga.visor.core.identity.IdentityStore
import com.macedonga.visor.ui.Line
import com.macedonga.visor.ui.PlainScreen
import com.macedonga.visor.ui.Reading
import com.macedonga.visor.ui.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DeviceViewModel @Inject constructor(
    val connection: GlassesConnection,
    private val store: IdentityStore,
) : ViewModel() {

    private val _glasses = MutableStateFlow<List<IdentityBundle>>(emptyList())

    /** The glasses set up on this phone. Read again whenever the screen comes back. */
    val glasses: StateFlow<List<IdentityBundle>> = _glasses.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _glasses.value = runCatching { store.all() }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun DeviceScreen(
    onSetUp: () -> Unit,
    onEventLog: () -> Unit,
    onMedia: () -> Unit,
    onSettings: () -> Unit,
    onMetaAi: () -> Unit,
    onLive: () -> Unit,
    model: DeviceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by model.connection.state.collectAsStateWithLifecycle()
    val running by model.connection.running.collectAsStateWithLifecycle()
    val glasses by model.glasses.collectAsStateWithLifecycle()

    var allowed by remember { mutableStateOf(hasBluetoothPermission(context)) }
    var contactsAllowed by remember { mutableStateOf(hasContactsPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = hasBluetoothPermission(context)
        contactsAllowed = hasContactsPermission(context)
    }
    LifecycleResumeEffect(Unit) {
        allowed = hasBluetoothPermission(context)
        contactsAllowed = hasContactsPermission(context)
        model.refresh()
        onPauseOrDispose { }
    }

    PlainScreen("Visor") {
        if (!allowed) {
            Line(stringResource(R.string.device_need_bluetooth_permission))
            Button(onClick = { ask.launch(wantedPermissions()) }) { Text(stringResource(R.string.device_allow)) }
        }
        if (allowed && !contactsAllowed) {
            Line(stringResource(R.string.device_contacts_hint))
            Button(onClick = { ask.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }) { Text(stringResource(R.string.device_share_contacts)) }
        }

        Section(stringResource(R.string.device_section_connection))
        Line(stringResource(connectionWords(state, hasBluetoothPermission = allowed)))
        Reading(stringResource(R.string.device_reading_glasses), state.target?.let { "${it.serial}, ${it.link.describe()}" } ?: "none")
        Reading(stringResource(R.string.device_reading_stage), state.link.words())
        if (state.reason.isNotBlank()) Reading(stringResource(R.string.device_reading_reason), state.reason)
        state.failure?.let { Reading(stringResource(R.string.device_reading_connect_failure), it.words()) }
        Reading(stringResource(R.string.device_reading_verdict), state.diagnosis?.words() ?: stringResource(R.string.device_not_reached_yet))
        if (running) {
            OutlinedButton(onClick = { GlassesService.stop(context) }) { Text(stringResource(R.string.device_disconnect)) }
        }

        Section(stringResource(R.string.device_section_glasses_on_phone))
        if (glasses.isEmpty()) Line(stringResource(R.string.device_no_glasses_on_phone), secondary = true)
        for (bundle in glasses) {
            Line("${bundle.serial}, ${bundle.glassesAddress}, ${bundle.state}")
            Button(
                enabled = allowed && !running,
                onClick = {
                    GlassesService.start(
                        context,
                        GlassesTarget(GlassesLink.Bluetooth(bundle.glassesAddress), bundle.serial),
                    )
                },
            ) { Text(stringResource(R.string.device_connect_to)) }
        }
        Button(onClick = onSetUp) { Text(stringResource(R.string.device_set_up_glasses)) }
        Button(onClick = onMedia) { Text(stringResource(R.string.device_media)) }
        Button(onClick = onLive) { Text(stringResource(R.string.device_live_view)) }
        Button(onClick = onMetaAi) { Text("Meta AI") }
        Button(onClick = onSettings) { Text(stringResource(R.string.device_settings)) }
        OutlinedButton(onClick = onEventLog) { Text(stringResource(R.string.device_event_log)) }

        Section(stringResource(R.string.device_section_status))
        if (state.device == null && state.info == null) {
            Line(stringResource(R.string.device_status_nothing_yet), secondary = true)
        }
        state.device?.let { DeviceReadings(it) }
        state.info?.let { InfoReadings(it) }
    }
}

@Composable
private fun DeviceReadings(device: DeviceState) {
    Reading(stringResource(R.string.device_reading_battery), device.battery?.let { "${it.percent}%" + if (it.charging) ", charging" else "" })
    Reading(stringResource(R.string.device_reading_battery_temperature), device.battery?.let { "%.1f °C".format(it.temperatureC) })
    Reading(
        "Case",
        device.case?.let { case ->
            when {
                !case.known -> "not seen"
                case.percent != null -> "${case.percent}%" + if (case.charging) ", charging" else ""
                else -> "level ${case.level} of ${case.scale}"
            }
        },
    )
    Reading("Worn", device.worn?.let { if (it) "yes" else "no" })
    Reading(stringResource(R.string.device_reading_hinge), device.hinge?.lowercase())
    Reading(
        stringResource(R.string.device_reading_storage),
        device.storage?.let { "%.1f GB free of %.1f GB".format(it.freeMb / 1024.0, it.totalMb / 1024.0) },
    )
    Reading("Heat", device.thermal?.health?.lowercase())
}

@Composable
private fun InfoReadings(info: DeviceInfo) {
    Reading(stringResource(R.string.device_reading_serial), info.serial)
    Reading(stringResource(R.string.device_reading_model), info.model.ifEmpty { null })
    Reading(stringResource(R.string.device_reading_build), info.build)
}

private fun GlassesLink.describe(): String = when (this) {
    is GlassesLink.Bluetooth -> address
    is GlassesLink.Wifi -> "$host:$port"
}

private fun wantedPermissions(): Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    add(Manifest.permission.READ_CONTACTS)
}.toTypedArray()

private fun hasBluetoothPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun hasContactsPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
