package com.zoffcc.applications.trifa;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static com.zoffcc.applications.trifa.MainActivity.set_JNI_video_buffer2;
import static com.zoffcc.applications.trifa.MainActivity.toxav_video_send_frame;
import static com.zoffcc.applications.trifa.MainActivity.video_buffer_2;

public class CameraWrapper
{
    private static final String TAG = "CameraWrapper";
    private Camera mCamera;
    private Camera.Parameters mCameraParamters;
    private static CameraWrapper mCameraWrapper;
    private boolean mIsPreviewing = false;
    private float mPreviewRate = -1.0f;
    public static final int IMAGE_HEIGHT = 720;
    public static final int IMAGE_WIDTH = 1280;
    private CameraPreviewCallback mCameraPreviewCallback;
    // private byte[] mImageCallbackBuffer = new byte[(CameraWrapper.IMAGE_WIDTH * CameraWrapper.IMAGE_HEIGHT * 3 / 2) ];
    private byte[] mImageCallbackBuffer = new byte[(CameraWrapper.IMAGE_WIDTH * CameraWrapper.IMAGE_HEIGHT )
            + ((CameraWrapper.IMAGE_WIDTH/2) * (CameraWrapper.IMAGE_HEIGHT/2))
            + ((CameraWrapper.IMAGE_WIDTH/2) * (CameraWrapper.IMAGE_HEIGHT/2))
            ];
    static Camera.Size camera_preview_size2 = null;

    public interface CamOpenOverCallback
    {
        public void cameraHasOpened();
    }

    private CameraWrapper()
    {
    }

    public static synchronized CameraWrapper getInstance()
    {
        if (mCameraWrapper == null)
        {
            mCameraWrapper = new CameraWrapper();
        }
        return mCameraWrapper;
    }

    public void doOpenCamera(CamOpenOverCallback callback, boolean front_camera)
    {
        Log.i(TAG, "Camera open....");
        int numCameras = Camera.getNumberOfCameras();

        int camera_type = Camera.CameraInfo.CAMERA_FACING_FRONT;
        if (front_camera == false)
        {
            camera_type = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numCameras; i++)
        {
            Camera.getCameraInfo(i, info);
            if (info.facing == camera_type)
            {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null)
        {
            Log.d(TAG, "this camera (" + front_camera + ") type found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null)
        {
            throw new RuntimeException("Unable to open camera");
        }
        Log.i(TAG, "Camera open over....");
        callback.cameraHasOpened();
    }

    public void doStartPreview(SurfaceHolder holder, float previewRate)
    {
        Log.i(TAG, "doStartPreview...");
        if (mIsPreviewing)
        {
            this.mCamera.stopPreview();
            return;
        }

        try
        {
            this.mCamera.setPreviewDisplay(holder);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        initCamera();
    }

    public void doStartPreview(SurfaceTexture surface, float previewRate)
    {
        Log.i(TAG, "doStartPreview()");
        if (mIsPreviewing)
        {
            this.mCamera.stopPreview();
            return;
        }

        try
        {
            this.mCamera.setPreviewTexture(surface);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        initCamera();
    }

    public void doStopCamera()
    {
        Log.i(TAG, "doStopCamera");
        if (this.mCamera != null)
        {
            mCameraPreviewCallback.close();
            this.mCamera.setPreviewCallback(null);
            this.mCamera.stopPreview();
            this.mIsPreviewing = false;
            this.mPreviewRate = -1f;
            this.mCamera.release();
            this.mCamera = null;
        }
    }

    private void initCamera()
    {
        if (this.mCamera != null)
        {
            this.mCameraParamters = this.mCamera.getParameters();
            // this.mCameraParamters.setPreviewFormat(ImageFormat.NV21);
            this.mCameraParamters.setPreviewFormat(ImageFormat.YV12);
            this.mCameraParamters.setFlashMode("off");
            this.mCameraParamters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            this.mCameraParamters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            this.mCameraParamters.setPreviewSize(IMAGE_WIDTH, IMAGE_HEIGHT);
            this.mCamera.setDisplayOrientation(90);
            mCameraPreviewCallback = new CameraPreviewCallback();
            mCamera.addCallbackBuffer(mImageCallbackBuffer);
            mCamera.setPreviewCallbackWithBuffer(mCameraPreviewCallback);
            List<String> focusModes = this.mCameraParamters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video"))
            {
                this.mCameraParamters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            this.mCamera.setParameters(this.mCameraParamters);
            this.mCamera.startPreview();

            this.mIsPreviewing = true;
        }
    }

    class CameraPreviewCallback implements Camera.PreviewCallback
    {
        private static final String TAG = "CameraPreviewCallback";
        private VideoEncoderFromBuffer videoEncoder = null;

        private CameraPreviewCallback()
        {
            videoEncoder = new VideoEncoderFromBuffer(CameraWrapper.IMAGE_WIDTH, CameraWrapper.IMAGE_HEIGHT);
        }

        void close()
        {
            try
            {
                videoEncoder.close();
            }
            catch (Exception e) // java.lang.IllegalStateException
            {
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera)
        {
            // Log.i(TAG, "onPreviewFrame");
            // videoEncoder.encodeFrame(data/*, encodeData*/);
            // camera.addCallbackBuffer(data);


            // ----------------------------
            if (data == null)
            {
            }
            else
            {
                if (camera_preview_size2 == null)
                {
                    Camera.Parameters p = camera.getParameters();
                    camera_preview_size2 = p.getPreviewSize();
                    Log.i(TAG, "onPreviewFrame:w=" + camera_preview_size2.width + " h=" + camera_preview_size2.height);

                    if (video_buffer_2 != null)
                    {
                        // video_buffer_2.clear();
                        video_buffer_2 = null;
                    }

                /*
                * YUV420 frame with width * height
                *
                * @param y Luminosity plane. Size = MAX(width, abs(ystride)) * height.
                * @param u U chroma plane. Size = MAX(width/2, abs(ustride)) * (height/2).
                * @param v V chroma plane. Size = MAX(width/2, abs(vstride)) * (height/2).
                */
                    int y_layer_size = (int) camera_preview_size2.width * camera_preview_size2.height;
                    int u_layer_size = (int) (camera_preview_size2.width / 2) * (camera_preview_size2.height / 2);
                    int v_layer_size = (int) (camera_preview_size2.width / 2) * (camera_preview_size2.height / 2);

                    int frame_width_px = (int) camera_preview_size2.width;
                    int frame_height_px = (int) camera_preview_size2.height;

                    int buffer_size_in_bytes2 = y_layer_size + v_layer_size + u_layer_size;

                    Log.i(TAG, "YUV420 frame w1=" + camera_preview_size2.width + " h1=" + camera_preview_size2.height + " bytes=" + buffer_size_in_bytes2);
                    Log.i(TAG, "YUV420 frame w=" + frame_width_px + " h=" + frame_height_px + " bytes=" + buffer_size_in_bytes2);
                    video_buffer_2 = ByteBuffer.allocateDirect(buffer_size_in_bytes2 + 1);
                    set_JNI_video_buffer2(video_buffer_2, camera_preview_size2.width, camera_preview_size2.height);
                }

                try
                {
                    video_buffer_2.rewind();
                    Log.i(TAG, "YUV420 data bytes=" + data.length);

                    video_buffer_2.put(data);
                    toxav_video_send_frame(Callstate.friend_number, camera_preview_size2.width, camera_preview_size2.height);
                }
                catch (java.nio.BufferOverflowException e)
                {
                    e.printStackTrace();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                camera.addCallbackBuffer(data);
            }
        }
    }
}