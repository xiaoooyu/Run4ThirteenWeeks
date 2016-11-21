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

    private static final long SECONDS_PER_MIN = TimeUnit.MINUTES.toSeconds(1);
    private static final long MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1);
    private static final long MILLISECONDS_PER_SECOND = TimeUnit.SECONDS.toMillis(1);

    private static int STATUS_STOP = 0;
    private static int STATUS_START = 1;
    private static int STATUS_PAUSE = 3;

    private static final String KEY_SPRINT_FORMULA = "sprint_formula";
    private static final String DEFL_SPRINT_FORMULA = "10-1-15-1-20-1-10";

    // Milliseconds between waking processor/screen for updates
    private static final long AMBIENT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(2);

    private TextView mTextView;
    private Button mButton;

    private int mStatus = STATUS_STOP;

    private long[] vibratePattern = new long[]{0, 300, 200, 300};

    private AlarmManager mAmbientStateAlarmManager;
    private PendingIntent mAmbientStatePendingIntent;

    private GoogleApiClient mGoogleApiClient;

    private String mSprintFormula;
    private int[] mSprintArray;
    private int mSprintIdx = 0;
    // one phrase due time, which indicates the alarm time
    private long mSprintUtil = 0L;

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

    private void flushDuration() {
        mSprintFormula = getSprintFormula();

        String[] sprints = mSprintFormula.trim().split("-");
        mSprintArray = new int[sprints.length];
        for (int i = 0; i < sprints.length; i++) {
            try {
                mSprintArray[i] = Integer.parseInt(sprints[i]);
            } catch (NumberFormatException ex) {
                mSprintArray[i] = 0;
            }
        }

        if (mStatus == STATUS_STOP) {
            initStartView();
        }
    }

    private String getSprintFormula() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        String formula = DEFL_SPRINT_FORMULA;
        if (preferences.contains(KEY_SPRINT_FORMULA)) {
            formula = preferences.getString(KEY_SPRINT_FORMULA, DEFL_SPRINT_FORMULA);
        }
        return formula;
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
            if (mStatus == STATUS_STOP) {
                start();
                mButton.setText("Stop");
            } else {
                stop();
                mButton.setText("Start");
            }

        }
    }

    private void stop() {
        initStartView();
        mStatus = STATUS_STOP;
    }

    private void initStartView() {
        mSprintIdx = 0;
        if (mTextView != null) {
            setRemainingTime(getSprintDuration());
        }
    }

    private void start() {
        long now = System.currentTimeMillis();

        if (mStatus == STATUS_STOP) {
            mStatus = STATUS_START;
            mSprintUtil = now + getSprintDuration();
        } else if (mStatus == STATUS_START) {
            if (mSprintIdx < mSprintArray.length) {
                mSprintIdx ++;
                mSprintUtil = now + getSprintDuration();
            } else {
                notifyAllFinish();
                return;
            }
        }
        refreshDisplayAndSetNextUpdate();
    }

    private void notifyAllFinish() {
        mTextView.setText(R.string.done);
    }

    private long getSprintDuration() {
        long duration = 0L;
        if (mSprintIdx >= 0 && mSprintIdx < mSprintArray.length) {
            duration = TimeUnit.MINUTES.toMillis(mSprintArray[mSprintIdx]);
        }
        return duration;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        // Described in the following section
        refreshDisplayAndSetNextUpdate();
    }

    private void refreshDisplayAndSetNextUpdate() {
        if (mStatus == STATUS_STOP) {
            initStartView();
            return;
        }

        long timeMs = System.currentTimeMillis();

        if (mSprintUtil <= timeMs) {
            notifyFinish();
            return;
        } else {
            setRemainingTime(mSprintUtil - timeMs);
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

        if (!isFinishing()) {
            start();
        }
    }

    private void setRemainingTime(long millisUntilFinished) {
        if (mTextView != null && millisUntilFinished > 0) {
            long totalSeconds = millisUntilFinished / MILLISECONDS_PER_SECOND;
            int seconds = (int) (totalSeconds % SECONDS_PER_MIN);
            int minutes = (int) (totalSeconds / SECONDS_PER_MIN % MINUTES_PER_HOUR);
//            int minutes = totalSeconds
//            int minutes = (int) (totalSeconds - seconds) % (SECONDS_PER_MIN * MINUTES_PER_HOUR);
            long hours = totalSeconds / (SECONDS_PER_MIN * MINUTES_PER_HOUR);

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
                    saveFormula(preferences, dataMap.getString(KEY_SPRINT_FORMULA, DEFL_SPRINT_FORMULA));
                    flushDuration();
                }
            }
        }
    }

    private void saveFormula(SharedPreferences preferences, String strng) {
        SharedPreferences.Editor editor = preferences.edit();
        SharedPreferencesCompat.EditorCompat.getInstance().apply(editor.putString(KEY_SPRINT_FORMULA, strng));
    }
}