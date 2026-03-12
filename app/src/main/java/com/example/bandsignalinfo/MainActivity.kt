package com.example.bandsignalinfo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bandsignalinfo.ui.theme.BandSignalInfoTheme
import com.example.bandsignalinfo.ui.theme.carrierColorScheme
import kotlinx.coroutines.delay

data class CellData(
    val type: String,
    val isServing: Boolean,
    val provider: String = "",
    val band: String = "N/A",
    val rsrp: String = "N/A",
    val rsrq: String = "N/A",
    val pci: String = "N/A",
    val tac: String = "N/A",
    val arfcn: String = "N/A",
    val arfcnLabel: String = "EARFCN",
)

val REFRESH_OPTIONS = listOf(1L to "1s", 2L to "2s", 5L to "5s", 10L to "10s", 30L to "30s")
const val PRIVACY_POLICY_URL = "https://privacypolicy.homeyers.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BandSignalInfoTheme {
                CellInfoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellInfoScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cells by remember { mutableStateOf<List<CellData>>(emptyList()) }
    var providerName by remember { mutableStateOf("") }
    var refreshSeconds by remember { mutableLongStateOf(1L) }
    var showSettings by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(!hasLocationPermission) }

    val darkTheme = isSystemInDarkTheme()
    val carrierScheme = remember(providerName, darkTheme) { carrierColorScheme(providerName, darkTheme) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Google Play Prominent Disclosure Requirement
    if (showDisclosure && !hasLocationPermission) {
        AlertDialog(
            onDismissRequest = { /* Require choice */ },
            title = { Text("Location Access Disclosure", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "Band Signal Info requires location access to identify the cell towers you are connected to. " +
                        "This data is used to display signal strength and frequency band info, including via a background service while the app is minimized. " +
                        "We do not collect or share your personal location data.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("View Privacy Policy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDisclosure = false
                    permissionLauncher.launch(requiredPermissions)
                }) {
                    Text("Accept & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text("Decline")
                }
            }
        )
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val intent = Intent(context, CellMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(hasLocationPermission, refreshSeconds) {
        if (hasLocationPermission) {
            while (true) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val provider = tm.networkOperatorName.takeIf { it.isNotBlank() } ?: "Unknown"
                providerName = provider
                val raw = try { tm.allCellInfo } catch (_: Exception) { null }
                cells = raw?.mapNotNull { parseCellInfo(it, provider) } ?: emptyList()
                delay(refreshSeconds * 1000)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings & About", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    Text("Refresh Interval", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    REFRESH_OPTIONS.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = refreshSeconds == seconds,
                                onClick = { refreshSeconds = seconds }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Privacy Policy", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Done") }
            }
        )
    }

    MaterialTheme(colorScheme = carrierScheme) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Band Signal Info", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (!hasLocationPermission) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Permissions Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Fine Location permission is needed to read cell tower information.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { showDisclosure = true }) {
                        Text("Grant Permissions")
                    }
                }
            }
        } else {
            val nrCell = cells.firstOrNull { it.type == "NR (5G)" }
            val lteCell = cells.firstOrNull { it.isServing && it.type == "LTE" }
            val primaryCells = listOfNotNull(nrCell, lteCell)
            val neighborCells = cells.filter { it !in primaryCells }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (cells.isEmpty()) {
                    item {
                        Text(
                            "No cell info available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (primaryCells.isNotEmpty()) {
                    item {
                        Text(
                            "SERVING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                        )
                    }
                    items(primaryCells) { cell -> ServingCellCard(cell) }
                }

                if (neighborCells.isNotEmpty()) {
                    item {
                        Text(
                            "NEIGHBORS  ${neighborCells.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 1.dp)
                        )
                    }
                    items(neighborCells) { cell -> NeighborCellCard(cell) }
                }
            }
        }
    }
    } // end MaterialTheme(carrierScheme)
}

