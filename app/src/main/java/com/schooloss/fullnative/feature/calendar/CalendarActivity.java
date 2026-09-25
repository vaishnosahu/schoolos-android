package com.schooloss.fullnative.feature.calendar;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class CalendarActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","calendar"); super.onCreate(b); } }