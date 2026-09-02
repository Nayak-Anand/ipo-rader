package com.iporadar.app.ui.allotment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iporadar.app.core.util.Fmt
import com.iporadar.app.data.model.AllotmentResult
import com.iporadar.app.data.model.Ipo
import com.iporadar.app.data.model.IpoStatus
import com.iporadar.app.data.model.PanCheck
import com.iporadar.app.data.model.PanEntry
import com.iporadar.app.data.model.Registrar
import com.iporadar.app.data.repo.CheckableCompany
import com.iporadar.app.data.model.Relationship
import com.iporadar.app.ui.IpoViewModel
import com.iporadar.app.ui.components.NeutralChip
import com.iporadar.app.ui.components.SectionHeader
import com.iporadar.app.ui.theme.LocalMarketColors

@Composable
fun AllotmentScreen(vm: IpoViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pans by vm.pans.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PanEntry?>(null) }

    val selectedIpoId by vm.selectedAllotmentIpo.collectAsStateWithLifecycle()
    val checkSupported by vm.checkSupported.collectAsStateWithLifecycle()
    val panChecks by vm.panChecks.collectAsStateWithLifecycle()
    val registrarCompanies by vm.registrarCompanies.collectAsStateWithLifecycle()
    val selectedCompany by vm.selectedCompany.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadRegistrarCompanies() }

    // Issues where checking allotment actually makes sense.
    val checkable = state.ipos.filter {
        it.status == IpoStatus.CLOSED || it.status == IpoStatus.LISTED
    }.take(12)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Allotment Status", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Family ke saare PAN ek jagah — tap karke copy, phir registrar page pe paste",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            PanVaultCard(
                pans = pans,
                onCopy = { entry ->
                    context.copyToClipboard(entry.pan)
                    Toast
                        .makeText(context, "${entry.displayName} ka PAN copy ho gaya", Toast.LENGTH_SHORT)
                        .show()
                },
                onDelete = { pendingDelete = it },
                onAdd = { showAddDialog = true }
            )
        }

        if (checkable.isNotEmpty()) {
            item {
                CheckCard(
                    ipos = checkable,
                    selectedId = selectedIpoId,
                    onSelect = vm::selectAllotmentIpo,
                    registrarCompanies = registrarCompanies,
                    selectedCompany = selectedCompany,
                    onSelectCompany = vm::selectCompany,
                    supported = checkSupported,
                    checks = panChecks,
                    onCheckAll = vm::checkAll,
                    onRecheck = vm::checkOne,
                    onOpenRegistrar = { url -> context.openUrl(url) }
                )
            }
        }

        item { SectionHeader(title = "Sabhi registrars") }
        items(Registrar.entries.toList(), key = { it.name }) { registrar ->
            RegistrarRow(
                registrar = registrar,
                onOpen = { context.openUrl(registrar.allotmentUrl) }
            )
        }

        item {
            Text(
                text = "KFin, Maashitla, Skyline aur Purva apna allotment lookup khula rakhte " +
                    "hain, isliye unke IPOs app ke andar hi check ho jaate hain — agar koi IPO upar " +
                    "ki list me na ho to dropdown me registrar ki apni list se chun lein. Bigshare, " +
                    "MUFG aur Cameo captcha lagate hain, unke liye PAN copy karke unki " +
                    "official site pe paste karna padega. Aapke PAN sirf is phone me save hote " +
                    "hain; check karte waqt PAN sirf usi registrar ko jaata hai, aur kahin nahi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAddDialog) {
        AddPanDialog(
            onDismiss = { showAddDialog = false },
            onSave = { pan, name, relationship ->
                vm.addPan(pan, name, relationship)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("PAN hataayein?") },
            text = { Text("${entry.displayName} (${entry.masked}) vault se hat jayega.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removePan(entry)
                    pendingDelete = null
                }) { Text("Hataayein") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Rehne dein") }
            }
        )
    }
}

