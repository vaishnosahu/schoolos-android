package com.schooloss.fullnative.feature.subjects;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class SubjectsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","subjects"); super.onCreate(b); } }