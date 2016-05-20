package surojit.com.opensjbluez;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by Surojit on 1/4/2016.
 */
public class BouncingBall extends SurfaceView implements SurfaceHolder.Callback {

    private final int RADIUS = 50;
    private final float FACTOR_BOUNCEBACK = 0.25f;
    private final float mDeltaT = 0.5f; // imaginary time interval between each acceleration updates

    private int mXCenter;
    private int mYCenter;
    private RectF mRectF;
    private final Paint mPaint;
    private BallThread mThread;
    private BallCallback ballCallback;

    private float mVx;
    private float mVy;
    float mHeightScreen;
    float mWidthScreen;

    public BouncingBall(Context context) {
        super(context);

        getHolder().addCallback(this);
        mThread = new BallThread(getHolder(), this);
        ballCallback = (BallCallback) context;
        setFocusable(true);

        mPaint = new Paint();
        mPaint.setColor(Integer.parseInt("3F51B5",16));
        mPaint.setAlpha(192);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mRectF = new RectF();
    }

    // set the position of the ball
    public boolean setOvalCenter(int x, int y) {
        mXCenter = x;
        mYCenter = y;
        return true;
    }

    public void setHeight(float mHeightScreen){
        this.mHeightScreen = mHeightScreen+48;
    }

    public void setWidth(float mWidthScreen){
        this.mWidthScreen = mWidthScreen;
    }

    // calculate and update the ball's position
    public boolean updateOvalCenter() {
        mVx -= ballCallback.getXAcceleration() * mDeltaT;
        mVy += ballCallback.getYAcceleration() * mDeltaT;

        mXCenter += (int) (mDeltaT * (mVx + 0.5 * ballCallback.getXAcceleration() * mDeltaT));
        mYCenter += (int) (mDeltaT * (mVy + 0.5  * ballCallback.getYAcceleration() * mDeltaT));

        if (mXCenter < RADIUS) {
            mXCenter = RADIUS;
            mVx = -mVx * FACTOR_BOUNCEBACK;
        }

        if (mYCenter < RADIUS) {
            mYCenter = RADIUS;
            mVy = -mVy * FACTOR_BOUNCEBACK;
        }
        if (mXCenter > mWidthScreen - RADIUS) {
            mXCenter = (int)mWidthScreen - RADIUS;
            mVx = -mVx * FACTOR_BOUNCEBACK;
        }

        if (mYCenter > mHeightScreen - 2 * RADIUS) {
            mYCenter = (int)mHeightScreen - 2 * RADIUS;
            mVy = -mVy * FACTOR_BOUNCEBACK;
        }

        return true;
    }

    // update the canvas
    protected void onDraw(Canvas canvas) {
        if (mRectF != null && canvas != null) {
            mRectF.set(mXCenter - RADIUS, mYCenter - RADIUS, mXCenter + RADIUS, mYCenter + RADIUS);
            canvas.drawColor(0xFFFFFFFF);
            canvas.drawOval(mRectF, mPaint);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (!mThread.isAlive()) {
            mThread = new BallThread(getHolder(), this);
        }
        if (!mThread.isRunning()) {
            mThread.setRunning(true);
            mThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        mThread.setRunning(false);
        while (retry) {
            try {
                mThread.join();
                retry = false;
            } catch (InterruptedException e) {

            }
        }
    }
}