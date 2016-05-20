package surojit.com.opensjbluez;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * Created by Surojit on 1/4/2016.
 */
public class BallThread extends Thread {
    private SurfaceHolder mSurfaceHolder;
    private BouncingBall mShapeView;
    private boolean mRun = false;

    public BallThread(SurfaceHolder surfaceHolder, BouncingBall shapeView) {
        mSurfaceHolder = surfaceHolder;
        mShapeView = shapeView;
    }

    public void setRunning(boolean run) {
        mRun = run;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public boolean isRunning(){
        return mRun;
    }

    @Override
    public void run() {
        Canvas c;
        while (mRun) {
            mShapeView.updateOvalCenter();
            c = null;
            try {
                c = mSurfaceHolder.lockCanvas(null);
                synchronized (mSurfaceHolder) {
                    mShapeView.onDraw(c);
                }
            } finally {
                if (c != null) {
                    mSurfaceHolder.unlockCanvasAndPost(c);
                }
            }
        }
    }
}