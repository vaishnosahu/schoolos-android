package com.schooloss.fullnative.feature.lessonplans;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class LessonPlansActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","lesson_plans"); super.onCreate(b); } }