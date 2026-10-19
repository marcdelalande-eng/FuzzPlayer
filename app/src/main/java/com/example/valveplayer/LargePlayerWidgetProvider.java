package com.example.valveplayer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Widget distinct dans le sélecteur Android : 4 colonnes × 2 lignes. */
public class LargePlayerWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) PlayerWidgetProvider.updateLarge(context, manager, id);
    }
}
