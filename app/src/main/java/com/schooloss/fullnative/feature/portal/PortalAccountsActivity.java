package com.schooloss.fullnative.feature.portal;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class PortalAccountsActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","portal_accounts"); super.onCreate(b); } }