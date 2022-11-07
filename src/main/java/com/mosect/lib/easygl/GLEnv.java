package com.mosect.lib.easygl;

import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * OpenGL绘制环境
 */
public class GLEnv {

    private static final String TAG = "GL/Env";

    private final static long SECOND_LENGTH = 1000 * 1000 * 1000L;

    private String name; // 名称，仅作为标记使用
    private int fps = 24; // 帧率：大于0，控制帧率；否则，不限制帧率
    private int state = 0; // 状态：0，未运行；1，运行中；2，已销毁

    private final byte[] lock = new byte[0]; // 资源锁
    private final List<Runnable> actions = new ArrayList<>(); // 表示下一帧要执行的动作列表
    private final List<GLObject<?>> objects = new ArrayList<>(); // 缓存的实体对象
    private final List<GLOutput<?>> outputs = new ArrayList<>(); // 缓存的输出对象
    private final HashSet<GLObject<?>> objectSet = new HashSet<>(); // 已缓存的实体对象
    private final Map<Class<?>, GLShader<?>> shaderMap = new HashMap<>(); // 缓存的Shader对象

    private GLOutput<?> currentOutput = null; // 当前输出
    private GLOutput<?> mainOutput; // 主输出

    private EGLDisplay display; // OpenGL实现接口对象

    /**
     * 开始绘制线程，环境将会初始化
     */
    public void start() {
        synchronized (lock) {
            if (state == 0) {
                state = 1;
                new Thread(this::loop).start();
            }
        }
    }

    /**
     * 在绘制线程执行动作
     *
     * @param runnable 动作
     * @return true，已提交执行动作；false，未提交执行动作
     */
    public boolean runGLAction(Runnable runnable) {
        synchronized (lock) {
            if (state != 2) {
                actions.add(runnable);
                return true;
            }
            return false;
        }
    }

    /**
     * 依附实体对象到环境
     *
     * @param object 实体对象
     */
    protected void attach(GLObject<?> object) {
        synchronized (lock) {
            if (objectSet.contains(object)) return; // 已缓存，忽略
            objectSet.add(object); // 记录对象
            if (object instanceof GLOutput) {
                GLOutput<?> output = (GLOutput<?>) object;
                if (outputs.add(output)) {
                    if (null != mainOutput && mainOutput.isValid()) {
                        // 已存在有效的主输出，可以依附此实体对象
                        output.dispatchAttached(true);
                    }
                }
            } else if (object instanceof GLShader) {
                GLShader<?> shader = (GLShader<?>) object;
                if (!shaderMap.containsKey(shader.getClass())) {
                    shaderMap.put(shader.getClass(), shader);
                    if (null != mainOutput && mainOutput.isValid()) {
                        // 已存在有效的主输出，可以依附此实体对象
                        if (null != mainOutput && mainOutput.isValid()) {
                            shader.dispatchAttached(true);
                        }
                    }
                }
            } else {
                if (objects.add(object)) {
                    if (null != mainOutput && mainOutput.isValid()) {
                        // 已存在有效的主输出，可以依附此实体对象
                        object.dispatchAttached(true);
                    }
                }
            }
        }
    }

    /**
     * 卸载实体对象
     *
     * @param object 实体对象
     */
    protected void detach(GLObject<?> object) {
        synchronized (lock) {
            if (!objectSet.contains(object)) return; // 未记录此对象，忽略操作
            objectSet.remove(object); // 移除实体对象记录
            if (object instanceof GLOutput) {
                GLOutput<?> output = (GLOutput<?>) object;
                if (outputs.remove(output)) {
                    // 卸载实体
                    output.dispatchAttached(false);
                    // 清空其他相关实体对象变量
                    if (output == currentOutput) {
                        currentOutput = null;
                    }
                    if (output == mainOutput) {
                        setMainOutput(null);
                    }
                }
            } else if (object instanceof GLShader) {
                GLShader<?> shader = (GLShader<?>) object;
                GLShader<?> existObj = shaderMap.get(shader.getClass());
                if (existObj == shader) {
                    shaderMap.remove(shader.getClass());
                    // 卸载实体
                    shader.dispatchAttached(false);
                }
            } else {
                if (objects.remove(object)) {
                    // 卸载实体
                    object.dispatchAttached(false);
                }
            }
        }
    }

