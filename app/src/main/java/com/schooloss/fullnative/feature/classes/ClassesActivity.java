package com.schooloss.fullnative.feature.classes;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ClassesActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","classes"); super.onCreate(b); } }