fun parseCellInfo(cellInfo: CellInfo, provider: String): CellData? {
    val unavail = Int.MAX_VALUE
    return when (cellInfo) {
        is CellInfoLte -> {
            val id = cellInfo.cellIdentity
            val sig = cellInfo.cellSignalStrength
            val earfcn = id.earfcn
            val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                id.bands.firstOrNull()?.let { "B$it" } ?: earfcnToLteBand(earfcn)
            } else earfcnToLteBand(earfcn)
            
            val rsrpVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.rsrp else sig.dbm
            val rsrqVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.rsrq else unavail

            CellData(
                type = "LTE",
                isServing = cellInfo.isRegistered,
                provider = provider,
                band = band,
                rsrp = rsrpVal.takeIf { it != unavail }?.let { "$it dBm" } ?: "N/A",
                rsrq = rsrqVal.takeIf { it != unavail }?.let { "$it dB" } ?: "N/A",
                pci = id.pci.takeIf { it != unavail }?.toString() ?: "N/A",
                tac = id.tac.takeIf { it != unavail }?.toString() ?: "N/A",
                arfcn = earfcn.takeIf { it != unavail }?.toString() ?: "N/A",
                arfcnLabel = "EARFCN"
            )
        }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                val id = cellInfo.cellIdentity as CellIdentityNr
                val sig = cellInfo.cellSignalStrength as CellSignalStrengthNr
                val nrarfcn = id.nrarfcn
                val arfcnBand = nrarfcnToNrBand(nrarfcn, provider)
                val band = if (arfcnBand != "N/A" && arfcnBand != "n?") {
                    arfcnBand
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    id.bands.firstOrNull()?.let { "n$it" } ?: "N/A"
                } else "N/A"
                val tac = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    id.tac.takeIf { it != unavail }?.toString() ?: "N/A"
                } else "N/A"
                CellData(
                    type = "NR (5G)",
                    isServing = cellInfo.isRegistered,
                    provider = provider,
                    band = band,
                    rsrp = sig.ssRsrp.takeIf { it != unavail }?.let { "$it dBm" } ?: "N/A",
                    rsrq = sig.ssRsrq.takeIf { it != unavail }?.let { "$it dB" } ?: "N/A",
                    pci = id.pci.takeIf { it != unavail }?.toString() ?: "N/A",
                    tac = tac,
                    arfcn = nrarfcn.takeIf { it != unavail }?.toString() ?: "N/A",
                    arfcnLabel = "NR-ARFCN"
                )
            } else if (cellInfo is CellInfoWcdma) {
                val id = cellInfo.cellIdentity
                val sig = cellInfo.cellSignalStrength
                CellData(
                    type = "WCDMA", isServing = cellInfo.isRegistered, provider = provider,
                    band = "N/A",
                    rsrp = sig.dbm.takeIf { it != unavail }?.let { "$it dBm" } ?: "N/A",
                    rsrq = "N/A",
                    pci = id.psc.takeIf { it != unavail }?.toString() ?: "N/A",
                    tac = id.lac.takeIf { it != unavail }?.toString() ?: "N/A",
                    arfcn = id.uarfcn.takeIf { it != unavail }?.toString() ?: "N/A",
                    arfcnLabel = "UARFCN"
                )
            } else if (cellInfo is CellInfoGsm) {
                val id = cellInfo.cellIdentity
                val sig = cellInfo.cellSignalStrength
                CellData(
                    type = "GSM", isServing = cellInfo.isRegistered, provider = provider,
                    band = "N/A",
                    rsrp = sig.dbm.takeIf { it != unavail }?.let { "$it dBm" } ?: "N/A",
                    rsrq = "N/A",
                    pci = id.bsic.takeIf { it != unavail }?.toString() ?: "N/A",
                    tac = id.lac.takeIf { it != unavail }?.toString() ?: "N/A",
                    arfcn = id.arfcn.takeIf { it != unavail }?.toString() ?: "N/A",
                    arfcnLabel = "ARFCN"
                )
            } else null
        }
    }
}

private val CARRIER_BAND_PREFS: List<Pair<String, List<Int>>> = listOf(
    "t-mobile"  to listOf(25, 41, 66, 71),
    "tmobile"   to listOf(25, 41, 66, 71),
    "metro"     to listOf(25, 41, 66, 71),
    "sprint"    to listOf(25, 41, 66),
    "at&t"      to listOf(2, 5, 14, 66, 77),
    "att"       to listOf(2, 5, 14, 66, 77),
    "firstnet"  to listOf(14, 2, 5, 66, 77),
    "verizon"   to listOf(5, 13, 66, 77, 261),
    "us cellular" to listOf(12, 66, 2),
    "dish"      to listOf(70, 66),
)

