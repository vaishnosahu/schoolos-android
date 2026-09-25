package com.schooloss.fullnative.feature.assignments;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class AssignmentsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","assignments"); super.onCreate(b); } }