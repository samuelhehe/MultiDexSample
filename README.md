---
layout: post
title: Android 低版本 multidex 65536 异步加载
category: 技术
tags: [android multidex 65536 ]
keywords:  Android multidex 65536 asyncload exception 
description: Android 低版本 multidex 65536 异步加载 ANR
---

Android 低版本 multidex 65536加载问题 异步加载解决方案。
====================================

使用子进程异步MultiDex Install 方法 介绍：

官网镇楼：
* [https://developer.android.com/studio/build/multidex.html](https://developer.android.com/studio/build/multidex.html)

现在将主要代码块 粘贴于此，以便回头查看。

AndroidManifest 中子进程配置如下：

![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_loadresactivity.png)

可以看到 process 处 设置 ":mini"  这里的":mini" 代表的是启动另外的进程，该进程以“applicaitonId:mini”命名，以":"为开头 这种写法是简写方式，其属性属于当前应用的私有进程，代表了其他应用的组件不可以和它跑在进程中。  参考文档： Android开发艺术探索 第二章 IPC机制的2.2.1中指出，开启多进程看似简单，实则暗藏杀机。经过实际测试的确如此，首先Application 的onCreate 会被调用N次 ，N的次数 包含了各种进程的名称数和主进程数。 
我们的操作就放在了attachBaseContext中 ，这是Android提供的方案，或者延伸为在attachBaseContext中同步加载dex的方案，它的好处是非常简单，所需的依赖集也非常少。 也就是说我们在启动加载过程中几乎不会出现NoClassFoundError , 话虽说如此，但实际测试中，碰到因此崩溃还是有的，测试工具是Testin ，但是测试报告，其中出现的错误机型，系统分布 确实是存在的。 这里我就不放截图了（因为我确实找不到了，登上testin 测试报告均已被清空，不知去向）

下边看一下在Application 子类 重写的attachBaseContext 方法的主要逻辑。

![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_attach.png)

因为application多次启动 这里就是为了抓取以mini结尾的进程。
![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_quickstart.png)

为了防止App 启动时出现ANR问题 采取了启动子进程 异步加载 class2.dex 方法， LoadResActivity 是启动的另外一个进程，看代码：

![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_loadres_activity.png)

![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_install_finish.png)

在上边的代码中 ， 第29行启动 异步load MultiDex install  ，第 36行便是 install 过程 ， 38行 是安装成功后调用Application中的installFinish 通过SP文件通信 告知主进程 子进程已经加载完毕。 后边是计时 log ， 退出当前进程 不再赘述。
![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_need_wait.png)

这里可以看到 installFinish 将classes2.dex 的sha1-digest 码保存至SP文件中，通过waitForDexopt 方法中 的needWait 方法来终止等待死循环。
![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_need_wait.png)

这里可以看到 SP 文件的读写属性 是MODE_MULTI_PROCESS的 ，也就是跨进程。  虽然说这种跨进程通信方式很low ，但有时候虽然他不是最好的方案，确实最有效的解决办法。但在某种意义上讲还是合适的，因为这种方法对即时性要求不高，交换一些简单的数据，对实时性要求不好，毕竟我们在里边sleep了200毫秒。

下边是在gradle中配置：
 ![image](https://raw.githubusercontent.com/samuelhehe/samuelhehe.github.io/master/res/multidex_gradle.png)

第57行  添加 android官方 multidex lib 依赖 ，以便导入multidex 相关class ， 第39， 40行 便是对class 的分割， method数量达到 48000 会进行下一个class.dex 分包。 当然如果打包时候发现方法数 48000< methodcount <= 65536 这样的情况 是不会进行分包的。 

注意： 第42行 注释行， 添加的条件是 需要指定main dex ， 该dex 分包的class 需要根据App的前期加载进行选择性打包。以防止前期使用而找不到Class的情况 ，接下来就是如何分包，如何将main class 加入到Maindex中去，这些东西已经有人帮我们做好了。看官网multiDexKeepProguard 部分 ， 也可以手动配置，这里就不在多说了。

## 总结：

就目前我们上边所述的方法 并不是完美的，采取的也是Android提供的方案，虽然采用多进程异步加载方案，但是同时也会有问题， 那就是 如果dex 比较大的情况下，我们的App会出现长时间加载 ， 在2.3 某些机型上测试 虽然不崩溃，但启动时间长达10s 。 通过testin 统计数据查看 基本上时间比较长的 或者NoclassFoundError的机型 一般都是2.3 ，3.0 的系统。再者可能就是机身内存比较小。 很遗憾，现在都已经6.0了，我觉得只要不崩溃，时间长点就长点吧。 
	最后一点需要提的是 在LoadResActivity 中的配置中可以看到 style ，内容一般就是透明的背景。 这就是为了防止出现长时间加载黑屏的办法。
以上代码已经整理好放在github 上 [https://github.com/samuelhehe/MultiDexSample](https://github.com/samuelhehe/MultiDexSample)欢迎批评指正，拍砖。

## 参考链接：
* [Android 使用android-support-multidex解决Dex超出方法数的限制问题,让你的应用不再爆棚](http://blog.csdn.net/t12x3456/article/details/40837287)
* [dex分包变形记](https://segmentfault.com/a/1190000004053072)
* [Android dex分包方案](http://www.tuicool.com/articles/rEBVNfY)
* [美团Android DEX自动拆包及动态加载简介](http://tech.meituan.com/mt-android-auto-split-dex.html)
* [关于『65535问题』的一点研究与思考](http://blog.csdn.net/zhaokaiqiang1992/article/details/50412975)
* [secondary-dex-gradle](https://github.com/creativepsyco/secondary-dex-gradle/)
* [Using Gradle to split external libraries in separated dex files to solve Android Dalvik 64k methods limit](https://stackoverflow.com/questions/23614095/using-gradle-to-split-external-libraries-in-separated-dex-files-to-solve-android)


