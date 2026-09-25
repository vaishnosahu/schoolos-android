package com.schooloss.fullnative.feature.staff;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class StaffActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","staff"); super.onCreate(b); } }