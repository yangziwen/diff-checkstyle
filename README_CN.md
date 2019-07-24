# diff-checkstyle
[![Build Status](https://www.travis-ci.org/yangziwen/diff-checkstyle.svg?branch=master)](https://www.travis-ci.org/yangziwen/diff-checkstyle)
[![Coverage Status](https://coveralls.io/repos/github/yangziwen/diff-checkstyle/badge.svg?branch=master)](https://coveralls.io/github/yangziwen/diff-checkstyle?branch=master)
### 介绍
在使用[checkstyle](http://checkstyle.sourceforge.net/)检查项目中的代码风格时，工具会一次性输出每个文件中的全部问题。这使得我们很难在清理完存量代码风格问题之前，对正在开发的代码进行有效的风格检查。

针对这一痛点，本人对checkstyle的命令行工具进行了扩展，使其支持仅检查和输出增量变更的代码行中出现的风格问题。

### 使用方法
* 本工具在checkstyle[原有命令行参数](http://checkstyle.sourceforge.net/cmdline.html)的基础上，新增<b>--git-dir</b>、<b>--include-staged-codes</b>和<b>--base-rev</b>三个参数。
    * git-dir：用于指定git代码库的根目录。使用此参数时，工具会忽略按checkstyle原生方式指定的待扫描文件，而是查找base-rev与HEAD之间发生过变更的代码文件进行扫描。
    * include-staged-codes：携带此选项时，工具在计算变更代码行的过程中会将暂存区内的变更一并计算在内。
    * base-rev：用于指定将最新代码(HEAD)与哪个历史版本(commit or branch or tag)进行比对。可以省略此参数，当指定include-staged-codes选项时，base-rev缺省值为HEAD(即最新的commit)，否则缺省值为HEAD~(即最新commit的第一父节点)。
#### 基于jar包执行检查
```
java -jar diff-checkstyle.jar -c /custom_checks.xml --git-dir ${your_git_repo_path} --base-rev HEAD~3 --include-staged-codes
```
#### 基于maven-exec-plugin插件执行检查
   * 执行`mvn install`将diff-checkstyle安装到本地maven仓库
   * 在需要进行增量代码风格扫描的项目的pom文件中引入以下配置
   ```xml
    <properties>
        <!-- 为base-rev参数声明默认值，可在调用插件时进行覆盖 -->
        <checkstyle.base.rev>HEAD~</checkstyle.base.rev>
    </properties>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>1.5.0</version>
            <configuration>
                <executable>java</executable>
                <includeProjectDependencies>false</includeProjectDependencies>
                <includePluginDependencies>true</includePluginDependencies>
                <executableDependency>
                    <groupId>io.github.yangziwen</groupId>
                    <artifactId>diff-checkstyle</artifactId>
                </executableDependency>
                <mainClass>io.github.yangziwen.checkstyle.Main</mainClass>
                <cleanupDaemonThreads>false</cleanupDaemonThreads>
                <arguments>
                    <!-- 可配置任何checkstyle命令行支持的参数 -->
                    <argument>-c</argument>
                    <argument>/custom_checks.xml</argument>
                    <argument>--git-dir</argument>
                    <argument>${basedir}</argument>
                    <argument>-f</argument>
                    <argument>xml</argument>
                    <argument>-o</argument>
                    <argument>${project.build.directory}/checkstyle.xml</argument>
                    <argument>--base-rev</argument>
                    <!-- 通过系统变量传入base-rev参数 -->
                    <argument>${checkstyle.base.rev}</argument>
                    <argument>--include-staged-codes</argument>
                </arguments>
            </configuration>
            <dependencies>
                <dependency>
                    <groupId>io.github.yangziwen</groupId>
                    <artifactId>diff-checkstyle</artifactId>
                    <version>${the_version_in_use}</version>
                    <type>jar</type>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
   ```
   * 在项目中执行`mvn exec:java -Dcheckstyle.base.rev=HEAD~10`即可进行增量的代码风格检查，并可在调用脚本中基于命令的返回值($?)判断是否存在代码风格问题。

#### 在本地提交commit时执行检查

* 将[pre-commit](https://github.com/yangziwen/diff-checkstyle/blob/master/hooks/pre-commit)钩子和[diff-checkstyle.jar](https://github.com/yangziwen/diff-checkstyle/releases/download/0.0.4/diff-checkstyle.jar)文件拷贝到git的hooks目录中(${git_dir}/.git/hooks)，即可实现每次提交commit前的增量代码风格检查，并打断可能引入增量代码风格问题的提交。
* 亦可运行下面的命令来安装钩子
```Shell
# 下载安装脚本
curl https://raw.githubusercontent.com/yangziwen/diff-checkstyle/master/hooks/install.sh > install.sh

# 安装钩子到指定的git代码库
sh install.sh --repo-path=${the_absolute_path_of_your_git_repository}

# 或者安装钩子到全局
sh install.sh --global
```
* 可通过以下方式开启或关闭检查
```
# 开启检查
git config checkstyle.enabled true

# 关闭检查
git config checkstyle.enabled false
```
* 可通过以下方式设置代码库中的豁免路径
```
git config checkstyle.exclude-regexp .+-client/.*
```

### 其他
* 除了checkstyle默认提供的[sun_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/sun_checks.xml)和[google_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml)配置，还追加了[custom_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_checks.xml)和[custom_full_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_full_checks.xml)这两个基本符合阿里巴巴代码规范的配置。
* 在有未提交(或未加入暂存区)的变更文件的情况下进行扫描，有可能导致工具计算出的变更代码行与工作区实际扫描文件的代码行不一致的情形，因此请先提交所有变更代码后再执行基于git-dir和base-rev参数的代码风格扫描。
