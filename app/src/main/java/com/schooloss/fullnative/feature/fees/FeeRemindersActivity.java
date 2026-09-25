package com.schooloss.fullnative.feature.fees;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class FeeRemindersActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","fee_reminders"); super.onCreate(b); } }