/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.hardware.hdmi;

import android.os.Handler;
import android.os.RemoteException;

/**
 * HdmiAudioSystemClient represents HDMI-CEC logical device of type Audio System in the Android
 * system which acts as an audio system device such as sound bar.
 *
 * <p>HdmiAudioSystemClient provides methods that control, get information from TV/Display device
 * connected through HDMI bus.
 *
 * @hide
 */
public final class HdmiAudioSystemClient extends HdmiClient {
    // TODO(b/110430593): add tests for this class
    private static final String TAG = "HdmiAudioSystemClient";

    private static final int REPORT_AUDIO_STATUS_INTERVAL_MS = 500;

    private final Handler mHandler = new Handler();
    private boolean mCanSendAudioStatus = true;
    private boolean mPendingReportAudioStatus;

    private int mLastVolume;
    private int mLastMaxVolume;
    private boolean mLastIsMute;


    /* package */ HdmiAudioSystemClient(IHdmiControlService service) {
        super(service);
    }

    /** @hide */
    // TODO(b/110094868): unhide and add @SystemApi for Q
    @Override
    public int getDeviceType() {
        return HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM;
    }

    /**
     * Sends a Report Audio Status HDMI CEC command to TV devices when necessary.
     *
     * According to HDMI CEC specification, an audio system can report its audio status when System
     * Audio Mode is on, so that the TV can display the audio status of external amplifier.
     *
     * @hide
     */
    public void sendReportAudioStatusCecCommand(boolean isMuteAdjust, int volume, int maxVolume,
            boolean isMute) {
        if (isMuteAdjust) {
            // always report audio status when it's muted/unmuted
            try {
                mService.reportAudioStatus(getDeviceType(), volume, maxVolume, isMute);
            } catch (RemoteException e) {
                // do nothing. Reporting audio status is optional.
            }
            return;
        }

        mLastVolume = volume;
        mLastMaxVolume = maxVolume;
        mLastIsMute = isMute;
        if (mCanSendAudioStatus) {
            try {
                mService.reportAudioStatus(getDeviceType(), volume, maxVolume, isMute);
                mCanSendAudioStatus = false;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mPendingReportAudioStatus) {
                            // report audio status if there is any pending message
                            try {
                                mService.reportAudioStatus(getDeviceType(), mLastVolume,
                                        mLastMaxVolume, mLastIsMute);
                                mHandler.postDelayed(this, REPORT_AUDIO_STATUS_INTERVAL_MS);
                            }  catch (RemoteException e) {
                                mCanSendAudioStatus = true;
                            } finally {
                                mPendingReportAudioStatus = false;
                            }
                        } else {
                            mCanSendAudioStatus = true;
                        }
                    }
                }, REPORT_AUDIO_STATUS_INTERVAL_MS);
            } catch (RemoteException e) {
                // do nothing. Reporting audio status is optional.
            }
        } else {
            // if audio status cannot be sent, send it latter
            mPendingReportAudioStatus = true;
        }
    }
}
