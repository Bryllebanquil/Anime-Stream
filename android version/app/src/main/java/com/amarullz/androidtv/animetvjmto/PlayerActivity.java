package com.amarullz.androidtv.animetvjmto;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {
    private AnimeView aView;
    private WebView webView;
    private RelativeLayout controlsLayout;
    private ImageButton playPauseButton;
    private ImageButton fullscreenButton;
    private SeekBar seekBar;
    private TextView titleTextView;
    private TextView timeTextView;
    private boolean isControlsVisible = true;
    private boolean isFullscreen = false;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);
        
        // Initialize views
        webView = findViewById(R.id.player_webview);
        controlsLayout = findViewById(R.id.player_controls);
        playPauseButton = findViewById(R.id.play_pause_button);
        fullscreenButton = findViewById(R.id.fullscreen_button);
        seekBar = findViewById(R.id.seekbar);
        titleTextView = findViewById(R.id.title_text);
        timeTextView = findViewById(R.id.time_text);
        
        // Get URL from intent
        String videoUrl = getIntent().getStringExtra("videoUrl");
        String videoTitle = getIntent().getStringExtra("videoTitle");
        
        // Set title
        if (videoTitle != null) {
            titleTextView.setText(videoTitle);
        }
        
        // Initialize AnimeView
        aView = new AnimeView(this);
        
        // Set up controls
        setupControls();
        
        // Load the video
        if (videoUrl != null) {
            aView.videoSetSource(videoUrl);
        }
    }
    
    private void setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener(v -> {
            if (aView.videoPlayer != null) {
                boolean isPlaying = aView.videoPlayer.getPlayWhenReady();
                if (isPlaying) {
                    aView.videoPlayerPause();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    aView.videoPlayerPlay();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                }
            }
        });
        
        // Fullscreen button
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        
        // Seek bar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && aView.videoPlayer != null) {
                    long duration = aView.videoPlayer.getDuration();
                    long seekPosition = (duration * progress) / 100;
                    aView.videoPlayer.seekTo(seekPosition);
                    updateTimeText(seekPosition, duration);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });
        
        // Hide controls after a delay
        controlsLayout.postDelayed(this::hideControls, 3000);
        
        // Show controls on tap
        webView.setOnClickListener(v -> {
            if (isControlsVisible) {
                hideControls();
            } else {
                showControls();
            }
        });
    }
    
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            fullscreenButton.setImageResource(android.R.drawable.ic_menu_revert);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            fullscreenButton.setImageResource(android.R.drawable.ic_menu_crop);
        }
    }
    
    private void showControls() {
        isControlsVisible = true;
        controlsLayout.setVisibility(View.VISIBLE);
        controlsLayout.postDelayed(this::hideControls, 3000);
    }
    
    private void hideControls() {
        isControlsVisible = false;
        controlsLayout.setVisibility(View.GONE);
    }
    
    private void updateTimeText(long position, long duration) {
        String timeText = formatTime(position) + " / " + formatTime(duration);
        timeTextView.setText(timeText);
    }
    
    private String formatTime(long timeMs) {
        long seconds = (timeMs / 1000) % 60;
        long minutes = (timeMs / (1000 * 60)) % 60;
        long hours = (timeMs / (1000 * 60 * 60)) % 24;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle rotation, update the player size, etc.
        aView.setFullscreen(newConfig.orientation);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (aView.videoPlayer != null) {
            aView.videoPlayerPause();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (aView.videoPlayer != null && !isControlsVisible) {
            aView.videoPlayerPlay();
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isFullscreen) {
                toggleFullscreen();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
} 