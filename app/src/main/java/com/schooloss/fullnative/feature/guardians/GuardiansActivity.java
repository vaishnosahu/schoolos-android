package com.schooloss.fullnative.feature.guardians;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class GuardiansActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","guardians"); super.onCreate(b); } }