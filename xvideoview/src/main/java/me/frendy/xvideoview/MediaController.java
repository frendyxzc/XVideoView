/*
* Copyright (C) 2015 Author <dictfb#gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package me.frendy.xvideoview;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

public class MediaController extends FrameLayout {


    private MediaPlayerControl mPlayer;

    private Activity mActivity;
    private Context mContext;

    private ProgressBar mProgress;

    private TextView mEndTime, mCurrentTime;

    private TextView mTitle;

    private boolean mShowing = true;

    private boolean mDragging;

    private boolean mScalable = false;
    private boolean mIsFullScreen = false;
//    private boolean mFullscreenEnabled = false;


    private static final int sDefaultTimeout = 3000;

    private static final int STATE_PLAYING = 1;
    private static final int STATE_PAUSE = 2;
    private static final int STATE_LOADING = 3;
    private static final int STATE_ERROR = 4;
    private static final int STATE_COMPLETE = 5;

    private int mState = STATE_LOADING;


    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SHOW_LOADING = 3;
    private static final int HIDE_LOADING = 4;
    private static final int SHOW_ERROR = 5;
    private static final int HIDE_ERROR = 6;
    private static final int SHOW_COMPLETE = 7;
    private static final int HIDE_COMPLETE = 8;
    StringBuilder mFormatBuilder;

    Formatter mFormatter;

    private ImageButton mTurnButton;// 开启暂停按钮

    private ImageButton mScaleButton;

    private View mBackButton;// 返回按钮

    private ViewGroup loadingLayout;

    private ViewGroup errorLayout;

    private View mTitleLayout;
    private View mControlLayout;

    private View mCenterPlayButton;

    // 屏幕宽高
    private float width;
    private float height;
    // 音频管理器
    private AudioManager mAudioManager;
    private TextView centerInfo;

    public MediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.MediaController);
        mScalable = a.getBoolean(R.styleable.MediaController_uvv_scalable, false);
        a.recycle();
        init(mContext);
    }

    public MediaController(Context context) {
        super(context);
        init(context);
    }

    public void attachActivity(Activity activity) {
        mActivity = activity;
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewRoot = inflater.inflate(R.layout.uvv_player_controller, this);
        viewRoot.setOnTouchListener(mTouchListener);
        initControllerView(viewRoot);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        width = DensityUtil.getWidthInPx(mContext);
        height = DensityUtil.getHeightInPx(mContext);
        threshold = DensityUtil.dip2px(mContext, 18);
    }


    private void initControllerView(View v) {
        mTitleLayout = v.findViewById(R.id.title_part);
        mControlLayout = v.findViewById(R.id.control_layout);
        loadingLayout = (ViewGroup) v.findViewById(R.id.loading_layout);
        errorLayout = (ViewGroup) v.findViewById(R.id.error_layout);
        mTurnButton = (ImageButton) v.findViewById(R.id.turn_button);
        mScaleButton = (ImageButton) v.findViewById(R.id.scale_button);
        mCenterPlayButton = v.findViewById(R.id.center_play_btn);
        mBackButton = v.findViewById(R.id.back_btn);
        centerInfo = (TextView) v.findViewById(R.id.centerInfo);

        if (mTurnButton != null) {
            mTurnButton.requestFocus();
            mTurnButton.setOnClickListener(mPauseListener);
        }

        if (mScalable) {
            if (mScaleButton != null) {
                mScaleButton.setVisibility(VISIBLE);
                mScaleButton.setOnClickListener(mScaleListener);
            }
        } else {
            if (mScaleButton != null) {
                mScaleButton.setVisibility(GONE);
            }
        }

        if (mCenterPlayButton != null) {//重新开始播放
            mCenterPlayButton.setOnClickListener(mCenterPlayListener);
        }

        if (mBackButton != null) {//返回按钮仅在全屏状态下可见
            mBackButton.setOnClickListener(mBackListener);
        }

        View bar = v.findViewById(R.id.seekbar);
        mProgress = (ProgressBar) bar;
        if (mProgress != null) {
            if (mProgress instanceof SeekBar) {
                SeekBar seeker = (SeekBar) mProgress;
                seeker.setOnSeekBarChangeListener(mSeekListener);
            }
            mProgress.setMax(1000);
        }

        mEndTime = (TextView) v.findViewById(R.id.duration);
        mCurrentTime = (TextView) v.findViewById(R.id.has_played);
        mTitle = (TextView) v.findViewById(R.id.title);
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }


    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 3 seconds of inactivity.
     */
    public void show() {
        show(sDefaultTimeout);
    }

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        try {
            if (mTurnButton != null && mPlayer != null && !mPlayer.canPause()) {
                mTurnButton.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
            // We were given an old version of the interface, that doesn't have
            // the canPause/canSeekXYZ methods. This is OK, it just means we
            // assume the media can be paused and seeked, and so we don't disable
            // the buttons.
        }
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 'timeout' milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use 0 to show
     *                the controller until hide() is called.
     */
    public void show(int timeout) {//只负责上下两条bar的显示,不负责中央loading,error,playBtn的显示.
        if (!mShowing) {
            setProgress();
            if (mTurnButton != null) {
                mTurnButton.requestFocus();
            }
            disableUnsupportedButtons();
            mShowing = true;
        }
        updatePausePlay();
        updateBackButton();

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        if (mTitleLayout.getVisibility() != VISIBLE) {
            mTitleLayout.setVisibility(VISIBLE);
        }
        if (mControlLayout.getVisibility() != VISIBLE) {
            mControlLayout.setVisibility(VISIBLE);
        }

        // cause the progress bar to be updated even if mShowing
        // was already true. This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    public boolean isShowing() {
        return mShowing;
    }


    public void hide() {//只负责上下两条bar的隐藏,不负责中央loading,error,playBtn的隐藏
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            mTitleLayout.setVisibility(GONE);
            mControlLayout.setVisibility(GONE);
            mShowing = false;
        }
    }


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case FADE_OUT: //1
                    hide();
                    break;
                case SHOW_PROGRESS: //2
                    pos = setProgress();
                    if (!mDragging && mShowing && mPlayer != null && mPlayer.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SHOW_LOADING: //3
                    show();
                    showCenterView(R.id.loading_layout);
                    break;
                case SHOW_COMPLETE: //7
                    showCenterView(R.id.center_play_btn);
                    break;
                case SHOW_ERROR: //5
                    show();
                    showCenterView(R.id.error_layout);
                    break;
                case HIDE_LOADING: //4
                case HIDE_ERROR: //6
                case HIDE_COMPLETE: //8
                    hide();
                    hideCenterView();
                    break;
            }
        }
    };

    private void showCenterView(int resId) {
        if (resId == R.id.loading_layout) {
            if (loadingLayout.getVisibility() != VISIBLE) {
                loadingLayout.setVisibility(VISIBLE);
            }
            if (mCenterPlayButton.getVisibility() == VISIBLE) {
                mCenterPlayButton.setVisibility(GONE);
            }
            if (errorLayout.getVisibility() == VISIBLE) {
                errorLayout.setVisibility(GONE);
            }
        } else if (resId == R.id.center_play_btn) {
            if (mCenterPlayButton.getVisibility() != VISIBLE) {
                mCenterPlayButton.setVisibility(VISIBLE);
            }
            if (loadingLayout.getVisibility() == VISIBLE) {
                loadingLayout.setVisibility(GONE);
            }
            if (errorLayout.getVisibility() == VISIBLE) {
                errorLayout.setVisibility(GONE);
            }

        } else if (resId == R.id.error_layout) {
            if (errorLayout.getVisibility() != VISIBLE) {
                errorLayout.setVisibility(VISIBLE);
            }
            if (mCenterPlayButton.getVisibility() == VISIBLE) {
                mCenterPlayButton.setVisibility(GONE);
            }
            if (loadingLayout.getVisibility() == VISIBLE) {
                loadingLayout.setVisibility(GONE);
            }

        }
    }


    private void hideCenterView() {
        if (mCenterPlayButton.getVisibility() == VISIBLE) {
            mCenterPlayButton.setVisibility(GONE);
        }
        if (errorLayout.getVisibility() == VISIBLE) {
            errorLayout.setVisibility(GONE);
        }
        if (loadingLayout.getVisibility() == VISIBLE) {
            loadingLayout.setVisibility(GONE);
        }
    }

    public void reset() {
        mCurrentTime.setText("00:00");
        mEndTime.setText("00:00");
        mProgress.setProgress(0);
        mTurnButton.setImageResource(R.drawable.uvv_player_player_btn);
        setVisibility(View.VISIBLE);
        hideLoading();
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private String stringForTime(long timeMs) {
        if (timeMs == (Long.MIN_VALUE + 1)) {
            timeMs = 0;
        }
        long totalSeconds = (timeMs + 500) / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        return hours > 0 ? mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
                : mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(stringForTime(duration));
        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime(position));

        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0); // show until hide is called
                handled = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!handled) {
                    handled = false;
                    show(sDefaultTimeout); // start timeout
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    boolean handled = false;
    private float mLastMotionX;
    private float mLastMotionY;
    private int startX;
    private int startY;
    private int threshold;
    private boolean isClick = true;
    //如果正在显示,则使之消失
    private OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mShowing) {
                    hide();
                    handled = true;
                    return true;
                }
            }
            //音量/亮度控制
            final float x = event.getX();
            final float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mLastMotionX = x;
                    mLastMotionY = y;
                    startX = (int) x;
                    startY = (int) y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = x - mLastMotionX;
                    float deltaY = y - mLastMotionY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    // 声音调节标识
                    boolean isAdjustAudio = false;
                    if (absDeltaX > threshold && absDeltaY > threshold) {
                        if (absDeltaX < absDeltaY) {
                            isAdjustAudio = true;
                        } else {
                            isAdjustAudio = false;
                        }
                    } else if (absDeltaX < threshold && absDeltaY > threshold) {
                        isAdjustAudio = true;
                    } else if (absDeltaX > threshold && absDeltaY < threshold) {
                        isAdjustAudio = false;
                    } else {
                        return true;
                    }
                    if (isAdjustAudio) {
                        if (x < width / 2) {
                            if (deltaY > 0 && mLastMotionY != 0) {
                                lightDown(absDeltaY);
                            } else if (deltaY < 0 && mLastMotionY != 0) {
                                lightUp(absDeltaY);
                            }
                        } else {
                            if (deltaY > 0 && mLastMotionY != 0) {
                                volumeDown(absDeltaY);
                            } else if (deltaY < 0 && mLastMotionY != 0) {
                                volumeUp(absDeltaY);
                            }
                        }
                    } else {
//                        if (deltaX > 0 && mLastMotionX != 0) {
//                            forward(absDeltaX);
//                        } else if (deltaX < 0 && mLastMotionX != 0) {
//                            backward(absDeltaX);
//                        }
                    }
                    mLastMotionX = x;
                    mLastMotionY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(x - startX) > threshold
                            || Math.abs(y - startY) > threshold) {
                        isClick = false;
                    }
                    mLastMotionX = 0;
                    mLastMotionY = 0;
                    startX = (int) 0;
                    startY = (int) 0;
