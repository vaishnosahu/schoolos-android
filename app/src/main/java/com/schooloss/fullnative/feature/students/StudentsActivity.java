package com.schooloss.fullnative.feature.students;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class StudentsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","students"); super.onCreate(b); } }