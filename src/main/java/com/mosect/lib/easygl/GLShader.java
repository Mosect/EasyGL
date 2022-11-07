package com.mosect.lib.easygl;

import android.opengl.GLES20;
import android.text.TextUtils;

/**
 * OpenGL Shader实体
 *
 * @param <T> shader实体所需内容类型
 */
public abstract class GLShader<T extends GLContent> extends GLObject<T> {

    private int[] program; // 程序信息：0，程序id；1，顶点shader id；2，片元shader id

    public GLShader(GLEnv env) {
        super(env);
    }

    @Override
    protected void onGLCreate() {
        super.onGLCreate();
        try {
            // 加载shader源码
            String vertSource = onLoadVertSource();
            String fragSource = onLoadFragSource();
            if (!TextUtils.isEmpty(vertSource) && !TextUtils.isEmpty(fragSource)) {
                // 创建程序
                program = createProgram(vertSource, fragSource);
                // 链接程序
                onLinkProgramBefore();
                linkProgram(program[0]);
                // 初始化程序
                onInitProgram();
            }
        } catch (Exception e) {
            // 出现异常，清空程序
            clearProgram();
            throw e;
        }
    }

    @Override
    protected void onGLDestroy() {
        super.onGLDestroy();
        clearProgram();
    }

    private void clearProgram() {
        if (null != program) {
            GLES20.glDeleteProgram(program[0]);
            GLES20.glDeleteShader(program[1]);
            GLES20.glDeleteShader(program[2]);
            program = null;
        }
    }

    protected int getProgramId() {
        if (null != program) {
            return program[0];
        }
        return 0;
    }

    /**
     * 在链接程序之前触发，在此方法中执行glBindAttribLocation操作
     */
    protected void onLinkProgramBefore() {
    }

    /**
     * 初始化程序，在此方法中执行glGetUniformLocation、glGetAttribLocation等操作
     */
    protected abstract void onInitProgram();

    /**
     * 加载顶点shader源码
     *
     * @return 顶点shader源码
     */
    protected abstract String onLoadVertSource();

    /**
     * 加载片元shader源码
     *
     * @return 片元shader源码
     */
    protected abstract String onLoadFragSource();

    /**
     * 加载shader
     *
     * @param shaderType shader类型：GL_VERTEX_SHADER、GL_FRAGMENT_SHADER
     * @param source     源码
     * @return shader id
     */
    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        getEnv().checkGlError("glCreateShader");
        GLES20.glShaderSource(shader, source);
        getEnv().checkGlError("glShaderSource");
        GLES20.glCompileShader(shader);
        getEnv().checkGlError("glCompileShader");
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * 创建OpenGL程序
     *
     * @param vertSource 顶点shader源码
     * @param fragSource 片元shader源码
     * @return 程序信息
     */
    private int[] createProgram(String vertSource, String fragSource) {
        int vertexShader = 0;
        int fragmentShader = 0;
        int programId = 0;
        try {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertSource);
            if (vertexShader == 0) throw new RuntimeException("loadShader:GL_VERTEX_SHADER failed");
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragSource);
            if (fragmentShader == 0)
                throw new RuntimeException("loadShader:GL_FRAGMENT_SHADER failed");
            programId = GLES20.glCreateProgram();
            if (programId == 0) throw new RuntimeException("glCreateProgram failed");

            GLES20.glAttachShader(programId, vertexShader);
            getEnv().checkEGLError("glAttachShader");
            GLES20.glAttachShader(programId, fragmentShader);
            getEnv().checkGlError("glAttachShader");
            return new int[]{
                    programId,
                    vertexShader,
                    fragmentShader
            };
        } catch (Exception e) {
            if (programId != 0) {
                GLES20.glDeleteProgram(programId);
            }
            if (vertexShader != 0) {
                GLES20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GLES20.glDeleteShader(fragmentShader);
            }
            throw e;
        }
    }

    /**
     * 链接OpenGL程序
     *
     * @param programId 程序id
     */
    private void linkProgram(int programId) {
        GLES20.glLinkProgram(programId);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw new RuntimeException("glLinkProgram failed");
        }
    }

    /**
     * glGetUniformLocation简单调用
     *
     * @param name 变量名称
     * @return 变量location
     */
    protected int getUniformLocation(String name) {
        int location = GLES20.glGetUniformLocation(getProgramId(), name);
        if (location < 0)
            throw new RuntimeException(String.format("glGetUniformLocation(%s) failed", name));
        return location;
    }

    /**
     * glGetAttribLocation简单调用
     *
     * @param name 变量名称
     * @return 变量location
     */
    protected int getAttribLocation(String name) {
        int location = GLES20.glGetAttribLocation(getProgramId(), name);
        if (location < 0)
            throw new RuntimeException(String.format("glGetAttribLocation(%s) failed", name));
        return location;
    }

    /**
     * 判断程序shader可用
     *
     * @return true，shader可用；false，shader不可用
     */
    public boolean isValid() {
        return getProgramId() > 0;
    }
}
