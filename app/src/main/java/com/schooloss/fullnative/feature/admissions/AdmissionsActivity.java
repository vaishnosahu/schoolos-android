package com.schooloss.fullnative.feature.admissions;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class AdmissionsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","admissions"); super.onCreate(b); } }