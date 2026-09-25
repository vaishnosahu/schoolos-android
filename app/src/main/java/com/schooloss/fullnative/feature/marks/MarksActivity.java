package com.schooloss.fullnative.feature.marks;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class MarksActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","marks"); super.onCreate(b); } }