fun nrarfcnToNrBand(nrarfcn: Int, operator: String): String {
    if (nrarfcn <= 0 || nrarfcn == Int.MAX_VALUE) return "N/A"
    val table = listOf(
        71  to 123400..130400,
        29  to 143400..145600,
        12  to 145800..149200,
        13  to 149200..151200,
        14  to 151600..153600,
        28  to 151600..160600,
        18  to 172000..175000,
        26  to 171800..178800,
        5   to 173800..178800,
        8   to 185000..192000,
        51  to 285400..286400,
        76  to 285400..286400,
        50  to 286400..303400,
        75  to 286400..303400,
        74  to 295000..303600,
        3   to 361000..376000,
        39  to 376000..384000,
        2   to 386000..398000,
        25  to 386000..399000,
        70  to 399000..404000,
        34  to 402000..405000,
        1   to 422000..434000,
        65  to 422000..440000,
        66  to 422000..440000,
        30  to 470000..472000,
        40  to 460000..480000,
        53  to 496700..499000,
        38  to 514000..524000,
        41  to 499200..537999,
        90  to 499200..537999,
        7   to 524000..538000,
        48  to 636667..646666,
        77  to 620000..680000,
        78  to 620000..653333,
        79  to 693334..733333,
        257 to 2054166..2104165,
        258 to 2016667..2070832,
        260 to 2229166..2279165,
        261 to 2070833..2084999,
    )
    val candidates = table.filter { nrarfcn in it.second }.map { it.first }
    if (candidates.isEmpty()) return "n?"
    if (candidates.size == 1) return "n${candidates.first()}"
    val op = operator.lowercase()
    val prefs = CARRIER_BAND_PREFS.firstOrNull { (name, _) -> op.contains(name) }?.second
    if (prefs != null) {
        val preferred = prefs.firstOrNull { it in candidates }
        if (preferred != null) return "n$preferred"
    }
    return "n${candidates.first()}"
}

fun earfcnToLteBand(earfcn: Int): String {
    if (earfcn == Int.MAX_VALUE || earfcn < 0) return "N/A"
    val table = listOf(
        1 to 0..599, 2 to 600..1199, 3 to 1200..1949, 4 to 1950..2399,
        5 to 2400..2649, 6 to 2650..2749, 7 to 2750..3449, 8 to 3450..3799,
        9 to 3800..4149, 10 to 4150..4749, 11 to 4750..4999, 12 to 5000..5179,
        13 to 5180..5279, 14 to 5280..5379, 17 to 5730..5849, 18 to 5850..5999,
        19 to 6000..6149, 20 to 6150..6449, 21 to 6450..6599, 22 to 6600..7399,
        23 to 7500..7699, 24 to 7700..8039, 25 to 8040..8689, 26 to 8690..9039,
        27 to 9040..9209, 28 to 9210..9659, 29 to 9660..9769, 30 to 9770..9869,
        31 to 9870..9919, 32 to 9920..10359, 33 to 36000..36199, 34 to 36200..36349,
        35 to 36350..36949, 36 to 36950..37549, 37 to 37550..37749, 38 to 37750..38249,
        39 to 38250..38649, 40 to 38650..39649, 41 to 39650..41589, 42 to 41590..43589,
        43 to 43590..45589, 44 to 45590..46589, 66 to 66436..67335, 71 to 68586..68935
    )
    return table.firstOrNull { earfcn in it.second }?.let { "B${it.first}" } ?: "B?"
}

@Composable
fun ServingCellCard(cell: CellData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cell.type, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(cell.band, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            CompactGrid(
                listOf(
                    "Provider" to cell.provider,
                    "RSRP" to cell.rsrp,
                    "RSRQ" to cell.rsrq,
                    "PCI" to cell.pci,
                    "TAC" to cell.tac,
                    cell.arfcnLabel to cell.arfcn
                )
            )
        }
    }
}

@Composable
fun NeighborCellCard(cell: CellData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.width(56.dp)) {
                Text(cell.type, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (cell.band != "N/A" && cell.band != "B?") {
                    Text(cell.band, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
            MiniStat("PCI", cell.pci)
            MiniStat("RSRP", cell.rsrp)
            if (cell.rsrq != "N/A") MiniStat("RSRQ", cell.rsrq)
            MiniStat(cell.arfcnLabel, cell.arfcn)
        }
    }
}

@Composable
fun CompactGrid(entries: List<Pair<String, String>>) {
    val chunked = entries.chunked(2)
    chunked.forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth()) {
            pair.forEach { (label, value) ->
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

@Composable
fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
