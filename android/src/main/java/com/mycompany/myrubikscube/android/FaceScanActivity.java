package com.mycompany.myrubikscube.android;

import static androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST;
import static java.lang.Math.min;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.mycompany.myrubikscube.R;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.KNearest;
import org.opencv.ml.Ml;
import org.opencv.utils.Converters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scans one face of the cube using CameraX and OpenCV.
 * The face to scan is passed via an Intent extra "FORCE_CENTER" (a character like 'U','R','F','D','L','B').
 * The scanned result is a 9-character string (row-major order) returned via the result Intent extra "FACE_STRING".
 */
public class FaceScanActivity extends AppCompatActivity {

    private static final String TAG = "FaceScanActivity";

    private ImageView imageView;
    private Button saveButton;

    // For CameraX
    private ImageAnalysis imageAnalysis = null;
    private Camera camera = null;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    // KNN classifier variables
    private Mat trainData;
    private KNearest knn;

    // For scanning logic
    // detectedColor[row][col] will hold the predicted color index for each cubie.
    private final int[][] detectedColor = new int[3][3];
    // We also use a 3x3 Mat array for smoothing (moving average)
    private Mat[][] aveColor = new Mat[3][3];
    private static final float ALPHA = 0.75f;

    // The forced center color (as a character, e.g., 'U', 'R', etc.)
    private char forcedCenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_scan);

        // Get the forced center color from the intent extra.
        forcedCenter = getIntent().getCharExtra("FORCE_CENTER", 'X');
        if (forcedCenter == 'X') {
            Log.e(TAG, "No forced center color provided. Finishing.");
            finish();
        }

        imageView = findViewById(R.id.imageViewFaceScan);
        saveButton = findViewById(R.id.saveButtonFaceScan);

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!");
            finish();
        } else {
            Log.d(TAG, "OpenCV initialized successfully.");
            initializeOpenCvComponents();
        }

        // Start the camera
        startCamera();

        // When user taps Save, build the 9-character string and return it.
        saveButton.setOnClickListener(v -> {
            // Force the center value to the forcedCenter
            StringBuilder faceString = new StringBuilder();
            synchronized (detectedColor) {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (row == 1 && col == 1) {
                            faceString.append(forcedCenter);
                        } else {
                            // Use the detected color index to look up the corresponding letter.
                            // We assume that ImageUtil.colorLabel[] (e.g., {"Y", "O", "G", "W", "R", "B"}) is defined.
                            faceString.append(ImageUtil.colorLabel[detectedColor[row][col]]);
                        }
                    }
                }
            }
            Log.d(TAG, "Scanned face string: " + faceString.toString());
            Intent resultIntent = new Intent();
            resultIntent.putExtra("FACE_STRING", faceString.toString());
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    private void initializeOpenCvComponents() {
        // Initialize KNN with training data from ImageUtil
        trainData = new Mat(6, 4, CvType.CV_32F);
        for (int i = 0; i < 6; i++) {
            trainData.put(i, 0, ImageUtil.colorData[i]);
        }
        knn = KNearest.create();
        knn.train(trainData, Ml.ROW_SAMPLE, Converters.vector_int_to_Mat(ImageUtil.colorResponse));

        // Allocate the moving average storage
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                aveColor[row][col] = null;
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                imageAnalysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new MyImageAnalyzer());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, cameraSelector, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "Camera init error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class MyImageAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            // Convert ImageProxy to Mat (using ImageUtil.getMatFromImage)
            Mat mat = ImageUtil.getMatFromImage(image);
            mat = fixMatRotation(mat);

            int h = mat.rows();
            int w = mat.cols();
            Mat matOutput = mat.clone();

            double cLen = min(h, w) * 0.8;
            int boxLen = (int) (cLen / 3);
            int startX = (int) ((w - cLen) / 2);
            int startY = (int) ((h - cLen) / 2);

            // Analyze each of the 9 squares
            synchronized (detectedColor) {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int x = startX + boxLen * col;
                        int y = startY + boxLen * row;
                        Mat color = ImageUtil.calcBoxColorAve(mat, x, y, boxLen);
                        color = ImageUtil.calcMovingAveColor(aveColor[row][col], color, ALPHA);
                        aveColor[row][col] = color;

                        Mat res = new Mat();
                        knn.findNearest(color, 1, res);
                        int colorIdx = (int) res.get(0, 0)[0];
                        res.release();
                        detectedColor[row][col] = colorIdx;

                        // Draw bounding box and label for preview
                        Imgproc.rectangle(matOutput, new Rect(x, y, boxLen, boxLen), new Scalar(255, 0, 0), 2);
                        String label = ImageUtil.colorLabel[colorIdx];
                        Imgproc.putText(matOutput, label, new Point(x + 10, y + 30),
                            Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(
                                ImageUtil.colorData[colorIdx][0],
                                ImageUtil.colorData[colorIdx][1],
                                ImageUtil.colorData[colorIdx][2]), 2);
                    }
                }
            }

            // Crop to a square preview
            if (h > w) {
                int bX = 0;
                int bY = startY - startX;
                matOutput = matOutput.submat(new Rect(bX, bY, w, w));
            } else {
                int bX = startX - startY;
                int bY = 0;
                matOutput = matOutput.submat(new Rect(bX, bY, h, h));
            }

            // Convert matOutput to bitmap and show in ImageView
            final android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                matOutput.cols(), matOutput.rows(), android.graphics.Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(matOutput, bmp);
            runOnUiThread(() -> imageView.setImageBitmap(bmp));

            matOutput.release();
            mat.release();
            image.close();
        }

        private Mat fixMatRotation(Mat matOrg) {
            Mat mat;
            switch (imageView.getDisplay().getRotation()) {
                case Surface.ROTATION_0:
                default:
                    mat = new Mat(matOrg.cols(), matOrg.rows(), matOrg.type());
                    Core.transpose(matOrg, mat);
                    Core.flip(mat, mat, 1);
                    break;
                case Surface.ROTATION_90:
                    mat = matOrg;
                    break;
                case Surface.ROTATION_270:
                    mat = matOrg;
                    Core.flip(mat, mat, -1);
                    break;
            }
            return mat;
        }
    }
}
