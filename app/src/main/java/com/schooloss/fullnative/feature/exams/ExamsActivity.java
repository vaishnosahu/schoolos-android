package com.schooloss.fullnative.feature.exams;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ExamsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","exams"); super.onCreate(b); } }