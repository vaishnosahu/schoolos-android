package com.schooloss.fullnative.feature.timetable;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class TimetableActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","timetable"); super.onCreate(b); } }