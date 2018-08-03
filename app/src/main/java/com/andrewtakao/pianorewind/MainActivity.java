package com.andrewtakao.pianorewind;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.andrewtakao.pianorewind.utilities.SpeechRecognitionUtils;
import com.andrewtakao.pianorewind.utilities.TimeUtils;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements RecognitionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private Context mContext;
    private String LOG_TAG = "VoiceRecognitionActivity";

    //Multiplier for rewind and fast forward
    private static final int mSkipTime = 1000;
    private static final float mSpeedChange = 0.2f;
    private static final float mOriginalSpeed = 1.0f;
    private static final int mBarUpdateInterval = 1000;
    private static float mSpeed;

    //To play songs as alarms
//    AssetFileDescriptor afd;
//    AudioManager.OnAudioFocusChangeListener afChangeListener;

    //Audio
    private AudioManager am;
    private MediaPlayer mMediaPlayer;

    //To get duration of song
    MediaMetadataRetriever mMetaRetriever;
    int mDuration;

    //Instantiate views
    private Button mLoadButton;
    private TextView mReturnedTextView;
    private TextView mSpeedTextView;
    private TextView mTimeTextView;
    private TextView mDurationTextView;
    private SeekBar mSeekBar;
    private ImageButton mStartStopButton;
    private ImageButton mStopButton;
    private ImageButton mSlowButton;
    private ImageButton mFastButton;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ProgressBar mAmplitudeBar;

    //Instantiate Speech
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;

    //Time between voice recognition
    private static final int mDelay = 500;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG,"onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();
        mLoadButton = (Button) findViewById(R.id.b_load);
        mStartStopButton = (ImageButton) findViewById(R.id.ib_start);
        mForwardButton = (ImageButton) findViewById(R.id.ib_forward);
        mBackButton = (ImageButton) findViewById(R.id.ib_rewind);
        mSlowButton = (ImageButton) findViewById(R.id.ib_slow);
        mFastButton = (ImageButton) findViewById(R.id.ib_fast);
        mReturnedTextView = (TextView) findViewById(R.id.tv_log);
        mSpeedTextView = (TextView) findViewById(R.id.tv_speed_value);
        mTimeTextView = (TextView) findViewById(R.id.tv_time);
        mDurationTextView = (TextView) findViewById(R.id.tv_duration);
        mAmplitudeBar = (ProgressBar) findViewById(R.id.pb_amplitude);
        mSeekBar = (SeekBar) findViewById(R.id.sb_song);

        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mLoadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.d(LOG_TAG, "Load clicked");


                try {
                    setMediaPlayerFromRaw(R.raw.arabesque);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //TODO Uncomment this
//                Intent intent_upload = new Intent();
//                intent_upload.setType("audio/*");
//                intent_upload.setAction(Intent.ACTION_GET_CONTENT);
//                startActivityForResult(intent_upload,2);

            }
        });
        //Default arabesque


        //Create and set up speech recognizer intent
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        Log.d(LOG_TAG,"onStart");
        am.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
