package ${APP_PACKAGE};

import android.os.Build;
import android.view.View;

public class NativeLoader extends android.app.NativeActivity
{
    static
    {
        // System.loadLibrary("raylib");
        System.loadLibrary("${PROJECT_NAME}");
    }

    @Override
    public void onResume()
    {
        super.onResume();
        FullScreencall();
    }

    public void FullScreencall()
    {
        if(Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if(Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}