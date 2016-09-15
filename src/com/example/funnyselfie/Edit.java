package com.example.funnyselfie;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

public class Edit extends Activity {

	public static final String EXTRA_BYTES = "BMP";

	private static final int MAX_NUMBER_OF_FACES = 10;
	private static final float GLASSES_SCALE_CONSTANT = 2.5f;
	private static final float HAT_SCALE_CONSTANT = 1.5f;
	private static final float HAT_OFFSET = 2.5f;
	private static final float TIE_SCALE_CONSTANT = 1f;
	private static final float TIE_OFFSET = 2.2f;

	private int NUMBER_OF_FACE_DETECTED;

	private FaceDetector.Face[] detectedFaces;

	private Bitmap mBitmap;
	private ImageView mImageView;

	private AdView mAdView;// google adview
	protected InterstitialAd interstitial;
	protected boolean AdLoaded = false;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.edit);

		byte[] bytes = getIntent().getByteArrayExtra(EXTRA_BYTES);
		BitmapFactory.Options bfo = new BitmapFactory.Options();
		bfo.inPreferredConfig = Bitmap.Config.RGB_565;
		bfo.inScaled = false;
		bfo.inDither = false;
		Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bfo);
		// Rotate the bitmap
		Matrix matrix = new Matrix();
		matrix.postRotate(270);
		Bitmap rotatedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
		bmp.recycle();
		
		int width = rotatedBitmap.getWidth();
		int height = rotatedBitmap.getHeight();
		detectedFaces = new FaceDetector.Face[MAX_NUMBER_OF_FACES];
		FaceDetector faceDetector = new FaceDetector(width, height, MAX_NUMBER_OF_FACES);
		NUMBER_OF_FACE_DETECTED = faceDetector.findFaces(rotatedBitmap, detectedFaces);

		decorateFacesOnBitmap(rotatedBitmap);

		mBitmap = rotatedBitmap;
		mImageView = (ImageView) findViewById(R.id.imageView1);
		mImageView.setImageDrawable(new BitmapDrawable(mBitmap));

		mAdView = (AdView) findViewById(R.id.adView);

		String adsID = getResources().getString(R.string.admob_banner);
		if (adsID != null && !adsID.isEmpty()) {
			loadAds();
		}
		ImageButton shareButton = (ImageButton) findViewById(R.id.button_share);
		shareButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				share();
			}
		});
	}

	private void share() {
		ByteArrayOutputStream bytesArray = new ByteArrayOutputStream();
		mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytesArray);
		File f = new File(Environment.getExternalStorageDirectory() + File.separator + "temporary_file.jpg");
		try {
			f.createNewFile();
			FileOutputStream fo = new FileOutputStream(f);
			fo.write(bytesArray.toByteArray());
			fo.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		final Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.setType("image/jpeg");
		shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
		startActivity(Intent.createChooser(shareIntent, "Share"));
	}

	@Override
	public void onBackPressed() {
		Intent intent = new Intent(this, Main.class);
		startActivity(intent);
		displayInterstitial();
		finish();
	}

	/**
	 * Helper method to scale each object (hat, glasses, tie) to the size of the
	 * face
	 */
	private Bitmap scaleObjectToFace(FaceDetector.Face face, Bitmap object, float scaleConstant) {
		float newWidth = face.eyesDistance() * scaleConstant;
		float scaleFactor = newWidth / object.getWidth();
		return Bitmap.createScaledBitmap(object, Math.round(newWidth), Math.round(object.getHeight() * scaleFactor), false);
	}

	/**
	 * Method iterates through the faces and decorates each with a properly
	 * sized and placed hat, glasses, and tie
	 */
	private void decorateFacesOnBitmap(Bitmap tempBitmap) {
		Canvas canvas = new Canvas(tempBitmap);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.STROKE);

		for (int count = 0; count < NUMBER_OF_FACE_DETECTED; count++) {
			FaceDetector.Face face = detectedFaces[count];

			PointF midPoint = new PointF();
			face.getMidPoint(midPoint);

			// Put the glasses on the face
			Bitmap glasses = getRandomGlass();
			glasses = scaleObjectToFace(face, glasses, GLASSES_SCALE_CONSTANT);
			canvas.drawBitmap(glasses, midPoint.x - glasses.getWidth() / 2, midPoint.y - glasses.getHeight() / 2, paint);

			// Put the hat on the head
			Bitmap hat = getRandomHat();
			hat = scaleObjectToFace(face, hat, HAT_SCALE_CONSTANT);
			float hatTop = midPoint.y - HAT_OFFSET * face.eyesDistance() + 20;
			canvas.drawBitmap(hat, midPoint.x - hat.getWidth() / 2, hatTop - hat.getHeight() / 2, paint);

			// Put on the tie beneath the head
			Bitmap tie = getRandomTie();
			tie = scaleObjectToFace(face, tie, TIE_SCALE_CONSTANT);
			float tieTop = midPoint.y + TIE_OFFSET * face.eyesDistance() -20;
			canvas.drawBitmap(tie, midPoint.x - tie.getWidth() / 2, tieTop, paint);
		}
	}

	private Bitmap getRandomGlass() {
		Random r = new Random();
		List<Bitmap> list = new ArrayList<Bitmap>();
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.glass001));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.glass002));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.glass003));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.glass004));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.glass005));
		int index = r.nextInt(list.size());
		return list.get(index);
	}

	private Bitmap getRandomHat() {
		Random r = new Random();
		List<Bitmap> list = new ArrayList<Bitmap>();
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.hat001));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.hat002));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.hat003));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.hat004));
		int index = r.nextInt(list.size());
		return list.get(index);
	}

	private Bitmap getRandomTie() {
		Random r = new Random();
		List<Bitmap> list = new ArrayList<Bitmap>();
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.tie001));
		list.add(BitmapFactory.decodeResource(getResources(), R.drawable.tie002));
		int index = r.nextInt(list.size());
		return list.get(index);
	}

	private void loadAds() {

		AdRequest adRequest = new AdRequest.Builder().build();
		mAdView.loadAd(adRequest);
		interstitial = new InterstitialAd(this);
		AdRequest adRequest2 = new AdRequest.Builder().build(); //
		interstitial.setAdUnitId(getResources().getString(R.string.admob_interstitial));
		interstitial.setAdListener(new AdListener() {

			@Override
			public void onAdLoaded() {
				AdLoaded = true;
			}

			@Override
			public void onAdClosed() {
				super.onAdClosed();
			}

			@Override
			public void onAdFailedToLoad(int errorCode) {
				AdLoaded = false;
			}
		});

		interstitial.loadAd(adRequest2);

	}

	public void displayInterstitial() {
		String adsID = getResources().getString(R.string.admob_banner);
		if (adsID != null && !adsID.isEmpty()) {
			if (interstitial.isLoaded()) {
				interstitial.show();
			}
		}
	}

	@Override
	public void onPause() {
		if (mAdView != null) {
			mAdView.pause();
		}
		super.onPause();
	}

	/** Called when returning to the activity */
	@Override
	public void onResume() {
		super.onResume();
		AdLoaded = false;
		if (mAdView != null) {
			mAdView.resume();
		}
	}

	/** Called before the activity is destroyed */
	@Override
	public void onDestroy() {
		if (mAdView != null) {
			mAdView.destroy();
		}
		super.onDestroy();
	}
}
