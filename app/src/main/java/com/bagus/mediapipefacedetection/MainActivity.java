package com.bagus.mediapipefacedetection;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.glutil.EglManager;

public class MainActivity extends AppCompatActivity {
    private static final String BINARY_GRAPH_NAME = "face_detection_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    private SurfaceTexture surfaceTexture;
    private SurfaceView surfaceView;
    private EglManager eglManager;
    private FrameProcessor frameProcessor;
    private ExternalTextureConverter externalTextureConverter;
    private CameraXPreviewHelper cameraXPreviewHelper;

    static {
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = new SurfaceView(this);
        setupPreviewDisplayView();
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        frameProcessor = new FrameProcessor(this, eglManager.getNativeContext(), BINARY_GRAPH_NAME, INPUT_VIDEO_STREAM_NAME, OUTPUT_VIDEO_STREAM_NAME);
        frameProcessor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);
        PermissionHelper.checkAndRequestCameraPermissions(this);
    }

    private void setupPreviewDisplayView() {
        surfaceView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(surfaceView);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                frameProcessor.getVideoSurfaceOutput().setSurface(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                onPreviewDisplaySurfaceChanged(width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                frameProcessor.getVideoSurfaceOutput().setSurface(null);
            }
        });
    }

    protected void onPreviewDisplaySurfaceChanged(int width, int height) {
        Size viewSize = new Size(width, height);
        Size displaySize = cameraXPreviewHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraXPreviewHelper.isCameraRotated();
        externalTextureConverter.setSurfaceTextureAndAttachToGLContext(
                surfaceTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    @Override
    protected void onResume() {
        super.onResume();
        externalTextureConverter = new ExternalTextureConverter(eglManager.getContext());
        externalTextureConverter.setFlipY(FLIP_FRAMES_VERTICALLY);
        externalTextureConverter.setConsumer(frameProcessor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    private void startCamera() {
        cameraXPreviewHelper = new CameraXPreviewHelper();
        cameraXPreviewHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    this.surfaceTexture = surfaceTexture;
                    surfaceView.setVisibility(View.VISIBLE);
                }
        );
        cameraXPreviewHelper.startCamera(this, CAMERA_FACING, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        externalTextureConverter.close();
        surfaceView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}