package com.schooloss.fullnative.feature.results;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ResultsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","results"); super.onCreate(b); } }