    /**
     * 绘制线程循环
     */
    private void loop() {
        Log.d(TAG, getPrintName() + "/loop: start");

        try {
            // 创建环境
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("EGL_DEFAULT_DISPLAY not found");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw new RuntimeException("eglInitialize failed");
            }
            Log.d(TAG, String.format("%s/eglInitialize: version=%s.%s", getPrintName(), version[0], version[1]));

            long lastDrawTime = -1;
            while (state == 1) {
                synchronized (lock) {
                    if (null != mainOutput && mainOutput.isValid()) {
                        // 主输出可用，切换成主输出
                        makeCurrent(mainOutput);
                    }
                    if (actions.size() > 0) {
                        for (Runnable action : actions) {
                            action.run();
                        }
                        actions.clear();
                    }
                }
                // 帧率控制
                int fps = this.fps;
                if (fps > 0 && lastDrawTime >= 0) {
                    long framePartTime = SECOND_LENGTH / fps;
                    long partTime = System.nanoTime() - lastDrawTime;
                    if (partTime > 0 && partTime < framePartTime) {
                        long timeOffset = framePartTime - partTime;
                        long mills = timeOffset / 1000000;
                        int nanos = (int) (timeOffset % 1000000);
                        try {
                            //noinspection BusyWait
                            Thread.sleep(mills, nanos);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                // 绘制帧
                lastDrawTime = System.nanoTime();
                synchronized (lock) {
                    dispatchFrame(false);
                    if (outputs.size() > 0) {
                        for (GLOutput<?> output : outputs) {
                            if (!output.isValid()) continue;
                            makeCurrent(output);
                            output.dispatchDraw();
                            for (GLShader<?> shader : shaderMap.values()) {
                                shader.dispatchDraw();
                            }
                            for (GLObject<?> obj : objects) {
                                obj.dispatchDraw();
                            }
                            output.dispatchCommit();
                        }
                    }
                    dispatchFrame(true);
                }
            }
        } finally {
            // 销毁
            synchronized (lock) {
                currentOutput = null;
                for (GLOutput<?> output : outputs) {
                    output.dispatchAttached(false);
                }
                outputs.clear();
                for (GLShader<?> shader : shaderMap.values()) {
                    shader.dispatchAttached(false);
                }
                for (GLObject<?> obj : objects) {
                    obj.dispatchAttached(false);
                }
                shaderMap.clear();
                objects.clear();
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(getDisplay(), EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    EGL14.eglTerminate(display);
                    display = EGL14.EGL_NO_DISPLAY;
                }
            }
        }

        Log.d(TAG, getPrintName() + "/loop: end");
    }

    /**
     * 通知帧是否开始绘制
     *
     * @param finished false，开始绘制；true，结束绘制
     */
    private void dispatchFrame(boolean finished) {
        for (GLOutput<?> output : outputs) {
            output.dispatchFrame(finished);
        }
        for (GLShader<?> shader : shaderMap.values()) {
            shader.dispatchFrame(finished);
        }
        for (GLObject<?> obj : objects) {
            obj.dispatchFrame(finished);
        }
    }

    /**
     * 销毁环境
     */
    public void destroy() {
        synchronized (lock) {
            if (state != 2) {
                state = 2;
            }
        }
    }

    /**
     * 请求一个shader对象
     *
     * @param type shader类型
     * @param <T>  shader内容类型
     * @return shader对象
     */
    @SuppressWarnings("unchecked")
    public <T extends GLShader<?>> T requestShader(Class<T> type) {
        try {
            T shader = (T) shaderMap.get(type);
            return shader;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 切换当前输出
     *
     * @param output 输出对象
     */
    private void makeCurrent(GLOutput<?> output) {
        if (currentOutput != output) {
            try {
                if (null != output) {
                    output.makeCurrent();
                }
                currentOutput = output;
            } catch (Exception e) {
                Log.w(TAG, getPrintName() + "/makeCurrent: ", e);
            }
        }
    }

    /**
     * 清空当前输出
     */
    private void clearCurrent() {
        Log.d(TAG, getPrintName() + "/clearCurrent: ");
        currentOutput = null;
        EGL14.eglMakeCurrent(getDisplay(), EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    /**
     * 设置FPS，大于0，控制帧率；否则，不限制帧率
     *
     * @param fps 帧率
     */
    public void setFps(int fps) {
        this.fps = fps;
    }

    /**
     * 获取帧率，注意：非实时帧率，返回的是控制的最大帧率
     *
     * @return 帧率
     */
    public int getFps() {
        return fps;
    }

    /**
     * 获取EGLDisplay对象
     *
     * @return EGLDisplay对象
     */
    public EGLDisplay getDisplay() {
        return display;
    }

    /**
     * 获取当前输出对象
     *
     * @return 当前输出对象
     */
    public GLOutput<?> getCurrentOutput() {
        return currentOutput;
    }

    /**
     * 检测EGL错误
     *
     * @param name 名称
     */
    public void checkEGLError(String name) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(String.format("%s.eglGetError: 0x%x (%s)", name, error, error));
        }
    }

    /**
     * 检测GL错误
     *
     * @param name 名称
     */
    public void checkGlError(String name) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(String.format("%s.glGetError: 0x%x (%s)", name, error, error));
        }
    }

    /**
     * 设置主输出
     *
     * @param mainOutput 主输出
     */
    void setMainOutput(GLOutput<?> mainOutput) {
        if (this.mainOutput != mainOutput) {
            if (null != this.mainOutput) {
                this.mainOutput.dispatchAttached(false);
            }
            if (null == mainOutput) {
                this.mainOutput = null;
            } else if (objectSet.contains(mainOutput)) {
                this.mainOutput = mainOutput;
                this.mainOutput.dispatchAttached(false);
                this.mainOutput.dispatchAttached(true);
            }
        }
    }

    /**
     * 通知环境输出有效
     *
     * @param output 输出
     */
    protected void dispatchOutputValid(GLOutput<?> output) {
        if (mainOutput == output) {
            Log.d(TAG, getPrintName() + "/dispatchOutputValid: mainOutput");
            // 主输出可用
            makeCurrent(output);
            // 加载输出
            for (GLOutput<?> ot : outputs) {
                ot.dispatchAttached(true);
            }
            // 加载shader
            for (GLShader<?> shader : shaderMap.values()) {
                shader.dispatchAttached(true);
            }
            // 加载object
            for (GLObject<?> obj : objects) {
                obj.dispatchAttached(true);
            }
        }
    }

    /**
     * 通知环境输出无效
     *
     * @param output 输出无效
     */
    protected void dispatchOutputInvalid(GLOutput<?> output) {
        if (mainOutput == output) {
            clearCurrent();
            Log.d(TAG, getPrintName() + "/dispatchOutputInvalid: mainOutput");
            // 主输出不可用
            // 卸载输出
            for (GLOutput<?> ot : outputs) {
                if (ot != output) {
                    ot.dispatchAttached(false);
                }
            }
            // 卸载shader
            for (GLShader<?> shader : shaderMap.values()) {
                shader.dispatchAttached(false);
            }
            // 卸载object
            for (GLObject<?> obj : objects) {
                obj.dispatchAttached(false);
            }
        }
    }

    /**
     * 获取主输出
     *
     * @return 主输出
     */
    public GLOutput<?> getMainOutput() {
        return mainOutput;
    }

    /**
     * 设置名称，仅作为标记使用
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取名称，仅作为标记使用
     *
     * @return 名称
     */
    public String getName() {
        if (TextUtils.isEmpty(name)) {
            return getClass().getSimpleName();
        }
        return name;
    }

    protected String getPrintName() {
        return getName() + "@" + Integer.toHexString(hashCode());
    }

    @Override
    public String toString() {
        return getPrintName() + " {" +
                "name='" + name + '\'' +
                ", fps=" + fps +
                ", state=" + state +
                '}';
    }
}