@Composable
private fun PanVaultCard(
    pans: List<PanEntry>,
    onCopy: (PanEntry) -> Unit,
    onDelete: (PanEntry) -> Unit,
    onAdd: () -> Unit
) {
    FlatCard {
        SectionHeader(
            title = "PAN vault",
            trailing = if (pans.isEmpty()) null else "${pans.size} saved"
        )

        if (pans.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ghar ke sabhi applications ke PAN yahan add kar lein — har baar type karne " +
                    "ki zaroorat nahi padegi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pans.forEach { entry ->
                    PanRow(
                        entry = entry,
                        onCopy = { onCopy(entry) },
                        onDelete = { onDelete(entry) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("PAN add karein")
        }
    }
}

@Composable
private fun PanRow(
    entry: PanEntry,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onCopy)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.holderName.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    NeutralChip(entry.relationship.label)
                }
            }
            Text(
                text = entry.masked,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "PAN copy karein",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "PAN hataayein",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPanDialog(
    onDismiss: () -> Unit,
    onSave: (pan: String, holderName: String, relationship: Relationship) -> Unit
) {
    var pan by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(Relationship.SELF) }

    val normalised = PanEntry.normalise(pan)
    val looksValid = PanEntry.PAN_PATTERN.matches(normalised)
    val showFormatHint = normalised.length == 10 && !looksValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Naya PAN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pan,
                    onValueChange = { pan = PanEntry.normalise(it) },
                    label = { Text("PAN number") },
                    placeholder = { Text("ABCDE1234F") },
                    singleLine = true,
                    isError = showFormatHint,
                    supportingText = {
                        if (showFormatHint) {
                            Text("Format: 5 letters, 4 digits, 1 letter")
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Text(
                    text = "Rishta",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RelationshipPicker(
                    selected = relationship,
                    onSelect = { relationship = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalised, name, relationship) },
                enabled = looksValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Wraps onto as many rows as it needs — nine relationships never fit on one line. */
@Composable
private fun RelationshipPicker(
    selected: Relationship,
    onSelect: (Relationship) -> Unit
) {
    val rows = Relationship.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

/**
 * Pick a recent IPO, then see every saved PAN's status against it.
 *
 * Only Maashitla exposes a lookup that can be automated; for every other registrar
 * this falls back to opening their site, and says so instead of leaving the user
 * wondering why nothing happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckCard(
    ipos: List<Ipo>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    registrarCompanies: List<CheckableCompany>,
    selectedCompany: CheckableCompany?,
    onSelectCompany: (CheckableCompany) -> Unit,
    supported: Boolean,
    checks: List<PanCheck>,
    onCheckAll: () -> Unit,
    onRecheck: (PanEntry) -> Unit,
    onOpenRegistrar: (String) -> Unit
) {
    val selected = ipos.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }

    FlatCard {
        SectionHeader(title = "Allotment check")
        Spacer(Modifier.height(10.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected?.name ?: selectedCompany?.label ?: "IPO chunein",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ipos.forEach { ipo ->
                    DropdownMenuItem(
                        text = { Text(ipo.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelect(ipo.id)
                            expanded = false
                        }
                    )
                }

                // Registrars handle plenty of SME issues our IPO list never sees.
                if (registrarCompanies.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        enabled = false,
                        text = {
                            Text(
                                "Registrar ki list se",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {}
                    )
                    registrarCompanies.forEach { company ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        company.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        company.registrarName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelectCompany(company)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (selected == null && selectedCompany == null) return@FlatCard

        Spacer(Modifier.height(12.dp))

        if (!supported && selected != null) {
            Text(
                text = registrarNote(selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val registrar = selected.registrar
            if (registrar != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenRegistrar(registrar.allotmentUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${registrar.displayName} pe kholein")
                }
            }
            return@FlatCard
        }

        if (checks.isEmpty()) {
            Text(
                text = "Pehle upar apne PAN add karein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@FlatCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            checks.forEach { check ->
                CheckRow(check = check, onRecheck = { onRecheck(check.pan) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onCheckAll, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sabka check karein")
        }
    }
}

@Composable
private fun CheckRow(check: PanCheck, onRecheck: () -> Unit) {
    val market = LocalMarketColors.current
    val result = check.result

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.pan.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = resultDetail(result) ?: check.pan.masked,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (result is AllotmentResult.Checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = resultLabel(result),
                style = MaterialTheme.typography.titleSmall,
                color = when (result) {
                    is AllotmentResult.Allotted -> market.positive
                    is AllotmentResult.NotAllotted -> market.negative
                    is AllotmentResult.Failed -> market.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (result.isTerminal) {
                IconButton(onClick = onRecheck, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Dobara check karein",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

private fun resultLabel(result: AllotmentResult): String = when (result) {
    AllotmentResult.Idle -> "—"
    AllotmentResult.Checking -> ""
    is AllotmentResult.Allotted -> "Allotted"
    is AllotmentResult.NotAllotted -> "Not allotted"
    AllotmentResult.NoRecord -> "No record"
    AllotmentResult.NotSupported -> "Manual"
    is AllotmentResult.Failed -> "Retry"
}

private fun resultDetail(result: AllotmentResult): String? = when (result) {
    is AllotmentResult.Allotted -> listOfNotNull(
        result.shares?.let { "$it shares" },
        result.applicationNo?.let { "App $it" }
    ).joinToString(" · ").ifEmpty { null }
    is AllotmentResult.NotAllotted -> result.applicationNo?.let { "App $it" }
    // Registrars return the same "nothing found" for both cases, so don't guess.
    AllotmentResult.NoRecord -> "Application nahi mila"
    is AllotmentResult.Failed -> result.message
    else -> null
}

private fun registrarNote(ipo: Ipo): String {
    val name = ipo.registrar?.displayName ?: ipo.registrarName
    return if (name == null) {
        "Is IPO ka registrar abhi announce nahi hua."
    } else {
        "$name automated check allow nahi karta (captcha / bot protection). " +
            "Upar apna PAN tap karke copy karein, phir unki site pe paste karein."
    }
}

@Composable
private fun RecentIpoRow(ipo: Ipo, onOpen: (String) -> Unit) {
    val registrar = ipo.registrar
    FlatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ipo.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        registrar?.displayName ?: ipo.registrarName ?: "Registrar TBA",
                        ipo.allotmentDate?.let { "Allotment ${Fmt.date(it)}" }
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (registrar != null) {
                TextButton(onClick = { onOpen(registrar.allotmentUrl) }) {
                    Text("Check")
                }
            } else {
                NeutralChip("TBA")
            }
        }
    }
}

@Composable
private fun RegistrarRow(registrar: Registrar, onOpen: () -> Unit) {
    FlatCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = registrar.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FlatCard(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PAN", text))
}
