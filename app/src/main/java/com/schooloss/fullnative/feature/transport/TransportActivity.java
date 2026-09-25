package com.schooloss.fullnative.feature.transport;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class TransportActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","transport"); super.onCreate(b); } }