//                    if (isClick) {
//                        showOrHide();
//                    }
                    hideCenterInfo();
                    isClick = true;
                    break;

                default:
                    break;
            }
            return false;
        }
    };

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mTurnButton != null) {
                    mTurnButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }

    private OnClickListener mPauseListener = new OnClickListener() {
        public void onClick(View v) {
            if (mPlayer != null) {
                doPauseResume();
                show(sDefaultTimeout);
            }
        }
    };

    private OnClickListener mScaleListener = new OnClickListener() {
        public void onClick(View v) {
            mIsFullScreen = !mIsFullScreen;
            updateScaleButton();
            updateBackButton();
            mPlayer.setFullscreen(mIsFullScreen);
        }
    };

    //仅全屏时才有返回按钮
    private OnClickListener mBackListener = new OnClickListener() {
        public void onClick(View v) {
            if (mIsFullScreen) {
                mIsFullScreen = false;
                updateScaleButton();
                updateBackButton();
                mPlayer.setFullscreen(false);
            } else if(mActivity != null) {
                mActivity.finish();
            }
        }
    };

    private OnClickListener mCenterPlayListener = new OnClickListener() {
        public void onClick(View v) {
            hideCenterView();
            mPlayer.start();
        }
    };

    private void updatePausePlay() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mTurnButton.setImageResource(R.drawable.uvv_stop_btn);
