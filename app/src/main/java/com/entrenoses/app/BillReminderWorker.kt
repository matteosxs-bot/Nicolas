package com.entrenoses.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object BillReminderScheduler {
    private const val UNIQUE_WORK = "entre_noses_bill_reminders"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

internal class BillReminderWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val bills = JSONArray(securePreferences(applicationContext).getString("bills", "[]"))
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))
            val due = mutableListOf<Pair<String, Long>>()
            for (i in 0 until bills.length()) {
                val bill = bills.optJSONObject(i) ?: continue
                if (bill.optBoolean("paid", false)) continue
                val title = bill.optString("title").ifBlank { "Conta sem nome" }
                val dueDate = runCatching { LocalDate.parse(bill.optString("dueDate"), formatter) }.getOrNull() ?: continue
                val days = ChronoUnit.DAYS.between(today, dueDate)
                if (days <= 3L) due.add(title to days)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Contas a vencer",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Lembretes de contas pendentes do Entre Noses" }
                )
            }
            if (due.isEmpty()) {
                NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
                return Result.success()
            }
            val lines = due.sortedBy { it.second }.take(5).joinToString("\n") { (title, days) ->
                when {
                    days < 0 -> "$title: vencida há ${-days} dia(s)"
                    days == 0L -> "$title: vence hoje"
                    else -> "$title: vence em ${days} dia(s)"
                }
            }
            val extra = if (due.size > 5) "\n+ ${due.size - 5} outras contas" else ""
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (due.size == 1) "Conta próxima do vencimento" else "${due.size} contas próximas do vencimento")
                .setContentText(lines.lines().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines + extra))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private companion object {
        const val CHANNEL_ID = "bill_due_reminders"
        const val NOTIFICATION_ID = 4801
    }
}
