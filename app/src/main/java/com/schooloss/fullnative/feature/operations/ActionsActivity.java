package com.schooloss.fullnative.feature.operations;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ActionsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","actions"); super.onCreate(b); } }