package com.example.caloriecounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CalorieWidget : AppWidgetProvider() {
 override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
 override fun onReceive(context: Context, intent: Intent) { super.onReceive(context,intent);refresh(context) }
 companion object {
  fun refresh(context: Context) {
   val manager=AppWidgetManager.getInstance(context)
   val ids=manager.getAppWidgetIds(ComponentName(context,CalorieWidget::class.java))
   if(ids.isEmpty()) return
   runCatching {
    Store(context).use { store ->
     val date=LocalDate.now();val entries=store.entries(date.toString());val p=store.profile()
     val calories=partialTotal(entries.map { it.total() },0);val remaining=p.targets[0]-calories
     val partial=entries.any { it.total()[0]==null }
     val views=RemoteViews(context.packageName,R.layout.calorie_widget)
     views.setTextViewText(R.id.widget_date,date.format(DateTimeFormatter.ofPattern("EEE d MMM")))
     views.setTextViewText(R.id.widget_used,"${numberText(calories,0)} kcal eaten"+if(partial) " (partial)" else "")
     views.setTextViewText(R.id.widget_left,if(remaining>=0) "${numberText(remaining,0)} kcal remaining" else "${numberText(-remaining,0)} kcal over target")
     val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
     views.setOnClickPendingIntent(R.id.widget_root,open)
     manager.updateAppWidget(ids,views)
    }
   }
  }
 }
}
