package com.schooloss.fullnative.feature.reports;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ReportsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","reports"); super.onCreate(b); } }