package com.mosect.lib.easygl;

import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;

/**
 * GLOutput内容，表示图像输出窗口
 */
public interface GLSurface extends GLContent {

    /**
     * 在{@link #getWindowObject() getWindowObject}返回此Object表示创建PBuffer窗口
     */
    Object PBUFFER_OBJECT = new Object();

    /**
     * 获取窗口对象
     *
     * @return 窗口对象，通常是{@link android.view.Surface Surface}对象，具体请查看
     * {@link android.opengl.EGL14#eglCreateWindowSurface(EGLDisplay, EGLConfig, Object, int[], int)}所支持对象
     */
    Object getWindowObject();

    /**
     * 获取窗口宽度
     *
     * @return 窗口宽度
     */
    int getWindowWidth();

    /**
     * 获取窗口高度
     *
     * @return 窗口高度
     */
    int getWindowHeight();
}
