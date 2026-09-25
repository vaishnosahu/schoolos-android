package com.schooloss.fullnative.feature.fees;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class FeesActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","fees"); super.onCreate(b); } }