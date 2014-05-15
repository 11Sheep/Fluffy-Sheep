package com.sheep.fluffysheep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements Runnable {
	
	private Context ctx;
	private Bitmap framebuffer;
	private Thread renderThread = null;
	private SurfaceHolder holder;
	volatile boolean running = false;
	
	/**
	 * Current wall left position 
	 */
	private float wallXPosition;
	
	/**
	 * Current wall width
	 */
	private float wallWidth;
	
	/**
	 * The hole in the wall position (Where the sheep need to pass through)  
	 */
	private float wallHoleYTopPosition, wallHoleYBottomPosition;
	
	private Paint wallPaint;
	
	/**
	 * The sheep vertical position
	 */
	private float sheepYPosition;
	
	/**
	 * The sheep current angle
	 */
	private float sheepAngle;
	
	/**
	 * Sheep current speed
	 */
	private float sheepVSpeed;
	
	/**
	 *  The number of seconds the wall pass from the right side of the screen to the left one
	 */
	private int wallMoveTime = 10;
	
	public GameView(Context context, Bitmap framebuffer) {
		super(context);
		
		ctx = context;

		this.framebuffer = framebuffer;
		this.holder = getHolder();
		
		wallPaint = new Paint();
		wallPaint.setColor(Color.BLACK);
	}

	public void resume() {
		// Create the game assets
		createNewWall();
		createBird();
		sheepVSpeed = 0;
	
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
		Canvas c = new Canvas(framebuffer);
		
		// Draw the background
		c.drawARGB(0xff, 0xff, 0xff, 0xff);
		
		drawWall(c);
		
		drawSheep(c);
	}

	/**
	 * @param deltaTime in ms
	 */
	private void updateLogic(float deltaTime) {
		// Check if the wall got out of the left side of the screen
		if (wallXPosition + wallWidth < 0) {
			// Yes, it is out of the screen, we need to create a new wall
			createNewWall();
		}		
		
		// Advance the wall ( physics: X = V * t )
		int wallSpeed = getWidth() / wallMoveTime;
		float pixelsToAdvance = ((deltaTime / 1000) * wallSpeed);
		
		wallXPosition -= pixelsToAdvance;
		
		// Calculate sheep position ( physics: h = m * (v * v)  )
		sheepVSpeed += 0.1;
		
		if (sheepVSpeed > 0) {
			sheepYPosition += (sheepVSpeed * sheepVSpeed);
		}
		else {
			sheepYPosition -= (sheepVSpeed * sheepVSpeed);
		}
	}
	
	private void drawWall(Canvas canvas) {
		// Draw the upper square
		canvas.drawRect(wallXPosition, 0, wallXPosition + wallWidth, wallHoleYTopPosition, wallPaint);
		
		// Draw the lower square
		canvas.drawRect(wallXPosition, wallHoleYBottomPosition, wallXPosition + wallWidth, getHeight(), wallPaint);	
	}
	
	private void drawSheep(Canvas canvas) {
		// Draw the sheep
		canvas.drawRect(getWidth() / 2, sheepYPosition, (getWidth() / 2) + 50, sheepYPosition + 50, wallPaint);
	}
	
	/**
	 * Creates a new wall and place it to the right of the screen (off the screen)
	 */
	private void createNewWall() {
		int screenWidth = this.getWidth();
		int screenHeight = this.getHeight();
		
		// Set the wall width
		wallWidth = (screenWidth / 10);
	
		// Set the wall position out of the screen
		wallXPosition = screenWidth;
		
		// create the hole at random position 
		wallHoleYTopPosition = (int) (Math.random() * (screenHeight / 2) + Math.random() * (screenHeight / 4));
		wallHoleYBottomPosition = wallHoleYTopPosition + (screenHeight / 4);
	}
	
	private void createBird() {
		// Place the bird ~ in the middle of the screen
		sheepYPosition = (getHeight() / 2);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			// The user pressed 
			sheepVSpeed = -4;
			
		}
		
		return super.onTouchEvent(event);
	}
}