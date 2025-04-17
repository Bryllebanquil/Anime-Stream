package com.amarullz.androidtv.animetvjmto;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsCompat;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.devbrackets.android.exomedia.core.video.scale.ScaleType;
import com.devbrackets.android.exomedia.core.video.surface.SurfaceEnvelope;
import com.devbrackets.android.exomedia.core.video.surface.SurfaceViewSurfaceEnvelope;
import com.devbrackets.android.exomedia.nmp.ExoMediaPlayerImpl;
import com.devbrackets.android.exomedia.nmp.config.PlayerConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@UnstableApi 
public class AnimeView extends WebViewClient {
    private static final String _TAG = "ATVLOG-VIEW";
    private final Activity activity;
    public final WebView webView;
    public SurfaceView videoView = null;
    public ExoMediaPlayerImpl videoPlayer = null;
    public final ImageView splash;
    public final FrameLayout videoLayout;
    public String playerInjectString;
    public boolean webViewReady = false;
    public static boolean USE_WEB_VIEW_ASSETS = false;

    public AudioManager audioManager;

    public int sysBrightness;
    public void initSysConfig() {
        try {
            sysBrightness = Settings.System.getInt(
                    activity.getContentResolver(), "screen_brightness"
            );
        } catch (Exception ignored) {
            sysBrightness = 127;
        }
        Log.d(_TAG, "ATVLOG Current Sys Brightness = " + sysBrightness);
    }

    public int sysheightNav = 0;
    public int sysheightStat = 0;

