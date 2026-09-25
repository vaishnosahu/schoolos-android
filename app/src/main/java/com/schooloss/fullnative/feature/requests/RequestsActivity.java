package com.schooloss.fullnative.feature.requests;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class RequestsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","requests"); super.onCreate(b); } }