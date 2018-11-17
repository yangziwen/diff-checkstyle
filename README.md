# diff-checkstyle
![ci](https://www.travis-ci.org/yangziwen/diff-checkstyle.svg?branch=master)

[Chinese Doc](https://github.com/yangziwen/diff-checkstyle/blob/master/README_CN.md)
### Introduction
When using [checkstyle](http://checkstyle.sourceforge.net/) to scan a project, it will output all the problems in each file at once. This makes it difficult to perform an effective style check on the codes under development before cleaning up all the existing style problems.

In response to this pain point, I extended the checkstyle command-line tool to support style checks only on incremental changes of code lines.

### Usage
* Besides [the original command-line options of checkstyle](http://checkstyle.sourceforge.net/cmdline.html), this tool adds three new options: <b>--git-dir</b>, <b>--include-staged-codes</b> and <b>--base-rev</b>
    * git-dir：Specify the root directory of the git repository. When using this option, the check job will only consider the files based on git-diff.
    * include-staged-codes：With this option, the tool will also consider the changes in git staging area.
    * base-rev：Specify the reference(commit or branch or tag) with which the latest commit(HEAD) will compare on the diff calculation. The default value of <b>base-rev</b> will be HEAD(the latest commit) if <b>--include-staged-codes</b> is used, otherwise the default is HEAD~(the first parent of the latest commit).
* Run with the jar file
```
java -jar diff-checkstyle.jar -c /custom_checks.xml --git-dir ${your_git_repo_path} --base-rev HEAD~3 --include-staged-codes
```
* Run with [maven-exec-plugin](http://www.mojohaus.org/exec-maven-plugin/)
   * Execute `mvn install` to install the archive file to your local maven repository
   * Introduce the following configuration to pom.xml of your project
   ```xml
    <properties>
        <!-- the default value of base-rev，can be overwritten when running -->
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
                    <!-- any parameters supported by the original checkstyle command-line tool -->
                    <argument>-c</argument>
                    <argument>/custom_checks.xml</argument>
                    <argument>--git-dir</argument>
                    <argument>${basedir}</argument>
                    <argument>-f</argument>
                    <argument>xml</argument>
                    <argument>-o</argument>
                    <argument>${project.build.directory}/checkstyle.xml</argument>
                    <argument>--base-rev</argument>
                    <!-- pass the base-rev value via the system variable -->
                    <argument>${checkstyle.base.rev}</argument>
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
   * Execute `mvn exec:java -Dcheckstyle.base.rev=HEAD~10 --include-staged-codes` to do the check job。

### Others
* In addition to the [sun_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/sun_checks.xml) and [google_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml) provided by checkstyle by default, two other configurations, [custom_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_checks.xml) and [custom_full_checks.xml](https://github.com/yangziwen/diff-checkstyle/blob/master/src/main/resources/custom_full_checks.xml) which basically conform to the Alibaba code specification, have been added. You can also use your favorite style configuration by specifying the absolute file path with <b>-c</b> option.
* Scanning with a changed file that has not been submitted and also not been added to the staging area may cause the modified code line calculated being inconsistent with the code line of the actual scanned file in the workspace, so please submit all changes first.
* Copy the [pre-commit](https://github.com/yangziwen/diff-checkstyle/blob/master/hooks/pre-commit) script and the diff-checkstyle.jar file to the git hooks directory (${git_dir}/.git/hooks), and it will do the check job whenever you submit a commit to your repository.