//        if (am.isWiredHeadsetOn()) {
//            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
//        }
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);

        mSpeed = mOriginalSpeed;
        mSpeedTextView.setText(String.valueOf(mSpeed));

        startMicrophone();
        super.onStart();

        setButtonsClickable();
        setPlayPauseButton();
    }

    public void setButtonsClickable() {
        boolean clickable = true;
        if(mMediaPlayer==null) {
            clickable = false;
        }
        mStartStopButton.setClickable(clickable);
        mFastButton.setClickable(clickable);
        mForwardButton.setClickable(clickable);
        mSlowButton.setClickable(clickable);
        mBackButton.setClickable(clickable);
    }

    public void setPlayPauseButton() {
        if (mMediaPlayer==null) {
            return;
        }
        if (mMediaPlayer.isPlaying()) {
            mStartStopButton.setImageResource(R.drawable.pause);
            mStartStopButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    pauseMusic(view);
                    setPlayPauseButton();
                }
            });
        } else {
            mStartStopButton.setImageResource(R.drawable.play);
            mStartStopButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startMusic(view);
                    setPlayPauseButton();
                }
            });
        }
    }

    public void startMicrophone() {
        Log.d(LOG_TAG, "startMicrophone");
        mAmplitudeBar.setVisibility(View.VISIBLE);
        mAmplitudeBar.setIndeterminate(true);
        if (speech!= null) {
            speech.startListening(recognizerIntent);
        }
    }

    public void stopMicrophone() {
        Log.d(LOG_TAG, "stopMicrophone");
        mAmplitudeBar.setIndeterminate(false);
        mAmplitudeBar.setVisibility(View.INVISIBLE);
        speech.stopListening();
    }

    public void setMediaPlayer(Object object) throws IOException {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(am.STREAM_SYSTEM);
        mMetaRetriever = new MediaMetadataRetriever();

        if (object instanceof Uri) {
            Log.d(LOG_TAG, "Uri");
            mMetaRetriever.setDataSource(this, (Uri) object);
            mMediaPlayer.setDataSource(this, (Uri) object);
            mMetaRetriever.setDataSource(this, (Uri) object);
        } else if (object instanceof Integer) {
            Log.d(LOG_TAG, "Arabesque");
            AssetFileDescriptor afd = this.getResources().openRawResourceFd((int) object);
            if (afd == null) return;
            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mMetaRetriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

        } else {
            Log.d(LOG_TAG, "What's the instanceof");
        }

        mMediaPlayer.prepare();

        //Blank beginning
        mMediaPlayer.seekTo(10000);

        mDuration = Integer.parseInt(mMetaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
//            mStartStopButton.setVisibility(View.VISIBLE);
//            mStopButton.setVisibility(View.VISIBLE);
        mSeekBar.setVisibility(View.VISIBLE);
        mSeekBar.setMax(mDuration);
        mDurationTextView.setText(TimeUtils.convertToMinutesAndSeconds(mDuration));

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mTimeTextView.setText(TimeUtils.convertToMinutesAndSeconds(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pauseMusic(null);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mMediaPlayer.seekTo(seekBar.getProgress());
                startMusic(null);
            }
        });

        updateBar();
        startMusic(null);
    }


    public void setMediaPlayerFromRaw(int id) throws IOException {
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(am.STREAM_SYSTEM);
        mMetaRetriever = new MediaMetadataRetriever();

        Log.d(LOG_TAG, "Arabesque");
        AssetFileDescriptor afd = this.getResources().openRawResourceFd(id);
        if (afd == null) return;
        mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        mMetaRetriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();

        mMediaPlayer.prepare();

        //Blank beginning
        mMediaPlayer.seekTo(10000);

        mDuration = Integer.parseInt(mMetaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
//            mStartStopButton.setVisibility(View.VISIBLE);
//            mStopButton.setVisibility(View.VISIBLE);
        mSeekBar.setVisibility(View.VISIBLE);
        mSeekBar.setMax(mDuration);
        mDurationTextView.setText(TimeUtils.convertToMinutesAndSeconds(mDuration));

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mTimeTextView.setText(TimeUtils.convertToMinutesAndSeconds(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pauseMusic(null);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mMediaPlayer.seekTo(seekBar.getProgress());
                startMusic(null);
            }
        });

        updateBar();
        startMusic(null);
    }


    public void setMediaPlayer(int id) {
        ;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "onActivityResult");
        if(requestCode == 2 && resultCode == RESULT_OK){
            Uri uri = data.getData();
            try {
                setMediaPlayer(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Mute other stream to mute google beep, put media player on unmuted stream (system)

//            mMediaPlayer = new MediaPlayer();
//            mMediaPlayer.setAudioStreamType(am.STREAM_SYSTEM);
//            mMetaRetriever = new MediaMetadataRetriever();


            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void startMusic(View v) {
        if (mMediaPlayer!=null&&!mMediaPlayer.isPlaying()){
            mMediaPlayer.start();
        }
    }

    public void pauseMusic(View v) {
        if (mMediaPlayer!=null&&mMediaPlayer.isPlaying()){
            mMediaPlayer.pause();
        }
    }

    public void rewindMusic(View v) {
        rewindSteps(1);
    }

    public void forwardMusic(View v) {
        forwardSteps(1);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void slowMusic(View v) {
        slowMedia();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void fastMusic(View v) {
        fastMedia();
    }



    public void rewindSteps(int steps) {
        if (mMediaPlayer!=null){
            int position = mMediaPlayer.getCurrentPosition();
            mMediaPlayer.seekTo(position-mSkipTime*steps);
        }
    }

    public void forwardSteps(int steps) {
        if (mMediaPlayer!=null){
            int position = mMediaPlayer.getCurrentPosition();
            mMediaPlayer.seekTo(position+mSkipTime*steps);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void slowMedia() {
        if (mMediaPlayer!=null) {
            if (mSpeed>.25f) {
                mSpeed -= mSpeedChange;
                mSpeedTextView.setText(String.valueOf(mSpeed));
                mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(mSpeed));
            } else {
                Toast.makeText(this, "can't go lower than .25", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void fastMedia() {
        if (mMediaPlayer!=null) {
            if (mSpeed<1) {
                mSpeed += mSpeedChange;
                mSpeedTextView.setText(String.valueOf(mSpeed));
                mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(mSpeed));
                if (mSpeed < .25f) {
                    normalSpeed();
                }
            } else {
                normalSpeed();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void normalSpeed() {
        if (mMediaPlayer!=null) {
            mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(mOriginalSpeed));
            mSpeedTextView.setText(String.valueOf(mSpeed));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speech != null) {
            speech.destroy();
            mAmplitudeBar.setIndeterminate(false);
        }
        speech = null;
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
        mAmplitudeBar.setIndeterminate(false);
        mAmplitudeBar.setMax(10);
    }

    @Override
    public void onRmsChanged(float v) {
//        Log.i(LOG_TAG, "onRmsChanged: " + v);
        mAmplitudeBar.setProgress((int) v);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
        mAmplitudeBar.setIndeterminate(true);
        stopMicrophone();
        Log.d(LOG_TAG, "onEndOfSpeech startMicrophone in x seconds");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startMicrophone();
            }

        }, mDelay);
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = SpeechRecognitionUtils.getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
        mReturnedTextView.setText(errorMessage);

        //Only stop microphone if it isn't stopped
        if (
                errorCode != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        errorCode != SpeechRecognizer.ERROR_NO_MATCH) {
            stopMicrophone();
        }
        //Only start microphone if onEndOfSpeech didn't get hit
        if (errorCode != SpeechRecognizer.ERROR_NO_MATCH) {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startMicrophone();
                }
            }, mDelay);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onResults(Bundle bundle) {

        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = bundle
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";

        for (String result : matches)
            text += result + "\n";

        translateCommand(matches);

        mReturnedTextView.setText(text);
    }

    public void updateBar() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try { while (!isInterrupted()) {
                    Thread.sleep(mBarUpdateInterval);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { mSeekBar.setProgress(mMediaPlayer.getCurrentPosition());}
                    }); }
                } catch (InterruptedException e) { } }
        };
        t.start();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void translateCommand(ArrayList<String> result) {
        if (result.contains("B-1") || result.contains("B1") || result.contains("back 1") || result.contains("back one")) {
            Toast.makeText(this, "Back 1", Toast.LENGTH_SHORT).show();
            rewindSteps(1);
        } else if (result.contains("B-2") || result.contains("B2") || result.contains("Back 2") || result.contains("back to")) {
            Toast.makeText(this, "Back 2", Toast.LENGTH_SHORT).show();
            rewindSteps(2);
        } else if (result.contains("B-3") || result.contains("B3") || result.contains("back 3") || result.contains("back three")) {
            Toast.makeText(this, "Back 3", Toast.LENGTH_SHORT).show();
            rewindSteps(3);
        } else if (result.contains("B-4") || result.contains("B4") || result.contains("back 4") || result.contains("back four")) {
            rewindSteps(4);
        } else if (result.contains("B-5") || result.contains("B5") || result.contains("back 5") || result.contains("back five")) {
            rewindSteps(5);
        } else if (result.contains("B-6") || result.contains("B6") || result.contains("back 6") || result.contains("back six")) {
            rewindSteps(6);
        } else if (result.contains("B-7") || result.contains("B7") || result.contains("back 7") || result.contains("back seven")) {
            rewindSteps(7);
        } else if (result.contains("B-8") || result.contains("B8") || result.contains("back 8") || result.contains("back eight")) {
            rewindSteps(8);
        } else if (result.contains("B-9") || result.contains("B9") || result.contains("back 9") || result.contains("back nine")) {
            rewindSteps(9);
        } else if (result.contains("F-1") || result.contains("F1") || result.contains("forward 1") || result.contains("forward one")) {
            Toast.makeText(this, "Forward 1", Toast.LENGTH_SHORT).show();
            forwardSteps(1);
        } else if (result.contains("F-2") || result.contains("F2") || result.contains("forward 2") || result.contains("forward to")) {
            forwardSteps(2);
        } else if (result.contains("F-3") || result.contains("F3") || result.contains("forward 3") || result.contains("forward three")) {
            forwardSteps(3);
        } else if (result.contains("F-4") || result.contains("F4") || result.contains("forward 4") || result.contains("forward four")) {
            forwardSteps(4);
        } else if (result.contains("F-5") || result.contains("F5") || result.contains("forward 5") || result.contains("forward five")) {
            forwardSteps(5);
        } else if (result.contains("F-6") || result.contains("F6") || result.contains("forward 6") || result.contains("forward six")) {
            forwardSteps(6);
        } else if (result.contains("F-7") || result.contains("F7") || result.contains("forward 7") || result.contains("forward seven")) {
            forwardSteps(7);
        } else if (result.contains("F-8") || result.contains("F8") || result.contains("forward 8") || result.contains("forward eight")) {
            forwardSteps(8);
        } else if (result.contains("F-9") || result.contains("F9") || result.contains("forward 9") || result.contains("forward nine")) {
            forwardSteps(9);
        } else if (result.contains("stop") || result.contains("pause")) {
            pauseMusic(null);
        } else if (result.contains("start") || result.contains("go")) {
            startMusic(null);
        } else if (result.contains("slower") || result.contains("slow down")) {
            slowMedia();
        } else if (result.contains("faster") || result.contains("speed up")) {
            fastMedia();
        } else if (result.contains("normal speed")) {
            normalSpeed();
        }
        else {
            Toast.makeText(getApplicationContext(), "sound detected" + result, Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }
}

