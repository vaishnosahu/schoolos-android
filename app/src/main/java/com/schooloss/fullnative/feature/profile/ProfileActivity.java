package com.schooloss.fullnative.feature.profile;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class ProfileActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","profile"); super.onCreate(b); } }