#!/bin/bash

opts="$*"

PRE_COMMIT_FILE_URL="https://raw.githubusercontent.com/yangziwen/diff-checkstyle/master/hooks/pre-commit"
DIFF_CHECKSTYLE_RELEASE_URL="https://github.com/yangziwen/diff-checkstyle/releases/download/0.0.3/diff-checkstyle.jar"
CHECKSTYLE_CONFIG_FILE_URL="https://raw.githubusercontent.com/yangziwen/diff-checkstyle/master/src/main/resources/custom_checks.xml"

function get_value_from_opts() {
    key="--$1="
    value=`echo "${opts##*${key}}" | awk -F' --' '{print $1}'`
    if [[ ! "$value" =~ ^-- ]]; then
        echo "$value"
        return 0
    fi
    if [[ "$opts" =~ --$1($|\ ) ]]; then
        echo "true"
        return 0
    fi
    echo ""
}

function is_diff_checkstyle_pre_commit_script() {
    if [[ "$PRE_COMMIT_BLOB_ID" == `git hash-object $1` ]]; then
        return 0
    fi
    return 1
}

function get_checkstyle_config_file_path() {
    curl $CHECKSTYLE_CONFIG_FILE_URL > custom_checks.xml
    echo "custom_checks.xml"
}

function get_pre_commit_script_path() {
    curl $PRE_COMMIT_FILE_URL > pre-commit-diff-checkstyle
    echo "pre-commit-diff-checkstyle"
}

function get_diff_checkstyle_jar_path() {
    curl -L -o diff-checkstyle.jar $DIFF_CHECKSTYLE_RELEASE_URL
    echo "diff-checkstyle.jar"
}

function update_checkstyle_config() {
    hook_path="`git config --global --get core.hooksPath`"
    config_file_path="`get_checkstyle_config_file_path`"
    cp $config_file_path $hook_path/custom_checks.xml
    git config --global checkstyle.config-file $hook_path/custom_checks.xml
}

function install_global_hook() {
    hook_path="`git config --global --get core.hooksPath`"
    if [[ -z "$hook_path" ]]; then
        git config --global core.hooksPath ~/.githooks
        hook_path="`git config --global --get core.hooksPath`"
    fi
    if [[ ! -d $hook_path ]]; then
        mkdir -p $hook_path
    fi
    pre_commit_script_path="`get_pre_commit_script_path`"
    diff_checkstyle_jar_path="`get_diff_checkstyle_jar_path`"
    cp $pre_commit_script_path $hook_path/pre-commit
    cp $diff_checkstyle_jar_path $hook_path/diff-checkstyle.jar
    chmod +x $hook_path/pre-commit
    git config --global checkstyle.enabled true
    echo "diff-checkstyle pre-commit hook is intalled globally!"
}

function install_hook_to_repo() {
    repo_path="$1"
    if [ ! -d $repo_path ]; then
        echo "repo[$repo_path] does not exist!"
        exit 1
    fi
    hook_path="$repo_path/.git/hooks"
    pre_commit_script_path="`get_pre_commit_script_path`"
    diff_checkstyle_jar_path="`get_diff_checkstyle_jar_path`"
    cp $pre_commit_script_path $hook_path/pre-commit
    cp $diff_checkstyle_jar_path $hook_path/diff-checkstyle.jar
    chmod +x $hook_path/pre-commit
    git config checkstyle.enabled true
    echo "diff-checkstyle pre-commit hook is installed to $repo_path!"
}

global="`get_value_from_opts global`"

repo_path="`get_value_from_opts repo-path`"

update_config_file="`get_value_from_opts update-config-file`"

if [[ "true" == "$global" ]]; then
    install_global_hook
    exit 0
fi

if [[ -n "$repo_path" ]]; then
    install_hook_to_repo $repo_path
    exit 0
fi

if [[ "true" == "$update_config_file" ]]; then
    update_checkstyle_config
    exit 0
fi

echo "Please specify the options correctly"
echo "  --global => install the diff-checkstyle hook globally"
echo "  --repo-path=\${the_absolute_path_of_your_git_repository} => install the diff-checkstyle hook to the specified git repository"
echo "  --update-config-file => download and use the latest checkstyle config file provided by this tool"
