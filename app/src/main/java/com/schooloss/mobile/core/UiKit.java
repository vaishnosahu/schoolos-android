package com.schooloss.nativeapp.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

public final class UiKit {
    public static final int BG = Color.rgb(247,249,253);
    public static final int TEXT = Color.rgb(25,33,50);
    public static final int MUTED = Color.rgb(106,116,140);
    public static final int LINE = Color.rgb(229,233,242);
    public static final int BLUE = Color.rgb(54,87,214);

    private UiKit() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        l.setBackground(round(c, Color.WHITE, 18, LINE));
        l.setElevation(dp(c,2));
        return l;
    }

    public static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value == null ? "" : value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    public static TextView label(Context c, String value, int sp, int color) {
        TextView t = text(c, value, sp, color, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    public static TextView title(Context c, String value, int sp) {
        TextView t = text(c, value, sp, TEXT, true);
        t.setPadding(0, dp(c,5), 0, dp(c,4));
        return t;
    }

    public static TextView body(Context c, String value, int sp) {
        TextView t = text(c, value, sp, MUTED, false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static TextView center(Context c, String value, int sp, int color, boolean bold) {
        TextView t = text(c, value, sp, color, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static Button button(Context c, String value, int bg, int fg) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(c,bg,14,bg));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(c,50)));
        return b;
    }

    public static EditText input(Context c, String hint, boolean password) {
        EditText e = new EditText(c);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(155,163,182));
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(dp(c,13),0,dp(c,13),0);
        e.setBackground(round(c,Color.rgb(250,251,254),14,LINE));
        if (password) {
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        }
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(c,48)));
        return e;
    }

    public static View gap(Context c, int h) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h)));
        return s;
    }

    public static GradientDrawable round(Context c, int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c,radius));
        d.setStroke(dp(c,1),stroke);
        return d;
    }

    public static GradientDrawable gradient(Context c, int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c,radius));
        return d;
    }

    public static int tint(int color, float amount) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        if (amount >= 0) {
            r = (int)(r + (255-r)*amount);
            g = (int)(g + (255-g)*amount);
            b = (int)(b + (255-b)*amount);
        } else {
            float f = 1f + amount;
            r = (int)(r*f); g = (int)(g*f); b = (int)(b*f);
        }
        return Color.rgb(clamp(r),clamp(g),clamp(b));
    }

    private static int clamp(int n) { return Math.max(0,Math.min(255,n)); }
}
