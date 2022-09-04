package com.devyk.webrtc.library;

/**
 * Java version of rtc::VideoSinkInterface.
 */
public interface VideoSink {
  /**
   * Implementations should call frame.retain() if they need to hold a reference to the frame after
   * this function returns. Each call to retain() should be followed by a call to frame.release()
   * when the reference is no longer needed.
   */
  @CalledByNative void onFrame(VideoFrame frame);
}