package com.schooloss.fullnative.feature.billing;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class BillingActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","billing"); super.onCreate(b); } }