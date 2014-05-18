package com.sheep.fluffysheep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements Runnable {

	private Context ctx;
	private Bitmap framebuffer;
	private Thread renderThread = null;
	private SurfaceHolder holder;
	volatile boolean running = false;

	private enum GameState {
		BeforeStart,	// Game has not started, showing 'start' option
		Running,		// Game is running
		Finished		// Game is finished (after user crash)
	}

	/**
	 * 	Holds the current game state
	 */
	private GameState gameState;

	private float sheepFallTime = 0;
	private float sheepStartYPosition = 0;
	private float sheepStartSpeed = 0;

	/**
	 * Current wall position 
	 */
	private float wallXPosition, wallYPosition;

	/**
	 * The hole in the wall position (Where the sheep need to pass through)  
	 */
	private float wallHoleYTopPosition, wallHoleYBottomPosition;

	private Paint wallPaint;

	/**
	 * The sheep vertical position
	 */
	private float sheepXPosition, sheepYPosition;

	/**
	 * The sheep current angle
	 */
	private float sheepAngle;

	/**
	 * Sheep current speed
	 */
	//private float sheepVSpeed;

	/**
	 *  The number of seconds the wall pass from the right side of the screen to the left one
	 */
	private int wallMoveTime = 7;

	private Bitmap sheepBitmap;

	private Bitmap backgroundImage;

	private Bitmap backgroundBottom;

	private float backgroundBottmXPosition;

	private Bitmap wallBitmap;
	
	private Bitmap startButtonBitmap;
	
	private Bitmap finishButton;

	public GameView(Context context, Bitmap framebuffer) {
		super(context);

		ctx = context;

		this.framebuffer = framebuffer;
		this.holder = getHolder();

		wallPaint = new Paint();
		wallPaint.setColor(Color.BLACK);
		wallPaint.setTextSize(30);

		sheepBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.ic_launcher);

		backgroundBottom = BitmapFactory.decodeResource(getResources(),
				R.drawable.background_bottom);
		
		startButtonBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.start_button);
		
		finishButton = BitmapFactory.decodeResource(getResources(),
				R.drawable.finish_button);

		prepareAssets();
		
		ResetGame();
	}

	public void resume() {
		running = true;
		renderThread = new Thread(this);
		renderThread.start();
	}

	public void pause() {
		running = false;
		while (true) {
			try {
				renderThread.join();
				break;
			} catch (InterruptedException e) {
				// retry
			}
		}
	}

	public void run() {
		Rect dstRect = new Rect();
		long startTime = System.nanoTime();

		while (running) {
			if (!holder.getSurface().isValid())
				continue;

			// Get the time in ms
			float deltaTime = (System.nanoTime() - startTime) / 1000000.0f;
			startTime = System.nanoTime();

			if (backgroundImage == null) {
				prepareAssets();
			}

			updateLogic(deltaTime);
			updateView(deltaTime);

			// Draw the framebuffer
			Canvas canvas = holder.lockCanvas();
			canvas.getClipBounds(dstRect);
			canvas.drawBitmap(framebuffer, 0, 0, null);
			holder.unlockCanvasAndPost(canvas);
		}
	}

	private void updateView(float deltaTime) {
		Canvas canvas = new Canvas(framebuffer);

		// Draw the background
		canvas.drawBitmap(backgroundImage, 0, 0, wallPaint);

		// Draw the wall
		canvas.drawBitmap(wallBitmap, wallXPosition, wallYPosition, wallPaint);

		// Create a rotated sheep bitmap according to the angle
		Matrix matrix = new Matrix();
		matrix.postRotate(sheepAngle, sheepBitmap.getWidth() / 2, sheepBitmap.getHeight() / 2);
		matrix.postTranslate(framebuffer.getWidth() / 2, sheepYPosition);

		// Draw the sheep
		canvas.drawBitmap(sheepBitmap, matrix, wallPaint);

		// Draw the bottom moving background part
		canvas.drawBitmap(backgroundBottom, backgroundBottmXPosition, framebuffer.getHeight() - backgroundBottom.getHeight(), wallPaint);

		if (gameState.equals(GameState.BeforeStart)) {
			// Show the start bitmap
			canvas.drawBitmap(startButtonBitmap, (framebuffer.getWidth() - startButtonBitmap.getWidth()) / 2, framebuffer.getHeight() / 5, wallPaint);
		}
		else if (gameState.equals(GameState.Finished)) {
			// Show the finish bitmap
			canvas.drawBitmap(finishButton, (framebuffer.getWidth() - startButtonBitmap.getWidth()) / 2, framebuffer.getHeight() / 5, wallPaint);
		}
	}

	private void prepareAssets() {

		Bitmap originaBackgroundImage = BitmapFactory.decodeResource(getResources(),
				R.drawable.game_background); 

		Bitmap originaBackgroundBottom = BitmapFactory.decodeResource(getResources(),
				R.drawable.background_bottom); 

		Bitmap originalWallBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.wall);

		float scaleWidth = ((float) framebuffer.getWidth()) / originaBackgroundImage.getWidth();
		float scaleHeight = ((float) framebuffer.getHeight()) / originaBackgroundImage.getHeight();

		Matrix matrix = new Matrix();
		matrix.postScale(scaleWidth, scaleHeight);

		backgroundImage = Bitmap.createBitmap(originaBackgroundImage, 0, 0, originaBackgroundImage.getWidth(), originaBackgroundImage.getHeight(), matrix, false);
		backgroundBottom = Bitmap.createBitmap(originaBackgroundBottom, 0, 0, originaBackgroundBottom.getWidth(), originaBackgroundBottom.getHeight(), matrix, false);
		wallBitmap = Bitmap.createBitmap(originalWallBitmap, 0, 0, originalWallBitmap.getWidth(), originalWallBitmap.getHeight(), matrix, false);

		if (backgroundImage != originaBackgroundImage) {
			originaBackgroundImage.recycle();
		}

		if (backgroundBottom != originaBackgroundBottom) {
			originaBackgroundBottom.recycle();
		}

		if (wallBitmap != originalWallBitmap) {
			originaBackgroundBottom.recycle();
		}

		// Create the game assets
		createNewWall();
		sheepXPosition = framebuffer.getWidth() / 2;
		sheepFallTime = 0;
	}

	/**
	 * @param deltaTime in ms
	 */
	private void updateLogic(float deltaTime) {

		// Update the game logic only if the game is running
		if (gameState.equals(GameState.Running)) {

			// Check if the wall got out of the left side of the screen
			if (wallXPosition + wallBitmap.getWidth() < 0) {
				// Yes, it is out of the screen, we need to create a new wall
				createNewWall();
			}		

			// Advance the wall ( physics: X = V * t )
			int wallSpeed = framebuffer.getWidth() / wallMoveTime;
			float pixelsToAdvance = ((deltaTime / 1000) * wallSpeed);

			wallXPosition -= pixelsToAdvance;

			sheepFallTime += deltaTime;

			sheepYPosition = (float) (300 * (sheepFallTime / 1000) * (sheepFallTime / 1000)) - sheepStartSpeed * (sheepFallTime / 1000) + sheepStartYPosition;

			float sheepVSpeed = 300 * (sheepFallTime / 1000) - sheepStartSpeed;

			Log.i("sheep", "Speed: " + sheepVSpeed);

			if (sheepVSpeed > 0) {
				// Sheep is with nose down according to her speed
				sheepAngle = -30 + sheepVSpeed / 2;
			}
			else {
				// Sheep is with nose up
				sheepAngle = -30;
			}

			// Advance the bottom background
			backgroundBottmXPosition -= pixelsToAdvance;

			if (backgroundBottmXPosition < -framebuffer.getWidth()) {
				backgroundBottmXPosition += framebuffer.getWidth();
			}

			CheckCollision();
		}
	}

	private void CheckCollision() {

		boolean doWeHaveCollision = false;

		// Check if the sheep collide horizontally in the wall 
		if ((sheepXPosition + sheepBitmap.getWidth() > wallXPosition) && (sheepXPosition < wallXPosition + wallBitmap.getWidth())) {

			// Yes, the sheep is in the wall horizontal range, now check vertically if the sheep is not in the wall hole

			if ((sheepYPosition < wallHoleYTopPosition + wallYPosition) || (sheepYPosition + sheepBitmap.getHeight() > wallHoleYBottomPosition + wallYPosition)) {

				// We have collision 
				Log.i("sheep", "col col");

				doWeHaveCollision = true;
			}
		}

		if (!doWeHaveCollision) {
			// Check if the sheep has crashed on the ground
			if (sheepYPosition + sheepBitmap.getHeight() > framebuffer.getHeight() - backgroundBottom.getHeight()) {

				// We have collision 
				Log.i("sheep", "col col");

				doWeHaveCollision = true;
			}
		}

		if (doWeHaveCollision) {
			// Stop the game
			gameState = GameState.Finished;
		}
	}

	/**
	 * Creates a new wall and place it to the right of the screen (off the screen)
	 */
	private void createNewWall() {
		int screenWidth = this.framebuffer.getWidth();
		int screenHeight = this.framebuffer.getHeight();

		// Set the wall width
		//	wallWidth = (screenWidth / 10);

		// Set the wall position out of the screen
		wallXPosition = screenWidth;

		// create the hole at random position 
		wallYPosition = -(int) (Math.random() * (screenHeight / 2));

		wallHoleYTopPosition = (float) (wallBitmap.getHeight() * 0.42);
		wallHoleYBottomPosition = (float) (wallBitmap.getHeight() * 0.6);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (gameState.equals(GameState.BeforeStart)) {
				// The user started the game
				gameState = GameState.Running;
			}
			
			if (gameState.equals(GameState.Finished)) {
				// The user re-started the game
				ResetGame();
			}
			
			// The user has touched the screen 
			if (sheepYPosition > 0) {
				sheepStartYPosition = sheepYPosition;
				sheepFallTime = 0;
				sheepStartSpeed = 250;
			}
			else {
				Log.d("Sheep", "Sheep is out of screen - do nothing with touch event");
			}

		}

		return super.onTouchEvent(event);
	}

	/**
	 * Reset all game values and prepare for a new game
	 */
	private void ResetGame() {
		
		gameState = GameState.BeforeStart;
		
		backgroundBottmXPosition = 0;
		
		// Place the bird ~ in the middle of the screen
		sheepYPosition = (framebuffer.getHeight() / 2);
		
		sheepAngle = 0;
		
		createNewWall();
	}
}