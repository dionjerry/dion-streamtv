package com.streamtv.webview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.app.AlertDialog;

import org.json.JSONArray;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TvMediaControllerView extends LinearLayout {
    public interface Listener { void onCommand(String command, double value); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> buttons = new ArrayList<>();
    private final SeekBar progress;
    private final TextView elapsed;
    private final TextView remaining;
    private final TextView status;
    private final TextView playPause;
    private TextView skipButton;
    private TextView nextButton;
    private final Listener listener;
    private boolean playing;
    private boolean mediaAvailable;
    private boolean fullscreenActive;
    private double duration;
    private String subtitleData="[]", qualityData="[]", audioData="[]";
    private int aspectMode;
    private float playbackSpeed=1f;
    private final Runnable autoHide = () -> {
        if (playing) animate().alpha(0f).setDuration(220).withEndAction(() -> setVisibility(INVISIBLE)).start();
    };

    public TvMediaControllerView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(dp(22), dp(12), dp(22), dp(14));
        setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xee172238, 0xf506090f});
        bg.setCornerRadii(new float[]{dp(18),dp(18),dp(18),dp(18),0,0,0,0});
        bg.setStroke(dp(1), 0xff40516c);
        setBackground(bg);
        setVisibility(GONE);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        status = label("VIDEO READY", 12, 0xff8facdf);
        status.setTypeface(null, 1);
        elapsed = label("0:00", 14, Color.WHITE);
        remaining = label("—:—", 14, 0xffaab6c8);
        top.addView(status, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        top.addView(elapsed);
        top.addView(remaining, spaced(0, 0, dp(12), 0));
        addView(top);

        progress = new SeekBar(context);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff4f86ff));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff344158));
        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(5));
        progressParams.setMargins(0, dp(8), 0, dp(10));
        addView(progress, progressParams);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser) {}
            public void onStartTrackingTouch(SeekBar bar) { handler.removeCallbacks(autoHide); }
            public void onStopTrackingTouch(SeekBar bar) { if(duration>0) listener.onCommand("seek",duration*bar.getProgress()/1000d); reveal(); }
        });

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        row.addView(button("↶ 10", "rewind", 10));
        playPause = button("▶  PLAY", "toggle", 0);
        row.addView(playPause, spaced(dp(10), 0, 0, 0));
        row.addView(button("10 ↷", "forward", 10), spaced(dp(10), 0, 0, 0));
        row.addView(button("🔇 MUTE", "mute", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("SPEED", "speedMenu", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("CC", "subtitleMenu", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("AUDIO", "audioMenu", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("QUALITY", "qualityMenu", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("ASPECT", "aspectMenu", 0), spaced(dp(10), 0, 0, 0));
        row.addView(button("⛶ FULL", "fullscreen", 0), spaced(dp(10), 0, 0, 0));
        skipButton=button("SKIP INTRO", "skip", 0); skipButton.setVisibility(GONE);
        row.addView(skipButton, spaced(dp(10),0,0,0));
        nextButton=button("NEXT →", "next", 0); nextButton.setVisibility(GONE);
        row.addView(nextButton, spaced(dp(10),0,0,0));
        row.addView(button("HIDE", "hideControls", 0), spaced(dp(10),0,0,0));
        addView(row);
    }

    private LayoutParams spaced(int left, int top, int right, int bottom) {
        LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private TextView button(String title, String command, double value) {
        TextView view = label(title, 13, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(17), dp(9), dp(17), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(command.equals("toggle") ? 0xff245bd8 : 0xff273349);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), command.equals("toggle") ? 0xff6e99ff : 0xff44536b);
        view.setBackground(bg);
        view.setOnClickListener(v -> {
            if (command.endsWith("Menu")) showMenu(command); else listener.onCommand(command, value);
            reveal();
        });
        buttons.add(view);
        return view;
    }

    public void update(boolean available, boolean isPlaying, boolean waiting, double position, double duration) {
        mediaAvailable = available;
        playing = isPlaying;
        if (!available) { setVisibility(GONE); return; }
        playPause.setText(isPlaying ? "Ⅱ  PAUSE" : "▶  PLAY");
        status.setText(waiting ? "BUFFERING…" : isPlaying ? "NOW PLAYING" : "PAUSED");
        elapsed.setText(format(position));
        remaining.setText(duration > 0 ? format(duration) : "—:—");
        this.duration=duration;
        progress.setProgress(duration > 0 ? (int)Math.min(1000, Math.max(0, position / duration * 1000)) : 0);
        if (!fullscreenActive) { setVisibility(GONE); return; }
        if (!isPlaying || waiting) reveal(); else scheduleHide();
    }

    public boolean hasMedia() { return mediaAvailable; }
    public boolean isPlaying() { return playing; }
    public void setMediaDetails(String streamType,String subtitles,String qualities,String audio) {
        subtitleData=subtitles==null?"[]":subtitles; qualityData=qualities==null?"[]":qualities; audioData=audio==null?"[]":audio;
        status.setText(streamType==null?"VIDEO":streamType);
    }
    public void setSkipAvailable(boolean available,String label) {
        skipButton.setText(label==null||label.isEmpty()?"SKIP INTRO":label.toUpperCase(Locale.US));
        skipButton.setVisibility(available?VISIBLE:GONE);
    }
    public void setNextAvailable(boolean available,double position,double duration) {
        nextButton.setVisibility(available&&duration>0&&duration-position<=90?VISIBLE:GONE);
    }
    public boolean isShowingControls() { return getVisibility() == VISIBLE && getAlpha() > .1f; }

    public void setFullscreenActive(boolean active) {
        fullscreenActive=active;
        if(!active)setVisibility(GONE);else reveal();
    }

    public void reveal() {
        if (!mediaAvailable || !fullscreenActive) return;
        handler.removeCallbacks(autoHide);
        animate().cancel(); setAlpha(1f); setVisibility(VISIBLE); bringToFront();
        scheduleHide();
    }

    public void hideNow() {
        handler.removeCallbacks(autoHide);
        setVisibility(INVISIBLE);
    }

    private void scheduleHide() {
        handler.removeCallbacks(autoHide);
        if (playing) handler.postDelayed(autoHide, 3200);
    }

    public boolean clickAt(int screenX, int screenY) {
        if (!isShowingControls()) return false;
        for (TextView button : buttons) {
            Rect bounds = new Rect();
            if (button.getGlobalVisibleRect(bounds) && bounds.contains(screenX, screenY)) {
                button.performClick(); return true;
            }
        }
        Rect seekBounds=new Rect();
        if(progress.getGlobalVisibleRect(seekBounds)&&seekBounds.contains(screenX,screenY)&&duration>0) {
            double fraction=Math.max(0,Math.min(1,(screenX-seekBounds.left)/(double)Math.max(1,seekBounds.width())));
            progress.setProgress((int)(fraction*1000)); listener.onCommand("seek",duration*fraction); reveal(); return true;
        }
        return false;
    }

    private void showMenu(String command) {
        if ("speedMenu".equals(command)) {
            String[] labels={"0.75×","1×","1.25×","1.5×","2×"}; double[] values={.75,1,1.25,1.5,2};
            new AlertDialog.Builder(getContext()).setTitle("Playback speed").setItems(labels,(d,i)->{playbackSpeed=(float)values[i];listener.onCommand("speed",values[i]);}).show(); return;
        }
        if ("aspectMenu".equals(command)) {
            String[] labels={"Fit","Fill","Stretch","Original"};
            new AlertDialog.Builder(getContext()).setTitle("Aspect mode").setSingleChoiceItems(labels,aspectMode,(d,i)->{aspectMode=i;listener.onCommand("aspect",i);d.dismiss();}).show(); return;
        }
        String data="subtitleMenu".equals(command)?subtitleData:"audioMenu".equals(command)?audioData:qualityData;
        String title="subtitleMenu".equals(command)?"Subtitles":"audioMenu".equals(command)?"Audio track":"Video quality";
        String action="subtitleMenu".equals(command)?"subtitle":"audioMenu".equals(command)?"audio":"quality";
        try {
            JSONArray array=new JSONArray(data);
            if(array.length()==0){new AlertDialog.Builder(getContext()).setTitle(title).setMessage("No selectable tracks were exposed by this website.").setPositiveButton("OK",null).show();return;}
            String[] labels=new String[array.length()]; for(int i=0;i<array.length();i++)labels[i]=array.getJSONObject(i).optString("label","Track "+(i+1));
            new AlertDialog.Builder(getContext()).setTitle(title).setItems(labels,(d,i)->listener.onCommand(action,i)).show();
        } catch(Exception ignored) {}
    }

    private String format(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "0:00";
        int total = (int)seconds;
        return total >= 3600
            ? String.format(Locale.US, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
            : String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
}
