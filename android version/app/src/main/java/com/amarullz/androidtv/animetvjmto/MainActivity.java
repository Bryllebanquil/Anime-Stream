package com.amarullz.androidtv.animetvjmto;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.util.UnstableApi;

import java.io.File;
import java.io.IOException;

@UnstableApi
public class MainActivity extends AppCompatActivity {
  public AnimeView aView;
  public static String ARG_URL=null;
  public static String ARG_TIP=null;
  public static String ARG_POS=null;
  public static String ARG_SD=null;

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    aView.setFullscreen(newConfig.orientation);
  }

  public void updateInstance(Bundle savedInstanceState){
    /* Load Arguments */
    if (savedInstanceState == null) {
      Bundle extras = getIntent().getExtras();
      if(extras != null) {
        ARG_URL= extras.getString("viewurl");
        ARG_TIP= extras.getString("viewtip");
        ARG_POS=extras.getString("viewpos");
        ARG_SD=extras.getString("viewsd");
      }
      else{
        ARG_URL=null;
        ARG_TIP=null;
        ARG_POS=null;
      }
    } else {
      ARG_URL= (String) savedInstanceState.getSerializable("viewurl");
      ARG_TIP= (String) savedInstanceState.getSerializable("viewtip");
      ARG_POS= (String) savedInstanceState.getSerializable("viewpos");
      ARG_SD= (String) savedInstanceState.getSerializable("viewsd");
    }
  }

  private void initRefreshRate(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Window w = getWindow();
      WindowManager.LayoutParams p = w.getAttributes();
      Display.Mode[] modes = getDisplay().getSupportedModes();
      Display.Mode cmode = getDisplay().getMode();
      Log.d("ATVLOG",
          "Current Mode "+cmode.getModeId()+" : " + cmode.getPhysicalWidth() + "x"+cmode.getPhysicalHeight()+"@"+cmode.getRefreshRate()+"hz");

      //find display mode with max hz
      int maxMode = -1;
      float maxHZ = 60f;
      for (Display.Mode m : modes) {
        Log.d("ATVLOG",
            "Mode "+m.getModeId()+" : " + m.getPhysicalWidth() + "x"+m.getPhysicalHeight()+"@"+m.getRefreshRate()+"hz");
        if (cmode.getPhysicalHeight() == m.getPhysicalHeight() && cmode.getPhysicalWidth() == m.getPhysicalWidth()) {
          if (cmode.getRefreshRate() <= m.getRefreshRate()) {
            maxHZ = m.getRefreshRate();
            maxMode = m.getModeId();
          }
        }
      }
      if (maxMode>-1){
        p.preferredDisplayModeId = maxMode;
        w.setAttributes(p);
        Log.d("ATVLOG", "Max Mode Value : " + maxHZ + "hz");
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.activity_main);

    initRefreshRate();
    initBluetooth();
    updateInstance(savedInstanceState);
    aView = new AnimeView(this);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if ((keyCode == KeyEvent.KEYCODE_BACK) && aView.webView.canGoBack()) {
      aView.webView.goBack();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      aView.webView.loadUrl("javascript:ui_showSettings()");
      return true;
    } else if (id == R.id.action_reload) {
      aView.reloadView();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @SuppressLint("RestrictedApi")
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (MediaButtonReceiver.handleIntent(null, new Intent(Intent.ACTION_MEDIA_BUTTON, null, this, MainActivity.class).putExtra(Intent.EXTRA_KEY_EVENT, event)) == null){
      return super.dispatchKeyEvent(event);
    }
    return true;
  }

  @Override
  protected void onStop() {
    super.onStop();
    aView.onStartPause(false);
    if (mSession!=null) mSession.setActive(false);
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (mSession!=null) mSession.setActive(true);
  }

  @Override
  protected void onPause() {
    super.onPause();
    aView.onStartPause(false);
    Bundle b = new Bundle();
    aView.onSaveRestore(true,b);
  }

  @Override
  protected void onResume() {
    super.onResume();
    aView.onStartPause(true);
    Bundle b = new Bundle();
    aView.onSaveRestore(false,b);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState)
  {
    super.onSaveInstanceState(outState);
    aView.onSaveRestore(true,outState);
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState)
  {
    super.onRestoreInstanceState(savedInstanceState);
    aView.onSaveRestore(false,savedInstanceState);
  }

  @Override
  protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);
    setIntent(intent);
    Bundle extras = intent.getExtras();
    if(extras != null) {
      ARG_URL= extras.getString("viewurl");
      ARG_TIP= extras.getString("viewtip");
      ARG_POS= extras.getString("viewpos");
      aView.updateArgs();
    }
  }

  public MediaSession mSession=null;

  public void mediaExec(String cmd, long p){
    if (aView.webView!=null) {
      aView.webView.post(() -> {
        if (cmd.equals("play") || cmd.equals("pause")) {
          aView.webView.loadUrl("javascript:player_playpause()");
        } else if (cmd.equals("stop")) {
          aView.webView.loadUrl("javascript:player_stop()");
        } else if (cmd.equals("prev")) {
          aView.webView.loadUrl("javascript:player_prev()");
        } else if (cmd.equals("next")) {
          aView.webView.loadUrl("javascript:player_next()");
        } else if (cmd.equals("seek")) {
          aView.webView.loadUrl("javascript:player_seek_to("+p+")");
        }
      });
    }
  }

  public long lastPlayPause=0;
  public void initBluetooth(){
    if (mSession!=null) return;
    mSession = new MediaSession(this,"AnimeTV");
    mSession.setCallback(new MediaSession.Callback() {
      public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (ke==null) return false;
        Log.d("ATVLOG","Media Button: "+ke.getKeyCode());
        if ((ke.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && ke.getAction()==KeyEvent.ACTION_DOWN)
            || (ke.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK && ke.getAction()==KeyEvent.ACTION_DOWN)
        ){
          long now = SystemClock.elapsedRealtime();
          if (now-lastPlayPause>1000) {
            mediaExec("play", 0);
            lastPlayPause = now;
          }
          return true;
        }
        return false;
      }

      @Override
      public void onPlay() {
        super.onPlay();
        Log.d("ATVLOG","Bluetooth: onPlay");
        mediaExec("play",0);
      }

      @Override
      public void onPause() {
        super.onPause();
        Log.d("ATVLOG","Bluetooth: onPause");
        mediaExec("pause",0);
      }

      @Override
      public void onSkipToNext() {
        super.onSkipToNext();
        Log.d("ATVLOG","Bluetooth: onSkipToNext");
        mediaExec("next",0);
      }

      @Override
      public void onSkipToPrevious() {
        super.onSkipToPrevious();
        Log.d("ATVLOG","Bluetooth: onSkipToPrevious");
        mediaExec("prev",0);
      }

      @Override
      public void onStop() {
        super.onStop();
        Log.d("ATVLOG","Bluetooth: onStop");
        mediaExec("stop",0);
      }

      @Override
      public void onSeekTo(long pos) {
        super.onSeekTo(pos);
        Log.d("ATVLOG","Bluetooth: onSeekTo "+pos);
        mediaExec("seek",pos);
      }
    });
    mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    mSession.setActive(true);
  }

  public long _metaPosition=0;
  public int _metaState=0;
  public boolean _metaHaveNext=false;
  public boolean _metaHavePrev=false;
  public float _metaSpeed=1f;

  public void mediaSetPrevNext(boolean haveNext, boolean havePrev) {
    _metaHaveNext=haveNext;
    _metaHavePrev=havePrev;
    updateMediaState();
  }

  public void mediaSetState(int mediaState,long pos) {
    _metaState=mediaState;
    _metaPosition=pos;
    updateMediaState();
  }

  public void mediaSetPosition(long pos) {
    _metaPosition=pos;
    updateMediaState();
  }

  public void mediaSetSpeed(float s) {
    _metaSpeed=s;
    updateMediaState();
  }

  public void updateMediaState(){
    if (mSession==null) return;
    PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE |
                PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP |
                PlaybackState.ACTION_SEEK_TO
        )
        .setState(_metaState, _metaPosition, _metaSpeed, SystemClock.elapsedRealtime());
    if (!_metaHaveNext){
      stateBuilder.setActions(stateBuilder.build().getActions()&~PlaybackState.ACTION_SKIP_TO_NEXT);
    }
    if (!_metaHavePrev){
      stateBuilder.setActions(stateBuilder.build().getActions()&~PlaybackState.ACTION_SKIP_TO_PREVIOUS);
    }

    mSession.setPlaybackState(stateBuilder.build());
    updateMetadata();
  }

  public long _metaDuration=-1L;
  public String _metaTitle="";
  public String _metaArtist="";
  public String _metaUrl="";

  public void mediaSetDuration(long duration){
    _metaDuration=duration;
    updateMetadata();
  }

  public void mediaSetMeta(String title, String artist, String url) {
    _metaTitle=title;
    _metaArtist=artist;
    _metaUrl=url;
    updateMetadata();
  }

  public void updateMetadata(){
    if (mSession==null) return;
    MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, _metaTitle)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, _metaArtist)
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, _metaTitle)
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, _metaArtist);
    if (_metaDuration>0){
      metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, _metaDuration);
    }
    mSession.setMetadata(metadataBuilder.build());
  }
} 