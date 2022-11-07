# EasyGL
Android简单的OpenGL调用库

# 指引

## 1. 引用项目
[![](https://jitpack.io/v/Mosect/EasyGL.svg)](https://jitpack.io/#Mosect/EasyGL)

## 2. 示例
```
// 1. 创建OpenGL环境
GLEnv env = new GLEnv();
// 开始启动环境
env.start();

// 2. 加入所需Shader，需要自己创建继承GLShader的类
new MyGLShader1(env).create();
new MyGLShader2(env).create();

// 3. 加入主输出
GLOutput<MyOutputContent> output = new GLOutput<>(env);
output.create();
output.setMain();

// 4. 创建输出内容，即窗口，Android上为一个Surface或者SurfaceHolder，此处MyOutputContent实现了GLSurface接口
MyOutputContent outputContent = new MyOutputContent();
output.setContent(outputContent);

// 5. 创建需要绘制的实体
GLObject<MyContent> obj1 = new GLObject<>(env);
obj1.create();
// 需要为实体添加内容
MyContent content = new MyContent();
obj1.setContent(content);

// 6. 不再使用时，销毁环境
env.destroy();
```

# 其他
此OpenGL库为核心库，只有基本框架，没有做具体实现。
