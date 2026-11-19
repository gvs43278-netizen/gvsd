package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.BusinessConstants
import com.example.domain.EmployeeAccess
import com.example.ui.components.StatusChip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen

@Composable
fun AdminSettingsScreen(
    ownerUid: String,
    ownerEmail: String,
    employees: List<EmployeeAccess>,
    isBusy: Boolean,
    onReverifyUid: () -> Unit,
    onAddEmployee: (String, String, () -> Unit) -> Unit,
    onToggleEmployeeActive: (EmployeeAccess) -> Unit,
    onRemoveEmployee: (String) -> Unit,
    onBuildBackupJson: () -> String,
    onRestoreBackup: (String) -> Unit,
    onNotify: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showAddEmpDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = onBuildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                onNotify("পূর্ণাঙ্গ JSON ব্যাকআপ সফলভাবে সংরক্ষণ করা হয়েছে।")
            } catch (e: Exception) {
                onNotify("ব্যাকআপ সেভ করতে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    onNotify("ফাইলটি খালি বা পড়া যায়নি।")
                } else {
                    pendingRestoreJson = content
                }
            } catch (e: Exception) {
                onNotify("ফাইল পড়তে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Owner UID & Security Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Owner ও নিরাপত্তা তথ্য", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Owner Gmail: $ownerEmail", style = MaterialTheme.typography.bodyMedium)
                    Text("Verified Auth UID: $ownerUid", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily)
                    Text(
                        "Legacy Super Admin UID: ${BusinessConstants.LEGACY_SUPER_ADMIN_UID}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusChip("Firebase Spark Free Plan (কোনো Paid API নেই)", ProfitGreen)
                    OutlinedButton(
                        onClick = onReverifyUid,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Owner UID পুনরায় যাচাই/সিঙ্ক করুন")
                    }
                }
            }
        }

        // 2. Backup & Restore Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ব্যাকআপ ও রিস্টোর (JSON)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "রিস্টোর করার আগে স্বয়ংক্রিয়ভাবে একটি Safety Backup তৈরি হয় এবং ভুল বা অসম্পূর্ণ ফাইল প্রত্যাখ্যান করা হয়।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportBackupLauncher.launch("business_manager_pro_backup.json") },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("JSON ব্যাকআপ")
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("import_backup_button")
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("রিস্টোর")
                        }
                    }
                }
            }
        }

        // 3. Employee Management Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("কর্মচারী ম্যানেজমেন্ট (${employees.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { showAddEmpDialog = true },
                    modifier = Modifier.testTag("add_employee_button")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("কর্মচারী যোগ")
                }
            }
        }

        if (employees.isEmpty()) {
            item {
                Text(
                    "এখনও কোনো কর্মচারী যুক্ত করা হয়নি। কর্মচারীর Gmail দিয়ে যুক্ত করলে তিনি লেনদেন জমা দিতে পারবেন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(employees, key = { it.email }) { emp ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("employee_card_${emp.email}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(emp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(emp.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (emp.active) "সক্রিয় (Pending জমা দিতে পারবে)" else "নিষ্ক্রিয়",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (emp.active) ProfitGreen else LossRed
                            )
                        }
                        Switch(
                            checked = emp.active,
                            onCheckedChange = { onToggleEmployeeActive(emp) }
                        )
                        IconButton(onClick = { onRemoveEmployee(emp.email) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "কর্মচারী বাদ দিন", tint = LossRed)
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                modifier = Modifier.fillMaxWidth().testTag("admin_sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("সাইন আউট")
            }
        }
    }

    if (showAddEmpDialog) {
        var empName by remember { mutableStateOf("") }
        var empEmail by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddEmpDialog = false },
            title = { Text("নতুন কর্মচারী যুক্ত করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = empName,
                        onValueChange = { empName = it },
                        label = { Text("কর্মচারীর নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("emp_name_input")
                    )
                    OutlinedTextField(
                        value = empEmail,
                        onValueChange = { empEmail = it },
                        label = { Text("কর্মচারীর Google Gmail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("emp_email_input")
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        if (empName.isBlank() || !empEmail.contains("@")) {
                            err = "সঠিক নাম ও Gmail লিখুন।"
                            return@Button
                        }
                        onAddEmployee(empName, empEmail) {
                            showAddEmpDialog = false
                        }
                    },
                    modifier = Modifier.testTag("emp_save_button")
                ) { Text("যুক্ত করুন") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddEmpDialog = false }) { Text("বাতিল") } }
        )
    }

    pendingRestoreJson?.let { rawJson ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("ব্যাকআপ রিস্টোর নিশ্চিতকরণ") },
            text = {
                Text("রিস্টোর করার আগে স্বয়ংক্রিয়ভাবে বর্তমান ডেটার একটি Safety Backup তৈরি করা হবে। তারপর নির্বাচিত ব্যাকআপ যাচাই করে রিস্টোর করা হবে।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreJson = null
                        onRestoreBackup(rawJson)
                    }
                ) { Text("Safety Backup ও রিস্টোর করুন") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreJson = null }) { Text("বাতিল") }
            }
        )
    }
}
