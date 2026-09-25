package com.schooloss.fullnative.core;
import android.content.Context;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;import com.google.android.material.button.MaterialButton;import com.google.android.material.card.MaterialCardView;
public final class Ui {
 private Ui(){} public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
 public static LinearLayout v(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;} public static LinearLayout h(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
 public static TextView text(Context c,String s,int sp,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
 public static Space gap(Context c,int h){Space s=new Space(c);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h)));return s;}
 public static MaterialCardView card(Context c){MaterialCardView x=new MaterialCardView(c);x.setRadius(dp(c,18));x.setCardElevation(dp(c,2));x.setCardBackgroundColor(Color.WHITE);x.setStrokeWidth(dp(c,1));x.setStrokeColor(Constants.LINE);return x;}
 public static MaterialButton button(Context c,String label,int bg,int fg){MaterialButton b=new MaterialButton(c);b.setText(label);b.setAllCaps(false);b.setTextColor(fg);b.setBackgroundColor(bg);b.setCornerRadius(dp(c,14));return b;}
 public static GradientDrawable round(int fill,float r){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(r);return d;}
 public static String pretty(String s){if(s==null)return"";String[] ps=s.replace('_',' ').split(" ");StringBuilder b=new StringBuilder();for(String p:ps){if(p.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
}