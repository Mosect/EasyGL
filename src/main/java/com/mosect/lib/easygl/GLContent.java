package com.mosect.lib.easygl;

/**
 * 素材内容，{@link com.mosect.lib.easygl.GLObject GLObject}
 */
public interface GLContent {

    /**
     * 初始化内容
     *
     * @param object OpenGL实体对象
     */
    void initContent(com.mosect.lib.easygl.GLObject<?> object);

    /**
     * 绘制内容
     */
    void drawContent();

    /**
     * 销毁内容
     */
    void destroyContent();
}
