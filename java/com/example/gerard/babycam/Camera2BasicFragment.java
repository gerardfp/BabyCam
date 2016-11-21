package com.example.gerard.babycam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static boolean compressing = false;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            if(!compressing){
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        compressing = true;
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        mTextureView.getBitmap().compress(Bitmap.CompressFormat.JPEG, MainActivity.jpegQuality, byteArrayOutputStream);
                        MainActivity.currentByteArray = byteArrayOutputStream.toByteArray();
                        compressing = false;
                    }
                });
                t.start();
            }
        }
    };

    private String mCameraId;
    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private boolean mFlashSupported;
    private int mSensorOrientation;


    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mTextureView.setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        mBackgroundThread = new HandlerThread("CameraBackgrounddd");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            //Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // don't use front facing camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                if(((displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180)
                                && (mSensorOrientation == 90 || mSensorOrientation == 270))
                        ||  ((displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270)
                                && (mSensorOrientation == 0 || mSensorOrientation == 180))){
                    swappedDimensions = true;
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    //mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    //mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface),
                new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        return;
                    }

                    mCaptureSession = cameraCaptureSession;
                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        if (mFlashSupported) {
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        }

                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    @Override
    public void onClick(View view) {
        final Activity activity = getActivity();
        if (null != activity) {
            LayoutInflater inflater = getActivity().getLayoutInflater();

            AlertDialog myad = new AlertDialog.Builder(activity)
                    .setView(inflater.inflate(R.layout.settings, null))
                    .setMessage("http://" + MainActivity.SERVER_IP + ":" + MainActivity.SERVER_PORT)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();


            SeekBar qualityControl = (SeekBar) myad.findViewById(R.id.qualitySeekBar);

            qualityControl.setProgress(MainActivity.jpegQuality);

            qualityControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                int progressChanged = 0;

                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    progressChanged = progress;
                    SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putInt("videoquality", progress);
                    editor.commit();
                    MainActivity.jpegQuality = progress;
                }

                public void onStartTrackingTouch(SeekBar seekBar) {
                    // TODO Auto-generated method stub
                }

                public void onStopTrackingTouch(SeekBar seekBar) {
                /*Toast.makeText(SeekbarActivity.this,"seek bar progress:"+progressChanged,
                        Toast.LENGTH_SHORT).show();*/
                }
            });
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