    @SuppressLint({"InternalInsetResource", "DiscouragedApi"})
    public void updateInsets() {
        sysheightStat = 0;
        sysheightNav = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsets insets =
                    activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets();
            sysheightStat =
                    px2dp(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
            sysheightNav =
                    px2dp(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
        }
        Log.d(_TAG, "SYS-BAR Size: " + sysheightStat + " / " + sysheightNav);
    }

    public void setFullscreen(int orientation) {
        if (orientation == 0) {
            orientation = activity.getResources().getConfiguration().orientation;
        }

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Portrait mode - show UI elements
            if (activity instanceof AppCompatActivity) {
                ((AppCompatActivity) activity).getSupportActionBar().show();
            }
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            // Landscape mode - full screen video
            if (activity instanceof AppCompatActivity) {
                ((AppCompatActivity) activity).getSupportActionBar().hide();
            }
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void webviewInitConfig(WebView wv) {
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (chromeClient == null) {
            chromeClient = new WebChromeClient() {
                @Override
                public Bitmap getDefaultVideoPoster() {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                }

                @Override
                public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Confirm")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok,
                                    (dialog, which) -> result.confirm())
                            .setNegativeButton(android.R.string.cancel,
                                    (dialog, which) -> result.cancel())
                            .setCancelable(false)
                            .create()
                            .show();
                    return true;
                }

                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Alert")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok,
                                    (dialog, which) -> result.confirm())
                            .setCancelable(false)
                            .create()
                            .show();
                    return true;
                }

                @Override
                public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                    final EditText input = new EditText(activity);
                    input.setText(defaultValue);
                    
                    new AlertDialog.Builder(activity)
                            .setTitle("Prompt")
                            .setMessage(message)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok,
                                    (dialog, which) -> result.confirm(input.getText().toString()))
                            .setNegativeButton(android.R.string.cancel,
                                    (dialog, which) -> result.cancel())
                            .setCancelable(false)
                            .create()
                            .show();
                    return true;
                }
            };
        }
        wv.setWebChromeClient(chromeClient);
    }

    public WebChromeClient chromeClient = null;
    public interface ChromePromptCallback {
        void confirm(String res);
        void cancel();
    }

    public int dp2px(float dpValue) {
        final float scale = activity.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public int px2dp(float px) {
        return (int) (px / activity.getResources().getDisplayMetrics().density);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public AnimeView(Activity mainActivity) {
        activity = mainActivity;
        
        webView = activity.findViewById(R.id.webview);
        videoLayout = activity.findViewById(R.id.video_layout);
        splash = activity.findViewById(R.id.splash);
        
        // Configure web view
        webviewInitConfig(webView);
        webView.setWebViewClient(this);
        
        // Add JavaScript interface
        JSViewApi jsApi = new JSViewApi();
        webView.addJavascriptInterface(jsApi, "JsApi");
        
        // Initialize system configuration
        initSysConfig();
        updateInsets();
        
        // Setup audio manager
        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        
        // Initialize video player
        initVideoView();
        
        // Load the web interface
        webView.loadUrl("file:///android_asset/index.html");
    }

    public void reloadView() {
        webView.reload();
    }

    public void videoViewSetScale(int type) {
        if (videoPlayer != null) {
            ScaleType scaleType = type == 1 ? ScaleType.CENTER_CROP : ScaleType.FIT_CENTER;
            videoPlayer.setScaleType(scaleType);
        }
    }

    public SurfaceEnvelope videoViewEnvelope;
    public PlayerConfig videoPlayerConfig;

    public int videoSizeWidth = 0;
    public int videoSizeHeight = 0;
    public void setVideoSize(int w, int h) {
        videoSizeWidth = w;
        videoSizeHeight = h;
    }

    public String videoAudioLanguage = "";
    public int videoSelectedQuality = 0; /* auto */

    @SuppressLint("UnsafeOptInUsageError")
    public void initVideoView() {
        if (videoView == null) {
            videoView = new SurfaceView(activity);
            videoLayout.addView(videoView);
            videoViewEnvelope = new SurfaceViewSurfaceEnvelope(videoView);
        }

        if (videoPlayer == null) {
            // Create ExoPlayer
            ExoPlayer exoPlayer = new ExoPlayer.Builder(activity).build();
            
            // Additional player setup can be done here
        }
    }

    public MainActivity me() {
        return (MainActivity) activity;
    }

    public void videoSetSource(String url) {
        if (activity instanceof PlayerActivity) {
            // Handle video playback in PlayerActivity
        } else {
            // Launch PlayerActivity for video playback
            Intent intent = new Intent(activity, PlayerActivity.class);
            intent.putExtra("videoUrl", url);
            intent.putExtra("videoTitle", me()._metaTitle);
            activity.startActivity(intent);
        }
    }

    public void videoPlayerPlay() {
        if (videoPlayer != null) {
            videoPlayer.setPlayWhenReady(true);
        }
    }

    public void videoPlayerPause() {
        if (videoPlayer != null) {
            videoPlayer.setPlayWhenReady(false);
        }
    }

    public void videoPlayerStop() {
        if (videoPlayer != null) {
            videoPlayer.stop();
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, WebResourceRequest request) {
        // Handle resource interception here if needed
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        webViewReady = true;
        splash.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        
        // Update arguments if needed
        updateArgs();
    }

    public void updateArgs() {
        if (MainActivity.ARG_URL != null) {
            webView.loadUrl("javascript:ui_openurl('" + MainActivity.ARG_URL + "')");
        }
    }

    public boolean pnUpdated = false;
    public String pnTitle = "";
    public String pnDesc = "";
    public String pnPoster = "";
    public String pnUri = "";
    public String pnTip = "";
    public int pnSd = 1;
    public int pnPos = 0;
    public int pnDuration = 0;

    private int videoStatCurrentPosition = 0;
    private boolean videoStatIsPlaying = false;
    private int videoStatScaleType = 0;
    private String videoStatCurrentUrl = "";

    public void onStartPause(boolean isStart) {
        if (isStart) {
            // Resume playback if needed
            if (videoStatIsPlaying && videoPlayer != null) {
                videoPlayer.setPlayWhenReady(true);
            }
        } else {
            // Save state and pause playback
            if (videoPlayer != null) {
                videoStatIsPlaying = videoPlayer.getPlayWhenReady();
                videoStatCurrentPosition = (int) videoPlayer.getCurrentPosition();
                videoPlayer.setPlayWhenReady(false);
            }
        }
    }

    public void onSaveRestore(boolean isSave, Bundle bundle) {
        if (isSave) {
            // Save state
            if (videoPlayer != null) {
                bundle.putInt("video_position", (int) videoPlayer.getCurrentPosition());
                bundle.putBoolean("video_playing", videoPlayer.getPlayWhenReady());
                bundle.putString("video_url", videoStatCurrentUrl);
                bundle.putInt("video_scale", videoStatScaleType);
            }
        } else {
            // Restore state
            if (bundle.containsKey("video_position")) {
                videoStatCurrentPosition = bundle.getInt("video_position");
                videoStatIsPlaying = bundle.getBoolean("video_playing");
                videoStatCurrentUrl = bundle.getString("video_url");
                videoStatScaleType = bundle.getInt("video_scale");
                
                if (videoStatCurrentUrl != null && !videoStatCurrentUrl.isEmpty()) {
                    videoSetSource(videoStatCurrentUrl);
                }
            }
        }
    }

    @UnstableApi 
    public class JSViewApi {
        @JavascriptInterface
        public void appQuit() {
            activity.finish();
        }

        @JavascriptInterface
        public void showToast(String txt) {
            activity.runOnUiThread(() -> 
                Toast.makeText(activity, txt, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void reloadHome() {
            activity.runOnUiThread(() -> webView.reload());
        }

        @JavascriptInterface
        public void goToUrl(String url) {
            activity.runOnUiThread(() -> webView.loadUrl(url));
        }

        @JavascriptInterface
        public void videoSetUrl(String url) {
            activity.runOnUiThread(() -> videoSetSource(url));
        }

        @JavascriptInterface
        public int videoBufferPercent() {
            if (videoPlayer != null) {
                return videoPlayer.getBufferedPercentage();
            }
            return 0;
        }

        @JavascriptInterface
        public void videoSetScale(int type) {
            videoStatScaleType = type;
            activity.runOnUiThread(() -> videoViewSetScale(type));
        }

        @JavascriptInterface
        public void openIntentUri(String s) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(s));
                activity.startActivity(intent);
            } catch (Exception e) {
                Log.e(_TAG, "Error opening intent: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public int videoGetDuration() {
            if (videoPlayer != null) {
                return (int) videoPlayer.getDuration();
            }
            return 0;
        }

        @JavascriptInterface
        public boolean videoIsPlaying() {
            if (videoPlayer != null) {
                return videoPlayer.getPlayWhenReady();
            }
            return false;
        }

        @JavascriptInterface
        public int videoGetPosition() {
            if (videoPlayer != null) {
                return (int) videoPlayer.getCurrentPosition();
            }
            return 0;
        }

        @JavascriptInterface
        public void videoPlay(boolean play) {
            activity.runOnUiThread(() -> {
                if (play) {
                    videoPlayerPlay();
                    me().mediaSetState(PlaybackState.STATE_PLAYING, videoGetPosition());
                } else {
                    videoPlayerPause();
                    me().mediaSetState(PlaybackState.STATE_PAUSED, videoGetPosition());
                }
            });
        }

        @JavascriptInterface
        public void videoSetMeta(String title, String artist, String poster) {
            me().mediaSetMeta(title, artist, poster);
        }

        @JavascriptInterface
        public void videoHaveNP(boolean n, boolean p) {
            me().mediaSetPrevNext(n, p);
        }

        @JavascriptInterface
        public String getVersion(int type) {
            if (type == 0) {
                return BuildConfig.VERSION_NAME;
            } else {
                return String.valueOf(BuildConfig.VERSION_CODE);
            }
        }
    }
} 