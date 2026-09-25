package com.schooloss.fullnative.feature.audit;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class AuditActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","audit"); super.onCreate(b); } }