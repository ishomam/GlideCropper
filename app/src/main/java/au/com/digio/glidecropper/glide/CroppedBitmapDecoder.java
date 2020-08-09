package au.com.digio.glidecropper.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

import java.io.IOException;
import java.io.InputStream;

public class CroppedBitmapDecoder implements ResourceDecoder<CroppedImageDecoderInput, Bitmap> {

    private Context context;
    private static final String TAG = "CroppedBitmapDecoder";


    public CroppedBitmapDecoder(Context context) {
        this.context = context;
    }

    @Override
    public boolean handles(@NonNull CroppedImageDecoderInput source, @NonNull Options options) throws IOException {
        return true;
    }


    @Nullable
    @Override
    public Resource<Bitmap> decode( @NonNull CroppedImageDecoderInput source,
                                            int width,
                                            int height,
                                            @NonNull Options options) throws IOException {

        Bitmap bitmap;
        BitmapRegionDecoder decoder = null;
        InputStream inputStream = null;

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inJustDecodeBounds = true;

        try {
            inputStream = context.getContentResolver().openInputStream(source.uri);
            BitmapFactory.decodeStream(inputStream, null, bitmapOptions);
        } finally {
            inputStream.close();
        }

        int orientation = getImageOrientation(context, source.uri);

        int imageWidth;
        int imageHeight;
        if (orientation == 0 || orientation == 180) {
            imageWidth = bitmapOptions.outWidth;
            imageHeight = bitmapOptions.outHeight;
        } else {
            imageWidth = bitmapOptions.outHeight;
            imageHeight = bitmapOptions.outWidth;
        }

        // In following: ensure the cropping region doesn't exceed the image dimensions
        int croppedWidth = Math.min(source.viewWidth, imageWidth);
        int croppedHeight = Math.min(source.viewHeight, imageHeight);
        int horizontalOffset = source.horizontalOffset;
        int verticalOffset = source.verticalOffset;

        // From here it's sure that: croppedWidth <= imageWidth, and croppedHeight <= imageHeight
        if (croppedWidth + horizontalOffset > imageWidth){
            horizontalOffset = 0;
        }
        if (croppedHeight + verticalOffset > imageHeight){
            verticalOffset = 0;
        }

        int desCroppedWidth = source.desImageWidth;
        int desCroppedHeight = 0;
        if(desCroppedWidth == croppedWidth){
            desCroppedHeight = croppedHeight;
        } else {
            desCroppedHeight = (int) (((float) croppedHeight / croppedWidth) * desCroppedWidth);
        }

        int desImageWidth = (int) (((float) imageWidth / croppedWidth) * desCroppedWidth);
        int desImageHeight = (int) (((float) imageHeight / croppedHeight) * desCroppedHeight);


        // All values that we have now are for the real correct photo orientation, but we need to
        // rotate values before encoding (in encoding the photo taken as portrait will be rotated 90°
        // anti-clockwise so it will be landscape so we need to map the values)
        // Rotate offsets
        // ***************************************************************************************
        if (orientation == 90) {
            int horizontalOffset_temp = horizontalOffset;
            horizontalOffset = verticalOffset;
            verticalOffset = imageWidth - horizontalOffset_temp - croppedWidth;
        } else if (orientation == 180) {
            horizontalOffset = imageWidth - horizontalOffset - croppedWidth;
            verticalOffset = imageHeight - verticalOffset - croppedHeight;
        } else if (orientation == 270) {
            int horizontalOffset_temp = horizontalOffset;
            horizontalOffset = imageHeight - verticalOffset - croppedHeight;
            verticalOffset = horizontalOffset_temp;
        } else if (orientation != 0) {
//            throw new RuntimeException("Image Orientation is not 90/180/270: " + orientation);
            Log.e(TAG, "Image Orientation is not 0/90/180/270, it's: " + orientation);
        }

        // Exchange width and height if orientation is 90 / 270
        if (orientation == 90 || orientation == 270) {
            int croppedWidth_temp = croppedWidth;
            croppedWidth = croppedHeight;
            croppedHeight = croppedWidth_temp;
            int desImageWidth_temp = desImageWidth;
            desImageWidth = desImageHeight;
            desImageHeight = desImageWidth_temp;
        }
        // ***************************************************************************************

        bitmapOptions.inJustDecodeBounds = false;
        bitmapOptions.inSampleSize = calculateInSampleSize(bitmapOptions, desImageWidth,
                desImageHeight);
        try {
            // Note: exact resizing using (inScaled, inDensity, inTargetDensity) is not supported by
            // BitmapRegionDecoder. However, inSampleSize is used to scale down to 1/2, 1/4, 1/8 of
            // the cropped region directly
            inputStream = context.getContentResolver().openInputStream(source.uri);
            decoder = BitmapRegionDecoder.newInstance(inputStream, false);

            Rect region = new Rect(horizontalOffset, verticalOffset,
                    horizontalOffset + croppedWidth,
                    verticalOffset + croppedHeight);

            // Decode image content within the cropping region
            bitmap = decoder.decodeRegion(region, bitmapOptions);
            bitmap = Bitmap.createScaledBitmap(bitmap, desCroppedWidth, desCroppedHeight, true);
            // We need to rotate the final image to bring back the original correct orientation
            if (orientation != 0) {
                bitmap = rotateImage(bitmap, orientation);
            }
        } finally {
            inputStream.close();
            decoder.recycle();
        }

        return new SimpleResource<>(bitmap);
    }

    /**
     * This rotates anti-clockwise by (a) then translates by u_x, u_y according to:
     * x' = x cos(a) - y sin(a) - u_x
     * y' = y cos(a) + x sin(a) - u_y
     * where u_x, and u_y refers to x and y axes AFTER rotation
     */
    private int[] rotateAndTranslatePoint(int[] xy, int antiClockwiseRotation,
                                                  int xTranslation, int yTranslation) {
        //            int horizontalOffset_temp = horizontalOffset;
//            horizontalOffset = verticalOffset;
//            verticalOffset = imageWidth - horizontalOffset_temp - croppedWidth;

        int[] xyOutput = new int[xy.length];
        int x = xy[0];
        int y = xy[1];
        int cos = 0;
        int sin = 0;
        if (antiClockwiseRotation == 0) {
            cos = 1;
            sin = 0;
        } else if (antiClockwiseRotation == 90 || antiClockwiseRotation == -270) {
            cos = 0;
            sin = 1;
        } else if (antiClockwiseRotation == 180) {
            cos = -1;
            sin = 0;
        } else if (antiClockwiseRotation == 270 || antiClockwiseRotation == -90 ) {
            cos = 0;
            sin = -1;
        }
        xyOutput[0] = x * cos - y * sin - xTranslation;
        xyOutput[1] = y * cos + x * sin - yTranslation;
        return xyOutput;
    }

    /**
     * This will return the amount of rotation as degrees (90 means the photo must rotated 90°
     *  clockwise to be in its photographed orientation)
     */
    private static int getImageOrientation(Context context, Uri imageUri) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(imageUri);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(imageUri.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int newWidth, int newHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > newHeight || width > newWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= newHeight
                    && (halfWidth / inSampleSize) >= newWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(),
                matrix, true);
        img.recycle();
        return rotatedImg;
    }
}
