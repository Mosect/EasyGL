package com.mosect.lib.easygl;

import android.text.TextUtils;
import android.util.Log;

/**
 * 表示一个OpenGL实体对象
 *
 * @param <T> 对象所需内容类型
 */
public class GLObject<T extends GLContent> {

    private static final String TAG = "GL/Object";

    private final GLEnv env; // 环境
    private String name; // 实体名称，仅作为标记使用，无其他用处
    private boolean attached; // 是否已依附到环境
    private T content; // 内容

    public GLObject(GLEnv env) {
        this.env = env;
    }

    /**
     * 在GLEnv中创建实体，必须调用此方法，此实体才可用
     * 注意：此方法不会立刻生效，需要环境中存在有效的主输出才生效
     */
    public void create() {
        env.runGLAction(() -> env.attach(this));
    }

    /**
     * 在GLEnv中销毁实体，不再需要此实体后调用
     * 注意：此方法不会立刻生效，需要环境中存在有效的主输出才生效
     */
    public void destroy() {
        env.runGLAction(() -> env.detach(this));
    }

    /**
     * 通知实体是否被依附到环境
     *
     * @param attached 是否被依附到环境
     */
    void dispatchAttached(boolean attached) {
        if (this.attached != attached) {
            this.attached = attached;
            if (attached) {
                Log.d(TAG, getPrintName() + "/onGLCreate: ");
                onGLCreate();
                Log.d(TAG, getPrintName() + "/onGLInitContent: ");
                onGLInitContent();
            } else {
                onGLClearContent();
                onGLDestroy();
            }
        }
    }

    /**
     * 通知实体，帧状态
     *
     * @param finished true，帧已绘制结束；false，帧开始绘制
     */
    void dispatchFrame(boolean finished) {
        if (finished) {
            onFrameEnd();
        } else {
            onFrameStart();
        }
    }

    /**
     * 通知实体，进行绘制
     */
    void dispatchDraw() {
        if (null != content) {
            content.drawContent();
        }
        onGLDraw();
    }

    /**
     * 设置内容
     * 注意：此方法不会立刻生效，需要环境中存在有效的主输出才生效
     *
     * @param content 内容对象
     */
    public void setContent(T content) {
        env.runGLAction(() -> {
            if (isAttached()) {
                Log.d(TAG, getPrintName() + "/onGLClearContent: ");
                onGLClearContent();
                this.content = content;
                Log.d(TAG, getPrintName() + "/onGLInitContent: ");
                onGLInitContent();
            } else {
                this.content = content;
            }
        });
    }

    /**
     * 获取环境对象
     *
     * @return 环境对象
     */
    public GLEnv getEnv() {
        return env;
    }

    /**
     * 获取内容
     *
     * @return 内容
     */
    public T getContent() {
        return content;
    }

    /**
     * 实体被创建时调用
     */
    protected void onGLCreate() {
    }

    /**
     * 实体初始化内容时调用
     */
    protected void onGLInitContent() {
        if (null != content) {
            content.initContent(this);
        }
    }

    /**
     * 实体清除内容时调用
     */
    protected void onGLClearContent() {
        if (null != content) {
            content.destroyContent();
        }
    }

    /**
     * 实体绘制时调用
     */
    protected void onGLDraw() {
    }

    /**
     * 帧开始
     */
    protected void onFrameStart() {
    }

    /**
     * 帧结束
     */
    protected void onFrameEnd() {
    }

    /**
     * 实体被消耗时调用
     */
    protected void onGLDestroy() {
    }

    /**
     * 判断是否已依附到环境
     *
     * @return true，依附到环境中，false，未依附到环境中
     */
    public boolean isAttached() {
        return attached;
    }

    @Override
    public String toString() {
        return getPrintName() + " {" +
                "name='" + name + '\'' +
                ", attached=" + attached +
                ", content=" + content +
                '}';
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
}
