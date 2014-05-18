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

	private static final String TAG = "fluffySheep";
	
	/**
	 *  Defines the gravity (The smaller - The faster the sheep will fall)
	 */
	
	private static final float GRAVITY_FACTOR = 3f;
	
	/**
	 *  The number of seconds the wall pass from the right side of the screen to the left one
	 */
	private static final float WALL_MOVE_TIME = 4f;
	
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
	 * 	Holds the current game state according to {@link}{@link GameState} value}
	 */
	private GameState gameState;

	/**
	 * The total time the sheep is flying 
	 * (used to calculate the current state of the wings that change every 500 ms)
	 */
	private float sheepTotalFlyTime = 0;
	
	/**
	 * The total time the sheep is in the air after user tap the screen 
	 * (used to calculate the sheep speed and height using physics
	 */
	private float sheepFallTime;
	
	private float sheepStartYPosition = 0;
	
	/**
	 * When this value is > 0 the sheep get a 'boost' up (We use it when the user tap the screen)
	 */
	private float sheepBoostUpSpeed;

	/**
	 * Current wall position 
	 */
	private float wallXPosition, wallYPosition;

	/**
	 * The hole in the wall position (Where the sheep need to pass through)  
	 */
	private float wallHoleYTopPosition, wallHoleYBottomPosition;

	/**
	 * The sheep vertical position
	 */
	private float sheepXPosition, sheepYPosition;

	/**
	 * The sheep current angle
	 */
	private float sheepAngle;

	private Bitmap sheepWingsUpBitmap, sheepWingsDownBitmap;
	private Bitmap backgroundImage;
	private Bitmap backgroundBottom;
	private Bitmap wallBitmap;
	private Bitmap startButtonBitmap;
	private Bitmap finishButtonBitmap;

	/**
	 * Holds the current background bottom line position 
	 * (we move it to give a feeling that the background is moving)
	 */
	private float backgroundBottmXPosition;

	/**
	 * Number of point gained for the current game session
	 */
	private int gamePoints;

	/**
	 * The total of walls created in the current game session
	 * (used to help calculate the user points)
	 */
	private int numOfWallsCreated;
	
	/**
	 * Holds the sheep current wings state. 
	 * If 'true' the sheep wings up bitmap is used, if 'false' the sheep wings down is used
	 */
	private boolean areSheepWingsUp;
	
	/**
	 * In order to keep the same game experience for each device 
	 * this variable (that depends on the height of the screen) is calculated on game start 
	 */
	private float deviceGravity;
	
	private Paint paint;

	public GameView(Context context, Bitmap framebuffer) {
		super(context);

		this.framebuffer = framebuffer;
		this.holder = getHolder();

		paint = new Paint();
		paint.setColor(Color.BLACK);
		paint.setTextSize(60);

		prepareAssets();

		ResetGame();
	}

	/**
	 * Resume the game
	 */
	public void resume() {
		running = true;
		renderThread = new Thread(this);
		renderThread.start();
	}

	/**
	 * Pause the game
	 */
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
			updateView();

			// Draw the framebuffer
			Canvas canvas = holder.lockCanvas();
			canvas.getClipBounds(dstRect);
			canvas.drawBitmap(framebuffer, 0, 0, null);
			holder.unlockCanvasAndPost(canvas);
		}
	}

	/**
	 * Draw all the game elements on the screen 
	 */
	private void updateView() {
		Canvas canvas = new Canvas(framebuffer);

		// Draw the background
		canvas.drawBitmap(backgroundImage, 0, 0, paint);

		// Draw the wall
		canvas.drawBitmap(wallBitmap, wallXPosition, wallYPosition, paint);

		// Decide which sheep image to show (wings up or wings down)
		Bitmap sheepBitmap = areSheepWingsUp ? sheepWingsUpBitmap : sheepWingsDownBitmap;

		// Create a rotated sheep bitmap according to the angle
		Matrix matrix = new Matrix();
		matrix.postRotate(sheepAngle, sheepBitmap.getWidth() / 2, sheepBitmap.getHeight() / 2);
		matrix.postTranslate(framebuffer.getWidth() / 2, sheepYPosition);

		// Draw the sheep
		canvas.drawBitmap(sheepBitmap, matrix, paint);

		// Draw the bottom moving background part
		canvas.drawBitmap(backgroundBottom, backgroundBottmXPosition, framebuffer.getHeight() - backgroundBottom.getHeight(), paint);

		// Show the game points
		paint.setColor(Color.BLACK);
		canvas.drawText(Integer.toString(gamePoints), 32, 62, paint);
		paint.setColor(Color.WHITE);
		canvas.drawText(Integer.toString(gamePoints), 30, 60, paint);

		if (gameState.equals(GameState.BeforeStart)) {
			// Show the start bitmap
			canvas.drawBitmap(startButtonBitmap, (framebuffer.getWidth() - startButtonBitmap.getWidth()) / 2, framebuffer.getHeight() / 5, paint);
		}
		else if (gameState.equals(GameState.Finished)) {
			// Show the finish bitmap
			canvas.drawBitmap(finishButtonBitmap, (framebuffer.getWidth() - startButtonBitmap.getWidth()) / 2, framebuffer.getHeight() / 5, paint);
		}
	}

	private void prepareAssets() {

		sheepWingsUpBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.sheep_wings_up);

		sheepWingsDownBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.sheep_wings_down);

		startButtonBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.start_button);

		finishButtonBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.finish_button);
		
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

		// Create the assets scaled to this specific screen size
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
	}

	/**
	 * Update all the game variables according to the givven time
	 * 
	 * @param deltaTime in ms from the last cycle
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
			int wallSpeed = (int) (framebuffer.getWidth() / WALL_MOVE_TIME);
			float pixelsToAdvance = ((deltaTime / 1000) * wallSpeed);

			wallXPosition -= pixelsToAdvance;

			sheepFallTime += deltaTime;

			sheepYPosition = (float) (deviceGravity * (sheepFallTime / 1000) * (sheepFallTime / 1000)) - sheepBoostUpSpeed * (sheepFallTime / 1000) + sheepStartYPosition;

			float sheepVSpeed = deviceGravity * (sheepFallTime / 1000) - sheepBoostUpSpeed;

			if (sheepVSpeed > 0) {
				// Sheep is with nose down according to her speed
				sheepAngle = -30 + sheepVSpeed / 2;
			}
			else {
				// Sheep is with nose up
				sheepAngle = -30;
			}
			
			sheepTotalFlyTime += deltaTime;

			// Decide if the sheep wings are up or down (each 250 ms we replace the wings state)
			if (sheepTotalFlyTime % 500 > 250) {
				areSheepWingsUp = true;
			}
			else {
				areSheepWingsUp = false;
			}

			// Advance the bottom background
			backgroundBottmXPosition -= pixelsToAdvance;

			if (backgroundBottmXPosition < -framebuffer.getWidth()) {
				backgroundBottmXPosition += framebuffer.getWidth();
			}

			CheckCollision();

			// Check if we need to add points to user (Checking if the sheep has passed the wall)
			if (sheepXPosition > wallXPosition + wallBitmap.getWidth()) {
				
				// Give points only if we did not give points for that wall (number of total walls = number of total points)
				if (numOfWallsCreated != gamePoints) {
					gamePoints++;
				}
			}
		}
	}

	/**
	 * Check if there is a collision between the sheep and the other elements
	 */
	private void CheckCollision() {

		boolean doWeHaveCollision = false;

		// Check if the sheep collide horizontally in the wall 
		if ((sheepXPosition + sheepWingsDownBitmap.getWidth() > wallXPosition) && (sheepXPosition < wallXPosition + wallBitmap.getWidth())) {

			// Yes, the sheep is in the wall horizontal range, now check vertically if the sheep is not in the wall hole

			if ((sheepYPosition < wallHoleYTopPosition + wallYPosition) || (sheepYPosition + sheepWingsDownBitmap.getHeight() > wallHoleYBottomPosition + wallYPosition)) {

				// We have collision 
				Log.i(TAG, "Sheep just crashed into a wall");

				doWeHaveCollision = true;
			}
		}

		if (!doWeHaveCollision) {
			// Check if the sheep has crashed on the ground
			if (sheepYPosition + sheepWingsDownBitmap.getHeight() > framebuffer.getHeight() - backgroundBottom.getHeight()) {

				// We have collision 
				Log.i(TAG, "Sheep just crashed into the floor");

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

		// Set the wall position out of the screen (The screen is moving from right to left)
		wallXPosition = screenWidth;

		// create the hole at random position 
		wallYPosition = -(int) (Math.random() * (screenHeight / 2));

		wallHoleYTopPosition = (float) (wallBitmap.getHeight() * 0.42);
		wallHoleYBottomPosition = (float) (wallBitmap.getHeight() * 0.6);

		numOfWallsCreated++;
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
				
				Log.i(TAG, "Sheep boost");
				
				sheepStartYPosition = sheepYPosition;
				
				// Reset the fall time (because of the boost)
				sheepFallTime = 0;
				
				// Give a boost up to the sheep
				sheepBoostUpSpeed = deviceGravity * 3 / 4;
			}
			else {
				Log.d(TAG, "Sheep is out of screen - do nothing with touch event");
			}

		}

		return super.onTouchEvent(event);
	}

	/**
	 * Reset all game values and prepare for a new game
	 */
	private void ResetGame() {
		
		Log.i(TAG, "Reset Game");
		
		gameState = GameState.BeforeStart;

		backgroundBottmXPosition = 0;

		// Place the bird ~ in the middle of the screen
		sheepYPosition = (framebuffer.getHeight() / 2);
		sheepXPosition = framebuffer.getWidth() / 2;

		sheepAngle = 0;

		gamePoints = 0;
		
		sheepFallTime = 0;

		numOfWallsCreated = 0;
		
		areSheepWingsUp = true;
		
		sheepTotalFlyTime = 0;
		
		sheepBoostUpSpeed = 0;
		
		deviceGravity = framebuffer.getHeight() / GRAVITY_FACTOR;

		createNewWall();
	}
}