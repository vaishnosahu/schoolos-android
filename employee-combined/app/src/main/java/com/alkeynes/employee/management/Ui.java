package com.alkeynes.employee.management;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

public final class Ui {
    public static final int NAVY=Color.rgb(15,39,71), BLUE=Color.rgb(23,105,224), BG=Color.rgb(244,247,251),
            TEXT=Color.rgb(20,32,51), MUTED=Color.rgb(104,118,138), BORDER=Color.rgb(222,229,238),
            GREEN=Color.rgb(24,139,86), AMBER=Color.rgb(199,132,20), RED=Color.rgb(190,63,63);
    private final Context c;
    public Ui(Context c){this.c=c;}
    public int dp(int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    public LinearLayout column(){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(0,1.08f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    public TextView label(String s){return text(s,15,TEXT,true);}
    public TextView muted(String s){return text(s,12,MUTED,false);}
    public TextView section(String s){return text(s,17,NAVY,true);}
    public LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(15),dp(16),dp(15));v.setBackground(round(Color.WHITE,18));v.setElevation(dp(1));return v;}
    public LinearLayout softCard(){LinearLayout v=column();v.setPadding(dp(16),dp(15),dp(16),dp(15));v.setBackground(round(Color.rgb(249,251,254),18));return v;}
    public LinearLayout darkCard(){LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(round(NAVY,20));v.setElevation(dp(2));return v;}
    public GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    public GradientDrawable stroke(int color,int border,float radius,int width){GradientDrawable g=round(color,radius);g.setStroke(dp(width),border);return g;}
    public GradientDrawable circle(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
    public TextView pill(String s,int bg,int fg){TextView v=text(s,10,fg,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(9),dp(5),dp(9),dp(5));v.setBackground(round(bg,99));return v;}
    public EditText input(String hint,int type){EditText e=new EditText(c);e.setHint(hint);e.setTextSize(15);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(137,150,168));e.setInputType(type);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(stroke(Color.WHITE,BORDER,12,1));e.setMinHeight(dp(52));return e;}
    public Button primary(String s){return button(s,NAVY,Color.WHITE);}
    public Button secondary(String s){return button(s,Color.WHITE,NAVY);}
    public Button danger(String s){return button(s,RED,Color.WHITE);}
    public Button button(String s,int bg,int fg){Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setTextColor(fg);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(50));if(bg==Color.WHITE)b.setBackground(stroke(bg,BORDER,14,1));else b.setBackground(round(bg,14));return b;}
    public LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    public LinearLayout.LayoutParams match(int l,int t,int r,int b){LinearLayout.LayoutParams p=match();p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    public LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-2,-2);}
    public LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    public ViewRow kv(String key,String value){return new ViewRow(key,value);}
    public final class ViewRow extends LinearLayout{
        ViewRow(String key,String value){super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(0,dp(9),0,0);addView(text(key,12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1f));addView(text(value,13,TEXT,true),wrap());}
    }
}
