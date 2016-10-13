package com.xiaoyu.run4thirteenweeks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.SharedPreferencesCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity
    implements View.OnClickListener,
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener,
    DataApi.DataListener {

    private static final String TAG = "WearMainActivity";

    private static final int SECONDS_PER_MIN = 60;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int MILLISECONDS_PER_SECOND = 1000;

    private static int STOP_STATUS = 0;
    private static int SPRINT_STATUS = 1;
    private static int REST_STATUS = 2;
    private static int PAUSE_STATUS = 3;

    private static final String KEY_SPRINT_DURATION = "sprint_duration";
    private static final int DEF_SPRINT_DURATION_MIN = 2;

    private static final String KEY_REST_DURATION = "rest_duration";
    private static final int DEF_REST_DURATION_MIN = 2;

    // Milliseconds between waking processor/screen for updates
    private static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(2);

    private long mSprintDurationMs;
    private long mRestDurationMs;

    private TextView mTextView;
    private Button mButton;
    private TextView mLoopView;

    private long mDueTime = 0L;

    private int mStatus = STOP_STATUS;
    private int mLoop = 0;
    private long[] vibratePattern = new long[]{0, 300, 200, 300};
    private AlarmManager mAmbientStateAlarmManager;
    private PendingIntent mAmbientStatePendingIntent;

    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAmbientEnabled();

        prepareAlarmService();

        setContentView(R.layout.activity_main);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mButton = (Button) stub.findViewById(R.id.button);
                mLoopView = (TextView) stub.findViewById(R.id.loop);

                if (mButton != null) {
                    mButton.setOnClickListener(MainActivity.this);
                }

                flushDuration();
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();
    }

    private long calculateMilliseconds(int min) {
        return min * SECONDS_PER_MIN * MILLISECONDS_PER_SECOND;
    }

    @Override
    protected void onResume() {
        super.onResume();

        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    private int getSprintDurationMin() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int durationMin = DEF_SPRINT_DURATION_MIN;
        if (preferences.contains(KEY_SPRINT_DURATION)) {
            durationMin = preferences.getInt(KEY_SPRINT_DURATION, DEF_REST_DURATION_MIN);
        }
        return durationMin;
    }

    private int getRestDurationMin() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int restMin = DEF_REST_DURATION_MIN;
        if (preferences.contains(KEY_REST_DURATION)) {
            restMin = preferences.getInt(KEY_REST_DURATION, DEF_REST_DURATION_MIN);
        }
        return restMin;
    }

    private void prepareAlarmService() {
        mAmbientStateAlarmManager =
            (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent ambientStateIntent =
            new Intent(getApplicationContext(), MainActivity.class);

        mAmbientStatePendingIntent = PendingIntent.getActivity(
            getApplicationContext(),
            0,
            ambientStateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);

        if (mButton != null) {
            mButton.setVisibility(View.GONE);
        }

        if (mTextView != null) {
            mTextView.getPaint().setAntiAlias(false);
        }
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();

        if (mButton != null) {
            mButton.setVisibility(View.VISIBLE);
        }

        if (mTextView != null) {
            mTextView.getPaint().setAntiAlias(true);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button) {
            if (mStatus == STOP_STATUS) {
                start();
                mButton.setText("Stop");
            } else {
                stop();
                mButton.setText("Start");
            }

        }
    }

    private void stop() {
        setStartView();
        mStatus = STOP_STATUS;
    }

    private void setStartView() {
        if (mTextView != null) {
            setRemainingTime(mSprintDurationMs);
        }
    }

    private void start() {
        long timeMs = System.currentTimeMillis();

        if (mStatus == STOP_STATUS || mStatus == REST_STATUS) {
            mStatus = SPRINT_STATUS;
            mDueTime = timeMs + mSprintDurationMs;
        } else if (mStatus == SPRINT_STATUS) {
            mStatus = REST_STATUS;
            mDueTime = timeMs + mRestDurationMs;
        }

        refreshDisplayAndSetNextUpdate();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        // Described in the following section
        refreshDisplayAndSetNextUpdate();
    }

    private void refreshDisplayAndSetNextUpdate() {
        if (mStatus == STOP_STATUS) {
            setStartView();
            return;
        }

        long timeMs = System.currentTimeMillis();

        if (mDueTime <= timeMs) {
            notifyFinish();
        } else {
            setRemainingTime(mDueTime - timeMs);
        }

        long delayMs = AMBIENT_INTERVAL_MS - (timeMs % AMBIENT_INTERVAL_MS);
        long triggerTimeMs = timeMs + delayMs;

        mAmbientStateAlarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMs,
            mAmbientStatePendingIntent);
    }

    private void notifyFinish() {
        final Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(vibratePattern, -1);

        if (mStatus == REST_STATUS) {
            mLoop ++;
            setLoopText();
        }

        if (!isFinishing()) {
            start();
        }
    }

    private void setLoopText() {
        if (mLoopView != null) {
            mLoopView.setText(String.format("%d laps", mLoop));
        }
    }

    private void setRemainingTime(long millisUntilFinished) {
        if (mTextView != null && millisUntilFinished > 0) {
            long totalSeconds = millisUntilFinished / MILLISECONDS_PER_SECOND;
            int seconds = (int) (totalSeconds % SECONDS_PER_MIN);
            int minutes = (int) (totalSeconds / SECONDS_PER_MIN % MINUTES_PER_HOUR);
//            int minutes = totalSeconds
//            int minutes = (int) (totalSeconds - seconds) % (SECONDS_PER_MIN * MINUTES_PER_HOUR);
            int hours = (int) totalSeconds / (SECONDS_PER_MIN * MINUTES_PER_HOUR);

            Log.d(TAG, String.format("%d seconds = %d hour, %d mins, %d seconds",
                totalSeconds, hours, minutes, seconds));

            StringBuilder builder = new StringBuilder();
            if (hours != 0) {
                builder.append(String.format("%d:", hours));
            }
            builder.append(String.format("%02d:%02d", minutes, seconds));

            mTextView.setText(builder.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/duration") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    SharedPreferences preferences = getPreferences(MODE_PRIVATE);
                    saveSprintDuration(preferences, dataMap.getInt(KEY_SPRINT_DURATION, DEF_SPRINT_DURATION_MIN));
                    saveRestDuration(preferences, dataMap.getInt(KEY_REST_DURATION, DEF_REST_DURATION_MIN));

                    flushDuration();
                }
            }
        }
    }

    private void saveRestDuration(SharedPreferences preferences, int restMin) {
        SharedPreferences.Editor editor = preferences.edit();
        SharedPreferencesCompat.EditorCompat.getInstance().apply(editor.putInt(KEY_REST_DURATION, restMin));
    }

    private void saveSprintDuration(SharedPreferences preferences, int sprintMin) {
        SharedPreferences.Editor editor = preferences.edit();
        SharedPreferencesCompat.EditorCompat.getInstance().apply(editor.putInt(KEY_SPRINT_DURATION, sprintMin));
    }

    private void flushDuration() {
        mSprintDurationMs = calculateMilliseconds(getSprintDurationMin());
        mRestDurationMs = calculateMilliseconds(getRestDurationMin());

        if (mStatus == STOP_STATUS) {
            setStartView();
        }
    }
}
