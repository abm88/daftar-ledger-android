package com.daftar.app.ui.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.components.badgeColor
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

private fun parseOpenings(values: Map<String, String>): Map<String, Double> =
    values.mapNotNull { (cur, raw) ->
        raw.trim().toDoubleOrNull()?.takeIf { it != 0.0 }?.let { cur to it }
    }.toMap()

/** "Add partner" sheet: tier, city, identity, phone, optional opening balances. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartnerSheet(
    onDismiss: () -> Unit,
    onSave: (
        name: String, shortName: String, initial: String, phone: String,
        city: City, tier: PartnerTier, openings: Map<String, Double>,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var shortName by rememberSaveable { mutableStateOf("") }
    var initial by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(City.KBL) }
    var tier by rememberSaveable { mutableStateOf(PartnerTier.REGULAR) }
    var openUsd by rememberSaveable { mutableStateOf("") }
    var openAfn by rememberSaveable { mutableStateOf("") }
    var openPkr by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Add partner", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            Text(
                "د نوي صرافي همکار اضافه کول",
                style = MaterialTheme.typography.bodyMedium,
                color = DaftarColors.Muted,
            )
            Spacer(Modifier.height(16.dp))

            MonoLabel("Relationship tier")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartnerTier.entries.forEach { t ->
                    val on = tier == t
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) DaftarColors.Copper.copy(alpha = 0.08f) else DaftarColors.PaperSoft)
                            .border(
                                1.5.dp,
                                if (on) DaftarColors.Copper else DaftarColors.LineStrong,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { tier = t }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            t.label,
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (on) DaftarColors.CopperDeep else DaftarColors.InkSoft,
                            ),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t.description,
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MonoLabel("Base city")
            Spacer(Modifier.height(8.dp))
            CityPickerRow(selected = city, onSelect = { city = it })

            Spacer(Modifier.height(14.dp))
            FieldBox("Saraf shop · سرای", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(name, { name = it }, "e.g. Sarai Shahzada — Haji Yusuf")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("Short name", modifier = Modifier.weight(1f)) {
                    FieldTextInput(shortName, { shortName = it }, "H. Yusuf")
                }
                FieldBox("Initial · اول توری", modifier = Modifier.weight(1f)) {
                    FieldTextInput(
                        initial, { if (it.length <= 1) initial = it }, "ی",
                        textStyle = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 16.sp, color = DaftarColors.Ink),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldBox("Phone", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(
                    phone, { phone = it }, "+93 70 000 0000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }

            Spacer(Modifier.height(16.dp))
            OpeningBalancesBox(
                title = "Opening balance",
                explainer = "Positive = they owe you. Negative = you owe.",
                accent = DaftarColors.CopperDeep,
                usd = openUsd, afn = openAfn, pkr = openPkr,
                onUsd = { openUsd = it }, onAfn = { openAfn = it }, onPkr = { openPkr = it },
                allowNegative = true,
            )

            Spacer(Modifier.height(18.dp))
            SubmitButton(
                label = "Add saraf · خوندي کړه",
                icon = Icons.Rounded.PersonAdd,
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name, shortName, initial, phone, city, tier,
                        parseOpenings(mapOf("USD" to openUsd, "AFN" to openAfn, "PKR" to openPkr)),
                    )
                },
            )
        }
    }
}

/** "Open new account" sheet: city, identity, notes, optional opening deposits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerSheet(
    onDismiss: () -> Unit,
    onSave: (
        name: String, shortName: String, initial: String, phone: String,
        city: City, notes: String, openings: Map<String, Double>,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var shortName by rememberSaveable { mutableStateOf("") }
    var initial by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(City.KBL) }
    var openUsd by rememberSaveable { mutableStateOf("") }
    var openAfn by rememberSaveable { mutableStateOf("") }
    var openPkr by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Open new account", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            Text("نوی حساب پرانیستل", style = MaterialTheme.typography.bodyMedium, color = DaftarColors.Muted)
            Spacer(Modifier.height(16.dp))

            MonoLabel("City")
            Spacer(Modifier.height(8.dp))
            CityPickerRow(selected = city, onSelect = { city = it })

            Spacer(Modifier.height(14.dp))
            FieldBox("Account holder name · د حساب والا نوم", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(name, { name = it }, "e.g. Haji Dawood")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("Short name", modifier = Modifier.weight(1f)) {
                    FieldTextInput(shortName, { shortName = it }, "Dawood")
                }
                FieldBox("Initial", modifier = Modifier.weight(1f)) {
                    FieldTextInput(
                        initial, { if (it.length <= 1) initial = it }, "د",
                        textStyle = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 16.sp, color = DaftarColors.Ink),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FieldBox("Phone", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(
                    phone, { phone = it }, "+93 70 000 0000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            Spacer(Modifier.height(10.dp))
            FieldBox("Business / notes", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(notes, { notes = it }, "e.g. Timber importer, monthly account")
            }

            Spacer(Modifier.height(16.dp))
            OpeningBalancesBox(
                title = "Opening deposit",
                explainer = "Starting balance when opening the account. Funds the holder is depositing with you.",
                accent = DaftarColors.Green,
                usd = openUsd, afn = openAfn, pkr = openPkr,
                onUsd = { openUsd = it }, onAfn = { openAfn = it }, onPkr = { openPkr = it },
                allowNegative = false,
            )

            Spacer(Modifier.height(18.dp))
            SubmitButton(
                label = "Open account · خوندي کړه",
                icon = Icons.Rounded.PersonAdd,
                container = DaftarColors.Green,
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name, shortName, initial, phone, city, notes,
                        parseOpenings(mapOf("USD" to openUsd, "AFN" to openAfn, "PKR" to openPkr)),
                    )
                },
            )
        }
    }
}

@Composable
fun CityPickerRow(selected: City, onSelect: (City) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        City.entries.forEach { c ->
            val on = selected == c
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.badgeColor else DaftarColors.PaperSoft)
                    .border(
                        1.5.dp,
                        if (on) c.badgeColor else DaftarColors.LineStrong,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(c) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(
                    c.code,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (on) DaftarColors.Paper else DaftarColors.InkSoft,
                    ),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    c.displayName,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = if (on) DaftarColors.Paper else DaftarColors.InkSoft,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OpeningBalancesBox(
    title: String,
    explainer: String,
    accent: androidx.compose.ui.graphics.Color,
    usd: String, afn: String, pkr: String,
    onUsd: (String) -> Unit, onAfn: (String) -> Unit, onPkr: (String) -> Unit,
    allowNegative: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(accent.copy(alpha = 0.4f), 1.dp, 12.dp)
            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel(title, color = accent, fontSize = 11, letterSpacing = 0.15)
            MonoLabel("Optional", fontSize = 10, letterSpacing = 0.05)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            explainer,
            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple("USD", usd, onUsd),
                Triple("AFN", afn, onAfn),
                Triple("PKR", pkr, onPkr),
            ).forEach { (cur, value, onChange) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(DaftarColors.Paper)
                        .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    MonoLabel(cur, fontSize = 9, letterSpacing = 0.15)
                    Spacer(Modifier.height(2.dp))
                    FieldTextInput(
                        value = value,
                        onValueChange = { raw ->
                            val filtered = raw.filter { ch ->
                                ch.isDigit() || ch == '.' || (allowNegative && ch == '-')
                            }
                            onChange(filtered)
                        },
                        placeholder = "0",
                        textStyle = TextStyle(
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = DaftarColors.Ink,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
    }
}
