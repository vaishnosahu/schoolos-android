package com.schooloss.fullnative.feature.attendance;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class AttendanceActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","attendance"); super.onCreate(b); } }