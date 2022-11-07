package com.mosect.lib.easygl;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

/**
 * 表示图像绘制的输出目标实体
 *
 * @param <T> 输出实体所需的内容类型
 */
public class GLOutput<T extends GLSurface> extends GLObject<T> {

    private static final String TAG = "GL/Output";

    private EGLConfig eglConfig = null;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private boolean selfContext = false; // 表示是否为自己内部创建的OpenGL上下文
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    public GLOutput(GLEnv env) {
        super(env);
    }

    @Override
    protected void onGLInitContent() {
        selfContext = false;
        GLSurface content = getContent();
        if (null != content) {
            // 触发内容初始化
            content.initContent(this);
            // 获取主输出
            GLOutput<?> mainOutput = getEnv().getMainOutput();
            if (mainOutput == this) {
                // 自己为主输出，则创建EGLContext对象
                selfContext = true;
                int surfaceType = getContent().getWindowObject() == GLSurface.PBUFFER_OBJECT ?
                        EGL14.EGL_PBUFFER_BIT : EGL14.EGL_WINDOW_BIT;
                int[] attribList = {
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE, surfaceType,
                        EGL14.EGL_NONE,
                };
                EGLConfig[] configs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!EGL14.eglChooseConfig(getEnv().getDisplay(), attribList, 0, configs,
                        0, configs.length, numConfigs, 0)) {
                    throw new RuntimeException("eglChooseConfig failed");
                }
                attribList = new int[]{
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE,
                };
                EGLContext context = EGL14.eglCreateContext(getEnv().getDisplay(),
                        configs[0], EGL14.EGL_NO_CONTEXT, attribList, 0);
                getEnv().checkEGLError("eglCreateContext");
                if (context == EGL14.EGL_NO_CONTEXT) {
                    throw new RuntimeException("eglCreateContext: EGL_NO_CONTEXT");
                }
                eglContext = context;
                eglConfig = configs[0];
            } else {
                eglContext = mainOutput.eglContext;
                eglConfig = mainOutput.eglConfig;
            }
            // 创建EGLSurface
            int[] attribList;
            if (getContent().getWindowObject() == GLSurface.PBUFFER_OBJECT) {
                attribList = new int[]{
                        EGL14.EGL_WIDTH, getContent().getWindowWidth(),
                        EGL14.EGL_HEIGHT, getContent().getWindowHeight(),
                        EGL14.EGL_NONE,
                };
            } else {
                attribList = new int[]{
                        EGL14.EGL_NONE,
                };
            }
            eglSurface = EGL14.eglCreateWindowSurface(getEnv().getDisplay(),
                    eglConfig, content.getWindowObject(), attribList, 0);
            getEnv().checkEGLError("eglCreateWindowSurface");
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw new RuntimeException("eglCreateWindowSurface: EGL_NO_SURFACE");
            }
            // 通知环境对象，输出可用
            getEnv().dispatchOutputValid(this);
        }
    }

    @Override
    protected void onGLClearContent() {
        super.onGLClearContent();
        GLSurface content = getContent();
        if (null != content) {
            // 触发内容清除方法
            content.destroyContent();
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            if (selfContext) {
                // 自己创建的上下文，需要销毁
                EGL14.eglDestroyContext(getEnv().getDisplay(), eglContext);
            }
            eglContext = EGL14.EGL_NO_CONTEXT;
        }
        eglConfig = null;
        selfContext = false;
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            // 销毁EGLSurface
            EGL14.eglDestroySurface(getEnv().getDisplay(), eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
            // 通知环境，输出不可用
            getEnv().dispatchOutputInvalid(this);
        }
    }

    /**
     * 将输出切换为当前输出
     */
    void makeCurrent() {
        if (!EGL14.eglMakeCurrent(getEnv().getDisplay(), eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
        onMakeCurrent();
    }

    /**
     * 通知输出，提交帧
     */
    void dispatchCommit() {
        boolean ok = EGL14.eglSwapBuffers(getEnv().getDisplay(), eglSurface);
        if (ok) {
            onFrameCommit();
        } else {
            Log.w(TAG, getPrintName() + "/dispatchCommit: failed");
        }
    }

    /**
     * 判断输出是否游戏
     *
     * @return true，有效，可以进行绘制；false，无效，不能进行绘制及其他OpenGL操作
     */
    public boolean isValid() {
        return eglSurface != EGL14.EGL_NO_SURFACE;
    }

    /**
     * 将此输出设置成主输出
     * 注意：环境中必须存在一个主输出，其相关实体对象才能正常工作
     */
    public void setMain() {
        getEnv().runGLAction(() -> getEnv().setMainOutput(this));
    }

    /**
     * 切换成当前时触发
     */
    protected void onMakeCurrent() {
        int width = getContent().getWindowWidth();
        int height = getContent().getWindowHeight();
        if (width > 0 && height > 0) {
            GLES20.glViewport(0, 0, width, height);
        }
    }

    /**
     * 帧提交时完成
     */
    protected void onFrameCommit() {
    }
}
