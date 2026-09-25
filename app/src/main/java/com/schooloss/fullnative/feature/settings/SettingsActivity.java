package com.schooloss.fullnative.feature.settings;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class SettingsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","settings"); super.onCreate(b); } }