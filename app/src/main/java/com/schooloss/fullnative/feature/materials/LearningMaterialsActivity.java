package com.schooloss.fullnative.feature.materials;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class LearningMaterialsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","learning_materials"); super.onCreate(b); } }