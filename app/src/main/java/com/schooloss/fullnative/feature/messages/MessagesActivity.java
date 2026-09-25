package com.schooloss.fullnative.feature.messages;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class MessagesActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","messages"); super.onCreate(b); } }