//            mCenterPlayButton.setVisibility(GONE);
        } else {
            mTurnButton.setImageResource(R.drawable.uvv_player_player_btn);
//            mCenterPlayButton.setVisibility(VISIBLE);
        }
    }

    void updateScaleButton() {
        if (mIsFullScreen) {
            mScaleButton.setImageResource(R.drawable.uvv_star_zoom_in);
        } else {
            mScaleButton.setImageResource(R.drawable.uvv_player_scale_btn);
        }
    }

    void toggleButtons(boolean isFullScreen) {
        mIsFullScreen = isFullScreen;
        updateScaleButton();
        updateBackButton();
    }

    void updateBackButton() {
        mBackButton.setVisibility(mIsFullScreen ? View.VISIBLE : View.VISIBLE);
    }

    boolean isFullScreen() {
        return mIsFullScreen;
    }

    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }


    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        int newPosition = 0;

        boolean change = false;

        public void onStartTrackingTouch(SeekBar bar) {
            if (mPlayer == null) {
                return;
            }
            show(3600000);

            mDragging = true;
            mHandler.removeMessages(SHOW_PROGRESS);
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (mPlayer == null || !fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            newPosition = (int) newposition;
            change = true;
        }

        public void onStopTrackingTouch(SeekBar bar) {
            if (mPlayer == null) {
                return;
            }
            if (change) {
                mPlayer.seekTo(newPosition);
                if (mCurrentTime != null) {
                    mCurrentTime.setText(stringForTime(newPosition));
                }
            }
            mDragging = false;
            setProgress();
            updatePausePlay();
            show(sDefaultTimeout);

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            mShowing = true;
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
//        super.setEnabled(enabled);
        if (mTurnButton != null) {
            mTurnButton.setEnabled(enabled);
        }
        if (mProgress != null) {
            mProgress.setEnabled(enabled);
        }
        if (mScalable) {
            mScaleButton.setEnabled(enabled);
        }
        mBackButton.setEnabled(true);// 全屏状态下右上角的返回键总是可用.
    }

    public void showLoading() {
        mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    public void hideLoading() {
        mHandler.sendEmptyMessage(HIDE_LOADING);
    }

    public void showError() {
        mHandler.sendEmptyMessage(SHOW_ERROR);
    }

    public void hideError() {
        mHandler.sendEmptyMessage(HIDE_ERROR);
    }

    public void showComplete() {
        mHandler.sendEmptyMessage(SHOW_COMPLETE);
    }

    public void hideComplete() {
        mHandler.sendEmptyMessage(HIDE_COMPLETE);
    }

    public void setTitle(String titile) {
        mTitle.setText(titile);
    }

//    public void setFullscreenEnabled(boolean enabled) {
//        mFullscreenEnabled = enabled;
//        mScaleButton.setVisibility(mIsFullScreen ? VISIBLE : GONE);
//    }


    public void setOnErrorView(int resId) {
        errorLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, errorLayout, true);
    }

    public void setOnErrorView(View onErrorView) {
        errorLayout.removeAllViews();
        errorLayout.addView(onErrorView);
    }

    public void setOnLoadingView(int resId) {
        loadingLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, loadingLayout, true);
    }

    public void setOnLoadingView(View onLoadingView) {
        loadingLayout.removeAllViews();
        loadingLayout.addView(onLoadingView);
    }

    public void setOnErrorViewClick(OnClickListener onClickListener) {
        errorLayout.setOnClickListener(onClickListener);
    }

    /**
     * 音量/亮度控制
     */
    private void volumeDown(float delatY) {
        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int down = (int) (delatY / height * max * 3);
        int volume = Math.max(current - down, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        int transformatVolume = volume * 100 / max;

        int drawableId;
        if (transformatVolume == 0) {
            drawableId = R.drawable.ic_volume_mute_white_36dp;
        } else {
            drawableId = R.drawable.ic_volume_down_white_36dp;
        }
        setVolumeOrBrightnessInfo(getContext().getString(R.string.volume_changing, transformatVolume), drawableId);
    }

    private void volumeUp(float delatY) {
        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int up = (int) ((delatY / height) * max * 3);
        int volume = Math.min(current + up, max);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        int transformatVolume = volume * 100 / max;

        int drawableId;
        if (transformatVolume == 0) {
            drawableId = R.drawable.ic_volume_mute_white_36dp;
        } else {
            drawableId = R.drawable.ic_volume_up_white_36dp;
        }
        setVolumeOrBrightnessInfo(getContext().getString(R.string.volume_changing, transformatVolume), drawableId);
    }

    private void lightDown(float delatY) {
        if(mActivity != null) {
            int down = (int) (delatY / height * 255);
            int transformatLight = LightnessController.getLightness(mActivity) - down;
            int brightness;
            if(transformatLight <= 0) {
                transformatLight = 1;
                brightness = 0;
            } else if(transformatLight >= 255) {
                transformatLight = 254;
                brightness = 100;
            } else {
                brightness = transformatLight * 100 / 255;
            }
            LightnessController.setLightness(mActivity, transformatLight);

            setVolumeOrBrightnessInfo(getContext().getString(R.string.brightness_changing, brightness), whichBrightnessImageToUse(brightness));
        }
    }

    private void lightUp(float delatY) {
        if(mActivity != null) {
            int up = (int) (delatY / height * 255);
            int transformatLight = LightnessController.getLightness(mActivity) + up;
            int brightness;
            if(transformatLight <= 0) {
                transformatLight = 1;
                brightness = 0;
            } else if(transformatLight >= 255) {
                transformatLight = 254;
                brightness = 100;
            } else {
                brightness = transformatLight * 100 / 255;
            }
            LightnessController.setLightness(mActivity, transformatLight);

            setVolumeOrBrightnessInfo(getContext().getString(R.string.brightness_changing, brightness), whichBrightnessImageToUse(brightness));
        }
    }

    private void setVolumeOrBrightnessInfo(String txt, @DrawableRes int drawableId) {
        centerInfo.setVisibility(VISIBLE);
        centerInfo.setText(txt);
        centerInfo.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
        centerInfo.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getContext(), drawableId), null, null);
    }

    private void hideCenterInfo() {
        if(centerInfo != null && centerInfo.getVisibility() == VISIBLE) {
            centerInfo.startAnimation(AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_out));
            centerInfo.setVisibility(GONE);
        }
    }

    @DrawableRes
    private int whichBrightnessImageToUse(int brightnessInt) {
        if (brightnessInt <= 15) {
            return R.drawable.ic_brightness_1_white_36dp;
        } else if (brightnessInt <= 30 && brightnessInt > 15) {
            return R.drawable.ic_brightness_2_white_36dp;
        } else if (brightnessInt <= 45 && brightnessInt > 30) {
            return R.drawable.ic_brightness_3_white_36dp;
        } else if (brightnessInt <= 60 && brightnessInt > 45) {
            return R.drawable.ic_brightness_4_white_36dp;
        } else if (brightnessInt <= 75 && brightnessInt > 60) {
            return R.drawable.ic_brightness_5_white_36dp;
        } else if (brightnessInt <= 90 && brightnessInt > 75) {
            return R.drawable.ic_brightness_6_white_36dp;
        } else {
            return R.drawable.ic_brightness_7_white_36dp;
        }
    }

    /**
     * 快进/快退控制
     */
    private void backward(float delataX) {
        int current = mPlayer.getCurrentPosition();
        int backwardTime = (int) (delataX / width * mPlayer.getDuration());
        int currentTime = current - backwardTime;
        mPlayer.seekTo(currentTime);
        mProgress.setProgress(currentTime * 100 / mPlayer.getDuration());
        mCurrentTime.setText(stringForTime(currentTime));

        setFastForwardOrRewind(currentTime, R.drawable.ic_fast_rewind_white_36dp);
    }

    private void forward(float delataX) {
        int current = mPlayer.getCurrentPosition();
        int forwardTime = (int) (delataX / width * mPlayer.getDuration());
        int currentTime = current + forwardTime;
        mPlayer.seekTo(currentTime);
        mProgress.setProgress(currentTime * 100 / mPlayer.getDuration());
        mCurrentTime.setText(stringForTime(currentTime));

        setFastForwardOrRewind(currentTime, R.drawable.ic_fast_forward_white_36dp);
    }

    private void setFastForwardOrRewind(long changingTime, @DrawableRes int drawableId) {
        centerInfo.setVisibility(VISIBLE);
        centerInfo.setText(generateFastForwardOrRewindTxt(changingTime));
        centerInfo.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getContext(), drawableId), null, null);
    }

    private CharSequence generateFastForwardOrRewindTxt(long changingTime) {
        long duration = mPlayer == null ? 0 : mPlayer.getDuration();
        String result = stringForTime(changingTime) + " / " + stringForTime(duration);

        int index = result.indexOf("/");
        SpannableString spannableString = new SpannableString(result);

        TypedValue typedValue = new TypedValue();
        TypedArray a = getContext().obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorAccent});
        int color = a.getColor(0, 0);
        a.recycle();
        spannableString.setSpan(new ForegroundColorSpan(color), 0, index, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        return spannableString;
    }

    public interface MediaPlayerControl {
        void start();

        void pause();

        int getDuration();

        int getCurrentPosition();

        void seekTo(int pos);

        boolean isPlaying();

        int getBufferPercentage();

        boolean canPause();

        boolean canSeekBackward();

        boolean canSeekForward();

        void closePlayer();//关闭播放视频,使播放器处于idle状态

        void setFullscreen(boolean fullscreen);

        /***
         *
         * @param fullscreen
         * @param screenOrientation valid only fullscreen=true.values should be one of
         *                          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
         *                          ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
         *                          ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
         *                          ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
         */
        void setFullscreen(boolean fullscreen, int screenOrientation);
    }
}
