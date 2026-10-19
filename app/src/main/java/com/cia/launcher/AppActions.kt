package com.cia.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Aksi bersama untuk menu long-press aplikasi (drawer & start menu).
 */

/** Buka halaman "Info aplikasi" di Setelan sistem, seperti launcher lain. */
fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    context.startActivity(intent)
}

/** Aplikasi sistem & launcher sendiri tidak bisa dihapus. */
fun isUninstallable(context: Context, packageName: String): Boolean {
    if (packageName == context.packageName) return false
    return try {
        val ai = context.packageManager.getApplicationInfo(packageName, 0)
        ai.flags and ApplicationInfo.FLAG_SYSTEM == 0
    } catch (_: Exception) {
        false
    }
}

/**
 * Konfirmasi lalu hapus aplikasi via PackageInstaller API milik launcher
 * sendiri (tidak dilempar via intent ke aplikasi lain).
 */
fun confirmUninstall(context: Context, packageName: String, label: String) {
    AlertDialog.Builder(context, R.style.Theme_CiaLauncher_Dialog)
        .setTitle(R.string.uninstall_confirm_title)
        .setMessage(context.getString(R.string.uninstall_confirm_msg, label))
        .setPositiveButton(R.string.uninstall) { _, _ ->
            try {
                val callbackIntent = Intent("${context.packageName}.UNINSTALL_RESULT").apply {
                    setPackage(context.packageName)
                }
                val sender = PendingIntent.getBroadcast(
                    context,
                    0,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                ).intentSender
                context.packageManager.packageInstaller.uninstall(packageName, sender)
            } catch (e: Exception) {
                android.util.Log.e("CiaLauncher", "Uninstall gagal", e)
                Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
