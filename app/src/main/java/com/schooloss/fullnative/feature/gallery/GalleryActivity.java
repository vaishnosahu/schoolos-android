package com.schooloss.fullnative.feature.gallery;
import android.os.Bundle;import com.schooloss.fullnative.feature.common.FeatureActivity;
public final class GalleryActivity extends FeatureActivity { @Override protected void onCreate(Bundle b){ getIntent().putExtra("feature","gallery"); super.onCreate(b); } }