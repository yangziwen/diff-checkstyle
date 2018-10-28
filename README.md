# diff-checkstyle
### 介绍
在使用[checkstyle](http://checkstyle.sourceforge.net/)检查项目中的代码风格时，工具会一次性输出每个文件中的全部问题。这使得我们很难在清理完存量代码风格问题之前，对正在开发的代码进行有效的风格检查。

针对这一痛点，本人对checkstyle的命令行工具进行了扩展，使其支持仅检查和输出增量变更的代码行中出现的风格问题。

### 使用方法
* 本工具在checkstyle[原有命令行参数](http://checkstyle.sourceforge.net/cmdline.html)的基础上，新增<b>--git-dir</b>和<b>--base-rev</b>两个参数。
    * git-dir：用于指定git代码库的根目录。使用此参数时，工具会忽略按checkstyle原生方式指定的待扫描文件，而是查找base-rev与HEAD之间发生过变更的代码文件进行扫描。
    * base-rev：用于指定将最新代码(HEAD)与哪个历史版本(commit or branch or tag)进行比对。可以省略此参数，缺省值为HEAD~(即最新commit的第一父节点)。
* 基于打包结果运行
```
java -jar diff-checkstyle.jar -c /custom_checks.xml --git-dir ${your_git_repo_path} --base-rev HEAD~3
```
* 基于maven的exec插件运行
```
mvn exec:java \
-Dexec.mainClass="io.github.yangziwen.checkstyle.Main" \
-Dexec.args="-c /custom_checks.xml --git-dir ${your_git_repo_path} --base-rev HEAD~3 "
```

### 其他
* 除了codestyle默认提供的[sun_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/sun_checks.xml)和[google_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml)配置，还追加了[custom_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_checks.xml)和[custom_full_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_full_checks.xml)这两个基本符合阿里巴巴代码规范的配置。
* 在有未提交的变更文件的情况下进行扫描，有可能导致基于HEAD计算出的变更代码行与工作区实际扫描文件的代码行不一致的情形，因此请先提交所有变更代码后再执行基于git-dir和base-rev参数的代码风格扫描。
